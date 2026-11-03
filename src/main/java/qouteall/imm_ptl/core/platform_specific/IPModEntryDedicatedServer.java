package qouteall.imm_ptl.core.platform_specific;

import net.fabricmc.api.DedicatedServerModInitializer;
import qouteall.imm_ptl.core.compat.IPModInfoChecking;

public class IPModEntryDedicatedServer implements DedicatedServerModInitializer {
   public void onInitializeServer() {
      IPModInfoChecking.initDedicatedServer();
   }
}
