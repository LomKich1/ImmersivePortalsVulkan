package qouteall.imm_ptl.core.mixin.client.render;

import java.util.Collection;
import net.minecraft.client.gl.CompiledShaderPipeline;
import net.minecraft.client.gl.GlCommandEncoder;
import net.minecraft.client.gl.RenderPassImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.render.FrontClipping;

@Mixin({GlCommandEncoder.class})
public class MixinGlCommandEncoder {
   @Inject(
      method = {"setupRenderPass"},
      at = {@At("RETURN")}
   )
   private void onTrySetupReturn(RenderPassImpl renderPass, Collection<String> dynamicUniforms, CallbackInfoReturnable<Boolean> cir) {
      if (cir.getReturnValueZ()) {
         CompiledShaderPipeline pipeline = ((IEGlRenderPass)renderPass).ip_getPipeline();
         if (pipeline != null) {
            FrontClipping.onProgramBound(pipeline.program());
         }
      }
   }
}
