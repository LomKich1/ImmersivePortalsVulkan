package qouteall.imm_ptl.core.teleportation;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.Entity.RemovalReason;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.profiler.Profilers;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.IPCGlobal;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.IPMcHelper;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.ScaleUtils;
import qouteall.imm_ptl.core.api.PortalAPI;
import qouteall.imm_ptl.core.collision.CollisionHelper;
import qouteall.imm_ptl.core.collision.PortalCollisionHandler;
import qouteall.imm_ptl.core.compat.GravityChangerInterface;
import qouteall.imm_ptl.core.compat.voxy_compatibility.VoxyInterface;
import qouteall.imm_ptl.core.ducks.IEClientPlayNetworkHandler;
import qouteall.imm_ptl.core.ducks.IEEntity;
import qouteall.imm_ptl.core.ducks.IEGameRenderer;
import qouteall.imm_ptl.core.ducks.IEMinecraftClient;
import qouteall.imm_ptl.core.ducks.IEParticleManager;
import qouteall.imm_ptl.core.network.ImmPtlNetworking;
import qouteall.imm_ptl.core.network.PacketRedirectionClient;
import qouteall.imm_ptl.core.platform_specific.O_O;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.PortalExtension;
import qouteall.imm_ptl.core.portal.animation.ClientPortalAnimationManagement;
import qouteall.imm_ptl.core.portal.animation.StableClientTimer;
import qouteall.imm_ptl.core.render.FrontClipping;
import qouteall.imm_ptl.core.render.MyGameRenderer;
import qouteall.imm_ptl.core.render.TransformationManager;
import qouteall.imm_ptl.core.render.context_management.FogRendererContext;
import qouteall.imm_ptl.core.render.context_management.RenderStates;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.my_util.Plane;
import qouteall.q_misc_util.my_util.Vec2d;
import qouteall.q_misc_util.platform.PlatformNetworking;

@Environment(EnvType.CLIENT)
public class ClientTeleportationManager {
   private static final Logger LOGGER = LogUtils.getLogger();
   public static final MinecraftClient client = MinecraftClient.getInstance();
   public static long tickTimeForTeleportation = 0L;
   private static long lastTeleportGameTime = 0L;
   private static long teleportTickTimeLimit = 0L;
   private static Vec3d lastPlayerEyePos = null;
   private static long lastRecordStableTickTime = 0L;
   private static float lastRecordStablePartialTicks = 0.0F;
   public static boolean isTeleportingTick = false;
   public static boolean isTeleportingFrame = false;
   public static boolean isTicking = false;
   private static final int teleportLimitPerFrame = 3;
   private static long teleportationCounter = 0L;

   public static void init() {
      IPGlobal.POST_CLIENT_TICK_EVENT.register(ClientTeleportationManager::tick);
      IPCGlobal.CLIENT_CLEANUP_EVENT.register(() -> lastPlayerEyePos = null);
   }

   private static void tick() {
      tickTimeForTeleportation++;
      changePlayerMotionIfCollidingWithPortal();
      isTeleportingTick = false;
   }

   public static void acceptSynchronizationDataFromServer(RegistryKey<World> dimension, Vec3d pos, boolean forceAccept) {
      if (!forceAccept) {
         if (isTeleportingFrequently()) {
            return;
         }

         if (client.player.age < 200) {
            return;
         }
      }

      if (client.player.getEntityWorld().getRegistryKey() != dimension) {
         forceTeleportPlayer(dimension, pos);
      }
   }

