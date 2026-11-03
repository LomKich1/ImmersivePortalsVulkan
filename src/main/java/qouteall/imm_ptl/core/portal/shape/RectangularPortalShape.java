package qouteall.imm_ptl.core.portal.shape;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.util.shape.VoxelShape;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.collision.CollisionHelper;
import qouteall.imm_ptl.core.collision.PortalCollisionHandler;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.animation.UnilateralPortalState;
import qouteall.imm_ptl.core.render.FrustumCuller;
import qouteall.imm_ptl.core.render.ViewAreaRenderer;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.my_util.BoxPredicateF;
import qouteall.q_misc_util.my_util.Plane;
import qouteall.q_misc_util.my_util.Range;
import qouteall.q_misc_util.my_util.RayTraceResult;
import qouteall.q_misc_util.my_util.TriangleConsumer;

public final class RectangularPortalShape implements PortalShape {
   public static final RectangularPortalShape INSTANCE = new RectangularPortalShape();

   public static void init() {
      PortalShapeSerialization.addSerializer(
         new PortalShapeSerialization.Serializer<>("rectangular", RectangularPortalShape.class, s -> new NbtCompound(), t -> INSTANCE)
      );
   }

   @Override
   public boolean isPlanar() {
      return true;
   }

   @Override
   public Box getBoundingBox(UnilateralPortalState portalState, boolean limitSize, double boxExpand) {
      double halfW = portalState.width() / 2.0;
      double halfH = portalState.height() / 2.0;
      if (limitSize) {
         halfW = Math.min(halfW, 64.0);
         halfH = Math.min(halfH, 64.0);
      }

      return Helper.boundingBoxOfPoints(
         new Vec3d[]{
            portalState.transformLocalToGlobal(halfW, halfH, boxExpand),
            portalState.transformLocalToGlobal(halfW, -halfH, boxExpand),
            portalState.transformLocalToGlobal(-halfW, halfH, boxExpand),
            portalState.transformLocalToGlobal(-halfW, -halfH, boxExpand),
            portalState.transformLocalToGlobal(halfW, halfH, -boxExpand),
            portalState.transformLocalToGlobal(halfW, -halfH, -boxExpand),
            portalState.transformLocalToGlobal(-halfW, halfH, -boxExpand),
            portalState.transformLocalToGlobal(-halfW, -halfH, -boxExpand)
         }
      );
   }

   @Override
   public double roughDistanceToPortalShape(UnilateralPortalState portalState, Vec3d pos) {
      Vec3d localPos = portalState.transformGlobalToLocal(pos);
      double distToRec = Helper.getDistanceToRectangle(
         localPos.getX(),
         localPos.getY(),
         -portalState.width() / 2.0,
         -portalState.height() / 2.0,
         portalState.width() / 2.0,
         portalState.height() / 2.0
      );
      return Math.sqrt(distToRec * distToRec + localPos.getZ() * localPos.getZ());
   }

   @Nullable
   @Override
   public RayTraceResult raytracePortalShapeByLocalPos(UnilateralPortalState portalState, Vec3d localFrom, Vec3d localTo, double leniency) {
      double width = portalState.width();
      double height = portalState.height();
      if (localFrom.getZ() > 0.0 && localTo.getZ() < 0.0) {
         double deltaZ = localTo.getZ() - localFrom.getZ();
         double t = -localFrom.getZ() / deltaZ;
         Vec3d hit = localFrom.add(localTo.subtract(localFrom).multiply(t));
         if (Math.abs(hit.getX()) < width / 2.0 + leniency && Math.abs(hit.getY()) < height / 2.0 + leniency) {
            return new RayTraceResult(t, hit, new Vec3d(0.0, 0.0, 1.0));
         }
      }

      return null;
   }

   @Override
   public Plane getOuterClipping(UnilateralPortalState portalState) {
      return new Plane(portalState.position(), portalState.getNormal());
   }

   @Override
   public Plane getInnerClipping(UnilateralPortalState thisSideState, UnilateralPortalState otherSideState, Portal portal) {
      return new Plane(otherSideState.position(), otherSideState.getNormal());
   }

   @Override
   public PortalShape getFlipped() {
      return this;
   }

