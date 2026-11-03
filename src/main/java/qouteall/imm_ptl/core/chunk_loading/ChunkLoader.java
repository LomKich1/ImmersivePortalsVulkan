package qouteall.imm_ptl.core.chunk_loading;

import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.mc_utils.ServerTaskList;
import qouteall.imm_ptl.core.portal.nether_portal.FastBlockAccess;
import qouteall.q_misc_util.my_util.MyTaskList;

public record ChunkLoader(RegistryKey<World> dimension, int x, int z, int radius) {
   public ChunkLoader(DimensionalChunkPos center, int radius) {
      this(center.dimension, center.x, center.z, radius);
   }

   public DimensionalChunkPos getCenter() {
      return new DimensionalChunkPos(this.dimension, this.x, this.z);
   }

   public int getLoadedChunkNum(MinecraftServer server) {
      int[] numBox = new int[]{0};
      ServerWorld serverWorld = McHelper.getServerWorld(server, this.dimension);
      this.foreachChunkPos((dim, x, z, dist) -> {
         if (McHelper.isServerChunkFullyLoaded(serverWorld, new ChunkPos(x, z))) {
            numBox[0]++;
         }
      });
      return numBox[0];
   }

   public int getChunkNum() {
      return (this.radius * 2 + 1) * (this.radius * 2 + 1);
   }

   public boolean isFullyLoaded(MinecraftServer server) {
      return this.getLoadedChunkNum(server) >= this.getChunkNum();
   }

   public void foreachChunkPos(ChunkLoader.ChunkPosConsumer func) {
      for (int dx = -this.radius; dx <= this.radius; dx++) {
         for (int dz = -this.radius; dz <= this.radius; dz++) {
            func.consume(this.dimension, this.x + dx, this.z + dz, Math.max(Math.abs(dx), Math.abs(dz)));
         }
      }
   }

   public void foreachChunkPosFromInnerToOuter(ChunkLoader.ChunkPosConsumer func) {
      func.consume(this.dimension, this.x, this.z, 0);

      for (int r = 1; r <= this.radius; r++) {
         int minX = this.x - r;
         int maxX = this.x + r;
         int minY = this.z - r;
         int maxY = this.z + r;

         for (int y = minY; y < maxY; y++) {
            func.consume(this.dimension, maxX, y, r);
         }

         for (int x = maxX; x > minX; x--) {
            func.consume(this.dimension, x, maxY, r);
         }

         for (int y = maxY; y > minY; y--) {
            func.consume(this.dimension, minX, y, r);
         }

         for (int x = minX; x < maxX; x++) {
            func.consume(this.dimension, x, minY, r);
         }
      }
   }

   public FastBlockAccess createFastBlockAccess(MinecraftServer server) {
      ServerWorld world = McHelper.getServerWorld(server, this.dimension);
      return this.createFastBlockAccess(world);
   }

   public FastBlockAccess createFastBlockAccess(ServerWorld world) {
      return FastBlockAccess.from(world, new ChunkPos(this.x, this.z), this.radius);
   }

   public void loadChunksAndDo(MinecraftServer server, Runnable runnable) {
      ImmPtlChunkTracking.addGlobalAdditionalChunkLoader(server, this);
      ServerTaskList.of(server).addTask(MyTaskList.withDelayCondition(() -> this.getLoadedChunkNum(server) < this.getChunkNum(), MyTaskList.oneShotTask(() -> {
         ImmPtlChunkTracking.removeGlobalAdditionalChunkLoader(server, this);
         runnable.run();
      })));
   }

   @Override
   public String toString() {
      return "(%s %d %d %d)".formatted(this.dimension.getValue(), this.x, this.z, this.radius);
   }

   public interface ChunkPosConsumer {
      void consume(RegistryKey<World> var1, int var2, int var3, int var4);
   }
}
