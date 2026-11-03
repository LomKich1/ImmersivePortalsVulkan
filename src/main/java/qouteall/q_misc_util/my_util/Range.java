package qouteall.q_misc_util.my_util;

import org.jetbrains.annotations.Nullable;

public record Range(double start, double end) {
   public static Range createUnordered(double p1, double p2) {
      return new Range(Math.min(p1, p2), Math.max(p1, p2));
   }

   @Nullable
   public static Range intersection(Range r1, Range r2) {
      Range range = new Range(Math.max(r1.start, r2.start), Math.min(r1.end, r2.end));
      return range.start >= range.end ? null : range;
   }

   @Nullable
   public Range intersect(Range another) {
      return intersection(this, another);
   }

   public static boolean rangeIntersects(double r1Start, double r1End, double r2Start, double r2End) {
      return Math.max(r1Start, r2Start) <= Math.min(r1End, r2End);
   }

   public static double getPushRangeMovement(double rangeToPushStart, double rangeToPushEnd, double colliderStart, double colliderEnd) {
      if (rangeToPushStart >= colliderEnd) {
         return 0.0;
      } else if (rangeToPushEnd <= colliderStart) {
         return 0.0;
      } else {
         double toPushMid = (rangeToPushStart + rangeToPushEnd) / 2.0;
         double colliderMid = (colliderStart + colliderEnd) / 2.0;
         return toPushMid > colliderMid ? colliderEnd - rangeToPushStart : colliderStart - rangeToPushEnd;
      }
   }

   public static double getConfineRangeMovement(double rangeToConfineStart, double rangeToConfineEnd, double barrierStart, double barrierEnd) {
      if (rangeToConfineStart < barrierStart) {
         return barrierStart - rangeToConfineStart;
      } else {
         return rangeToConfineEnd > barrierEnd ? barrierEnd - rangeToConfineEnd : 0.0;
      }
   }
}
