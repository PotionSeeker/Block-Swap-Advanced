package potionseeker.block_swap_advanced;

import potionseeker.block_swap_advanced.network.NetworkHandler;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

@Mod(BlockSwap.MOD_ID)
public class BlockSwapForge {

    public BlockSwapForge() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::commonSetup);
        new BlockSwap();
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        BlockSwap.initMod();
        NetworkHandler.init();
    }
}