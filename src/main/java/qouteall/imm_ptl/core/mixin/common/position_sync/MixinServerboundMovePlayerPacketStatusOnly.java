package qouteall.imm_ptl.core.mixin.common.position_sync;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.OnGroundOnly;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.ducks.IEPlayerMoveC2SPacket;

@Mixin({OnGroundOnly.class})
public class MixinServerboundMovePlayerPacketStatusOnly {
   @Inject(
      method = {"Lnet/minecraft/network/packet/c2s/play/PlayerMoveC2SPacket$OnGroundOnly;read(Lnet/minecraft/network/PacketByteBuf;)Lnet/minecraft/network/packet/c2s/play/PlayerMoveC2SPacket$OnGroundOnly;"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private static void onRead(PacketByteBuf buf, CallbackInfoReturnable<OnGroundOnly> cir) {
      RegistryKey<World> playerDim = buf.readRegistryKey(RegistryKeys.WORLD);
      ((IEPlayerMoveC2SPacket)cir.getReturnValue()).ip_setPlayerDimension(playerDim);
   }
}
