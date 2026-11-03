package qouteall.q_misc_util.my_util;

import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.Comparator;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3i;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import qouteall.q_misc_util.Helper;

public enum AARotation {
   SOUTH_ROT0(Direction.SOUTH, Direction.EAST),
   SOUTH_ROT90(Direction.SOUTH, Direction.UP),
   SOUTH_ROT180(Direction.SOUTH, Direction.WEST),
   SOUTH_ROT270(Direction.SOUTH, Direction.DOWN),
   NORTH_ROT0(Direction.NORTH, Direction.WEST),
   NORTH_ROT90(Direction.NORTH, Direction.UP),
   NORTH_ROT180(Direction.NORTH, Direction.EAST),
   NORTH_ROT270(Direction.NORTH, Direction.DOWN),
   EAST_ROT0(Direction.EAST, Direction.NORTH),
   EAST_ROT90(Direction.EAST, Direction.UP),
   EAST_ROT180(Direction.EAST, Direction.SOUTH),
   EAST_ROT270(Direction.EAST, Direction.DOWN),
   WEST_ROT0(Direction.WEST, Direction.SOUTH),
   WEST_ROT90(Direction.WEST, Direction.UP),
   WEST_ROT180(Direction.WEST, Direction.NORTH),
   WEST_ROT270(Direction.WEST, Direction.DOWN),
   UP_ROT0(Direction.UP, Direction.NORTH),
   UP_ROT90(Direction.UP, Direction.WEST),
   UP_ROT180(Direction.UP, Direction.SOUTH),
   UP_ROT270(Direction.UP, Direction.EAST),
   DOWN_ROT0(Direction.DOWN, Direction.SOUTH),
   DOWN_ROT90(Direction.DOWN, Direction.WEST),
   DOWN_ROT180(Direction.DOWN, Direction.NORTH),
   DOWN_ROT270(Direction.DOWN, Direction.EAST);

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
      Direction defaultX = Direction.EAST;
      Direction defaultY = Direction.UP;
      Direction newX = rotateDir90DegreesAlong(defaultX, direction);
      Direction newY = rotateDir90DegreesAlong(defaultY, direction);
      return getAARotationFromXY(newX, newY);
   }

   @Nullable
   public BlockRotation toVanillaRotation() {
      return switch (this) {
         case SOUTH_ROT0 -> BlockRotation.NONE;
         case NORTH_ROT0 -> BlockRotation.CLOCKWISE_180;
         case EAST_ROT0 -> BlockRotation.COUNTERCLOCKWISE_90;
         case WEST_ROT0 -> BlockRotation.CLOCKWISE_90;
         default -> null;
      };
   }

   public static AARotation fromVanillaRotation(BlockRotation vanillaRotation) {
      return switch (vanillaRotation) {
         case NONE -> SOUTH_ROT0;
         case CLOCKWISE_90 -> WEST_ROT0;
         case CLOCKWISE_180 -> NORTH_ROT0;
         case COUNTERCLOCKWISE_90 -> EAST_ROT0;
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
