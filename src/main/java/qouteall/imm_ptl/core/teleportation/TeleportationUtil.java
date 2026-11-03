package qouteall.imm_ptl.core.teleportation;

import com.mojang.logging.LogUtils;
import java.util.Comparator;
import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.PortalState;
import qouteall.imm_ptl.core.portal.animation.UnilateralPortalState;
import qouteall.imm_ptl.core.portal.shape.PortalShape;
import qouteall.q_misc_util.my_util.RayTraceResult;

public class TeleportationUtil {
   private static final Logger LOGGER = LogUtils.getLogger();

   public static TeleportationUtil.PortalPointVelocity getPortalPointVelocity(PortalState lastTickState, PortalState thisTickState, Vec3d localPos) {
      Vec3d lastThisSidePos = lastTickState.getThisSideState().transformLocalToGlobal(localPos);
      Vec3d currentThisSidePos = thisTickState.getThisSideState().transformLocalToGlobal(localPos);
      Vec3d thisSideVelocity = currentThisSidePos.subtract(lastThisSidePos);
      Vec3d lastOtherSidePos = lastTickState.transformPoint(lastThisSidePos);
      Vec3d currentOtherSidePos = thisTickState.transformPoint(currentThisSidePos);
      Vec3d otherSideVelocity = currentOtherSidePos.subtract(lastOtherSidePos);
      return new TeleportationUtil.PortalPointVelocity(thisSideVelocity, otherSideVelocity);
   }

   public static void transformEntityVelocity(
      Portal portal, Entity entity, TeleportationUtil.PortalPointVelocity portalPointVelocity, Vec3d oldEntityPos
   ) {
      Vec3d oldVelocityRelativeToPortal = McHelper.getWorldVelocity(entity).subtract(portalPointVelocity.thisSidePointVelocity());
      Vec3d transformedVelocityRelativeToPortal = portal.transformVelocityRelativeToPortal(oldVelocityRelativeToPortal, entity, oldEntityPos);
      Vec3d newVelocity = transformedVelocityRelativeToPortal.add(portalPointVelocity.otherSidePointVelocity());
      McHelper.setWorldVelocity(entity, newVelocity);
   }

   @Nullable
   public static TeleportationUtil.Teleportation checkStaticTeleportation(
      Portal portal, Vec3d lastPos, Vec3d currentPos, Vec3d lastTickEyePos, Vec3d thisTickEyePos
   ) {
      Vec3d lastLocalPos = portal.transformFromWorldToPortalLocal(lastPos);
      Vec3d currentLocalPos = portal.transformFromWorldToPortalLocal(currentPos);
      PortalShape portalShape = portal.getPortalShape();
      UnilateralPortalState portalThisSideState = portal.getThisSideState();
      RayTraceResult localRayTraceResult = portalShape.raytracePortalShapeByLocalPos(portalThisSideState, lastLocalPos, currentLocalPos, 0.0);
      if (localRayTraceResult == null) {
         return null;
      } else {
         Vec3d worldHitPos = portalThisSideState.transformLocalToGlobal(localRayTraceResult.hitPos());
         Vec3d newLastTickEyePos = portal.transformPoint(lastTickEyePos);
         Vec3d newThisTickEyePos = portal.transformPoint(thisTickEyePos);
         PortalState portalState = portal.getPortalState();
         return new TeleportationUtil.Teleportation(
            false,
            portal,
            lastPos,
            currentPos,
            lastLocalPos,
            currentLocalPos,
            localRayTraceResult.hitPos(),
            localRayTraceResult.t(),
            worldHitPos,
            portalThisSideState.transformVecLocalToGlobal(localRayTraceResult.surfaceNormal()),
            portalState,
            portalState,
            portalState,
            portalState,
            portalState,
            TeleportationUtil.PortalPointVelocity.ZERO,
            portal.transformPoint(worldHitPos),
            newLastTickEyePos,
            newThisTickEyePos
         );
      }
   }

