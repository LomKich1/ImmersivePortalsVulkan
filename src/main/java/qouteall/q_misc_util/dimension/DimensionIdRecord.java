package qouteall.q_misc_util.dimension;

import net.minecraft.world.World;
import net.minecraft.registry.RegistryKey;
import qouteall.q_misc_util.MiscHelper;

@Deprecated
public class DimensionIdRecord {
   public static DimensionIdRecord serverRecord = new DimensionIdRecord();

   public RegistryKey<World> getDim(int dimIntId) {
      return DimensionIntId.getServerMap(MiscHelper.getServer()).fromIntegerId(dimIntId);
   }
}
