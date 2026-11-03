package qouteall.q_misc_util.my_util;

import java.util.Objects;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.util.Pair;
import net.minecraft.util.math.Vec3d;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class DQuaternion {
   private static final Logger logger = LogManager.getLogger(DQuaternion.class);
   public final double x;
   public final double y;
   public final double z;
   public final double w;
   public static final DQuaternion identity = new DQuaternion(0.0, 0.0, 0.0, 1.0);

   public DQuaternion(double x, double y, double z, double w) {
      this.x = x;
      this.y = y;
      this.z = z;
      this.w = w;
   }

   public static DQuaternion fromMcQuaternion(Quaternionf quaternion) {
      return new DQuaternion(quaternion.x(), quaternion.y(), quaternion.z(), quaternion.w());
   }

   public static DQuaternion fromMcQuaternion(Quaterniond quaternion) {
      return new DQuaternion(quaternion.x(), quaternion.y(), quaternion.z(), quaternion.w());
   }

   public Vec3d getRotatingAxis() {
      return new Vec3d(this.x, this.y, this.z).normalize();
   }

   public double getRotatingAngleRadians() {
      return Math.acos(this.w) * 2.0;
   }

   public double getRotatingAngleDegrees() {
      return Math.toDegrees(this.getRotatingAngleRadians());
   }

   public Quaternionf toMcQuaternion() {
      return new Quaternionf((float)this.x, (float)this.y, (float)this.z, (float)this.w);
   }

   public Matrix4f toMatrix() {
      return new Matrix4f().set(this.toMcQuaternion());
   }

   public static DQuaternion rotationByDegrees(Vec3d rotatingAxis, double degrees) {
      return rotationByRadians(rotatingAxis, Math.toRadians(degrees));
   }

   public static DQuaternion rotationByRadians(Vec3d axis, double rotationAngle) {
      double s = Math.sin(rotationAngle / 2.0);
      Vec3d normalizedAxis = axis.normalize();
      return new DQuaternion(normalizedAxis.getX() * s, normalizedAxis.getY() * s, normalizedAxis.getZ() * s, Math.cos(rotationAngle / 2.0));
   }

   public Vec3d rotate(Vec3d vec) {
      DQuaternion result = this.hamiltonProduct(new DQuaternion(vec.x, vec.y, vec.z, 0.0)).hamiltonProduct(this.getConjugated());
      return new Vec3d(result.x, result.y, result.z);
   }

   public double getX() {
      return this.x;
   }

   public double getY() {
      return this.y;
   }

   public double getZ() {
      return this.z;
   }

   public double getW() {
      return this.w;
   }

   public DQuaternion hamiltonProduct(DQuaternion other) {
      double x1 = this.getX();
      double y1 = this.getY();
      double z1 = this.getZ();
      double w1 = this.getW();
      double x2 = other.getX();
      double y2 = other.getY();
      double z2 = other.getZ();
      double w2 = other.getW();
      return new DQuaternion(
         w1 * x2 + x1 * w2 + y1 * z2 - z1 * y2,
         w1 * y2 - x1 * z2 + y1 * w2 + z1 * x2,
         w1 * z2 + x1 * y2 - y1 * x2 + z1 * w2,
         w1 * w2 - x1 * x2 - y1 * y2 - z1 * z2
      );
   }

   public DQuaternion combine(DQuaternion other) {
      return other.hamiltonProduct(this);
   }

   public DQuaternion getConjugated() {
      return new DQuaternion(-this.x, -this.y, -this.z, this.w);
   }

   public DQuaternion multiply(double val) {
      return new DQuaternion(this.x * val, this.y * val, this.z * val, this.w * val);
   }

   public DQuaternion add(DQuaternion q) {
      return new DQuaternion(this.x + q.x, this.y + q.y, this.z + q.z, this.w + q.w);
   }

   public double dotProduct(DQuaternion q) {
      return this.getX() * q.getX() + this.getY() * q.getY() + this.getZ() * q.getZ() + this.getW() * q.getW();
   }

   public DQuaternion getNormalized() {
      double lenSq = this.dotProduct(this);
      if (lenSq != 0.0) {
         double len = Math.sqrt(lenSq);
         return this.multiply(1.0 / len);
      } else {
         logger.error("Normalizing zero-length quaternion", new Throwable());
         return identity;
      }
   }

   public static DQuaternion getCameraRotation(double pitch, double yaw) {
      DQuaternion r1 = rotationByDegrees(new Vec3d(1.0, 0.0, 0.0), pitch);
      DQuaternion r2 = rotationByDegrees(new Vec3d(0.0, 1.0, 0.0), yaw + 180.0);
      return r1.hamiltonProduct(r2);
   }

   public static DQuaternion getCameraRotation1(double pitch, double yaw) {
      double p = Math.toRadians(pitch) / 2.0;
      double y = Math.toRadians(yaw) / 2.0;
      return new DQuaternion(-Math.sin(p) * Math.sin(y), Math.cos(p) * Math.cos(y), Math.sin(p) * Math.cos(y), -Math.cos(p) * Math.sin(y));
   }

   public static boolean isClose(DQuaternion a, DQuaternion b, double valve) {
      return distanceSq(a, b) < valve;
   }

   public static boolean isClose(DQuaternion a, DQuaternion b) {
      return isClose(a, b, 1.0E-8);
   }

   public static double distanceSq(DQuaternion a, DQuaternion b) {
      double da1 = a.getX() - b.getX();
      double db1 = a.getY() - b.getY();
      double dc1 = a.getZ() - b.getZ();
      double dd1 = a.getW() - b.getW();
      double v1 = da1 * da1 + db1 * db1 + dc1 * dc1 + dd1 * dd1;
      double da2 = a.getX() + b.getX();
      double db2 = a.getY() + b.getY();
      double dc2 = a.getZ() + b.getZ();
      double dd2 = a.getW() + b.getW();
      double v2 = da2 * da2 + db2 * db2 + dc2 * dc2 + dd2 * dd2;
      return Math.min(v1, v2);
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) {
         return true;
      } else if (o != null && this.getClass() == o.getClass()) {
         DQuaternion that = (DQuaternion)o;
         return Double.compare(that.x, this.x) == 0
            && Double.compare(that.y, this.y) == 0
            && Double.compare(that.z, this.z) == 0
            && Double.compare(that.w, this.w) == 0;
      } else {
         return false;
      }
   }

   @Override
   public int hashCode() {
      return Objects.hash(this.x, this.y, this.z, this.w);
   }

   @Override
   public String toString() {
      Vec3d rotatingAxis = this.getRotatingAxis();
      return String.format(
         "Rotates %.3f degrees along (%.3f %.3f %.3f) Quaternion:(%.3f %.3f %.3f %.3f)",
         this.getRotatingAngleDegrees(),
         rotatingAxis.x,
         rotatingAxis.y,
         rotatingAxis.z,
         this.x,
         this.y,
         this.z,
         this.w
      );
   }

   public static DQuaternion interpolate(DQuaternion a, DQuaternion b, double t) {
      double dot = a.dotProduct(b);
      if (dot < 0.0) {
         a = a.multiply(-1.0);
         dot = -dot;
      }

      double DOT_THRESHOLD = 0.9995;
      if (dot > DOT_THRESHOLD) {
         return a.multiply(1.0 - t).add(b.multiply(t)).getNormalized();
      } else {
         double theta_0 = Math.acos(dot);
         double theta = theta_0 * t;
         double sin_theta = Math.sin(theta);
         double sin_theta_0 = Math.sin(theta_0);
         double s0 = Math.cos(theta) - dot * sin_theta / sin_theta_0;
         double s1 = sin_theta / sin_theta_0;
         return a.multiply(s0).add(b.multiply(s1));
      }
   }

   public static Pair<Double, Double> getPitchYawFromRotation(DQuaternion quaternion) {
      double x = quaternion.getX();
      double y = quaternion.getY();
      double z = quaternion.getZ();
      double w = quaternion.getW();
      double cosYaw = 2.0 * (y * y + z * z) - 1.0;
      double sinYaw = -(x * z + y * w) * 2.0;
      double cosPitch = 1.0 - 2.0 * (x * x + z * z);
      double sinPitch = (x * w + y * z) * 2.0;
      return new Pair(Math.toDegrees(Math.atan2(sinPitch, cosPitch)), Math.toDegrees(Math.atan2(sinYaw, cosYaw)));
   }

   public static DQuaternion matrixToQuaternion(Vec3d x, Vec3d y, Vec3d z) {
      double m00 = x.getX();
      double m11 = y.getY();
      double m22 = z.getZ();
      double m12 = z.getY();
      double m21 = y.getZ();
      double m20 = x.getZ();
      double m02 = z.getX();
      double m01 = y.getX();
      double m10 = x.getY();
      double tr = m00 + m11 + m22;
      double qx;
      double qy;
      double qz;
      double qw;
      if (tr > 0.0) {
         double S = Math.sqrt(tr + 1.0) * 2.0;
         qw = 0.25 * S;
         qx = (m21 - m12) / S;
         qy = (m02 - m20) / S;
         qz = (m10 - m01) / S;
      } else if (m00 > m11 && m00 > m22) {
         double S = Math.sqrt(1.0 + m00 - m11 - m22) * 2.0;
         qw = (m21 - m12) / S;
         qx = 0.25 * S;
         qy = (m01 + m10) / S;
         qz = (m02 + m20) / S;
      } else if (m11 > m22) {
         double S = Math.sqrt(1.0 + m11 - m00 - m22) * 2.0;
         qw = (m02 - m20) / S;
         qx = (m01 + m10) / S;
         qy = 0.25 * S;
         qz = (m12 + m21) / S;
      } else {
         double S = Math.sqrt(1.0 + m22 - m00 - m11) * 2.0;
         qw = (m10 - m01) / S;
         qx = (m02 + m20) / S;
         qy = (m12 + m21) / S;
         qz = 0.25 * S;
      }

      return new DQuaternion(qx, qy, qz, qw);
   }

   public static DQuaternion fromFacingVecs(Vec3d axisW, Vec3d axisH) {
      return matrixToQuaternion(axisW, axisH, axisW.crossProduct(axisH));
   }

   public static DQuaternion getRotationBetween(Vec3d from, Vec3d to) {
      from = from.normalize();
      to = to.normalize();
      Vec3d axis = from.crossProduct(to).normalize();
      double cos = from.dotProduct(to);
      double angle = Math.acos(cos);
      return rotationByRadians(axis, angle);
   }

   public NbtElement toTag() {
      NbtCompound tag = new NbtCompound();
      tag.putDouble("x", this.x);
      tag.putDouble("y", this.y);
      tag.putDouble("z", this.z);
      tag.putDouble("w", this.w);
      return tag;
   }

   public static DQuaternion fromTag(NbtElement tag) {
      if (tag instanceof NbtCompound compoundTag) {
         return !compoundTag.contains("x")
            ? identity
            : new DQuaternion(
               compoundTag.getDouble("x", 0.0), compoundTag.getDouble("y", 0.0), compoundTag.getDouble("z", 0.0), compoundTag.getDouble("w", 0.0)
            );
      } else {
         return identity;
      }
   }

   public DQuaternion fixFloatingPointErrorAccumulation() {
      DQuaternion quaternion = new DQuaternion(
         fixCoordinateFloatingPointError(this.getX()),
         fixCoordinateFloatingPointError(this.getY()),
         fixCoordinateFloatingPointError(this.getZ()),
         fixCoordinateFloatingPointError(this.getW())
      );
      return quaternion.getNormalized();
   }

   private static double fixCoordinateFloatingPointError(double num) {
      double threshold = 1.0E-7;
      if (Math.abs(num) < 1.0E-7) {
         return 0.0;
      } else if (Math.abs(num - 1.0) < 1.0E-7) {
         return 1.0;
      } else {
         return Math.abs(num - -1.0) < 1.0E-7 ? -1.0 : num;
      }
   }

   public boolean isAxisAligned() {
      return isCoordinateZeroOrOneOrNegativeOne(this.getX())
         && isCoordinateZeroOrOneOrNegativeOne(this.getY())
         && isCoordinateZeroOrOneOrNegativeOne(this.getZ())
         && isCoordinateZeroOrOneOrNegativeOne(this.getW());
   }

   private static boolean isCoordinateZeroOrOneOrNegativeOne(double coord) {
      return coord == 0.0 || coord == 1.0 || coord == -1.0;
   }

   public static DQuaternion fromEulerAngle(Vec3d eulerAngle) {
      return fromMcQuaternion(
         new Quaternionf()
            .rotateZ((float)Math.toRadians(eulerAngle.z))
            .rotateY((float)Math.toRadians(-eulerAngle.y))
            .rotateX((float)Math.toRadians(eulerAngle.x))
      );
   }

   public Vec3d toEulerAngle() {
      Quaternionf q = this.toMcQuaternion();
      Vector3f result = q.getEulerAnglesZYX(new Vector3f());
      return new Vec3d(Math.toDegrees(result.x), Math.toDegrees(-result.y), Math.toDegrees(result.z));
   }

   @NotNull
   public static DQuaternion fromNullable(@Nullable DQuaternion q) {
      return q == null ? identity : q;
   }

   public Vec3d getAxisW() {
      return this.rotate(new Vec3d(1.0, 0.0, 0.0));
   }

   public Vec3d getAxisH() {
      return this.rotate(new Vec3d(0.0, 1.0, 0.0));
   }

   public Vec3d getNormal() {
      return this.rotate(new Vec3d(0.0, 0.0, 1.0));
   }

   public boolean isValid() {
      return Math.abs(this.dotProduct(this)) > 0.9;
   }
}
