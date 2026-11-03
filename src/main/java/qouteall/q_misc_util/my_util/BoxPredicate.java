package qouteall.q_misc_util.my_util;

public interface BoxPredicate {
   BoxPredicate nonePredicate = (minX, minY, minZ, maxX, maxY, maxZ) -> false;

   boolean test(double var1, double var3, double var5, double var7, double var9, double var11);
}
