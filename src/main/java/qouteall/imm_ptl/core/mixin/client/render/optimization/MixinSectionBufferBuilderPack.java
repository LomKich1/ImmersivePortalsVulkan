package qouteall.imm_ptl.core.mixin.client.render.optimization;

import java.util.Map;
import java.util.function.Function;
import net.minecraft.client.render.BlockRenderLayer;
import net.minecraft.client.render.chunk.BlockBufferAllocatorStorage;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import qouteall.imm_ptl.core.IPGlobal;

@Mixin({BlockBufferAllocatorStorage.class})
public class MixinSectionBufferBuilderPack {
   @Redirect(
      method = {"<init>"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/util/Util;mapEnum(Ljava/lang/Class;Ljava/util/function/Function;)Ljava/util/Map;"
      )}
   )
   private Map<BlockRenderLayer, BufferAllocator> redirectMakeBuffers(Class<BlockRenderLayer> layerClass, Function<BlockRenderLayer, BufferAllocator> original) {
      return !IPGlobal.saveMemoryInBufferPack
         ? Util.mapEnum(layerClass, original)
         : Util.mapEnum(layerClass, layer -> new BufferAllocator(Math.min(128, layer.getBufferSize())));
   }
}
