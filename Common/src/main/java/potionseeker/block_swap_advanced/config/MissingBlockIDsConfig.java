package potionseeker.block_swap_advanced.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import potionseeker.block_swap_advanced.BlockSwap;
import potionseeker.block_swap_advanced.serialization.CodecUtil;
import potionseeker.block_swap_advanced.serialization.CommentedCodec;
import potionseeker.block_swap_advanced.serialization.JanksonJsonOps;
import potionseeker.block_swap_advanced.serialization.JanksonUtil;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Function;

public record MissingBlockIDsConfig(Map<String, Block> idRemapper) {

    private static final String HEADER = """
        /*
         Missing Block IDs Configuration
         This file maps invalid or missing block IDs to valid ones, useful for compatibility with old worlds or mods with removed blocks.
         Location: Stored in 'serverconfig/missing_block_ids.json5' for each world (e.g., 'world/serverconfig/missing_block_ids.json5').
         Default Config: A global template can be placed in 'defaultconfigs/missing_block_ids.json5', which is copied to new worlds' 'serverconfig' folder if present. If absent, a default config is generated in 'serverconfig'.
         For detailed instructions, see 'config/block_swap_advanced/block_swap_readme.json5'.
        
         Example:
         {
             "id_remapper": {
                 "minecraft:coarse_dirt": "minecraft:dirt",
                 "minecraft:diamond_block": "minecraft:emerald_block"
             }
         }
        */
        """;

    private static final Codec<Map<String, String>> ID_REMAPPER_CODEC = Codec.unboundedMap(Codec.STRING, Codec.STRING);

    private static final Codec<MissingBlockIDsConfig> CODEC = RecordCodecBuilder.create(builder ->
            builder.group(
                    CommentedCodec.of(ID_REMAPPER_CODEC, "id_remapper", "A map of blocks that specifies what the \"old\" broken block is and what its \"new\" functional block is.\nExample:\n" + HEADER, new Object2ObjectOpenHashMap<>()).optionalFieldOf("id_remapper", new Object2ObjectOpenHashMap<>()).forGetter(config -> {
                        Object2ObjectOpenHashMap<String, String> map = new Object2ObjectOpenHashMap<>();
                        config.idRemapper().forEach((oldId, block) -> map.put(oldId, BuiltInRegistries.BLOCK.getKey(block).toString()));
                        return map;
                    })
            ).apply(builder, map -> {
                IdentityHashMap<String, Block> idRemapper = new IdentityHashMap<>();
                map.forEach((oldId, newIdStr) -> {
                    ResourceLocation newId = new ResourceLocation(newIdStr);
                    Block block = BuiltInRegistries.BLOCK.get(newId);
                    if (block != null) {
                        idRemapper.put(oldId, block);
                    } else {
                        BlockSwap.LOGGER.error("Invalid new block ID in id_remapper: {}", newIdStr);
                    }
                });
                return new MissingBlockIDsConfig(idRemapper);
            })
    );

    private static MissingBlockIDsConfig CONFIG = null;

    public static MissingBlockIDsConfig getConfig(boolean reload) {
        if (CONFIG == null || reload) {
            if (BlockSwap.CONFIG_PATH == null) {
                BlockSwap.LOGGER.warn("CONFIG_PATH is null, using default config");
                CONFIG = new MissingBlockIDsConfig(new IdentityHashMap<>());
                return CONFIG;
            }
            Path configPath = BlockSwap.CONFIG_PATH.resolve("missing_block_ids.json5");
            File configFile = configPath.toFile();
            if (!configFile.exists()) {
                Path defaultConfigPath = BlockSwap.DEFAULT_CONFIG_PATH.resolve("missing_block_ids.json5");
                File defaultConfigFile = defaultConfigPath.toFile();
                BlockSwap.LOGGER.debug("Checking for default missing_block_ids config at: {}", defaultConfigPath);
                if (defaultConfigFile.exists() && defaultConfigFile.isFile()) {
                    try {
                        Files.createDirectories(configPath.getParent());
                        Files.copy(defaultConfigPath, configPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        BlockSwap.LOGGER.info("Successfully copied default missing_block_ids config from {} to {}", defaultConfigPath, configPath);
                    } catch (Exception e) {
                        BlockSwap.LOGGER.error("Failed to copy default missing_block_ids config from {} to {}: {}", defaultConfigPath, configPath, e.getMessage());
                        // Generate default config in serverconfig
                        IdentityHashMap<String, Block> defaultMap = new IdentityHashMap<>();
                        JanksonUtil.createConfig(configPath, CODEC, HEADER, new Object2ObjectOpenHashMap<>(), JanksonJsonOps.INSTANCE, new MissingBlockIDsConfig(defaultMap));
                    }
                } else {
                    BlockSwap.LOGGER.info("No default missing_block_ids config found at {}. Generating default config at {}", defaultConfigPath, configPath);
                    // Generate default config in serverconfig
                    IdentityHashMap<String, Block> defaultMap = new IdentityHashMap<>();
                    JanksonUtil.createConfig(configPath, CODEC, HEADER, new Object2ObjectOpenHashMap<>(), JanksonJsonOps.INSTANCE, new MissingBlockIDsConfig(defaultMap));
                }
            }
            try {
                CONFIG = JanksonUtil.readConfig(configPath, CODEC, JanksonJsonOps.INSTANCE);
                BlockSwap.LOGGER.debug("Loaded missing_block_ids config from: {}", configPath);
            } catch (Exception e) {
                BlockSwap.LOGGER.error("Failed to load missing_block_ids config from {}: {}. Using default config.", configPath, e.getMessage());
                CONFIG = new MissingBlockIDsConfig(new IdentityHashMap<>());
            }
        }
        return CONFIG;
    }
}