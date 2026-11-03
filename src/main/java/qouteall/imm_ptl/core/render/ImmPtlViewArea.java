package qouteall.imm_ptl.core.render;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.LongConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.profiler.Profilers;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.util.math.BlockPos;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.registry.RegistryKey;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.BuiltChunkStorage;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.client.render.chunk.ChunkBuilder.class_851;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.chunk_loading.ImmPtlClientChunkMap;
import qouteall.imm_ptl.core.ducks.IERenderSection;
import qouteall.imm_ptl.core.ducks.IEWorldRenderer;
import qouteall.imm_ptl.core.miscellaneous.GcMonitor;
import qouteall.imm_ptl.core.mixin.client.render.IEViewArea;
import qouteall.q_misc_util.Helper;

@Environment(EnvType.CLIENT)
public class ImmPtlViewArea extends BuiltChunkStorage {
   private final ChunkBuilder factory;
   private final Long2ObjectOpenHashMap<ImmPtlViewArea.Column> columnMap = new Long2ObjectOpenHashMap();
   private final Long2ObjectOpenHashMap<ImmPtlViewArea.Preset> presets = new Long2ObjectOpenHashMap();
   private ImmPtlViewArea.Preset currentPreset = null;
   public final int minSectionY;
   public final int endSectionY;
   private boolean isAlive = true;

   public static void init() {
      ImmPtlClientChunkMap.clientChunkUnloadSignal.connect(section -> {
         RegistryKey<World> dimension = section.getWorld().getRegistryKey();
         WorldRenderer worldRenderer = ClientWorldLoader.WORLD_RENDERER_MAP.get(dimension);
         if (worldRenderer != null && ((IEWorldRenderer)worldRenderer).ip_getBuiltChunkStorage() instanceof ImmPtlViewArea immPtlViewArea) {
            immPtlViewArea.onChunkUnload(section.getPos().x, section.getPos().z);
         }
      });
      IPGlobal.POST_CLIENT_TICK_EVENT.register(() -> {
         if (ClientWorldLoader.getIsInitialized()) {
            for (ClientWorld world : ClientWorldLoader.getClientWorlds()) {
               WorldRenderer worldRenderer = ClientWorldLoader.getWorldRenderer(world.getRegistryKey());
               if (((IEWorldRenderer)worldRenderer).ip_getBuiltChunkStorage() instanceof ImmPtlViewArea immPtlViewArea) {
                  immPtlViewArea.tick();
               }
            }
         }
      });
   }

   public ImmPtlViewArea(ChunkBuilder sectionBuilder, World world, int r, WorldRenderer worldRenderer) {
      super(sectionBuilder, world, r, worldRenderer);
      this.factory = sectionBuilder;
      int cacheSize = this.sizeX * this.sizeY * this.sizeZ;
      if (IPGlobal.cacheGlBuffer) {
         cacheSize /= 10;
      }

      this.minSectionY = McHelper.getMinSectionY(world);
      this.endSectionY = McHelper.getMaxSectionYExclusive(world);
   }

   protected void createChunks(ChunkBuilder sectionBuilder_1) {
      int num = this.sizeX * this.sizeY * this.sizeZ;
      this.chunks = new class_851[num];
   }

   public void clear() {
      Set<class_851> allActiveBuiltChunks = this.getAllActiveBuiltChunks();
      allActiveBuiltChunks.forEach(class_851::clear);
      this.columnMap.clear();
      this.presets.clear();
      this.isAlive = false;
   }

   public void updateCameraPosition(ChunkSectionPos cameraSectionPos) {
      Profilers.get().push("built_section_storage");
      int cameraChunkX = cameraSectionPos.getSectionX();
      int cameraChunkZ = cameraSectionPos.getSectionZ();
      ChunkPos cameraChunkPos = new ChunkPos(cameraChunkX, cameraChunkZ);
      ImmPtlViewArea.Preset preset = (ImmPtlViewArea.Preset)this.presets
         .computeIfAbsent(cameraChunkPos.toLong(), whatever -> this.createPresetByChunkPos(cameraChunkX, cameraChunkZ));
      preset.lastActiveTime = System.nanoTime();
      this.chunks = preset.data;
      this.currentPreset = preset;
      ((IEViewArea)this).ip_setCameraSectionPos(cameraSectionPos);
      this.worldRenderer.getChunkRenderingDataPreparer().scheduleTerrainUpdate();
      Profilers.get().pop();
   }

