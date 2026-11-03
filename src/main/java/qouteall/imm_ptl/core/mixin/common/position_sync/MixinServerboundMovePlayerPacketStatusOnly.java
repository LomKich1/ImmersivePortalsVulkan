package qouteall.imm_ptl.core.mixin.common.position_sync;

import net.minecraft.world.World;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket.class_5911;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.ducks.IEPlayerMoveC2SPacket;

@Mixin({class_5911.class})
public class MixinServerboundMovePlayerPacketStatusOnly {
   @Inject(
      method = {"Lnet/minecraft/PlayerMoveC2SPacket.OnGroundOnly;read(Lnet/minecraft/PacketByteBuf;)Lnet/minecraft/PlayerMoveC2SPacket.OnGroundOnly;"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private static void onRead(PacketByteBuf buf, CallbackInfoReturnable<class_5911> cir) {
      RegistryKey<World> playerDim = buf.readRegistryKey(RegistryKeys.WORLD);
      ((IEPlayerMoveC2SPacket)cir.getReturnValue()).ip_setPlayerDimension(playerDim);
   }
}
