package qouteall.imm_ptl.core.mixin.client.render;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.render.WorldRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.IPCGlobal;

@Mixin(
   value = {WorldRenderer.class},
   priority = 900
)
public class MixinLevelRenderer_BeforeIris {
   @Inject(
      method = {"lambda$addMainPass$1", "method_62214"},
      at = @At(
         value = "CONSTANT",
         args = {"stringValue=translucent"}
      ),
      require = 1
   )
   private void iris$beginTranslucents(CallbackInfo ci, @Local(argsOnly = true) Matrix4f modelView) {
      IPCGlobal.renderer.onBeginIrisTranslucentRendering(modelView);
   }
}
