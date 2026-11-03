package qouteall.imm_ptl.core.mixin.client.sync;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.World;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.RegistryKey;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.class_2829;
import org.apache.commons.lang3.Validate;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.ducks.IEPlayerMoveC2SPacket;
import qouteall.imm_ptl.core.network.ImmPtlNetworkConfig;

@Environment(EnvType.CLIENT)
@Mixin({class_2829.class})
public class MixinServerboundMovePlayerPacketPos {
   @Inject(
      method = {"Lnet/minecraft/PlayerMoveC2SPacket.PositionAndOnGround;write(Lnet/minecraft/PacketByteBuf;)V"},
      at = {@At("RETURN")}
   )
   private void onWrite(PacketByteBuf buf, CallbackInfo ci) {
      if (ImmPtlNetworkConfig.doesServerHaveImmPtl()) {
         RegistryKey<World> playerDimension = ((IEPlayerMoveC2SPacket)this).ip_getPlayerDimension();
         Validate.notNull(playerDimension, "player dimension is null", new Object[0]);
         buf.writeRegistryKey(playerDimension);
      }
   }
}
