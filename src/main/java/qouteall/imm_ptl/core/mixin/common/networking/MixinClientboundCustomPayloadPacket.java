package qouteall.imm_ptl.core.mixin.common.networking;

import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.s2c.common.CustomPayloadS2CPacket;
import net.minecraft.network.listener.ClientCommonPacketListener;
import net.minecraft.network.packet.CustomPayload;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.ducks.IECustomPayloadPacket;
import qouteall.imm_ptl.core.network.PacketRedirection;

@Mixin({CustomPayloadS2CPacket.class})
public class MixinClientboundCustomPayloadPacket implements IECustomPayloadPacket {
   @Shadow
   @Final
   private CustomPayload comp_1646;

   @Inject(
      method = {"method_53024(Lnet/minecraft/ClientCommonPacketListener;)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onHandle(ClientCommonPacketListener clientCommonPacketListener, CallbackInfo ci) {
      if (this.comp_1646 instanceof PacketRedirection.Payload redirectPayload) {
         if (clientCommonPacketListener instanceof ClientPlayPacketListener clientGamePacketListener) {
            redirectPayload.handle(clientGamePacketListener);
         }

         ci.cancel();
      }
   }
}
