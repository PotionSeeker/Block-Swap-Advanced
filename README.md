
<img width="1213" height="186" alt="block_swap_advanced_edition" src="https://github.com/user-attachments/assets/8b1e9157-a86e-4b51-a693-cf0f21586579" />

# Block Swap Advanced
'Block Swap Advanced' is a utility / world gen mod which provides players with a way to modify the blocks that make up their world by swapping them around: Control swaps based on block properties, Control the dimension and biome where swaps occur, Limit swaps to world generation or player placement, Limit swaps to a specific height range, Add some randomness or gradiation, and more...

<img width="1168" height="748" alt="block_swap_demo" src="https://github.com/user-attachments/assets/cea3e87a-279e-408c-92f1-0fd600f70500" />

# BlockSwap Configuration
By modifying the BlockSwap Config, players are able to swap any number of blocks from one type into another. This can be set to occur during world generation, retro-generation, or only during block placement.
The config file is stored in 'serverconfig/block_swap.json5' for each world (e.g., 'world/serverconfig/block_swap.json5'), and players can set up a global block swap template that will affect all newly generated worlds by placing their config in the 'defaultconfigs' folder (e.g. 'defaultconfigs/block_swap.json5')
        
## Available 'swapper' Options
- Old Block (BlockState, required): The block that will get replaced (e.g., {"Name": "minecraft:cobblestone"}).
- New Block (BlockState, required): The new block to replace the old block (e.g., {"Name": "minecraft:diamond_block"}).
- Supports block properties (e.g., {"Name": "minecraft:pointed_dripstone", "Properties": {"thickness": "tip"}}).

- replace_placement (boolean, default: false): Whether to swap blocks when placed by players or entities. Ignored if only_replace_placements is true.
- only_replace_placements (boolean, default: false): Whether to swap only blocks placed by players or entities, skipping world generation and retro-generation.
- defer_swap (boolean, default: false): Whether to defer swapping until after chunk generation features complete, allowing dependent blocks (e.g., pointed_dripstone on dripstone_block) to generate.

- min_y (integer, default: world min height, -64): Minimum Y level for swaps. Use Integer.MIN_VALUE for world minimum.
- max_y (integer, default: world max height, 320): Maximum Y level for swaps. Use Integer.MAX_VALUE for world maximum.

- dimensions_whitelist (list of strings, default: []): Dimension IDs where swaps apply (e.g., ["minecraft:overworld"]). Empty allows all unless blacklisted.
- dimensions_blacklist (list of strings, default: []): Dimension IDs where swaps are prevented (e.g., ["minecraft:the_nether"]).

- biomes_whitelist (list of strings, default: []): Biome IDs where swaps apply (e.g., ["minecraft:plains"]). Empty allows all unless blacklisted.
- biomes_blacklist (list of strings, default: []): Biome IDs where swaps are prevented (e.g., ["minecraft:deep_ocean"]).

- block_swap_rand (float, 0.0 to 1.0, default: 1.0): Randomization factor for swaps (0.0 = no swaps, 1.0 = all swaps, 0.5 = 50% chance).
- min_y_buffer_zone (integer, default: 0): Y-range below min_y where swap probability decreases (e.g., 8 extends from min_y to min_y-8).
- max_y_buffer_zone (integer, default: 0): Y-range above max_y where swap probability decreases (e.g., 8 extends from max_y to max_y+8).

- ignore_block_properties (boolean, default: false): Whether to ignore block properties when matching the old state (true = match block type only, false = match specified properties).

# Notes
- All fields are optional unless specified. Omitted fields use default values.
- Block states use Minecraft block IDs and properties (see Minecraft wiki or generated known_states files in 'serverconfig/known_states').
- Swaps occur during world generation (unless deferred with defer_swap), retro-generation (if retro_gen is true), or placement (if replace_placement or only_replace_placements is true).
- Buffer zones create a gradual transition for natural-looking swaps.
- Use defer_swap for blocks that other blocks depend on during generation (for example, if you modify dripstone_block without defer_swap, pointed_dripstone won't generate. If want to modify dripstone_block, and still want pointed_dripstone to generate, use defer_swap).
- Use generate_all_known_states to export all possible block states for reference to 'serverconfig/known_states'.

# Warning
- Block Swap Advanced can be used to completely change your world, but if you aren't careful, it could also ruin everything. Make a backup of your world before applying any changes if you aren't sure.

<img width="425" height="315" alt="midas_house_1" src="https://github.com/user-attachments/assets/c2b738be-a005-4adb-b3a2-e3b4d92451a9" />

# Credit
- 'Block Swap Advanced' is a rewrite and expansion of Corgi_Taco's 'Block Swap' mod and as such it uses the same license

# ToDo
- Update to 1.21.1 and more?
- Backport to 1.19.2 and less?
- Add more swap configurations?
