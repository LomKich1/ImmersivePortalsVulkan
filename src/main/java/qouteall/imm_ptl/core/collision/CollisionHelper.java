package qouteall.imm_ptl.core.collision;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Direction.Axis;
import net.minecraft.util.profiler.Profilers;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.World;
import net.minecraft.world.border.WorldBorder;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.ScaleUtils;
import qouteall.imm_ptl.core.ducks.IEEntity;
import qouteall.imm_ptl.core.miscellaneous.IPVanillaCopy;
import qouteall.imm_ptl.core.mixin.common.collision.IEEntity_Collision;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.global_portals.GlobalPortalStorage;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.MiscHelper;
import qouteall.q_misc_util.my_util.LimitedLogger;
import qouteall.q_misc_util.platform.Platform;

public class CollisionHelper {
   private static final LimitedLogger limitedLogger = new LimitedLogger(20);
   private static boolean thisTickStagnate = false;
   private static boolean lastTickStagnate = false;

   @Nullable
   public static Box clipBox(Box box, Vec3d planePos, Vec3d planeNormal) {
      boolean xForward = planeNormal.x > 0.0;
      boolean yForward = planeNormal.y > 0.0;
      boolean zForward = planeNormal.z > 0.0;
      Vec3d pushedPos = new Vec3d(xForward ? box.minX : box.maxX, yForward ? box.minY : box.maxY, zForward ? box.minZ : box.maxZ);
      Vec3d staticPos = new Vec3d(xForward ? box.maxX : box.minX, yForward ? box.maxY : box.minY, zForward ? box.maxZ : box.minZ);
      double tOfPushedPos = Helper.getCollidingT(planePos, planeNormal, pushedPos, planeNormal);
      boolean isPushedPosInFrontOfPlane = tOfPushedPos < 0.0;
      if (isPushedPosInFrontOfPlane) {
         return box;
      } else {
         boolean isStaticPosInFrontOfPlane = Helper.isInFrontOfPlane(staticPos, planePos, planeNormal);
         if (!isStaticPosInFrontOfPlane) {
            return null;
         } else {
            Vec3d afterBeingPushed = pushedPos.add(planeNormal.multiply(tOfPushedPos));
            return new Box(afterBeingPushed, staticPos);
         }
      }
   }

   public static boolean isBoxFullyBehindPlane(Vec3d planePos, Vec3d planeNormal, Box box) {
      boolean xForward = planeNormal.x > 0.0;
      boolean yForward = planeNormal.y > 0.0;
      boolean zForward = planeNormal.z > 0.0;
      Vec3d testingPos = new Vec3d(xForward ? box.maxX : box.minX, yForward ? box.maxY : box.minY, zForward ? box.maxZ : box.minZ);
      return testingPos.subtract(planePos).dotProduct(planeNormal) < 0.0;
   }

   public static boolean canCollideWithPortal(Entity entity, Portal portal, float partialTick) {
      return mayEntityCollideWithPortal(entity, portal, entity.getCameraPosVec(partialTick), entity.getBoundingBox());
   }

   public static boolean mayEntityCollideWithPortal(Entity entity, Portal portal, Vec3d entityEyePos, Box entityBoundingBox) {
      return !portal.canCollideWithEntity(entity)
         ? false
         : portal.getPortalShape().canCollideWith(portal, portal.getThisSideState(), entityEyePos, entityBoundingBox);
   }

   public static double absMin(double a, double b) {
      return Math.abs(a) < Math.abs(b) ? a : b;
   }

   public static double fixCoordinateFloatingPointError(double attemptedMove, double result) {
      if (Math.abs(attemptedMove - result) < 0.001) {
         return attemptedMove;
      } else {
         return Math.abs(result) < 1.0E-4 ? 0.0 : result;
      }
   }

   @Nullable
   public static VoxelShape clipVoxelShape(VoxelShape shape, Vec3d clippingPlanePos, Vec3d clippingPlaneNormal) {
      if (shape.isEmpty()) {
         return null;
      } else {
         Box shapeBoundingBox = shape.getBoundingBox();
         boolean boxBehindPlane = isBoxFullyBehindPlane(clippingPlanePos, clippingPlaneNormal, shapeBoundingBox);
         if (boxBehindPlane) {
            return null;
         } else {
            boolean isFullyInFrontOfPlane = isBoxFullyBehindPlane(clippingPlanePos, clippingPlaneNormal.multiply(-1.0), shapeBoundingBox);
            if (isFullyInFrontOfPlane) {
               return shape;
            } else {
               Box clippedBoundingBox = clipBox(shapeBoundingBox, clippingPlanePos, clippingPlaneNormal);
               return clippedBoundingBox == null ? null : VoxelShapes.combine(shape, VoxelShapes.cuboid(clippedBoundingBox), BooleanBiFunction.AND);
            }
         }
      }
   }