   public static void manageTeleportation(boolean isTicking_) {
      if (!IPGlobal.disableTeleportation) {
         isTicking = isTicking_;
         teleportationCounter++;
         isTeleportingFrame = false;
         if (client.world == null || client.player == null) {
            lastPlayerEyePos = null;
         } else if (client.player.lastX != 0.0 || client.player.lastY != 0.0 || client.player.lastZ != 0.0) {
            Profilers.get().push("ip_teleport");
            ClientPortalAnimationManagement.foreachCustomAnimatedPortals(
               portal -> PortalExtension.forClusterPortals(portal, p -> p.animation.updateClientState(p, teleportationCounter))
            );
            float realPartialTicks = RenderStates.getPartialTick();
            TeleportationUtil.Teleportation lastTeleportation = null;
            RegistryKey<World> originalDim = client.player.getEntityWorld().getRegistryKey();
            if (lastPlayerEyePos != null) {
               for (int i = 0; i <= 3; i++) {
                  TeleportationUtil.Teleportation teleportation = tryTeleport(realPartialTicks);
                  if (teleportation == null) {
                     break;
                  }

                  lastTeleportation = teleportation;
                  if (i != 0) {
                     LOGGER.info("The client player made a combo-teleport");
                     if (i == 3) {
                        LOGGER.info("Combo teleport out of limit. Reject teleportation!");
                        Vec3d oldPos = lastPlayerEyePos.subtract(McHelper.getEyeOffset(client.player));
                        forceTeleportPlayer(originalDim, oldPos.add(teleportation.worldSurfaceNormal().multiply(-0.001)));
                        client.player.setVelocity(teleportation.worldSurfaceNormal().multiply(-0.1));
                        lastTeleportation = null;
                        break;
                     }
                  }
               }
            }

            if (lastTeleportation != null && PortalExtension.get(lastTeleportation.portal()).adjustPositionAfterTeleport) {
               adjustPlayerPosition(client.player);
            }

            lastPlayerEyePos = getPlayerEyePos(realPartialTicks);
            lastRecordStableTickTime = StableClientTimer.getStableTickTime();
            lastRecordStablePartialTicks = StableClientTimer.getStablePartialTicks();
            Profilers.get().pop();
         }
      }
   }

   @Nullable
   private static TeleportationUtil.Teleportation tryTeleport(float partialTicks) {
      ClientPlayerEntity player = client.player;

      assert player != null;

      Vec3d thisFrameEyePos = getPlayerEyePos(partialTicks);
      if (lastPlayerEyePos.squaredDistanceTo(thisFrameEyePos) > 1600.0) {
         return null;
      } else {
         assert client.world != null;

         long currentGameTime = client.world.getTime();
         Vec3d lastTickEyePos = McHelper.getLastTickEyePos(player);
         Vec3d thisTickEyePos = McHelper.getEyePos(player);
         ArrayList<TeleportationUtil.Teleportation> teleportationCandidates = new ArrayList<>();
         IPMcHelper.traverseNearbyPortals(
            player.getEntityWorld(),
            thisFrameEyePos,
            IPGlobal.maxNormalPortalRadius + 1,
            portalx -> {
               if (portalx.canTeleportEntity(player)) {
                  if (portalx.animation.clientLastFramePortalStateCounter == teleportationCounter - 1L
                     && portalx.animation.clientLastFramePortalState != null
                     && portalx.animation.lastTickAnimatedState != null
                     && portalx.animation.thisTickAnimatedState != null) {
                     assert portalx.animation.clientCurrentFramePortalState != null;

                     TeleportationUtil.Teleportation teleportationx = TeleportationUtil.checkDynamicTeleportation(
                        portalx,
                        portalx.animation.clientLastFramePortalState,
                        portalx.animation.clientCurrentFramePortalState,
                        lastPlayerEyePos,
                        thisFrameEyePos,
                        portalx.animation.lastTickAnimatedState,
                        portalx.animation.thisTickAnimatedState,
                        lastTickEyePos,
                        thisTickEyePos,
                        partialTicks
                     );
                     if (teleportationx != null) {
                        teleportationCandidates.add(teleportationx);
                     }
                  } else {
                     TeleportationUtil.Teleportation teleportationxx = TeleportationUtil.checkStaticTeleportation(
                        portalx, lastPlayerEyePos, thisFrameEyePos, lastTickEyePos, thisTickEyePos
                     );
                     if (teleportationxx != null) {
                        teleportationCandidates.add(teleportationxx);
                     }
                  }
               }
            }
         );
         TeleportationUtil.Teleportation teleportation = teleportationCandidates.stream()
            .min(Comparator.comparingDouble(p -> p.worldCollisionPoint().squaredDistanceTo(lastPlayerEyePos)))
            .orElse(null);
         if (teleportation != null) {
            Portal portal = teleportation.portal();
            Vec3d collidingPos = teleportation.worldCollisionPoint();
            Profilers.get().push("portal_teleport");
            teleportPlayer(teleportation, partialTicks);
            Profilers.get().pop();
            boolean allowOverlappedTeleport = portal.respectParallelOrientedPortal();
            double adjustment = allowOverlappedTeleport ? -0.001 : 0.001;
            Vec3d newDelta = teleportation.newThisTickEyePos().subtract(teleportation.newLastTickEyePos());
            lastPlayerEyePos = teleportation.teleportationCheckpoint().add(newDelta.multiply(adjustment));
            return teleportation;
         } else {
            return null;
         }
      }
   }

