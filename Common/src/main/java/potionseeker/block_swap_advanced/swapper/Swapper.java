package potionseeker.block_swap_advanced.swapper;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.ChunkPos;
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
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.*;
import java.util.function.Supplier;

public class Swapper {
    public static final Codec<BlockState> COMMENTED_STATE_CODEC = CodecUtil.BLOCK_STATE_CODEC;

    // Core fields for block swapping (9 fields)
    public record CoreSwapEntry(
            BlockState oldState,
            BlockState newState,
            boolean replacePlacement,
            int minY,
            int maxY,
            float blockSwapRand,
            int minYBufferZone,
            int maxYBufferZone,
            boolean ignoreBlockProperties
    ) {
        public static final Codec<CoreSwapEntry> CODEC = RecordCodecBuilder.create(builder ->
                builder.group(
                        COMMENTED_STATE_CODEC.fieldOf("old").forGetter(CoreSwapEntry::oldState),
                        COMMENTED_STATE_CODEC.fieldOf("new").forGetter(CoreSwapEntry::newState),
                        Codec.BOOL.optionalFieldOf("replace_placement", true).forGetter(CoreSwapEntry::replacePlacement),
                        Codec.INT.optionalFieldOf("min_y", Integer.MIN_VALUE).forGetter(CoreSwapEntry::minY),
                        Codec.INT.optionalFieldOf("max_y", Integer.MAX_VALUE).forGetter(CoreSwapEntry::maxY),
                        Codec.floatRange(0.0F, 1.0F).optionalFieldOf("block_swap_rand", 1.0F).forGetter(CoreSwapEntry::blockSwapRand),
                        Codec.INT.optionalFieldOf("min_y_buffer_zone", 0).forGetter(CoreSwapEntry::minYBufferZone),
                        Codec.INT.optionalFieldOf("max_y_buffer_zone", 0).forGetter(CoreSwapEntry::maxYBufferZone),
                        Codec.BOOL.optionalFieldOf("ignore_block_properties", false).forGetter(CoreSwapEntry::ignoreBlockProperties)
                ).apply(builder, CoreSwapEntry::new)
        );
    }

    // Filter fields for environmental constraints (6 fields)
    public record FilterEntry(
            List<String> dimensions_whitelist,
            List<String> dimensions_blacklist,
            List<String> biomes_whitelist,
            List<String> biomes_blacklist,
            List<String> structures_whitelist,
            List<String> structures_blacklist
    ) {
        public static final Codec<FilterEntry> CODEC = RecordCodecBuilder.create(builder ->
                builder.group(
                        Codec.STRING.listOf().optionalFieldOf("dimensions_whitelist", List.of()).forGetter(FilterEntry::dimensions_whitelist),
                        Codec.STRING.listOf().optionalFieldOf("dimensions_blacklist", List.of()).forGetter(FilterEntry::dimensions_blacklist),
                        Codec.STRING.listOf().optionalFieldOf("biomes_whitelist", List.of()).forGetter(FilterEntry::biomes_whitelist),
                        Codec.STRING.listOf().optionalFieldOf("biomes_blacklist", List.of()).forGetter(FilterEntry::biomes_blacklist),
                        Codec.STRING.listOf().optionalFieldOf("structures_whitelist", List.of()).forGetter(FilterEntry::structures_whitelist),
                        Codec.STRING.listOf().optionalFieldOf("structures_blacklist", List.of()).forGetter(FilterEntry::structures_blacklist)
                ).apply(builder, FilterEntry::new)
        );
    }

    // Combined SwapEntry class to maintain interface
    public static class SwapEntry {
        private final CoreSwapEntry core;
        private final FilterEntry filter;
        private final boolean only_replace_placements;
        private final boolean defer_swap;

        public static final Codec<SwapEntry> CODEC = RecordCodecBuilder.create(builder ->
                builder.group(
                        CoreSwapEntry.CODEC.fieldOf("core").forGetter(e -> e.core),
                        FilterEntry.CODEC.optionalFieldOf("filter", new FilterEntry(List.of(), List.of(), List.of(), List.of(), List.of(), List.of())).forGetter(e -> e.filter),
                        Codec.BOOL.optionalFieldOf("only_replace_placements", false).forGetter(e -> e.only_replace_placements),
                        Codec.BOOL.optionalFieldOf("defer_swap", false).forGetter(e -> e.defer_swap)
                ).apply(builder, SwapEntry::new)
        );

