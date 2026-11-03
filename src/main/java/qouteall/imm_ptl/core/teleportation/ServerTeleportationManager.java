package qouteall.imm_ptl.core.teleportation;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.Entity.RemovalReason;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.profiler.Profilers;
import net.minecraft.world.World;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import qouteall.dimlib.api.DimensionAPI;
import qouteall.imm_ptl.core.IPMcHelper;
import qouteall.imm_ptl.core.IPPerServerInfo;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.ScaleUtils;
import qouteall.imm_ptl.core.chunk_loading.ImmPtlChunkTracking;
import qouteall.imm_ptl.core.collision.PortalCollisionHandler;
import qouteall.imm_ptl.core.compat.GravityChangerInterface;
import qouteall.imm_ptl.core.ducks.IEEntity;
import qouteall.imm_ptl.core.ducks.IEServerPlayNetworkHandler;
import qouteall.imm_ptl.core.ducks.IEServerPlayerEntity;
import qouteall.imm_ptl.core.mc_utils.ServerTaskList;
import qouteall.imm_ptl.core.platform_specific.IPConfig;
import qouteall.imm_ptl.core.platform_specific.O_O;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.global_portals.GlobalPortalStorage;
import qouteall.q_misc_util.MiscHelper;
import qouteall.q_misc_util.api.McRemoteProcedureCall;
import qouteall.q_misc_util.my_util.MyTaskList;
import qouteall.q_misc_util.my_util.WithDim;
import qouteall.q_misc_util.platform.Platform;

public class ServerTeleportationManager {
   private static final Logger LOGGER = LogUtils.getLogger();
   private final Set<Entity> teleportingEntities = new HashSet<>();
   private final WeakHashMap<Entity, Long> lastTeleportGameTime = new WeakHashMap<>();
   public boolean isFiringMyChangeDimensionEvent = false;
   public final WeakHashMap<ServerPlayerEntity, WithDim<Vec3d>> lastPosition = new WeakHashMap<>();

   public static ServerTeleportationManager of(MinecraftServer server) {
      return IPPerServerInfo.of(server).teleportationManager;
   }

   public static void init() {
      Platform.get().onServerTickEnd(server -> of(server).tick(server));
      Portal.SERVER_PORTAL_TICK_SIGNAL.register(portal -> {
         ServerTeleportationManager serverTeleportationManager = of(portal.getEntityWorld().getServer());
         getEntitiesToTeleport(portal).forEach(entity -> serverTeleportationManager.startTeleportingRegularEntity(portal, entity));
      });
      DimensionAPI.SERVER_PRE_REMOVE_DIMENSION_EVENT.register(world -> of(world.getServer()).evacuatePlayersFromDimension(world));
   }

   private void tick(MinecraftServer server) {
      this.teleportingEntities.clear();
      this.manageGlobalPortalTeleportation();
   }

   public static boolean shouldEntityTeleport(Portal portal, Entity entity) {
      if (entity.getEntityWorld() != portal.getEntityWorld()) {
         return false;
      } else if (!portal.canTeleportEntity(entity)) {
         return false;
      } else {
         Vec3d lastEyePos = isFreshEntity(entity) ? entity.getCameraPosVec(1.0F) : entity.getCameraPosVec(0.0F);
         Vec3d nextEyePos = entity.getCameraPosVec(1.0F);
         if (entity instanceof ProjectileEntity) {
            nextEyePos = nextEyePos.add(McHelper.getWorldVelocity(entity));
         }

         return portal.isMovedThroughPortal(lastEyePos, nextEyePos);
      }
   }

