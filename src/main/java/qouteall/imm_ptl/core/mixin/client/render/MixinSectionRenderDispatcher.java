package qouteall.imm_ptl.core.mixin.client.render;

import net.minecraft.client.render.BufferBuilderStorage;
import net.minecraft.client.render.chunk.BlockBufferBuilderPool;
import net.minecraft.client.render.chunk.ChunkBuilder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.compat.sodium_compatibility.SodiumInterface;

@Mixin({ChunkBuilder.class})
public class MixinSectionRenderDispatcher {
   @Shadow
   @Final
   private BlockBufferBuilderPool buffersPool;

   @Redirect(
      method = {"<init>"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/render/BufferBuilderStorage;getBlockBufferBuildersPool()Lnet/minecraft/client/render/chunk/BlockBufferBuilderPool;"
      )
   )
   private BlockBufferBuilderPool redirectSectionBufferBuilderPool(BufferBuilderStorage instance) {
      if (ClientWorldLoader.getIsCreatingClientWorld() && !SodiumInterface.invoker.isSodiumPresent()) {
         int processors = Runtime.getRuntime().availableProcessors();
         return BlockBufferBuilderPool.allocate(processors);
      } else {
         return instance.getBlockBufferBuildersPool();
      }
   }
}