   private static Vec3d refHandleCollisionWithShapeProcessor(Entity entity, Vec3d attemptedMove, Function<VoxelShape, VoxelShape> filter) {
      Box boundingBox = entity.getBoundingBox();
      List<VoxelShape> entityCollisions = entity.getEntityWorld().getEntityCollisions(entity, boundingBox.stretch(attemptedMove));
      BiFunction<Vec3d, Box, Vec3d> collisionFunc = (attempt, bb) -> collideBoundingBox(entity, attempt, bb, entity.getEntityWorld(), entityCollisions, filter);
      Vec3d collidedMovement = attemptedMove.lengthSquared() == 0.0 ? attemptedMove : collisionFunc.apply(attemptedMove, boundingBox);
      boolean collideX = attemptedMove.x != collidedMovement.x;
      boolean collideY = attemptedMove.y != collidedMovement.y;
      boolean collideZ = attemptedMove.z != collidedMovement.z;
      boolean collidesWithFloor = collideY && attemptedMove.y < 0.0;
      boolean touchGround = entity.isOnGround() || collidesWithFloor;
      boolean collidesHorizontally = collideX || collideZ;
      float maxUpStep = entity.getStepHeight();
      if (maxUpStep > 0.0F && touchGround && collidesHorizontally) {
         Vec3d stepping = collisionFunc.apply(new Vec3d(attemptedMove.x, maxUpStep, attemptedMove.z), boundingBox);
         Vec3d verticalStep = collisionFunc.apply(new Vec3d(0.0, maxUpStep, 0.0), boundingBox.stretch(attemptedMove.x, 0.0, attemptedMove.z));
         if (verticalStep.y < maxUpStep) {
            Vec3d horizontalMoveAfterVerticalStepping = collisionFunc.apply(new Vec3d(attemptedMove.x, 0.0, attemptedMove.z), boundingBox.offset(verticalStep))
               .add(verticalStep);
            if (horizontalMoveAfterVerticalStepping.horizontalLengthSquared() > stepping.horizontalLengthSquared()) {
               stepping = horizontalMoveAfterVerticalStepping;
            }
         }

         if (stepping.horizontalLengthSquared() > collidedMovement.horizontalLengthSquared()) {
            Vec3d movingDown = collisionFunc.apply(new Vec3d(0.0, -stepping.y + attemptedMove.y, 0.0), boundingBox.offset(stepping));
            return stepping.add(movingDown);
         }
      }

      return collidedMovement;
   }

