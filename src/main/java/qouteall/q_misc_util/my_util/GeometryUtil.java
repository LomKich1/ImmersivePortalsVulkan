package qouteall.q_misc_util.my_util;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.Comparator;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

public class GeometryUtil {
   public static double getAngle(double dx1, double dy1, double dx2, double dy2) {
      double l1 = Math.sqrt(dx1 * dx1 + dy1 * dy1);
      double l2 = Math.sqrt(dx2 * dx2 + dy2 * dy2);
      if (l1 != 0.0 && l2 != 0.0) {
         double dot = dx1 / l1 * (dx2 / l2) + dy1 / l1 * (dy2 / l2);
         return Math.acos(dot);
      } else {
         return 0.0;
      }
   }

   public static boolean isOppositeVec(double v1x, double v1y, double v2x, double v2y) {
      double d1l = Math.sqrt(v1x * v1x + v1y * v1y);
      double d2l = Math.sqrt(v2x * v2x + v2y * v2y);
      if (d1l != 0.0 && d2l != 0.0) {
         double dot = v1x / d1l * (v2x / d2l) + v1y / d1l * (v2y / d2l);
         return Math.abs(dot - -1.0) < 1.0E-5;
      } else {
         return true;
      }
   }

   public static boolean triangleIntersects(
      double t0x0,
      double t0y0,
      double t0x1,
      double t0y1,
      double t0x2,
      double t0y2,
      double t1x0,
      double t1y0,
      double t1x1,
      double t1y1,
      double t1x2,
      double t1y2
   ) {
      GeometryUtil.BiDoublePredicate separatesByAxis = (axisX, axisY) -> triangleAxisSeparatesAlongAxis(
         t0x0, t0y0, t0x1, t0y1, t0x2, t0y2, t1x0, t1y0, t1x1, t1y1, t1x2, t1y2, axisX, axisY
      );
      double vec00x = t0x1 - t0x0;
      double vec00y = t0y1 - t0y0;
      double vec01x = t0x2 - t0x1;
      double vec01y = t0y2 - t0y1;
      double vec02x = t0x0 - t0x2;
      double vec02y = t0y0 - t0y2;
      double vec10x = t1x1 - t1x0;
      double vec10y = t1y1 - t1y0;
      double vec11x = t1x2 - t1x1;
      double vec11y = t1y2 - t1y1;
      double vec12x = t1x0 - t1x2;
      double vec12y = t1y0 - t1y2;
      double axis00x = -vec00y;
      double axis01x = -vec01y;
      double axis02x = -vec02y;
      double axis10x = -vec10y;
      double axis11x = -vec11y;
      double axis12x = -vec12y;
      boolean canSeparate = separatesByAxis.test(axis00x, vec00x)
         || separatesByAxis.test(axis01x, vec01x)
         || separatesByAxis.test(axis02x, vec02x)
         || separatesByAxis.test(axis10x, vec10x)
         || separatesByAxis.test(axis11x, vec11x)
         || separatesByAxis.test(axis12x, vec12x);
      return !canSeparate;
   }

   private static boolean triangleAxisSeparatesAlongAxis(
      double t0x0,
      double t0y0,
      double t0x1,
      double t0y1,
      double t0x2,
      double t0y2,
      double t1x0,
      double t1y0,
      double t1x1,
      double t1y1,
      double t1x2,
      double t1y2,
      double axisX,
      double axisY
   ) {
      double t0p0 = t0x0 * axisX + t0y0 * axisY;
      double t0p1 = t0x1 * axisX + t0y1 * axisY;
      double t0p2 = t0x2 * axisX + t0y2 * axisY;
      double t1p0 = t1x0 * axisX + t1y0 * axisY;
      double t1p1 = t1x1 * axisX + t1y1 * axisY;
      double t1p2 = t1x2 * axisX + t1y2 * axisY;
      double t0min = Math.min(Math.min(t0p0, t0p1), t0p2);
      double t0max = Math.max(Math.max(t0p0, t0p1), t0p2);
      double t1min = Math.min(Math.min(t1p0, t1p1), t1p2);
      double t1max = Math.max(Math.max(t1p0, t1p1), t1p2);
      return t0max - t1min < 0.0 || t1max - t0min < 0.0;
   }

