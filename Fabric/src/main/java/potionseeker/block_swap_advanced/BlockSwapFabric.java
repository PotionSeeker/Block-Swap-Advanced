package potionseeker.block_swap_advanced;

import potionseeker.block_swap_advanced.network.NetworkHandler;
import net.fabricmc.api.ModInitializer;

public class BlockSwapFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        BlockSwap.initMod();
        NetworkHandler.init();
    }
}