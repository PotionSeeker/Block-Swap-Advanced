package potionseeker.block_swap_advanced.mixin;

import potionseeker.block_swap_advanced.config.BlockSwapConfig;
import potionseeker.block_swap_advanced.network.NetworkHandler;
import potionseeker.block_swap_advanced.network.packet.ClientConfigSyncPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerList.class)
public abstract class MixinPlayerList {
    @Inject(method = {"sendLevelInfo(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/server/level/ServerLevel;)V"}, at = @At(value = "HEAD"))
    private void sendServerConfig(ServerPlayer playerIn, ServerLevel worldIn, CallbackInfo ci) {
        NetworkHandler.sendToClient(playerIn, new ClientConfigSyncPacket(BlockSwapConfig.getConfig(true)));
    }
}