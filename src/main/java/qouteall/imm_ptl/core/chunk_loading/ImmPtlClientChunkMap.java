package qouteall.imm_ptl.core.chunk_loading;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientChunkManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.ChunkData.BlockEntityVisitor;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.LightType;
import net.minecraft.world.Heightmap.Type;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;
import org.apache.commons.lang3.Validate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.compat.sodium_compatibility.SodiumInterface;
import qouteall.imm_ptl.core.ducks.IEMinecraftClient;
import qouteall.imm_ptl.core.miscellaneous.IPVanillaCopy;
import qouteall.imm_ptl.core.platform_specific.O_O;
import qouteall.q_misc_util.my_util.SignalArged;

@Environment(EnvType.CLIENT)
@IPVanillaCopy
public class ImmPtlClientChunkMap extends ClientChunkManager {
   private static final Logger LOGGER = LogManager.getLogger();
   protected final Long2ObjectOpenHashMap<WorldChunk> chunkMapForMainThread = new Long2ObjectOpenHashMap();
   protected final Long2ObjectOpenHashMap<WorldChunk> chunkMapForOtherThreads = new Long2ObjectOpenHashMap();
   public final Thread mainThread = ((IEMinecraftClient)(Object)MinecraftClient.getInstance()).ip_getRunningThread();
   public static final SignalArged<WorldChunk> clientChunkLoadSignal = new SignalArged<>();
   public static final SignalArged<WorldChunk> clientChunkUnloadSignal = new SignalArged<>();

   public ImmPtlClientChunkMap(ClientWorld clientWorld, int loadDistance) {
      super(clientWorld, 1);
   }

   public void unload(ChunkPos pos) {
      Validate.isTrue(Thread.currentThread() == this.mainThread);
      WorldChunk chunk = (WorldChunk)this.chunkMapForMainThread.get(pos.toLong());
      if (chunk != null) {
         this.modifyChunkMap(chunkMap -> chunkMap.remove(pos.toLong()));
         O_O.postClientChunkUnloadEvent(chunk);
         this.world.unloadBlockEntities(chunk);
         SodiumInterface.invoker.onClientChunkUnloaded(this.world, pos.x, pos.z);
         clientChunkUnloadSignal.emit(chunk);
      }
   }

   public <T> T readChunkMap(Function<Long2ObjectOpenHashMap<WorldChunk>, T> func) {
      if (Thread.currentThread() == this.mainThread) {
         return func.apply(this.chunkMapForMainThread);
      } else {
         synchronized (this.chunkMapForOtherThreads) {
            return func.apply(this.chunkMapForOtherThreads);
         }
      }
   }

   public void modifyChunkMap(Consumer<Long2ObjectOpenHashMap<WorldChunk>> func) {
      Validate.isTrue(Thread.currentThread() == this.mainThread);
      func.accept(this.chunkMapForMainThread);
      synchronized (this.chunkMapForOtherThreads) {
         func.accept(this.chunkMapForOtherThreads);
      }
   }

   @Nullable
   public WorldChunk voxy$cheekyGetChunk(int x, int z) {
      return this.getChunk(x, z, ChunkStatus.FULL, false);
   }

   public WorldChunk getChunk(int x, int z, ChunkStatus chunkStatus, boolean create) {
      return this.readChunkMap(chunkMap -> {
         WorldChunk chunk = (WorldChunk)chunkMap.get(ChunkPos.toLong(x, z));
         if (chunk != null) {
            return chunk;
         } else {
            return create ? this.emptyChunk : null;
         }
      });
   }

   public boolean isChunkLoaded(int x, int z) {
      return this.readChunkMap(chunkMap -> chunkMap.containsKey(ChunkPos.toLong(x, z)));
   }

   public void onChunkBiomeData(int x, int z, PacketByteBuf buf) {
      Validate.isTrue(Thread.currentThread() == this.mainThread);
      long chunkPosLong = ChunkPos.toLong(x, z);
      WorldChunk worldChunk = (WorldChunk)this.chunkMapForMainThread.get(chunkPosLong);
      new ChunkPos(x, z);
      if (worldChunk == null) {
         LOGGER.error("Trying to replace biomes for missing chunk {} {}", x, z);
      } else {
         worldChunk.loadBiomeFromPacket(buf);
      }
   }

   public WorldChunk loadChunkFromPacket(int x, int z, PacketByteBuf buf, Map<Type, long[]> heightmaps, Consumer<BlockEntityVisitor> consumer) {
      Validate.isTrue(Thread.currentThread() == this.mainThread);
      long chunkPosLong = ChunkPos.toLong(x, z);
      WorldChunk worldChunk = (WorldChunk)this.chunkMapForMainThread.get(chunkPosLong);
      if (worldChunk == null) {
         worldChunk = new WorldChunk(this.world, new ChunkPos(x, z));
         this.loadChunkDataFromPacket(buf, heightmaps, worldChunk, consumer);
         this.modifyChunkMap(chunkMap -> chunkMap.put(chunkPosLong, worldChunk));
      } else {
         this.loadChunkDataFromPacket(buf, heightmaps, worldChunk, consumer);
      }

      this.world.resetChunkColor(new ChunkPos(x, z));
      O_O.postClientChunkLoadEvent(worldChunk);
      SodiumInterface.invoker.onClientChunkLoaded(this.world, x, z);
      clientChunkLoadSignal.emit(worldChunk);
      return worldChunk;
   }

   private void loadChunkDataFromPacket(PacketByteBuf buf, Map<Type, long[]> heightmaps, WorldChunk worldChunk, Consumer<BlockEntityVisitor> consumer) {
      try {
         worldChunk.loadFromPacket(buf, heightmaps, consumer);
      } catch (Exception var6) {
         LOGGER.error("Error deserializing chunk packet {} {}", worldChunk.getWorld().getRegistryKey().getValue(), worldChunk.getPos(), var6);
         CHelper.printChat(
            Text.literal(
                  "Failed to deserialize chunk packet. %s %s %s"
                     .formatted(worldChunk.getWorld().getRegistryKey().getValue(), worldChunk.getPos().x, worldChunk.getPos().z)
               )
               .append(Text.literal(" Report issue:"))
               .append(McHelper.getLinkText(O_O.getIssueLink()))
               .formatted(Formatting.RED)
         );
         throw new RuntimeException(var6);
      }
   }

   public List<WorldChunk> getCopiedChunkList() {
      return this.readChunkMap(chunkMap -> Arrays.asList((WorldChunk[])chunkMap.values().toArray(new WorldChunk[0])));
   }

   public void setChunkMapCenter(int x, int z) {
   }

   public void updateLoadDistance(int loadDistance) {
   }

   public String getDebugString() {
      return "Client Chunks (ImmPtl) " + this.getLoadedChunkCount();
   }

   public int getLoadedChunkCount() {
      return this.readChunkMap(chunkMap -> chunkMap.size());
   }

   public void onLightUpdate(LightType type, ChunkSectionPos pos) {
      ClientWorldLoader.getWorldRenderer(this.world.getRegistryKey()).scheduleChunkRender(pos.getSectionX(), pos.getSectionY(), pos.getSectionZ());
   }
}
