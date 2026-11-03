package qouteall.imm_ptl.core.mixin.common.entity_sync;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ChunkHolder;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerChunkLoadingManager;
import net.minecraft.server.world.ServerChunkLoadingManager.class_3208;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.ducks.IEChunkMap;
import qouteall.imm_ptl.core.ducks.IETrackedEntity;
import qouteall.imm_ptl.core.miscellaneous.IPVanillaCopy;
import qouteall.imm_ptl.core.teleportation.ServerTeleportationManager;

@Mixin({ServerChunkLoadingManager.class})
public abstract class MixinChunkMap_E implements IEChunkMap {
   @Shadow
   @Final
   public Int2ObjectMap<class_3208> entityTrackers;
   @Shadow
   @Final
   private ServerWorld world;

   @Shadow
   abstract void handlePlayerAddedOrRemoved(ServerPlayerEntity var1, boolean var2);

   @Shadow
   @Nullable
   protected abstract ChunkHolder getCurrentChunkHolder(long var1);

   @Inject(
      method = {"loadEntity"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/ServerChunkLoadingManager.EntityTracker;updateTrackedStatus(Ljava/util/List;)V"
      )}
   )
   private void onAddEntityUpdatePlayers(Entity entity, CallbackInfo ci) {
      class_3208 trackedEntity = (class_3208)this.entityTrackers.get(entity.getId());
      if (trackedEntity != null) {
         ((IETrackedEntity)trackedEntity).ip_updateEntityTrackingStatus();
      }
   }

   @Inject(
      method = {"Lnet/minecraft/ServerChunkLoadingManager;unloadEntity(Lnet/minecraft/Entity;)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   @IPVanillaCopy
   private void onUnloadEntity(Entity entity, CallbackInfo ci) {
      if (ServerTeleportationManager.of(entity.getEntityWorld().getServer()).isTeleporting(entity)) {
         if (entity instanceof ServerPlayerEntity player) {
            Object tracker = this.entityTrackers.remove(entity.getId());
            ((IETrackedEntity)tracker).ip_stopTrackingToAllPlayers();
            this.handlePlayerAddedOrRemoved(player, false);
         } else {
            this.entityTrackers.remove(entity.getId());
         }

         ci.cancel();
      }
   }

   @Inject(
      method = {"Lnet/minecraft/ServerChunkLoadingManager;tickEntityMovement()V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onTickEntityMovement(CallbackInfo ci) {
      ci.cancel();
   }

   @Override
   public void ip_onPlayerUnload(ServerPlayerEntity oldPlayer) {
      this.entityTrackers.values().forEach(obj -> obj.stopTracking(oldPlayer));
   }

   @Override
   public void ip_onDimensionRemove() {
      this.entityTrackers.values().forEach(obj -> ((IETrackedEntity)obj).ip_onDimensionRemove());
   }

   @Override
   public void ip_resendSpawnPacketToTrackers(Entity entity) {
      Object tracker = this.entityTrackers.get(entity.getId());
      Validate.notNull(tracker, "entity not yet tracked", new Object[0]);
      ((IETrackedEntity)tracker).ip_resendSpawnPacketToTrackers();
   }

   @Override
   public Int2ObjectMap<class_3208> ip_getEntityTrackerMap() {
      return this.entityTrackers;
   }

   @Nullable
   @Override
   public ChunkHolder ip_getUpdatingChunkIfPresent(long chunkPos) {
      return this.getCurrentChunkHolder(chunkPos);
   }
}
