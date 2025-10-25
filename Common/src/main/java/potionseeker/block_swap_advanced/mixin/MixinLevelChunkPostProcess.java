package potionseeker.block_swap_advanced.mixin;

import potionseeker.block_swap_advanced.BlockSwap;
import potionseeker.block_swap_advanced.ProcessedChunksData;
import potionseeker.block_swap_advanced.config.BlockSwapConfig;
import potionseeker.block_swap_advanced.swapper.Swapper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelChunk.class)
public class MixinLevelChunkPostProcess {

    @Inject(method = "postProcessGeneration()V", at = @At("TAIL"))
    private void onPostProcessGeneration(CallbackInfo ci) {
        LevelChunk chunk = (LevelChunk) (Object) this;
        if (!(chunk.getLevel() instanceof ServerLevel serverLevel)) {
            return; // Server-side only
        }

        BlockSwapConfig config = BlockSwapConfig.getConfig(false);
        ProcessedChunksData data = BlockSwap.getProcessedChunksData(serverLevel);
        String newConfigHash = Integer.toString(config.swapEntries().hashCode());

        // Run deferred swaps for new chunks
        Swapper.runDeferredSwaps(chunk);

        // Run swaps for unprocessed chunks (retro_gen)
        if (config.retroGen() && !data.isChunkProcessed(chunk.getPos())) {
            BlockSwap.LOGGER.debug("Processing chunk {} for retro_gen", chunk.getPos());
            Swapper.runRetroGenerator(chunk);
            data.markChunkProcessed(chunk.getPos());
        } else if (config.redoGen() && !newConfigHash.equals(data.getConfigHash()) && !data.wasProcessedThisSession(chunk.getPos())) {
            BlockSwap.LOGGER.debug("Processing chunk {} for redo_gen", chunk.getPos());
            Swapper.runRetroGenerator(chunk);
            data.markProcessedThisSession(chunk.getPos());
        }
    }
}