        public SwapEntry(CoreSwapEntry core, FilterEntry filter, boolean only_replace_placements, boolean defer_swap) {
            this.core = core;
            this.filter = filter;
            this.only_replace_placements = only_replace_placements;
            this.defer_swap = defer_swap;
        }

        // Getter methods to match original SwapEntry record
        public BlockState oldState() { return core.oldState(); }
        public BlockState newState() { return core.newState(); }
        public boolean replacePlacement() { return core.replacePlacement(); }
        public int minY() { return core.minY(); }
        public int maxY() { return core.maxY(); }
        public float blockSwapRand() { return core.blockSwapRand(); }
        public int minYBufferZone() { return core.minYBufferZone(); }
        public int maxYBufferZone() { return core.maxYBufferZone(); }
        public boolean ignoreBlockProperties() { return core.ignoreBlockProperties(); }
        public boolean only_replace_placements() { return only_replace_placements; }
        public boolean defer_swap() { return defer_swap; }
        public List<String> dimensions_whitelist() { return filter.dimensions_whitelist(); }
        public List<String> dimensions_blacklist() { return filter.dimensions_blacklist(); }
        public List<String> biomes_whitelist() { return filter.biomes_whitelist(); }
        public List<String> biomes_blacklist() { return filter.biomes_blacklist(); }
        public List<String> structures_whitelist() { return filter.structures_whitelist(); }
        public List<String> structures_blacklist() { return filter.structures_blacklist(); }
    }

    public static final Supplier<Codec<List<SwapEntry>>> SWAP_ENTRY_CODEC = () -> SwapEntry.CODEC.listOf();

    private static BlockSwapConfig CONFIG = null;
    private static Object2ObjectOpenHashMap<Block, List<SwapEntry>> BLOCK_TO_ENTRIES = new Object2ObjectOpenHashMap<>();
    private static Reference2ReferenceOpenHashMap<Block, Int2ObjectOpenHashMap<Property<?>>> PROPERTY_CACHE = new Reference2ReferenceOpenHashMap<>();
    private static String LAST_CONFIG_HASH = "";

    public static void updateConfig(BlockSwapConfig config) {
        String newConfigHash = Integer.toString(config.swapEntries().hashCode());
        if (config.redoGen() && !newConfigHash.equals(LAST_CONFIG_HASH)) {
            LAST_CONFIG_HASH = newConfigHash;
            BlockSwap.LOGGER.debug("Config hash changed to {}, will update ProcessedChunksData when a ServerLevel is available", newConfigHash);
        }

        CONFIG = config;
        BLOCK_TO_ENTRIES.clear();
        PROPERTY_CACHE.clear();
        BlockSwap.LOGGER.debug("Updating BLOCK_TO_ENTRIES with {} swap entries", config.swapEntries().size());
        for (SwapEntry entry : config.swapEntries()) {
            Block block = entry.oldState().getBlock();
            BLOCK_TO_ENTRIES.computeIfAbsent(block, k -> new ArrayList<>()).add(entry);
            BlockSwap.LOGGER.debug("Registered SwapEntry: oldState={}, newState={}, replacePlacement={}, blockSwapRand={}",
                    entry.oldState(), entry.newState(), entry.replacePlacement(), entry.blockSwapRand());
        }
        BlockSwap.LOGGER.info("Updated BLOCK_TO_ENTRIES with {} blocks", BLOCK_TO_ENTRIES.size());
    }

    public static List<SwapEntry> getEntriesForBlock(Block block) {
        return BLOCK_TO_ENTRIES.get(block);
    }

