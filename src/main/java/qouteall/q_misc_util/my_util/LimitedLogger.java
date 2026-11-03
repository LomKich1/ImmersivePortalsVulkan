package qouteall.q_misc_util.my_util;

import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qouteall.q_misc_util.Helper;

@Deprecated
public class LimitedLogger {
   private static final Logger LOGGER = LoggerFactory.getLogger(LimitedLogger.class);
   private int remain;

   public LimitedLogger(int maxCount) {
      this.remain = maxCount;
   }

   public void log(String s) {
      this.invoke(() -> Helper.log(s));
   }

   public void err(String s) {
      this.invoke(() -> Helper.err(s));
   }

   public void lInfo(Logger logger, String template, Object... args) {
      this.invoke(() -> logger.info(template, args));
   }

   public void lErr(Logger logger, String template, Object... args) {
      this.invoke(() -> logger.error(template, args));
   }

   public void lInfo(org.apache.logging.log4j.Logger logger, String template, Object... args) {
      this.invoke(() -> logger.info(template, args));
   }

   public void lErr(org.apache.logging.log4j.Logger logger, String template, Object... args) {
      this.invoke(() -> logger.error(template, args));
   }

   public void invoke(Runnable r) {
      if (this.remain > 0) {
         this.remain--;
         r.run();
         if (this.remain == 0) {
            LOGGER.info("The logging reached its limit. Similar log won't be displayed.");
         }
      }
   }

   public void throwException(Supplier<RuntimeException> s) {
      this.invoke(() -> {
         throw (RuntimeException)s.get();
      });
   }
}