   @IPVanillaCopy
   public static Vec3d handleCollisionWithShapeProcessor(
      Entity entity, Box boundingBox, World world, Vec3d attemptedMove, Function<VoxelShape, VoxelShape> filter, Direction gravity, double steppingScale
   ) {
      Direction jumpDirection = gravity.getOpposite();
      Axis gravityAxis = gravity.getAxis();
      List<VoxelShape> entityCollisions = world.getEntityCollisions(entity, boundingBox.stretch(attemptedMove));
      BiFunction<Vec3d, Box, Vec3d> collisionFunc = (attempt, bb) -> collideBoundingBox(entity, attempt, bb, world, entityCollisions, filter);
      Vec3d collidedMovement = attemptedMove.lengthSquared() == 0.0 ? attemptedMove : collisionFunc.apply(attemptedMove, boundingBox);
      Vec3d collisionDelta = attemptedMove.subtract(collidedMovement);
      boolean collidesOnGravityAxis = Helper.getCoordinate(collisionDelta, gravityAxis) != 0.0;
      boolean attemptToMoveAlongGravity = Helper.getSignedCoordinate(attemptedMove, gravity) > 0.0;
      boolean collidesWithFloor = collidesOnGravityAxis && attemptToMoveAlongGravity;
      boolean touchGround = entity.isOnGround() || collidesWithFloor;
      boolean collidesHorizontally = movesOnNonGravityAxis(collisionDelta, gravityAxis);
      double maxUpStep = entity.getStepHeight();
      if (steppingScale > 1.0) {
         maxUpStep *= steppingScale;
      }

      if (maxUpStep > 0.0 && touchGround && collidesHorizontally) {
         Vec3d stepping = collisionFunc.apply(Helper.putSignedCoordinate(attemptedMove, jumpDirection, maxUpStep), boundingBox);
         Vec3d expandVec = Helper.putSignedCoordinate(attemptedMove, gravity, 0.0);
         Vec3d verticalStep = collisionFunc.apply(Helper.putSignedCoordinate(Vec3d.ZERO, jumpDirection, maxUpStep), boundingBox.stretch(expandVec));
         if (Helper.getSignedCoordinate(verticalStep, jumpDirection) < maxUpStep + 0.001) {
            Vec3d horizontalMoveAfterVerticalStepping = collisionFunc.apply(expandVec, boundingBox.offset(verticalStep)).add(verticalStep);
            if (Helper.getDistanceSqrOnAxisPlane(horizontalMoveAfterVerticalStepping, gravityAxis) > Helper.getDistanceSqrOnAxisPlane(stepping, gravityAxis)) {
               stepping = horizontalMoveAfterVerticalStepping;
            }
         }

         if (Helper.getDistanceSqrOnAxisPlane(stepping, gravityAxis) > Helper.getDistanceSqrOnAxisPlane(collidedMovement, gravityAxis)) {
            double steppingVerticalLen = Helper.getSignedCoordinate(stepping, jumpDirection);
            double attemptMoveVerticalLen = Helper.getSignedCoordinate(attemptedMove, jumpDirection);
            Vec3d movingDown = collisionFunc.apply(
               Helper.putSignedCoordinate(Vec3d.ZERO, jumpDirection, -steppingVerticalLen + attemptMoveVerticalLen), boundingBox.offset(stepping)
            );
            return stepping.add(movingDown);
         }
      }

      return collidedMovement;
   }

   public static boolean movesOnNonGravityAxis(Vec3d vec, Axis gravityAxis) {
      return switch (gravityAxis) {
         case X -> vec.y != 0.0 || vec.z != 0.0;
         case Y -> vec.x != 0.0 || vec.z != 0.0;
         case Z -> vec.x != 0.0 || vec.y != 0.0;
         default -> throw new MatchException(null, null);
      };
   }

   @IPVanillaCopy
   public static Vec3d collideBoundingBox(
      Entity entity, Vec3d vec, Box collisionBox, World level, List<VoxelShape> potentialHits, Function<VoxelShape, VoxelShape> shapeProcessor
   ) {
      Builder<VoxelShape> builder = ImmutableList.builderWithExpectedSize(potentialHits.size() + 1);

      for (VoxelShape potentialHit : potentialHits) {
         VoxelShape processed = shapeProcessor.apply(potentialHit);
         if (processed != null) {
            builder.add(processed);
         }
      }

      WorldBorder worldBorder = level.getWorldBorder();
      Vec3d boundingBoxCenter = collisionBox.getCenter();
      boolean addWorldBorderCollision = worldBorder.contains(boundingBoxCenter.x, boundingBoxCenter.z)
         && worldBorder.getDistanceInsideBorder(boundingBoxCenter.x, boundingBoxCenter.z) < 32.0;
      if (addWorldBorderCollision) {
         builder.add(worldBorder.asVoxelShape());
      }

      for (VoxelShape blockCollision : level.getBlockCollisions(entity, collisionBox.stretch(vec))) {
         VoxelShape processed = shapeProcessor.apply(blockCollision);
         if (processed != null) {
            builder.add(processed);
         }
      }

      return IEEntity_Collision.ip_CollideWithShapes(vec, collisionBox, builder.build());
   }

   public static Box transformBox(Portal portal, Box originalBox) {
      return portal.getRotation() == null && portal.getScale() == 1.0
         ? originalBox.offset(portal.getDestPos().subtract(portal.getOriginPos()))
         : Helper.transformBox(originalBox, portal::transformPoint);
   }

   @Deprecated
   public static World getWorld(boolean isClient, RegistryKey<World> dimension) {
      return (World)(isClient ? CHelper.getClientWorld(dimension) : MiscHelper.getServer().getWorld(dimension));
   }

   public static boolean isCollidingWithAnyPortal(Entity entity) {
      return ((IEEntity)(Object)entity).ip_getCollidingPortal() != null;
   }

