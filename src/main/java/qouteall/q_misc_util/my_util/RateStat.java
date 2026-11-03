package qouteall.q_misc_util.my_util;

import com.mojang.logging.LogUtils;
import java.util.function.DoubleConsumer;
import org.slf4j.Logger;

public class RateStat {
   private static final Logger LOGGER = LogUtils.getLogger();
   private final DoubleConsumer rateConsumer;
   private long lastUpdateSecond = 0L;
   private int hitCount = 0;

   public RateStat(DoubleConsumer rateConsumer) {
      this.rateConsumer = rateConsumer;
   }

   public RateStat(String name) {
      this(rate -> LOGGER.info("{} rate: {}", name, rate));
   }

   public void update() {
      long currTime = System.nanoTime();
      long currUpdateSecond = currTime / 1000000000L;
      if (this.lastUpdateSecond == 0L) {
         this.lastUpdateSecond = currUpdateSecond;
      } else {
         if (this.lastUpdateSecond != currUpdateSecond) {
            long passedSeconds = currUpdateSecond - this.lastUpdateSecond;
            int accumulatedHitCount = this.hitCount;
            this.hitCount = 0;
            this.lastUpdateSecond = currUpdateSecond;
            double rate = (double)accumulatedHitCount / passedSeconds;
            if (rate != 0.0) {
               this.rateConsumer.accept(rate);
            }
         }
      }
   }

   public void hit() {
      this.update();
      this.hitCount++;
   }
}
