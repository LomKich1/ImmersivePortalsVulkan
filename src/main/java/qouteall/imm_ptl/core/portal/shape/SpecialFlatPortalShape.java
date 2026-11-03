package qouteall.imm_ptl.core.portal.shape;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.animation.UnilateralPortalState;
import qouteall.imm_ptl.core.render.ViewAreaRenderer;
import qouteall.q_misc_util.my_util.BoxPredicateF;
import qouteall.q_misc_util.my_util.Mesh2D;
import qouteall.q_misc_util.my_util.Plane;
import qouteall.q_misc_util.my_util.RayTraceResult;
import qouteall.q_misc_util.my_util.TriangleConsumer;

public final class SpecialFlatPortalShape implements PortalShape {
   @NotNull
   public final Mesh2D mesh;

   public SpecialFlatPortalShape(@NotNull Mesh2D mesh) {
      this.mesh = mesh;
      this.mesh.enableTriangleLookup();
   }

   public static void init() {
      PortalShapeSerialization.addSerializer(
         new PortalShapeSerialization.Serializer<>(
            "specialFlat", SpecialFlatPortalShape.class, SpecialFlatPortalShape::serialize, SpecialFlatPortalShape::deserialize
         )
      );
   }

   @NotNull
   private NbtCompound serialize() {
      NbtCompound compoundTag = new NbtCompound();
      compoundTag.put("shape", this.mesh.toTag());
      return compoundTag;
   }

   @Nullable
   private static SpecialFlatPortalShape deserialize(NbtCompound tag) {
      Mesh2D m = Mesh2D.fromTag(tag.getCompoundOrEmpty("shape"));
      return m == null ? null : new SpecialFlatPortalShape(m);
   }

   @Override
   public boolean isPlanar() {
      return true;
   }

   @Override
   public Box getBoundingBox(UnilateralPortalState portalState, boolean limitSize, double boxExpand) {
      return RectangularPortalShape.INSTANCE.getBoundingBox(portalState, limitSize, boxExpand);
   }

   @Override
   public double roughDistanceToPortalShape(UnilateralPortalState portalState, Vec3d pos) {
      return RectangularPortalShape.INSTANCE.roughDistanceToPortalShape(portalState, pos);
   }

   @Nullable
   @Override
   public RayTraceResult raytracePortalShapeByLocalPos(UnilateralPortalState portalState, Vec3d localFrom, Vec3d localTo, double leniency) {
      RayTraceResult roughRayTrace = RectangularPortalShape.INSTANCE.raytracePortalShapeByLocalPos(portalState, localFrom, localTo, leniency);
      if (roughRayTrace == null) {
         return null;
      } else {
         double localX = roughRayTrace.hitPos().getX();
         double localY = roughRayTrace.hitPos().getY();
         double halfWidth = portalState.width() / 2.0;
         double halfHeight = portalState.height() / 2.0;
         double boxR = Math.max(leniency, 1.0E-5);
         double nx = localX / halfWidth;
         double ny = localY / halfHeight;
         boolean intersectWithMesh = this.mesh.boxIntersects(nx - boxR, ny - boxR, nx + boxR, ny + boxR);
         return intersectWithMesh ? roughRayTrace : null;
      }
   }

   @Override
   public Plane getOuterClipping(UnilateralPortalState portalState) {
      return RectangularPortalShape.INSTANCE.getOuterClipping(portalState);
   }

   @Override
   public Plane getInnerClipping(UnilateralPortalState thisSideState, UnilateralPortalState otherSideState, Portal portal) {
      return RectangularPortalShape.INSTANCE.getInnerClipping(thisSideState, otherSideState, portal);
   }

   @Override
   public PortalShape getFlipped() {
      Mesh2D newMesh = new Mesh2D();

      for (int ti = 0; ti < this.mesh.getStoredTriangleNum(); ti++) {
         if (this.mesh.isTriangleValid(ti)) {
            int p0Index = this.mesh.trianglePointIndexes.getInt(ti * 3);
            int p1Index = this.mesh.trianglePointIndexes.getInt(ti * 3 + 1);
            int p2Index = this.mesh.trianglePointIndexes.getInt(ti * 3 + 2);
            newMesh.addTriangle(
               -this.mesh.pointCoords.getDouble(p0Index * 2),
               this.mesh.pointCoords.getDouble(p0Index * 2 + 1),
               -this.mesh.pointCoords.getDouble(p1Index * 2),
               this.mesh.pointCoords.getDouble(p1Index * 2 + 1),
               -this.mesh.pointCoords.getDouble(p2Index * 2),
               this.mesh.pointCoords.getDouble(p2Index * 2 + 1)
            );
         }
      }

      return new SpecialFlatPortalShape(newMesh);
   }

