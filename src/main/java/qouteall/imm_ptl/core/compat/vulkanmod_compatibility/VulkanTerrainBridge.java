package qouteall.imm_ptl.core.compat.vulkanmod_compatibility;

import java.lang.reflect.Field;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.world.ClientWorld;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.compat.RenderBackendCompat;
import qouteall.q_misc_util.Helper;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;

/**
 * Terrain side of the VulkanMod bridge.
 *
 * <p>This is the minimal first version that ships together with the chunk status bridge: it only
 * knows the single terrain renderer VulkanMod creates and can put the sections of a chunk column
 * back on its dirty list. The per dimension terrain contexts (one VulkanMod WorldRenderer per
 * client world, level binding suppression for secondary worlds, task dispatcher sharing) are the
 * next stage and replace the body of {@link #reDirtySections}.</p>
 *
 * <p>VulkanMod has one WorldRenderer shared by every LevelRenderer, and its LevelRenderer hooks
 * call setLevel/allChanged on it. Immersive Portals creates a LevelRenderer per dimension and
 * calls both on each, so a freshly loaded nether would rebind the shared terrain to the nether and
 * the overworld terrain would vanish. The shared renderer is therefore kept bound to the world the
 * player is in (Minecraft's current world outside portal passes); calls on behalf of other worlds
 * are refused, and a portal pass into another world draws no terrain until that world gets a
 * terrain context of its own.</p>
 */
public class VulkanTerrainBridge {
   private VulkanTerrainBridge() {
   }

   /**
    * VulkanMod's SectionGraph.scheduleRebuilds() marks a section "not dirty" even when its compile
    * task was refused because the chunk was not render-ready yet, so the section is never retried.
    * Called by the router when a chunk becomes ready.
    */
   public static void reDirtySections(RegistryKey<World> dimension, int chunkX, int chunkZ) {
      if (!VulkanModAccess.isAvailable()) {
         return;
      }
      Object worldRenderer = VulkanModAccess.getWorldRenderer();
      if (worldRenderer == null) {
         return;
      }
      ClientWorld boundLevel = VulkanModAccess.getWorldRendererLevel(worldRenderer);
      // until per dimension contexts exist, only the dimension the renderer is bound to can be fixed
      if (boundLevel == null || !boundLevel.getRegistryKey().equals(dimension)) {
         return;
      }
      Object sectionGrid = VulkanModAccess.getSectionGrid(worldRenderer);
      if (sectionGrid == null) {
         return;
      }
      for (Object section : VulkanModAccess.getRenderSectionsAt(sectionGrid, chunkX, chunkZ)) {
         if (section != null) {
            VulkanModAccess.setSectionDirty(section, false);
         }
      }
      VulkanModAccess.setGraphNeedsUpdate(worldRenderer, true);
   }

   private static int loggedRefusals = 0;

   private static ClientWorld currentWorld() {
      return MinecraftClient.getInstance().world;
   }

   /** True while Minecraft is rendering or handling a world other than the one the terrain is bound to. */
   public static boolean isForeignWorld(ClientWorld boundLevel) {
      ClientWorld world = currentWorld();
      return world != null && boundLevel != world;
   }

   /** Gate for VulkanMod's WorldRenderer.setLevel. Returns false to refuse the call. */
   public static boolean allowSetLevel(ClientWorld requested) {
      boolean allowed;
      String reason;
      if (requested == null) {
         // a real disconnect clears the current world first; a secondary renderer being torn down does not
         allowed = currentWorld() == null;
         reason = "unbind while a world is active";
      } else if (ClientWorldLoader.getIsCreatingClientWorld()) {
         allowed = false;
         reason = "secondary world is being created";
      } else {
         allowed = requested == currentWorld();
         reason = "not the current world";
      }
      String name = requested == null ? "nothing" : requested.getRegistryKey().getValue().toString();
      if (allowed) {
         VulkanChunkStatusRouter.setBoundDimension(requested == null ? null : requested.getRegistryKey());
         Helper.log("[IP-VK] terrain renderer bound to " + name);
      } else if (loggedRefusals < 8) {
         loggedRefusals++;
         Helper.log("[IP-VK] refused terrain rebind to " + name + " (" + reason + ")");
      }
      return allowed;
   }

   /** Gate for VulkanMod's WorldRenderer.allChanged, which rebuilds the grid of the bound level. */
   public static boolean allowAllChanged(ClientWorld boundLevel) {
      if (ClientWorldLoader.getIsCreatingClientWorld()) {
         return false;
      }
      return boundLevel == null || !isForeignWorld(boundLevel);
   }

