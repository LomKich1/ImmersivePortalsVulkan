package qouteall.q_misc_util.my_util;

import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record Plane(Vec3d pos, Vec3d normal) {
   public Plane(Vec3d pos, Vec3d normal) {
      this.pos = pos;
      this.normal = normal.normalize();
   }

   public double getDistanceTo(Vec3d point) {
      return this.normal.dotProduct(point.subtract(this.pos));
   }

   public double getDistanceTo(double x, double y, double z) {
      return this.normal.getX() * (x - this.pos.getX()) + this.normal.getY() * (y - this.pos.getY()) + this.normal.getZ() * (z - this.pos.getZ());
   }

   @NotNull
   public Vec3d getProjection(Vec3d point) {
      return point.subtract(this.normal.multiply(this.getDistanceTo(point)));
   }

   public Vec3d getReflection(Vec3d point) {
      return point.subtract(this.normal.multiply(2.0 * this.getDistanceTo(point)));
   }

   public boolean isPointOnPositiveSide(Vec3d point) {
      return this.getDistanceTo(point) > 0.0;
   }

   public Plane move(double distance) {
      return new Plane(this.pos.add(this.normal.multiply(distance)), this.normal);
   }

   public Plane getOpposite() {
      return new Plane(this.pos, this.normal.multiply(-1.0));
   }

   @Nullable
   public Vec3d rayTrace(Vec3d origin, Vec3d vec) {
      double t = this.rayTraceGetT(origin, vec);
      if (Double.isNaN(t)) {
         return null;
      } else {
         return t < 0.0 ? null : origin.add(vec.multiply(t));
      }
   }

   public double rayTraceGetT(Vec3d lineOrigin, Vec3d lineVec) {
      double lineVecProjectToNormal = this.normal.dotProduct(lineVec);
      return Math.abs(lineVecProjectToNormal) < 1.0E-5 ? Double.NaN : -this.getDistanceTo(lineOrigin) / lineVecProjectToNormal;
   }

   public double rayTraceGetT(double lineOriginX, double lineOriginY, double lineOriginZ, double lineVecX, double lineVecY, double lineVecZ) {
      double lineVecProjectToNormal = this.normal.getX() * lineVecX + this.normal.getY() * lineVecY + this.normal.getZ() * lineVecZ;
      return Math.abs(lineVecProjectToNormal) < 1.0E-5 ? Double.NaN : -this.getDistanceTo(lineOriginX, lineOriginY, lineOriginZ) / lineVecProjectToNormal;
   }

   @Nullable
   public Vec3d intersectionWithLineSegment(Vec3d lineP1, Vec3d lineP2) {
      Vec3d lineVec = lineP2.subtract(lineP1);
      double t = this.rayTraceGetT(lineP1, lineVec);
      if (Double.isNaN(t)) {
         return null;
      } else {
         return !(t < 0.0) && !(t > 1.0) ? lineP1.add(lineVec.multiply(t)) : null;
      }
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) {
         return true;
      } else if (o != null && this.getClass() == o.getClass()) {
         Plane plane = (Plane)o;
         return this.pos.equals(plane.pos) && this.normal.equals(plane.normal);
      } else {
         return false;
      }
   }

   @Override
   public String toString() {
      return "Plane{pos=" + this.pos + ", normal=" + this.normal + "}";
   }

   public static Plane interpolate(Plane a, Plane b, double progress) {
      Vec3d pos = a.pos.lerp(b.pos, progress);
      Vec3d normal = a.normal.lerp(b.normal, progress).normalize();
      return new Plane(pos, normal);
   }

   public Plane getParallelPlane(Vec3d pos) {
      return new Plane(pos, this.normal);
   }

   public double getEquationX() {
      return this.normal.getX();
   }

   public double getEquationY() {
      return this.normal.getY();
   }

   public double getEquationZ() {
      return this.normal.getZ();
   }

   public double getEquationW() {
      return -this.normal.dotProduct(this.pos);
   }
}