   public static Vec3d getPlayerEyePos(float partialTick) {
      return client.player.getCameraPosVec(partialTick);
   }

   private static void teleportPlayer(TeleportationUtil.Teleportation teleportation, float partialTicks) {
      Portal portal = teleportation.portal();
      if (tickTimeForTeleportation <= teleportTickTimeLimit) {
         Helper.log("Client player teleportation rejected");
      } else {
         lastTeleportGameTime = tickTimeForTeleportation;
         ClientPlayerEntity player = client.player;
         Validate.isTrue(player != null);
         RegistryKey<World> toDimension = portal.getDestDim();
         float partialTick = RenderStates.getPartialTick();
         Entity vehicle = player.getVehicle();
         Vec3d oldVehiclePos = vehicle != null ? vehicle.getEntityPos() : null;
         Vec3d thisTickEyePos = McHelper.getEyePos(player);
         Vec3d lastTickEyePos = McHelper.getLastTickEyePos(player);
         Vec3d newThisTickEyePos = teleportation.newThisTickEyePos();
         Vec3d newLastTickEyePos = teleportation.newLastTickEyePos();
         ClientWorld fromWorld = client.world;
         RegistryKey<World> fromDimension = fromWorld.getRegistryKey();
         if (fromDimension != toDimension) {
            ClientWorld toWorld = ClientWorldLoader.getWorld(toDimension);
            changePlayerDimension(player, fromWorld, toWorld, newThisTickEyePos);
         }

         McHelper.setEyePos(player, newThisTickEyePos, newLastTickEyePos);
         McHelper.updateBoundingBox(player);
         Vec3d oldRealVelocity = McHelper.getWorldVelocity(player);
         TransformationManager.managePlayerRotationAndChangeGravity(portal);
         McHelper.setWorldVelocity(player, oldRealVelocity);
         TeleportationUtil.PortalPointVelocity portalPointVelocity = teleportation.portalPointVelocity();
         TeleportationUtil.transformEntityVelocity(portal, player, portalPointVelocity, thisTickEyePos);
         if (vehicle != null) {
            TeleportationUtil.transformEntityVelocity(portal, vehicle, portalPointVelocity, oldVehiclePos);
         }

         ScaleUtils.onClientPlayerTeleported(portal);
         player.networkHandler
            .sendPacket(
               PlatformNetworking.get()
                  .createC2SPacket(new ImmPtlNetworking.TeleportPacket(PortalAPI.clientDimKeyToInt(fromDimension), thisTickEyePos, portal.getUuid()))
            );
         PortalCollisionHandler.updateCollidingPortalAfterTeleportation(player, newThisTickEyePos, newLastTickEyePos, RenderStates.getPartialTick());
         McHelper.adjustVehicle(player);
         RenderStates.updatePreRenderInfo(partialTick);
         if (teleportation.isDynamic()) {
            LOGGER.info(
               "Client Teleported Dynamically\nportal: {}\ntickTime: {}\nduring ticking: {}\ncounter: {}\neye pos (by frame): {} -> {}\npartial ticks: {}\nnew immediate eye pos: {}\nportal origin/normal: {} {}\nportal dest/content dir: {} {}",
               new Object[]{
                  portal,
                  tickTimeForTeleportation,
                  isTicking,
                  teleportationCounter,
                  teleportation.lastWorldEyePos(),
                  teleportation.currentWorldEyePos(),
                  partialTicks,
                  teleportation.newLastTickEyePos().lerp(teleportation.newThisTickEyePos(), partialTick),
                  portal.getOriginPos(),
                  portal.getNormal(),
                  portal.getDestPos(),
                  portal.getContentDirection()
               }
            );
         } else {
            LOGGER.info(
               "Client Teleported Statically\nportal: {}\neye pos: {} -> {}",
               new Object[]{portal, teleportation.lastWorldEyePos(), teleportation.currentWorldEyePos()}
            );
         }

         isTeleportingTick = true;
         isTeleportingFrame = true;
         MyGameRenderer.vanillaTerrainSetupOverride = 1;
      }
   }

