package qouteall.imm_ptl.core.mixin.client.render;

import net.minecraft.client.render.chunk.BlockBufferAllocatorStorage;
import net.minecraft.client.render.chunk.ChunkBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({ChunkBuilder.class})
public interface IESectionRenderDispatcher {
   @Accessor("buffers")
   BlockBufferAllocatorStorage ip_getFixedBuffers();

   @Mutable
   @Accessor("buffers")
   void ip_setFixedBuffers(BlockBufferAllocatorStorage var1);
}
