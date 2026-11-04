package qouteall.imm_ptl.core.compat.vulkanmod_compatibility;

import net.minecraft.client.world.ClientWorld;
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
}
