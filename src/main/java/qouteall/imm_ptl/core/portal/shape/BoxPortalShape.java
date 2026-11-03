package qouteall.imm_ptl.core.portal.shape;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.collision.CollisionHelper;
import qouteall.imm_ptl.core.collision.PortalCollisionHandler;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.animation.UnilateralPortalState;
import qouteall.imm_ptl.core.render.ViewAreaRenderer;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.my_util.BoxPredicateF;
import qouteall.q_misc_util.my_util.IntBox;
import qouteall.q_misc_util.my_util.Plane;
import qouteall.q_misc_util.my_util.Range;
import qouteall.q_misc_util.my_util.RayTraceResult;
import qouteall.q_misc_util.my_util.TriangleConsumer;

public final class BoxPortalShape implements PortalShape {
   public static final BoxPortalShape FACING_OUTWARDS = new BoxPortalShape(true);
   public static final BoxPortalShape FACING_INWARDS = new BoxPortalShape(false);
   public final boolean facingOutwards;

   public static void init() {
      PortalShapeSerialization.addSerializer(
         new PortalShapeSerialization.Serializer<>("box", BoxPortalShape.class, BoxPortalShape::serialize, BoxPortalShape::deserialize)
      );
   }

   private static BoxPortalShape deserialize(NbtCompound tag) {
      boolean facingOutwards1 = tag.getBoolean("facingOutwards", false);
      return facingOutwards1 ? FACING_OUTWARDS : FACING_INWARDS;
   }

   public static NbtCompound serialize(BoxPortalShape boxShape) {
      NbtCompound compoundTag = new NbtCompound();
      compoundTag.putBoolean("facingOutwards", boxShape.facingOutwards);
      return compoundTag;
   }

   private BoxPortalShape(boolean facingOutwards) {
      this.facingOutwards = facingOutwards;
   }

   @Override
   public boolean isPlanar() {
      return false;
   }

   @Override
   public Box getBoundingBox(UnilateralPortalState portalState, boolean limitSize, double boxExpand) {
      double halfW = portalState.width() / 2.0 + boxExpand;
      double halfH = portalState.height() / 2.0 + boxExpand;
      double halfT = portalState.thickness() / 2.0 + boxExpand;
      if (limitSize) {
         halfW = Math.min(halfW, 32.0);
         halfH = Math.min(halfH, 32.0);
         halfT = Math.min(halfT, 32.0);
      }

      return Helper.boundingBoxOfPoints(
         new Vec3d[]{
            portalState.transformLocalToGlobal(-halfW, -halfH, -halfT),
            portalState.transformLocalToGlobal(-halfW, -halfH, halfT),
            portalState.transformLocalToGlobal(-halfW, halfH, -halfT),
            portalState.transformLocalToGlobal(-halfW, halfH, halfT),
            portalState.transformLocalToGlobal(halfW, -halfH, -halfT),
            portalState.transformLocalToGlobal(halfW, -halfH, halfT),
            portalState.transformLocalToGlobal(halfW, halfH, -halfT),
            portalState.transformLocalToGlobal(halfW, halfH, halfT)
         }
      );
   }

   @Override
   public double roughDistanceToPortalShape(UnilateralPortalState portalState, Vec3d pos) {
      Vec3d localPos = portalState.transformGlobalToLocal(pos);
      double dx = Helper.getDistanceToRange(-portalState.width() / 2.0, portalState.width() / 2.0, localPos.getX());
      double dy = Helper.getDistanceToRange(-portalState.height() / 2.0, portalState.height() / 2.0, localPos.getY());
      double dz = Helper.getDistanceToRange(-portalState.thickness() / 2.0, portalState.thickness() / 2.0, localPos.getZ());
      return Math.sqrt(dx * dx + dy * dy + dz * dz);
   }

