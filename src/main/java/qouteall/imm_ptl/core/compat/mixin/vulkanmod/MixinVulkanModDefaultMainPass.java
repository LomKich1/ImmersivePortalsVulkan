package qouteall.imm_ptl.core.compat.mixin.vulkanmod;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.compat.vulkanmod_compatibility.VulkanOffscreenTarget;

/**
 * VulkanMod's terrain layers, clouds and a few GL style paths call rebindMainTarget to "get back to
 * the screen". During a portal pass the target Immersive Portals wants is the secondary
 * framebuffer, so that is bound instead.
 */
@Pseudo
@Mixin(targets = "net.vulkanmod.vulkan.pass.DefaultMainPass", remap = false)
public class MixinVulkanModDefaultMainPass {
   @Inject(method = "rebindMainTarget", at = @At("HEAD"), cancellable = true)
   private void ip_stayOnPortalTarget(CallbackInfo ci) {
      if (VulkanOffscreenTarget.isActiveTargetOffscreen() && VulkanOffscreenTarget.bindActiveTarget()) {
         ci.cancel();
      }
   }
}
