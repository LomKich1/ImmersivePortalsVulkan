package qouteall.imm_ptl.core.chunk_loading;

import com.mojang.logging.LogUtils;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.util.List;
import java.util.Locale;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public class KeepChunksMemoryValve {
   private static final Logger LOGGER = LogUtils.getLogger();
   public static final String RELEASE_ABOVE_PROPERTY = "immptl.keepChunksReleaseAbovePercent";
   public static final int RELEASE_BATCH = 256;
   private static final double RELEASE_ABOVE;
   private static final double RESUME_BELOW;
   private static boolean initialized;
   @Nullable
   private static MemoryPoolMXBean oldGeneration;
   private static List<GarbageCollectorMXBean> collectors;
   private static long sampledCollectionCount;
   private static long sampledUsedAfterCollection;
   private static boolean releasing;
   private static long releasedInEpisode;
   private static long lastToldMillis;
   private static final long TELL_INTERVAL_MILLIS = 60000L;

   private static void init() {
      initialized = true;

      for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
         if (pool.getType() == MemoryType.HEAP && pool.isCollectionUsageThresholdSupported()) {
            String name = pool.getName().toLowerCase(Locale.ROOT);
            if (name.contains("old") || name.contains("tenured")) {
               oldGeneration = pool;
               break;
            }
         }
      }

      collectors = ManagementFactory.getGarbageCollectorMXBeans();
      LOGGER.info(
         "Keep Chunks Loaded Forever: memory valve judging by {}; releasing the farthest kept chunks above {}% of the {} MiB heap, keeping again below {}%",
         new Object[]{
            oldGeneration != null
               ? "the live data of \"" + oldGeneration.getName() + "\" after its collections"
               : "the heap in use after a collection (no old-generation pool with collection usage)",
            Math.round(RELEASE_ABOVE * 100.0),
            Runtime.getRuntime().maxMemory() >> 20,
            Math.round(RESUME_BELOW * 100.0)
         }
      );
   }

   private static long liveAfterCollection() {
      if (oldGeneration != null) {
         MemoryUsage usage = oldGeneration.getCollectionUsage();
         return usage == null ? -1L : usage.getUsed();
      } else {
         long count = 0L;

         for (GarbageCollectorMXBean collector : collectors) {
            count += Math.max(0L, collector.getCollectionCount());
         }

         if (count != sampledCollectionCount) {
            sampledCollectionCount = count;
            Runtime runtime = Runtime.getRuntime();
            sampledUsedAfterCollection = runtime.totalMemory() - runtime.freeMemory();
         }

         return sampledUsedAfterCollection;
      }
   }

   public static boolean shouldRelease(MinecraftServer server) {
      if (!initialized) {
         init();
      }

      long live = liveAfterCollection();
      if (live < 0L) {
         return releasing;
      } else {
         long max = Runtime.getRuntime().maxMemory();
         double fraction = (double)live / max;
         if (!releasing && fraction >= RELEASE_ABOVE) {
            releasing = true;
            releasedInEpisode = 0L;
            LOGGER.warn(
               "Keep Chunks Loaded Forever: memory is low ({} of {} MiB live after the last collection, {}% of the heap): releasing the kept chunks farthest from the players, {} per update, until it is below {}%",
               new Object[]{live >> 20, max >> 20, Math.round(fraction * 100.0), 256, Math.round(RESUME_BELOW * 100.0)}
            );
            long now = System.currentTimeMillis();
            if (now - lastToldMillis >= 60000L) {
               lastToldMillis = now;
               tell(server, Text.translatable("imm_ptl.keep_chunks_memory_low", new Object[]{Math.round(fraction * 100.0)}));
            }
         } else if (releasing && fraction < RESUME_BELOW) {
            releasing = false;
            LOGGER.info(
               "Keep Chunks Loaded Forever: memory recovered ({} of {} MiB live, {}%) after releasing {} kept chunk records: kept chunks are kept again",
               new Object[]{live >> 20, max >> 20, Math.round(fraction * 100.0), releasedInEpisode}
            );
         }

         return releasing;
      }
   }

   public static void onReleased(int count) {
      releasedInEpisode += count;
      if (count > 0) {
         LOGGER.debug("Keep Chunks Loaded Forever: released {} kept chunk records ({} in this episode)", count, releasedInEpisode);
      }
   }

   private static void tell(MinecraftServer server, Text message) {
      for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
         player.sendMessage(message);
      }
   }

   static {
      int percent = 80;
      String property = System.getProperty("immptl.keepChunksReleaseAbovePercent");
      if (property != null) {
         try {
            percent = Math.max(20, Math.min(99, Integer.parseInt(property.trim())));
         } catch (NumberFormatException var3) {
            LOGGER.warn("{}: not a number, using {}: {}", new Object[]{"immptl.keepChunksReleaseAbovePercent", percent, property});
         }
      }

      RELEASE_ABOVE = percent / 100.0;
      RESUME_BELOW = (percent - 10) / 100.0;
      initialized = false;
      oldGeneration = null;
      collectors = List.of();
      sampledCollectionCount = -1L;
      sampledUsedAfterCollection = -1L;
      releasing = false;
      releasedInEpisode = 0L;
      lastToldMillis = 0L;
   }
}
