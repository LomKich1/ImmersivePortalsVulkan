package qouteall.imm_ptl.core.compat.vulkanmod_compatibility;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.world.ClientWorld;
import qouteall.imm_ptl.core.compat.RenderBackendCompat;
import qouteall.q_misc_util.Helper;

/**
 * Reflection access to the parts of VulkanMod 0.6.8 (Minecraft 1.21.11) that the chunk status
 * bridge drives. VulkanMod is not a compile time dependency, so nothing here references its
 * classes in source. Any failed lookup switches the whole bridge off instead of guessing.
 *
 * <p>Only the members needed for the chunk status bridge are resolved at this stage. The terrain
 * context members (constructing a second terrain renderer, task dispatcher sharing and so on) come
 * with VulkanTerrainBridge. Note that in 0.6.8 {@code WorldRenderer.init} takes five arguments, so
 * the constructor lookup of the 1.21.1 fork does not apply.</p>
 */
public class VulkanModAccess {
   private static boolean resolved = false;
   private static boolean available = false;

   private static Field chunkStatusMapInstanceField;
   private static Constructor<?> chunkStatusMapConstructor;
   private static Method chunkStatusMapSet;
   private static Method chunkStatusMapReset;
   private static Method chunkStatusMapReady;

   private static Field worldRendererInstanceField;
   private static Field worldRendererLevelField;
   private static Field worldRendererSectionGridField;
   private static Field worldRendererGraphNeedsUpdateField;
   private static Method sectionGridGetRenderSectionsAt;
   private static Method renderSectionSetDirty;
   private static Method worldRendererSetLevel;
   private static Method worldRendererSetupRenderer;

   private static boolean frontFaceResolved = false;
   private static Field frontFaceField;

   private static String lastReportedFailure = null;

   private VulkanModAccess() {
   }

   public static boolean isAvailable() {
      resolve();
      return available;
   }

   private static void resolve() {
      if (resolved) {
         return;
      }
      resolved = true;
      if (!RenderBackendCompat.IS_NON_GL_RENDERER) {
         return;
      }
      try {
         Class<?> statusMap = Class.forName("net.vulkanmod.render.chunk.ChunkStatusMap");
         chunkStatusMapInstanceField = statusMap.getDeclaredField("INSTANCE");
         chunkStatusMapInstanceField.setAccessible(true);
         chunkStatusMapConstructor = statusMap.getDeclaredConstructor(int.class);
         chunkStatusMapConstructor.setAccessible(true);
         chunkStatusMapSet = statusMap.getDeclaredMethod("setChunkStatus", int.class, int.class, byte.class);
         chunkStatusMapReset = statusMap.getDeclaredMethod("resetChunkStatus", int.class, int.class, byte.class);
         chunkStatusMapReady = statusMap.getDeclaredMethod("chunkRenderReady", int.class, int.class);

         Class<?> worldRenderer = Class.forName("net.vulkanmod.render.chunk.WorldRenderer");
         worldRendererInstanceField = worldRenderer.getDeclaredField("INSTANCE");
         worldRendererInstanceField.setAccessible(true);
         worldRendererLevelField = worldRenderer.getDeclaredField("level");
         worldRendererLevelField.setAccessible(true);
         worldRendererSectionGridField = worldRenderer.getDeclaredField("sectionGrid");
         worldRendererSectionGridField.setAccessible(true);
         worldRendererGraphNeedsUpdateField = worldRenderer.getDeclaredField("graphNeedsUpdate");
         worldRendererGraphNeedsUpdateField.setAccessible(true);

         worldRendererSetLevel = worldRenderer.getDeclaredMethod("setLevel", ClientWorld.class);
         worldRendererSetupRenderer = worldRenderer.getDeclaredMethod(
            "setupRenderer", Camera.class, Frustum.class, boolean.class, boolean.class);

         Class<?> sectionGrid = Class.forName("net.vulkanmod.render.chunk.SectionGrid");
         sectionGridGetRenderSectionsAt = sectionGrid.getDeclaredMethod("getRenderSectionsAt", int.class, int.class);
         Class<?> renderSection = Class.forName("net.vulkanmod.render.chunk.RenderSection");
         renderSectionSetDirty = renderSection.getDeclaredMethod("setDirty", boolean.class);

         available = true;
         Helper.log("[Immersive Portals] VulkanMod chunk status bridge ready");
      } catch (Throwable e) {
         available = false;
         Helper.err("[Immersive Portals] cannot drive VulkanMod chunk status map: " + e);
      }
   }

   // ---- face winding ----

   /**
    * Sets VulkanMod's global front face (it is encoded into the pipeline state at every draw).
    * Clockwise is what GL's glCullFace(GL_FRONT) amounts to with back face culling on, which is
    * what a view through an odd number of mirrors needs. Returns false when it cannot be done.
    */
   public static boolean setFrontFaceClockwise(boolean clockwise) {
      if (!frontFaceResolved) {
         frontFaceResolved = true;
         try {
            frontFaceField = Class.forName("net.vulkanmod.vulkan.VRenderSystem").getDeclaredField("frontFace");
            frontFaceField.setAccessible(true);
         } catch (Throwable e) {
            frontFaceField = null;
            Helper.err("[Immersive Portals] cannot switch VulkanMod's face winding for mirrors: " + e);
         }
      }
      if (frontFaceField == null) {
         return false;
      }
      try {
         // VK_FRONT_FACE_COUNTER_CLOCKWISE = 0, VK_FRONT_FACE_CLOCKWISE = 1
         frontFaceField.setInt(null, clockwise ? 1 : 0);
         return true;
      } catch (Throwable e) {
         frontFaceField = null;
         return false;
      }
   }

