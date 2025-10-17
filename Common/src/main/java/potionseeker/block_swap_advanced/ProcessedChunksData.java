package potionseeker.block_swap_advanced;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashSet;
import java.util.Set;

public class ProcessedChunksData extends SavedData {
    private static final Logger LOGGER = LogManager.getLogger("BlockSwap");
    private static final String DATA_NAME = "block_swap_processed";
    private final Set<ChunkPos> processedChunks = new HashSet<>();

    public static ProcessedChunksData load(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
            ProcessedChunksData::load,
            () -> new ProcessedChunksData(),
            DATA_NAME
        );
    }

    public boolean isChunkProcessed(ChunkPos pos) {
        return processedChunks.contains(pos);
    }

    public void markChunkProcessed(ChunkPos pos) {
        if (processedChunks.add(pos)) {
            LOGGER.debug("Marked chunk {} as processed", pos);
            setDirty();
        }
    }

    private static ProcessedChunksData load(CompoundTag tag) {
        ProcessedChunksData data = new ProcessedChunksData();
        ListTag chunkList = tag.getList("ProcessedChunks", Tag.TAG_LONG); // Type 4 is LongTag
        for (int i = 0; i < chunkList.size(); i++) {
            Tag element = chunkList.get(i);
            if (element instanceof LongTag longTag) {
                long pos = longTag.getAsLong();
                data.processedChunks.add(new ChunkPos(pos));
            }
        }
        LOGGER.debug("Loaded {} processed chunks", data.processedChunks.size());
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag chunkList = new ListTag();
        for (ChunkPos pos : processedChunks) {
            chunkList.add(LongTag.valueOf(pos.toLong()));
        }
        tag.put("ProcessedChunks", chunkList);
        LOGGER.debug("Saving {} processed chunks", processedChunks.size());
        return tag;
    }
}