package potionseeker.block_swap_advanced.swapper;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import potionseeker.block_swap_advanced.BlockSwap;
import potionseeker.block_swap_advanced.config.BlockSwapConfig;
import potionseeker.block_swap_advanced.ProcessedChunksData;
import potionseeker.block_swap_advanced.mixin.access.StateHolderAccess;
import potionseeker.block_swap_advanced.serialization.CodecUtil;
import potionseeker.block_swap_advanced.serialization.CommentedCodec;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateHolder;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

public class Swapper {
    public static final Codec<BlockState> COMMENTED_STATE_CODEC = CodecUtil.BLOCK_STATE_CODEC;

    public record SwapEntry(BlockState oldState, BlockState newState, boolean replacePlacement, int minY, int maxY,
                            List<String> dimensions_whitelist, List<String> dimensions_blacklist,
                            List<String> biomes_whitelist, List<String> biomes_blacklist,
                            float blockSwapRand, int minYBufferZone, int maxYBufferZone,
                            boolean ignoreBlockProperties, boolean only_replace_placements, boolean defer_swap) {
        public static final Codec<SwapEntry> CODEC = RecordCodecBuilder.create(builder ->
            builder.group(
                COMMENTED_STATE_CODEC.fieldOf("old").forGetter(SwapEntry::oldState),
                COMMENTED_STATE_CODEC.fieldOf("new").forGetter(SwapEntry::newState),
                CommentedCodec.of(Codec.BOOL, "replace_placement", "Whether blocks are replaced when placed in the world.", false).fieldOf("replace_placement").forGetter(SwapEntry::replacePlacement),
                CommentedCodec.of(Codec.INT, "min_y", "Minimum Y level for block replacement.", Integer.MIN_VALUE).fieldOf("min_y").forGetter(SwapEntry::minY),
                CommentedCodec.of(Codec.INT, "max_y", "Maximum Y level for block replacement.", Integer.MAX_VALUE).fieldOf("max_y").forGetter(SwapEntry::maxY),
                CommentedCodec.of(Codec.STRING.listOf(), "dimensions_whitelist", "List of dimension IDs where the swap applies. Empty means all dimensions unless blacklisted.", List.of()).fieldOf("dimensions_whitelist").forGetter(SwapEntry::dimensions_whitelist),
                CommentedCodec.of(Codec.STRING.listOf(), "dimensions_blacklist", "List of dimension IDs where the swap is prevented.", List.of()).fieldOf("dimensions_blacklist").forGetter(SwapEntry::dimensions_blacklist),
                CommentedCodec.of(Codec.STRING.listOf(), "biomes_whitelist", "List of biome IDs where the swap applies. Empty means all biomes unless blacklisted.", List.of()).fieldOf("biomes_whitelist").forGetter(SwapEntry::biomes_whitelist),
                CommentedCodec.of(Codec.STRING.listOf(), "biomes_blacklist", "List of biome IDs where the swap is prevented.", List.of()).fieldOf("biomes_blacklist").forGetter(SwapEntry::biomes_blacklist),
                CommentedCodec.of(Codec.floatRange(0.0F, 1.0F), "block_swap_rand", "Randomization factor for block swapping (0.0 = no swaps, 1.0 = all swaps).", 1.0F).fieldOf("block_swap_rand").forGetter(SwapEntry::blockSwapRand),
                CommentedCodec.of(Codec.INT, "min_y_buffer_zone", "Y-range below min_y where swap probability decreases (e.g., 8 extends from min_y to min_y-8).", 0).fieldOf("min_y_buffer_zone").forGetter(SwapEntry::minYBufferZone),
                CommentedCodec.of(Codec.INT, "max_y_buffer_zone", "Y-range above max_y where swap probability decreases (e.g., 8 extends from max_y to max_y+8).", 0).fieldOf("max_y_buffer_zone").forGetter(SwapEntry::maxYBufferZone),
                CommentedCodec.of(Codec.BOOL, "ignore_block_properties", "Whether to ignore block properties when matching the old state (true = match block type only, false = match specified properties).", false).fieldOf("ignore_block_properties").forGetter(SwapEntry::ignoreBlockProperties),
                CommentedCodec.of(Codec.BOOL, "only_replace_placements", "Whether to swap only blocks placed by players or entities, skipping world generation and retro-generation.", false).fieldOf("only_replace_placements").forGetter(SwapEntry::only_replace_placements),
                CommentedCodec.of(Codec.BOOL, "defer_swap", "Whether to defer swapping until after chunk generation features complete, allowing dependent blocks to generate.", false).fieldOf("defer_swap").forGetter(SwapEntry::defer_swap)
        ).apply(builder, SwapEntry::new));
    }

