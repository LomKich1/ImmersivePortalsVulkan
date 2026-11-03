package qouteall.imm_ptl.core.network;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.BundlePacket;
import net.minecraft.network.packet.BundleSplitterPacket;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.CustomPayload.Id;
import net.minecraft.network.packet.s2c.common.CustomPayloadS2CPacket;
import net.minecraft.network.packet.s2c.play.BundleS2CPacket;
import net.minecraft.network.state.NetworkState;
import net.minecraft.network.state.PlayStateFactories;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerCommonNetworkHandler;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.api.PortalAPI;
import qouteall.imm_ptl.core.ducks.IEWorld;
import qouteall.q_misc_util.platform.PlatformNetworking;

public class PacketRedirection {
   private static final Logger LOGGER = LoggerFactory.getLogger(PacketRedirection.class);
   public static final Identifier payloadId = McHelper.newResourceLocation("i:r");
   private static final ThreadLocal<RegistryKey<World>> serverPacketRedirection = ThreadLocal.withInitial(() -> null);
   private static final ThreadLocal<PacketRedirection.ForceBundleCallback> forceBundle = ThreadLocal.withInitial(() -> null);
   private static final NetworkState<ClientPlayPacketListener> PLACEHOLDER_PROTOCOL_INFO = PlayStateFactories.S2C.bind(argBuf -> (RegistryByteBuf)argBuf);

   public static void init() {
      PlatformNetworking.get().registerPlayS2C(PacketRedirection.Payload.TYPE, PacketRedirection.Payload.CODEC);
   }

   public static void withForceRedirect(ServerWorld world, Runnable func) {
      withForceRedirectAndGet(world, () -> {
         func.run();
         return null;
      });
   }

   public static <T> T withForceRedirectAndGet(ServerWorld world, Supplier<T> func) {
      if (((IEWorld)world).portal_getThread() != Thread.currentThread()) {
         LOGGER.error(
            "It's possible that a mod is trying to handle packet in networking thread instead of server thread. This is not thread safe and can cause rare bugs! (ImmPtl is just doing checking, it's not an issue of iPortal)",
            new Throwable()
         );
      }

      RegistryKey<World> redirectDim = world.getRegistryKey();
      RegistryKey<World> oldRedirection = serverPacketRedirection.get();
      if (oldRedirection != redirectDim) {
         serverPacketRedirection.set(redirectDim);
      }

      Object var4;
      try {
         var4 = func.get();
      } finally {
         if (oldRedirection != redirectDim) {
            serverPacketRedirection.set(oldRedirection);
         }
      }

      return (T)var4;
   }

   @Nullable
   public static RegistryKey<World> getForceRedirectDimension() {
      return serverPacketRedirection.get();
   }

   public static void sendRedirectedPacket(
      ServerPlayNetworkHandler serverPlayNetworkHandler, Packet<ClientPlayPacketListener> packet, RegistryKey<World> dimension
   ) {
      if (getForceRedirectDimension() == dimension) {
         serverPlayNetworkHandler.sendPacket(packet);
      } else {
         serverPlayNetworkHandler.sendPacket(createRedirectedMessage(serverPlayNetworkHandler.player.server, dimension, packet));
      }
   }

   public static void validateForceRedirecting() {
      Validate.isTrue(getForceRedirectDimension() != null);
   }

   public static Packet<ClientPlayPacketListener> createRedirectedMessage(
      MinecraftServer server, RegistryKey<World> dimension, Packet<ClientPlayPacketListener> packet
   ) {
      if (isRedirectPacket(packet)) {
         return packet;
      } else {
         Validate.isTrue(!(packet instanceof BundleSplitterPacket));
         if (!(packet instanceof BundleS2CPacket bundlePacket)) {
            int intDimId = PortalAPI.serverDimKeyToInt(server, dimension);
            PacketRedirection.Payload payload = new PacketRedirection.Payload(intDimId, packet);
            return PlatformNetworking.get().createS2CPacket(payload);
         } else {
            List<Packet<ClientPlayPacketListener>> newSubPackets = new ArrayList<>();

            for (Packet<? super ClientPlayPacketListener> subPacket : bundlePacket.getPackets()) {
               newSubPackets.add(createRedirectedMessage(server, dimension, subPacket));
            }

            return new BundleS2CPacket(newSubPackets);
         }
      }
   }

