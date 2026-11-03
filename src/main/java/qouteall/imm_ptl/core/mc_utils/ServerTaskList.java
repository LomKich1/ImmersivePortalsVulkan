package qouteall.imm_ptl.core.mc_utils;

import net.minecraft.server.MinecraftServer;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.IPPerServerInfo;
import qouteall.q_misc_util.my_util.MyTaskList;
import qouteall.q_misc_util.platform.Platform;

public class ServerTaskList {
   public static void init() {
      Platform.get().onServerTickEnd(server -> of(server).processTasks());
      IPGlobal.SERVER_CLEANUP_EVENT.register(server -> of(server).forceClearTasks());
   }

   public static MyTaskList of(MinecraftServer server) {
      return IPPerServerInfo.of(server).taskList;
   }
}
