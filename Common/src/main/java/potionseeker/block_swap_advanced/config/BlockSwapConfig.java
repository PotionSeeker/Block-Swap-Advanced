package potionseeker.block_swap_advanced.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import potionseeker.block_swap_advanced.BlockSwap;
import potionseeker.block_swap_advanced.serialization.CommentedCodec;
import potionseeker.block_swap_advanced.serialization.JanksonJsonOps;
import potionseeker.block_swap_advanced.serialization.JanksonUtil;
import potionseeker.block_swap_advanced.swapper.Swapper;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public record BlockSwapConfig(
        List<Swapper.SwapEntry> swapEntries,
        boolean retroGen,
        boolean redoGen,
        boolean generateBlockInfo,
        boolean verboseLogging,
        int chunkSwapRange
) {
    public static final BlockSwapConfig DEFAULT = new BlockSwapConfig(List.of(), true, false, false, false, -1);

    public static final Codec<BlockSwapConfig> CODEC = RecordCodecBuilder.create(builder ->
            builder.group(
                    Swapper.SWAP_ENTRY_CODEC.get().fieldOf("swapper").forGetter(BlockSwapConfig::swapEntries),
                    Codec.BOOL.optionalFieldOf("retro_gen", true).forGetter(BlockSwapConfig::retroGen),
                    Codec.BOOL.optionalFieldOf("redo_gen", false).forGetter(BlockSwapConfig::redoGen),
                    Codec.BOOL.optionalFieldOf("generate_block_info", false).forGetter(BlockSwapConfig::generateBlockInfo),
                    Codec.BOOL.optionalFieldOf("verbose_logging", false).forGetter(BlockSwapConfig::verboseLogging),
                    Codec.INT.optionalFieldOf("chunk_swap_range", -1).forGetter(BlockSwapConfig::chunkSwapRange)
            ).apply(builder, BlockSwapConfig::new)
    );

    private static BlockSwapConfig CONFIG = null;

    public static BlockSwapConfig getConfig(boolean loadFromFile) {
        if (!loadFromFile && CONFIG != null) {
            BlockSwap.LOGGER.debug("Returning cached config: retroGen={}, redoGen={}, swapEntries={}", CONFIG.retroGen(), CONFIG.redoGen(), CONFIG.swapEntries().size());
            return CONFIG;
        }

        Path configPath = BlockSwap.CONFIG_PATH != null
                ? BlockSwap.CONFIG_PATH.resolve("block_swap.json5")
                : Paths.get("config").resolve(BlockSwap.MOD_ID).resolve("block_swap.json5");

        try {
            Files.createDirectories(BlockSwap.DEFAULT_CONFIG_PATH);
            BlockSwap.LOGGER.debug("Ensured defaultconfigs folder exists at: {}", BlockSwap.DEFAULT_CONFIG_PATH);

            Files.createDirectories(configPath.getParent());
            if (!Files.exists(configPath)) {
                Path defaultConfigPath = BlockSwap.DEFAULT_CONFIG_PATH.resolve("block_swap.json5");
                if (Files.exists(defaultConfigPath)) {
                    Files.copy(defaultConfigPath, configPath);
                    BlockSwap.LOGGER.info("Copied default config from {} to: {}", defaultConfigPath, configPath);
                } else {
                    JanksonUtil.createConfig(configPath, CODEC, CONFIG_HEADER, new Object2ObjectOpenHashMap<>(), JanksonJsonOps.INSTANCE, DEFAULT);

                    JanksonUtil.createConfig(defaultConfigPath, CODEC, CONFIG_HEADER, new Object2ObjectOpenHashMap<>(), JanksonJsonOps.INSTANCE, DEFAULT);
                    BlockSwap.LOGGER.info("Created default config at: {} and {}", configPath, defaultConfigPath);
                }
            }
        } catch (Exception e) {
            BlockSwap.LOGGER.error("Failed to copy or create config at {}: {}", configPath, e.getMessage());
            CONFIG = BlockSwapConfig.DEFAULT;
            Swapper.updateConfig(CONFIG);
            return CONFIG;
        }

        try {
            DataResult<BlockSwapConfig> result = JanksonUtil.readConfigWithResult(configPath, BlockSwapConfig.CODEC, JanksonJsonOps.INSTANCE);
            if (result.result().isPresent()) {
                CONFIG = result.result().get();
                for (int i = 0; i < CONFIG.swapEntries().size(); i++) {
                    Swapper.SwapEntry entry = CONFIG.swapEntries().get(i);
                    if (entry.newState().isAir()) {
                        String errorMsg = String.format("Invalid 'new' block state at swapper[%d]: %s is air. Config will not be loaded.",
                                i, entry.newState().getBlock().toString());
                        BlockSwap.LOGGER.error(errorMsg);
                        annotateConfigWithError(configPath, "swapper[" + i + "].core.new.Name", errorMsg);
                        CONFIG = BlockSwapConfig.DEFAULT;
                        Swapper.updateConfig(CONFIG);
                        return CONFIG;
                    }
                }
                BlockSwap.LOGGER.info("Loaded config from: {}, retroGen={}, redoGen={}, swapEntries={}", configPath, CONFIG.retroGen(), CONFIG.redoGen(), CONFIG.swapEntries().size());
                Swapper.updateConfig(CONFIG);
                removeErrorAnnotations(configPath);
            } else {
                String errorMsg = result.error().map(error -> error.message()).orElse("Unknown parsing error");
                if (!errorMsg.contains("No key swapper in MapLike[{}]")) {
                    BlockSwap.LOGGER.error("Failed to load config from {}: {}. Using default config.", configPath, errorMsg);
                    annotateConfigWithError(configPath, "swapper", errorMsg);
                }
                CONFIG = BlockSwapConfig.DEFAULT;
                Swapper.updateConfig(CONFIG);
            }
        } catch (Exception e) {
            String errorMsg = e.getMessage();
            if (!errorMsg.contains("No key swapper in MapLike[{}]")) {
                BlockSwap.LOGGER.error("Failed to load config from {}: {}. Using default config.", configPath, errorMsg);
                annotateConfigWithError(configPath, "swapper", errorMsg);
            }
            CONFIG = BlockSwapConfig.DEFAULT;
            Swapper.updateConfig(CONFIG);
        }
        return CONFIG;
    }

    private static void annotateConfigWithError(Path configPath, String fieldPath, String errorMsg) {
        try {
            List<String> lines = Files.readAllLines(configPath);
            int errorSectionIndex = -1;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).trim().equals("Generated Errors:")) {
                    errorSectionIndex = i;
                    break;
                }
            }

            if (errorSectionIndex == -1) {
                for (int i = lines.size() - 1; i >= 0; i--) {
                    if (lines.get(i).trim().equals("*/")) {
                        lines.add(i, "Generated Errors:");
                        lines.add(i + 1, "");
                        errorSectionIndex = i;
                        break;
                    }
                }
            }

            String formattedError = "// ERROR: " + errorMsg + " (Field: " + fieldPath + ")";
            if (errorSectionIndex != -1) {
                lines.add(errorSectionIndex + 1, formattedError);
            } else {
                lines.add(formattedError);
            }

            Files.write(configPath, String.join("\n", lines).getBytes());
            BlockSwap.LOGGER.info("Annotated config file {} with error: {}", configPath, errorMsg);
        } catch (IOException e) {
            BlockSwap.LOGGER.error("Failed to annotate config file {} with error: {}", configPath, e.getMessage());
        }
    }

    private static void removeErrorAnnotations(Path configPath) {
        try {
            List<String> lines = Files.readAllLines(configPath);
            List<String> filteredLines = new ArrayList<>();
            boolean inErrorSection = false;
            int errorsRemoved = 0;

            for (String line : lines) {
                if (line.trim().equals("Generated Errors:")) {
                    inErrorSection = true;
                    continue;
                }
                if (inErrorSection && line.trim().startsWith("// ERROR")) {
                    errorsRemoved++;
                    continue;
                }
                if (inErrorSection && line.trim().equals("--------------------------")) {
                    inErrorSection = false;
                    continue;
                }
                filteredLines.add(line);
            }

            if (errorsRemoved > 0) {
                Files.write(configPath, String.join("\n", filteredLines).getBytes());
                BlockSwap.LOGGER.info("Removed {} error annotations from config file: {}", errorsRemoved, configPath);
            } else {
                BlockSwap.LOGGER.debug("No error annotations found in config file: {}", configPath);
            }
        } catch (IOException e) {
            BlockSwap.LOGGER.error("Failed to remove error annotations from config file {}: {}", configPath, e.getMessage());
        }
    }

    public boolean contains(BlockState state) {
        for (Swapper.SwapEntry entry : swapEntries) {
            BlockState oldState = entry.oldState();
            if (oldState.getBlock() == state.getBlock()) {
                if (entry.ignoreBlockProperties()) {
                    return true;
                }
                boolean propertiesMatch = true;
                for (Property<?> property : oldState.getValues().keySet()) {
                    if (!state.hasProperty(property) || !state.getValue(property).equals(oldState.getValue(property))) {
                        propertiesMatch = false;
                        break;
                    }
                }
                if (propertiesMatch) {
                    return true;
                }
            }
        }
        return false;
    }

    public static final String CONFIG_HEADER = """
        /*
            Block Swap Advanced Configuration
            ---------------------------------
            This file configures block swapping behavior for Block Swap Advanced.

            Configuration Options:
            - swapper: List of block swap rules (e.g., swapping minecraft:cobblestone to minecraft:diamond_block).
            - retro_gen: If true, applies swaps to existing unprocessed chunks when the world loads (default: true).
            - redo_gen: If true, re-applies swaps to any chunks when the config changes (resets processed chunks).
            - generate_block_info: If true, generates block info files for reference (useful for modded blocks).
            - verbose_logging: If true, logs detailed swap operations for debugging.
            - chunk_swap_range: Chunks around players to process (-1 = all loaded, 0 = none except new/placed).

            Swap Entry Fields:
            Core (required):
            - old (block state, e.g., {"Name": "minecraft:cobblestone"}): Block to replace.
            - new (block state, e.g., {"Name": "minecraft:diamond_block"}): Block to swap to.
            - replace_placement (bool, default: true): Swap when block is placed (e.g., by player).
            - min_y (int, default: min world height): Minimum Y level for swap.
            - max_y (int, default: max world height): Maximum Y level for swap.
            - block_swap_rand (float, 0.0-1.0, default: 1.0): Swap probability.
            - min_y_buffer_zone (int, default: 0): Y-transition zone below min_y (probability ramps).
            - max_y_buffer_zone (int, default: 0): Y-transition zone above max_y.
            - ignore_block_properties (bool, default: false): Ignore properties (e.g., facing) when matching.

            Filter (optional):
            - dimensions_whitelist (list<string>, default: []): Allow in these (e.g., ["minecraft:overworld"]). Empty = all unless blacklisted.
            - dimensions_blacklist (list<string>, default: []): Block in these (e.g., ["minecraft:the_nether"]).
            - biomes_whitelist (list<string>, default: []): Allow in these (e.g., ["minecraft:plains"]). Empty = all unless blacklisted.
            - biomes_blacklist (list<string>, default: []): Block in these (e.g., ["minecraft:desert"]).
            - structures_whitelist (list<string>, default: []): Allow in these (e.g., ["minecraft:village_plains"]). Empty = all unless blacklisted.
            - structures_blacklist (list<string>, default: []): Block in these (e.g., ["minecraft:mineshaft"]).
            
            Boolean Flags (outside core/filter):
            - only_replace_placements (bool, default: false): Swap ONLY on player block placement (skip generation/retro-gen).
            - defer_swap (bool, default: false): Delay swap until after generation features (for dependencies, e.g., pointed_dripstone on dripstone_block).

            Basic Example:
            
            {
              "swapper": [
                {
                  "core": {
                    "old": {"Name": "minecraft:grass_block"},
                    "new": {"Name": "minecraft:dirt"},
                    "replace_placement": true
                  }
                }
              ],
              "retro_gen": true
            }

            Generated Errors: 
            *Any errors in the formatting of the block swap config will be logged here*
        */
        """;
}