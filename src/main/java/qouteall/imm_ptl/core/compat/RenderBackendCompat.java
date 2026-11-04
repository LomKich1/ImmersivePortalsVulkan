package qouteall.imm_ptl.core.compat;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import qouteall.imm_ptl.core.compat.vulkanmod_compatibility.VulkanChunkStatusRouter;

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

   /**
    * VulkanMod learns that a chunk's block data arrived from a mixin inside the vanilla
    * ClientChunkManager.loadChunkFromPacket. ImmPtlClientChunkMap overrides that method without
    * calling super, so the notification never happens and no section of the chunk is ever built
    * (and entities in it are culled, because VulkanMod answers isSectionCompiledAndVisible from the
    * built sections). Reported here for the world the chunk belongs to.
    */
   public static void notifyVulkanChunkDataReady(RegistryKey<World> dimension, int chunkX, int chunkZ) {
      if (!IS_NON_GL_RENDERER) {
         return;
      }
      VulkanChunkStatusRouter.withDimension(dimension, () ->
         VulkanChunkStatusRouter.forwardSet(chunkX, chunkZ, VulkanChunkStatusRouter.FLAG_DATA_READY));
   }

   /** Counterpart of {@link #notifyVulkanChunkDataReady} for chunk unloading. */
   public static void notifyVulkanChunkUnloaded(RegistryKey<World> dimension, int chunkX, int chunkZ) {
      if (!IS_NON_GL_RENDERER) {
         return;
      }
      VulkanChunkStatusRouter.withDimension(dimension, () ->
         VulkanChunkStatusRouter.forwardReset(chunkX, chunkZ, VulkanChunkStatusRouter.FLAG_DATA_READY));
   }

   // ---- the window's own render target ----

   private static Framebuffer realMainTarget = null;

   /**
    * Called by MixinMinecraft.ip_setFrameBuffer before the reference is replaced. The first swap
    * always starts from the window's real target (later swaps restore it), so that is what is kept.
    */
   public static void noteMainTargetSwap(Framebuffer current) {
      if (realMainTarget == null) {
         realMainTarget = current;
      }
   }

   /** The window's own render target, whatever Immersive Portals currently put in its place. */
   public static Framebuffer getRealMainTarget() {
      Framebuffer result = realMainTarget;
      return result != null ? result : MinecraftClient.getInstance().getFramebuffer();
   }
}
