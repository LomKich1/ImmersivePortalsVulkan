package qouteall.imm_ptl.core.mixin.common.chunk_sync;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.server.world.ChunkHolder;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerLightingProvider;
import net.minecraft.server.world.ServerChunkLoadingManager;
import net.minecraft.server.network.ChunkFilter;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.chunk_loading.ImmPtlChunkTracking;
import qouteall.imm_ptl.core.ducks.IEChunkMap;

@Mixin(
   value = {ServerChunkLoadingManager.class},
   priority = 1100
)
public abstract class MixinChunkMap_C implements IEChunkMap {
   @Shadow
   @Final
   private ServerWorld world;
   @Shadow
   @Final
   private ServerLightingProvider lightingProvider;

   @Shadow
   protected abstract ChunkHolder getChunkHolder(long var1);

   @Shadow
   abstract int getViewDistance(ServerPlayerEntity var1);

   @Override
   public int ip_getPlayerViewDistance(ServerPlayerEntity player) {
      return this.getViewDistance(player);
   }

   @Override
   public ServerWorld ip_getWorld() {
      return this.world;
   }

   @Override
   public ServerLightingProvider ip_getLightingProvider() {
      return this.lightingProvider;
   }

   @Override
   public ChunkHolder ip_getChunkHolder(long chunkPosLong) {
      return this.getChunkHolder(chunkPosLong);
   }

   @Inject(
      method = {"sendWatchPackets"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onUpdateChunkTracking(ServerPlayerEntity serverPlayer, ChunkFilter chunkTrackingView, CallbackInfo ci) {
      ci.cancel();
   }

   @Inject(
      method = {"getPlayersWatchingChunk"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onGetPlayers(ChunkPos chunkPos, boolean boundaryOnly, CallbackInfoReturnable<List<ServerPlayerEntity>> cir) {
      List<ServerPlayerEntity> viewers = ImmPtlChunkTracking.getPlayersViewingChunk(
         this.world.getRegistryKey(), chunkPos.x, chunkPos.z, boundaryOnly
      );
      List<ServerPlayerEntity> result = new ArrayList<>(viewers.size());

      for (ServerPlayerEntity viewer : viewers) {
         if (viewer.getServerWorld() == this.world) {
            result.add(viewer);
         }
      }

      cir.setReturnValue(result);
   }

   @Overwrite
   private void sendToPlayers(ChunkHolder holder, WorldChunk chunk) {
      ImmPtlChunkTracking.onChunkProvidedDeferred(chunk);
      this.world.method_14178().markForUpdate(holder);
      this.world.getSubscriptionTracker().trackChunk(chunk);
   }
}
