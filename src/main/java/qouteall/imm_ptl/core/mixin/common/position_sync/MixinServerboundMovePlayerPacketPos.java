package qouteall.imm_ptl.core.mixin.common.position_sync;

import net.minecraft.world.World;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.class_2829;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.ducks.IEPlayerMoveC2SPacket;

@Mixin({class_2829.class})
public class MixinServerboundMovePlayerPacketPos {
   @Inject(
      method = {"Lnet/minecraft/PlayerMoveC2SPacket.PositionAndOnGround;read(Lnet/minecraft/PacketByteBuf;)Lnet/minecraft/PlayerMoveC2SPacket.PositionAndOnGround;"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private static void onRead(PacketByteBuf buf, CallbackInfoReturnable<class_2829> cir) {
      RegistryKey<World> playerDim = buf.readRegistryKey(RegistryKeys.WORLD);
      ((IEPlayerMoveC2SPacket)cir.getReturnValue()).ip_setPlayerDimension(playerDim);
   }
}
