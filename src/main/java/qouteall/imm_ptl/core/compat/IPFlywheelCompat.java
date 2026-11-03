package qouteall.imm_ptl.core.compat;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.platform.Platform;

@Environment(EnvType.CLIENT)
public class IPFlywheelCompat {
   public static boolean isFlywheelPresent = false;

   public static void init() {
      if (Platform.get().isModLoaded("flywheel")) {
         Helper.log("Flywheel is present");
      }
   }
}
