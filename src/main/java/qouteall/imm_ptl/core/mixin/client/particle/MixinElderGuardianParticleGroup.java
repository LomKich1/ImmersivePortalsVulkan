package qouteall.imm_ptl.core.mixin.client.particle;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Queue;
import java.util.stream.Stream;
import net.minecraft.client.particle.ElderGuardianParticle;
import net.minecraft.client.particle.ElderGuardianParticleRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import qouteall.imm_ptl.core.render.context_management.RenderStates;

@Mixin({ElderGuardianParticleRenderer.class})
public class MixinElderGuardianParticleGroup {
   @WrapOperation(
      method = {"render"},
      at = {@At(
         value = "INVOKE",
         target = "Ljava/util/Queue;stream()Ljava/util/stream/Stream;",
         remap = false
      )}
   )
   private Stream<ElderGuardianParticle> wrapParticleStream(Queue<ElderGuardianParticle> particles, Operation<Stream<ElderGuardianParticle>> original) {
      return ((Stream)original.call(new Object[]{particles})).filter(RenderStates::shouldRenderParticle);
   }
}
