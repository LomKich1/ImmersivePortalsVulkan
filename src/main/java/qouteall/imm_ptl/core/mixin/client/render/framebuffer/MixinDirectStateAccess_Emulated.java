package qouteall.imm_ptl.core.mixin.client.render.framebuffer;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import qouteall.imm_ptl.core.render.StencilDepthTextures;

@Mixin(
   targets = {"net/minecraft/client/gl/BufferManager$DefaultBufferManager"}
)
public class MixinDirectStateAccess_Emulated {
   @WrapOperation(
      method = {"setupFramebuffer"},
      at = @At(
         value = "INVOKE",
         target = "Lcom/mojang/blaze3d/opengl/GlStateManager;_glFramebufferTexture2D(IIIII)V",
         ordinal = 1
      )
   )
   private void wrapAttachDepthTexture(int target, int attachment, int textureTarget, int texture, int level, Operation<Void> original) {
      if (StencilDepthTextures.isStencilTexture(texture)) {
         original.call(new Object[]{target, 33306, textureTarget, texture, level});
      } else {
         original.call(new Object[]{target, attachment, textureTarget, texture, level});
         original.call(new Object[]{target, 36128, textureTarget, 0, level});
      }
   }
}
