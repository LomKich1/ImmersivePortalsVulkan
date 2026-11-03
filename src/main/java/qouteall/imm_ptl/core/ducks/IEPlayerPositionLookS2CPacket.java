package qouteall.imm_ptl.core.ducks;

import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;

public interface IEPlayerPositionLookS2CPacket {
   RegistryKey<World> ip_getPlayerDimension();

   void ip_setPlayerDimension(RegistryKey<World> var1);
}
