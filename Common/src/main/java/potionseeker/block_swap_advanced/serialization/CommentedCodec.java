package potionseeker.block_swap_advanced.serialization;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import potionseeker.block_swap_advanced.BlockSwap;

public class CommentedCodec {
    public static <T> Codec<T> of(Codec<T> codec, String fieldName, String comment, T defaultValue) {
        return Codec.of(
                codec,
                new Codec<T>() {
                    @Override
                    public <U> DataResult<Pair<T, U>> decode(DynamicOps<U> ops, U input) {
                        BlockSwap.LOGGER.info("Decoding field {} with input: {}", fieldName, input);
                        DataResult<Pair<T, U>> result = codec.decode(ops, input);
                        if (result.error().isPresent()) {
                            BlockSwap.LOGGER.error("Failed to decode field {}: {}", fieldName, result.error().get().message());
                            return DataResult.success(Pair.of(defaultValue, input));
                        }
                        return result;
                    }

                    @Override
                    public <U> DataResult<U> encode(T input, DynamicOps<U> ops, U prefix) {
                        if (!input.equals(defaultValue)) {
                            DataResult<U> result = codec.encode(input, ops, prefix);
                            if (result.error().isPresent()) {
                                BlockSwap.LOGGER.error("Failed to encode field {}: {}", fieldName, result.error().get().message());
                                return result;
                            }
                            return result;
                        }
                        return DataResult.success(prefix);
                    }
                }
        );
    }
}