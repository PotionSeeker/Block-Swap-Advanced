package potionseeker.block_swap_advanced;

import potionseeker.block_swap_advanced.config.BlockSwapConfig;
import potionseeker.block_swap_advanced.swapper.Swapper;
import net.minecraft.server.level.ServerLevel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class BlockSwap {
    public static final String MOD_ID = "block_swap_advanced";
    public static Logger LOGGER = LogManager.getLogger();
    public static Path CONFIG_PATH = null;
    public static Path DEFAULT_CONFIG_PATH = null;

    private static final String README_CONTENT = """
        {
            // BlockSwap Mod Readme
            // This file provides an overview of the BlockSwap mod and instructions for configuration.
            
            // Overview:
            // BlockSwap is a Minecraft mod that allows you to swap one block type for another during world generation, retro-generation, or block placement.
            // It supports advanced features like property matching, Y-range restrictions, dimension/biome filters, randomization, buffer zones, and deferred swapping for dependent blocks.
            
            // Configuration:
            // - Config Files:
            //   - block_swap.json5: Defines block swap rules (e.g., swapping minecraft:cobblestone to minecraft:diamond_block).
            //   - missing_block_ids.json5: Maps invalid or missing block IDs to valid ones (e.g., for compatibility with old worlds or other mods).
            // - Locations:
            //   - Per-World Configs: Stored in each world's 'serverconfig' folder (e.g., 'world/serverconfig/block_swap.json5', 'world/serverconfig/missing_block_ids.json5').
            //   - Default Configs: Stored in the Minecraft instance's 'defaultconfigs' folder (e.g., '.minecraft/defaultconfigs/block_swap.json5', '.minecraft/defaultconfigs/missing_block_ids.json5').
            // - How It Works:
            //   - When a new world is created, the mod checks for 'block_swap.json5' and 'missing_block_ids.json5' in 'defaultconfigs'.
            //   - If present, these files are copied to the world's 'serverconfig' folder.
            //   - If absent, default configs are generated in the world's 'serverconfig' folder with empty swap rules and mappings.
            //   - Each world uses its own 'serverconfig' configs, allowing unique block swap rules per world.
            // - Editing Configs:
            //   - Place a customized 'block_swap.json5' or 'missing_block_ids.json5' in 'defaultconfigs' to apply to new worlds.
            //   - Modify 'world/serverconfig/block_swap.json5' to change rules for an existing world.
            //   - See 'block_swap.json5' comments for detailed options (e.g., replace_placement, only_replace_placements, defer_swap, ignore_block_properties, min_y, max_y, buffer zones).
            
            // Key Features:
            // - Swap Blocks: Replace blocks during world generation, retro-generation (existing chunks), or placement (e.g., player-placed blocks).
            // - Placement-Only Swaps: Use only_replace_placements: true to swap only blocks placed by players or entities, skipping world generation and retro-generation.
            // - Deferred Swaps: Use defer_swap: true to delay swapping until after chunk generation features complete, allowing dependent blocks (e.g., pointed_dripstone on dripstone_block) to generate.
            // - Property Matching: Swap specific block states (e.g., minecraft:oak_log[axis=z]) or ignore properties to swap all variants.
            // - Y-Range and Buffer Zones: Restrict swaps to specific Y-levels with gradual probability transitions.
            // - Dimension and Biome Filters: Apply swaps only in specific dimensions (e.g., minecraft:overworld) or biomes (e.g., minecraft:plains).
            // - Randomization: Control swap probability (e.g., 50% chance with block_swap_rand: 0.5).
            // - Retro-Generation: Apply swaps to existing chunks (enable with retro_gen: true).
            // - Known States: Generate all possible block states for reference (enable with generate_all_known_states: true).
            
            // Usage Tips:
            // - Place a customized 'block_swap.json5' in 'defaultconfigs' to apply it to all new worlds.
            // - Edit 'world/serverconfig/block_swap.json5' to tweak swaps for a specific world without affecting others.
            // - Use 'verbose_logging: true' in 'block_swap.json5' to debug swaps in the game logs (latest.log).
            // - Check generated 'known_states' files in 'world/serverconfig/known_states' for valid block states.
            
            // Example:
            // To swap minecraft:dripstone_block to minecraft:basalt and minecraft:pointed_dripstone to new_caves:pointed_basalt, deferring the dripstone_block swap to allow pointed_dripstone to generate:
            // 1. Edit '.minecraft/defaultconfigs/block_swap.json5':
            //    ```json5
            //    {
            //        swapper: [
            //            {
            //                old: { Name: "minecraft:dripstone_block" },
            //                new: { Name: "minecraft:basalt" },
            //                min_y: -64,
            //                max_y: 16,
            //                min_y_buffer_zone: 8,
            //                max_y_buffer_zone: 8,
            //                ignore_block_properties: false,
            //                defer_swap: true
            //            },
            //            {
            //                old: { Name: "minecraft:pointed_dripstone" },
            //                new: { Name: "new_caves:pointed_basalt" },
            //                min_y: -64,
            //                max_y: 16,
            //                min_y_buffer_zone: 8,
            //                max_y_buffer_zone: 8,
            //                ignore_block_properties: true
            //            }
            //        ]
            //    }
            //    ```
            // 2. Create a new world; the config will be copied to 'world/serverconfig/block_swap.json5'.
            
            // Notes:
            // - Configs are JSON5 format, supporting comments and flexible syntax.
            // - Invalid configs (e.g., circular references, invalid block IDs) are logged as errors, and defaults are used.
            // - The mod supports Forge and Fabric for Minecraft 1.20.1, working in single-player and multiplayer.
            // - For advanced configs, refer to the comments in 'block_swap.json5' or generated 'known_states' files.
            
            // Support:
            // - Report issues or request features via the mod's repository or community channels.
            // - Check logs (latest.log) with verbose_logging enabled for debugging.
        }
        """;

    public BlockSwap() {
        initConfig(Paths.get("config").resolve(MOD_ID));
    }

    public static void init(Path worldPath) {
        // Set CONFIG_PATH to world-specific serverconfig
        CONFIG_PATH = worldPath.resolve("serverconfig");
        // Set DEFAULT_CONFIG_PATH to .minecraft/defaultconfigs
        DEFAULT_CONFIG_PATH = Paths.get("").resolve("defaultconfigs");
        // Generate readme in config/blockswap
        Path readmePath = Paths.get("config").resolve(MOD_ID).resolve("readme.json5");
        try {
            Files.createDirectories(readmePath.getParent());
            if (!Files.exists(readmePath)) {
                Files.writeString(readmePath, README_CONTENT);
                LOGGER.info("Generated readme at {}", readmePath);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to generate readme at {}: {}", readmePath, e.getMessage());
        }
        initConfig(worldPath);
    }

    private static void initConfig(Path worldPath) {
        if (worldPath != null) {
            BlockSwapConfig config = BlockSwapConfig.getConfig(true);
            Swapper.updateConfig(config);
        }
    }

    public static ProcessedChunksData getProcessedChunksData(ServerLevel level) {
        return ProcessedChunksData.load(level);
    }
}