package potionseeker.block_swap_advanced.mixin;

import potionseeker.block_swap_advanced.config.BlockSwapConfig;
import potionseeker.block_swap_advanced.swapper.Swapper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(WorldGenRegion.class)
public abstract class MixinWorldGenRegion {
    private static final Logger LOGGER = LogManager.getLogger("BlockSwap");

    @ModifyArg(
        method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/chunk/ChunkAccess;setBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;"
        ),
        index = 1
    )
    private BlockState modifyBlockState(BlockPos pos, BlockState state, boolean isMoving) {
        WorldGenRegion region = (WorldGenRegion) (Object) this;
        Level level = region.getLevel();
        BlockSwapConfig config = BlockSwapConfig.getConfig(false);

        if (config.contains(state)) {
            BlockState newState = Swapper.remapState(state, level, pos, false);
            if (!newState.equals(state)) {
                if (newState == null || newState.isAir()) {
                    LOGGER.warn("Invalid swap state for {} at {}: got air or null", state, pos);
                    return state;
                }
                LOGGER.debug("Swapped {} to {} at {}", state, newState, pos);
                return newState;
            }
        }
        return state;
    }
}