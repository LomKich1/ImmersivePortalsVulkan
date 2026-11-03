package qouteall.imm_ptl.core.network;

import com.mojang.logging.LogUtils;
import java.util.Objects;
import java.util.UUID;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.world.World;
import net.minecraft.util.math.Vec3d;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.registry.RegistryKey;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload.class_9154;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.api.PortalAPI;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.global_portals.GlobalPortalStorage;
import qouteall.imm_ptl.core.teleportation.ServerTeleportationManager;
import qouteall.q_misc_util.platform.PlatformNetworking;

public class ImmPtlNetworking {
   private static final Logger LOGGER = LogUtils.getLogger();

   public static void init() {
      PlatformNetworking.get().registerPlayC2S(ImmPtlNetworking.TeleportPacket.TYPE, ImmPtlNetworking.TeleportPacket.CODEC);
      PlatformNetworking.get().registerPlayS2C(ImmPtlNetworking.GlobalPortalSyncPacket.TYPE, ImmPtlNetworking.GlobalPortalSyncPacket.CODEC);
      PlatformNetworking.get().registerPlayS2C(ImmPtlNetworking.PortalSyncPacket.TYPE, ImmPtlNetworking.PortalSyncPacket.CODEC);
      PlatformNetworking.get().registerServerReceiver(ImmPtlNetworking.TeleportPacket.TYPE, (packet, player) -> packet.handle(player));
   }

   public static void initClient() {
      PlatformNetworking.get().registerClientReceiver(ImmPtlNetworking.GlobalPortalSyncPacket.TYPE, (packet, c) -> packet.handle());
      PlatformNetworking.get().registerClientReceiver(ImmPtlNetworking.PortalSyncPacket.TYPE, (packet, c) -> packet.handle());
   }

   public record GlobalPortalSyncPacket(int dimensionId, NbtCompound data) implements CustomPayload {
      public static final class_9154<ImmPtlNetworking.GlobalPortalSyncPacket> TYPE = new class_9154(McHelper.newResourceLocation("imm_ptl:upd_glb_ptl"));
      public static final PacketCodec<PacketByteBuf, ImmPtlNetworking.GlobalPortalSyncPacket> CODEC = PacketCodec.ofStatic(
         (b, p) -> p.write(b), ImmPtlNetworking.GlobalPortalSyncPacket::read
      );

      public static ImmPtlNetworking.GlobalPortalSyncPacket read(PacketByteBuf buf) {
         int dimId = buf.readVarInt();
         NbtCompound compoundTag = buf.readNbt();
         return new ImmPtlNetworking.GlobalPortalSyncPacket(dimId, compoundTag);
      }

      public void write(PacketByteBuf buf) {
         buf.writeVarInt(this.dimensionId);
         buf.writeNbt(this.data);
      }

      @Environment(EnvType.CLIENT)
      public void handle() {
         RegistryKey<World> dim = PortalAPI.clientIntToDimKey(this.dimensionId);
         GlobalPortalStorage.receiveGlobalPortalSync(dim, this.data);
      }

      @NotNull
      public class_9154<? extends CustomPayload> getId() {
         return TYPE;
      }
   }

