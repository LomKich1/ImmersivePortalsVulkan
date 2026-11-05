package qouteall.imm_ptl.core.compat.vulkanmod_compatibility;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import qouteall.imm_ptl.core.ClientWorldLoader;
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
}
