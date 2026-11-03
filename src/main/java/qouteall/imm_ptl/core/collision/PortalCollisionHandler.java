package qouteall.imm_ptl.core.collision;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import net.minecraft.util.profiler.Profilers;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.util.shape.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.compat.GravityChangerInterface;
import qouteall.imm_ptl.core.ducks.IEEntity;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.my_util.Plane;
import qouteall.q_misc_util.my_util.Range;

public class PortalCollisionHandler {
   private static final int maxCollidingPortals = 6;
   public long lastActiveTime;
   public final List<PortalCollisionEntry> portalCollisions = new ArrayList<>();

   public boolean isRecentlyCollidingWithPortal(Entity entity) {
      return getTiming(entity) - this.lastActiveTime < 20L;
   }

   public void update(Entity entity) {
      this.portalCollisions
         .removeIf(
            p -> {
               if (p.portal.getEntityWorld() != entity.getEntityWorld()) {
                  return true;
               } else {
                  Box stretchedBoundingBox = CollisionHelper.getStretchedBoundingBox(entity);
                  if (!stretchedBoundingBox.expand(0.5).intersects(p.portal.getBoundingBox())) {
                     return true;
                  } else {
                     return Math.abs(getTiming(entity) - p.activeTime) >= 3L
                        ? true
                        : !CollisionHelper.mayEntityCollideWithPortal(entity, p.portal, entity.getCameraPosVec(0.0F), entity.getBoundingBox());
                  }
               }
            }
         );
   }

   private static int getTiming(Entity entity) {
      return entity.age;
   }

   public Vec3d handleCollision(Entity entity, Vec3d attemptedMove) {
      if (this.portalCollisions.isEmpty()) {
         return attemptedMove;
      } else {
         Profilers.get().push("cross_portal_collision");
         this.portalCollisions.sort(Comparator.<PortalCollisionEntry>comparingLong(p -> p.activeTime).reversed());
         Vec3d result = doHandleCollision(entity, attemptedMove, 1, this.portalCollisions, entity.getBoundingBox());
         Profilers.get().pop();
         return result;
      }
   }

   private static Vec3d doHandleCollision(
      Entity entity, Vec3d attemptedMove, int portalLayer, List<PortalCollisionEntry> portalCollisions, Box originalBoundingBox
   ) {
      Vec3d currentMove = handleThisSideMove(entity, attemptedMove, originalBoundingBox, portalCollisions);

      for (PortalCollisionEntry portalCollision : portalCollisions) {
         Portal portal = portalCollision.portal;
         Vec3d eyePos = entity.getCameraPosVec(0.0F);
         currentMove = handleOtherSideMove(entity, currentMove, portal, originalBoundingBox, portal.transformPoint(eyePos), portalLayer);
      }

      Vec3d r = new Vec3d(
         CollisionHelper.fixCoordinateFloatingPointError(attemptedMove.x, currentMove.x),
         CollisionHelper.fixCoordinateFloatingPointError(attemptedMove.y, currentMove.y),
         CollisionHelper.fixCoordinateFloatingPointError(attemptedMove.z, currentMove.z)
      );
      return r;
   }

