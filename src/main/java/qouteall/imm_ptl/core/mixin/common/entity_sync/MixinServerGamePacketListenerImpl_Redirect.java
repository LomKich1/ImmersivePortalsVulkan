package qouteall.imm_ptl.core.mixin.common.entity_sync;

import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerCommonNetworkHandler;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.network.PacketRedirection;

@Mixin({ServerCommonNetworkHandler.class})
public class MixinServerGamePacketListenerImpl_Redirect {
   @Shadow
   @Final
   protected MinecraftServer server;

   @ModifyVariable(
      method = {"send(Lnet/minecraft/network/packet/Packet;Lio/netty/channel/ChannelFutureListener;)V"},
      at = @At("HEAD"),
      argsOnly = true
   )
   private Packet modifyPacket(Packet originalPacket) {
      return PacketRedirection.getForceRedirectDimension() == null
         ? originalPacket
         : PacketRedirection.createRedirectedMessage(this.server, PacketRedirection.getForceRedirectDimension(), originalPacket);
   }

   @Inject(
      method = {"send(Lnet/minecraft/network/packet/Packet;Lio/netty/channel/ChannelFutureListener;)V"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/network/ClientConnection;send(Lnet/minecraft/network/packet/Packet;Lio/netty/channel/ChannelFutureListener;Z)V"
      ),
      cancellable = true
   )
   private void onSend(Packet<?> packet, @Nullable ChannelFutureListener packetSendListener, CallbackInfo ci) {
      PacketRedirection.ForceBundleCallback forceBundleCallback = PacketRedirection.getForceBundleCallback();
      if (forceBundleCallback != null) {
         forceBundleCallback.accept((ServerCommonNetworkHandler)(Object)this, (Packet<ClientPlayPacketListener>)packet);
         ci.cancel();
      }
   }
}
