package qouteall.imm_ptl.core.ducks;

import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;

public interface IEPlayerMoveC2SPacket {
   RegistryKey<World> ip_getPlayerDimension();

   void ip_setPlayerDimension(RegistryKey<World> var1);
}
