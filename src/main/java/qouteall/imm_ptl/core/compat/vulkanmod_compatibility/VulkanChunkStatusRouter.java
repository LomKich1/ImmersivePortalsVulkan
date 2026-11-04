package qouteall.imm_ptl.core.compat.vulkanmod_compatibility;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import qouteall.imm_ptl.core.compat.RenderBackendCompat;
import qouteall.q_misc_util.Helper;

/**
 * Makes VulkanMod's chunk readiness table dimension aware.
 *
 * <p>VulkanMod keeps one global {@code ChunkStatusMap} keyed by chunk position without a
 * dimension, and only builds terrain for columns that reached the ready state. Immersive Portals
 * keeps several client worlds alive in one frame, so a chunk of the nether would otherwise mark the
 * same coordinates ready in the overworld. The static {@code INSTANCE} field is replaced by a
 * dispatcher whose calls (see MixinVulkanModChunkStatusMap) are forwarded to per dimension tables.
 * Reads resolve the dimension of the terrain that is bound, writes resolve the dimension of the
 * world the packet belongs to.</p>
 *
 * <p>Written against VulkanMod 0.6.8 for Minecraft 1.21.11. Flag values are those of
 * {@code ChunkStatusMap}: DATA_READY = 1, LIGHT_READY = 2, NEIGHBOURS_READY = 4.</p>
 */
public class VulkanChunkStatusRouter {
   public static final byte FLAG_DATA_READY = 1;
   public static final byte FLAG_ALL = 7;

   private static Object dispatcher;
   private static final Object2ObjectOpenHashMap<RegistryKey<World>, Object> tables = new Object2ObjectOpenHashMap<>();

   private static RegistryKey<World> boundDimension = null;
   private static RegistryKey<World> writeDimension = null;
   private static RegistryKey<World> explicitDimension = null;

   private VulkanChunkStatusRouter() {
   }

   // ---- installation ----

   /**
    * Called instead of {@code ChunkStatusMap.createInstance}. Returns false when the mod cannot be
    * driven, so that VulkanMod's own behaviour stays intact. Tables are deliberately not cleared:
    * VulkanMod calls createInstance from WorldRenderer.setLevel, which can happen while other
    * worlds are loaded, and wiping a world the server will not send again leaves it unbuildable.
    */
   public static boolean ensureDispatcher(int renderDistance) {
      if (!RenderBackendCompat.IS_NON_GL_RENDERER || !VulkanModAccess.isAvailable()) {
         return false;
      }
      Object current = VulkanModAccess.getChunkStatusMap();
      if (current != null && current == dispatcher) {
         return true;
      }
      if (current != null && dispatcher == null && !tables.isEmpty()) {
         return false;
      }
      Object created = VulkanModAccess.newChunkStatusMap(renderDistance);
      if (created == null) {
         return false;
      }
      dispatcher = created;
      VulkanModAccess.setChunkStatusMap(created);
      Helper.log("[Immersive Portals] VulkanMod chunk status dispatcher installed");
      return true;
   }

   public static boolean isDispatcher(Object map) {
      return map != null && map == dispatcher;
   }

   // ---- dimensions ----

   public static void setBoundDimension(RegistryKey<World> dimension) {
      boundDimension = dimension;
   }

   public static RegistryKey<World> getBoundDimension() {
      RegistryKey<World> result = boundDimension;
      return result != null ? result : currentPlayerDimension();
   }

   /** Recorded before vanilla applies chunk or light data, so the write reaches the right world. */
   public static void setWriteDimension(RegistryKey<World> dimension) {
      writeDimension = dimension;
   }

   private static RegistryKey<World> currentWriteDimension() {
      if (explicitDimension != null) {
         return explicitDimension;
      }
      return writeDimension != null ? writeDimension : currentPlayerDimension();
   }

   private static RegistryKey<World> currentPlayerDimension() {
      ClientWorld level = MinecraftClient.getInstance().world;
      return level == null ? null : level.getRegistryKey();
   }

