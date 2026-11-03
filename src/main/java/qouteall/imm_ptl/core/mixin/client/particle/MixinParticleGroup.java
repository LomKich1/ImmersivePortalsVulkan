package qouteall.imm_ptl.core.mixin.client.particle;

import net.minecraft.client.particle.ParticleRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ParticleRenderer.class})
public class MixinParticleGroup {
   @Inject(
      method = {"tickParticle"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onTickParticle(Particle particle, CallbackInfo ci) {
      if (((IEParticle)particle).portal_getWorld() != MinecraftClient.getInstance().world) {
         ci.cancel();
      }
   }
}