   public void scheduleRebuild(int cx, int cy, int cz, boolean isImportant) {
      class_851 builtChunk = this.provideBuiltChunkByChunkPos(cx, cy, cz);
      builtChunk.scheduleRebuild(isImportant);
   }

   public class_851 provideBuiltChunkByChunkPos(int cx, int cy, int cz) {
      ImmPtlViewArea.Column column = this.provideColumn(ChunkPos.toLong(cx, cz));
      int offsetChunkY = MathHelper.clamp(cy - McHelper.getMinSectionY(this.world), 0, McHelper.getYSectionNumber(this.world) - 1);
      return column.sections[offsetChunkY];
   }

   private ImmPtlViewArea.Preset createPresetByChunkPos(int sectionX, int sectionZ) {
      class_851[] sections1 = new class_851[this.sizeX * this.sizeY * this.sizeZ];

      for (int cx = 0; cx < this.sizeX; cx++) {
         int xBlockSize = this.sizeX * 16;
         int xStart = (sectionX << 4) - xBlockSize / 2;
         int px = xStart + Math.floorMod(cx * 16 - xStart, xBlockSize);

         for (int cz = 0; cz < this.sizeZ; cz++) {
            int zBlockSize = this.sizeZ * 16;
            int zStart = (sectionZ << 4) - zBlockSize / 2;
            int pz = zStart + Math.floorMod(cz * 16 - zStart, zBlockSize);
            Validate.isTrue(px % 16 == 0);
            Validate.isTrue(pz % 16 == 0);
            ImmPtlViewArea.Column column = this.provideColumn(ChunkPos.toLong(px >> 4, pz >> 4));

            for (int offsetCy = 0; offsetCy < this.sizeY; offsetCy++) {
               int index = this.getChunkIndex(cx, offsetCy, cz);
               sections1[index] = column.sections[offsetCy];
            }
         }
      }

      return new ImmPtlViewArea.Preset(sections1);
   }

   private void foreachPresetCoveredChunkPoses(int centerChunkX, int centerChunkZ, LongConsumer func) {
      class_851[] sections1 = new class_851[this.sizeX * this.sizeY * this.sizeZ];

      for (int cx = 0; cx < this.sizeX; cx++) {
         int xBlockSize = this.sizeX * 16;
         int xStart = (centerChunkX << 4) - xBlockSize / 2;
         int px = xStart + Math.floorMod(cx * 16 - xStart, xBlockSize);

         for (int cz = 0; cz < this.sizeZ; cz++) {
            int zBlockSize = this.sizeZ * 16;
            int zStart = (centerChunkZ << 4) - zBlockSize / 2;
            int pz = zStart + Math.floorMod(cz * 16 - zStart, zBlockSize);
            Validate.isTrue(px % 16 == 0);
            Validate.isTrue(pz % 16 == 0);
            long sectionPos = ChunkPos.toLong(px >> 4, pz >> 4);
            func.accept(sectionPos);
         }
      }
   }

   private int getChunkIndex(int x, int y, int z) {
      return (z * this.sizeY + y) * this.sizeX + x;
   }

   public ImmPtlViewArea.Column provideColumn(long sectionPos) {
      return (ImmPtlViewArea.Column)this.columnMap.computeIfAbsent(sectionPos, this::createColumn);
   }

