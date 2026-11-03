package qouteall.imm_ptl.peripheral.alternate_dimension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import net.minecraft.util.Pair;
import qouteall.q_misc_util.Helper;

public class RandomSelector<T> {
   private Object[] entries;
   private int[] subWeightSum;
   private int weightSum;

   public RandomSelector(List<Pair<T, Integer>> data) {
      this.entries = data.stream().map(Pair::getLeft).toArray();
      this.subWeightSum = Helper.mapReduce(data.stream(), (preSum, curr) -> preSum + (Integer)curr.getRight(), new Helper.SimpleBox<>(0))
         .mapToInt(i -> i)
         .toArray();
      this.weightSum = this.subWeightSum[this.subWeightSum.length - 1];
   }

   public T select(Random random) {
      int randomValue = random.nextInt(this.weightSum);
      return this.selectByRandomValue(randomValue);
   }

   public T selectByRandomValue(int randomValue) {
      int result = Arrays.binarySearch(this.subWeightSum, 0, this.subWeightSum.length, randomValue);
      if (result >= 0) {
         return (T)this.entries[result + 1];
      } else {
         int firstEleGreaterThanValue = -(result + 1);
         return (T)this.entries[firstEleGreaterThanValue];
      }
   }

   public static class Builder<A> {
      private ArrayList<Pair<A, Integer>> data = new ArrayList<>();

      public RandomSelector.Builder<A> add(int weight, A element) {
         this.data.add(new Pair(element, weight));
         return this;
      }

      public RandomSelector<A> build() {
         return new RandomSelector<>(this.data);
      }
   }
}