   public static void sendRedirectedMessage(ServerPlayerEntity player, RegistryKey<World> dimension, Packet<ClientPlayPacketListener> packet) {
      player.networkHandler.sendPacket(createRedirectedMessage(player.server, dimension, packet));
   }

   public static boolean isRedirectPacket(Packet<?> packet) {
      return packet instanceof CustomPayloadS2CPacket customPayloadPacket && customPayloadPacket.payload() instanceof PacketRedirection.Payload;
   }

   public static <R> R withForceBundle(Supplier<R> func) {
      PacketRedirection.ForceBundleCallback forceBundleCallback = getForceBundleCallback();
      if (forceBundleCallback != null) {
         return func.get();
      } else {
         Map<ServerCommonNetworkHandler, List<Packet<ClientPlayPacketListener>>> map = new HashMap<>();
         forceBundle.set((listenerx, packet) -> {
            List<Packet<ClientPlayPacketListener>> packetsToBundle = map.computeIfAbsent(listenerx, k -> new ArrayList<>());
            if (packet instanceof BundlePacket<?> bundlePacket) {
               for (Packet<?> subPacket : bundlePacket.getPackets()) {
                  packetsToBundle.add((Packet<ClientPlayPacketListener>)subPacket);
               }
            } else {
               packetsToBundle.add(packet);
            }
         });

         Object var3;
         try {
            var3 = func.get();
         } finally {
            forceBundle.set(null);

            for (Entry<ServerCommonNetworkHandler, List<Packet<ClientPlayPacketListener>>> e : map.entrySet()) {
               ServerCommonNetworkHandler listener = e.getKey();
               List<Packet<ClientPlayPacketListener>> packets = e.getValue();
               listener.sendPacket(new BundleS2CPacket(packets));
            }
         }

         return (R)var3;
      }
   }

   @Nullable
   public static PacketRedirection.ForceBundleCallback getForceBundleCallback() {
      return forceBundle.get();
   }

   public interface ForceBundleCallback {
      void accept(ServerCommonNetworkHandler var1, Packet<ClientPlayPacketListener> var2);
   }

   public record Payload(int dimensionIntId, Packet<? extends ClientPlayPacketListener> packet) implements CustomPayload {
      public static final Id<PacketRedirection.Payload> TYPE = new Id(PacketRedirection.payloadId);
      public static final PacketCodec<RegistryByteBuf, PacketRedirection.Payload> CODEC = PacketCodec.ofStatic(
         (b, p) -> p.write(b), PacketRedirection.Payload::read
      );

      public void write(RegistryByteBuf buf) {
         Validate.notNull(this.packet, "packet is null", new Object[0]);
         buf.writeVarInt(this.dimensionIntId);
         PacketRedirection.PLACEHOLDER_PROTOCOL_INFO.codec().encode(buf, this.packet);
      }

      public static PacketRedirection.Payload read(PacketByteBuf buf) {
         int dimensionIntId = buf.readVarInt();
         Packet<ClientPlayPacketListener> packet = (Packet<ClientPlayPacketListener>)PacketRedirection.PLACEHOLDER_PROTOCOL_INFO.codec().decode(buf);
         return new PacketRedirection.Payload(dimensionIntId, packet);
      }

      @Environment(EnvType.CLIENT)
      public void handle(ClientPlayPacketListener listener) {
         PacketRedirectionClient.handleRedirectedPacket(this.dimensionIntId, this.packet, listener);
      }

      @NotNull
      public Id<? extends CustomPayload> getId() {
         return TYPE;
      }
   }
}
