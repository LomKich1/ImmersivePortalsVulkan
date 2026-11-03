package qouteall.q_misc_util.mixin.dimension;

import net.minecraft.network.ClientConnection;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.q_misc_util.MiscNetworking;

@Mixin({PlayerManager.class})
public class MixinPlayerList_Misc {
   @Inject(
      method = {"onPlayerConnect"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/network/packet/s2c/play/DifficultyS2CPacket;<init>(Lnet/minecraft/world/Difficulty;Z)V"
      )}
   )
   private void onConnectionEstablished(ClientConnection connection, ServerPlayerEntity player, ConnectedClientData commonListenerCookie, CallbackInfo ci) {
      player.networkHandler.sendPacket(MiscNetworking.DimIdSyncPacket.createPacket(player.server));
   }
}
