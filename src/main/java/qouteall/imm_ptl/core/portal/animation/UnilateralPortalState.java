package qouteall.imm_ptl.core.portal.animation;

import com.mojang.datafixers.util.Pair;
import java.util.Arrays;
import java.util.Comparator;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix3d;
import org.joml.Matrix3dc;
import org.joml.Vector3d;
import qouteall.imm_ptl.core.portal.PortalManipulation;
import qouteall.imm_ptl.core.portal.PortalState;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.my_util.DQuaternion;
import qouteall.q_misc_util.my_util.animation.Animated;

public record UnilateralPortalState(
   RegistryKey<World> dimension,
   Vec3d position,
   DQuaternion orientation,
   double width,
   double height,
   double thickness,
   Matrix3dc orientationMatrix,
   Matrix3dc orientationMatrixReverse
) {
   public static final Animated.TypeInfo<UnilateralPortalState> ANIMATION_TYPE_INFO = new Animated.TypeInfo<UnilateralPortalState>() {
      public UnilateralPortalState interpolate(UnilateralPortalState start, UnilateralPortalState end, double progress) {
         if (start.dimension() != end.dimension()) {
            return end;
         } else {
            Pair<UnilateralPortalState.RectInvariant, UnilateralPortalState> p = start.turnToClosestTo(end.orientation());
            start = (UnilateralPortalState)p.getSecond();
            return new UnilateralPortalState(
               start.dimension(),
               start.position().lerp(end.position(), progress),
               DQuaternion.interpolate(start.orientation(), end.orientation(), progress),
               MathHelper.lerp(progress, start.width(), end.width()),
               MathHelper.lerp(progress, start.height(), end.height()),
               MathHelper.lerp(progress, start.thickness(), end.thickness())
            );
         }
      }

      public boolean isClose(UnilateralPortalState a, UnilateralPortalState b) {
         return a.dimension() == b.dimension()
            && a.position().squaredDistanceTo(b.position()) < 1.0E-4
            && DQuaternion.isClose(a.orientation(), b.orientation())
            && Math.abs(a.width() - b.width()) < 0.001
            && Math.abs(a.height() - b.height()) < 0.001
            && Math.abs(a.thickness() - b.thickness()) < 0.001;
      }
   };

   public UnilateralPortalState(RegistryKey<World> dimension, Vec3d position, DQuaternion orientation, double width, double height) {
      this(dimension, position, orientation, width, height, 0.0);
   }

   public UnilateralPortalState(RegistryKey<World> dimension, Vec3d position, DQuaternion orientation, double width, double height, double thickness) {
      this(
         dimension,
         position,
         orientation,
         width,
         height,
         thickness,
         new Matrix3d().set(orientation.toMcQuaternion()),
         new Matrix3d().set(orientation.toMcQuaternion().conjugate())
      );
   }

   public static UnilateralPortalState extractThisSide(PortalState portalState) {
      return new UnilateralPortalState(
         portalState.fromWorld, portalState.fromPos, portalState.orientation, portalState.width, portalState.height, portalState.thickness
      );
   }

   public static UnilateralPortalState extractOtherSide(PortalState portalState) {
      DQuaternion otherSideOrientation = portalState.rotation
         .hamiltonProduct(portalState.orientation)
         .hamiltonProduct(portalState.isMirror ? DQuaternion.identity : PortalManipulation.flipAxisW);
      return new UnilateralPortalState(
         portalState.toWorld,
         portalState.toPos,
         otherSideOrientation,
         portalState.width * portalState.scaling,
         portalState.height * portalState.scaling,
         portalState.thickness * portalState.scaling
      );
   }

   public static PortalState combine(UnilateralPortalState thisSide, UnilateralPortalState otherSide) {
      DQuaternion otherSideOrientation = otherSide.orientation;
      DQuaternion thisSideOrientation = thisSide.orientation;
      DQuaternion rotation = PortalManipulation.computeDeltaTransformation(thisSideOrientation, otherSideOrientation);
      double scale = otherSide.width / thisSide.width;
      return new PortalState(
         thisSide.dimension,
         thisSide.position,
         otherSide.dimension,
         otherSide.position,
         scale,
         rotation,
         thisSide.orientation,
         thisSide.width,
         thisSide.height,
         thisSide.thickness,
         false
      );
   }

   public static UnilateralPortalState interpolate(UnilateralPortalState from, UnilateralPortalState to, double progress) {
      return new UnilateralPortalState(
         from.dimension,
         Helper.interpolatePos(from.position, to.position, progress),
         DQuaternion.interpolate(from.orientation, to.orientation, progress),
         MathHelper.lerp(progress, from.width, to.width),
         MathHelper.lerp(progress, from.height, to.height),
         MathHelper.lerp(progress, from.thickness, to.thickness)
      );
   }

   public NbtCompound toTag() {
      NbtCompound tag = new NbtCompound();
      tag.putString("dimension", this.dimension.getValue().toString());
      Helper.putVec3d(tag, "position", this.position);
      tag.put("orientation", this.orientation.toTag());
      tag.putDouble("width", this.width);
      tag.putDouble("height", this.height);
      tag.putDouble("thickness", this.thickness);
      return tag;
   }

   public static UnilateralPortalState fromTag(NbtCompound tag) {
      RegistryKey<World> dimension = Helper.dimIdToKey(tag.getString("dimension", ""));
      Vec3d point = Helper.getVec3d(tag, "position");
      DQuaternion orientation = DQuaternion.fromTag(tag.getCompoundOrEmpty("orientation"));
      double width = tag.getDouble("width", 0.0);
      double height = tag.getDouble("height", 0.0);
      double thickness = tag.getDouble("thickness", 0.0);
      return new UnilateralPortalState(dimension, point, orientation, width, height, thickness);
   }

   public DeltaUnilateralPortalState subtract(UnilateralPortalState other) {
      return DeltaUnilateralPortalState.fromDiff(other, this);
   }

   public UnilateralPortalState apply(DeltaUnilateralPortalState thisSideDelta) {
      return new UnilateralPortalState.Builder().from(this).apply(thisSideDelta).build();
   }

   public Vec3d getAxisW() {
      return new Vec3d(this.orientationMatrix.m00(), this.orientationMatrix.m01(), this.orientationMatrix.m02());
   }

   public Vec3d getAxisH() {
      return new Vec3d(this.orientationMatrix.m10(), this.orientationMatrix.m11(), this.orientationMatrix.m12());
   }

   public Vec3d getNormal() {
      return new Vec3d(this.orientationMatrix.m20(), this.orientationMatrix.m21(), this.orientationMatrix.m22());
   }

   public Vec3d pointOnPlane(double x, double y) {
      return this.transformLocalToGlobal(x, y, 0.0);
   }

   public Vec3d transformLocalToGlobal(double x, double y, double z) {
      Vector3d v = new Vector3d(x, y, z);
      this.orientationMatrix.transform(v);
      v.add(this.position.getX(), this.position.getY(), this.position.getZ());
      return new Vec3d(v.x, v.y, v.z);
   }

   public Vec3d transformLocalToGlobal(Vec3d vec3) {
      return this.transformLocalToGlobal(vec3.x, vec3.y, vec3.z);
   }

   public Vec3d transformGlobalToLocal(double x, double y, double z) {
      Vector3d v = new Vector3d(x, y, z);
      v.sub(this.position.getX(), this.position.getY(), this.position.getZ());
      this.orientationMatrixReverse.transform(v);
      return new Vec3d(v.x, v.y, v.z);
   }

   public Vec3d transformGlobalToLocal(Vec3d vec3) {
      return this.transformGlobalToLocal(vec3.x, vec3.y, vec3.z);
   }

   public Vec3d transformVecLocalToGlobal(Vec3d vec3) {
      Vector3d v = new Vector3d(vec3.x, vec3.y, vec3.z);
      this.orientationMatrix.transform(v);
      return new Vec3d(v.x, v.y, v.z);
   }

   public Vec3d transformVecGlobalToLocal(Vec3d vec3) {
      Vector3d v = new Vector3d(vec3.x, vec3.y, vec3.z);
      this.orientationMatrixReverse.transform(v);
      return new Vec3d(v.x, v.y, v.z);
   }

   public Pair<UnilateralPortalState.RectInvariant, UnilateralPortalState> turnToClosestTo(DQuaternion targetOrientation) {
      return Arrays.stream(UnilateralPortalState.RectInvariant.values())
         .map(inv -> Pair.of(inv, inv.getVariantOf(this)))
         .min(Comparator.comparingDouble(p -> DQuaternion.distanceSq(((UnilateralPortalState)p.getSecond()).orientation(), targetOrientation)))
         .orElseThrow();
   }

   public static class Builder {
      public RegistryKey<World> dimension;
      public Vec3d position;
      public DQuaternion orientation;
      public double width;
      public double height;
      public double thickness;

      public UnilateralPortalState build() {
         Validate.notNull(this.dimension, "dimension is null", new Object[0]);
         Validate.notNull(this.position, "position is null", new Object[0]);
         Validate.notNull(this.orientation, "orientation is null", new Object[0]);
         Validate.isTrue(this.width > 0.0, "invalid width %s", this.width);
         Validate.isTrue(this.height > 0.0, "invalid height %s", this.height);
         return new UnilateralPortalState(this.dimension, this.position, this.orientation, this.width, this.height, this.thickness);
      }

      public UnilateralPortalState.Builder dimension(RegistryKey<World> dimension) {
         this.dimension = dimension;
         return this;
      }

      public UnilateralPortalState.Builder position(Vec3d point) {
         this.position = point;
         return this;
      }

      public UnilateralPortalState.Builder orientation(DQuaternion orientation) {
         this.orientation = orientation;
         return this;
      }

      public UnilateralPortalState.Builder width(double width) {
         this.width = width;
         return this;
      }

      public UnilateralPortalState.Builder height(double height) {
         this.height = height;
         return this;
      }

      public UnilateralPortalState.Builder thickness(double thickness) {
         this.thickness = thickness;
         return this;
      }

      @NotNull
      public UnilateralPortalState.Builder from(UnilateralPortalState other) {
         this.dimension = other.dimension;
         this.position = other.position;
         this.orientation = other.orientation;
         this.width = other.width;
         this.height = other.height;
         this.thickness = other.thickness;
         return this;
      }

      public UnilateralPortalState.Builder offset(Vec3d offset) {
         this.position = this.position.add(offset);
         return this;
      }

      public UnilateralPortalState.Builder rotate(DQuaternion rotation) {
         this.orientation = rotation.hamiltonProduct(this.orientation);
         return this;
      }

      public UnilateralPortalState.Builder scaleWidth(double scale) {
         this.width *= scale;
         return this;
      }

      public UnilateralPortalState.Builder scaleHeight(double scale) {
         this.height *= scale;
         return this;
      }

      public UnilateralPortalState.Builder scaleThickness(double scale) {
         this.thickness *= scale;
         return this;
      }

      public UnilateralPortalState.Builder apply(DeltaUnilateralPortalState delta) {
         if (delta.offset() != null) {
            this.position = this.position.add(delta.offset());
         }

         if (delta.rotation() != null) {
            this.orientation = delta.rotation().hamiltonProduct(this.orientation);
         }

         if (delta.sizeScaling() != null) {
            this.width = this.width * delta.sizeScaling().getX();
            this.height = this.height * delta.sizeScaling().getY();
            this.thickness = this.thickness * delta.sizeScaling().getZ();
         }

         return this;
      }
   }

   public static enum RectInvariant {
      IDENTITY,
      ROTATE_90,
      ROTATE_180,
      ROTATE_270,
      FLIP_X,
      FLIP_X_ROTATE_90,
      FLIP_X_ROTATE_180,
      FLIP_X_ROTATE_270;

      public UnilateralPortalState getVariantOf(UnilateralPortalState state) {
         return switch (this) {
            case IDENTITY -> state;
            case ROTATE_90 -> new UnilateralPortalState(
               state.dimension,
               state.position,
               state.orientation.hamiltonProduct(DQuaternion.rotationByDegrees(new Vec3d(0.0, 0.0, 1.0), 90.0)),
               state.height,
               state.width
            );
            case ROTATE_180 -> new UnilateralPortalState(
               state.dimension,
               state.position,
               state.orientation.hamiltonProduct(DQuaternion.rotationByDegrees(new Vec3d(0.0, 0.0, 1.0), 180.0)),
               state.width,
               state.height
            );
            case ROTATE_270 -> new UnilateralPortalState(
               state.dimension,
               state.position,
               state.orientation.hamiltonProduct(DQuaternion.rotationByDegrees(new Vec3d(0.0, 0.0, 1.0), 270.0)),
               state.height,
               state.width
            );
            case FLIP_X -> new UnilateralPortalState(
               state.dimension,
               state.position,
               state.orientation.hamiltonProduct(DQuaternion.rotationByDegrees(new Vec3d(1.0, 0.0, 0.0), 180.0)),
               state.width,
               state.height
            );
            case FLIP_X_ROTATE_90 -> new UnilateralPortalState(
               state.dimension,
               state.position,
               state.orientation
                  .hamiltonProduct(DQuaternion.rotationByDegrees(new Vec3d(0.0, 0.0, 1.0), 90.0))
                  .hamiltonProduct(DQuaternion.rotationByDegrees(new Vec3d(1.0, 0.0, 0.0), 180.0)),
               state.height,
               state.width
            );
            case FLIP_X_ROTATE_180 -> new UnilateralPortalState(
               state.dimension,
               state.position,
               state.orientation
                  .hamiltonProduct(DQuaternion.rotationByDegrees(new Vec3d(0.0, 0.0, 1.0), 180.0))
                  .hamiltonProduct(DQuaternion.rotationByDegrees(new Vec3d(1.0, 0.0, 0.0), 180.0)),
               state.width,
               state.height
            );
            case FLIP_X_ROTATE_270 -> new UnilateralPortalState(
               state.dimension,
               state.position,
               state.orientation
                  .hamiltonProduct(DQuaternion.rotationByDegrees(new Vec3d(0.0, 0.0, 1.0), 270.0))
                  .hamiltonProduct(DQuaternion.rotationByDegrees(new Vec3d(1.0, 0.0, 0.0), 180.0)),
               state.height,
               state.width
            );
         };
      }

      public boolean switchesWidthAndHeight() {
         return switch (this) {
            case IDENTITY, ROTATE_180, FLIP_X, FLIP_X_ROTATE_180 -> false;
            case ROTATE_90, ROTATE_270, FLIP_X_ROTATE_90, FLIP_X_ROTATE_270 -> true;
         };
      }
   }
}
