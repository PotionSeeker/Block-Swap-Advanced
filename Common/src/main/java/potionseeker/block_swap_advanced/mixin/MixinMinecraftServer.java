package potionseeker.block_swap_advanced.mixin;

import com.google.common.collect.ImmutableMap;
import com.mojang.datafixers.DataFixer;
import com.mojang.serialization.Codec;
import potionseeker.block_swap_advanced.BlockSwap;
import potionseeker.block_swap_advanced.config.BlockSwapConfig;
import potionseeker.block_swap_advanced.config.MissingBlockIDsConfig;
import potionseeker.block_swap_advanced.serialization.BlockInfo;
import potionseeker.block_swap_advanced.serialization.JanksonJsonOps;
import potionseeker.block_swap_advanced.serialization.JanksonUtil;
import potionseeker.block_swap_advanced.swapper.Swapper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.Services;
import net.minecraft.server.WorldStem;
import net.minecraft.server.level.progress.ChunkProgressListenerFactory;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.net.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Mixin(MinecraftServer.class)
public abstract class MixinMinecraftServer {

    @Inject(
            method = "<init>(Ljava/lang/Thread;Lnet/minecraft/world/level/storage/LevelStorageSource$LevelStorageAccess;Lnet/minecraft/server/packs/repository/PackRepository;Lnet/minecraft/server/WorldStem;Ljava/net/Proxy;Lcom/mojang/datafixers/DataFixer;Lnet/minecraft/server/Services;Lnet/minecraft/server/level/progress/ChunkProgressListenerFactory;)V",
            at = @At("RETURN")
    )
    private void blockSwap_loadConfig(Thread thread, LevelStorageSource.LevelStorageAccess levelStorageAccess, PackRepository packRepository, WorldStem worldStem, Proxy proxy, DataFixer dataFixer, Services services, ChunkProgressListenerFactory chunkProgressListenerFactory, CallbackInfo ci) {
        Path worldPath = levelStorageAccess.getLevelPath(net.minecraft.world.level.storage.LevelResource.ROOT);
        BlockSwap.init(worldPath);
        BlockSwapConfig config = BlockSwapConfig.getConfig(true);
        Swapper.updateConfig(config);
        MissingBlockIDsConfig missingBlockIDsConfig = MissingBlockIDsConfig.getConfig(true);

        BlockSwap.LOGGER.info("Config loaded: generateBlockInfo={}", config.generateBlockInfo());
        if (config.generateBlockInfo()) {
            Path blockSwapInfoPath = BlockSwap.CONFIG_PATH.resolve("block_swap_info");
            BuiltInRegistries.BLOCK.keySet().stream()
                    .filter(key -> !key.getNamespace().equals("minecraft"))
                    .map(BuiltInRegistries.BLOCK::get)
                    .map(block -> {
                        ResourceLocation blockKey = BuiltInRegistries.BLOCK.getKey(block);
                        List<BlockState> states = block.getStateDefinition().getPossibleStates();
                        Map<String, List<String>> properties = new TreeMap<>();
                        if (!states.isEmpty() && !states.get(0).getProperties().isEmpty()) {
                            states.get(0).getProperties().forEach(property -> {
                                List<String> values = states.stream()
                                        .map(state -> state.getValue(property).toString())
                                        .distinct()
                                        .sorted()
                                        .collect(Collectors.toList());
                                properties.put(property.getName(), values);
                            });
                        }
                        return new BlockInfo(blockKey.toString(), properties);
                    })
                    .sorted(Comparator.comparing(BlockInfo::name))
                    .forEach(blockInfo -> {
                        String modId = blockInfo.name().split(":")[0];
                        Path modBlocksPath = blockSwapInfoPath.resolve(modId + ".json5");
                        List<BlockInfo> blockInfoList = BuiltInRegistries.BLOCK.keySet().stream()
                                .filter(key -> key.getNamespace().equals(modId))
                                .map(BuiltInRegistries.BLOCK::get)
                                .map(block -> {
                                    ResourceLocation blockKey = BuiltInRegistries.BLOCK.getKey(block);
                                    List<BlockState> states = block.getStateDefinition().getPossibleStates();
                                    Map<String, List<String>> properties = new TreeMap<>();
                                    if (!states.isEmpty() && !states.get(0).getProperties().isEmpty()) {
                                        states.get(0).getProperties().forEach(property -> {
                                            List<String> values = states.stream()
                                                    .map(state -> state.getValue(property).toString())
                                                    .distinct()
                                                    .sorted()
                                                    .collect(Collectors.toList());
                                            properties.put(property.getName(), values);
                                        });
                                    }
                                    return new BlockInfo(blockKey.toString(), properties);
                                })
                                .sorted(Comparator.comparing(BlockInfo::name))
                                .collect(Collectors.toList());

                        BlockSwap.LOGGER.debug("Generated block info list for mod {}: {} blocks: {}", modId, blockInfoList.size(), blockInfoList);
                        String header = """
                        /*
                         Block Information for %s
                         This file lists all blocks and their possible states for the %s mod.
                         Generated because 'generate_block_info' is set to true in block_swap.json5.
                         Use this to reference valid block IDs and states when configuring block_swap.json5.
                        */
                        """.formatted(modId, modId);
                        try {
                            Files.createDirectories(modBlocksPath.getParent());
                            BlockSwap.LOGGER.debug("Writing block info for mod {} to {}", modId, modBlocksPath);
                            JanksonUtil.createConfig(modBlocksPath, Codec.list(BlockInfo.CODEC), header, ImmutableMap.of(), JanksonJsonOps.INSTANCE, blockInfoList);
                            BlockSwap.LOGGER.info("Generated {} at {}", modBlocksPath.getFileName(), modBlocksPath);
                        } catch (Exception e) {
                            BlockSwap.LOGGER.error("Failed to generate {} at {}: {}", modBlocksPath.getFileName(), modBlocksPath, e.getMessage(), e);
                        }
                    });
        } else {
            BlockSwap.LOGGER.info("Block info generation skipped: generate_block_info is false");
        }
    }
}