    public static boolean isWithinChunkSwapRange(ServerLevel serverLevel, ChunkPos chunkPos, int chunkSwapRange) {
        if (chunkSwapRange < 0) {
            return true; // Process all loaded chunks
        }
        if (chunkSwapRange == 0) {
            return false; // No chunks processed
        }
        for (ServerPlayer player : serverLevel.players()) {
            ChunkPos playerChunk = new ChunkPos(BlockPos.containing(player.getPosition(1.0F)));
            int distance = Math.max(Math.abs(playerChunk.x - chunkPos.x), Math.abs(playerChunk.z - chunkPos.z));
            if (distance < chunkSwapRange) {
                return true; // Within range of at least one player
            }
        }
        return false; // No players within chunk range
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static BlockState remapState(BlockState incomingState, Level world, BlockPos pos, boolean isPlacement) {
        if (CONFIG == null) {
            BlockSwap.LOGGER.debug("CONFIG is null, loading config");
            updateConfig(BlockSwapConfig.getConfig(true));
        }

        Block block = incomingState.getBlock();
        List<SwapEntry> entries = BLOCK_TO_ENTRIES.get(block);
        if (entries == null || entries.isEmpty()) {
            BlockSwap.LOGGER.debug("No SwapEntry found for block: {}", block);
            return incomingState;
        }

        String dimensionId = world.dimension().location().toString();
        ResourceLocation biomeId = world.registryAccess()
                .registryOrThrow(Registries.BIOME)
                .getKey(world.getBiome(pos).value());
        Random random = new Random(pos.asLong());

        BlockSwap.LOGGER.debug("Checking {} SwapEntry(s) for state={} at pos={}", entries.size(), incomingState, pos);
        for (SwapEntry entry : entries) {
            // Skip generation swaps if only_replace_placements
            if (entry.only_replace_placements() && !isPlacement) {
                BlockSwap.LOGGER.debug("Skipping SwapEntry due to only_replace_placements: oldState={}", entry.oldState());
                continue;
            }
            // Skip placement swaps if replace_placement is false
            if (!entry.replacePlacement() && isPlacement) {
                BlockSwap.LOGGER.debug("Skipping SwapEntry due to replacePlacement=false: oldState={}", entry.oldState());
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
                BlockSwap.LOGGER.debug("Properties do not match for SwapEntry: oldState={}, incomingState={}", oldState, incomingState);
                continue;
            }

            // Y-range and buffer zones
            int effectiveMinY = entry.minY() == Integer.MIN_VALUE ? world.getMinBuildHeight() : entry.minY();
            int effectiveMaxY = entry.maxY() == Integer.MAX_VALUE ? world.getMaxBuildHeight() : entry.maxY();
            float swapProbability = entry.blockSwapRand();
            int blockY = pos.getY();
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
                BlockSwap.LOGGER.debug("Swap skipped due to randomization: swapProbability={}, random={}", swapProbability, random.nextFloat());
                continue;
            }

            // Dimension checks
            boolean dimensionAllowed = entry.dimensions_whitelist().isEmpty() || entry.dimensions_whitelist().contains(dimensionId);
            if (dimensionAllowed && entry.dimensions_blacklist().contains(dimensionId)) {
                dimensionAllowed = false;
            }
            if (!dimensionAllowed) {
                BlockSwap.LOGGER.debug("Swap skipped due to dimension filter: dimensionId={}", dimensionId);
                continue;
            }

            // Biome checks
            boolean biomeAllowed = entry.biomes_whitelist().isEmpty() || entry.biomes_whitelist().contains(biomeId.toString());
            if (biomeAllowed && entry.biomes_blacklist().contains(biomeId.toString())) {
                biomeAllowed = false;
            }
            if (!biomeAllowed) {
                BlockSwap.LOGGER.debug("Swap skipped due to biome filter: biomeId={}", biomeId);
                continue;
            }

            // Structure checks (ServerLevel only)
            boolean structureAllowed = true;
            if (world instanceof ServerLevel serverWorld && (!entry.structures_whitelist().isEmpty() || !entry.structures_blacklist().isEmpty())) {
                structureAllowed = false;
                // Check whitelist
                for (String structStr : entry.structures_whitelist()) {
                    ResourceLocation structId = new ResourceLocation(structStr);
                    Structure structure = serverWorld.registryAccess().registryOrThrow(Registries.STRUCTURE).get(structId);
                    if (structure != null) {
                        StructureStart start = serverWorld.structureManager().getStructureWithPieceAt(pos, structure);
                        if (start != null && start.isValid()) {
                            structureAllowed = true;
                            break;
                        }
                    }
                }
                // Check blacklist if whitelisted
                if (structureAllowed && !entry.structures_blacklist().isEmpty()) {
                    for (String structStr : entry.structures_blacklist()) {
                        ResourceLocation structId = new ResourceLocation(structStr);
                        Structure structure = serverWorld.registryAccess().registryOrThrow(Registries.STRUCTURE).get(structId);
                        if (structure != null) {
                            StructureStart start = serverWorld.structureManager().getStructureWithPieceAt(pos, structure);
                            if (start != null && start.isValid()) {
                                structureAllowed = false;
                                break;
                            }
                        }
                    }
                }
            }
            if (!structureAllowed) {
                BlockSwap.LOGGER.debug("Swap skipped due to structure filter at {}", pos);
                continue;
            }

            final BlockState finalNewState = entry.newState();
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
                BlockSwap.LOGGER.info("Swapping {} to {} at {} during world generation or placement", incomingState, newState, pos);
            }
            return newState;
        }
        return incomingState;
    }

    public static void runRetroGenerator(LevelChunk chunk) {
        swapExistingChunk(chunk);
    }

    public static void swapExistingChunk(LevelChunk chunk) {
        if (CONFIG == null) {
            BlockSwap.LOGGER.debug("CONFIG is null, loading config");
            updateConfig(BlockSwapConfig.getConfig(true));
        }
        if (!(chunk.getLevel() instanceof ServerLevel serverLevel)) {
            return; // Server-side only
        }
        if (!CONFIG.retroGen() && !CONFIG.redoGen()) {
            return; // Skip if neither retroGen nor redoGen is enabled
        }

        ChunkPos chunkPos = chunk.getPos();
        if (!isWithinChunkSwapRange(serverLevel, chunkPos, CONFIG.chunkSwapRange())) {
            BlockSwap.LOGGER.debug("Chunk swap skipped for {}: outside chunk_swap_range", chunkPos);
            return;
        }

        ProcessedChunksData data = ProcessedChunksData.load(serverLevel);
        String newConfigHash = Integer.toString(CONFIG.swapEntries().hashCode());
        boolean isProcessed = data.isChunkProcessed(chunkPos);
        if (!CONFIG.redoGen() && isProcessed && newConfigHash.equals(data.getConfigHash())) {
            BlockSwap.LOGGER.debug("Skipping chunk {}: already processed and config unchanged", chunkPos);
            return; // Skip already processed chunks unless redoGen is enabled or config changed
        }

        // Update config hash if necessary
        if (!newConfigHash.equals(data.getConfigHash()) && CONFIG.redoGen()) {
            data.updateConfigHash(newConfigHash);
            BlockSwap.LOGGER.debug("Updated config hash to {}, clearing processed chunks for redoGen", newConfigHash);
        }

        BlockSwap.LOGGER.debug("Swapping existing chunk: {} (retroGen={}, redoGen={})", chunkPos, CONFIG.retroGen(), CONFIG.redoGen());
        Level world = chunk.getLevel();
        LevelChunkSection[] sections = chunk.getSections();
        Random random = new Random(chunkPos.toLong());
        String dimensionId = world.dimension().location().toString();
        Object2ObjectOpenHashMap<BlockPos, ResourceLocation> biomeIdCache = new Object2ObjectOpenHashMap<>();

        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            if (section != null) {
                int bottomY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(i));
                for (int x = 0; x < 16; x++) {
                    for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            BlockPos blockPos = new BlockPos(
                                    SectionPos.sectionToBlockCoord(chunkPos.x) + x,
                                    bottomY + y,
                                    SectionPos.sectionToBlockCoord(chunkPos.z) + z
                            );

                            BlockState state = section.getBlockState(x, y, z);
                            List<SwapEntry> entries = BLOCK_TO_ENTRIES.get(state.getBlock());
                            if (entries == null) {
                                continue;
                            }

                            BlockState newState = state;
                            for (SwapEntry entry : entries) {
                                if (entry.only_replace_placements() || entry.defer_swap()) {
                                    continue;
                                }

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

                                if (swapProbability < 1.0F && random.nextFloat() > swapProbability) {
                                    continue;
                                }

                                boolean dimensionAllowed = entry.dimensions_whitelist().isEmpty() || entry.dimensions_whitelist().contains(dimensionId);
                                if (dimensionAllowed && entry.dimensions_blacklist().contains(dimensionId)) {
                                    dimensionAllowed = false;
                                }
                                if (!dimensionAllowed) {
                                    continue;
                                }

                                ResourceLocation cachedBiomeId = biomeIdCache.get(blockPos);
                                if (cachedBiomeId == null) {
                                    cachedBiomeId = world.registryAccess()
                                            .registryOrThrow(Registries.BIOME)
                                            .getKey(world.getBiome(blockPos).value());
                                    biomeIdCache.put(blockPos, cachedBiomeId);
                                }
                                boolean biomeAllowed = entry.biomes_whitelist().isEmpty() || entry.biomes_whitelist().contains(cachedBiomeId.toString());
                                if (biomeAllowed && entry.biomes_blacklist().contains(cachedBiomeId.toString())) {
                                    biomeAllowed = false;
                                }
                                if (!biomeAllowed) {
                                    continue;
                                }

                                boolean structureAllowed = true;
                                if (world instanceof ServerLevel serverWorld && (!entry.structures_whitelist().isEmpty() || !entry.structures_blacklist().isEmpty())) {
                                    structureAllowed = false;
                                    for (String structStr : entry.structures_whitelist()) {
                                        ResourceLocation structId = new ResourceLocation(structStr);
                                        Structure structure = serverWorld.registryAccess().registryOrThrow(Registries.STRUCTURE).get(structId);
                                        if (structure != null) {
                                            StructureStart start = serverWorld.structureManager().getStructureWithPieceAt(blockPos, structure);
                                            if (start != null && start.isValid()) {
                                                structureAllowed = true;
                                                break;
                                            }
                                        }
                                    }
                                    if (structureAllowed && !entry.structures_blacklist().isEmpty()) {
                                        for (String structStr : entry.structures_blacklist()) {
                                            ResourceLocation structId = new ResourceLocation(structStr);
                                            Structure structure = serverWorld.registryAccess().registryOrThrow(Registries.STRUCTURE).get(structId);
                                            if (structure != null) {
                                                StructureStart start = serverWorld.structureManager().getStructureWithPieceAt(blockPos, structure);
                                                if (start != null && start.isValid()) {
                                                    structureAllowed = false;
                                                    break;
                                                }
                                            }
                                        }
                                    }
                                }
                                if (!structureAllowed) {
                                    BlockSwap.LOGGER.debug("Structure filter blocked retro-swap at {}", blockPos);
                                    continue;
                                }

                                final BlockState finalNewState = entry.newState();
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
                                break;
                            }

                            if (!newState.equals(state) && CONFIG.verboseLogging()) {
                                BlockSwap.LOGGER.info("Swapping {} to {} at {} during retroGen or redoGen", state, newState, blockPos);
                            }
                            if (!newState.equals(state)) {
                                world.setBlock(blockPos, newState, 2);
                            }
                        }
                    }
                }
            }
        }
        data.markChunkProcessed(chunkPos);
        data.markProcessedThisSession(chunkPos);
        BlockSwap.LOGGER.debug("Marked chunk {} as processed for retroGen/redoGen", chunkPos);
    }

    public static void runDeferredSwaps(LevelChunk chunk) {
        if (CONFIG == null) {
            BlockSwap.LOGGER.debug("CONFIG is null, loading config");
            updateConfig(BlockSwapConfig.getConfig(true));
        }
        if (!(chunk.getLevel() instanceof ServerLevel serverLevel)) {
            return; // Server-side only
        }

        ChunkPos chunkPos = chunk.getPos();
        if (!isWithinChunkSwapRange(serverLevel, chunkPos, CONFIG.chunkSwapRange())) {
            BlockSwap.LOGGER.debug("Deferred chunk swap skipped for {}: outside chunk_swap_range", chunkPos);
            return;
        }

        ProcessedChunksData data = ProcessedChunksData.load(serverLevel);
        String newConfigHash = Integer.toString(CONFIG.swapEntries().hashCode());
        if (CONFIG.redoGen() && !newConfigHash.equals(data.getConfigHash())) {
            data.updateConfigHash(newConfigHash);
            BlockSwap.LOGGER.debug("Updated config hash to {} during deferred swap for {}", newConfigHash, chunkPos);
        }

        BlockSwap.LOGGER.debug("Running deferred swaps for chunk {}", chunkPos);
        Level world = chunk.getLevel();
        LevelChunkSection[] sections = chunk.getSections();
        Random random = new Random(chunkPos.toLong());
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
                                    SectionPos.sectionToBlockCoord(chunkPos.x) + x,
                                    bottomY + y,
                                    SectionPos.sectionToBlockCoord(chunkPos.z) + z
                            );

                            BlockState state = section.getBlockState(x, y, z);
                            List<SwapEntry> entries = BLOCK_TO_ENTRIES.get(state.getBlock());
                            if (entries == null) {
                                continue;
                            }

                            BlockState newState = state;
                            for (SwapEntry entry : entries) {
                                if (!entry.defer_swap()) {
                                    continue;
                                }

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

                                if (swapProbability < 1.0F && random.nextFloat() > swapProbability) {
                                    continue;
                                }

                                boolean dimensionAllowed = entry.dimensions_whitelist().isEmpty() || entry.dimensions_whitelist().contains(dimensionId);
                                if (dimensionAllowed && entry.dimensions_blacklist().contains(dimensionId)) {
                                    dimensionAllowed = false;
                                }
                                if (!dimensionAllowed) {
                                    continue;
                                }

                                ResourceLocation cachedBiomeId = biomeIdCache.get(blockPos);
                                if (cachedBiomeId == null) {
                                    cachedBiomeId = world.registryAccess()
                                            .registryOrThrow(Registries.BIOME)
                                            .getKey(world.getBiome(blockPos).value());
                                    biomeIdCache.put(blockPos, cachedBiomeId);
                                }
                                boolean biomeAllowed = entry.biomes_whitelist().isEmpty() || entry.biomes_whitelist().contains(cachedBiomeId.toString());
                                if (biomeAllowed && entry.biomes_blacklist().contains(cachedBiomeId.toString())) {
                                    biomeAllowed = false;
                                }
                                if (!biomeAllowed) {
                                    continue;
                                }

                                boolean structureAllowed = true;
                                if (world instanceof ServerLevel serverWorld && (!entry.structures_whitelist().isEmpty() || !entry.structures_blacklist().isEmpty())) {
                                    structureAllowed = false;
                                    for (String structStr : entry.structures_whitelist()) {
                                        ResourceLocation structId = new ResourceLocation(structStr);
                                        Structure structure = serverWorld.registryAccess().registryOrThrow(Registries.STRUCTURE).get(structId);
                                        if (structure != null) {
                                            StructureStart start = serverWorld.structureManager().getStructureWithPieceAt(blockPos, structure);
                                            if (start != null && start.isValid()) {
                                                structureAllowed = true;
                                                break;
                                            }
                                        }
                                    }
                                    if (structureAllowed && !entry.structures_blacklist().isEmpty()) {
                                        for (String structStr : entry.structures_blacklist()) {
                                            ResourceLocation structId = new ResourceLocation(structStr);
                                            Structure structure = serverWorld.registryAccess().registryOrThrow(Registries.STRUCTURE).get(structId);
                                            if (structure != null) {
                                                StructureStart start = serverWorld.structureManager().getStructureWithPieceAt(blockPos, structure);
                                                if (start != null && start.isValid()) {
                                                    structureAllowed = false;
                                                    break;
                                                }
                                            }
                                        }
                                    }
                                }
                                if (!structureAllowed) {
                                    BlockSwap.LOGGER.debug("Structure filter blocked deferred swap at {}", blockPos);
                                    continue;
                                }

                                final BlockState finalNewState = entry.newState();
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
                                break;
                            }

                            if (!newState.equals(state) && CONFIG.verboseLogging()) {
                                BlockSwap.LOGGER.info("Swapping {} to {} at {} during deferred swap", state, newState, blockPos);
                            }
                            if (!newState.equals(state)) {
                                world.setBlock(blockPos, newState, 2);
                            }
                        }
                    }
                }
            }
        }
    }
}