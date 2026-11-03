package qouteall.imm_ptl.core.network;

import com.mojang.logging.LogUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.thread.ThreadExecutor;
import net.minecraft.world.World;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.RegistryKey;
import net.minecraft.network.packet.PacketType;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.q_misc_util.dimension.DimensionIntId;
import qouteall.q_misc_util.my_util.LimitedLogger;

@Environment(EnvType.CLIENT)
public class PacketRedirectionClient {
   public static final MinecraftClient client = MinecraftClient.getInstance();
   private static final LimitedLogger limitedLogger = new LimitedLogger(100);
   private static final Logger LOGGER = LogUtils.getLogger();
   public static final ThreadLocal<RegistryKey<World>> clientTaskRedirection = ThreadLocal.withInitial(() -> null);

   public static boolean getIsProcessingRedirectedMessage() {
      return clientTaskRedirection.get() != null;
   }

   public static void handleRedirectedPacket(int dimensionIntId, Packet<ClientPlayPacketListener> packet, ClientPlayPacketListener handler) {
      MinecraftClient minecraft = MinecraftClient.getInstance();
      if (minecraft.isOnThread()) {
         RegistryKey<World> dimension = DimensionIntId.getClientMap().fromIntegerId(dimensionIntId);
         RegistryKey<World> oldTaskRedirection = clientTaskRedirection.get();
         clientTaskRedirection.set(dimension);

         try {
            ClientWorldLoader.withSwitchedWorldFailSoft(dimension, () -> packet.apply(handler));
         } finally {
            clientTaskRedirection.set(oldTaskRedirection);
         }
      } else {
         minecraft.getPacketApplyBatcher().add(handler, new PacketRedirectionClient.QueuedRedirectedPacket(dimensionIntId, packet));
      }
   }

   @Deprecated
   public static void old_handleRedirectedPacket(RegistryKey<World> dimension, Packet<ClientPlayPacketListener> packet, ClientPlayPacketListener handler) {
      RegistryKey<World> oldTaskRedirection = clientTaskRedirection.get();
      clientTaskRedirection.set(dimension);

      try {
         if (MinecraftClient.getInstance().isOnThread()) {
            ClientWorldLoader.withSwitchedWorldFailSoft(dimension, () -> packet.apply(handler));
         } else {
            packet.apply(handler);
         }
      } finally {
         clientTaskRedirection.set(oldTaskRedirection);
      }
   }

   private record QueuedRedirectedPacket(int dimensionIntId, Packet<ClientPlayPacketListener> packet) implements Packet<ClientPlayPacketListener> {
      public PacketType<? extends Packet<ClientPlayPacketListener>> getPacketType() {
         return this.packet.getPacketType();
      }

      public void handle(ClientPlayPacketListener listener) {
         try {
            PacketRedirectionClient.handleRedirectedPacket(this.dimensionIntId, this.packet, listener);
         } catch (Exception var3) {
            if (ThreadExecutor.isMemoryError(var3)) {
               throw var3;
            }

            PacketRedirectionClient.LOGGER
               .error("Error handling redirected packet {} of dimension id {}", new Object[]{this.packet, this.dimensionIntId, var3});
         }
      }
   }
}