   public static boolean triangleIntersectsWithAABB(
      double tx0, double ty0, double tx1, double ty1, double tx2, double ty2, double minX, double minY, double maxX, double maxY
   ) {
      double tMinX = Math.min(Math.min(tx0, tx1), tx2);
      double tMaxX = Math.max(Math.max(tx0, tx1), tx2);
      double tMinY = Math.min(Math.min(ty0, ty1), ty2);
      double tMaxY = Math.max(Math.max(ty0, ty1), ty2);
      if (!(tMaxX < minX) && !(tMinX > maxX) && !(tMaxY < minY) && !(tMinY > maxY)) {
         if (isAABBFullyOnRightSideOfLine(minX, minY, maxX, maxY, tx0, ty0, tx1 - tx0, ty1 - ty0)) {
            return false;
         } else {
            return isAABBFullyOnRightSideOfLine(minX, minY, maxX, maxY, tx1, ty1, tx2 - tx1, ty2 - ty1)
               ? false
               : !isAABBFullyOnRightSideOfLine(minX, minY, maxX, maxY, tx2, ty2, tx0 - tx2, ty0 - ty2);
         }
      } else {
         return false;
      }
   }

   private static boolean isAABBFullyOnLeftSideOfLine(
      double minX, double minY, double maxX, double maxY, double originX, double originY, double lineVecX, double lineVecY
   ) {
      double facingVecX = -lineVecY;
      double testingPointX = facingVecX > 0.0 ? minX : maxX;
      double testingPointY = lineVecX > 0.0 ? minY : maxY;
      double dx = testingPointX - originX;
      double dy = testingPointY - originY;
      double dot = dx * facingVecX + dy * lineVecX;
      return dot > 0.0;
   }

   private static boolean isAABBFullyOnRightSideOfLine(
      double minX, double minY, double maxX, double maxY, double originX, double originY, double lineVecX, double lineVecY
   ) {
      return isAABBFullyOnLeftSideOfLine(minX, minY, maxX, maxY, originX, originY, -lineVecX, -lineVecY);
   }

   public static Vec3d selectCoordFromAABB(Box box, boolean xPositive, boolean yPositive, boolean zPositive) {
      return new Vec3d(xPositive ? box.maxX : box.minX, yPositive ? box.maxY : box.minY, zPositive ? box.maxZ : box.minZ);
   }

