package qouteall.q_misc_util.api;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.packet.Packet;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.network.listener.ClientCommonPacketListener;
import net.minecraft.network.listener.ServerCommonPacketListener;
import qouteall.q_misc_util.ImplRemoteProcedureCall;

public class McRemoteProcedureCall {
   public static void tellClientToInvoke(ServerPlayerEntity player, String methodPath, Object... arguments) {
      Packet<ClientCommonPacketListener> packet = createPacketToSendToClient(methodPath, arguments);
      player.networkHandler.sendPacket(packet);
   }

   public static Packet<ClientCommonPacketListener> createPacketToSendToClient(String methodPath, Object... arguments) {
      return ImplRemoteProcedureCall.createS2CPacket(methodPath, arguments);
   }

   @Environment(EnvType.CLIENT)
   public static void tellServerToInvoke(String methodPath, Object... arguments) {
      Packet<ServerCommonPacketListener> packet = createPacketToSendToServer(methodPath, arguments);
      MinecraftClient.getInstance().getNetworkHandler().sendPacket(packet);
   }

   public static Packet<ServerCommonPacketListener> createPacketToSendToServer(String methodPath, Object... arguments) {
      return ImplRemoteProcedureCall.createC2SPacket(methodPath, arguments);
   }
}
