package qouteall.imm_ptl.core.compat.mixin.vulkanmod;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import qouteall.imm_ptl.core.compat.RenderBackendCompat;

/**
 * VkCommandEncoder decides whether a render pass or clear targets the window's framebuffer by
 * comparing with Minecraft's current main render target. Immersive Portals swaps that reference for
 * the secondary framebuffer during a portal pass, so VulkanMod would take its own framebuffer for
 * the secondary one: clears (including the magenta clear of the portal buffer) and every pass
 * would land on the screen. These three comparisons have to see the window's real target.
 *
 * <p>The call is matched by its intermediary name because the target is another mod's compiled
 * code: class_310.method_1522 is MinecraftClient.getFramebuffer.</p>
 */
@Pseudo
@Mixin(targets = "net.vulkanmod.render.engine.VkCommandEncoder", remap = false)
public class MixinVulkanModCommandEncoder {
   @Redirect(
      method = {"createRenderPass", "clearColorTexture", "clearColorAndDepthTextures"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/class_310;method_1522()Lnet/minecraft/class_276;",
         remap = false
      ),
      require = 3,
      expect = 3
   )
   private Framebuffer ip_realMainTarget(MinecraftClient client) {
      return RenderBackendCompat.getRealMainTarget();
   }
}
