package qouteall.imm_ptl.core.chunk_loading;

import net.minecraft.server.MinecraftServer;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.my_util.LimitedLogger;
import qouteall.q_misc_util.platform.Platform;

public class ServerPerformanceMonitor {
   private static final int sampleNum = 20;
   private static PerformanceLevel level = PerformanceLevel.bad;
   private static final LimitedLogger limitedLogger = new LimitedLogger(50);
   private static long lastUpdateTime = 0L;

   public static void init() {
      Platform.get().onServerTickEnd(ServerPerformanceMonitor::tick);
   }

   private static void tick(MinecraftServer server) {
      if (!IPGlobal.enableServerPerformanceAdjustment) {
         level = PerformanceLevel.good;
      } else if (server.isRunning()) {
         long currTime = System.nanoTime();
         if (currTime - lastUpdateTime >= Helper.secondToNano(20.0)) {
            lastUpdateTime = currTime;
            PerformanceLevel newLevel = PerformanceLevel.getServerPerformanceLevel(server);
            if (newLevel != level) {
               level = newLevel;
               limitedLogger.log("Server performance level: " + newLevel);
            }
         }
      }
   }

   public static PerformanceLevel getLevel() {
      return level;
   }
}