   public record PortalSyncPacket(int id, UUID uuid, EntityType<?> entityType, int dimensionId, double x, double y, double z, NbtCompound extraData)
      implements CustomPayload {
      public static final class_9154<ImmPtlNetworking.PortalSyncPacket> TYPE = new class_9154(McHelper.newResourceLocation("imm_ptl:spawn_portal"));
      public static final PacketCodec<RegistryByteBuf, ImmPtlNetworking.PortalSyncPacket> CODEC = PacketCodec.ofStatic(
         (b, p) -> p.write(b), ImmPtlNetworking.PortalSyncPacket::read
      );

      public void write(RegistryByteBuf buf) {
         buf.writeVarInt(this.id);
         buf.writeUuid(this.uuid);
         PacketCodecs.registryValue(RegistryKeys.field_41266).encode(buf, this.entityType);
         buf.writeVarInt(this.dimensionId);
         buf.method_52940(this.x);
         buf.method_52940(this.y);
         buf.method_52940(this.z);
         buf.writeNbt(this.extraData);
      }

      public static ImmPtlNetworking.PortalSyncPacket read(RegistryByteBuf buf) {
         int id = buf.readVarInt();
         UUID uuid = buf.readUuid();
         EntityType<?> type = (EntityType<?>)PacketCodecs.registryValue(RegistryKeys.field_41266).decode(buf);
         int dimensionId = buf.readVarInt();
         double x = buf.readDouble();
         double y = buf.readDouble();
         double z = buf.readDouble();
         NbtCompound extraData = buf.readNbt();
         return new ImmPtlNetworking.PortalSyncPacket(id, uuid, type, dimensionId, x, y, z, extraData);
      }

      @Environment(EnvType.CLIENT)
      public void handle() {
         RegistryKey<World> dimension = PortalAPI.clientIntToDimKey(this.dimensionId);
         ClientWorld world = ClientWorldLoader.getWorld(dimension);
         if (world.getEntityById(this.id) instanceof Portal existingPortal) {
            if (!Objects.equals(existingPortal.getUuid(), this.uuid)) {
               ImmPtlNetworking.LOGGER.error("UUID mismatch when syncing portal {} {}", existingPortal, this.uuid);
               return;
            }

            if (existingPortal.getType() != this.entityType) {
               ImmPtlNetworking.LOGGER.error("Entity type mismatch when syncing portal {} {}", existingPortal, this.entityType);
               return;
            }

            existingPortal.acceptDataSync(new Vec3d(this.x, this.y, this.z), this.extraData);
         } else {
            Entity entity = this.entityType.create(world, SpawnReason.field_52444);
            Validate.notNull(entity, "Entity type is null", new Object[0]);
            if (!(entity instanceof Portal portal)) {
               ImmPtlNetworking.LOGGER.error("Spawned entity is not a portal. {} {}", entity, this.entityType);
               return;
            }

            entity.setId(this.id);
            entity.setUuid(this.uuid);
            entity.updateTrackedPosition(this.x, this.y, this.z);
            entity.refreshPositionAfterTeleport(this.x, this.y, this.z);
            portal.readPortalDataFromNbt(this.extraData);
            world.addEntity(entity);
            ClientWorldLoader.getWorld(portal.getDestDim());
            Portal.CLIENT_PORTAL_SPAWN_EVENT.invoker().accept(portal);
            if (IPGlobal.clientPortalLoadDebug) {
               ImmPtlNetworking.LOGGER.info("Portal loaded to client {}", portal);
            }
         }
      }

      @NotNull
      public class_9154<? extends CustomPayload> getId() {
         return TYPE;
      }
   }

   public record TeleportPacket(int dimensionId, Vec3d eyePosBeforeTeleportation, UUID portalId) implements CustomPayload {
      public static final class_9154<ImmPtlNetworking.TeleportPacket> TYPE = new class_9154(Identifier.of("imm_ptl", "teleport"));
      public static final PacketCodec<PacketByteBuf, ImmPtlNetworking.TeleportPacket> CODEC = PacketCodec.ofStatic(
         (b, p) -> p.write(b), ImmPtlNetworking.TeleportPacket::read
      );

      public static ImmPtlNetworking.TeleportPacket read(PacketByteBuf buf) {
         int dimId = buf.readVarInt();
         Vec3d pos = new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble());
         UUID portalId = buf.readUuid();
         return new ImmPtlNetworking.TeleportPacket(dimId, pos, portalId);
      }

      public void write(PacketByteBuf buf) {
         buf.writeVarInt(this.dimensionId);
         buf.method_52940(this.eyePosBeforeTeleportation.x);
         buf.method_52940(this.eyePosBeforeTeleportation.y);
         buf.method_52940(this.eyePosBeforeTeleportation.z);
         buf.writeUuid(this.portalId);
      }

      public void handle(ServerPlayerEntity player) {
         RegistryKey<World> dim = PortalAPI.serverIntToDimKey(player.server, this.dimensionId);
         ServerTeleportationManager.of(player.server).onPlayerTeleportedInClient(player, dim, this.eyePosBeforeTeleportation, this.portalId);
      }

      @NotNull
      public class_9154<? extends CustomPayload> getId() {
         return TYPE;
      }
   }
}
