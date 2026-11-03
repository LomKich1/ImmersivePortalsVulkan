package qouteall.dimlib.mixin.common;

import net.minecraft.network.ClientConnection;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.dimlib.DimLibNetworking;
import qouteall.q_misc_util.platform.PlatformNetworking;

@Mixin({PlayerManager.class})
public class MixinPlayerList {
   @Inject(
      method = {"onPlayerConnect"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/network/packet/s2c/play/DifficultyS2CPacket;<init>(Lnet/minecraft/world/Difficulty;Z)V"
      )}
   )
   private void onConnectionEstablished(ClientConnection connection, ServerPlayerEntity player, ConnectedClientData commonListenerCookie, CallbackInfo ci) {
      player.networkHandler.sendPacket(PlatformNetworking.get().createS2CPacket(DimLibNetworking.DimSyncPacket.createPacket(player.server)));
   }
}
