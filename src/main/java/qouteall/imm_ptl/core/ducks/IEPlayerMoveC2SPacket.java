package qouteall.imm_ptl.core.ducks;

import net.minecraft.world.World;
import net.minecraft.registry.RegistryKey;

public interface IEPlayerMoveC2SPacket {
   RegistryKey<World> ip_getPlayerDimension();

   void ip_setPlayerDimension(RegistryKey<World> var1);
}
