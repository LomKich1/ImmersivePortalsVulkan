package qouteall.imm_ptl.core.portal;

import net.minecraft.world.World;
import net.minecraft.util.math.Vec3d;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.MathHelper;
import net.minecraft.registry.RegistryKey;
import org.apache.commons.lang3.Validate;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.portal.animation.UnilateralPortalState;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.my_util.DQuaternion;

public class PortalState {
   public final RegistryKey<World> fromWorld;
   public final Vec3d fromPos;
   public final RegistryKey<World> toWorld;
   public final Vec3d toPos;
   public final double scaling;
   public final DQuaternion rotation;
   public final DQuaternion orientation;
   public final double width;
   public final double height;
   public final double thickness;
   public final boolean isMirror;

   public PortalState(
      RegistryKey<World> fromWorld,
      Vec3d fromPos,
      RegistryKey<World> toWorld,
      Vec3d toPos,
      double scaling,
      DQuaternion rotation,
      DQuaternion orientation,
      double width,
      double height
   ) {
      this.fromWorld = fromWorld;
      this.fromPos = fromPos;
      this.toWorld = toWorld;
      this.toPos = toPos;
      this.scaling = scaling;
      this.rotation = rotation;
      this.orientation = orientation;
      this.width = width;
      this.height = height;
      this.thickness = 0.0;
      this.isMirror = false;
   }

   public PortalState(
      RegistryKey<World> fromWorld,
      Vec3d fromPos,
      RegistryKey<World> toWorld,
      Vec3d toPos,
      double scaling,
      DQuaternion rotation,
      DQuaternion orientation,
      double width,
      double height,
      double thickness,
      boolean isMirror
   ) {
      this.fromWorld = fromWorld;
      this.fromPos = fromPos;
      this.toWorld = toWorld;
      this.toPos = toPos;
      this.scaling = scaling;
      this.rotation = rotation;
      this.orientation = orientation;
      this.width = width;
      this.height = height;
      this.thickness = thickness;
      this.isMirror = isMirror;
   }

   public PortalState withThisSideUpdated(UnilateralPortalState thisSide, boolean lockScale) {
      UnilateralPortalState otherSide = UnilateralPortalState.extractOtherSide(this);
      if (lockScale) {
         otherSide = new UnilateralPortalState.Builder()
            .from(otherSide)
            .width(thisSide.width() * this.scaling)
            .height(thisSide.height() * this.scaling)
            .thickness(thisSide.thickness() * this.scaling)
            .build();
      }

      return UnilateralPortalState.combine(thisSide, otherSide);
   }

   public NbtCompound toTag() {
      NbtCompound tag = new NbtCompound();
      tag.putString("fromWorld", this.fromWorld.getValue().toString());
      tag.putString("toWorld", this.toWorld.getValue().toString());
      Helper.putVec3d(tag, "fromPos", this.fromPos);
      Helper.putVec3d(tag, "toPos", this.toPos);
      tag.putDouble("scaling", this.scaling);
      tag.putDouble("width", this.width);
      tag.putDouble("height", this.height);
      tag.putDouble("thickness", this.thickness);
      tag.put("rotation", this.rotation.toTag());
      tag.put("orientation", this.orientation.toTag());
      tag.putBoolean("isMirror", this.isMirror);
      return tag;
   }

   public static PortalState fromTag(NbtCompound tag) {
      RegistryKey<World> fromWorld = Helper.dimIdToKey(tag.getString("fromWorld", ""));
      RegistryKey<World> toWorld = Helper.dimIdToKey(tag.getString("toWorld", ""));
      Vec3d fromPos = Helper.getVec3d(tag, "fromPos");
      Vec3d toPos = Helper.getVec3d(tag, "toPos");
      double scaling = tag.getDouble("scaling", 0.0);
      double width = tag.getDouble("width", 0.0);
      double height = tag.getDouble("height", 0.0);
      double thickness = tag.getDouble("thickness", 0.0);
      DQuaternion rotation = DQuaternion.fromTag(tag.getCompoundOrEmpty("rotation"));
      DQuaternion orientation = DQuaternion.fromTag(tag.getCompoundOrEmpty("orientation"));
      boolean isMirror = tag.getBoolean("isMirror", false);
      return new PortalState(fromWorld, fromPos, toWorld, toPos, scaling, rotation, orientation, width, height, thickness, isMirror);
   }

