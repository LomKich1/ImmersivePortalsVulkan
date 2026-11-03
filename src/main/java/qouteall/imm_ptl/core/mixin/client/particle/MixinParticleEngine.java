package qouteall.imm_ptl.core.mixin.client.particle;

import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.SubmittableBatch;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.ducks.IEParticleManager;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import qouteall.imm_ptl.core.render.context_management.RenderStates;

@Mixin({ParticleManager.class})
public class MixinParticleEngine implements IEParticleManager {
   @Shadow
   protected ClientWorld world;

   @Inject(
      method = {"addToBatch"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onBeginExtractingParticles(SubmittableBatch particlesRenderState, Frustum frustum, Camera camera, float partialTick, CallbackInfo ci) {
      if (PortalRendering.isRendering() && RenderStates.getRenderedPortalNum() > 4) {
         ci.cancel();
      }
   }

   @Override
   public void ip_setWorld(ClientWorld world_) {
      this.world = world_;
   }
}
