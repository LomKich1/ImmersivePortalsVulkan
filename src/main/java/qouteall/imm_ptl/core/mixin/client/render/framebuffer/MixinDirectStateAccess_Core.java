package qouteall.imm_ptl.core.mixin.client.render.framebuffer;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import qouteall.imm_ptl.core.render.StencilDepthTextures;

@Mixin(
   targets = {"net/minecraft/client/gl/BufferManager$ARBBufferManager"}
)
public class MixinDirectStateAccess_Core {
   @WrapOperation(
      method = {"setupFramebuffer"},
      at = {@At(
         value = "INVOKE",
         target = "Lorg/lwjgl/opengl/ARBDirectStateAccess;glNamedFramebufferTexture(IIII)V",
         ordinal = 1,
         remap = false
      )}
   )
   private void wrapAttachDepthTexture(int framebuffer, int attachment, int texture, int level, Operation<Void> original) {
      if (StencilDepthTextures.isStencilTexture(texture)) {
         original.call(new Object[]{framebuffer, 33306, texture, level});
      } else {
         original.call(new Object[]{framebuffer, attachment, texture, level});
         original.call(new Object[]{framebuffer, 36128, 0, level});
      }
   }
}
