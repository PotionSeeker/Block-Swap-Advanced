package potionseeker.block_swap_advanced.serialization;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public record BlockInfo(String name, Map<String, List<String>> properties) {
    public static final Codec<BlockInfo> CODEC = RecordCodecBuilder.create(builder ->
            builder.group(
                    Codec.STRING.fieldOf("Name").forGetter(BlockInfo::name),
                    Codec.unboundedMap(Codec.STRING, Codec.list(Codec.STRING))
                            .optionalFieldOf("Properties", new TreeMap<>())
                            .forGetter(BlockInfo::properties)
            ).apply(builder, BlockInfo::new)
    );
}