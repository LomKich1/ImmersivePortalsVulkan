package qouteall.imm_ptl.core.mixin.common.entity_sync;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.EntityTrackerEntry;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.server.network.PlayerAssociatedNetworkHandler;
import net.minecraft.server.world.ServerChunkLoadingManager.class_3208;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.chunk_loading.ImmPtlChunkTracking;
import qouteall.imm_ptl.core.ducks.IEChunkMap;
import qouteall.imm_ptl.core.ducks.IEEntityTrackerEntry;
import qouteall.imm_ptl.core.ducks.IETrackedEntity;
import qouteall.imm_ptl.core.miscellaneous.IPVanillaCopy;
import qouteall.imm_ptl.core.network.PacketRedirection;

@Mixin({class_3208.class})
public abstract class MixinTrackedEntity implements IETrackedEntity {
   @Shadow
   @Final
   private EntityTrackerEntry entry;
   @Shadow
   @Final
   private Entity entity;
   @Shadow
   @Final
   private Set<PlayerAssociatedNetworkHandler> listeners;
   @Shadow
   private ChunkSectionPos trackedSection;

   @Shadow
   public abstract void stopTracking();

   @Shadow
   protected abstract int getMaxTrackDistance();

   @Redirect(
      method = {"sendToListeners(Lnet/minecraft/Packet;)V", "sendToListenersIf(Lnet/minecraft/Packet;Ljava/util/function/Predicate;)V"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/PlayerAssociatedNetworkHandler;sendPacket(Lnet/minecraft/Packet;)V"
      )}
   )
   private void onSendToOtherNearbyPlayers(PlayerAssociatedNetworkHandler entityTrackingListener, Packet<?> packet) {
      PacketRedirection.withForceRedirect((ServerWorld)this.entity.getEntityWorld(), () -> entityTrackingListener.sendPacket(packet));
   }

   @Redirect(
      method = {"sendToSelfAndListeners(Lnet/minecraft/Packet;)V"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/ServerPlayNetworkHandler;sendPacket(Lnet/minecraft/Packet;)V"
      )}
   )
   private void onSendToNearbyPlayers(ServerPlayNetworkHandler serverPlayNetworkHandler, Packet packet) {
      PacketRedirection.sendRedirectedPacket(serverPlayNetworkHandler, packet, this.entity.getEntityWorld().getRegistryKey());
   }

   @Inject(
      method = {"updateTrackedStatus"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onUpdatePlayer(ServerPlayerEntity player, CallbackInfo ci) {
      ci.cancel();
   }

   @Inject(
      method = {"updateTrackedStatus"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onUpdatePlayers(List<ServerPlayerEntity> list, CallbackInfo ci) {
      ci.cancel();
   }

   @Override
   public Entity ip_getEntity() {
      return this.entity;
   }

   @IPVanillaCopy
   @Override
   public void ip_updateEntityTrackingStatus() {
      IEChunkMap chunkMap = (IEChunkMap)((ServerWorld)this.entity.getEntityWorld()).method_14178().chunkLoadingManager;
      Object2ObjectOpenHashMap<ServerPlayerEntity, ImmPtlChunkTracking.PlayerWatchRecord> watchRecMap = ImmPtlChunkTracking.getWatchRecordForChunk(
         this.entity.getEntityWorld().getRegistryKey(), this.entity.getChunkPos().x, this.entity.getChunkPos().z
      );
      int effectiveRange = this.getMaxTrackDistance();
      this.listeners.removeIf(connection -> {
         ServerPlayerEntity playerx = connection.getPlayer();
         boolean shouldRemove = !watches(this.entity, watchRecMap, effectiveRange, playerx);
         if (shouldRemove) {
            PacketRedirection.withForceRedirect((ServerWorld)this.entity.getEntityWorld(), () -> this.entry.stopTracking(playerx));
         }

         return shouldRemove;
      });
      if (watchRecMap != null) {
         ObjectIterator var4 = watchRecMap.entrySet().iterator();

         while (var4.hasNext()) {
            Entry<ServerPlayerEntity, ImmPtlChunkTracking.PlayerWatchRecord> e = (Entry<ServerPlayerEntity, ImmPtlChunkTracking.PlayerWatchRecord>)var4.next();
            ServerPlayerEntity player = e.getKey();
            ImmPtlChunkTracking.PlayerWatchRecord rec = e.getValue();
            if (recWatches(this.entity, effectiveRange, rec, player) && this.listeners.add(player.networkHandler)) {
               PacketRedirection.withForceRedirect((ServerWorld)this.entity.getEntityWorld(), () -> this.entry.startTracking(player));
            }
         }
      }
   }

   @Unique
   private static boolean watches(
      Entity entity, @Nullable Map<ServerPlayerEntity, ImmPtlChunkTracking.PlayerWatchRecord> watchRec, int effectiveRange, ServerPlayerEntity player
   ) {
      if (watchRec == null) {
         return false;
      } else if (entity == player) {
         return false;
      } else {
         ImmPtlChunkTracking.PlayerWatchRecord rec = watchRec.get(player);
         return recWatches(entity, effectiveRange, rec, player);
      }
   }

   @Unique
   private static boolean recWatches(Entity entity, int effectiveRange, ImmPtlChunkTracking.PlayerWatchRecord rec, ServerPlayerEntity player) {
      if (rec == null) {
         return false;
      } else if (!rec.isLoadedToPlayer) {
         return false;
      } else {
         return entity == player ? false : rec.distanceToSource * 16 + 8 <= effectiveRange;
      }
   }

   @Override
   public void ip_onDimensionRemove() {
      for (PlayerAssociatedNetworkHandler connection : this.listeners) {
         this.entry.stopTracking(connection.getPlayer());
      }

      this.listeners.clear();
   }

   @Override
   public void ip_resendSpawnPacketToTrackers() {
      ((IEEntityTrackerEntry)this.entry).ip_updateTrackedEntityPosition();
      Packet spawnPacket = this.entity.createSpawnPacket(this.entry);
      Packet<ClientPlayPacketListener> redirected = PacketRedirection.createRedirectedMessage(
         this.entity.getEntityWorld().getServer(), this.entity.getEntityWorld().getRegistryKey(), spawnPacket
      );
      this.listeners.forEach(handler -> handler.sendPacket(redirected));
   }

   @Override
   public void ip_stopTrackingToAllPlayers() {
      this.stopTracking();
   }

   @Override
   public void ip_sendChanges() {
      this.entry.tick();
   }

   @Override
   public ChunkSectionPos ip_getLastSectionPos() {
      return this.trackedSection;
   }

   @Override
   public void ip_setLastSectionPos(ChunkSectionPos arg) {
      this.trackedSection = arg;
   }
}
