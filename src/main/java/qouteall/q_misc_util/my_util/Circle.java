package qouteall.q_misc_util.my_util;

import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

public record Circle(Plane plane, Vec3d circleCenter, double radius) {
   @Nullable
   public Vec3d projectToCircle(Vec3d pos) {
      Vec3d projection = this.plane.getProjection(pos);
      Vec3d offset = projection.subtract(this.circleCenter);
      return offset.lengthSquared() < 0.001 ? null : offset.normalize().multiply(this.radius).add(this.circleCenter);
   }
}
