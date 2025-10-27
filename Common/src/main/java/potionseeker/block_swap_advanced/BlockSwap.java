package potionseeker.block_swap_advanced;

import potionseeker.block_swap_advanced.config.BlockSwapConfig;
import potionseeker.block_swap_advanced.config.MissingBlockIDsConfig;
import potionseeker.block_swap_advanced.serialization.JanksonJsonOps;
import potionseeker.block_swap_advanced.serialization.JanksonUtil;
import potionseeker.block_swap_advanced.swapper.Swapper;
import net.minecraft.server.level.ServerLevel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class BlockSwap {
    public static final String MOD_ID = "block_swap_advanced";
    public static Logger LOGGER = LogManager.getLogger();
    public static Path CONFIG_PATH = null;
    public static Path DEFAULT_CONFIG_PATH = Paths.get("defaultconfigs");

    private static final String README_CONTENT = """
        {/*
         Block Swap Advanced
         -------------------
         *This file provides an overview of Block Swap Advanced and instructions for configuration.
        
         Overview:
         BlockSwap is a mod that allows you to swap one block type for another. This can be set to occur during world generation, block placement, or based on processed or unprocessed chunks.
         Advanced features include property matching, Y-range restrictions, dimension filters, biome filters, structure filters, randomization, buffer zones, and deferred swapping for dependent blocks.
        
         Configuration:
         - Config Files:
           - block_swap.json5: Defines block swap rules (e.g., swapping minecraft:cobblestone to minecraft:diamond_block).
           - missing_block_ids.json5: Maps invalid or missing block IDs to valid ones (e.g., for compatibility with old worlds or other mods).
         
         - File Locations:
           - Per-World Configs: Stored in each world's 'serverconfig' folder (e.g., 'world/serverconfig/block_swap.json5', 'world/serverconfig/missing_block_ids.json5').
           - Default Configs: Stored in the Minecraft instance's 'defaultconfigs' folder (e.g., '.minecraft/defaultconfigs/block_swap.json5', '.minecraft/defaultconfigs/missing_block_ids.json5').
           - Example Config: An example 'block_swap.json5' is generated in 'config/block_swap_advanced/' alongside the readme. Copy this to 'defaultconfigs' to use it for new worlds.
         
         - How It Works:
           - When a new world is created, the mod checks for 'block_swap.json5' and 'missing_block_ids.json5' in 'defaultconfigs'.
           - If present, these files are copied to the world's 'serverconfig' folder.
           - If absent, default configs are generated in both 'defaultconfigs' and the world's 'serverconfig' folder.
           - Each world uses its own 'serverconfig' configs, allowing unique block swap rules per world.
           - The example config in 'config/block_swap_advanced/block_swap.json5' provides a starting point for customization.
         
         - Editing Configs:
           - Place a customized 'block_swap.json5' or 'missing_block_ids.json5' in 'defaultconfigs' to apply to all newly created worlds.
           - Modify 'world/serverconfig/block_swap.json5' to change rules for an existing world.
            
            Configuration Structure:
        
            Configuration Options:
            - swapper: List of block swap rules.
            - retro_gen: If true, applies swaps to unprocessed chunks when the world loads (default: true).
            - redo_gen: If true, re-applies swaps to all chunks when the config changes.
            - generate_block_info: If true, generates block info files for reference.
            - verbose_logging: If true, block swaps will be added to the latest.log.
            - chunk_swap_range: Limits swapping to chunks within this range from players (-1 for all loaded chunks, 0 to disable, >1 for chunk range).
        
             'core' Object (required for swaps):
             - old (BlockState, required): Block to replace. e.g., {"Name": "minecraft:cobblestone"}.
             - new (BlockState, required): Replacement block. e.g., {"Name": "minecraft:diamond_block"}.
             - Players can define Properties and Partial properties of relevant blocks. e.g., {"Name": "minecraft:oak_log", "Properties": {"axis": "y"}}.
             
             'core' Object (optional, defaults to internal values):
             - replace_placement (bool, default: true): Swap on player/entity placement.
             - min_y (int, default: min world height): Minimum Y for swaps. Don't use unless setting a value other than the min world height.
             - max_y (int, default: max world height): Maximum Y for swaps. Don't use unless setting a value other than the max world height.
             - block_swap_rand (float 0.0-1.0, default: 1.0): Swap probability (1.0 = always, 0.5 = 50%, 0.0 = never).
             - min_y_buffer_zone (int, default: 0): Gradual fade below min_y (e.g., 8 = linear drop from 100% at min_y to 0% at min_y-8).
             - max_y_buffer_zone (int, default: 0): Gradual fade above max_y (similar to min_y).
             - ignore_block_properties (bool, default: false): Match block type only (true = ignore variants like 'axis' or 'lit'; false = match specified properties).
        
             'filter' Object (optional, defaults to empty lists = no filtering):
             - dimensions_whitelist (list<string>, default: []): Allow swaps in these dimensions (e.g., ["minecraft:overworld"]).
             - dimensions_blacklist (list<string>, default: []): Prevent swaps in these dimensions (e.g., ["minecraft:the_nether"]).
             - biomes_whitelist (list<string>, default: []): Allow in these biomes (e.g., ["minecraft:plains"]).
             - biomes_blacklist (list<string>, default: []): Prevent swaps in these biomes (e.g., ["minecraft:deep_ocean"]).
             - structures_whitelist (list<string>, default: []): Allow swaps in these structures (e.g., ["minecraft:village_plains"]).
             - structures_blacklist (list<string>, default: []): Prevent swaps in these structures (e.g., ["minecraft:mineshaft"]).
        
         Boolean Flags (outside core/filter):
         - only_replace_placements (boolean, default: false): Whether block swaps should be limited to ONLY occur for player block placement (skip generation/retro-gen).
         - defer_swap (boolean, default: false): Whether to delay swapping until after chunk generation features complete, allowing dependent blocks to generate (e.g., pointed_dripstone depends on dripstone_block being generated).
        
         Full Default Config 
         - This config shows how all the available options should be arranged:
            {
                "swapper":
                [
                    { "core":{
                        "old": {},
                        "new": {},
                        "replace_placement": true,
                        "min_y": *,
                        "max_y": *,
                        "block_swap_rand": 1.0,
                        "min_y_buffer_zone": 0,
                        "max_y_buffer_zone": 0,
                        "ignore_block_properties": false
                    },
                    "filter":{
                        "dimensions_whitelist": [],
                        "dimensions_blacklist": [],
                        "biomes_whitelist": [],
                        "biomes_blacklist": [],
                        "structures_whitelist": [],
                        "structures_blacklist": []
                    },
                    "only_replace_placements": false,
                    "defer_swap": false
                    }
                ],
                  "retro_gen": true,
                  "redo_gen": false,
                  "generate_block_info": false,
                  "verbose_logging": false,
                  "chunk_swap_range": -1
            }

         Examples:

         2. Basic Swap 
         - This config will swap cobblestone for blocks of diamond:
            {
              "swapper": [
                {
                  "core": {
                    "old": {"Name": "minecraft:cobblestone"},
                    "new": {"Name": "minecraft:diamond_block"},
                    "replace_placement": true
                  }
                }
              ],
              "retro_gen": true,
              "redo_gen": false,
              "generate_block_info": false
            }

         3. Placement-Only
         - This config will swap cobblestone for diamond blocks, but ONLY when the player places blocks of cobblestone:
            {
              "swapper": [
                {
                  "core": {
                    "old": {"Name": "minecraft:cobblestone"},
                    "new": {"Name": "minecraft:diamond_block"},
                    "replace_placement": true
                  },
                  "only_replace_placements": true
                }
              ],
              "retro_gen": false,
              "redo_gen": false,
              "generate_block_info": false
            }
        
         4. Swapping Blocks that have properties
         - This config will swap 'oak_logs' rotated on the Y axis for 'basalt' rotated on the Y axis
            {
              "swapper": [
                {
                  "core": {
                    "old": {"Name": "minecraft:oak_log", "Properties": {"axis": "y"}},
                    "new": {"Name": "minecraft:basalt", "Properties": {"axis": "y"}},
                    "replace_placement": true
                  }
                }
              ],
              "retro_gen": true,
              "redo_gen": false,
              "generate_block_info": false
            }
        
         5. Defer Swap
         - To replace 'dripstone_block' and 'pointed_dripstone', you must use defer_swap on 'dripstone_block', otherwise the 'pointed_dripstone' won't generate
         {
             "swapper": [
                 {
                     "core": {
                         "old": {"Name": "minecraft:dripstone_block"},
                         "new": {"Name": "minecraft:basalt"},
                         "ignore_block_properties": false,
                         "defer_swap": true
                     }
                 },
                 {
                     "core": {
                         "old": {"Name": "minecraft:pointed_dripstone"},
                         "new": {"Name": "new_caves:pointed_basalt"},
                         "ignore_block_properties": true
                     }
                 }
             ],
             "retro_gen": true,
             "redo_gen": false,
             "generate_block_info": false
         }

         6. Height Range
         - This config will swap stone for dirt within the world height range of 54 to 64
         {
             "swapper": [
                 {
                     "core": {
                         "old": {"Name": "minecraft:stone"},
                         "new": {"Name": "minecraft:dirt"},
                         "min_y": 54,
                         "max_y": 64
                     }
                 }
             ],
             "retro_gen": true,
             "redo_gen": false
         }

         Notes:
         - Configs are JSON5 format, supporting comments and flexible syntax.
         - Invalid configs (e.g., circular references, invalid block IDs) will be logged within the config file.
         - To have a world swap blocks during world generation, make sure either 'retro_gen' or 'redo_gen' are set to 'true' in a preset config using 'defaultconfigs'.
         - Using 'verbose_logging' can cause a large degree of lag and should only be used for debugging.
        
         Support:
         - Report issues or request features via the mod's github[](https://github.com/PotionSeeker/Block-Swap-Advanced).
         - Check logs (latest.log) with verbose_logging enabled for debugging.
        */
        """;

    public BlockSwap() {
        // Removed initialization because
    }

    public static void init(Path worldPath) {
        // Set paths for world-specific config handling
        CONFIG_PATH = worldPath.resolve("serverconfig");
        try {
            Files.createDirectories(DEFAULT_CONFIG_PATH);
            BlockSwap.LOGGER.debug("Ensured defaultconfigs folder exists at: {}", DEFAULT_CONFIG_PATH);
        } catch (IOException e) {
            BlockSwap.LOGGER.error("Failed to create defaultconfigs folder at {}: {}", DEFAULT_CONFIG_PATH, e.getMessage());
        }
        generateReadmeAndExampleConfig();
    }

    public static void initMod() {
        // Called during mod initialization to ensure defaultconfigs folder exists
        try {
            Files.createDirectories(DEFAULT_CONFIG_PATH);
            BlockSwap.LOGGER.debug("Ensured defaultconfigs folder exists at: {}", DEFAULT_CONFIG_PATH);
        } catch (IOException e) {
            BlockSwap.LOGGER.error("Failed to create defaultconfigs folder at {}: {}", DEFAULT_CONFIG_PATH, e.getMessage());
        }
        generateReadmeAndExampleConfig();
    }

    private static void generateReadmeAndExampleConfig() {
        Path configDir = Paths.get("config").resolve(MOD_ID);
        try {
            Files.createDirectories(configDir);

            // Generate readme
            Path readmePath = configDir.resolve("block_swap_readme.json5");
            if (!Files.exists(readmePath)) {
                Files.writeString(readmePath, README_CONTENT);
                LOGGER.info("Generated block_swap_readme.json5 at {}", readmePath);
            }

            // Generate example config
            Path exampleConfigPath = configDir.resolve("block_swap.json5");
            if (!Files.exists(exampleConfigPath)) {
                JanksonUtil.createConfig(
                        exampleConfigPath,
                        BlockSwapConfig.CODEC,
                        BlockSwapConfig.CONFIG_HEADER,
                        new it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap<>(),
                        JanksonJsonOps.INSTANCE,
                        BlockSwapConfig.DEFAULT
                );
                LOGGER.info("Generated example block_swap.json5 at {}", exampleConfigPath);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to generate readme or example config at {}: {}", configDir, e.getMessage());
        }
    }

    public static ProcessedChunksData getProcessedChunksData(ServerLevel level) {
        return ProcessedChunksData.load(level);
    }
}