   private ImmPtlViewArea.Column createColumn(long sectionPos) {
      class_851[] array = new class_851[this.sizeY];
      int sectionX = ChunkPos.getPackedX(sectionPos);
      int sectionZ = ChunkPos.getPackedZ(sectionPos);
      int minSectionY = McHelper.getMinSectionY(this.world);

      for (int offsetCY = 0; offsetCY < this.sizeY; offsetCY++) {
         class_851 builtChunk = new class_851(this.factory, 0, ChunkSectionPos.asLong(sectionX, offsetCY + minSectionY, sectionZ));
         array[offsetCY] = builtChunk;
      }

      return new ImmPtlViewArea.Column(array);
   }

   private void tick() {
      if (this.isAlive) {
         ClientWorld worldClient = MinecraftClient.getInstance().world;
         if (worldClient != null) {
            if (GcMonitor.isMemoryNotEnough()) {
               if (worldClient.getTime() % 3L == 0L) {
                  this.purge();
               }
            } else if (worldClient.getTime() % 213L == 66L) {
               this.purge();
            }
         }
      }
   }

   private void purge() {
      Profilers.get().push("my_built_section_storage_purge");
      long dropTime = Helper.secondToNano(GcMonitor.isMemoryNotEnough() ? 3.0 : 20.0);
      long currentTime = System.nanoTime();
      this.presets.long2ObjectEntrySet().removeIf(entry -> {
         ImmPtlViewArea.Preset preset = (ImmPtlViewArea.Preset)entry.getValue();
         long centerChunkPos = entry.getLongKey();
         boolean shouldDropPreset = this.shouldDropPreset(dropTime, currentTime, preset);
         if (!shouldDropPreset) {
            this.foreachPresetCoveredChunkPoses(ChunkPos.getPackedX(centerChunkPos), ChunkPos.getPackedZ(centerChunkPos), columnChunkPos -> {
               ImmPtlViewArea.Column column = (ImmPtlViewArea.Column)this.columnMap.get(columnChunkPos);
               column.mark = currentTime;
            });
         }

         return shouldDropPreset;
      });
      long timeThreshold = Helper.secondToNano(5.0);
      ArrayDeque<class_851> toDelete = new ArrayDeque<>();
      this.columnMap.long2ObjectEntrySet().removeIf(entry -> {
         ImmPtlViewArea.Column column = (ImmPtlViewArea.Column)entry.getValue();
         boolean shouldRemove = currentTime - column.mark > timeThreshold;
         if (shouldRemove) {
            toDelete.addAll(Arrays.asList(column.sections));
         }

         return shouldRemove;
      });
      if (!toDelete.isEmpty()) {
         IPGlobal.PRE_GAME_RENDER_TASK_LIST.addTask(() -> {
            if (toDelete.isEmpty()) {
               return true;
            } else {
               for (int num = 0; !toDelete.isEmpty() && num < 100; num++) {
                  class_851 builtChunk = toDelete.poll();
                  builtChunk.clear();
               }

               return false;
            }
         });
      }

      Profilers.get().pop();
   }

   private boolean shouldDropPreset(long dropTime, long currentTime, ImmPtlViewArea.Preset preset) {
      return preset.data == this.chunks ? false : currentTime - preset.lastActiveTime > dropTime;
   }

   private Set<class_851> getAllActiveBuiltChunks() {
      HashSet<class_851> result = new HashSet<>();
      this.presets.forEach((key, preset) -> result.addAll(Arrays.asList(preset.data)));
      if (this.chunks != null) {
         result.addAll(Arrays.asList(this.chunks));
      }

      result.remove(null);
      return result;
   }

   public int getManagedSectionNum() {
      return this.columnMap.size() * this.sizeY;
   }

   public String getDebugString() {
      return String.format("Built Section Storage Columns:%s", this.columnMap.size());
   }

   public int getRadius() {
      return (this.sizeX - 1) / 2;
   }

   public boolean isRegionActive(int cxStart, int czStart, int cxEnd, int czEnd) {
      for (int cx = cxStart; cx <= cxEnd; cx++) {
         for (int cz = czStart; cz <= czEnd; cz++) {
            if (this.columnMap.containsKey(ChunkPos.toLong(cx, cz))) {
               return true;
            }
         }
      }

      return false;
   }