   /** Runs the action with every table access, read included, aimed at the given dimension. */
   public static void withDimension(RegistryKey<World> dimension, Runnable action) {
      RegistryKey<World> previous = explicitDimension;
      explicitDimension = dimension;
      try {
         action.run();
      } finally {
         explicitDimension = previous;
      }
   }

   // ---- forwarding, called by MixinVulkanModChunkStatusMap ----

   public static void forwardSet(int chunkX, int chunkZ, byte flag) {
      RegistryKey<World> dimension = currentWriteDimension();
      Object table = tableFor(dimension);
      boolean[] readyBefore = readinessAround(table, chunkX, chunkZ);
      VulkanModAccess.chunkStatusSet(table, chunkX, chunkZ, flag);
      reDirtyNewlyReadyChunks(dimension, table, chunkX, chunkZ, readyBefore);
   }

   public static void forwardReset(int chunkX, int chunkZ, byte flag) {
      VulkanModAccess.chunkStatusReset(tableFor(currentWriteDimension()), chunkX, chunkZ, flag);
   }

   public static boolean forwardIsReady(int chunkX, int chunkZ) {
      RegistryKey<World> dimension = explicitDimension != null ? explicitDimension : getBoundDimension();
      return VulkanModAccess.chunkStatusReady(tableFor(dimension), chunkX, chunkZ);
   }

   /**
    * Every transition into the ready state has to put the sections of that chunk back on the dirty
    * list (see VulkanTerrainBridge). setChunkStatus also promotes the eight neighbours, so the
    * whole 3x3 block is checked.
    */
   private static void reDirtyNewlyReadyChunks(
      RegistryKey<World> dimension, Object table, int x, int z, boolean[] readyBefore
   ) {
      int i = 0;
      for (int dx = -1; dx <= 1; dx++) {
         for (int dz = -1; dz <= 1; dz++) {
            if (VulkanModAccess.chunkStatusReady(table, x + dx, z + dz) && !readyBefore[i]) {
               VulkanTerrainBridge.reDirtySections(dimension, x + dx, z + dz);
            }
            i++;
         }
      }
   }

   private static boolean[] readinessAround(Object table, int x, int z) {
      boolean[] result = new boolean[9];
      if (table == null) {
         return result;
      }
      int i = 0;
      for (int dx = -1; dx <= 1; dx++) {
         for (int dz = -1; dz <= 1; dz++) {
            result[i++] = VulkanModAccess.chunkStatusReady(table, x + dx, z + dz);
         }
      }
      return result;
   }

   private static Object tableFor(RegistryKey<World> dimension) {
      if (dimension == null) {
         return null;
      }
      Object table = tables.get(dimension);
      if (table == null) {
         table = VulkanModAccess.newChunkStatusMap(8);
         if (table == null) {
            return null;
         }
         tables.put(dimension, table);
         Helper.log("[Immersive Portals] VulkanMod readiness table created for " + dimension.getValue());
      }
      return table;
   }

   /** Drops the table of a world that is gone, so that a re-entered world starts clean. */
   public static void invalidate(RegistryKey<World> dimension) {
      tables.remove(dimension);
   }

   // ---- filling ----

   /**
    * Marks every chunk the client already holds as ready. VulkanMod only learns about a chunk from
    * a packet, and a world loaded earlier will not be sent again. Used by the terrain bridge.
    */
   public static void markLoadedChunksReady(ClientWorld level) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client.player == null) {
         return;
      }
      RegistryKey<World> dimension = level.getRegistryKey();
      int radius = client.options.getClampedViewDistance() + 1;
      ChunkPos center = new ChunkPos(client.player.getBlockPos());
      int marked = 0;
      for (int x = center.x - radius; x <= center.x + radius; x++) {
         for (int z = center.z - radius; z <= center.z + radius; z++) {
            WorldChunk chunk = level.getChunkManager().getChunk(x, z, ChunkStatus.FULL, false);
            if (chunk != null) {
               VulkanModAccess.chunkStatusSet(tableFor(dimension), x, z, FLAG_ALL);
               marked++;
            }
         }
      }
      if (marked > 0) {
         Helper.log("[Immersive Portals] marked " + marked + " already loaded chunks of "
            + dimension.getValue() + " ready for VulkanMod");
      }
   }
}
