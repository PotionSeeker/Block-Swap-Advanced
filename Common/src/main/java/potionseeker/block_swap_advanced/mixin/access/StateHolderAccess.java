package potionseeker.block_swap_advanced.mixin.access;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.state.StateHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(StateHolder.class)
public interface StateHolderAccess<O, S> {


    @Accessor("owner")
    O blockSwap_GetOwner();

    @Accessor("propertiesCodec")
    MapCodec<S> blockSwap_getPropertiesCodec();
}
