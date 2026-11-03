package qouteall.q_misc_util.my_util;

public interface BoxPredicateF {
   BoxPredicateF nonePredicate = (minX, minY, minZ, maxX, maxY, maxZ) -> false;

   boolean test(float var1, float var2, float var3, float var4, float var5, float var6);
}
