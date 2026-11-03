package qouteall.imm_ptl.core.render;

import java.util.Comparator;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.MinecraftClient;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.IPCGlobal;
import qouteall.imm_ptl.core.compat.iris_compatibility.IrisInterface;
import qouteall.imm_ptl.core.compat.sodium_compatibility.SodiumInterface;
import qouteall.imm_ptl.core.portal.Mirror;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.animation.UnilateralPortalState;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import qouteall.q_misc_util.my_util.BoxPredicateF;
import qouteall.q_misc_util.my_util.Plane;

@Environment(EnvType.CLIENT)
public class FrustumCuller {
   @Nullable
   private BoxPredicateF canDetermineInvisibleFunc;
   private double camX;
   private double camY;
   private double camZ;

   public void update(double cameraX, double cameraY, double cameraZ) {
      this.canDetermineInvisibleFunc = this.getCanDetermineInvisibleFunc(cameraX, cameraY, cameraZ);
      this.camX = cameraX;
      this.camY = cameraY;
      this.camZ = cameraZ;
   }

   public boolean canDetermineInvisibleWithCameraCoord(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
      return this.canDetermineInvisibleFunc == null ? false : this.canDetermineInvisibleFunc.test(minX, minY, minZ, maxX, maxY, maxZ);
   }

   public boolean canDetermineInvisibleWithCameraCoord(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
      return this.canDetermineInvisibleFunc == null
         ? false
         : this.canDetermineInvisibleFunc.test((float)minX, (float)minY, (float)minZ, (float)maxX, (float)maxY, (float)maxZ);
   }

   public boolean canDetermineInvisibleWithWorldCoord(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
      return this.canDetermineInvisibleFunc == null
         ? false
         : this.canDetermineInvisibleWithCameraCoord(minX - this.camX, minY - this.camY, minZ - this.camZ, maxX - this.camX, maxY - this.camY, maxZ - this.camZ);
   }

   @Nullable
   private BoxPredicateF getCanDetermineInvisibleFunc(double cameraX, double cameraY, double cameraZ) {
      if (!IPCGlobal.doUseAdvancedFrustumCulling) {
         return null;
      } else if (IrisInterface.invoker.isRenderingShadowMap()) {
         return null;
      } else if (PortalRendering.isRendering()) {
         Portal renderingPortal = PortalRendering.getRenderingPortal();
         return renderingPortal.getPortalShape().getInnerFrustumCullingFunc(renderingPortal, new Vec3d(cameraX, cameraY, cameraZ));
      } else if (!IPCGlobal.useSuperAdvancedFrustumCulling) {
         return null;
      } else if (!SodiumInterface.invoker.isSodiumPresent()) {
         return null;
      } else {
         Portal portal = getCurrentNearestVisibleCullablePortal();
         return portal != null ? portal.getPortalShape().getOuterFrustumCullingFunc(portal, new Vec3d(cameraX, cameraY, cameraZ)) : null;
      }
   }

   @Nullable
   private static Portal getCurrentNearestVisibleCullablePortal() {
      if (TransformationManager.isIsometricView) {
         return null;
      } else {
         Vec3d cameraPos = MinecraftClient.getInstance().gameRenderer.getCamera().getCameraPos();
         return CHelper.getClientNearbyPortals(16.0)
            .filter(portal -> portal.isInFrontOfPortal(cameraPos))
            .filter(Portal::canDoOuterFrustumCulling)
            .min(Comparator.comparingDouble(portal -> portal.getDistanceToNearestPointInPortal(cameraPos)))
            .orElse(null);
      }
   }

   public static Vec3d[] getRectPortalFourVerticesCounterClockwise(UnilateralPortalState thisSideState) {
      double halfWidth = thisSideState.width() / 2.0;
      double halfHeight = thisSideState.height() / 2.0;
      return new Vec3d[]{
         thisSideState.transformLocalToGlobal(halfWidth, -halfHeight, 0.0),
         thisSideState.transformLocalToGlobal(halfWidth, halfHeight, 0.0),
         thisSideState.transformLocalToGlobal(-halfWidth, halfHeight, 0.0),
         thisSideState.transformLocalToGlobal(-halfWidth, -halfHeight, 0.0)
      };
   }

