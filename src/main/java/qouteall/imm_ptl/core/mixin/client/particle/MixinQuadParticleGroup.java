package qouteall.imm_ptl.core.mixin.client.particle;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.particle.BillboardParticleRenderer;
import net.minecraft.client.particle.BillboardParticle;
import net.minecraft.client.render.Frustum;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import qouteall.imm_ptl.core.render.context_management.RenderStates;

@Mixin({BillboardParticleRenderer.class})
public class MixinQuadParticleGroup {
   @WrapOperation(
      method = {"render"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/Frustum;intersectPoint(DDD)Z"
      )}
   )
   private boolean wrapParticleFrustumCheck(Frustum frustum, double x, double y, double z, Operation<Boolean> original, @Local BillboardParticle particle) {
      return !RenderStates.shouldRenderParticle(particle) ? false : (Boolean)original.call(new Object[]{frustum, x, y, z});
   }
}
