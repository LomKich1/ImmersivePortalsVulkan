package qouteall.imm_ptl.core.ducks;

import net.minecraft.world.World;
import net.minecraft.registry.RegistryKey;

public interface IEPlayerPositionLookS2CPacket {
   RegistryKey<World> ip_getPlayerDimension();

   void ip_setPlayerDimension(RegistryKey<World> var1);
}