   public static FrustumCuller.Frustum4Planes getFrustumPlanesFromFourVerticesCounterClockwise(Vec3d[] vertices) {
      Vec3d normal0 = vertices[1].crossProduct(vertices[0]).normalize();
      Vec3d normal1 = vertices[2].crossProduct(vertices[1]).normalize();
      Vec3d normal2 = vertices[3].crossProduct(vertices[2]).normalize();
      Vec3d normal3 = vertices[0].crossProduct(vertices[3]).normalize();
      return new FrustumCuller.Frustum4Planes(
         (float)normal0.x,
         (float)normal0.y,
         (float)normal0.z,
         0.0F,
         (float)normal1.x,
         (float)normal1.y,
         (float)normal1.z,
         0.0F,
         (float)normal2.x,
         (float)normal2.y,
         (float)normal2.z,
         0.0F,
         (float)normal3.x,
         (float)normal3.y,
         (float)normal3.z,
         0.0F
      );
   }

   public static BoxPredicateF getFlatPortalInnerFrustumCullingFunc(Portal portal, Vec3d cameraPos) {
      Vec3d[] v = getRectPortalFourVerticesCounterClockwise(portal.getThisSideState());
      Vec3d[] vTransformed = new Vec3d[]{
         portal.transformPoint(v[0]).subtract(cameraPos),
         portal.transformPoint(v[1]).subtract(cameraPos),
         portal.transformPoint(v[2]).subtract(cameraPos),
         portal.transformPoint(v[3]).subtract(cameraPos)
      };
      if (portal instanceof Mirror) {
         vTransformed = new Vec3d[]{vTransformed[3], vTransformed[2], vTransformed[1], vTransformed[0]};
      }

      FrustumCuller.Frustum4Planes fourPlanes = getFrustumPlanesFromFourVerticesCounterClockwise(vTransformed);
      return fourPlanes::isFullyOutside;
   }

   public static BoxPredicateF getFlatPortalOuterFrustumCullingFunc(Portal portal, Vec3d cameraPos) {
      Vec3d[] v = getRectPortalFourVerticesCounterClockwise(portal.getThisSideState());
      Vec3d[] vTransformed = new Vec3d[]{
         v[0].subtract(cameraPos), v[1].subtract(cameraPos), v[2].subtract(cameraPos), v[3].subtract(cameraPos)
      };
      FrustumCuller.Frustum4Planes fourPlanes = getFrustumPlanesFromFourVerticesCounterClockwise(vTransformed);
      Plane portalPlane = new Plane(portal.getOriginPos().subtract(cameraPos), portal.getNormal());
      float portalPlaneX = (float)portalPlane.getEquationX();
      float portalPlaneY = (float)portalPlane.getEquationY();
      float portalPlaneZ = (float)portalPlane.getEquationZ();
      float portalPlaneW = (float)portalPlane.getEquationW();
      return (minX, minY, minZ, maxX, maxY, maxZ) -> {
         boolean a = isFullyBehindPlane(minX, minY, minZ, maxX, maxY, maxZ, portalPlaneX, portalPlaneY, portalPlaneZ, portalPlaneW);
         boolean b = fourPlanes.isFullyInside(minX, minY, minZ, maxX, maxY, maxZ);
         return a && b;
      };
   }

   public static boolean isFullyInFrontOfPlane(
      float minX, float minY, float minZ, float maxX, float maxY, float maxZ, float planeX, float planeY, float planeZ, float planeW
   ) {
      float testingBoxX = planeX > 0.0F ? minX : maxX;
      float testingBoxY = planeY > 0.0F ? minY : maxY;
      float testingBoxZ = planeZ > 0.0F ? minZ : maxZ;
      return testingBoxX * planeX + testingBoxY * planeY + testingBoxZ * planeZ + planeW > 0.0F;
   }

   public static boolean isFullyBehindPlane(
      float minX, float minY, float minZ, float maxX, float maxY, float maxZ, float planeX, float planeY, float planeZ, float planeW
   ) {
      float testingBoxX = planeX > 0.0F ? maxX : minX;
      float testingBoxY = planeY > 0.0F ? maxY : minY;
      float testingBoxZ = planeZ > 0.0F ? maxZ : minZ;
      return testingBoxX * planeX + testingBoxY * planeY + testingBoxZ * planeZ + planeW < 0.0F;
   }