   // ---- chunk status map ----

   public static Object newChunkStatusMap(int renderDistance) {
      if (!isAvailable()) {
         return null;
      }
      try {
         return chunkStatusMapConstructor.newInstance(Math.max(renderDistance, 1));
      } catch (Throwable e) {
         reportOnce("new chunk status map", e);
         return null;
      }
   }

   public static Object getChunkStatusMap() {
      if (!isAvailable()) {
         return null;
      }
      try {
         return chunkStatusMapInstanceField.get(null);
      } catch (Throwable e) {
         return null;
      }
   }

   public static void setChunkStatusMap(Object map) {
      if (!isAvailable()) {
         return;
      }
      try {
         chunkStatusMapInstanceField.set(null, map);
      } catch (Throwable e) {
         reportOnce("set chunk status map", e);
      }
   }

   public static void chunkStatusSet(Object map, int x, int z, byte flag) {
      invoke(chunkStatusMapSet, map, x, z, flag, "set chunk status");
   }

   public static void chunkStatusReset(Object map, int x, int z, byte flag) {
      invoke(chunkStatusMapReset, map, x, z, flag, "reset chunk status");
   }

   public static boolean chunkStatusReady(Object map, int x, int z) {
      if (map == null || !isAvailable()) {
         return false;
      }
      try {
         return (Boolean) chunkStatusMapReady.invoke(map, x, z);
      } catch (Throwable e) {
         return false;
      }
   }

   private static void invoke(Method method, Object map, int x, int z, byte flag, String what) {
      if (map == null || !isAvailable()) {
         return;
      }
      try {
         method.invoke(map, x, z, flag);
      } catch (Throwable e) {
         reportOnce(what, e);
      }
   }

   // ---- the shared terrain renderer ----

   public static Object getWorldRenderer() {
      if (!isAvailable()) {
         return null;
      }
      try {
         return worldRendererInstanceField.get(null);
      } catch (Throwable e) {
         return null;
      }
   }

   public static ClientWorld getWorldRendererLevel(Object worldRenderer) {
      if (!isAvailable() || worldRenderer == null) {
         return null;
      }
      try {
         Object level = worldRendererLevelField.get(worldRenderer);
         return level instanceof ClientWorld ? (ClientWorld) level : null;
      } catch (Throwable e) {
         return null;
      }
   }

   /** Calls VulkanMod's own WorldRenderer.setLevel (rebuilds its section grid for that world). */
   public static void bindWorldRendererLevel(Object worldRenderer, ClientWorld level) {
      if (!isAvailable() || worldRenderer == null) {
         return;
      }
      try {
         worldRendererSetLevel.invoke(worldRenderer, level);
      } catch (Throwable e) {
         reportOnce("bind terrain level", e);
      }
   }

   /** Runs VulkanMod's per frame terrain setup (culling and section scheduling) for a camera. */
   public static void runSetupRenderer(Object worldRenderer, Camera camera, Frustum frustum, boolean spectator) {
      if (!isAvailable() || worldRenderer == null) {
         return;
      }
      try {
         worldRendererSetupRenderer.invoke(worldRenderer, camera, frustum, false, spectator);
      } catch (Throwable e) {
         reportOnce("run terrain setup", e);
      }
   }

   public static Object getSectionGrid(Object worldRenderer) {
      if (!isAvailable() || worldRenderer == null) {
         return null;
      }
      try {
         return worldRendererSectionGridField.get(worldRenderer);
      } catch (Throwable e) {
         return null;
      }
   }

   public static void setGraphNeedsUpdate(Object worldRenderer, boolean value) {
      if (!isAvailable() || worldRenderer == null) {
         return;
      }
      try {
         worldRendererGraphNeedsUpdateField.setBoolean(worldRenderer, value);
      } catch (Throwable e) {
         reportOnce("set graph needs update", e);
      }
   }

   @SuppressWarnings("unchecked")
   public static Iterable<Object> getRenderSectionsAt(Object sectionGrid, int chunkX, int chunkZ) {
      if (!isAvailable() || sectionGrid == null) {
         return Collections.emptyList();
      }
      try {
         return (Iterable<Object>) sectionGridGetRenderSectionsAt.invoke(sectionGrid, chunkX, chunkZ);
      } catch (Throwable e) {
         return Collections.emptyList();
      }
   }

   public static void setSectionDirty(Object renderSection, boolean playerChanged) {
      if (!isAvailable() || renderSection == null) {
         return;
      }
      try {
         renderSectionSetDirty.invoke(renderSection, playerChanged);
      } catch (Throwable e) {
         reportOnce("set section dirty", e);
      }
   }

   private static void reportOnce(String what, Throwable e) {
      if (what.equals(lastReportedFailure)) {
         return;
      }
      lastReportedFailure = what;
      Helper.err("[Immersive Portals] VulkanMod access failed while trying to " + what + ": " + e);
   }
}
