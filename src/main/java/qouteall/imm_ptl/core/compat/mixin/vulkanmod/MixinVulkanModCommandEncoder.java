package qouteall.imm_ptl.core.compat.mixin.vulkanmod;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import qouteall.imm_ptl.core.compat.RenderBackendCompat;
import qouteall.q_misc_util.Helper;

/**
 * VkCommandEncoder decides whether a render pass or clear targets the window's framebuffer by
 * comparing with Minecraft's current main render target. Immersive Portals swaps that reference for
 * the secondary framebuffer during a portal pass, so VulkanMod would take its own framebuffer for
 * the secondary one. These comparisons have to see the window's real target.
 *
 * <p>The call is matched by its intermediary name because the target is another mod's compiled
 * code: class_310.method_1522 is MinecraftClient.getFramebuffer. The injection is not required to
 * match every site, because the released VulkanMod jar may differ from the sources this was written
 * against; the first calls log which VulkanMod methods actually run through the redirect.</p>
 */
@Pseudo
@Mixin(targets = "net.vulkanmod.render.engine.VkCommandEncoder", remap = false)
public class MixinVulkanModCommandEncoder {
   private static final Set<String> ip_loggedCallers = new HashSet<>();

   @Redirect(
      method = {"createRenderPass", "clearColorTexture", "clearColorAndDepthTextures"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/class_310;method_1522()Lnet/minecraft/class_276;",
         remap = false
      ),
      require = 0
   )
   private Framebuffer ip_realMainTarget(MinecraftClient client) {
      if (ip_loggedCallers.size() < 8) {
         String caller = StackWalker.getInstance()
            .walk(frames -> frames.skip(1).findFirst().map(f -> f.getMethodName()).orElse("?"));
         if (ip_loggedCallers.add(caller)) {
            Helper.log("[IP-VK] VkCommandEncoder." + caller + " compares against the real main target");
         }
      }
      return RenderBackendCompat.getRealMainTarget();
   }
}
