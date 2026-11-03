package qouteall.imm_ptl.core.mixin.client.sync;

import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import net.minecraft.util.math.Vec3d;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.network.packet.s2c.play.UnloadChunkS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityPassengersSetS2CPacket;
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.RegistryKey;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.network.packet.s2c.play.LightData;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientConnectionState;
import net.minecraft.registry.DynamicRegistryManager.class_6890;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.ducks.IEClientPlayNetworkHandler;
import qouteall.imm_ptl.core.ducks.IEPlayerPositionLookS2CPacket;
import qouteall.imm_ptl.core.network.ImmPtlNetworkConfig;
import qouteall.imm_ptl.core.teleportation.ClientTeleportationManager;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.my_util.CountDownInt;

@Mixin({ClientPlayNetworkHandler.class})
public abstract class MixinClientPacketListener implements IEClientPlayNetworkHandler {
   private static CountDownInt LOG_LIMIT = new CountDownInt(20);
   @Shadow
   private ClientWorld world;
   @Mutable
   @Shadow
   @Final
   private Map<UUID, PlayerListEntry> playerListEntries;
   @Shadow
   @Final
   private static Logger LOGGER;
   private boolean isReProcessingPassengerPacket;

   @Shadow
   public abstract void onEntityPassengersSet(EntityPassengersSetS2CPacket var1);

   @Shadow
   protected abstract void readLightData(int var1, int var2, LightData var3, boolean var4);

   @Shadow
   public abstract class_6890 getRegistryManager();

   @Shadow
   protected abstract void scheduleRenderChunk(WorldChunk var1, int var2, int var3);

   @Override
   public void ip_setWorld(ClientWorld world) {
      this.world = world;
   }

   @Inject(
      method = {"<init>"},
      at = {@At("RETURN")}
   )
   private void onInit(MinecraftClient minecraft, ClientConnection connection, ClientConnectionState commonListenerCookie, CallbackInfo ci) {
      this.isReProcessingPassengerPacket = false;
   }

