package potionseeker.block_swap_advanced.mixin;

import potionseeker.block_swap_advanced.BlockSwap;
import potionseeker.block_swap_advanced.config.BlockSwapConfig;
import potionseeker.block_swap_advanced.swapper.Swapper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(Level.class)
public abstract class MixinLevel {
    @Shadow
    public abstract boolean isClientSide();

    @ModifyArg(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z"), index = 1)
    private BlockState modifyBlockState3(BlockPos pos, BlockState state, int flags, int maxUpdateDepth) {
        BlockSwap.LOGGER.debug("MixinLevel.modifyBlockState3 called: state={}, pos={}, isClientSide={}", state, pos, isClientSide());
        if (!isClientSide() && BlockSwap.CONFIG_PATH != null && BlockSwapConfig.getConfig(false).contains(state)) {
            BlockState newState = Swapper.remapState(state, (Level) (Object) this, pos, true);
            return newState;
        }
        return state;
    }

    @ModifyArg(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/LevelChunk;setBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;"), index = 1)
    private BlockState modifyBlockState4(BlockPos pos, BlockState state, boolean lock) {
        BlockSwap.LOGGER.debug("MixinLevel.modifyBlockState4 called: state={}, pos={}, isClientSide={}", state, pos, isClientSide());
        if (!isClientSide() && BlockSwap.CONFIG_PATH != null && BlockSwapConfig.getConfig(false).contains(state)) {
            BlockState newState = Swapper.remapState(state, (Level) (Object) this, pos, true);
            return newState;
        }
        return state;
    }
}