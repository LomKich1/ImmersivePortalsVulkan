package qouteall.imm_ptl.core.portal.shape;

import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.math.ChunkSectionPos;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.animation.UnilateralPortalState;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.my_util.BoxPredicateF;
import qouteall.q_misc_util.my_util.Plane;
import qouteall.q_misc_util.my_util.RayTraceResult;
import qouteall.q_misc_util.my_util.TriangleConsumer;

public interface PortalShape {
   boolean isPlanar();

   Box getBoundingBox(UnilateralPortalState var1, boolean var2, double var3);

   double roughDistanceToPortalShape(UnilateralPortalState var1, Vec3d var2);

   @Nullable
   RayTraceResult raytracePortalShapeByLocalPos(UnilateralPortalState var1, Vec3d var2, Vec3d var3, double var4);

   @Nullable
   default RayTraceResult raytracePortalShape(UnilateralPortalState portalState, Vec3d from, Vec3d to, double leniency) {
      Vec3d localFrom = portalState.transformGlobalToLocal(from);
      Vec3d localTo = portalState.transformGlobalToLocal(to);
      RayTraceResult hit = this.raytracePortalShapeByLocalPos(portalState, localFrom, localTo, leniency);
      return hit != null
         ? new RayTraceResult(hit.t(), portalState.transformLocalToGlobal(hit.hitPos()), portalState.transformVecLocalToGlobal(hit.surfaceNormal()))
         : null;
   }

   @Nullable
   Plane getOuterClipping(UnilateralPortalState var1);

   @Nullable
   Plane getInnerClipping(UnilateralPortalState var1, UnilateralPortalState var2, Portal var3);

   @Nullable
   default List<Plane> getNearbyPortalPlanes(UnilateralPortalState portalState, Box box) {
      Plane outerClipping = this.getOuterClipping(portalState);
      return outerClipping != null ? List.of(outerClipping) : null;
   }

   PortalShape getFlipped();

   PortalShape getReverse();

   boolean roughTestVisibility(UnilateralPortalState var1, Vec3d var2, boolean var3);

   @Environment(EnvType.CLIENT)
   void renderViewAreaMesh(Vec3d var1, UnilateralPortalState var2, TriangleConsumer var3, boolean var4);

   boolean canCollideWith(Portal var1, UnilateralPortalState var2, Vec3d var3, Box var4);

   default boolean isBoxInPortalProjection(UnilateralPortalState portalState, Box box) {
      Vec3d[] vertexes = Helper.eightVerticesOf(box);
      Vec3d originPos = portalState.position();
      Vec3d[] transformed = new Vec3d[vertexes.length];

      for (int i = 0; i < transformed.length; i++) {
         transformed[i] = portalState.transformGlobalToLocal(vertexes[i]);
      }

      double minX = transformed[0].getX();
      double maxX = minX;
      double minY = transformed[0].getY();
      double maxY = minY;
      double minZ = transformed[0].getZ();
      double maxZ = minZ;

      for (int i = 1; i < 8; i++) {
         Vec3d v = transformed[i];
         minX = Math.min(minX, v.getX());
         maxX = Math.max(maxX, v.getX());
         minY = Math.min(minY, v.getY());
         maxY = Math.max(maxY, v.getY());
         minZ = Math.min(minZ, v.getZ());
         maxZ = Math.max(maxZ, v.getZ());
      }

      return this.isLocalBoxInPortalProjection(portalState, minX, minY, minZ, maxX, maxY, maxZ);
   }

   boolean isLocalBoxInPortalProjection(UnilateralPortalState var1, double var2, double var4, double var6, double var8, double var10, double var12);

   @Nullable
   default VoxelShape getThisSideCollisionExclusion(UnilateralPortalState portalState) {
      return null;
   }

   Vec3d getMovementForPushingEntityOutOfPortal(Portal var1, UnilateralPortalState var2, Entity var3, Vec3d var4);

   PortalShape cloneIfNecessary();

   @Environment(EnvType.CLIENT)
   default boolean canDoOuterFrustumCulling() {
      return false;
   }

   @Environment(EnvType.CLIENT)
   @Nullable
   default BoxPredicateF getInnerFrustumCullingFunc(Portal portal, Vec3d cameraPos) {
      return null;
   }

   @Nullable
   default Box transformEntityActiveCollisionBox(Portal portal, Box box, Entity entity) {
      return box;
   }

   @Environment(EnvType.CLIENT)
   @Nullable
   default BoxPredicateF getOuterFrustumCullingFunc(Portal portal, Vec3d cameraPos) {
      return null;
   }

   @Environment(EnvType.CLIENT)
   @Nullable
   default ChunkSectionPos getModifiedVisibleSectionIterationOrigin(Portal portal, Vec3d cameraPos) {
      return null;
   }

   boolean shouldRenderInside(Portal var1, Box var2);
}