   @Inject(
      method = {"Lnet/minecraft/ClientPlayNetworkHandler;onPlayerPositionLook(Lnet/minecraft/PlayerPositionLookS2CPacket;)V"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/NetworkThreadUtils;forceMainThread(Lnet/minecraft/Packet;Lnet/minecraft/PacketListener;Lnet/minecraft/PacketApplyBatcher;)V",
         shift = Shift.AFTER
      )}
   )
   private void onProcessingPositionPacket(PlayerPositionLookS2CPacket packet, CallbackInfo ci) {
      if (ImmPtlNetworkConfig.doesServerHaveImmPtl()) {
         RegistryKey<World> packetDim = ((IEPlayerPositionLookS2CPacket)packet).ip_getPlayerDimension();
         ClientPlayerEntity player = MinecraftClient.getInstance().player;

         assert player != null;

         World playerWorld = player.getEntityWorld();
         if (packetDim != playerWorld.getRegistryKey()) {
            LOGGER.info(
               "[ImmPtl] Client accepted position packet in another dimension. Packet: {} {} {} {}. Player: {} {} {} {}",
               new Object[]{
                  packetDim.getValue(),
                  packet.comp_3228().comp_3148().x,
                  packet.comp_3228().comp_3148().y,
                  packet.comp_3228().comp_3148().z,
                  playerWorld.getRegistryKey().getValue(),
                  player.getX(),
                  player.getY(),
                  player.getZ()
               }
            );
            ClientTeleportationManager.forceTeleportPlayer(
               packetDim,
               new Vec3d(packet.comp_3228().comp_3148().x, packet.comp_3228().comp_3148().y, packet.comp_3228().comp_3148().z)
            );
         }

         LOGGER.info(
            "[ImmPtl] Client accepted position packet {} {} {} {}",
            new Object[]{
               packetDim.getValue(),
               packet.comp_3228().comp_3148().x,
               packet.comp_3228().comp_3148().y,
               packet.comp_3228().comp_3148().z
            }
         );
      }
   }

   @Inject(
      method = {"Lnet/minecraft/ClientPlayNetworkHandler;onEntityPassengersSet(Lnet/minecraft/EntityPassengersSetS2CPacket;)V"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/NetworkThreadUtils;forceMainThread(Lnet/minecraft/Packet;Lnet/minecraft/PacketListener;Lnet/minecraft/PacketApplyBatcher;)V",
         shift = Shift.AFTER
      )},
      cancellable = true
   )
   private void onOnEntityPassengersSet(EntityPassengersSetS2CPacket entityPassengersSetS2CPacket_1, CallbackInfo ci) {
      Entity entity_1 = this.world.getEntityById(entityPassengersSetS2CPacket_1.getEntityId());
      if (entity_1 == null && !this.isReProcessingPassengerPacket) {
         Helper.log("Re-processed riding packet");
         IPGlobal.CLIENT_TASK_LIST.addTask(() -> {
            this.isReProcessingPassengerPacket = true;
            this.onEntityPassengersSet(entityPassengersSetS2CPacket_1);
            this.isReProcessingPassengerPacket = false;
            return true;
         });
         ci.cancel();
      }
   }

   @Redirect(
      method = {"Lnet/minecraft/ClientPlayNetworkHandler;onEntityTrackerUpdate(Lnet/minecraft/EntityTrackerUpdateS2CPacket;)V"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/ClientWorld;getEntityById(I)Lnet/minecraft/Entity;"
      )}
   )
   private Entity redirectGetEntityById(ClientWorld clientWorld, int id) {
      Entity entity = clientWorld.getEntityById(id);
      if (entity == null && LOG_LIMIT.tryDecrement()) {
         LOGGER.warn("missing entity for data tracking {} {}", clientWorld, id);
      }

      return entity;
   }

   @Inject(
      method = {"onWorldTimeUpdate"},
      at = {@At("RETURN")}
   )
   private void onSetTime(WorldTimeUpdateS2CPacket packet, CallbackInfo ci) {
      if (ClientWorldLoader.getIsInitialized()) {
         ClientWorld currentWorld = MinecraftClient.getInstance().world;

         for (ClientWorld clientWorld : ClientWorldLoader.getClientWorlds()) {
            if (clientWorld != currentWorld) {
               clientWorld.method_28104().setTime(packet.comp_3219());
            }
         }
      }
   }

   @Redirect(
      method = {"onPlayerActionResponse"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/ClientWorld;handlePlayerActionResponse(I)V"
      )}
   )
   private void redirectHandleBlockChangedAck(ClientWorld instance, int seqNumber) {
      for (ClientWorld clientWorld : ClientWorldLoader.getClientWorlds()) {
         clientWorld.handlePlayerActionResponse(seqNumber);
      }
   }

   @Inject(
      method = {"onEntitySpawn"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/NetworkThreadUtils;forceMainThread(Lnet/minecraft/Packet;Lnet/minecraft/PacketListener;Lnet/minecraft/PacketApplyBatcher;)V",
         shift = Shift.AFTER
      )},
      cancellable = true
   )
   private void onHandleAddEntity(EntitySpawnS2CPacket packet, CallbackInfo ci) {
      int entityId = packet.getEntityId();
      Entity existingEntity = this.world.getEntityById(entityId);
      if (existingEntity != null && !existingEntity.getPassengerList().isEmpty()) {
         LOGGER.warn("[ImmPtl] Entity already exists and has passengers when accepting add-entity packet. Ignoring. {} {}", existingEntity, packet);
         ci.cancel();
      }
   }

   @Inject(
      method = {"onChunkData"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/NetworkThreadUtils;forceMainThread(Lnet/minecraft/Packet;Lnet/minecraft/PacketListener;Lnet/minecraft/PacketApplyBatcher;)V",
         shift = Shift.AFTER
      )}
   )
   private void onHandleLevelChunkWithLight(ChunkDataS2CPacket packet, CallbackInfo ci) {
      if (IPGlobal.chunkPacketDebug) {
         LOGGER.info(
            "Chunk Load Packet {} {} {}", new Object[]{this.world.getRegistryKey().getValue(), packet.getChunkX(), packet.getChunkZ()}
         );
      }
   }

   @Inject(
      method = {"onUnloadChunk"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/NetworkThreadUtils;forceMainThread(Lnet/minecraft/Packet;Lnet/minecraft/PacketListener;Lnet/minecraft/PacketApplyBatcher;)V",
         shift = Shift.AFTER
      )}
   )
   private void onHandleForgetLevelChunk(UnloadChunkS2CPacket packet, CallbackInfo ci) {
      if (IPGlobal.chunkPacketDebug) {
         LOGGER.info(
            "Chunk Unload Packet {} {} {}",
            new Object[]{this.world.getRegistryKey().getValue(), packet.comp_1726().x, packet.comp_1726().z}
         );
      }
   }
}