   public void onChunkUnload(int sectionX, int sectionZ) {
      long sectionPos = ChunkPos.toLong(sectionX, sectionZ);
      ImmPtlViewArea.Column column = (ImmPtlViewArea.Column)this.columnMap.get(sectionPos);
      if (column != null) {
         for (class_851 builtChunk : column.sections) {
            ((IERenderSection)builtChunk).portal_fullyReset();
         }
      }
   }

   public class_851 getSectionFromRawArray(BlockPos sectionOrigin, class_851[] sections) {
      int i = MathHelper.floorDiv(sectionOrigin.getX(), 16);
      int j = MathHelper.floorDiv(sectionOrigin.getY() - McHelper.getMinY(this.world), 16);
      int k = MathHelper.floorDiv(sectionOrigin.getZ(), 16);
      if (j >= 0 && j < this.sizeY) {
         i = MathHelper.floorMod(i, this.sizeX);
         k = MathHelper.floorMod(k, this.sizeZ);
         return sections[this.getChunkIndex(i, j, k)];
      } else {
         return null;
      }
   }

   @Nullable
   protected class_851 getRenderedChunk(BlockPos pos) {
      int i = MathHelper.floorDiv(pos.getX(), 16);
      int j = MathHelper.floorDiv(pos.getY() - McHelper.getMinY(this.world), 16);
      int k = MathHelper.floorDiv(pos.getZ(), 16);
      return this.getRenderSectionByGridCoords(i, j, k, pos);
   }

   @Nullable
   protected class_851 getRenderedChunk(long sectionNode) {
      int i = ChunkSectionPos.unpackX(sectionNode);
      int j = ChunkSectionPos.unpackY(sectionNode) - McHelper.getMinSectionY(this.world);
      int k = ChunkSectionPos.unpackZ(sectionNode);
      return this.getRenderSectionByGridCoords(i, j, k, ChunkSectionPos.from(sectionNode).getMinPos());
   }

   @Nullable
   private class_851 getRenderSectionByGridCoords(int i, int j, int k, BlockPos pos) {
      if (j >= 0 && j < this.sizeY) {
         i = MathHelper.floorMod(i, this.sizeX);
         k = MathHelper.floorMod(k, this.sizeZ);
         int sectionIndex = this.getChunkIndex(i, j, k);
         class_851 result = this.chunks[sectionIndex];
         if (result == null) {
            Helper.err("Null RenderChunk " + pos);
            return null;
         } else {
            ((IERenderSection)result).portal_setIndex(sectionIndex);
            return result;
         }
      } else {
         return null;
      }
   }

   @Nullable
   public class_851 rawFetch(int cx, int cy, int cz, long timeMark) {
      if (cy >= this.minSectionY && cy < this.endSectionY) {
         long l = ChunkPos.toLong(cx, cz);
         ImmPtlViewArea.Column column = this.provideColumn(l);
         column.mark = timeMark;
         int yOffset = cy - this.minSectionY;
         return column.sections[yOffset];
      } else {
         return null;
      }
   }

   @Nullable
   public class_851 rawGet(int cx, int cy, int cz) {
      if (cy >= this.minSectionY && cy < this.endSectionY) {
         long l = ChunkPos.toLong(cx, cz);
         ImmPtlViewArea.Column column = (ImmPtlViewArea.Column)this.columnMap.get(l);
         if (column == null) {
            return null;
         } else {
            int yOffset = cy - this.minSectionY;
            return column.sections[yOffset];
         }
      } else {
         return null;
      }
   }

   public static class Column {
      public long mark = 0L;
      public class_851[] sections;

      public Column(class_851[] sections) {
         this.sections = sections;
      }
   }

   public static class Preset {
      public final class_851[] data;
      public long lastActiveTime = 0L;

      public Preset(class_851[] data) {
         this.data = data;
      }
   }
}
