package potionseeker.block_swap_advanced.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import potionseeker.block_swap_advanced.BlockSwap;
import potionseeker.block_swap_advanced.swapper.Swapper;
import potionseeker.block_swap_advanced.serialization.CommentedCodec;
import potionseeker.block_swap_advanced.serialization.JanksonJsonOps;
import potionseeker.block_swap_advanced.serialization.JanksonUtil;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

public record BlockSwapConfig(List<Swapper.SwapEntry> swapEntries, boolean retroGen, boolean generateAllKnownStates, boolean verboseLogging) {

    public static final BlockSwapConfig DEFAULT = new BlockSwapConfig(List.of(), false, false, false);

    private static final String CONFIG_HEADER = """
        // BlockSwap Configuration
        // This file configures the BlockSwap mod, which allows swapping one block type for another during world generation, retro-generation, or block placement.
        // Location: Stored in 'serverconfig/block_swap.json5' for each world (e.g., 'world/serverconfig/block_swap.json5').
        // Default Config: A global template can be placed in 'defaultconfigs/block_swap.json5', which is copied to new worlds' 'serverconfig' folder if present. If absent, a default config is generated in 'serverconfig'.
        // All fields are optional unless specified. Omitted fields use default values.
        // For detailed instructions, see 'config/block_swap_advanced/readme.json5'.
        
        // Available 'swapper' Options:
        // - old (BlockState, required): The block state to replace (e.g., {"Name": "minecraft:cobblestone"}). Supports partial properties (e.g., {"Name": "minecraft:pointed_dripstone", "Properties": {"thickness": "tip"}}).
        // - new (BlockState, required): The block state to replace with (e.g., {"Name": "minecraft:diamond_block"}).
        // - replace_placement (boolean, default: false): Whether to swap blocks when placed by players or entities. Ignored if only_replace_placements is true.
        // - only_replace_placements (boolean, default: false): Whether to swap only blocks placed by players or entities, skipping world generation and retro-generation.
        // - defer_swap (boolean, default: false): Whether to defer swapping until after chunk generation features complete, allowing dependent blocks (e.g., pointed_dripstone on dripstone_block) to generate.
        // - min_y (integer, default: world min height, -64): Minimum Y level for swaps. Use Integer.MIN_VALUE for world minimum.
        // - max_y (integer, default: world max height, 320): Maximum Y level for swaps. Use Integer.MAX_VALUE for world maximum.
        // - dimensions_whitelist (list of strings, default: []): Dimension IDs where swaps apply (e.g., ["minecraft:overworld"]). Empty allows all unless blacklisted.
        // - dimensions_blacklist (list of strings, default: []): Dimension IDs where swaps are prevented (e.g., ["minecraft:the_nether"]).
        // - biomes_whitelist (list of strings, default: []): Biome IDs where swaps apply (e.g., ["minecraft:plains"]). Empty allows all unless blacklisted.
        // - biomes_blacklist (list of strings, default: []): Biome IDs where swaps are prevented (e.g., ["minecraft:deep_ocean"]).
        // - block_swap_rand (float, 0.0 to 1.0, default: 1.0): Randomization factor for swaps (0.0 = no swaps, 1.0 = all swaps, 0.5 = 50% chance).
        // - min_y_buffer_zone (integer, default: 0): Y-range below min_y where swap probability decreases (e.g., 8 extends from min_y to min_y-8).
        // - max_y_buffer_zone (integer, default: 0): Y-range above max_y where swap probability decreases (e.g., 8 extends from max_y to max_y+8).
        // - ignore_block_properties (boolean, default: false): Whether to ignore block properties when matching the old state (true = match block type only, false = match specified properties).
        
        // Notes:
        // - Block states use Minecraft block IDs and properties (see Minecraft wiki or generated known_states files in 'serverconfig/known_states').
        // - Swaps occur during world generation (unless deferred with defer_swap), retro-generation (if retro_gen is true), or placement (if replace_placement or only_replace_placements is true).
        // - Buffer zones create a gradual transition for natural-looking swaps.
        // - Use defer_swap for blocks that other blocks depend on during generation (e.g., dripstone_block for pointed_dripstone).
        // - Use generate_all_known_states to export all possible block states for reference to 'serverconfig/known_states'.
        """;

