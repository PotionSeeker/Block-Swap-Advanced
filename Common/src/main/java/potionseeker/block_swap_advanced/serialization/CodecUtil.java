package potionseeker.block_swap_advanced.serialization;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import potionseeker.block_swap_advanced.BlockSwap;

public class CodecUtil {
    public static final MapCodec<Block> BLOCK_MAP_CODEC = RecordCodecBuilder.mapCodec(builder -> builder.group(
            Codec.STRING.fieldOf("Name").forGetter(block -> BuiltInRegistries.BLOCK.getKey(block).toString())
    ).apply(builder, name -> BuiltInRegistries.BLOCK.get(new ResourceLocation(name))));

    public static final Codec<Block> BLOCK_CODEC = BLOCK_MAP_CODEC.codec();

    public static final MapCodec<BlockState> BLOCK_STATE_MAP_CODEC = RecordCodecBuilder.mapCodec(builder -> builder.group(
            Codec.STRING.fieldOf("Name").forGetter(state -> BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString())
    ).apply(builder, name -> {
        Block block = BuiltInRegistries.BLOCK.get(new ResourceLocation(name));
        if (block == null) {
            BlockSwap.LOGGER.error("Invalid block ID: {}", name);
            throw new IllegalArgumentException("Invalid block ID: " + name);
        }
        BlockState state = block.defaultBlockState();
        BlockSwap.LOGGER.info("Deserialized BlockState: name={}, state={}", name, state);
        return state;
    }));

    public static final Codec<BlockState> BLOCK_STATE_CODEC = BLOCK_STATE_MAP_CODEC.codec();
}