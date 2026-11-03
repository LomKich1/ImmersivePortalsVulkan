package qouteall.imm_ptl.core.mixin.client.render.framebuffer;

import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.client.gl.WindowFramebuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.ducks.IEFrameBuffer;
import qouteall.imm_ptl.core.render.StencilDepthTextures;

@Mixin({WindowFramebuffer.class})
public abstract class MixinMainTarget {
   @Inject(
      method = {"createDepthAttachment"},
      at = {@At("HEAD")}
   )
   private void onBeginAllocateDepthAttachment(CallbackInfoReturnable<GpuTexture> cir) {
      StencilDepthTextures.creatingStencilDepth = ((IEFrameBuffer)this).ip_getIsStencilBufferEnabled();
   }

   @Inject(
      method = {"createDepthAttachment"},
      at = {@At("RETURN")}
   )
   private void onEndAllocateDepthAttachment(CallbackInfoReturnable<GpuTexture> cir) {
      StencilDepthTextures.creatingStencilDepth = false;
   }
}