   @Nullable
   @Override
   public RayTraceResult raytracePortalShapeByLocalPos(UnilateralPortalState portalState, Vec3d localFrom, Vec3d localTo, double leniency) {
      Vec3d lineVec = localTo.subtract(localFrom);
      return Helper.raytraceAABB(
         this.facingOutwards,
         -portalState.width() / 2.0,
         -portalState.height() / 2.0,
         -portalState.thickness() / 2.0,
         portalState.width() / 2.0,
         portalState.height() / 2.0,
         portalState.thickness() / 2.0,
         localFrom.getX(),
         localFrom.getY(),
         localFrom.getZ(),
         lineVec.getX(),
         lineVec.getY(),
         lineVec.getZ()
      );
   }

   @Nullable
   @Override
   public Plane getOuterClipping(UnilateralPortalState portalState) {
      return null;
   }

   @Nullable
   @Override
   public Plane getInnerClipping(UnilateralPortalState thisSideState, UnilateralPortalState otherSideState, Portal portal) {
      return null;
   }

   @Override
   public PortalShape getFlipped() {
      return this.facingOutwards ? FACING_INWARDS : FACING_OUTWARDS;
   }

   @Override
   public PortalShape getReverse() {
      return this.getFlipped();
   }

   @Override
   public boolean roughTestVisibility(UnilateralPortalState portalState, Vec3d cameraPos, boolean isIrisShaderOn) {
      if (isIrisShaderOn) {
         return true;
      } else {
         Vec3d localPos = portalState.transformGlobalToLocal(cameraPos);
         boolean in = localPos.getX() > -portalState.width() / 2.0
            && localPos.getX() < portalState.width() / 2.0
            && localPos.getY() > -portalState.height() / 2.0
            && localPos.getY() < portalState.height() / 2.0
            && localPos.getZ() > -portalState.thickness() / 2.0
            && localPos.getZ() < portalState.thickness() / 2.0;
         return this.facingOutwards ? !in : in;
      }
   }

   @Environment(EnvType.CLIENT)
   @Override
   public void renderViewAreaMesh(Vec3d portalOriginRelativeToCamera, UnilateralPortalState portalState, TriangleConsumer vertexOutput, boolean isGlobalPortal) {
      Vec3d localHX = portalState.getAxisW().multiply(portalState.width() / 2.0);
      Vec3d localHY = portalState.getAxisH().multiply(portalState.height() / 2.0);
      Vec3d localHZ = portalState.getNormal().multiply(portalState.thickness() / 2.0);
      ViewAreaRenderer.outputFullQuad(
         vertexOutput, portalOriginRelativeToCamera.add(localHX), this.facingOutwards ? localHY : localHZ, this.facingOutwards ? localHZ : localHY
      );
      ViewAreaRenderer.outputFullQuad(
         vertexOutput, portalOriginRelativeToCamera.subtract(localHX), this.facingOutwards ? localHZ : localHY, this.facingOutwards ? localHY : localHZ
      );
      ViewAreaRenderer.outputFullQuad(
         vertexOutput, portalOriginRelativeToCamera.add(localHY), this.facingOutwards ? localHZ : localHX, this.facingOutwards ? localHX : localHZ
      );
      ViewAreaRenderer.outputFullQuad(
         vertexOutput, portalOriginRelativeToCamera.subtract(localHY), this.facingOutwards ? localHX : localHZ, this.facingOutwards ? localHZ : localHX
      );
      ViewAreaRenderer.outputFullQuad(
         vertexOutput, portalOriginRelativeToCamera.add(localHZ), this.facingOutwards ? localHX : localHY, this.facingOutwards ? localHY : localHX
      );
      ViewAreaRenderer.outputFullQuad(
         vertexOutput, portalOriginRelativeToCamera.subtract(localHZ), this.facingOutwards ? localHY : localHX, this.facingOutwards ? localHX : localHY
      );
   }

   @Override
   public boolean canCollideWith(Portal portal, UnilateralPortalState portalState, Vec3d entityEyePos, Box entityBoundingBox) {
      Box expandedBox = this.getBoundingBox(portalState, false, 2.0);
      return expandedBox.intersects(entityBoundingBox);
   }

