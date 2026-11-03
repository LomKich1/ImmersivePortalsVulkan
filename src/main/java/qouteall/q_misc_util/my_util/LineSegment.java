package qouteall.q_misc_util.my_util;

import net.minecraft.util.math.Vec3d;

public record LineSegment(Vec3d start, Vec3d end) {
   public LineSegment interpolate(LineSegment a, LineSegment b, double progress) {
      return new LineSegment(a.start().lerp(b.start(), progress), a.end().lerp(b.end(), progress));
   }

   public boolean isClose(LineSegment value, double v) {
      return this.start().squaredDistanceTo(value.start()) < v * v && this.end().squaredDistanceTo(value.end()) < v * v;
   }
}