   public static boolean isTeleportingFrequently() {
      return tickTimeForTeleportation - lastTeleportGameTime <= 100L || tickTimeForTeleportation <= teleportTickTimeLimit;
   }

   public static void forceTeleportPlayer(RegistryKey<World> toDimension, Vec3d destination) {
      LOGGER.info("client player force teleported {} {}", toDimension.getValue(), destination);
      ClientWorld fromWorld = client.world;

      assert fromWorld != null;

      RegistryKey<World> fromDimension = fromWorld.getRegistryKey();
      ClientPlayerEntity player = client.player;

      assert player != null;

      if (fromDimension != toDimension) {
         ClientWorld toWorld = ClientWorldLoader.getWorld(toDimension);
         Vec3d eyeOffset = McHelper.getEyeOffset(player);
         changePlayerDimension(player, fromWorld, toWorld, destination.add(eyeOffset));
      }

      player.setPosition(destination.x, destination.y, destination.z);
      McHelper.adjustVehicle(player);
      lastPlayerEyePos = null;
      RenderStates.updatePreRenderInfo(RenderStates.getPartialTick());
      MyGameRenderer.vanillaTerrainSetupOverride = 1;
   }

   public static void changePlayerDimension(ClientPlayerEntity player, ClientWorld fromWorld, ClientWorld toWorld, Vec3d newEyePos) {
      Validate.isTrue(!WorldRenderInfo.isRendering());
      Validate.isTrue(!FrontClipping.isClippingEnabled);
      Validate.isTrue(!PacketRedirectionClient.getIsProcessingRedirectedMessage());
      Entity vehicle = player.getVehicle();
      player.detach();
      RegistryKey<World> toDimension = toWorld.getRegistryKey();
      RegistryKey<World> fromDimension = fromWorld.getRegistryKey();
      ((IEClientPlayNetworkHandler)(Object)client.getNetworkHandler()).ip_setWorld(toWorld);
      fromWorld.removeEntity(player.getId(), RemovalReason.CHANGED_DIMENSION);
      ((IEEntity)(Object)player).ip_setWorld(toWorld);
      McHelper.setEyePos(player, newEyePos, newEyePos);
      McHelper.updateBoundingBox(player);
      ((IEEntity)(Object)player).ip_unsetRemoved();
      toWorld.addEntity(player);
      IEGameRenderer gameRenderer = (IEGameRenderer)(Object)MinecraftClient.getInstance().gameRenderer;
      gameRenderer.ip_setLightmapTextureManager(ClientWorldLoader.getDimensionRenderHelper(toDimension).lightmapTexture);
      client.world = toWorld;
      ((IEMinecraftClient)(Object)client).ip_setWorldRenderer(ClientWorldLoader.getWorldRenderer(toDimension));
      VoxyInterface.invoker
         .onPlayerDimensionChangePromoted(ClientWorldLoader.getWorldRenderer(toDimension), toWorld, ClientWorldLoader.getWorldRenderer(fromDimension));
      if (client.particleManager != null) {
         ((IEParticleManager)(Object)client.particleManager).ip_setWorld(toWorld);
      }

      if (vehicle != null) {
         Vec3d offset = McHelper.getVehicleOffsetFromPassenger(vehicle, player);
         Vec3d vehiclePos = player.getEntityPos().add(offset);
         moveClientEntityAcrossDimension(vehicle, toWorld, vehiclePos);
         McHelper.setPosAndLastTickPos(vehicle, player.getEntityPos().add(offset), McHelper.lastTickPosOf(player).add(offset));
         player.startRiding(vehicle, true, false);
      }

      Helper.log(
         String.format(
            "Client Changed Dimension from %s to %s time: %s age: %s", fromDimension.getValue(), toDimension.getValue(), tickTimeForTeleportation, player.age
         )
      );
      FogRendererContext.onPlayerTeleport(fromDimension, toDimension);
      O_O.onPlayerChangeDimensionClient(fromDimension, toDimension);
   }

