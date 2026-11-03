package qouteall.dimlib.config;

import eu.midnightdust.lib.config.MidnightConfig;
import eu.midnightdust.lib.config.MidnightConfig.Entry;

public class DimLibConfig extends MidnightConfig {
   @Entry
   public static boolean suppressExperimentalWarning = false;
}