   @Deprecated
   public static FrustumCuller.BatchTestResult testBoxTwoVertices(
      double minX,
      double minY,
      double minZ,
      double maxX,
      double maxY,
      double maxZ,
      double planeNormalX,
      double planeNormalY,
      double planeNormalZ,
      double planePosX,
      double planePosY,
      double planePosZ
   ) {
      double p1x;
      double p2x;
      if (planeNormalX > 0.0) {
         p1x = minX;
         p2x = maxX;
      } else {
         p1x = maxX;
         p2x = minX;
      }

      double p1y;
      double p2y;
      if (planeNormalY > 0.0) {
         p1y = minY;
         p2y = maxY;
      } else {
         p1y = maxY;
         p2y = minY;
      }

      double p1z;
      double p2z;
      if (planeNormalZ > 0.0) {
         p1z = minZ;
         p2z = maxZ;
      } else {
         p1z = maxZ;
         p2z = minZ;
      }

      boolean r1 = isInFrontOf(p1x - planePosX, p1y - planePosY, p1z - planePosZ, planeNormalX, planeNormalY, planeNormalZ);
      boolean r2 = isInFrontOf(p2x - planePosX, p2y - planePosY, p2z - planePosZ, planeNormalX, planeNormalY, planeNormalZ);
      if (r1 && r2) {
         return FrustumCuller.BatchTestResult.all_true;
      } else {
         return !r1 && !r2 ? FrustumCuller.BatchTestResult.all_false : FrustumCuller.BatchTestResult.both;
      }
   }

   @Deprecated
   public static FrustumCuller.BatchTestResult testBoxTwoVertices(
      double minX, double minY, double minZ, double maxX, double maxY, double maxZ, Vec3d planeNormal
   ) {
      return testBoxTwoVertices(minX, minY, minZ, maxX, maxY, maxZ, planeNormal.x, planeNormal.y, planeNormal.z, 0.0, 0.0, 0.0);
   }

   @Deprecated
   private static boolean isInFrontOf(double x, double y, double z, double planeNormalX, double planeNormalY, double planeNormalZ) {
      return x * planeNormalX + y * planeNormalY + z * planeNormalZ >= 0.0;
   }

   @Deprecated
   public static enum BatchTestResult {
      all_true,
      all_false,
      both;
   }

   public record Frustum4Planes(
      float p0X,
      float p0Y,
      float p0Z,
      float p0W,
      float p1X,
      float p1Y,
      float p1Z,
      float p1W,
      float p2X,
      float p2Y,
      float p2Z,
      float p2W,
      float p3X,
      float p3Y,
      float p3Z,
      float p3W
   ) {
      public boolean isFullyOutside(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
         return FrustumCuller.isFullyBehindPlane(minX, minY, minZ, maxX, maxY, maxZ, this.p0X, this.p0Y, this.p0Z, this.p0W)
            || FrustumCuller.isFullyBehindPlane(minX, minY, minZ, maxX, maxY, maxZ, this.p1X, this.p1Y, this.p1Z, this.p1W)
            || FrustumCuller.isFullyBehindPlane(minX, minY, minZ, maxX, maxY, maxZ, this.p2X, this.p2Y, this.p2Z, this.p2W)
            || FrustumCuller.isFullyBehindPlane(minX, minY, minZ, maxX, maxY, maxZ, this.p3X, this.p3Y, this.p3Z, this.p3W);
      }

      public boolean isFullyInside(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
         return FrustumCuller.isFullyInFrontOfPlane(minX, minY, minZ, maxX, maxY, maxZ, this.p0X, this.p0Y, this.p0Z, this.p0W)
            && FrustumCuller.isFullyInFrontOfPlane(minX, minY, minZ, maxX, maxY, maxZ, this.p1X, this.p1Y, this.p1Z, this.p1W)
            && FrustumCuller.isFullyInFrontOfPlane(minX, minY, minZ, maxX, maxY, maxZ, this.p2X, this.p2Y, this.p2Z, this.p2W)
            && FrustumCuller.isFullyInFrontOfPlane(minX, minY, minZ, maxX, maxY, maxZ, this.p3X, this.p3Y, this.p3Z, this.p3W);
      }
   }
}
