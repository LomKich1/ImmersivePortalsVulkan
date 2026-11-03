package qouteall.imm_ptl.peripheral.platform_specific;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import qouteall.q_misc_util.platform.ModInfo;
import qouteall.q_misc_util.platform.Platform;

public class IPFeatureControl {
   private static final Logger LOGGER = LogUtils.getLogger();

   public static boolean isProvidedByJarInJar() {
      ModInfo modContainer = Platform.get().getModContainer("iportal").orElseThrow(() -> new RuntimeException("iportal mod not found"));
      return modContainer.isNested();
   }

   public static boolean enableVanillaBehaviorChangingByDefault() {
      return !isProvidedByJarInJar();
   }
}