   @Override
   public PortalShape getReverse() {
      return this.getFlipped();
   }

   @Environment(EnvType.CLIENT)
   @Override
   public void renderViewAreaMesh(Vec3d portalOriginRelativeToCamera, UnilateralPortalState portalState, TriangleConsumer vertexOutput, boolean isGlobalPortal) {
      double halfWidth = portalState.width() / 2.0;
      double halfHeight = portalState.height() / 2.0;
      Vec3d localXAxis = portalState.getAxisW().multiply(halfWidth);
      Vec3d localYAxis = portalState.getAxisH().multiply(halfHeight);

      for (int ti = 0; ti < this.mesh.getStoredTriangleNum(); ti++) {
         if (this.mesh.isTriangleValid(ti)) {
            int p0Index = this.mesh.getTrianglePointIndex(ti, 0);
            int p1Index = this.mesh.getTrianglePointIndex(ti, 1);
            int p2Index = this.mesh.getTrianglePointIndex(ti, 2);
            double p0x = this.mesh.getPointX(p0Index);
            double p0y = this.mesh.getPointY(p0Index);
            double p1x = this.mesh.getPointX(p1Index);
            double p1y = this.mesh.getPointY(p1Index);
            double p2x = this.mesh.getPointX(p2Index);
            double p2y = this.mesh.getPointY(p2Index);
            ViewAreaRenderer.outputTriangle(vertexOutput, portalOriginRelativeToCamera, localXAxis, localYAxis, p0x, p0y, p1x, p1y, p2x, p2y);
         }
      }
   }

   @Override
   public boolean canCollideWith(Portal portal, UnilateralPortalState portalState, Vec3d entityEyePos, Box entityBoundingBox) {
      boolean inFrontOfPortal = portal.isInFrontOfPortal(entityEyePos);
      return !inFrontOfPortal ? false : this.isBoxInPortalProjection(portalState, entityBoundingBox);
   }

   @Override
   public boolean isLocalBoxInPortalProjection(UnilateralPortalState portalState, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
      boolean roughTest = RectangularPortalShape.INSTANCE.isLocalBoxInPortalProjection(portalState, minX, minY, minZ, maxX, maxY, maxZ);
      if (!roughTest) {
         return false;
      } else {
         double halfWidth = portalState.width() / 2.0;
         double halfHeight = portalState.height() / 2.0;
         return this.mesh.boxIntersects(minX / halfWidth, minY / halfHeight, maxX / halfWidth, maxY / halfHeight);
      }
   }

   @Override
   public Vec3d getMovementForPushingEntityOutOfPortal(Portal portal, UnilateralPortalState portalState, Entity entity, Vec3d attemptedMove) {
      return RectangularPortalShape.INSTANCE.getMovementForPushingEntityOutOfPortal(portal, portalState, entity, attemptedMove);
   }

   @Override
   public PortalShape cloneIfNecessary() {
      return new SpecialFlatPortalShape(this.mesh.copy());
   }

   @Override
   public boolean roughTestVisibility(UnilateralPortalState portalState, Vec3d cameraPos, boolean isIrisShaderOn) {
      return RectangularPortalShape.INSTANCE.roughTestVisibility(portalState, cameraPos, isIrisShaderOn);
   }

   public static SpecialFlatPortalShape createDefault() {
      return new SpecialFlatPortalShape(Mesh2D.createNewFullQuadMesh());
   }

   @Environment(EnvType.CLIENT)
   @Override
   public BoxPredicateF getInnerFrustumCullingFunc(Portal portal, Vec3d cameraPos) {
      return RectangularPortalShape.INSTANCE.getInnerFrustumCullingFunc(portal, cameraPos);
   }

   @Environment(EnvType.CLIENT)
   @Override
   public BoxPredicateF getOuterFrustumCullingFunc(Portal portal, Vec3d cameraPos) {
      return RectangularPortalShape.INSTANCE.getOuterFrustumCullingFunc(portal, cameraPos);
   }

   @Override
   public boolean shouldRenderInside(Portal portal, Box box) {
      return RectangularPortalShape.INSTANCE.shouldRenderInside(portal, box);
   }

   @Override
   public VoxelShape getThisSideCollisionExclusion(UnilateralPortalState portalState) {
      return RectangularPortalShape.INSTANCE.getThisSideCollisionExclusion(portalState);
   }

   @Nullable
   @Override
   public Box transformEntityActiveCollisionBox(Portal portal, Box box, Entity entity) {
      return RectangularPortalShape.INSTANCE.transformEntityActiveCollisionBox(portal, box, entity);
   }
}