   @Override
   public boolean isLocalBoxInPortalProjection(UnilateralPortalState portalState, double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
      return Range.rangeIntersects(-portalState.width() / 2.0, portalState.width() / 2.0, minX, maxX)
         && Range.rangeIntersects(-portalState.height() / 2.0, portalState.height() / 2.0, minY, maxY)
         && Range.rangeIntersects(-portalState.thickness() / 2.0, portalState.thickness() / 2.0, minZ, maxZ);
   }

   @Override
   public Vec3d getMovementForPushingEntityOutOfPortal(Portal portal, UnilateralPortalState portalState, Entity entity, Vec3d attemptedMove) {
      Box entityLocalBox = Helper.transformBox(entity.getBoundingBox(), portalState::transformGlobalToLocal);
      Vec3d localMove = portalState.transformVecGlobalToLocal(attemptedMove);
      Box movedEntityLocalBox = entityLocalBox.offset(localMove);
      Box portalLocalBox = new Box(
         -portalState.width() / 2.0,
         -portalState.height() / 2.0,
         -portalState.thickness() / 2.0,
         portalState.width() / 2.0,
         portalState.height() / 2.0,
         portalState.thickness() / 2.0
      );
      Vec3d offset = this.facingOutwards
         ? PortalCollisionHandler.getOffsetForPushingBoxOutOfAABB(movedEntityLocalBox, portalLocalBox, localMove)
         : PortalCollisionHandler.getOffsetForConfiningBoxInsideAABB(movedEntityLocalBox, portalLocalBox);
      return portalState.transformVecLocalToGlobal(localMove.add(offset));
   }

   @Override
   public PortalShape cloneIfNecessary() {
      return this;
   }

   @Nullable
   @Override
   public Box transformEntityActiveCollisionBox(Portal portal, Box box, Entity entity) {
      Vec3d eyePos = entity.getCameraPosVec(1.0F);
      UnilateralPortalState thisSideState = portal.getThisSideState();
      Vec3d localEyePos = thisSideState.transformGlobalToLocal(eyePos);
      Box currBox = box;
      if (box != null && localEyePos.getX() < -thisSideState.width() / 2.0) {
         currBox = CollisionHelper.clipBox(
            box,
            thisSideState.transformLocalToGlobal(new Vec3d(-thisSideState.width() / 2.0, 0.0, 0.0)),
            thisSideState.transformVecLocalToGlobal(new Vec3d(-1.0, 0.0, 0.0))
         );
      }

      if (currBox != null && localEyePos.getX() > thisSideState.width() / 2.0) {
         currBox = CollisionHelper.clipBox(
            currBox,
            thisSideState.transformLocalToGlobal(new Vec3d(thisSideState.width() / 2.0, 0.0, 0.0)),
            thisSideState.transformVecLocalToGlobal(new Vec3d(1.0, 0.0, 0.0))
         );
      }

      if (currBox != null && localEyePos.getY() < -thisSideState.height() / 2.0) {
         currBox = CollisionHelper.clipBox(
            currBox,
            thisSideState.transformLocalToGlobal(new Vec3d(0.0, -thisSideState.height() / 2.0, 0.0)),
            thisSideState.transformVecLocalToGlobal(new Vec3d(0.0, -1.0, 0.0))
         );
      }

      if (currBox != null && localEyePos.getY() > thisSideState.height() / 2.0) {
         currBox = CollisionHelper.clipBox(
            currBox,
            thisSideState.transformLocalToGlobal(new Vec3d(0.0, thisSideState.height() / 2.0, 0.0)),
            thisSideState.transformVecLocalToGlobal(new Vec3d(0.0, 1.0, 0.0))
         );
      }

      if (currBox != null && localEyePos.getZ() < -thisSideState.thickness() / 2.0) {
         currBox = CollisionHelper.clipBox(
            currBox,
            thisSideState.transformLocalToGlobal(new Vec3d(0.0, 0.0, -thisSideState.thickness() / 2.0)),
            thisSideState.transformVecLocalToGlobal(new Vec3d(0.0, 0.0, -1.0))
         );
      }

      if (currBox != null && localEyePos.getZ() > thisSideState.thickness() / 2.0) {
         currBox = CollisionHelper.clipBox(
            currBox,
            thisSideState.transformLocalToGlobal(new Vec3d(0.0, 0.0, thisSideState.thickness() / 2.0)),
            thisSideState.transformVecLocalToGlobal(new Vec3d(0.0, 0.0, 1.0))
         );
      }

      return currBox;
   }

