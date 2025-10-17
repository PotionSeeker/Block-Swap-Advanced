package potionseeker.block_swap_advanced;

import potionseeker.block_swap_advanced.config.MissingBlockIDsConfig;
import potionseeker.block_swap_advanced.network.NetworkHandler;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public class BlockSwapFabric implements ModInitializer {
    
    @Override
    public void onInitialize() {
        BlockSwap.init(FabricLoader.getInstance().getConfigDir().resolve(BlockSwap.MOD_ID));
        NetworkHandler.init();
        MissingBlockIDsConfig.getConfig(true);
    }
}