   @Nullable
   public static TeleportationUtil.Teleportation checkDynamicTeleportation(
      Portal portal,
      PortalState lastFrameState,
      PortalState currentFrameState,
      Vec3d lastFrameEyePos,
      Vec3d currentFrameEyePos,
      PortalState lastTickState,
      PortalState thisTickState,
      Vec3d lastTickEyePos,
      Vec3d thisTickEyePos,
      float partialTicks
   ) {
      Vec3d lastLocalPos = lastFrameState.worldPosToPortalLocalPos(lastFrameEyePos);
      Vec3d currentLocalPos = currentFrameState.worldPosToPortalLocalPos(currentFrameEyePos);
      UnilateralPortalState portalThisSideState = portal.getThisSideState();
      PortalShape portalShape = portal.getPortalShape();
      RayTraceResult rayTraceResult = portalShape.raytracePortalShapeByLocalPos(portalThisSideState, lastLocalPos, currentLocalPos, 0.0);
      if (rayTraceResult == null) {
         return null;
      } else {
         Vec3d localHitPos = rayTraceResult.hitPos();
         Vec3d worldHitPos = portalThisSideState.transformLocalToGlobal(localHitPos);
         TeleportationUtil.PortalPointVelocity portalPointVelocity = getPortalPointVelocity(lastTickState, thisTickState, localHitPos);
         PortalState collisionPortalState = PortalState.interpolate(lastFrameState, currentFrameState, rayTraceResult.t(), false);
         Vec3d collisionPointMappedToThisFrame = thisTickState.getThisSideState().transformLocalToGlobal(localHitPos);
         Vec3d collisionPointMappedToLastFrame = lastFrameState.getThisSideState().transformLocalToGlobal(localHitPos);
         Vec3d newOtherSideLastTickPos = lastTickState.transformPoint(lastTickEyePos);
         Vec3d newOtherSideThisTickPos = thisTickState.transformPoint(thisTickEyePos);
         Vec3d newImmediateCameraPos = newOtherSideLastTickPos.lerp(newOtherSideThisTickPos, partialTicks);
         Vec3d correctImmediateCameraPos = collisionPortalState.transformPoint(currentFrameEyePos);
         Vec3d deltaVelocity = portalPointVelocity.otherSidePointVelocity()
            .subtract(collisionPortalState.transformVec(portalPointVelocity.thisSidePointVelocity()));
         Vec3d offset = correctImmediateCameraPos.subtract(newImmediateCameraPos);
         newOtherSideLastTickPos = newOtherSideLastTickPos.add(offset);
         newOtherSideThisTickPos = newOtherSideThisTickPos.add(offset);
         if (portalShape.isPlanar()) {
            double dot = newOtherSideThisTickPos.subtract(thisTickState.toPos).dotProduct(thisTickState.getContentDirection());
            if (dot < 1.0E-5) {
               LOGGER.info("Teleported to behind the end-tick portal destination. Corrected.");
               Vec3d offset1 = thisTickState.getContentDirection().multiply(Math.max(-dot, 0.0) + 1.0E-5);
               newOtherSideThisTickPos = newOtherSideThisTickPos.add(offset1);
               newOtherSideLastTickPos = newOtherSideLastTickPos.add(offset1);
            }

            newImmediateCameraPos = newOtherSideLastTickPos.lerp(newOtherSideThisTickPos, partialTicks);
            dot = newImmediateCameraPos.subtract(currentFrameState.toPos).dotProduct(currentFrameState.getContentDirection());
            if (dot < 1.0E-5) {
               LOGGER.info("Teleported to behind the end-frame portal destination. Corrected.");
               Vec3d offset1 = currentFrameState.getContentDirection().multiply(Math.max(-dot, 0.0) + 1.0E-5);
               newOtherSideThisTickPos = newOtherSideThisTickPos.add(offset1);
               newOtherSideLastTickPos = newOtherSideLastTickPos.add(offset1);
            }

            newImmediateCameraPos = newOtherSideLastTickPos.lerp(newOtherSideThisTickPos, partialTicks);
            dot = newImmediateCameraPos.subtract(lastFrameState.toPos).dotProduct(lastFrameState.getContentDirection());
            if (dot < 1.0E-5) {
               LOGGER.info("Teleported to behind the last-frame portal destination. Corrected.");
               Vec3d offset1 = lastFrameState.getContentDirection().multiply(Math.max(-dot, 0.0) + 0.001);
               newOtherSideThisTickPos = newOtherSideThisTickPos.add(offset1);
               newOtherSideLastTickPos = newOtherSideLastTickPos.add(offset1);
            }
         }

         Vec3d teleportationCheckpoint = newOtherSideLastTickPos.lerp(newOtherSideThisTickPos, partialTicks);
         return new TeleportationUtil.Teleportation(
            true,
            portal,
            lastFrameEyePos,
            currentFrameEyePos,
            lastLocalPos,
            currentLocalPos,
            localHitPos,
            rayTraceResult.t(),
            worldHitPos,
            portalThisSideState.transformVecLocalToGlobal(rayTraceResult.surfaceNormal()),
            collisionPortalState,
            lastFrameState,
            currentFrameState,
            lastTickState,
            thisTickState,
            portalPointVelocity,
            teleportationCheckpoint,
            newOtherSideLastTickPos,
            newOtherSideThisTickPos
         );
      }
   }

