
<img width="1213" height="186" alt="block_swap_advanced_edition" src="https://github.com/user-attachments/assets/6639f56f-2127-405a-83a6-7f13e9d92bb1" />

# Block Swap Advanced
'Block Swap Advanced' is a utility / world gen mod which provides players with a way to modify the blocks that make up their world by swapping them around: Control swaps based on block properties, Control the dimension, biome, or structure where swaps occur, Limit swaps to world generation or player placement, Limit swaps to a specific height range, Add some randomness or gradiation, and more...

<img width="1168" height="748" alt="block_swap_demo" src="https://github.com/user-attachments/assets/e8c0f865-1232-48f2-8e71-3f85f4b7b8d4" />

# Block Swapping Configuration
By modifying a config file called block_swap.json5, players are able to swap any number of blocks from one type into another using a variety of different options.

The config file is stored in 'serverconfig/block_swap.json5' for each world (e.g., 'world/serverconfig/block_swap.json5')

Players can set up a global block swap template that will be copied to all newly generated worlds. This is done by placing a config in the 'defaultconfigs' folder (e.g. 'defaultconfigs/block_swap.json5')
        
## Available 'swapper' Options
These are all the currently availible options that can be used to define how blocks get swapped.
### Required 'core' options:
- old (BlockState, required): The block that will get replaced (e.g., {"Name": "minecraft:cobblestone"}).
- new (BlockState, required): The new block to replace the old block (e.g., {"Name": "minecraft:diamond_block"}).

        *Note - Block Swap Advanced supports block properties:
        (e.g., {"Name": "minecraft:pointed_dripstone", "Properties": {"thickness": "tip"}}).

### Optional 'core' options:
- replace_placement (boolean, default: true): Whether to swap blocks when placed by players or entities. Ignored if only_replace_placements is true.

- min_y (integer, default: world min height): Minimum Y level for swaps. Don't use unless setting a value other than the min world height.
- max_y (integer, default: world max height): Maximum Y level for swaps. Don't use unless setting a value other than the max world height.

- block_swap_rand (float, 0.0 to 1.0, default: 1.0): Randomization factor for swaps (0.0 = no swaps, 1.0 = all swaps, 0.5 = 50% chance).

- min_y_buffer_zone (integer, default: 0): Y-range below min_y where swap probability decreases (e.g., 8 extends from min_y to min_y-8).
- max_y_buffer_zone (integer, default: 0): Y-range above max_y where swap probability decreases (e.g., 8 extends from max_y to max_y+8).

- ignore_block_properties (boolean, default: false): Whether to ignore block properties when matching the old state (true = match block type only, false = match specified properties).

### 'filter' options:
- dimensions_whitelist (list of strings, default: []): Dimension IDs where swaps apply (e.g., ["minecraft:overworld"]).
- dimensions_blacklist (list of strings, default: []): Dimension IDs where swaps are prevented (e.g., ["minecraft:the_nether"]).

- biomes_whitelist (list of strings, default: []): Biome IDs where swaps apply (e.g., ["minecraft:plains"]).
- biomes_blacklist (list of strings, default: []): Biome IDs where swaps are prevented (e.g., ["minecraft:deep_ocean"]).

- structures_whitelist (list<string>, default: []): Structure IDs where swaps apply (e.g., ["minecraft:village_plains"]).
- structures_blacklist (list<string>, default: []): Structure IDs where swaps are prevented (e.g., ["minecraft:mineshaft"]).
  
### Boolean flags:
- only_replace_placement (boolean, default: false): Whether block swaps should be limited to ONLY occur for player block placement (skip generation/retro_gen/redo_gen).
- defer_swap (boolean, default: false): Whether to delay swapping until after chunk generation features complete, allowing dependent blocks to generate (e.g., pointed_dripstone depends on dripstone_block being generated).

# Notes
- An example 'block_swap.json5' can be found in the mod's config folder (block_swap_advanced). This can be used as a template in 'defaultconfig'
- All fields are optional unless specified. Omitted fields use default internal values.
- Block states use block IDs and properties. Set 'generate_block_info' to 'true' to generate a list of all block information for reference).
- To have swaps occur during world generation, use 'retro_gen' along with putting a template block_swap.json5 in 'defaultconfig'.
- When using 'retro_gen', upon leaving and rejoining the world, defined block swaps will occur once for all chunks that haven't been processed.
- To have swaps reload every time the world is loaded, use 'redo_gen'.
- When using 'min_y_buffer' or 'max_y_buffer', buffer zones create a gradual transition for more natural-looking block swaps.
- Use 'defer_swap' for blocks that other blocks depend on during generation (for example, if you modify dripstone_block without defer_swap, pointed_dripstone won't generate. To avoid this, use defer_swap).
- When swapping occurs for a large amount of blocks, there may be lag during world load. You can use 'chunk_swap_range' to limit block swapping to only occur within a certain range of players to help with preformance.

 - Report issues or request features via the mod's github (https://github.com/PotionSeeker/Block-Swap-Advanced).
 - Check logs (latest.log) with verbose_logging enabled for debugging. Be careful as using 'verbose_logging' can cause lag due to adding information to latest.log.

# Warning
- Block Swap Advanced can be used to completely change your world, but if you aren't careful, it could also ruin everything. Make a backup of your world before applying any changes if you aren't sure.

<img width="425" height="315" alt="midas_house_1" src="https://github.com/user-attachments/assets/85a51164-94cf-400e-9886-97dedd0fac47" />

# ToDo List
- Add new config option: conditional_swap - swap blocks only when conditional properties are met:

        (e.g., swap basalt with pointed_dripstone, but only if there is air above or below)
        (e.g., swap sand with glass, but only if there is lava in any direction around it)
- Update to 1.21.1 and more?
- Backport to 1.19.2 and less?
- Add more swap configurations?

# Credit
- Block Swap Advanced is a rewrite and expansion of Corgi_Taco's 'Block Swap' mod and as such it uses the same license
- Credit to Corgi Taco and J.T. McQuigg for their work on the original 'Block Swap' mod