   public static ObjectArrayList<Vec2d> getSlicePolygonOfCube(Box box, Plane plane, Vec3d planeX, Vec3d planeY, double scaleX, double scaleY) {
      Vec3d planeNormal = plane.normal();
      double[] boxEdges = new double[]{
         box.minX,
         box.minY,
         box.minZ,
         box.maxX,
         box.minY,
         box.minZ,
         box.minX,
         box.minY,
         box.maxZ,
         box.maxX,
         box.minY,
         box.maxZ,
         box.minX,
         box.maxY,
         box.minZ,
         box.maxX,
         box.maxY,
         box.minZ,
         box.minX,
         box.maxY,
         box.maxZ,
         box.maxX,
         box.maxY,
         box.maxZ,
         box.minX,
         box.minY,
         box.minZ,
         box.minX,
         box.maxY,
         box.minZ,
         box.minX,
         box.minY,
         box.maxZ,
         box.minX,
         box.maxY,
         box.maxZ,
         box.maxX,
         box.minY,
         box.minZ,
         box.maxX,
         box.maxY,
         box.minZ,
         box.maxX,
         box.minY,
         box.maxZ,
         box.maxX,
         box.maxY,
         box.maxZ,
         box.minX,
         box.minY,
         box.minZ,
         box.minX,
         box.minY,
         box.maxZ,
         box.minX,
         box.maxY,
         box.minZ,
         box.minX,
         box.maxY,
         box.maxZ,
         box.maxX,
         box.minY,
         box.minZ,
         box.maxX,
         box.minY,
         box.maxZ,
         box.maxX,
         box.maxY,
         box.minZ,
         box.maxX,
         box.maxY,
         box.maxZ
      };
      ObjectArrayList<Vec2d> result = new ObjectArrayList();
      double xSum = 0.0;
      double ySum = 0.0;

      for (int i = 0; i < 12; i++) {
         int ei = i * 6;
         double p0x = boxEdges[ei + 0];
         double p0y = boxEdges[ei + 1];
         double p0z = boxEdges[ei + 2];
         double p1x = boxEdges[ei + 3];
         double p1y = boxEdges[ei + 4];
         double p1z = boxEdges[ei + 5];
         double vx = p1x - p0x;
         double vy = p1y - p0y;
         double vz = p1z - p0z;
         double t = plane.rayTraceGetT(p0x, p0y, p0z, vx, vy, vz);
         if (!Double.isNaN(t) && !(t < 0.0) && !(t > 1.0)) {
            double x = p0x + vx * t;
            double y = p0y + vy * t;
            double z = p0z + vz * t;
            double dx = x - plane.pos().getX();
            double dy = y - plane.pos().getY();
            double dz = z - plane.pos().getZ();
            double localX = (planeX.getX() * dx + planeX.getY() * dy + planeX.getZ() * dz) / scaleX;
            double localY = (planeY.getX() * dx + planeY.getY() * dy + planeY.getZ() * dz) / scaleY;
            result.add(new Vec2d(localX, localY));
            xSum += localX;
            ySum += localY;
         }
      }

      if (result.isEmpty()) {
         return result;
      } else {
         double avgX = xSum / result.size();
         double avgY = ySum / result.size();
         result.sort(Comparator.comparingDouble(vec -> Math.atan2(vec.y() - avgY, vec.x() - avgX)));
         return result;
      }
   }

   @FunctionalInterface
   public interface BiDoublePredicate {
      boolean test(double var1, double var3);
   }

   public record Line2D(double linePX, double linePY, double dirX, double dirY) {
      public static GeometryUtil.Line2D fromTwoPoints(double x1, double y1, double x2, double y2) {
         double dx = x2 - x1;
         double dy = y2 - y1;
         return new GeometryUtil.Line2D(x1, y1, dx, dy);
      }

      public double getSideVecX() {
         return -this.dirY;
      }

      public double getSideVecY() {
         return this.dirX;
      }

      public int testSide(double x, double y) {
         double dx = x - this.linePX;
         double dy = y - this.linePY;
         double dot = dx * this.getSideVecX() + dy * this.getSideVecY();
         if (dot > 1.0E-5) {
            return 1;
         } else {
            return dot < -1.0E-5 ? -1 : 0;
         }
      }

      public boolean testSideBool(double x, double y) {
         double dx = x - this.linePX;
         double dy = y - this.linePY;
         double dot = dx * this.getSideVecX() + dy * this.getSideVecY();
         return dot > 0.0;
      }

      public double getIntersectionWithLine(GeometryUtil.Line2D other) {
         double det = this.dirX * other.dirY - this.dirY * other.dirX;
         return Math.abs(det) < 1.0E-8 ? Double.NaN : ((other.linePX - this.linePX) * other.dirY - (other.linePY - this.linePY) * other.dirX) / det;
      }

      public double getDistanceToLineIfWithinProjection(double x, double y) {
         double vx = x - this.linePX;
         double vy = y - this.linePY;
         double dotWithLineVec = vx * this.dirX + vy * this.dirY;
         double dotWithSideVec = vx * this.getSideVecX() + vy * this.getSideVecY();
         double lineLen = Math.sqrt(this.dirX * this.dirX + this.dirY * this.dirY);
         double lenAlongLineVec = dotWithLineVec / lineLen;
         double lenAlongSideVec = dotWithSideVec / lineLen;
         return !(lenAlongLineVec < 0.0) && !(lenAlongLineVec > lineLen) ? Math.abs(lenAlongSideVec) : Double.NaN;
      }
   }
}
