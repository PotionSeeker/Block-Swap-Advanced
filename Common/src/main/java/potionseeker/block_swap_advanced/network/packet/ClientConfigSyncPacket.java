package potionseeker.block_swap_advanced.network.packet;

import potionseeker.block_swap_advanced.BlockSwap;
import potionseeker.block_swap_advanced.config.BlockSwapConfig;
import potionseeker.block_swap_advanced.network.packet.util.PacketHandle;
import potionseeker.block_swap_advanced.swapper.Swapper;
import net.minecraft.network.FriendlyByteBuf;

public class ClientConfigSyncPacket implements PacketHandle {

    private final BlockSwapConfig blockSwapConfig;

    public ClientConfigSyncPacket(BlockSwapConfig blockSwapConfig) {
        this.blockSwapConfig = blockSwapConfig;
    }

    public static void writeToPacket(ClientConfigSyncPacket packet, FriendlyByteBuf buf) {
        buf.writeJsonWithCodec(BlockSwapConfig.CODEC, packet.blockSwapConfig);
    }

    public static ClientConfigSyncPacket readFromPacket(FriendlyByteBuf buf) {
        return new ClientConfigSyncPacket(buf.readJsonWithCodec(BlockSwapConfig.CODEC));
    }

    @Override
    public void handle() {
        BlockSwap.LOGGER.info("Client received config sync: retroGen={}, redoGen={}, swapEntries={}",
                blockSwapConfig.retroGen(), blockSwapConfig.redoGen(), blockSwapConfig.swapEntries().size());
        Swapper.updateConfig(blockSwapConfig); // Update Swapper with synced config
    }
}