package qouteall.imm_ptl.core.compat.vulkanmod_compatibility;

import com.mojang.blaze3d.opengl.GlStateManager;
import java.lang.reflect.Method;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import qouteall.imm_ptl.core.compat.RenderBackendCompat;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.platform.ClientPlatform;

/**
 * Keeps VulkanMod's terrain pass on the framebuffer Immersive Portals is rendering into.
 *
 * <p>Immersive Portals renders a portal view by replacing Minecraft's main render target with a
 * secondary framebuffer. VulkanMod 0.6.8 treats "the main render target" specially in two ways:
 * {@code VkCommandEncoder} compares textures with {@code getMainRenderTarget()} to decide whether
 * a pass goes to its own swapchain framebuffer (handled by MixinVulkanModCommandEncoder), and
 * {@code WorldRenderer.renderSectionLayer} calls {@code MainPass.rebindMainTarget()} at the start of
 * every terrain layer, which ends whatever pass is bound and begins the window's one. Left alone,
 * the terrain of a portal view is drawn straight onto the screen with the portal's camera.</p>
 *
 * <p>Instead of cancelling the rebind (which could leave no pass bound at all), this binds the
 * framebuffer that is currently Minecraft's render target, the way
 * {@code VkCommandEncoder.createRenderPass} does for any other target: bind its VkFbo, then set a
 * GL style viewport, which VulkanMod records flipped to Vulkan convention.</p>
 */
public class VulkanOffscreenTarget {
   private static boolean resolved = false;
   private static boolean available = false;
   private static Method getFbo;
   private static Method bind;
   private static boolean inBind = false;
   private static boolean loggedBind = false;

   private VulkanOffscreenTarget() {
   }

   private static void resolve() {
      if (resolved) {
         return;
      }
      resolved = true;
      try {
         Class<?> textureView = Class.forName("net.vulkanmod.render.engine.VkTextureView");
         for (Method m : textureView.getMethods()) {
            if (m.getName().equals("getFbo") && m.getParameterCount() == 1) {
               getFbo = m;
            }
         }
         bind = Class.forName("net.vulkanmod.render.engine.VkFbo").getMethod("bind");
         available = getFbo != null;
      } catch (Throwable e) {
         available = false;
         Helper.err("[Immersive Portals] cannot bind offscreen target in VulkanMod: " + e);
      }
   }

   /** True while Minecraft's render target is not the window's own, i.e. in a portal or GUI portal pass. */
   public static boolean isActiveTargetOffscreen() {
      if (!RenderBackendCompat.IS_NON_GL_RENDERER) {
         return false;
      }
      Framebuffer real = RenderBackendCompat.getRealMainTarget();
      return real != null && MinecraftClient.getInstance().getFramebuffer() != real;
   }

   /**
    * Binds the active offscreen target. Returns false when that was not possible, in which case
    * the caller must let VulkanMod run its own rebind.
    */
   public static boolean bindActiveTarget() {
      if (inBind) {
         return false;
      }
      resolve();
      if (!available) {
         return false;
      }
      Framebuffer target = MinecraftClient.getInstance().getFramebuffer();
      inBind = true;
      try {
         Object colorView = ClientPlatform.get().unwrapGpuTextureView(target.getColorAttachmentView());
         Object depth = target.getDepthAttachment() == null
            ? null
            : ClientPlatform.get().unwrapGpuTexture(target.getDepthAttachment());
         Object fbo = getFbo.invoke(colorView, depth);
         bind.invoke(fbo);
         GlStateManager._viewport(0, 0, target.textureWidth, target.textureHeight);
         if (!loggedBind) {
            loggedBind = true;
            Helper.log("[IP-VK] terrain pass bound to offscreen target " + target.textureWidth + "x" + target.textureHeight);
         }
         return true;
      } catch (Throwable e) {
         available = false;
         Helper.err("[Immersive Portals] binding offscreen target failed, falling back to VulkanMod: " + e);
         return false;
      } finally {
         inBind = false;
      }
   }
}