   @Deprecated
   private static TeleportationUtil.PortalPointVelocity getConservativePortalPointVelocity(
      PortalState lastTickState, PortalState thisTickState, Vec3d lastTickPos, Vec3d thisTickPos
   ) {
      List<Vec3d> localPoses = List.of(
         lastTickState.worldPosToPortalLocalPos(lastTickPos),
         lastTickState.worldPosToPortalLocalPos(thisTickPos),
         thisTickState.worldPosToPortalLocalPos(lastTickPos),
         thisTickState.worldPosToPortalLocalPos(thisTickPos)
      );
      List<TeleportationUtil.PortalPointVelocity> portalPointVelocities = localPoses.stream()
         .map(localPos -> getPortalPointVelocity(lastTickState, thisTickState, localPos))
         .toList();
      return new TeleportationUtil.PortalPointVelocity(
         portalPointVelocities.stream()
            .map(v -> v.thisSidePointVelocity)
            .max(Comparator.comparingDouble(p -> p.dotProduct(lastTickState.getNormal())))
            .orElseThrow(),
         portalPointVelocities.stream()
            .map(v -> v.otherSidePointVelocity)
            .max(Comparator.comparingDouble(p -> p.dotProduct(thisTickState.getContentDirection())))
            .orElseThrow()
      );
   }

   private record CollisionInfo(double portalLocalX, double portalLocalY, double tOfCollision, Vec3d collisionPos) {
   }

   public record PortalPointOffset(Vec3d thisSideOffset, Vec3d otherSideOffse) {
   }

   public record PortalPointVelocity(Vec3d thisSidePointVelocity, Vec3d otherSidePointVelocity) {
      public static final TeleportationUtil.PortalPointVelocity ZERO = new TeleportationUtil.PortalPointVelocity(Vec3d.ZERO, Vec3d.ZERO);
   }

   public record Teleportation(
      boolean isDynamic,
      Portal portal,
      Vec3d lastWorldEyePos,
      Vec3d currentWorldEyePos,
      Vec3d lastLocalEyePos,
      Vec3d currentLocalEyePos,
      Vec3d localCollisionPoint,
      double tOfCollision,
      Vec3d worldCollisionPoint,
      Vec3d worldSurfaceNormal,
      PortalState collidingPortalState,
      PortalState lastFrameState,
      PortalState thisFrameState,
      PortalState lastTickState,
      PortalState thisTickState,
      TeleportationUtil.PortalPointVelocity portalPointVelocity,
      Vec3d teleportationCheckpoint,
      Vec3d newLastTickEyePos,
      Vec3d newThisTickEyePos
   ) {
   }
}
