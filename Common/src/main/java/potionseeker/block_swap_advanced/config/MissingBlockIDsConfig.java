package potionseeker.block_swap_advanced.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import potionseeker.block_swap_advanced.BlockSwap;
import potionseeker.block_swap_advanced.serialization.CodecUtil;
import potionseeker.block_swap_advanced.serialization.CommentedCodec;
import potionseeker.block_swap_advanced.serialization.JanksonJsonOps;
import potionseeker.block_swap_advanced.serialization.JanksonUtil;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.world.level.block.Block;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.Map;

public record MissingBlockIDsConfig(Map<String, Block> idRemapper) {

    private static final String HEADER = """
        // Missing Block IDs Configuration
        // This file maps invalid or missing block IDs to valid ones, useful for compatibility with old worlds or mods with removed blocks.
        // Location: Stored in 'serverconfig/missing_block_ids.json5' for each world (e.g., 'world/serverconfig/missing_block_ids.json5').
        // Default Config: A global template can be placed in 'defaultconfigs/missing_block_ids.json5', which is copied to new worlds' 'serverconfig' folder if present. If absent, a default config is generated in 'serverconfig'.
        // For detailed instructions, see 'config/block_swap_advanced/readme.json5'.
        """;

    private static final String ID_REMAPPER_EXAMPLE = """
        "id_remapper": {
            // "Broken ID": "Valid ID"
            "minecraft:coarse_dirt": "minecraft:dirt",
            "minecraft:diamond_block": "minecraft:emerald_block"
        }
        """;

    private static final Codec<MissingBlockIDsConfig> CODEC = RecordCodecBuilder.create(builder ->
            builder.group(
                    CommentedCodec.of(Codec.unboundedMap(Codec.STRING, CodecUtil.BLOCK_CODEC), "id_remapper", "A map of blocks that specifies what the \"old\" broken block is and what its \"new\" functional block is.\nExample:\n" + ID_REMAPPER_EXAMPLE, new IdentityHashMap<>()).fieldOf("id_remapper").forGetter(MissingBlockIDsConfig::idRemapper)
            ).apply(builder, MissingBlockIDsConfig::new)
    );

    private static MissingBlockIDsConfig CONFIG = null;

    public static MissingBlockIDsConfig getConfig(boolean reload) {
        if (CONFIG == null || reload) {
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
                        // Generate default config with example content
                        IdentityHashMap<String, Block> defaultMap = new IdentityHashMap<>();
                        defaultMap.put("minecraft:invalid_block", net.minecraft.world.level.block.Blocks.STONE);
                        JanksonUtil.createConfig(configPath, CODEC, HEADER, new Object2ObjectOpenHashMap<>(), JanksonJsonOps.INSTANCE, new MissingBlockIDsConfig(defaultMap));
                    }
                } else {
                    BlockSwap.LOGGER.info("No default missing_block_ids config found at {}. Generating new config at {}", defaultConfigPath, configPath);
                    // Generate default config with example content
                    IdentityHashMap<String, Block> defaultMap = new IdentityHashMap<>();
                    defaultMap.put("minecraft:invalid_block", net.minecraft.world.level.block.Blocks.STONE);
                    JanksonUtil.createConfig(configPath, CODEC, HEADER, new Object2ObjectOpenHashMap<>(), JanksonJsonOps.INSTANCE, new MissingBlockIDsConfig(defaultMap));
                }
            }
            try {
                CONFIG = JanksonUtil.readConfig(configPath, CODEC, JanksonJsonOps.INSTANCE);
                BlockSwap.LOGGER.debug("Loaded missing_block_ids config from: {}", configPath);
            } catch (Exception e) {
                BlockSwap.LOGGER.error("Failed to load missing_block_ids config from {}: {}. Using default config.", configPath, e.getMessage());
                IdentityHashMap<String, Block> defaultMap = new IdentityHashMap<>();
                defaultMap.put("minecraft:invalid_block", net.minecraft.world.level.block.Blocks.STONE);
                CONFIG = new MissingBlockIDsConfig(defaultMap);
            }
        }
        return CONFIG;
    }
}