   private static void changePlayerMotionIfCollidingWithPortal() {
      ClientPlayerEntity player = client.player;
      Portal portal = ((IEEntity)(Object)player).ip_getCollidingPortal();
      if (portal != null) {
         if (PortalExtension.get(portal).motionAffinity > 0.0) {
            changeMotion(player, portal);
         } else if (PortalExtension.get(portal).motionAffinity < 0.0 && player.getVelocity().length() > 0.7) {
            changeMotion(player, portal);
         }
      }
   }

   private static void changeMotion(Entity player, Portal portal) {
      Vec3d velocity = player.getVelocity();
      player.setVelocity(velocity.multiply(1.0 + PortalExtension.get(portal).motionAffinity));
   }

   public static void moveClientEntityAcrossDimension(Entity entity, ClientWorld newWorld, Vec3d newPos) {
      ClientWorld oldWorld = (ClientWorld)entity.getEntityWorld();
      oldWorld.removeEntity(entity.getId(), RemovalReason.CHANGED_DIMENSION);
      ((IEEntity)(Object)entity).ip_setWorld(newWorld);
      entity.setPosition(newPos.x, newPos.y, newPos.z);
      ((IEEntity)(Object)entity).ip_unsetRemoved();
      newWorld.addEntity(entity);
      Validate.isTrue(!entity.isRemoved());
   }

   public static void disableTeleportFor(int ticks) {
      teleportTickTimeLimit = tickTimeForTeleportation + ticks;
   }

