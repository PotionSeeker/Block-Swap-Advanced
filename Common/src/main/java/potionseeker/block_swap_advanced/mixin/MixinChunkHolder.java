package potionseeker.block_swap_advanced.mixin;

import potionseeker.block_swap_advanced.BlockSwap;
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
        if (config.retroGen() && !BlockSwap.getProcessedChunksData(serverLevel).isChunkProcessed(chunk.getPos())) {
            Swapper.runRetroGenerator(chunk);
        }
    }
}