   @Override
   public PortalShape getReverse() {
      return this;
   }

   @Environment(EnvType.CLIENT)
   @Override
   public void renderViewAreaMesh(
      Vec3d portalOriginRelativeToCamera, UnilateralPortalState portalState, TriangleConsumer vertexOutput, boolean isGlobalPortal
   ) {
      double w = Math.min(portalState.width(), 23333.0);
      double h = Math.min(portalState.height(), 23333.0);
      Vec3d localXAxis = portalState.getAxisW().multiply(w / 2.0);
      Vec3d localYAxis = portalState.getAxisH().multiply(h / 2.0);
      ViewAreaRenderer.outputFullQuad(vertexOutput, portalOriginRelativeToCamera, localXAxis, localYAxis);
   }

   @Override
   public boolean roughTestVisibility(UnilateralPortalState portalState, Vec3d cameraPos, boolean isIrisShaderOn) {
      Vec3d localPos = portalState.transformGlobalToLocal(cameraPos);
      return localPos.getZ() > 0.0;
   }

   @Override
   public boolean canCollideWith(Portal portal, UnilateralPortalState portalState, Vec3d entityEyePos, Box entityBoundingBox) {
      boolean inFrontOfPortal = portalState.transformGlobalToLocal(entityEyePos).getZ() > 0.0;
      return !inFrontOfPortal ? false : this.isBoxInPortalProjection(portalState, entityBoundingBox);
   }

   @Override
   public boolean isLocalBoxInPortalProjection(UnilateralPortalState portalState, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
      double halfWidth = portalState.width() / 2.0;
      double halfHeight = portalState.height() / 2.0;
      return Range.rangeIntersects(-halfWidth, halfWidth, minX, maxX) && Range.rangeIntersects(-halfHeight, halfHeight, minY, maxY);
   }

   @Override
   public Vec3d getMovementForPushingEntityOutOfPortal(Portal portal, UnilateralPortalState portalState, Entity entity, Vec3d attemptedMove) {
      Box originalBoundingBox = entity.getBoundingBox();
      return PortalCollisionHandler.getMovementForPushingEntityOutOfPortal(attemptedMove, portalState.position(), portalState.getNormal(), originalBoundingBox);
   }

   @Override
   public PortalShape cloneIfNecessary() {
      return this;
   }

   @Environment(EnvType.CLIENT)
   @Override
   public boolean canDoOuterFrustumCulling() {
      return true;
   }

   @Environment(EnvType.CLIENT)
   @Override
   public BoxPredicateF getInnerFrustumCullingFunc(Portal portal, Vec3d cameraPos) {
      return FrustumCuller.getFlatPortalInnerFrustumCullingFunc(portal, cameraPos);
   }

   @Override
   public BoxPredicateF getOuterFrustumCullingFunc(Portal portal, Vec3d cameraPos) {
      return FrustumCuller.getFlatPortalOuterFrustumCullingFunc(portal, cameraPos);
   }

   @Override
   public boolean shouldRenderInside(Portal portal, Box box) {
      Plane innerClipping = this.getInnerClipping(portal.getThisSideState(), portal.getOtherSideState(), portal);
      double testX = innerClipping.normal().x < 0.0 ? box.minX : box.maxX;
      double testY = innerClipping.normal().y < 0.0 ? box.minY : box.maxY;
      double testZ = innerClipping.normal().z < 0.0 ? box.minZ : box.maxZ;
      return innerClipping.isPointOnPositiveSide(new Vec3d(testX, testY, testZ));
   }

   @Override
   public VoxelShape getThisSideCollisionExclusion(UnilateralPortalState portalState) {
      Box thinAreaBox = this.getBoundingBox(portalState, true, 0.0);
      Vec3d reaching = portalState.getNormal().multiply(-10.0);
      Box exclusion = thinAreaBox.union(thinAreaBox.offset(reaching));
      return VoxelShapes.cuboid(exclusion);
   }

   @Nullable
   @Override
   public Box transformEntityActiveCollisionBox(Portal portal, Box box, Entity entity) {
      return CollisionHelper.clipBox(box, portal.getOriginPos(), portal.getNormal());
   }
}