    public static final Supplier<Codec<List<SwapEntry>>> SWAP_ENTRY_CODEC = () -> SwapEntry.CODEC.listOf();

    private static BlockSwapConfig CONFIG = null;
    private static Object2ObjectOpenHashMap<Block, List<SwapEntry>> BLOCK_TO_ENTRIES = new Object2ObjectOpenHashMap<>();
    private static Reference2ReferenceOpenHashMap<Block, Int2ObjectOpenHashMap<Property<?>>> PROPERTY_CACHE = new Reference2ReferenceOpenHashMap<>();

    public static void updateConfig(BlockSwapConfig config) {
        CONFIG = config;
        BLOCK_TO_ENTRIES.clear();
        for (SwapEntry entry : config.swapEntries()) {
            Block block = entry.oldState().getBlock();
            BLOCK_TO_ENTRIES.computeIfAbsent(block, k -> new ArrayList<>()).add(entry);
            BlockSwap.LOGGER.info("Added SwapEntry to BLOCK_TO_ENTRIES: oldState={}, newState={}", entry.oldState(), entry.newState());
        }
        BlockSwap.LOGGER.info("Updated BLOCK_TO_ENTRIES: {}", BLOCK_TO_ENTRIES);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static BlockState remapState(BlockState incomingState, Level world, BlockPos pos, boolean isPlacement) {
        BlockSwap.LOGGER.info("remapState: state={}, pos={}, isPlacement={}", incomingState, pos, isPlacement);
        if (CONFIG == null) {
            updateConfig(BlockSwapConfig.getConfig(false));
            BlockSwap.LOGGER.info("CONFIG was null, updated to: {}", CONFIG);
        }
        if (CONFIG.retroGen() && isPlacement) {
            BlockSwap.LOGGER.info("Skipping placement swap due to retroGen");
            return incomingState;
        }

        Block block = incomingState.getBlock();
        List<SwapEntry> entries = BLOCK_TO_ENTRIES.get(block);
        if (entries == null) {
            BlockSwap.LOGGER.info("No swap entries for block: {}", block);
            return incomingState;
        }

        String dimensionId = world.dimension().location().toString();
        ResourceLocation biomeId = world.registryAccess()
                .registryOrThrow(Registries.BIOME)
                .getKey(world.getBiome(pos).value());
        Random random = new Random(pos.asLong()); // Seed based on block position

        for (SwapEntry entry : entries) {
            // Skip non-placement swaps if only_replace_placements is true
            if (entry.only_replace_placements() && !isPlacement) {
                continue;
            }
            // Skip swaps during world generation if defer_swap is true
            if (entry.defer_swap() && !isPlacement) {
                continue;
            }

            // Property matching
            BlockState oldState = entry.oldState();
            boolean propertiesMatch = true;
            if (!entry.ignoreBlockProperties() && !oldState.getValues().isEmpty()) {
                for (Property<?> property : oldState.getValues().keySet()) {
                    if (!incomingState.hasProperty(property) || !incomingState.getValue(property).equals(oldState.getValue(property))) {
                        propertiesMatch = false;
                        break;
                    }
                }
            }
            if (!propertiesMatch) {
                continue;
            }

            // Allow placement swaps if only_replace_placements is true or replace_placement is true
            if (isPlacement && !(entry.only_replace_placements() || entry.replacePlacement())) {
                return incomingState;
            }

            // Y-range and buffer zones
            int effectiveMinY = entry.minY() == Integer.MIN_VALUE ? world.getMinBuildHeight() : entry.minY();
            int effectiveMaxY = entry.maxY() == Integer.MAX_VALUE ? world.getMaxBuildHeight() : entry.maxY();
            float swapProbability = entry.blockSwapRand();
            int y = pos.getY();
            if (y < effectiveMinY) {
                if (entry.minYBufferZone() > 0) {
                    int bufferStart = effectiveMinY;
                    int bufferEnd = effectiveMinY - entry.minYBufferZone();
                    if (y >= bufferEnd && y < bufferStart) {
                        swapProbability *= (float)(y - bufferEnd) / (bufferStart - bufferEnd);
                    } else {
                        swapProbability = 0.0F;
                    }
                } else {
                    swapProbability = 0.0F;
                }
            } else if (y > effectiveMaxY) {
                if (entry.maxYBufferZone() > 0) {
                    int bufferStart = effectiveMaxY;
                    int bufferEnd = effectiveMaxY + entry.maxYBufferZone();
                    if (y > bufferStart && y <= bufferEnd) {
                        swapProbability *= (float)(bufferEnd - y) / (bufferEnd - bufferStart);
                    } else {
                        swapProbability = 0.0F;
                    }
                } else {
                    swapProbability = 0.0F;
                }
            }

            // Apply randomization
            if (swapProbability < 1.0F && random.nextFloat() > swapProbability) {
                continue; // Skip swap based on probability
            }

            // Dimension checks
            boolean dimensionAllowed = true;
            if (!entry.dimensions_whitelist().isEmpty()) {
                dimensionAllowed = entry.dimensions_whitelist().contains(dimensionId);
            }
            if (dimensionAllowed && entry.dimensions_blacklist().contains(dimensionId)) {
                dimensionAllowed = false;
            }
            if (!dimensionAllowed) {
                continue;
            }

            // Biome checks
            boolean biomeAllowed = true;
            if (!entry.biomes_whitelist().isEmpty()) {
                biomeAllowed = biomeId != null && entry.biomes_whitelist().contains(biomeId.toString());
            }
            if (biomeAllowed && biomeId != null && entry.biomes_blacklist().contains(biomeId.toString())) {
                biomeAllowed = false;
            }
            if (!biomeAllowed) {
                continue;
            }

            final BlockState finalNewState = entry.newState(); // Final for lambda
            Int2ObjectOpenHashMap<Property<?>> newStateProperties = PROPERTY_CACHE.computeIfAbsent(finalNewState.getBlock(), (block1) -> Util.make(new Int2ObjectOpenHashMap<>(), (set) -> {
                for (Property<?> property : finalNewState.getProperties()) {
                    set.put(property.generateHashCode(), property);
                }
            }));

            BlockState newState = finalNewState;
            for (Property<?> property : incomingState.getProperties()) {
                Property newProperty = newStateProperties.get(property.generateHashCode());
                if (newProperty != null) {
                    newState = newState.setValue(newProperty, incomingState.getValue((Property) newProperty));
                }
            }

            if (CONFIG.verboseLogging()) {
                BlockSwap.LOGGER.debug("Swapping {} to {} at {}", incomingState, newState, pos);
            }

            BlockSwap.LOGGER.info("Swapping {} to {} at {}", incomingState, newState, pos);
            return newState;
        }
        BlockSwap.LOGGER.info("No matching SwapEntry for state: {}", incomingState);
        return incomingState;
    }

    public static void runRetroGenerator(LevelChunk chunk) {
        swapExistingChunk(chunk);
    }

    public static void swapExistingChunk(LevelChunk chunk) {
        if (CONFIG == null) {
            updateConfig(BlockSwapConfig.getConfig(false));
        }
        if (!(chunk.getLevel() instanceof ServerLevel)) {
            return; // Server-side only
        }
        if (!CONFIG.retroGen()) {
            return; // Skip if retroGen is disabled
        }

        ProcessedChunksData data = ProcessedChunksData.load((ServerLevel) chunk.getLevel());
        if (data.isChunkProcessed(chunk.getPos())) {
            return; // Skip already processed chunks
        }

        if (CONFIG.verboseLogging()) {
            BlockSwap.LOGGER.debug("Swapping existing chunk: {}", chunk.getPos());
        }
        Level world = chunk.getLevel();
        LevelChunkSection[] sections = chunk.getSections();
        Random random = new Random(chunk.getPos().toLong()); // Chunk-seeded random
        String dimensionId = world.dimension().location().toString();
        Object2ObjectOpenHashMap<BlockPos, String> biomeCache = new Object2ObjectOpenHashMap<>();
        Object2ObjectOpenHashMap<BlockPos, ResourceLocation> biomeIdCache = new Object2ObjectOpenHashMap<>();

        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            if (section != null) {
                int bottomY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(i));
                for (int x = 0; x < 16; x++) {
                    for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            BlockPos blockPos = new BlockPos(
                                    SectionPos.sectionToBlockCoord(chunk.getPos().x) + x,
                                    bottomY + y,
                                    SectionPos.sectionToBlockCoord(chunk.getPos().z) + z
                            );
                            BlockState state = section.getBlockState(x, y, z);
                            List<SwapEntry> entries = BLOCK_TO_ENTRIES.get(state.getBlock());
                            if (entries == null) {
                                continue; // Skip if no entries for this block
                            }

                            BlockState newState = state;
                            for (SwapEntry entry : entries) {
                                // Skip placement-only or deferred swaps
                                if (entry.only_replace_placements() || entry.defer_swap()) {
                                    continue;
                                }

                                // Property matching
                                BlockState oldState = entry.oldState();
                                boolean propertiesMatch = true;
                                if (!entry.ignoreBlockProperties() && !oldState.getValues().isEmpty()) {
                                    for (Property<?> property : oldState.getValues().keySet()) {
                                        if (!state.hasProperty(property) || !state.getValue(property).equals(oldState.getValue(property))) {
                                            propertiesMatch = false;
                                            break;
                                        }
                                    }
                                }
                                if (!propertiesMatch) {
                                    continue;
                                }

                                // Y-range and buffer zones
                                int effectiveMinY = entry.minY() == Integer.MIN_VALUE ? world.getMinBuildHeight() : entry.minY();
                                int effectiveMaxY = entry.maxY() == Integer.MAX_VALUE ? world.getMaxBuildHeight() : entry.maxY();
                                float swapProbability = entry.blockSwapRand();
                                int blockY = blockPos.getY();
                                if (blockY < effectiveMinY) {
                                    if (entry.minYBufferZone() > 0) {
                                        int bufferStart = effectiveMinY;
                                        int bufferEnd = effectiveMinY - entry.minYBufferZone();
                                        if (blockY >= bufferEnd && blockY < bufferStart) {
                                            swapProbability *= (float)(blockY - bufferEnd) / (bufferStart - bufferEnd);
                                        } else {
                                            swapProbability = 0.0F;
                                        }
                                    } else {
                                        swapProbability = 0.0F;
                                    }
                                } else if (blockY > effectiveMaxY) {
                                    if (entry.maxYBufferZone() > 0) {
                                        int bufferStart = effectiveMaxY;
                                        int bufferEnd = effectiveMaxY + entry.maxYBufferZone();
                                        if (blockY > bufferStart && blockY <= bufferEnd) {
                                            swapProbability *= (float)(bufferEnd - blockY) / (bufferEnd - bufferStart);
                                        } else {
                                            swapProbability = 0.0F;
                                        }
                                    } else {
                                        swapProbability = 0.0F;
                                    }
                                }

                                // Apply randomization
                                if (swapProbability < 1.0F && random.nextFloat() > swapProbability) {
                                    continue; // Skip swap based on probability
                                }

                                // Dimension checks
                                boolean dimensionAllowed = true;
                                if (!entry.dimensions_whitelist().isEmpty()) {
                                    dimensionAllowed = entry.dimensions_whitelist().contains(dimensionId);
                                }
                                if (dimensionAllowed && entry.dimensions_blacklist().contains(dimensionId)) {
                                    dimensionAllowed = false;
                                }
                                if (!dimensionAllowed) {
                                    continue;
                                }

                                // Biome checks with caching
                                String biomeKey = blockPos.toString();
                                ResourceLocation cachedBiomeId = biomeIdCache.get(blockPos);
                                if (cachedBiomeId == null) {
                                    cachedBiomeId = world.registryAccess()
                                            .registryOrThrow(Registries.BIOME)
                                            .getKey(world.getBiome(blockPos).value());
                                    biomeIdCache.put(blockPos, cachedBiomeId);
                                }
                                boolean biomeAllowed = true;
                                if (!entry.biomes_whitelist().isEmpty()) {
                                    biomeAllowed = cachedBiomeId != null && entry.biomes_whitelist().contains(cachedBiomeId.toString());
                                }
                                if (biomeAllowed && cachedBiomeId != null && entry.biomes_blacklist().contains(cachedBiomeId.toString())) {
                                    biomeAllowed = false;
                                }
                                if (!biomeAllowed) {
                                    continue;
                                }

                                final BlockState finalNewState = entry.newState(); // Final for lambda
                                Int2ObjectOpenHashMap<Property<?>> newStateProperties = PROPERTY_CACHE.computeIfAbsent(finalNewState.getBlock(), (block1) -> Util.make(new Int2ObjectOpenHashMap<>(), (set) -> {
                                    for (Property<?> property : finalNewState.getProperties()) {
                                        set.put(property.generateHashCode(), property);
                                    }
                                }));

                                newState = finalNewState;
                                for (Property<?> property : state.getProperties()) {
                                    Property newProperty = newStateProperties.get(property.generateHashCode());
                                    if (newProperty != null) {
                                        newState = newState.setValue(newProperty, state.getValue((Property) newProperty));
                                    }
                                }
                                break; // Apply first matching entry
                            }

                            if (!newState.equals(state)) {
                                if (CONFIG.verboseLogging()) {
                                    BlockSwap.LOGGER.debug("Swapping {} to {} at {} during retroGen", state, newState, blockPos);
                                }
                                world.setBlock(blockPos, newState, 2);
                            }
                        }
                    }
                }
            }
        }
        data.markChunkProcessed(chunk.getPos());
        if (CONFIG.verboseLogging()) {
            BlockSwap.LOGGER.debug("Marked chunk {} as processed", chunk.getPos());
        }
    }

    public static void runDeferredSwaps(LevelChunk chunk) {
        if (CONFIG == null) {
            updateConfig(BlockSwapConfig.getConfig(false));
        }
        if (!(chunk.getLevel() instanceof ServerLevel)) {
            return; // Server-side only
        }

        if (CONFIG.verboseLogging()) {
            BlockSwap.LOGGER.debug("Running deferred swaps for chunk {}", chunk.getPos());
        }
        Level world = chunk.getLevel();
        LevelChunkSection[] sections = chunk.getSections();
        Random random = new Random(chunk.getPos().toLong()); // Chunk-seeded random
        Object2ObjectOpenHashMap<BlockPos, String> biomeCache = new Object2ObjectOpenHashMap<>();
        Object2ObjectOpenHashMap<BlockPos, ResourceLocation> biomeIdCache = new Object2ObjectOpenHashMap<>();
        String dimensionId = world.dimension().location().toString();

        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            if (section != null) {
                int bottomY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(i));
                for (int x = 0; x < 16; x++) {
                    for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            BlockPos blockPos = new BlockPos(
                                    SectionPos.sectionToBlockCoord(chunk.getPos().x) + x,
                                    bottomY + y,
                                    SectionPos.sectionToBlockCoord(chunk.getPos().z) + z
                            );
                            BlockState state = section.getBlockState(x, y, z);
                            List<SwapEntry> entries = BLOCK_TO_ENTRIES.get(state.getBlock());
                            if (entries == null) {
                                continue; // Skip if no entries for this block
                            }

                            BlockState newState = state;
                            for (SwapEntry entry : entries) {
                                // Process only deferred swaps
                                if (!entry.defer_swap()) {
                                    continue;
                                }

                                // Property matching
                                BlockState oldState = entry.oldState();
                                boolean propertiesMatch = true;
                                if (!entry.ignoreBlockProperties() && !oldState.getValues().isEmpty()) {
                                    for (Property<?> property : oldState.getValues().keySet()) {
                                        if (!state.hasProperty(property) || !state.getValue(property).equals(oldState.getValue(property))) {
                                            propertiesMatch = false;
                                            break;
                                        }
                                    }
                                }
                                if (!propertiesMatch) {
                                    continue;
                                }

                                // Y-range and buffer zones
                                int effectiveMinY = entry.minY() == Integer.MIN_VALUE ? world.getMinBuildHeight() : entry.minY();
                                int effectiveMaxY = entry.maxY() == Integer.MAX_VALUE ? world.getMaxBuildHeight() : entry.maxY();
                                float swapProbability = entry.blockSwapRand();
                                int blockY = blockPos.getY();
                                if (blockY < effectiveMinY) {
                                    if (entry.minYBufferZone() > 0) {
                                        int bufferStart = effectiveMinY;
                                        int bufferEnd = effectiveMinY - entry.minYBufferZone();
                                        if (blockY >= bufferEnd && blockY < bufferStart) {
                                            swapProbability *= (float)(blockY - bufferEnd) / (bufferStart - bufferEnd);
                                        } else {
                                            swapProbability = 0.0F;
                                        }
                                    } else {
                                        swapProbability = 0.0F;
                                    }
                                } else if (blockY > effectiveMaxY) {
                                    if (entry.maxYBufferZone() > 0) {
                                        int bufferStart = effectiveMaxY;
                                        int bufferEnd = effectiveMaxY + entry.maxYBufferZone();
                                        if (blockY > bufferStart && blockY <= bufferEnd) {
                                            swapProbability *= (float)(bufferEnd - blockY) / (bufferEnd - bufferStart);
                                        } else {
                                            swapProbability = 0.0F;
                                        }
                                    } else {
                                        swapProbability = 0.0F;
                                    }
                                }

                                // Apply randomization
                                if (swapProbability < 1.0F && random.nextFloat() > swapProbability) {
                                    continue; // Skip swap based on probability
                                }

                                // Dimension checks
                                boolean dimensionAllowed = true;
                                if (!entry.dimensions_whitelist().isEmpty()) {
                                    dimensionAllowed = entry.dimensions_whitelist().contains(dimensionId);
                                }
                                if (dimensionAllowed && entry.dimensions_blacklist().contains(dimensionId)) {
                                    dimensionAllowed = false;
                                }
                                if (!dimensionAllowed) {
                                    continue;
                                }

                                // Biome checks with caching
                                String biomeKey = blockPos.toString();
                                ResourceLocation cachedBiomeId = biomeIdCache.get(blockPos);
                                if (cachedBiomeId == null) {
                                    cachedBiomeId = world.registryAccess()
                                            .registryOrThrow(Registries.BIOME)
                                            .getKey(world.getBiome(blockPos).value());
                                    biomeIdCache.put(blockPos, cachedBiomeId);
                                }
                                boolean biomeAllowed = true;
                                if (!entry.biomes_whitelist().isEmpty()) {
                                    biomeAllowed = cachedBiomeId != null && entry.biomes_whitelist().contains(cachedBiomeId.toString());
                                }
                                if (biomeAllowed && cachedBiomeId != null && entry.biomes_blacklist().contains(cachedBiomeId.toString())) {
                                    biomeAllowed = false;
                                }
                                if (!biomeAllowed) {
                                    continue;
                                }

                                final BlockState finalNewState = entry.newState(); // Final for lambda
                                Int2ObjectOpenHashMap<Property<?>> newStateProperties = PROPERTY_CACHE.computeIfAbsent(finalNewState.getBlock(), (block1) -> Util.make(new Int2ObjectOpenHashMap<>(), (set) -> {
                                    for (Property<?> property : finalNewState.getProperties()) {
                                        set.put(property.generateHashCode(), property);
                                    }
                                }));

                                newState = finalNewState;
                                for (Property<?> property : state.getProperties()) {
                                    Property newProperty = newStateProperties.get(property.generateHashCode());
                                    if (newProperty != null) {
                                        newState = newState.setValue(newProperty, state.getValue((Property) newProperty));
                                    }
                                }
                                break; // Apply first matching entry
                            }

                            if (!newState.equals(state)) {
                                if (CONFIG.verboseLogging()) {
                                    BlockSwap.LOGGER.debug("Swapping {} to {} at {} during deferred swap", state, newState, blockPos);
                                }
                                world.setBlock(blockPos, newState, 2);
                            }
                        }
                    }
                }
            }
        }
    }
}