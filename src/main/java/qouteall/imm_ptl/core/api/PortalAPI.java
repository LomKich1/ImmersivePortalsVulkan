package qouteall.imm_ptl.core.api;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Pair;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.chunk_loading.ChunkLoader;
import qouteall.imm_ptl.core.chunk_loading.ImmPtlChunkTracking;
import qouteall.imm_ptl.core.network.PacketRedirection;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.PortalManipulation;
import qouteall.imm_ptl.core.portal.global_portals.GlobalPortalStorage;
import qouteall.imm_ptl.core.teleportation.ServerTeleportationManager;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.dimension.DimensionIntId;
import qouteall.q_misc_util.my_util.DQuaternion;
import qouteall.q_misc_util.my_util.IntBox;

public class PortalAPI {
   public static void setPortalPositionOrientationAndSize(Portal portal, Vec3d position, DQuaternion orientation, double width, double height) {
      portal.setOriginPos(position);
      portal.setOrientationAndSize(McHelper.getAxisWFromOrientation(orientation), McHelper.getAxisHFromOrientation(orientation), width, height);
   }

   public static void setPortalOrthodoxShape(Portal portal, Direction facing, Box portalArea) {
      Pair<Direction, Direction> directions = Helper.getPerpendicularDirections(facing);
      Vec3d areaSize = Helper.getBoxSize(portalArea);
      Box boxSurface = Helper.getBoxSurface(portalArea, facing);
      Vec3d center = boxSurface.getCenter();
      portal.setPosition(center.x, center.y, center.z);
      portal.setAxisW(Vec3d.of(((Direction)directions.getLeft()).getVector()));
      portal.setAxisH(Vec3d.of(((Direction)directions.getRight()).getVector()));
      portal.setWidth(Helper.getCoordinate(areaSize, ((Direction)directions.getLeft()).getAxis()));
      portal.setHeight(Helper.getCoordinate(areaSize, ((Direction)directions.getRight()).getAxis()));
   }

   public static void setPortalTransformation(
      Portal portal, RegistryKey<World> destinationDimension, Vec3d destinationPosition, @Nullable DQuaternion rotation, double scale
   ) {
      portal.setDestinationDimension(destinationDimension);
      portal.setDestination(destinationPosition);
      portal.setRotation(rotation);
      portal.setScaleTransformation(scale);
   }

   public static DQuaternion getPortalOrientationQuaternion(Portal portal) {
      return PortalManipulation.getPortalOrientationQuaternion(portal.getAxisW(), portal.getAxisH());
   }

   public static void setPortalOrientationQuaternion(Portal portal, DQuaternion quaternion) {
      PortalManipulation.setPortalOrientationQuaternion(portal, quaternion);
   }

   @Deprecated
   public static void spawnServerEntity(Entity entity) {
      McHelper.spawnServerEntity(entity);
   }

   public static <T extends Portal> T createReversePortal(T portal) {
      return (T)PortalManipulation.createReversePortal(portal, (EntityType<Portal>)(EntityType<?>)portal.getType());
   }

   public static <T extends Portal> T createFlippedPortal(T portal) {
      return (T)PortalManipulation.createFlippedPortal(portal, (EntityType<Portal>)(EntityType<?>)portal.getType());
   }

   public static <T extends Portal> T copyPortal(Portal portal, EntityType<T> entityType) {
      return (T)PortalManipulation.copyPortal(portal, (EntityType<Portal>)(EntityType<?>)entityType);
   }

   public static void addGlobalPortal(ServerWorld world, Portal portal) {
      McHelper.validateOnServerThread();
      GlobalPortalStorage.get(world).addPortal(portal);
   }

   public static void removeGlobalPortal(ServerWorld world, Portal portal) {
      McHelper.validateOnServerThread();
      GlobalPortalStorage.get(world).removePortal(portal);
   }

   public static void addChunkLoaderForPlayer(ServerPlayerEntity player, ChunkLoader chunkLoader) {
      McHelper.validateOnServerThread();
      ImmPtlChunkTracking.addPerPlayerAdditionalChunkLoader(player, chunkLoader);
   }

   public static void removeChunkLoaderForPlayer(ServerPlayerEntity player, ChunkLoader chunkLoader) {
      McHelper.validateOnServerThread();
      ImmPtlChunkTracking.removePerPlayerAdditionalChunkLoader(player, chunkLoader);
   }

   public static void addGlobalChunkLoader(MinecraftServer server, ChunkLoader chunkLoader) {
      ImmPtlChunkTracking.addGlobalAdditionalChunkLoader(server, chunkLoader);
   }

   public static void removeGlobalChunkLoader(MinecraftServer server, ChunkLoader chunkLoader) {
      ImmPtlChunkTracking.removeGlobalAdditionalChunkLoader(server, chunkLoader);
   }

   public static Entity teleportEntity(Entity entity, ServerWorld targetWorld, Vec3d targetPos) {
      return ServerTeleportationManager.teleportEntityGeneral(entity, targetPos, targetWorld);
   }

   public static void syncBlockUpdateToClientImmediately(ServerWorld world, IntBox box) {
      ImmPtlChunkTracking.syncBlockUpdateToClientImmediately(world, box);
   }

   @Environment(EnvType.CLIENT)
   public static int clientDimKeyToInt(RegistryKey<World> dimension) {
      return DimensionIntId.getClientMap().toIntegerId(dimension);
   }

   @Environment(EnvType.CLIENT)
   public static RegistryKey<World> clientIntToDimKey(int integerId) {
      return DimensionIntId.getClientMap().fromIntegerId(integerId);
   }

   public static int serverDimKeyToInt(MinecraftServer server, RegistryKey<World> dimension) {
      return DimensionIntId.getServerMap(server).toIntegerId(dimension);
   }

   public static RegistryKey<World> serverIntToDimKey(MinecraftServer server, int integerId) {
      return DimensionIntId.getServerMap(server).fromIntegerId(integerId);
   }

   public static void sendPacketToEntityTrackers(Entity entity, Packet<ClientPlayPacketListener> packet) {
      McHelper.sendToTrackers(
         entity, PacketRedirection.createRedirectedMessage(entity.getEntityWorld().getServer(), entity.getEntityWorld().getRegistryKey(), packet)
      );
   }
}
