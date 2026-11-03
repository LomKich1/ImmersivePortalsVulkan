package qouteall.imm_ptl.core.mixin.client.render;

import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.client.render.BuiltChunkStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({BuiltChunkStorage.class})
public interface IEViewArea {
   @Accessor("sectionPos")
   void ip_setCameraSectionPos(ChunkSectionPos var1);
}
