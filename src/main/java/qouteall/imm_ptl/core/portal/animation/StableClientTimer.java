package qouteall.imm_ptl.core.portal.animation;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.tick.TickManager;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.IPCGlobal;
import qouteall.q_misc_util.my_util.LimitedLogger;

@Environment(EnvType.CLIENT)
public class StableClientTimer {
   private static final LimitedLogger limitedLogger = new LimitedLogger(20);
   private static boolean initialized = false;
   @Nullable
   private static StableClientTimer.Time stableTime = null;
   @Nullable
   private static StableClientTimer.Time offsetTime = null;
   private static long referenceTickTime = 0L;
   private static double timeFlowScale = 1.0;
   @Nullable
   private static double debugOffset;

   public static void init() {
      IPCGlobal.CLIENT_CLEANUP_EVENT.register(StableClientTimer::cleanup);
   }

   public static long getStableTickTime() {
      return stableTime == null ? 0L : stableTime.tickTime;
   }

   public static float getStablePartialTicks() {
      return stableTime == null ? 0.0F : stableTime.partialTicks;
   }

   private static void cleanup() {
      initialized = false;
      stableTime = null;
      offsetTime = null;
   }

   private static void reset(long worldGameTime, float partialTicks) {
      timeFlowScale = 1.0;
      stableTime = new StableClientTimer.Time(worldGameTime, partialTicks);
      StableClientTimer.Time referenceTime = new StableClientTimer.Time(referenceTickTime, partialTicks);
      offsetTime = stableTime.subtracted(referenceTime);
   }

   public static void tick() {
      MinecraftClient mc = MinecraftClient.getInstance();
      if (mc.world != null) {
         TickManager tickRateManager = mc.world.getTickManager();
         if (tickRateManager.getTickRate() <= 20.0) {
            referenceTickTime++;
         } else {
            referenceTickTime = referenceTickTime + (long)(tickRateManager.getTickRate() / 20.0F);
         }
      }
   }

   public static void update(long worldGameTime, float partialTicks) {
      if (!initialized) {
         initialized = true;
         reset(worldGameTime, partialTicks);
      } else {
         MinecraftClient mc = MinecraftClient.getInstance();
         if (!mc.isPaused()) {
            if (mc.world != null) {
               if (mc.world.getTickManager().shouldTick()) {
                  Validate.notNull(stableTime);
                  Validate.notNull(offsetTime);
                  StableClientTimer.Time lastStableTime = stableTime;
                  StableClientTimer.Time referenceTime = new StableClientTimer.Time(referenceTickTime, partialTicks);
                  StableClientTimer.Time serverSyncedTime = new StableClientTimer.Time(worldGameTime, partialTicks);
                  StableClientTimer.Time projectedStableTime = referenceTime.added(offsetTime);
                  double deltaTickTime = serverSyncedTime.subtractedLen(projectedStableTime);
                  double stableTimeDelta = projectedStableTime.subtractedLen(lastStableTime);
                  if (!(stableTimeDelta < 0.0)) {
                     if (deltaTickTime < 0.0) {
                        double targetSubtractLastStable = serverSyncedTime.subtractedLen(lastStableTime);
                        if (targetSubtractLastStable < 0.0) {
                           if (targetSubtractLastStable < -100.0) {
                              reset(worldGameTime, partialTicks);
                              limitedLogger.err("Reset the client stable timer because the target time is too far behind");
                           } else {
                              if (timeFlowScale > 1.0) {
                                 timeFlowScale = 1.0;
                              }

                              timeFlowScale *= 0.9999;
                              stableTime = lastStableTime.added((float)(timeFlowScale * stableTimeDelta));
                              offsetTime = stableTime.subtracted(referenceTime);
                           }
                        } else {
                           stableTime = serverSyncedTime;
                           offsetTime = stableTime.subtracted(referenceTime);
                           timeFlowScale = 1.0;
                        }
                     } else {
                        if (timeFlowScale < 1.0) {
                           timeFlowScale = 1.0;
                        }

                        timeFlowScale *= 1.0001;
                        stableTime = lastStableTime.added((float)(timeFlowScale * stableTimeDelta));
                        offsetTime = stableTime.subtracted(referenceTime);
                     }

                     debugOffset = serverSyncedTime.subtractedLen(stableTime);
                  }
               }
            }
         }
      }
   }

   public static String getDebugString() {
      double shownOffset = debugOffset;
      if (Math.abs(shownOffset) < 1.0E-4) {
         shownOffset = 0.0;
      }

      return String.format("Stable Timer Offset %.3f", shownOffset);
   }

   public static final class Time {
      final long tickTime;
      final float partialTicks;

      Time(long tickTime, float partialTicks) {
         this.tickTime = tickTime;
         this.partialTicks = partialTicks;
      }

      StableClientTimer.Time normalized() {
         long tickTime = this.tickTime;
         float partialTicks = this.partialTicks;
         if (partialTicks < 0.0F) {
            int fullTicks = (int)Math.floor(partialTicks);
            partialTicks -= fullTicks;
            tickTime += fullTicks;
         }

         if (partialTicks >= 1.0F) {
            int fullTicks = (int)partialTicks;
            partialTicks -= fullTicks;
            tickTime += fullTicks;
         }

         return new StableClientTimer.Time(tickTime, partialTicks);
      }

      double subtractedLen(StableClientTimer.Time another) {
         return (float)(this.tickTime - another.tickTime) + this.partialTicks - another.partialTicks;
      }

      StableClientTimer.Time subtracted(StableClientTimer.Time another) {
         return new StableClientTimer.Time(this.tickTime - another.tickTime, this.partialTicks - another.partialTicks).normalized();
      }

      StableClientTimer.Time added(StableClientTimer.Time another) {
         return new StableClientTimer.Time(this.tickTime + another.tickTime, this.partialTicks + another.partialTicks).normalized();
      }

      StableClientTimer.Time added(float delta) {
         return new StableClientTimer.Time(this.tickTime, this.partialTicks + delta).normalized();
      }

      @Override
      public String toString() {
         return "%d+%.3f".formatted(this.tickTime, this.partialTicks);
      }
   }
}
