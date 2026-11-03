package qouteall.imm_ptl.core.mixin.common.other_sync;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.List;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.PlayerManager;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.chunk_loading.ImmPtlChunkTracking;
import qouteall.imm_ptl.core.network.PacketRedirection;
import qouteall.imm_ptl.core.portal.global_portals.GlobalPortalStorage;
import qouteall.imm_ptl.core.teleportation.ServerTeleportationManager;
import qouteall.q_misc_util.Helper;

@Mixin(
   value = {PlayerManager.class},
   priority = 800
)
public class MixinPlayerList {
   @Shadow
   @Final
   private List<ServerPlayerEntity> players;
   @Shadow
   @Final
   private MinecraftServer server;

   @Inject(
      method = {"Lnet/minecraft/PlayerManager;sendWorldInfo(Lnet/minecraft/ServerPlayerEntity;Lnet/minecraft/ServerWorld;)V"},
      at = {@At("RETURN")}
   )
   private void onSendWorldInfo(ServerPlayerEntity player, ServerWorld world, CallbackInfo ci) {
      if (!ServerTeleportationManager.of(player.server).isFiringMyChangeDimensionEvent) {
         GlobalPortalStorage.onPlayerLoggedIn(player);
      }
   }

   @Inject(
      method = {"onPlayerConnect"},
      at = {@At("TAIL")}
   )
   private void onOnPlayerConnect(ClientConnection connection, ServerPlayerEntity player, ConnectedClientData commonListenerCookie, CallbackInfo ci) {
      ImmPtlChunkTracking.immediatelyUpdateForPlayer(player);
      Helper.LOGGER.info("Player login {} {}", player.getServerWorld().getTime(), player);
   }

   @Inject(
      method = {"Lnet/minecraft/PlayerManager;sendToDimension(Lnet/minecraft/Packet;Lnet/minecraft/RegistryKey;)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   public void sendToDimension(Packet<?> packet, RegistryKey<World> dimension, CallbackInfo ci) {
      for (ServerPlayerEntity player : this.players) {
         if (player.getServerWorld().getRegistryKey() == dimension) {
            PacketRedirection.sendRedirectedMessage(player, dimension, (Packet<ClientPlayPacketListener>)packet);
         }
      }

      ci.cancel();
   }

   @Redirect(
      method = {"respawnPlayer"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/ServerPlayerEntity;copyFrom(Lnet/minecraft/ServerPlayerEntity;Z)V"
      )}
   )
   private void onRestoreFrom(ServerPlayerEntity newPlayer, ServerPlayerEntity that, boolean keepEverything) {
      newPlayer.copyFrom(that, keepEverything);
      newPlayer.networkHandler.player = newPlayer;
   }

   @Overwrite
   public void sendToAround(
      @Nullable PlayerEntity excludingPlayer, double x, double y, double z, double distance, RegistryKey<World> dimension, Packet<?> packet
   ) {
      ChunkPos chunkPos = new ChunkPos(BlockPos.ofFloored(new Vec3d(x, y, z)));
      Object2ObjectOpenHashMap<ServerPlayerEntity, ImmPtlChunkTracking.PlayerWatchRecord> recs = ImmPtlChunkTracking.getWatchRecordForChunk(
         dimension, chunkPos.x, chunkPos.z
      );
      if (recs != null) {
         ObjectIterator var14 = recs.values().iterator();

         while (var14.hasNext()) {
            ImmPtlChunkTracking.PlayerWatchRecord rec = (ImmPtlChunkTracking.PlayerWatchRecord)var14.next();
            if (rec.isLoadedToPlayer
               && rec.player != excludingPlayer
               && ImmPtlChunkTracking.isPlayerWatchingChunkWithinRadius(rec.player, dimension, chunkPos.x, chunkPos.z, (int)distance + 16)) {
               rec.player
                  .networkHandler
                  .sendPacket(PacketRedirection.createRedirectedMessage(rec.player.getServerWorld().getServer(), dimension, (Packet<ClientPlayPacketListener>)packet));
            }
         }
      }
   }
}
