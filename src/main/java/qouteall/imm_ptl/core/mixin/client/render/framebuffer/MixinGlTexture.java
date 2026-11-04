package qouteall.imm_ptl.core.mixin.client.render.framebuffer;

import net.minecraft.client.texture.GlTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.render.StencilDepthTextures;

@Mixin({GlTexture.class})
public class MixinGlTexture {
   @Inject(
      method = {"free"},
      at = @At("HEAD")
   )
   private void onDestroyImmediately(CallbackInfo ci) {
      StencilDepthTextures.unregister(((GlTexture)(Object)this).getGlId());
   }
}