   private static Vec3d handleOtherSideMove(
      Entity entity, Vec3d attemptedMove, Portal collidingPortal, Box originalBoundingBox, Vec3d transformedEyePos, int portalLayer
   ) {
      if (!collidingPortal.getHasCrossPortalCollision()) {
         return attemptedMove;
      } else if (portalLayer >= 5) {
         return attemptedMove;
      } else {
         Vec3d transformedAttemptedMove = collidingPortal.transformLocalVec(attemptedMove);
         Box boxOtherSide = CollisionHelper.transformBox(collidingPortal, originalBoundingBox);
         if (boxOtherSide == null) {
            return attemptedMove;
         } else if (isBoxTooBig(boxOtherSide)) {
            return attemptedMove;
         } else {
            World destinationWorld = collidingPortal.getDestWorld();
            if (!destinationWorld.isChunkLoaded(BlockPos.ofFloored(boxOtherSide.getCenter()))) {
               return portalLayer <= 1 ? handleOtherSideChunkNotLoaded(entity, attemptedMove, collidingPortal, originalBoundingBox) : attemptedMove;
            } else {
               List<Portal> indirectCollidingPortals = McHelper.findEntitiesByBox(
                  Portal.class,
                  collidingPortal.getDestinationWorld(),
                  boxOtherSide.stretch(transformedAttemptedMove),
                  IPGlobal.maxNormalPortalRadius,
                  p -> CollisionHelper.mayEntityCollideWithPortal(entity, p, transformedEyePos, boxOtherSide)
                     && collidingPortal.isOnDestinationSide(p.getOriginPos(), 0.1)
               );
               Direction transformedGravityDirection = collidingPortal.getTransformedGravityDirection(
                  GravityChangerInterface.invoker.getGravityDirection(entity)
               );
               Plane innerClipping = collidingPortal.getInnerClipping();
               Vec3d collided = CollisionHelper.handleCollisionWithShapeProcessor(
                  entity, boxOtherSide, destinationWorld, transformedAttemptedMove, shape -> {
                     VoxelShape current = innerClipping == null ? shape : CollisionHelper.clipVoxelShape(shape, innerClipping.pos(), innerClipping.normal());
                     if (current == null) {
                        return null;
                     } else {
                        if (!indirectCollidingPortals.isEmpty()) {
                           current = processThisSideCollisionShape(current, indirectCollidingPortals);
                        }

                        return current;
                     }
                  }, transformedGravityDirection, collidingPortal.getScale()
               );
               if (!indirectCollidingPortals.isEmpty()) {
                  for (Portal indirectCollidingPortal : indirectCollidingPortals) {
                     collided = handleOtherSideMove(
                        entity, collided, indirectCollidingPortal, boxOtherSide, collidingPortal.transformPoint(transformedEyePos), portalLayer + 1
                     );
                  }
               }

               collided = new Vec3d(
                  CollisionHelper.fixCoordinateFloatingPointError(transformedAttemptedMove.x, collided.x),
                  CollisionHelper.fixCoordinateFloatingPointError(transformedAttemptedMove.y, collided.y),
                  CollisionHelper.fixCoordinateFloatingPointError(transformedAttemptedMove.z, collided.z)
               );
               return collidingPortal.inverseTransformLocalVec(collided);
            }
         }
      }
   }

   private static Vec3d handleOtherSideChunkNotLoaded(Entity entity, Vec3d attemptedMove, Portal collidingPortal, Box originalBoundingBox) {
      if (entity instanceof PlayerEntity && entity.getEntityWorld().isClient()) {
         CollisionHelper.informClientStagnant();
      }

      return collidingPortal.getPortalShape()
         .getMovementForPushingEntityOutOfPortal(collidingPortal, collidingPortal.getThisSideState(), entity, attemptedMove);
   }

   private static Vec3d handleThisSideMove(
      Entity entity, Vec3d attemptedMove, Box originalBoundingBox, List<PortalCollisionEntry> portalCollisions
   ) {
      Direction gravity = GravityChangerInterface.invoker.getGravityDirection(entity);
      return CollisionHelper.handleCollisionWithShapeProcessor(
         entity,
         entity.getBoundingBox(),
         entity.getEntityWorld(),
         attemptedMove,
         shape -> processThisSideCollisionShape(shape, Helper.mappedListView(portalCollisions, e -> e.portal)),
         gravity,
         1.0
      );
   }

   @Nullable
   private static VoxelShape processThisSideCollisionShape(VoxelShape originalShape, List<Portal> portalCollisions) {
      VoxelShape shape = originalShape;
      if (originalShape.isEmpty()) {
         return originalShape;
      } else {
         Box shapeBounds = originalShape.getBoundingBox();

         for (int i = 0; i < portalCollisions.size(); i++) {
            Portal portal = portalCollisions.get(i);
            Plane clipping = portal.getPortalShape().getOuterClipping(portal.getThisSideState());
            if (clipping != null) {
               boolean boxFullyBehindPlane = CollisionHelper.isBoxFullyBehindPlane(clipping.pos(), clipping.normal(), shapeBounds);
               if (!boxFullyBehindPlane) {
                  continue;
               }
            }

            VoxelShape exclusion = portal.getThisSideCollisionExclusion();
            if (exclusion != null && !exclusion.isEmpty()) {
               if (Helper.boxContains(exclusion.getBoundingBox(), shapeBounds)) {
                  return null;
               }

               shape = VoxelShapes.combine(shape, exclusion, BooleanBiFunction.ONLY_FIRST);
               if (shape.isEmpty()) {
                  return shape;
               }

               shapeBounds = shape.getBoundingBox();
            }
         }

         return shape;
      }
   }

   @Nullable
   public Box getActiveCollisionBox(Entity entity, Box rawBoundingBox) {
      Box currentBox = rawBoundingBox;

      for (PortalCollisionEntry portalCollision : this.portalCollisions) {
         Portal portal = portalCollision.portal;
         Box newBox = portal.getPortalShape().transformEntityActiveCollisionBox(portal, currentBox, entity);
         if (newBox == null) {
            return null;
         }

         currentBox = newBox;
      }

      return currentBox;
   }

