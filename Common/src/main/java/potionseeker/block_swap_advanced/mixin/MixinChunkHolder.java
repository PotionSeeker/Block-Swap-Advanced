package potionseeker.block_swap_advanced.mixin;

import potionseeker.block_swap_advanced.BlockSwap;
import potionseeker.block_swap_advanced.ProcessedChunksData;
import potionseeker.block_swap_advanced.config.BlockSwapConfig;
import potionseeker.block_swap_advanced.swapper.Swapper;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkHolder.class)
public class MixinChunkHolder {

    @Inject(method = "broadcastChanges", at = @At("HEAD"))
    private void runChunkUpdates(LevelChunk chunk, CallbackInfo ci) {
        if (!(chunk.getLevel() instanceof ServerLevel serverLevel)) {
            return; // Server-side only
        }

        BlockSwapConfig config = BlockSwapConfig.getConfig(false);
        ProcessedChunksData data = BlockSwap.getProcessedChunksData(serverLevel);
        String newConfigHash = Integer.toString(config.swapEntries().hashCode());

        // Update config hash if redoGen is enabled and hash has changed
        if (config.redoGen() && !newConfigHash.equals(data.getConfigHash())) {
            data.updateConfigHash(newConfigHash);
            BlockSwap.LOGGER.debug("Updated config hash to {} during chunk processing for {}", newConfigHash, chunk.getPos());
        }

        // Check if chunk is within chunk_swap_range
        boolean withinRange = Swapper.isWithinChunkSwapRange(serverLevel, chunk.getPos(), config.chunkSwapRange());
        if (!withinRange) {
            BlockSwap.LOGGER.debug("Chunk {} skipped: outside chunk_swap_range", chunk.getPos());
            return;
        }

        // Process chunks for retroGen or redoGen
        if (config.retroGen() && !data.isChunkProcessed(chunk.getPos())) {
            BlockSwap.LOGGER.debug("Processing chunk {} for retro_gen", chunk.getPos());
            Swapper.runRetroGenerator(chunk);
            data.markChunkProcessed(chunk.getPos());
            data.markProcessedThisSession(chunk.getPos());
        } else if (config.redoGen() && !data.wasProcessedThisSession(chunk.getPos())) {
            BlockSwap.LOGGER.debug("Processing chunk {} for redo_gen", chunk.getPos());
            Swapper.runRetroGenerator(chunk);
            data.markChunkProcessed(chunk.getPos());
            data.markProcessedThisSession(chunk.getPos());
        }
    }
}