   public void startTeleportingRegularEntity(Portal portal, Entity entity) {
      if (!(entity instanceof ServerPlayerEntity)) {
         if (!(entity instanceof Portal)) {
            if (entity.getVehicle() == null && !this.doesEntityClusterContainPlayer(entity)) {
               if (!entity.isRemoved()) {
                  if (entity.canTeleportBetween(entity.getEntityWorld(), portal.getDestinationWorld())) {
                     if (!this.isJustTeleported(entity, 1L)) {
                        if (isFreshEntity(entity)) {
                           LOGGER.debug("Teleporting a fresh new entity {}", entity);
                           entity.resetPosition();
                        }

                        double motion = McHelper.lastTickPosOf(entity).squaredDistanceTo(entity.getEntityPos());
                        if (!(motion > 20.0)) {
                           ServerTaskList.of(portal.getEntityWorld().getServer()).addTask(() -> {
                              try {
                                 this.teleportRegularEntity(entity, portal);
                              } catch (Throwable var4) {
                                 LOGGER.error("", var4);
                              }

                              return true;
                           });
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static Stream<Entity> getEntitiesToTeleport(Portal portal) {
      return portal.getEntityWorld()
         .getEntitiesByClass(Entity.class, portal.getBoundingBox().expand(2.0), e -> true)
         .stream()
         .filter(e -> !(e instanceof Portal))
         .filter(entity -> shouldEntityTeleport(portal, entity));
   }

   public void onPlayerTeleportedInClient(ServerPlayerEntity player, RegistryKey<World> dimensionBefore, Vec3d eyePosBeforeTeleportation, UUID portalId) {
      if (player.getRemovalReason() != null) {
         LOGGER.error("Trying to teleport a removed player {}", player);
      } else {
         Portal portal = this.findPortal(player.server, dimensionBefore, portalId);
         if (portal == null) {
            LOGGER.error("Unable to find portal {} in {} to teleport {}", new Object[]{portalId, dimensionBefore.getValue(), player});
         } else {
            this.lastTeleportGameTime.put(player, McHelper.getServerGameTime());
            Vec3d oldFeetPos = eyePosBeforeTeleportation.subtract(McHelper.getEyeOffset(player));
            String failReason = this.validatePlayerTeleportationAndGetReason(player, dimensionBefore, oldFeetPos, portal);
            if (failReason == null) {
               if (this.isTeleporting(player)) {
                  LOGGER.info("{} is teleporting frequently", player);
               }

               notifyChasersForPlayer(player, portal);
               RegistryKey<World> dimensionTo = portal.getDestDim();
               Vec3d newEyePos = portal.transformPoint(eyePosBeforeTeleportation);
               this.recordLastPosition(player, dimensionBefore, oldFeetPos);
               this.teleportPlayer(player, dimensionTo, newEyePos);
               portal.onEntityTeleportedOnServer(player);
               ScaleUtils.onServerEntityTeleported(player, portal);
               if (portal.getTeleportChangesGravity()) {
                  Direction oldGravityDir = GravityChangerInterface.invoker.getGravityDirection(player);
                  GravityChangerInterface.invoker.setBaseGravityDirectionServer(player, portal.getTransformedGravityDirection(oldGravityDir));
               }
            } else {
               LOGGER.error(
                  "Player {} {} {} cannot teleport through portal {}\nReason: {}",
                  new Object[]{player, player.getEntityWorld().getRegistryKey().getValue(), player.getEntityPos(), portal, failReason}
               );
               teleportEntityGeneral(player, player.getEntityPos(), player.getEntityWorld());
               ScaleUtils.setBaseScale(player, ScaleUtils.getBaseScale(player));
               GravityChangerInterface.invoker.setBaseGravityDirectionServer(player, GravityChangerInterface.invoker.getGravityDirection(player));
            }
         }
      }
   }

   @Nullable
   private Portal findPortal(MinecraftServer server, RegistryKey<World> dimensionBefore, UUID portalId) {
      ServerWorld originalWorld = server.getWorld(dimensionBefore);
      if (originalWorld == null) {
         LOGGER.error("Missing world {} when finding portal", dimensionBefore.getValue());
         return null;
      } else {
         Entity portalEntity = originalWorld.getEntity(portalId);
         if (portalEntity == null) {
            portalEntity = GlobalPortalStorage.get(originalWorld).data.stream().filter(p -> p.getUuid().equals(portalId)).findFirst().orElse(null);
         }

         if (portalEntity == null) {
            return null;
         } else {
            return portalEntity instanceof Portal ? (Portal)portalEntity : null;
         }
      }
   }

   public void recordLastPosition(ServerPlayerEntity player, RegistryKey<World> dim, Vec3d pos) {
      this.lastPosition.put(player, new WithDim<>(dim, pos));
   }

   @Nullable
   private String validatePlayerTeleportationAndGetReason(ServerPlayerEntity player, RegistryKey<World> dimensionBefore, Vec3d posBefore, Portal portal) {
      if (player.getVehicle() != null) {
         return null;
      } else if (((IEServerPlayNetworkHandler)player.networkHandler).ip_hasAwaitingTeleport()) {
         return "has awaiting teleport";
      } else if (!portal.canTeleportEntity(player)) {
         return "portal cannot teleport player";
      } else if (player.getEntityWorld().getRegistryKey() != dimensionBefore) {
         return "player is not in the dimensionBefore in packet";
      } else if (player.getEntityPos().squaredDistanceTo(posBefore) > 256.0) {
         return "player is too far from the posBefore in packet";
      } else {
         return portal.getDistanceToNearestPointInPortal(posBefore) > 20.0 ? "posBefore is too far from portal" : null;
      }
   }

   public static boolean canPlayerReachPos(ServerPlayerEntity player, RegistryKey<World> dimension, Vec3d pos) {
      Vec3d playerPos = player.getEntityPos();
      return player.getEntityWorld().getRegistryKey() == dimension && playerPos.squaredDistanceTo(pos) < 256.0
         ? true
         : IPMcHelper.getNearbyPortals(player, 20.0)
            .filter(portal -> portal.getDestDim() == dimension)
            .filter(portal -> portal.canTeleportEntity(player))
            .map(portal -> portal.transformPoint(playerPos))
            .anyMatch(mappedPos -> mappedPos.squaredDistanceTo(pos) < 256.0);
   }

   public static boolean canPlayerReachBlockEntity(ServerPlayerEntity player, BlockEntity blockEntity) {
      World world = blockEntity.getWorld();
      return world == null ? false : canPlayerReachPos(player, world.getRegistryKey(), Vec3d.ofCenter(blockEntity.getPos()));
   }

   public void teleportPlayer(ServerPlayerEntity player, RegistryKey<World> dimensionTo, Vec3d newEyePos) {
      MinecraftServer server = player.server;
      Profilers.get().push("portal_teleport");
      ServerWorld fromWorld = player.getEntityWorld();
      ServerWorld toWorld = server.getWorld(dimensionTo);
      if (player.getEntityWorld().getRegistryKey() == dimensionTo) {
         McHelper.setEyePos(player, newEyePos, newEyePos);
         McHelper.updateBoundingBox(player);
      } else {
         this.changePlayerDimension(player, fromWorld, toWorld, newEyePos);
      }

      McHelper.adjustVehicle(player);
      player.networkHandler.syncWithPlayerPosition();
      PortalCollisionHandler.updateCollidingPortalAfterTeleportation(player, newEyePos, newEyePos, 1.0F);
      Profilers.get().pop();
   }

   public void forceTeleportPlayer(ServerPlayerEntity player, RegistryKey<World> dimensionTo, Vec3d newPos) {
      this.forceTeleportPlayer(player, dimensionTo, newPos, true);
   }

   public void forceTeleportPlayer(ServerPlayerEntity player, RegistryKey<World> dimensionTo, Vec3d newPos, boolean sendPacket) {
      if (IPConfig.getConfig().serverTeleportLogging) {
         LOGGER.info("Force teleporting {} to {} {}", new Object[]{player, dimensionTo.getValue(), newPos});
      }

      ServerWorld fromWorld = player.getEntityWorld();
      ServerWorld toWorld = player.server.getWorld(dimensionTo);
      if (toWorld == null) {
         LOGGER.error("Cannot teleport player {} to non-existing dimension {}", player, dimensionTo.getValue());
      } else {
         if (player.getEntityWorld().getRegistryKey() == dimensionTo) {
            player.setPosition(newPos.x, newPos.y, newPos.z);
         } else {
            this.changePlayerDimension(player, fromWorld, toWorld, newPos.add(McHelper.getEyeOffset(player)));
         }

         if (sendPacket) {
            player.networkHandler.requestTeleport(newPos.x, newPos.y, newPos.z, player.getYaw(), player.getPitch());
         }

         player.networkHandler.syncWithPlayerPosition();
         Vec3d newEyePos = McHelper.getEyePos(player);
         PortalCollisionHandler.updateCollidingPortalAfterTeleportation(player, newEyePos, newEyePos, 1.0F);
         ImmPtlChunkTracking.immediatelyUpdateForPlayer(player);
      }
   }

   private void changePlayerDimension(ServerPlayerEntity player, ServerWorld fromWorld, ServerWorld toWorld, Vec3d newEyePos) {
      this.teleportingEntities.add(player);
      Entity vehicle = player.getVehicle();
      if (vehicle != null) {
         ((IEServerPlayerEntity)player).ip_stopRidingWithoutTeleportRequest();
      }

      Vec3d oldPos = player.getEntityPos();
      fromWorld.removePlayer(player, RemovalReason.CHANGED_DIMENSION);
      ((IEEntity)player).ip_unsetRemoved();
      McHelper.setEyePos(player, newEyePos, newEyePos);
      McHelper.updateBoundingBox(player);
      player.setServerWorld(toWorld);
      toWorld.onDimensionChanged(player);
      if (vehicle != null) {
         Vec3d offset = McHelper.getVehicleOffsetFromPassenger(vehicle, player);
         Vec3d vehiclePos = player.getEntityPos().add(offset);
         vehicle = this.teleportVehicleAcrossDimensions(vehicle, toWorld.getRegistryKey(), vehiclePos.add(McHelper.getEyeOffset(vehicle)));
         McHelper.setPosAndLastTickPos(vehicle, player.getEntityPos().add(offset), McHelper.lastTickPosOf(player).add(offset));
         ((IEServerPlayerEntity)player).ip_startRidingWithoutTeleportRequest(vehicle);
         McHelper.adjustVehicle(player);
      }

      if (IPConfig.getConfig().serverTeleportLogging) {
         LOGGER.info(
            "{} :: ({} {} {} {})->({} {} {} {})",
            new Object[]{
               player.getName().getContent(),
               fromWorld.getRegistryKey().getValue(),
               oldPos.getX(),
               oldPos.getY(),
               oldPos.getZ(),
               toWorld.getRegistryKey().getValue(),
               (int)player.getX(),
               (int)player.getY(),
               (int)player.getZ()
            }
         );
      }

      O_O.onPlayerTravelOnServer(player, fromWorld, toWorld);
      ((IEServerPlayerEntity)player).portal_worldChanged(fromWorld, oldPos);
      Platform.get().onPlayerChangedDimension(player, fromWorld.getRegistryKey(), toWorld.getRegistryKey());
   }

   private void manageGlobalPortalTeleportation() {
      for (ServerWorld world : MiscHelper.getServer().getWorlds()) {
         for (Entity entity : world.iterateEntities()) {
            if (!(entity instanceof ServerPlayerEntity)) {
               Portal collidingPortal = ((IEEntity)entity).ip_getCollidingPortal();
               if (collidingPortal != null && collidingPortal.getIsGlobal() && shouldEntityTeleport(collidingPortal, entity)) {
                  this.startTeleportingRegularEntity(collidingPortal, entity);
               }
            }
         }
      }
   }

   public boolean isTeleporting(Entity entity) {
      return this.teleportingEntities.contains(entity);
   }

   private void teleportRegularEntity(Entity entity, Portal portal) {
      Validate.isTrue(!(entity instanceof ServerPlayerEntity));
      if (entity.getRemovalReason() != null) {
         LOGGER.error("Trying to teleport an entity that is already removed {} {}", entity, portal);
      } else if (entity.getEntityWorld() != portal.getEntityWorld()) {
         LOGGER.error("Cannot teleport {} from {} through {}", new Object[]{entity, entity.getEntityWorld().getRegistryKey(), portal});
      } else if (portal.getDistanceToNearestPointInPortal(entity.getEyePos()) > 5.0) {
         LOGGER.error("Entity is too far to teleport {} {}", entity, portal);
      } else {
         long currGameTime = McHelper.getServerGameTime();
         Long lastTeleportGameTime = this.lastTeleportGameTime.getOrDefault(entity, 0L);
         if (currGameTime - lastTeleportGameTime > 0L) {
            this.lastTeleportGameTime.put(entity, currGameTime);
            if (!entity.hasVehicle() && !this.doesEntityClusterContainPlayer(entity)) {
               Vec3d velocity = entity.getVelocity();
               Vec3d oldPos = entity.getEntityPos();
               List<Entity> passengerList = entity.getPassengerList();
               Vec3d newEyePos = getRegularEntityTeleportedEyePos(entity, portal);
               TeleportationUtil.transformEntityVelocity(portal, entity, TeleportationUtil.PortalPointVelocity.ZERO, oldPos);
               if (portal.getDestDim() != entity.getEntityWorld().getRegistryKey()) {
                  entity = this.changeEntityDimension(entity, portal.getDestDim(), newEyePos, true);
                  passengerList.stream()
                     .map(e -> this.changeEntityDimension(e, portal.getDestDim(), newEyePos, true))
                     .collect(Collectors.toList())
                     .forEach(e -> e.startRiding(entity, true, false));
               }

               McHelper.setEyePos(entity, newEyePos, newEyePos);
               McHelper.updateBoundingBox(entity);
               McHelper.sendToTrackers(
                  entity,
                  McRemoteProcedureCall.createPacketToSendToClient(
                     "qouteall.imm_ptl.core.teleportation.ClientTeleportationManager.RemoteCallables.updateEntityPos",
                     entity.getEntityWorld().getRegistryKey(),
                     entity.getId(),
                     entity.getEntityPos()
                  )
               );
               portal.onEntityTeleportedOnServer(entity);
               ScaleUtils.onServerEntityTeleported(entity, portal);
               this.lastTeleportGameTime.put(entity, currGameTime);
            }
         }
      }
   }

   private static Vec3d getRegularEntityTeleportedEyePos(Entity entity, Portal portal) {
      Vec3d eyePosThisTick = McHelper.getEyePos(entity);
      Vec3d eyePosLastTick = McHelper.getLastTickEyePos(entity);
      Vec3d deltaMovement = eyePosThisTick.subtract(eyePosLastTick);
      Vec3d deltaMovementDirection = deltaMovement.normalize();
      Vec3d collidingPoint = portal.rayTrace(eyePosThisTick.subtract(deltaMovementDirection.multiply(5.0)), eyePosThisTick.add(deltaMovementDirection));
      if (collidingPoint == null) {
         collidingPoint = eyePosLastTick;
      }

      return portal.transformPoint(collidingPoint).add(deltaMovementDirection.multiply(0.05));
   }

   public Entity changeEntityDimension(Entity entity, RegistryKey<World> toDimension, Vec3d newEyePos, boolean recreateEntity) {
      if (entity.getRemovalReason() != null) {
         LOGGER.error("Trying to teleport a removed entity {}", entity, new Throwable());
         return entity;
      } else {
         MinecraftServer server = entity.getEntityWorld().getServer();
         Validate.notNull(server, "server is null", new Object[0]);
         ServerWorld fromWorld = (ServerWorld)entity.getEntityWorld();
         ServerWorld toWorld = server.getWorld(toDimension);
         if (toWorld == null) {
            LOGGER.error("Invalid dest dimension {} to teleport entity {} to", toDimension.getValue(), entity);
            return entity;
         } else {
            entity.detach();
            if (recreateEntity) {
               Entity newEntity = entity.getType().create(toWorld, SpawnReason.DIMENSION_TRAVEL);
               if (newEntity == null) {
                  return entity;
               } else {
                  newEntity.copyFrom(entity);
                  newEntity.setId(entity.getId());
                  McHelper.setEyePos(newEntity, newEyePos, newEyePos);
                  McHelper.updateBoundingBox(newEntity);
                  newEntity.setHeadYaw(entity.getHeadYaw());
                  entity.remove(RemovalReason.CHANGED_DIMENSION);
                  toWorld.onDimensionChanged(newEntity);
                  return newEntity;
               }
            } else {
               entity.remove(RemovalReason.CHANGED_DIMENSION);
               ((IEEntity)entity).ip_unsetRemoved();
               McHelper.setEyePos(entity, newEyePos, newEyePos);
               McHelper.updateBoundingBox(entity);
               ((IEEntity)entity).ip_setWorld(toWorld);
               toWorld.onDimensionChanged(entity);
               Validate.isTrue(!entity.isRemoved());
               return entity;
            }
         }
      }
   }

   public Entity teleportVehicleAcrossDimensions(Entity entity, RegistryKey<World> toDimension, Vec3d newEyePos) {
      this.teleportingEntities.add(entity);
      ServerWorld fromWorld = (ServerWorld)entity.getEntityWorld();
      ServerWorld toWorld = MiscHelper.getServer().getWorld(toDimension);
      Entity newEntity = entity.getType().create(toWorld, SpawnReason.DIMENSION_TRAVEL);
      Validate.isTrue(newEntity != null);
      newEntity.copyFrom(entity);
      newEntity.setId(entity.getId());
      McHelper.setEyePos(newEntity, newEyePos, newEyePos);
      McHelper.updateBoundingBox(newEntity);
      newEntity.setHeadYaw(entity.getHeadYaw());
      entity.remove(RemovalReason.CHANGED_DIMENSION);
      ((IEEntity)entity).ip_unsetRemoved();
      toWorld.onDimensionChanged(newEntity);
      return newEntity;
   }

   private boolean doesEntityClusterContainPlayer(Entity entity) {
      if (entity instanceof PlayerEntity) {
         return true;
      } else {
         List<Entity> passengerList = entity.getPassengerList();
         return passengerList.isEmpty() ? false : passengerList.stream().anyMatch(this::doesEntityClusterContainPlayer);
      }
   }

   public static boolean isFreshEntity(Entity entity) {
      return entity.lastX == 0.0 && entity.lastY == 0.0 && entity.lastZ == 0.0;
   }

   public boolean isJustTeleported(Entity entity, long valveTickTime) {
      long currGameTime = McHelper.getServerGameTime();
      Long lastTeleportGameTime = this.lastTeleportGameTime.getOrDefault(entity, -100000L);
      return currGameTime - lastTeleportGameTime < valveTickTime;
   }

   public static Entity teleportEntityGeneral(Entity entity, Vec3d targetPos, ServerWorld targetWorld) {
      if (entity instanceof ServerPlayerEntity serverPlayer) {
         of(serverPlayer.server).forceTeleportPlayer(serverPlayer, targetWorld.getRegistryKey(), targetPos);
         return entity;
      } else {
         return teleportRegularEntityTo(entity, targetWorld.getRegistryKey(), targetPos);
      }
   }

   public static <E extends Entity> E teleportRegularEntityTo(E entity, RegistryKey<World> targetDim, Vec3d targetPos) {
      if (entity.getEntityWorld().getRegistryKey() == targetDim) {
         entity.refreshPositionAndAngles(targetPos.x, targetPos.y, targetPos.z, entity.getYaw(), entity.getPitch());
         entity.setHeadYaw(entity.getYaw());
         return entity;
      } else {
         return (E)of(entity.getEntityWorld().getServer()).changeEntityDimension(entity, targetDim, targetPos.add(McHelper.getEyeOffset(entity)), true);
      }
   }

   private static void notifyChasersForPlayer(ServerPlayerEntity player, Portal portal) {
      for (MobEntity chaser : McHelper.findEntitiesRough(MobEntity.class, player.getEntityWorld(), player.getEntityPos(), 1, e -> e.getTarget() == player)) {
         chaser.setTarget(null);
         notifyChaser(player, portal, chaser);
      }
   }

   private static void notifyChaser(ServerPlayerEntity player, Portal portal, MobEntity chaser) {
      Vec3d targetPos = player.getEntityPos().add(portal.getNormal().multiply(-0.1));
      UUID chaserId = chaser.getUuid();
      ServerWorld destWorld = (ServerWorld)portal.getDestinationWorld();
      ServerTaskList.of(player.server).addTask(MyTaskList.withRetryNumberLimit(140, () -> {
         if (chaser.isRemoved()) {
            Entity newChaser = destWorld.getEntity(chaserId);
            if (newChaser instanceof MobEntity) {
               ((MobEntity)newChaser).setTarget(player);
               return true;
            } else {
               return false;
            }
         } else {
            if (chaser.getEntityPos().distanceTo(targetPos) < 2.0) {
               chaser.getMoveControl().moveTo(targetPos.x, targetPos.y, targetPos.z, 1.0);
            } else {
               Path path = chaser.getNavigation().findPathTo(BlockPos.ofFloored(targetPos), 0);
               chaser.getNavigation().startMovingAlong(path, 1.0);
            }

            return false;
         }
      }, () -> {}));
   }

   private void evacuatePlayersFromDimension(ServerWorld world) {
      for (ServerPlayerEntity player : new ArrayList(MiscHelper.getServer().getPlayerManager().getPlayerList())) {
         if (player.getEntityWorld().getRegistryKey() == world.getRegistryKey()) {
            ServerWorld overWorld = McHelper.getOverWorldOnServer();
            BlockPos spawnPos = overWorld.getSpawnPoint().getPos();
            this.forceTeleportPlayer(player, World.OVERWORLD, Vec3d.ofCenter(spawnPos));
            player.sendMessage(Text.literal("Teleported to spawn pos because dimension %s had been removed".formatted(world.getRegistryKey().getValue())));
         }
      }
   }
}