   public static void updateCollidingPortalAfterTeleportation(Entity entity, Vec3d newEyePos, Vec3d newLastTickEyePos, float partialTicks) {
      ((IEEntity)entity).ip_clearCollidingPortal();
      McHelper.findEntitiesByBox(
            Portal.class, entity.getEntityWorld(), CollisionHelper.getStretchedBoundingBox(entity), IPGlobal.maxNormalPortalRadius, p -> true
         )
         .forEach(p -> CollisionHelper.notifyCollidingPortals(p, partialTicks));
      McHelper.setEyePos(entity, newEyePos, newLastTickEyePos);
      McHelper.updateBoundingBox(entity);
   }

   public boolean hasCollisionEntry() {
      return !this.portalCollisions.isEmpty();
   }

   public void notifyCollidingWithPortal(Entity entity, Portal portal) {
      if (this.portalCollisions.size() < 6) {
         long timing = getTiming(entity);
         int i = Helper.indexOf(this.portalCollisions, p -> p.portal == portal);
         if (i == -1) {
            this.portalCollisions.add(new PortalCollisionEntry(portal, timing));
         } else {
            this.portalCollisions.get(i).activeTime = timing;
         }

         portal.onCollidingWithEntity(entity);
         this.lastActiveTime = timing;
      }
   }

   public List<Portal> getCollidingPortals() {
      return Helper.mappedListView(this.portalCollisions, p -> p.portal);
   }

   public static Vec3d getOffsetForPushingBoxOutOfAABB(Box boxToPush, Box portalBox, Vec3d movement) {
      double absMovementX = Math.abs(movement.x);
      double absMovementY = Math.abs(movement.y);
      double absMovementZ = Math.abs(movement.z);
      if (absMovementX > absMovementY && absMovementX > absMovementZ) {
         double mx = Range.getPushRangeMovement(boxToPush.minX, boxToPush.maxX, portalBox.minX, portalBox.maxX);
         if (mx != 0.0) {
            return new Vec3d(mx, 0.0, 0.0);
         }
      }

      if (absMovementY > absMovementX && absMovementY > absMovementZ) {
         double my = Range.getPushRangeMovement(boxToPush.minY, boxToPush.maxY, portalBox.minY, portalBox.maxY);
         if (my != 0.0) {
            return new Vec3d(0.0, my, 0.0);
         }
      }

      if (absMovementZ > absMovementX && absMovementZ > absMovementY) {
         double mz = Range.getPushRangeMovement(boxToPush.minZ, boxToPush.maxZ, portalBox.minZ, portalBox.maxZ);
         if (mz != 0.0) {
            return new Vec3d(0.0, 0.0, mz);
         }
      }

      return Vec3d.ZERO;
   }

   public static Vec3d getOffsetForConfiningBoxInsideAABB(Box boxToPush, Box portalBox) {
      return new Vec3d(
         Range.getConfineRangeMovement(boxToPush.minX, boxToPush.maxX, portalBox.minX, portalBox.maxX),
         Range.getConfineRangeMovement(boxToPush.minY, boxToPush.maxY, portalBox.minY, portalBox.maxY),
         Range.getConfineRangeMovement(boxToPush.minZ, boxToPush.maxZ, portalBox.minZ, portalBox.maxZ)
      );
   }

   @Nullable
   public static Vec3d getOffsetForPushingBoxOutOfPlane(Vec3d origin, Vec3d normal, Box movedBoundingBox) {
      Vec3d innerDirection = normal.multiply(-1.0);
      double innerSignedDistance = Arrays.stream(Helper.eightVerticesOf(movedBoundingBox))
         .mapToDouble(pos -> pos.subtract(origin).dotProduct(normal))
         .min()
         .orElseThrow();
      return innerSignedDistance < 0.0 ? normal.multiply(-innerSignedDistance) : null;
   }

   @NotNull
   public static Vec3d getMovementForPushingEntityOutOfPortal(Vec3d attemptedMove, Vec3d origin, Vec3d normal, Box originalBoundingBox) {
      Vec3d innerDirection = normal.multiply(-1.0);
      double innerSignedDistance = Arrays.stream(Helper.eightVerticesOf(originalBoundingBox))
         .mapToDouble(pos -> pos.subtract(origin).dotProduct(normal))
         .min()
         .orElseThrow();
      Vec3d attemptedMoveProjectedToInnerDirection = innerDirection.multiply(innerDirection.dotProduct(attemptedMove));
      return innerSignedDistance < 0.0
         ? attemptedMove.add(normal.multiply(-innerSignedDistance)).subtract(attemptedMoveProjectedToInnerDirection)
         : attemptedMove.subtract(attemptedMoveProjectedToInnerDirection);
   }

   private static boolean isBoxTooBig(Box aabb) {
      return aabb.getLengthX() > 100.0 || aabb.getLengthY() > 100.0 || aabb.getLengthZ() > 100.0;
   }
}
