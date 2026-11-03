package qouteall.q_misc_util.my_util;

import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

public record Sphere(Vec3d center, double radius) {
   @Nullable
   public Vec3d projectToSphere(Vec3d pos) {
      Vec3d delta = pos.subtract(this.center());
      return delta.lengthSquared() < 0.001 ? null : this.center().add(delta.normalize().multiply(this.radius()));
   }

   @Nullable
   public Circle getIntersectionWithPlane(Plane plane) {
      double distance = plane.getDistanceTo(this.center());
      if (distance > this.radius()) {
         return null;
      } else {
         Vec3d circleCenter = plane.getProjection(this.center());
         double newRadius = Math.sqrt(this.radius() * this.radius() - distance * distance);
         return new Circle(plane, circleCenter, newRadius);
      }
   }

   @Nullable
   public Vec3d rayTrace(Vec3d lineOrigin, Vec3d lineVec) {
      Vec3d delta = lineOrigin.subtract(this.center());
      double a = lineVec.lengthSquared();
      double b = delta.dotProduct(lineVec) * 2.0;
      double c = delta.lengthSquared() - this.radius() * this.radius();
      double discriminant = b * b - 4.0 * a * c;
      if (discriminant < 0.0) {
         return null;
      } else {
         double sqrtDiscriminant = Math.sqrt(discriminant);
         double t1 = (-b + sqrtDiscriminant) / (2.0 * a);
         double t2 = (-b - sqrtDiscriminant) / (2.0 * a);
         if (t1 < 0.0 && t2 < 0.0) {
            return null;
         } else if (t1 < 0.0) {
            return lineOrigin.add(lineVec.multiply(t2));
         } else {
            return t2 < 0.0 ? lineOrigin.add(lineVec.multiply(t1)) : lineOrigin.add(lineVec.multiply(Math.min(t1, t2)));
         }
      }
   }

   public static Sphere interpolate(Sphere a, Sphere b, double progress) {
      return new Sphere(a.center().lerp(b.center(), progress), MathHelper.lerp(progress, a.radius(), b.radius()));
   }
}