   private static void adjustPlayerPosition(ClientPlayerEntity player) {
      if (!player.isSpectator()) {
         if (player.getVehicle() == null) {
            Box playerBoundingBox = player.getBoundingBox();
            PortalCollisionHandler portalCollisionHandler = ((IEEntity)(Object)player).ip_getPortalCollisionHandler();
            List<Portal> collidingPortals = portalCollisionHandler == null ? Collections.emptyList() : portalCollisionHandler.getCollidingPortals();
            Direction gravityDir = GravityChangerInterface.invoker.getGravityDirection(player);
            Direction levitationDir = gravityDir.getOpposite();
            Vec3d eyeOffset = GravityChangerInterface.invoker.getEyeOffset(player);
            Box bottomHalfBox = playerBoundingBox.shrink(eyeOffset.x / 2.0, eyeOffset.y / 2.0, eyeOffset.z / 2.0);
            Function<VoxelShape, VoxelShape> shapeFilter = c -> {
               VoxelShape curr = c;

               for (Portal collidingPortal : collidingPortals) {
                  Plane outerClipping = collidingPortal.getPortalShape().getOuterClipping(collidingPortal.getThisSideState());
                  if (outerClipping != null) {
                     curr = CollisionHelper.clipVoxelShape(curr, outerClipping.pos(), outerClipping.normal());
                     if (curr == null) {
                        return null;
                     }
                  }
               }

               return curr;
            };
            Box collisionUnion = CollisionHelper.getTotalBlockCollisionBox(player, bottomHalfBox, shapeFilter);
            if (collisionUnion != null) {
               Vec3d anchor = player.getEntityPos();
               Box collisionUnionLocal = Helper.transformBox(
                  collisionUnion, v -> GravityChangerInterface.invoker.transformWorldToPlayer(gravityDir, v.subtract(anchor))
               );
               Box playerBoxLocal = Helper.transformBox(
                  playerBoundingBox, v -> GravityChangerInterface.invoker.transformWorldToPlayer(gravityDir, v.subtract(anchor))
               );
               double targetLocalY = collisionUnionLocal.maxY + 0.01;
               double originalLocalY = playerBoxLocal.minY;
               double delta = targetLocalY - originalLocalY;
               if (!(delta <= 0.0)) {
                  Vec3d levitationVec = Vec3d.of(levitationDir.getVector());
                  Vec3d offset = levitationVec.multiply(delta);
                  int ticks = 5;
                  Helper.log("Adjusting Client Player Position");
                  int[] counter = new int[]{0};
                  IPGlobal.CLIENT_TASK_LIST
                     .addTask(
                        () -> {
                           if (player.isRemoved()) {
                              return true;
                           } else if (GravityChangerInterface.invoker.getGravityDirection(player) != gravityDir) {
                              return true;
                           } else if (counter[0] >= 5) {
                              return true;
                           } else {
                              counter[0]++;
                              double len = player.getEntityPos().subtract(anchor).dotProduct(levitationVec);
                              if (!(len < -1.0) && !(len > 2.0)) {
                                 double progress = counter[0] / 5.0;
                                 progress = TransformationManager.mapProgress(progress);
                                 Vec3d expectedPos = anchor.add(offset.multiply(progress));
                                 Vec3d newPos = Helper.putCoordinate(
                                    player.getEntityPos(), levitationDir.getAxis(), Helper.getCoordinate(expectedPos, levitationDir.getAxis())
                                 );
                                 Portal currentCollidingPortal = ((IEEntity)(Object)player).ip_getCollidingPortal();
                                 if (currentCollidingPortal != null) {
                                    Vec3d eyePos = McHelper.getEyePos(player);
                                    Vec3d newEyePos = newPos.add(McHelper.getEyeOffset(player));
                                    if (currentCollidingPortal.rayTrace(eyePos, newEyePos) != null) {
                                       return true;
                                    }
                                 }

                                 player.setPos(newPos.x, newPos.y, newPos.z);
                                 McHelper.updateBoundingBox(player);
                                 return false;
                              } else {
                                 return true;
                              }
                           }
                        }
                     );
               }
            }
         }
      }
   }

   public static class RemoteCallables {
      public static void updateEntityPos(RegistryKey<World> dim, int entityId, Vec3d pos) {
         ClientWorld world = ClientWorldLoader.getWorld(dim);
         Entity entity = world.getEntityById(entityId);
         if (entity == null) {
            Helper.err("cannot find entity to update position");
         } else {
            entity.setPosition(pos);
            entity.updateTrackedPositionAndAngles(pos, entity.getYaw(), entity.getPitch());
            entity.setPosition(pos);
         }
      }
   }

   private record TeleportationRec(Portal portal, Vec2d portalLocalXY, Vec3d collisionPos) {
   }
}
