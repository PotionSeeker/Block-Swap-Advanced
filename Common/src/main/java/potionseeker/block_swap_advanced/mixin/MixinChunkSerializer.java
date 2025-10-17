package potionseeker.block_swap_advanced.mixin;

import potionseeker.block_swap_advanced.config.MissingBlockIDsConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Map;

@Mixin(ChunkSerializer.class)
public class MixinChunkSerializer {
    @Redirect(
            method = "read(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/ai/village/poi/PoiManager;Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/world/level/chunk/ProtoChunk;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/nbt/CompoundTag;getCompound(Ljava/lang/String;)Lnet/minecraft/nbt/CompoundTag;",
                    ordinal = 0
            )
    )
    private static CompoundTag repairStates(CompoundTag tag, String key) {
        CompoundTag blockStates = tag.getCompound(key);
        if (key.equals("block_states")) {
            Map<String, Block> idRemapper = MissingBlockIDsConfig.getConfig(false).idRemapper();
            for (Tag paletteTag : blockStates.getList("palette", Tag.TAG_COMPOUND)) {
                if (paletteTag instanceof CompoundTag paletteEntry) {
                    String name = paletteEntry.getString("Name");
                    if (idRemapper.containsKey(name)) {
                        paletteEntry.remove("Name");
                        paletteEntry.putString("Name", BuiltInRegistries.BLOCK.getKey(idRemapper.get(name)).toString());
                    }
                }
            }
        }
        return blockStates;
    }
}