   @Nullable
   @Override
   public ChunkSectionPos getModifiedVisibleSectionIterationOrigin(Portal portal, Vec3d innerCameraPos) {
      if (!IPGlobal.boxPortalSpecialIteration) {
         return null;
      } else if (!this.facingOutwards) {
         return null;
      } else {
         BoxPortalShape.InnerSectionRange r = this.getInnerSectionRange(portal);
         ChunkSectionPos cameraPosSection = ChunkSectionPos.from(innerCameraPos);
         int secX = MathHelper.clamp(cameraPosSection.getSectionX(), r.l().getSectionX(), r.hInclusive().getSectionX());
         int secY = MathHelper.clamp(cameraPosSection.getSectionY(), r.l().getSectionY(), r.hInclusive().getSectionY());
         int secZ = MathHelper.clamp(cameraPosSection.getSectionZ(), r.l().getSectionZ(), r.hInclusive().getSectionZ());
         return ChunkSectionPos.from(secX, secY, secZ);
      }
   }

   @Override
   public boolean shouldRenderInside(Portal portal, Box box) {
      return true;
   }

   private BoxPortalShape.InnerSectionRange getInnerSectionRange(Portal portal) {
      Box otherSideBoundingBox = this.getReverse().getBoundingBox(portal.getOtherSideState(), false, 0.0);
      IntBox otherSideIntBox = IntBox.fromRealNumberBox(otherSideBoundingBox);
      ChunkSectionPos lSectionPos = ChunkSectionPos.from(otherSideIntBox.l);
      ChunkSectionPos hSectionPos = ChunkSectionPos.from(otherSideIntBox.h);
      return new BoxPortalShape.InnerSectionRange(lSectionPos, hSectionPos);
   }

   @Nullable
   @Override
   public BoxPredicateF getInnerFrustumCullingFunc(Portal portal, Vec3d cameraPos) {
      if (!IPGlobal.boxPortalSpecialIteration) {
         return null;
      } else if (!this.facingOutwards) {
         return null;
      } else {
         BoxPortalShape.InnerSectionRange innerSectionRange = this.getInnerSectionRange(portal);
         float lx = (float)(innerSectionRange.l.getSectionX() * 16 - cameraPos.x);
         float ly = (float)(innerSectionRange.l.getSectionY() * 16 - cameraPos.y);
         float lz = (float)(innerSectionRange.l.getSectionZ() * 16 - cameraPos.z);
         float hx = (float)((innerSectionRange.hInclusive.getSectionX() + 1) * 16 - cameraPos.x);
         float hy = (float)((innerSectionRange.hInclusive.getSectionY() + 1) * 16 - cameraPos.y);
         float hz = (float)((innerSectionRange.hInclusive.getSectionZ() + 1) * 16 - cameraPos.z);
         return (minX, minY, minZ, maxX, maxY, maxZ) -> {
            float midX = (minX + maxX) / 2.0F;
            float midY = (minY + maxY) / 2.0F;
            float midZ = (minZ + maxZ) / 2.0F;
            return !(midX > lx) || !(midX < hx) || !(midY > ly) || !(midY < hy) || !(midZ > lz) || !(midZ < hz);
         };
      }
   }

   private record InnerSectionRange(ChunkSectionPos l, ChunkSectionPos hInclusive) {
   }
}