    private static final String SWAPPER_EXAMPLE = """
            "swapper": [
                {
                    "old": { "Name": "minecraft:cobblestone" },
                    "new": { "Name": "minecraft:diamond_block" },
                    "block_swap_rand": 0.5
                },
                {
                    "old": {
                        "Name": "minecraft:pointed_dripstone",
                        "Properties": { "thickness": "tip" }
                    },
                    "new": { "Name": "minecraft:purple_terracotta" },
                    "only_replace_placements": true,
                    "min_y": -64,
                    "max_y": 16,
                    "min_y_buffer_zone": 8,
                    "max_y_buffer_zone": 8,
                    "ignore_block_properties": false
                },
                {
                    "old": {
                        "Name": "minecraft:pointed_dripstone",
                        "Properties": { "vertical_direction": "up" }
                    },
                    "new": { "Name": "minecraft:gold_block" },
                    "dimensions_whitelist": ["minecraft:overworld"],
                    "biomes_blacklist": ["minecraft:deep_ocean"],
                    "block_swap_rand": 0.75,
                    "ignore_block_properties": true
                },
                {
                    "old": {
                        "Name": "minecraft:oak_log",
                        "Properties": { "axis": "z" }
                    },
                    "new": { "Name": "minecraft:diamond_block" },
                    "replace_placement": true,
                    "ignore_block_properties": false
                },
                {
                    "old": { "Name": "minecraft:oak_log" },
                    "new": { "Name": "minecraft:emerald_block" },
                    "only_replace_placements": true,
                    "ignore_block_properties": true
                },
                {
                    "old": { "Name": "minecraft:dripstone_block" },
                    "new": { "Name": "minecraft:basalt" },
                    "min_y": -64,
                    "max_y": 16,
                    "min_y_buffer_zone": 8,
                    "max_y_buffer_zone": 8,
                    "ignore_block_properties": false,
                    "defer_swap": true
                }
            ]
            """;

    private static final Codec<BlockSwapConfig> RAW_CODEC = RecordCodecBuilder.create(builder ->
            builder.group(
                    CommentedCodec.of(Swapper.SWAP_ENTRY_CODEC.get(), "swapper", "A list of swap entries specifying the old and new block states, placement behavior, Y range, dimensions, biomes, randomization, buffer zones, property matching, and deferred swapping.\nExample:\n" + SWAPPER_EXAMPLE, List.of()).fieldOf("swapper").forGetter(BlockSwapConfig::swapEntries),
                    CommentedCodec.of(Codec.BOOL, "retro_gen", "Whether blocks are replaced in existing chunks.", false).fieldOf("retro_gen").forGetter(BlockSwapConfig::retroGen),
                    CommentedCodec.of(Codec.BOOL, "generate_all_known_states", "Generates all block states for all blocks in the registry.", false).fieldOf("generate_all_known_states").forGetter(BlockSwapConfig::generateAllKnownStates),
                    CommentedCodec.of(Codec.BOOL, "verbose_logging", "Enable verbose debug logging for block swaps.", false).fieldOf("verbose_logging").forGetter(BlockSwapConfig::verboseLogging)
            ).apply(builder, (swapEntries, retroGen, generateAllKnownStates, verboseLogging) -> {
                BlockSwap.LOGGER.info("Deserialized BlockSwapConfig: swapEntries={}, retroGen={}, generateAllKnownStates={}, verboseLogging={}", swapEntries, retroGen, generateAllKnownStates, verboseLogging);
                return new BlockSwapConfig(swapEntries, retroGen, generateAllKnownStates, verboseLogging);
            })
    );

    public static final Codec<BlockSwapConfig> CODEC = RAW_CODEC.flatXmap(verifyConfig(), verifyConfig());

    public boolean contains(BlockState state) {
        for (Swapper.SwapEntry entry : swapEntries) {
            BlockState oldState = entry.oldState();
            if (state.getBlock() != oldState.getBlock()) {
                continue;
            }
            boolean propertiesMatch = true;
            if (!entry.ignoreBlockProperties() && !oldState.getValues().isEmpty()) {
                for (Property<?> property : oldState.getValues().keySet()) {
                    if (!state.hasProperty(property) || !state.getValue(property).equals(oldState.getValue(property))) {
                        propertiesMatch = false;
                        break;
                    }
                }
            }
            if (propertiesMatch) {
                return true;
            }
        }
        return false;
    }