   public static PortalState interpolate(PortalState a, PortalState b, double progress, boolean inverseScale) {
      Validate.isTrue(a.fromWorld == b.fromWorld);
      Validate.isTrue(a.toWorld == b.toWorld);
      return new PortalState(
         a.fromWorld,
         Helper.interpolatePos(a.fromPos, b.fromPos, progress),
         a.toWorld,
         Helper.interpolatePos(a.toPos, b.toPos, progress),
         interpolateScale(a, b, progress, inverseScale),
         DQuaternion.interpolate(a.rotation, b.rotation, progress),
         DQuaternion.interpolate(a.orientation, b.orientation, progress),
         MathHelper.lerp(progress, a.width, b.width),
         MathHelper.lerp(progress, a.height, b.height),
         MathHelper.lerp(progress, a.thickness, b.thickness),
         a.isMirror
      );
   }

   private static double interpolateScale(PortalState a, PortalState b, double progress, boolean inverseScale) {
      return inverseScale ? 1.0 / MathHelper.lerp(progress, 1.0 / a.scaling, 1.0 / b.scaling) : MathHelper.lerp(progress, a.scaling, b.scaling);
   }

   public Vec3d getPointOnSurface(double localX, double localY) {
      Vec3d axisW = McHelper.getAxisWFromOrientation(this.orientation);
      Vec3d axisH = McHelper.getAxisHFromOrientation(this.orientation);
      return axisW.multiply(localX).add(axisH.multiply(localY)).add(this.fromPos);
   }

   public Vec3d transformPoint(Vec3d pos) {
      Vec3d offset = pos.subtract(this.fromPos);
      Vec3d offsetTransformed = this.transformVec(offset);
      return offsetTransformed.add(this.toPos);
   }

   public Vec3d transformVec(Vec3d vec) {
      Vec3d rotated = this.rotation.rotate(vec);
      Vec3d scaled = rotated.multiply(this.scaling);
      return this.isMirror ? Mirror.mirroredVec(scaled, this.getNormal()) : scaled;
   }

   public Vec3d worldPosToPortalLocalPos(Vec3d worldPos) {
      Vec3d axisW = McHelper.getAxisWFromOrientation(this.orientation);
      Vec3d axisH = McHelper.getAxisHFromOrientation(this.orientation);
      Vec3d origin = this.fromPos;
      Vec3d offset = worldPos.subtract(origin);
      return new Vec3d(offset.dotProduct(axisW), offset.dotProduct(axisH), offset.dotProduct(axisW.crossProduct(axisH)));
   }

   public Vec3d portalLocalPosToWorldPos(Vec3d portalLocalPos) {
      Vec3d axisW = McHelper.getAxisWFromOrientation(this.orientation);
      Vec3d axisH = McHelper.getAxisHFromOrientation(this.orientation);
      Vec3d origin = this.fromPos;
      return origin.add(axisW.multiply(portalLocalPos.x))
         .add(axisH.multiply(portalLocalPos.y))
         .add(axisW.crossProduct(axisH).multiply(portalLocalPos.z));
   }

   public Vec3d getLocalPosTransformed(double collisionLocalX, double collisionLocalY) {
      return this.transformPoint(this.portalLocalPosToWorldPos(new Vec3d(collisionLocalX, collisionLocalY, 0.0)));
   }

   public Vec3d getNormal() {
      return McHelper.getNormalFromOrientation(this.orientation);
   }

   public Vec3d getContentDirection() {
      return this.rotation.rotate(this.getNormal().multiply(-1.0));
   }

   public UnilateralPortalState getThisSideState() {
      return UnilateralPortalState.extractThisSide(this);
   }

   public UnilateralPortalState getOtherSideState() {
      return UnilateralPortalState.extractOtherSide(this);
   }
}
