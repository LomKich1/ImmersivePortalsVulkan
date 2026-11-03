package qouteall.imm_ptl.core.miscellaneous;

import java.util.ArrayDeque;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.chunk_loading.PerformanceLevel;
import qouteall.imm_ptl.core.commands.PortalDebugCommands;
import qouteall.q_misc_util.api.McRemoteProcedureCall;

@Environment(EnvType.CLIENT)
public class ClientPerformanceMonitor {
   public static PerformanceLevel level = PerformanceLevel.medium;
   private static final ArrayDeque<ClientPerformanceMonitor.Record> records = new ArrayDeque<>();
   private static int averageFps = 60;
   private static int minimumFps = 60;
   private static int averageFreeMemoryMB = 1000;
   private static final int sampleNum = 20;
   private static int counter = 0;

   public static void updateEverySecond(int newFps) {
      if (MinecraftClient.getInstance().player != null) {
         long maxMemoryBytes = Runtime.getRuntime().maxMemory();
         long totalMemoryBytes = Runtime.getRuntime().totalMemory();
         long freeMemoryBytes = Runtime.getRuntime().freeMemory();
         long usedMemoryBytes = totalMemoryBytes - freeMemoryBytes;
         long actualFreeMemoryBytes = maxMemoryBytes - usedMemoryBytes;
         int freeMemoryMB = (int)PortalDebugCommands.toMiB(actualFreeMemoryBytes);
         records.addLast(new ClientPerformanceMonitor.Record(newFps, freeMemoryMB));
         if (records.size() > 20) {
            records.removeFirst();
         }

         averageFps = (int)records.stream().mapToInt(r -> r.FPS).average().orElse(60.0);
         minimumFps = records.stream().mapToInt(r -> r.FPS).min().orElse(60);
         averageFreeMemoryMB = (int)records.stream().mapToInt(r -> r.freeMemoryMB).average().orElse(1000.0);
         counter++;
         if (counter % 5 == 0) {
            updateAndSend();
         }
      }
   }

   public static int getAverageFps() {
      return averageFps;
   }

   public static int getMinimumFps() {
      return minimumFps;
   }

   public static int getAverageFreeMemoryMB() {
      return averageFreeMemoryMB;
   }

   private static void updateAndSend() {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      if (player != null) {
         if (!IPGlobal.enableClientPerformanceAdjustment) {
            level = PerformanceLevel.good;
         } else {
            level = PerformanceLevel.getClientPerformanceLevel(averageFps, averageFreeMemoryMB);
         }

         McRemoteProcedureCall.tellServerToInvoke("qouteall.imm_ptl.core.chunk_loading.ImmPtlChunkTracking.RemoteCallables.acceptClientPerformanceInfo", level);
      }
   }

   public static class Record {
      public final int FPS;
      public final int freeMemoryMB;

      public Record(int FPS, int freeMemoryMB) {
         this.FPS = FPS;
         this.freeMemoryMB = freeMemoryMB;
      }
   }
}
