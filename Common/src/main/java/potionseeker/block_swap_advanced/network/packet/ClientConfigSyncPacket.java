package potionseeker.block_swap_advanced.network.packet;

import potionseeker.block_swap_advanced.config.BlockSwapConfig;
import potionseeker.block_swap_advanced.network.packet.util.PacketHandle;
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
        BlockSwapConfig.getConfig(blockSwapConfig);
    }
}