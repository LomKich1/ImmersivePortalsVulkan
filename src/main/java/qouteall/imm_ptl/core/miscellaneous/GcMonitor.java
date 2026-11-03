package qouteall.imm_ptl.core.miscellaneous;

import com.mojang.logging.LogUtils;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.WeakHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.IPMcHelper;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.commands.PortalDebugCommands;
import qouteall.imm_ptl.core.platform_specific.IPConfig;
import qouteall.imm_ptl.core.platform_specific.O_O;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.my_util.CountDownInt;
import qouteall.q_misc_util.my_util.MyTaskList;
import qouteall.q_misc_util.platform.Platform;

public class GcMonitor {
   private static boolean memoryNotEnough = false;
   private static final WeakHashMap<GarbageCollectorMXBean, Long> lastCollectCount = new WeakHashMap<>();
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final CountDownInt MESSAGE_LIMIT = new CountDownInt(3);
   private static final CountDownInt LOG_LIMIT = new CountDownInt(3);
   private static long lastUpdateTime = 0L;
   private static long lastLongPauseTime = 0L;
   public static final String LINK = "https://filmora.wondershare.com/game-recording/how-to-allocate-more-ram-to-minecraft.html";

   @Environment(EnvType.CLIENT)
   public static void initClient() {
      IPGlobal.PRE_GAME_RENDER_EVENT.register(GcMonitor::update);
      long maxMemory = Runtime.getRuntime().maxMemory();
      long maxMemoryMB = PortalDebugCommands.toMiB(maxMemory);
      if (maxMemoryMB <= 2048L) {
         IPGlobal.CLIENT_TASK_LIST
            .addTask(
               MyTaskList.withDelayCondition(
                  () -> MinecraftClient.getInstance().world == null,
                  MyTaskList.oneShotTask(
                     () -> {
                        if (IPConfig.getConfig().shouldDisplayWarning("low_max_memory")) {
                           CHelper.printChat(
                              Text.translatable("imm_ptl.low_max_memory", new Object[]{maxMemoryMB})
                                 .formatted(Formatting.RED)
                                 .append(McHelper.getLinkText("https://filmora.wondershare.com/game-recording/how-to-allocate-more-ram-to-minecraft.html"))
                                 .append(IPMcHelper.getDisableWarningText("low_max_memory"))
                           );
                        }
                     }
                  )
               )
            );
      }
   }

   public static void initCommon() {
      Platform.get().onServerTickEnd(server -> {
         if (server.isDedicated()) {
            update();
         }
      });
   }

   private static void update() {
      double longPauseThresholdSeconds = 0.3;
      if (PortalDebugCommands.toMiB(Runtime.getRuntime().maxMemory()) < 2049L) {
         longPauseThresholdSeconds = 0.1;
      }

      long currTime = System.nanoTime();
      if (currTime - lastUpdateTime > Helper.secondToNano(longPauseThresholdSeconds)) {
         lastLongPauseTime = currTime;
      }

      lastUpdateTime = currTime;

      for (GarbageCollectorMXBean garbageCollectorMXBean : ManagementFactory.getGarbageCollectorMXBeans()) {
         long currCount = garbageCollectorMXBean.getCollectionCount();
         Long lastCount = lastCollectCount.get(garbageCollectorMXBean);
         lastCollectCount.put(garbageCollectorMXBean, currCount);
         if (lastCount != null && lastCount != currCount) {
            check();
         }
      }
   }

   private static void check() {
      long maxMemory = Runtime.getRuntime().maxMemory();
      long totalMemory = Runtime.getRuntime().totalMemory();
      long freeMemory = Runtime.getRuntime().freeMemory();
      long usedMemory = totalMemory - freeMemory;
      double usage = (double)usedMemory / maxMemory;
      double timeFromLongPause = System.nanoTime() - lastLongPauseTime;
      if (PortalDebugCommands.toMiB(maxMemory - usedMemory) < 300L && timeFromLongPause < Helper.secondToNano(2.0)) {
         if (memoryNotEnough && !O_O.isDedicatedServer()) {
            informMemoryNotEnoughClient();
         }

         if (LOG_LIMIT.tryDecrement()) {
            LOGGER.warn(
               String.format(
                  "Memory seems not enough. Try to Shrink loading distance or allocate more memory.\nMemory: % 2d%% %03d/%03dMB\n(Note: The memory check may be inaccurate.)\n",
                  usedMemory * 100L / maxMemory,
                  PortalDebugCommands.toMiB(usedMemory),
                  PortalDebugCommands.toMiB(maxMemory)
               )
            );
            if (LOG_LIMIT.isZero()) {
               LOGGER.info("Memory warning logging reached limit.");
            }
         }

         memoryNotEnough = true;
      } else {
         memoryNotEnough = false;
      }
   }

   @Environment(EnvType.CLIENT)
   private static void informMemoryNotEnoughClient() {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client.player != null && client.player.age > 40 && MESSAGE_LIMIT.tryDecrement()) {
         CHelper.printChat(
            Text.translatable("imm_ptl.memory_not_enough")
               .append(McHelper.getLinkText("https://filmora.wondershare.com/game-recording/how-to-allocate-more-ram-to-minecraft.html"))
         );
      }
   }

   public static boolean isMemoryNotEnough() {
      return memoryNotEnough;
   }
}