   /**
    * Called at the start of every terrain setup. In the main view a renderer bound to another world
    * (the player changed dimension) is rebound; inside a portal pass into another world nothing is
    * set up. Returns false when terrain must be skipped for this call.
    */
   public static boolean prepareForFrame(Object worldRenderer) {
      ClientWorld bound = VulkanModAccess.getWorldRendererLevel(worldRenderer);
      ClientWorld world = currentWorld();
      if (world == null || bound == world) {
         return true;
      }
      if (VulkanOffscreenTarget.isActiveTargetOffscreen()) {
         return false;
      }
      VulkanModAccess.bindWorldRendererLevel(worldRenderer, world);
      return VulkanModAccess.getWorldRendererLevel(worldRenderer) == world;
   }

   // ---- nested (portal) passes on the same terrain renderer ----
   //
   // A portal pass into the same dimension uses the same LevelRenderer and the same VulkanMod
   // WorldRenderer as the pass that contains it. VulkanMod keeps the camera, the matrices (in the
   // LevelRenderer) and the set of visible sections (in the WorldRenderer) between the terrain
   // setup and the later terrain layers, so after the portal pass the translucent layer (water,
   // glass) would be drawn with the portal's camera and sections. The state is saved before a
   // nested pass and restored after it.

   private static long setupCount = 0;
   private static Camera lastSetupCamera = null;
   private static Frustum lastSetupFrustum = null;
   private static boolean lastSetupSpectator = false;

   private static boolean fieldsResolved = false;
   private static Field fCamX, fCamY, fCamZ, fModelView, fProjection;

   /** Called by the WorldRenderer.setupRenderer hook each time the setup actually runs. */
   public static void onSetupRenderer(Camera camera, Frustum frustum, boolean spectator) {
      setupCount++;
      lastSetupCamera = camera;
      lastSetupFrustum = frustum;
      lastSetupSpectator = spectator;
   }

   private static final class NestedState {
      WorldRenderer levelRenderer;
      double camX, camY, camZ;
      Object modelView, projection;
      long setupCountAtBegin;
      Camera camera;
      Frustum frustum;
      boolean spectator;
   }

   private static void resolveFields(WorldRenderer levelRenderer) {
      if (fieldsResolved) {
         return;
      }
      fieldsResolved = true;
      try {
         Class<?> c = levelRenderer.getClass();
         fCamX = field(c, "camX");
         fCamY = field(c, "camY");
         fCamZ = field(c, "camZ");
         fModelView = field(c, "modelView");
         fProjection = field(c, "projection");
      } catch (Throwable e) {
         fCamX = null;
         Helper.err("[Immersive Portals] cannot read VulkanMod's per frame view state: " + e);
      }
   }

   private static Field field(Class<?> c, String name) throws NoSuchFieldException {
      Field f = c.getDeclaredField(name);
      f.setAccessible(true);
      return f;
   }

   /** Call before a nested world render starts, with the LevelRenderer of the enclosing pass. */
   public static Object beginNestedRender(WorldRenderer outerLevelRenderer) {
      if (!RenderBackendCompat.IS_NON_GL_RENDERER || outerLevelRenderer == null || !VulkanModAccess.isAvailable()) {
         return null;
      }
      resolveFields(outerLevelRenderer);
      if (fCamX == null) {
         return null;
      }
      try {
         NestedState state = new NestedState();
         state.levelRenderer = outerLevelRenderer;
         state.camX = fCamX.getDouble(outerLevelRenderer);
         state.camY = fCamY.getDouble(outerLevelRenderer);
         state.camZ = fCamZ.getDouble(outerLevelRenderer);
         state.modelView = fModelView.get(outerLevelRenderer);
         state.projection = fProjection.get(outerLevelRenderer);
         state.setupCountAtBegin = setupCount;
         state.camera = lastSetupCamera;
         state.frustum = lastSetupFrustum;
         state.spectator = lastSetupSpectator;
         return state;
      } catch (Throwable e) {
         return null;
      }
   }

   /** Call after the nested render, once the enclosing world and camera are back in place. */
   public static void endNestedRender(Object token) {
      if (!(token instanceof NestedState)) {
         return;
      }
      NestedState state = (NestedState) token;
      try {
         fCamX.setDouble(state.levelRenderer, state.camX);
         fCamY.setDouble(state.levelRenderer, state.camY);
         fCamZ.setDouble(state.levelRenderer, state.camZ);
         fModelView.set(state.levelRenderer, state.modelView);
         fProjection.set(state.levelRenderer, state.projection);
      } catch (Throwable e) {
         Helper.err("[Immersive Portals] restoring VulkanMod view state failed: " + e);
         return;
      }
      // the nested pass ran the terrain setup on this renderer: redo it for the enclosing camera
      if (setupCount != state.setupCountAtBegin && state.camera != null && state.frustum != null) {
         VulkanModAccess.runSetupRenderer(VulkanModAccess.getWorldRenderer(), state.camera, state.frustum, state.spectator);
      }
   }
}
