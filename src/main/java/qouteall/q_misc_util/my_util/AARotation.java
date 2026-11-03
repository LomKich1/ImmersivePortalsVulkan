package qouteall.q_misc_util.my_util;

import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.Comparator;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.BlockRotation;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import qouteall.q_misc_util.Helper;

public enum AARotation {
   SOUTH_ROT0(Direction.field_11035, Direction.field_11034),
   SOUTH_ROT90(Direction.field_11035, Direction.field_11036),
   SOUTH_ROT180(Direction.field_11035, Direction.field_11039),
   SOUTH_ROT270(Direction.field_11035, Direction.field_11033),
   NORTH_ROT0(Direction.field_11043, Direction.field_11039),
   NORTH_ROT90(Direction.field_11043, Direction.field_11036),
   NORTH_ROT180(Direction.field_11043, Direction.field_11034),
   NORTH_ROT270(Direction.field_11043, Direction.field_11033),
   EAST_ROT0(Direction.field_11034, Direction.field_11043),
   EAST_ROT90(Direction.field_11034, Direction.field_11036),
   EAST_ROT180(Direction.field_11034, Direction.field_11035),
   EAST_ROT270(Direction.field_11034, Direction.field_11033),
   WEST_ROT0(Direction.field_11039, Direction.field_11035),
   WEST_ROT90(Direction.field_11039, Direction.field_11036),
   WEST_ROT180(Direction.field_11039, Direction.field_11043),
   WEST_ROT270(Direction.field_11039, Direction.field_11033),
   UP_ROT0(Direction.field_11036, Direction.field_11043),
   UP_ROT90(Direction.field_11036, Direction.field_11039),
   UP_ROT180(Direction.field_11036, Direction.field_11035),
   UP_ROT270(Direction.field_11036, Direction.field_11034),
   DOWN_ROT0(Direction.field_11033, Direction.field_11035),
   DOWN_ROT90(Direction.field_11033, Direction.field_11039),
   DOWN_ROT180(Direction.field_11033, Direction.field_11043),
   DOWN_ROT270(Direction.field_11033, Direction.field_11034);

   public static final AARotation IDENTITY = SOUTH_ROT0;
   public final Direction transformedX;
   public final Direction transformedY;
   public final Direction transformedZ;
   public final IntMatrix3 matrix;
   public final DQuaternion quaternion;
   private static final AARotation[][] multiplicationCache = new AARotation[24][24];
   private static final AARotation[] inverseCache;
   public static final ImmutableList<AARotation> rotationsSortedByAngle;

   private AARotation(Direction transformedZ, Direction transformedX) {
      this.transformedZ = transformedZ;
      this.transformedX = transformedX;
      this.transformedY = dirCrossProduct(transformedZ, transformedX);
      this.matrix = new IntMatrix3(this.transformedX.getVector(), this.transformedY.getVector(), this.transformedZ.getVector());
      this.quaternion = this.matrix.toQuaternion();
   }

   public BlockPos transform(Vec3i vec) {
      return this.matrix.transform(vec);
   }

   public Direction transformDirection(Direction direction) {
      BlockPos transformedVec = this.transform(direction.getVector());
      return Helper.directionFromDelta(transformedVec.getX(), transformedVec.getY(), transformedVec.getZ());
   }

   @NotNull
   public static Direction dirCrossProduct(Direction a, Direction b) {
      Validate.isTrue(a.getAxis() != b.getAxis());
      Direction result = Helper.directionFromDelta(
         a.getOffsetY() * b.getOffsetZ() - a.getOffsetZ() * b.getOffsetY(),
         a.getOffsetZ() * b.getOffsetX() - a.getOffsetX() * b.getOffsetZ(),
         a.getOffsetX() * b.getOffsetY() - a.getOffsetY() * b.getOffsetX()
      );
      Validate.notNull(result);
      return result;
   }

   public static Direction rotateDir90DegreesAlong(Direction direction, Direction axis) {
      return direction.getAxis() == axis.getAxis() ? direction : dirCrossProduct(axis, direction);
   }

   public AARotation multiply(AARotation other) {
      return multiplicationCache[this.ordinal()][other.ordinal()];
   }

   private AARotation rawMultiply(AARotation other) {
      return getAARotationFromZX(this.transformDirection(other.transformedZ), this.transformDirection(other.transformedX));
   }

   public static AARotation getAARotationFromZX(Direction transformedZ, Direction transformedX) {
      for (AARotation value : values()) {
         if (value.transformedZ == transformedZ && value.transformedX == transformedX) {
            return value;
         }
      }

      throw new IllegalArgumentException();
   }

   public static AARotation getAARotationFromYZ(Direction transformedY, Direction transformedZ) {
      for (AARotation value : values()) {
         if (value.transformedY == transformedY && value.transformedZ == transformedZ) {
            return value;
         }
      }

      throw new IllegalArgumentException();
   }

   public static AARotation getAARotationFromXY(Direction transformedX, Direction transformedY) {
      for (AARotation value : values()) {
         if (value.transformedX == transformedX && value.transformedY == transformedY) {
            return value;
         }
      }

      throw new IllegalArgumentException();
   }

   public AARotation getInverse() {
      return inverseCache[this.ordinal()];
   }

   public static AARotation get90DegreesRotationAlong(Direction direction) {
      Direction defaultX = Direction.field_11034;
      Direction defaultY = Direction.field_11036;
      Direction newX = rotateDir90DegreesAlong(defaultX, direction);
      Direction newY = rotateDir90DegreesAlong(defaultY, direction);
      return getAARotationFromXY(newX, newY);
   }

   @Nullable
   public BlockRotation toVanillaRotation() {
      return switch (this) {
         case SOUTH_ROT0 -> BlockRotation.field_11467;
         case NORTH_ROT0 -> BlockRotation.field_11464;
         case EAST_ROT0 -> BlockRotation.field_11465;
         case WEST_ROT0 -> BlockRotation.field_11463;
         default -> null;
      };
   }

   public static AARotation fromVanillaRotation(BlockRotation vanillaRotation) {
      return switch (vanillaRotation) {
         case field_11467 -> SOUTH_ROT0;
         case field_11463 -> WEST_ROT0;
         case field_11464 -> NORTH_ROT0;
         case field_11465 -> EAST_ROT0;
         default -> throw new MatchException(null, null);
      };
   }

   static {
      for (AARotation a : values()) {
         for (AARotation b : values()) {
            multiplicationCache[a.ordinal()][b.ordinal()] = a.rawMultiply(b);
         }
      }

      inverseCache = new AARotation[24];
      AARotation[] values = values();

      for (int i = 0; i < values.length; i++) {
         AARotation rot = values[i];
         AARotation inverse = Arrays.stream(values()).filter(bx -> rot.multiply(bx) == IDENTITY).findFirst().orElseThrow();
         inverseCache[i] = inverse;
      }

      values = Arrays.copyOf(values(), 24);
      Arrays.sort(values, Comparator.comparingDouble(r -> r.quaternion.getRotatingAngleDegrees()));
      rotationsSortedByAngle = ImmutableList.copyOf(values);
   }
}