   public static void updateCollidingPortalForWorld(World world, float partialTick) {
      Profilers.get().push("update_colliding_portal");
      List<Portal> globalPortals = GlobalPortalStorage.getGlobalPortals(world);

      for (Entity entity : McHelper.getWorldEntityList(world)) {
         if (entity instanceof Portal portal) {
            notifyCollidingPortals(portal, partialTick);
         } else {
            Box entityBoundingBoxStretched = getStretchedBoundingBox(entity);

            for (Portal globalPortal : globalPortals) {
               Box globalPortalBoundingBox = globalPortal.getBoundingBox();
               if (entityBoundingBoxStretched.intersects(globalPortalBoundingBox) && canCollideWithPortal(entity, globalPortal, partialTick)) {
                  ((IEEntity)(Object)entity).ip_notifyCollidingWithPortal(globalPortal);
               }
            }
         }
      }

      Profilers.get().pop();
   }

   public static void init() {
      Platform.get().onServerTickEnd(server -> {
         for (ServerWorld world : server.getWorlds()) {
            updateCollidingPortalForWorld(world, 0.0F);
         }
      });
   }

   @Environment(EnvType.CLIENT)
   public static void initClient() {
      IPGlobal.POST_CLIENT_TICK_EVENT.register(CollisionHelper::tickClient);
   }

   @Environment(EnvType.CLIENT)
   public static void tickClient() {
      updateClientCollidingStatus();
      updateClientStagnateStatus();
   }

   @Environment(EnvType.CLIENT)
   private static void updateClientCollidingStatus() {
      if (ClientWorldLoader.getIsInitialized()) {
         for (ClientWorld world : ClientWorldLoader.getClientWorlds()) {
            updateCollidingPortalForWorld(world, 0.0F);
         }
      }
   }

   public static void notifyCollidingPortals(Portal portal, float partialTick) {
      if (portal.isTeleportable()) {
         Box portalBoundingBox = portal.getBoundingBox();
         McHelper.foreachEntitiesByBoxApproximateRegions(Entity.class, portal.getEntityWorld(), portalBoundingBox, 8.0, entity -> {
            if (!(entity instanceof Portal)) {
               Box entityBoxStretched = getStretchedBoundingBox(entity);
               if (entityBoxStretched.intersects(portalBoundingBox)) {
                  boolean canCollideWithPortal = canCollideWithPortal(entity, portal, partialTick);
                  if (canCollideWithPortal) {
                     ((IEEntity)(Object)entity).ip_notifyCollidingWithPortal(portal);
                  }
               }
            }
         });
      }
   }

   public static Box getStretchedBoundingBox(Entity entity) {
      Vec3d backwardExpand = McHelper.lastTickPosOf(entity).subtract(entity.getEntityPos());
      Vec3d forwardExpand = McHelper.getWorldVelocity(entity);
      Box box = entity.getBoundingBox().stretch(forwardExpand.multiply(1.2)).stretch(backwardExpand);
      double scale = ScaleUtils.getScale(entity);
      if (scale > 4.0) {
         box = box.expand(scale);
      }

      return box;
   }

   @Environment(EnvType.CLIENT)
   public static void informClientStagnant() {
      thisTickStagnate = true;
      limitedLogger.log("client movement stagnated");
   }

   @Environment(EnvType.CLIENT)
   private static void updateClientStagnateStatus() {
      if (thisTickStagnate && lastTickStagnate) {
         MinecraftClient.getInstance().inGameHud.setOverlayMessage(Text.translatable("imm_ptl.stagnate_movement"), false);
      } else if (!thisTickStagnate && lastTickStagnate) {
         MinecraftClient.getInstance().inGameHud.setOverlayMessage(Text.literal(""), false);
      }

      lastTickStagnate = thisTickStagnate;
      thisTickStagnate = false;
   }

   @Nullable
   public static Box getTotalBlockCollisionBox(Entity entity, Box box, Function<VoxelShape, VoxelShape> shapeFilter) {
      Iterable<VoxelShape> collisions = entity.getEntityWorld().getBlockCollisions(entity, box);
      Box collisionUnion = null;

      for (VoxelShape c : collisions) {
         VoxelShape currCollision = shapeFilter.apply(c);
         if (currCollision != null && !currCollision.isEmpty()) {
            Box collisionBoundingBox = currCollision.getBoundingBox();
            if (collisionUnion == null) {
               collisionUnion = collisionBoundingBox;
            } else {
               collisionUnion = collisionUnion.union(collisionBoundingBox);
            }
         }
      }

      return collisionUnion;
   }
}
