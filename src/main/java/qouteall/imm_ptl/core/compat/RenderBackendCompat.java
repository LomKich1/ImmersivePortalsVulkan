package qouteall.imm_ptl.core.compat;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Tells the rest of the mod which graphics backend Minecraft is running on.
 *
 * <p>With VulkanMod there is no OpenGL context. VulkanMod itself intercepts a part of the
 * OpenGL calls (glEnable/glDisable, glViewport, glClear, textures, buffers, GlStateManager._*)
 * but not all of them. Anything it does not intercept (stencil, glCullFace, queries, uniforms,
 * glReadPixels, vertex arrays) would crash with "No GLCapabilities instance set". Code that
 * uses such calls has to check this class first.</p>
 */
public class RenderBackendCompat {
   public static final String VULKAN_MOD_ID = "vulkanmod";

   /** True when VulkanMod (no OpenGL context) is loaded. */
   public static final boolean IS_NON_GL_RENDERER = FabricLoader.getInstance().isModLoaded(VULKAN_MOD_ID);

   private RenderBackendCompat() {
   }

   /** Raw LWJGL GL calls that VulkanMod does not emulate are safe to call. */
   public static boolean isRawGlAvailable() {
      return !IS_NON_GL_RENDERER;
   }

   /** Stencil based portal masking. */
   public static boolean supportsStencil() {
      return !IS_NON_GL_RENDERER;
   }

   /** GL occlusion queries. */
   public static boolean supportsOcclusionQuery() {
      return !IS_NON_GL_RENDERER;
   }

   public static String getBackendName() {
      return IS_NON_GL_RENDERER ? "Vulkan (VulkanMod)" : "OpenGL";
   }
}
