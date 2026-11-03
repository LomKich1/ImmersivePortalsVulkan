package qouteall.imm_ptl.core.mixin.common.position_sync;

import net.minecraft.world.World;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.registry.RegistryKey;
import org.spongepowered.asm.mixin.Mixin;
import qouteall.imm_ptl.core.ducks.IEPlayerMoveC2SPacket;

@Mixin({PlayerMoveC2SPacket.class})
public class MixinServerboundMovePlayerPacket_S implements IEPlayerMoveC2SPacket {
   private RegistryKey<World> playerDimension;

   @Override
   public RegistryKey<World> ip_getPlayerDimension() {
      return this.playerDimension;
   }

   @Override
   public void ip_setPlayerDimension(RegistryKey<World> dim) {
      this.playerDimension = dim;
   }
}
