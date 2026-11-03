package qouteall.q_misc_util.my_util;

import org.apache.commons.lang3.Validate;

public class CountDownInt {
   private int value;

   public CountDownInt(int value) {
      Validate.isTrue(value >= 0);
      this.value = value;
   }

   public int getValue() {
      return this.value;
   }

   public boolean tryDecrement() {
      if (this.value > 0) {
         this.value--;
         return true;
      } else {
         return false;
      }
   }

   public boolean isZero() {
      return this.value == 0;
   }
}
