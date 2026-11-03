package qouteall.q_misc_util.my_util;

import java.util.Objects;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.DirectionTransformation;
import net.minecraft.util.math.Direction.class_2351;
import net.minecraft.util.math.Direction.class_2352;
import org.joml.Matrix3f;
import qouteall.q_misc_util.Helper;

public class IntMatrix3 {
   public final Vec3i x;
   public final Vec3i y;
   public final Vec3i z;

   public IntMatrix3(Vec3i x, Vec3i y, Vec3i z) {
      this.x = x;
      this.y = y;
      this.z = z;
   }

   public IntMatrix3(DirectionTransformation t) {
      Direction d1 = t.map(Direction.from(class_2351.field_11048, class_2352.field_11056));
      Direction d2 = t.map(Direction.from(class_2351.field_11052, class_2352.field_11056));
      Direction d3 = t.map(Direction.from(class_2351.field_11051, class_2352.field_11056));
      this.x = d1.getVector();
      this.y = d2.getVector();
      this.z = d3.getVector();
   }

   public BlockPos transform(Vec3i p) {
      return Helper.scale(this.x, p.getX()).method_10081(Helper.scale(this.y, p.getY())).method_10081(Helper.scale(this.z, p.getZ()));
   }

   public IntMatrix3 multiply(IntMatrix3 m) {
      return new IntMatrix3(m.transform(this.x), m.transform(this.y), m.transform(this.z));
   }

   public Direction transformDirection(Direction direction) {
      BlockPos vec = this.transform(direction.getVector());
      return Helper.directionFromDelta(vec.getX(), vec.getY(), vec.getZ());
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) {
         return true;
      } else if (o != null && this.getClass() == o.getClass()) {
         IntMatrix3 that = (IntMatrix3)o;
         return this.x.equals(that.x) && this.y.equals(that.y) && this.z.equals(that.z);
      } else {
         return false;
      }
   }

   @Override
   public int hashCode() {
      return Objects.hash(this.x, this.y, this.z);
   }

   public static IntMatrix3 getIdentity() {
      return new IntMatrix3(new BlockPos(1, 0, 0), new BlockPos(0, 1, 0), new BlockPos(0, 0, 1));
   }

   public Matrix3f toMatrix() {
      Matrix3f matrix = new Matrix3f();
      matrix.set(0, 0, this.x.getX());
      matrix.set(0, 1, this.x.getY());
      matrix.set(0, 2, this.x.getZ());
      matrix.set(1, 0, this.y.getX());
      matrix.set(1, 1, this.y.getY());
      matrix.set(1, 2, this.y.getZ());
      matrix.set(2, 0, this.z.getX());
      matrix.set(2, 1, this.z.getY());
      matrix.set(2, 2, this.z.getZ());
      return matrix;
   }

   public DQuaternion toQuaternion() {
      return DQuaternion.matrixToQuaternion(Vec3d.of(this.x), Vec3d.of(this.y), Vec3d.of(this.z));
   }
}
