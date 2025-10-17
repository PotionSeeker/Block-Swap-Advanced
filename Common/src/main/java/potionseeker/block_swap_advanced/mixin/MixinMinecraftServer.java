package potionseeker.block_swap_advanced.mixin;

import com.google.common.collect.ImmutableMap;
import com.mojang.datafixers.DataFixer;
import potionseeker.block_swap_advanced.BlockSwap;
import potionseeker.block_swap_advanced.config.BlockSwapConfig;
import potionseeker.block_swap_advanced.config.MissingBlockIDsConfig;
import potionseeker.block_swap_advanced.swapper.Swapper;
import potionseeker.block_swap_advanced.serialization.JanksonJsonOps;
import potionseeker.block_swap_advanced.serialization.JanksonUtil;

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
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Mixin(MinecraftServer.class)
public abstract class MixinMinecraftServer {

    @Inject(at = @At("RETURN"), method = "<init>")
    private void blockSwap_loadConfig(Thread thread, LevelStorageSource.LevelStorageAccess levelStorageAccess, PackRepository packRepository, WorldStem worldStem, Proxy proxy, DataFixer dataFixer, Services services, ChunkProgressListenerFactory chunkProgressListenerFactory, CallbackInfo ci) {
        Path worldPath = levelStorageAccess.getLevelPath(net.minecraft.world.level.storage.LevelResource.ROOT);
        BlockSwap.init(worldPath);
        BlockSwapConfig config = BlockSwapConfig.getConfig(true);
        Swapper.updateConfig(config);
        MissingBlockIDsConfig missingBlockIDsConfig = MissingBlockIDsConfig.getConfig(true);

        if (config.generateAllKnownStates()) {
            Map<Block, List<BlockState>> allKnownStates = new TreeMap<>(Comparator.comparing(block -> BuiltInRegistries.BLOCK.getKey(block).toString()));
            for (Block block : BuiltInRegistries.BLOCK) {
                allKnownStates.computeIfAbsent(block, key -> key.getStateDefinition().getPossibleStates());
            }

            allKnownStates.forEach((block, blockStates) -> {
                ResourceLocation blockKey = BuiltInRegistries.BLOCK.getKey(block);
                Path knownStatesPath = BlockSwap.CONFIG_PATH.resolve("known_states").resolve(blockKey.getNamespace()).resolve(blockKey.getPath() + ".json5");
                JanksonUtil.createConfig(knownStatesPath, Swapper.COMMENTED_STATE_CODEC.listOf(), JanksonUtil.HEADER_CLOSED, ImmutableMap.of(), JanksonJsonOps.INSTANCE, blockStates);
            });
        }
    }
}