    private static Function<BlockSwapConfig, DataResult<BlockSwapConfig>> verifyConfig() {
        return blockSwapConfig -> {
            StringBuilder errors = new StringBuilder();
            for (Swapper.SwapEntry entry : blockSwapConfig.swapEntries) {
                BlockState oldState = entry.oldState();
                BlockState newState = entry.newState();
                // Check for circular references
                if (blockSwapConfig.swapEntries.stream().anyMatch(e -> e.oldState().getBlock() == newState.getBlock())) {
                    errors.append("Circular reference detected: ").append(newState.toString()).append("\n");
                }
                // Validate Y range
                if (entry.minY() > entry.maxY()) {
                    errors.append("Invalid Y range for entry: min_y (").append(entry.minY()).append(") > max_y (").append(entry.maxY()).append(")\n");
                }
                // Validate buffer zones
                if (entry.minYBufferZone() < 0) {
                    errors.append("Invalid min_y_buffer_zone: ").append(entry.minYBufferZone()).append(" (must be non-negative)\n");
                }
                if (entry.maxYBufferZone() < 0) {
                    errors.append("Invalid max_y_buffer_zone: ").append(entry.maxYBufferZone()).append(" (must be non-negative)\n");
                }
                // Validate dimension IDs
                for (String dimension : entry.dimensions_whitelist()) {
                    if (!ResourceLocation.isValidResourceLocation(dimension)) {
                        errors.append("Invalid dimension ID in dimensions_whitelist: ").append(dimension).append("\n");
                    }
                }
                for (String dimension : entry.dimensions_blacklist()) {
                    if (!ResourceLocation.isValidResourceLocation(dimension)) {
                        errors.append("Invalid dimension ID in dimensions_blacklist: ").append(dimension).append("\n");
                    }
                }
                // Validate biome IDs
                for (String biome : entry.biomes_whitelist()) {
                    if (!ResourceLocation.isValidResourceLocation(biome)) {
                        errors.append("Invalid biome ID in biomes_whitelist: ").append(biome).append("\n");
                    }
                }
                for (String biome : entry.biomes_blacklist()) {
                    if (!ResourceLocation.isValidResourceLocation(biome)) {
                        errors.append("Invalid biome ID in biomes_blacklist: ").append(biome).append("\n");
                    }
                }
            }

            if (!errors.isEmpty()) {
                return DataResult.error(() -> errors.toString());
            }
            return DataResult.success(blockSwapConfig);
        };
    }

    public static BlockSwapConfig getConfig(BlockSwapConfig server) {
        CONFIG = server;
        return CONFIG;
    }

    public static BlockSwapConfig getConfig(boolean reload) {
        if (CONFIG == null || reload) {
            Path configPath = BlockSwap.CONFIG_PATH.resolve("block_swap.json5");

            File configFile = configPath.toFile();
            if (!configFile.exists()) {
                Path defaultConfigPath = BlockSwap.DEFAULT_CONFIG_PATH.resolve("block_swap.json5");
                File defaultConfigFile = defaultConfigPath.toFile();
                BlockSwap.LOGGER.info("Checking for default config at: {}", defaultConfigPath);
                if (defaultConfigFile.exists() && defaultConfigFile.isFile()) {
                    try {
                        Files.createDirectories(configPath.getParent());
                        Files.copy(defaultConfigPath, configPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        BlockSwap.LOGGER.info("Successfully copied default config from {} to {}", defaultConfigPath, configPath);
                    } catch (Exception e) {
                        BlockSwap.LOGGER.error("Failed to copy default config from {} to {}: {}", defaultConfigPath, configPath, e.getMessage());
                        // Generate default config as fallback
                        JanksonUtil.createConfig(configPath, BlockSwapConfig.CODEC, CONFIG_HEADER, new Object2ObjectOpenHashMap<>(), JanksonJsonOps.INSTANCE, BlockSwapConfig.DEFAULT);
                    }
                } else {
                    BlockSwap.LOGGER.info("No default config found at {}. Generating new config at {}", defaultConfigPath, configPath);
                    JanksonUtil.createConfig(configPath, BlockSwapConfig.CODEC, CONFIG_HEADER, new Object2ObjectOpenHashMap<>(), JanksonJsonOps.INSTANCE, BlockSwapConfig.DEFAULT);
                }
            }
            try {
                CONFIG = JanksonUtil.readConfig(configPath, BlockSwapConfig.CODEC, JanksonJsonOps.INSTANCE);
                BlockSwap.LOGGER.info("Loaded config from: {}", configPath);
            } catch (Exception e) {
                BlockSwap.LOGGER.error("Failed to load config from {}: {}. Using default config.", configPath, e.getMessage());
                CONFIG = BlockSwapConfig.DEFAULT;
            }
        }
        return CONFIG;
    }

    private static BlockSwapConfig CONFIG = null;
}