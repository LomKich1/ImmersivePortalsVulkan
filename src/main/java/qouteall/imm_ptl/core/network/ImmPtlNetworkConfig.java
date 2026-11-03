package qouteall.imm_ptl.core.network;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.CustomPayload.Id;
import net.minecraft.server.network.ServerPlayerConfigurationTask;
import net.minecraft.server.network.ServerPlayerConfigurationTask.Key;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.IPMcHelper;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.platform_specific.IPConfig;
import qouteall.imm_ptl.core.platform_specific.O_O;
import qouteall.q_misc_util.platform.ClientPlatform;
import qouteall.q_misc_util.platform.PlatformNetworking;

public class ImmPtlNetworkConfig {
   private static final Logger LOGGER = LogUtils.getLogger();
   public static ImmPtlNetworkConfig.ModVersion immPtlVersion;
   @Nullable
   private static ImmPtlNetworkConfig.ModVersion serverVersion = null;

   public static void init() {
      immPtlVersion = O_O.getImmPtlVersion();
      LOGGER.info("Immersive Portals Core version {}", immPtlVersion);
      PlatformNetworking.get().registerConfigurationS2C(ImmPtlNetworkConfig.S2CConfigStartPacket.TYPE, ImmPtlNetworkConfig.S2CConfigStartPacket.CODEC);
      PlatformNetworking.get().registerConfigurationC2S(ImmPtlNetworkConfig.C2SConfigCompletePacket.TYPE, ImmPtlNetworkConfig.C2SConfigCompletePacket.CODEC);
      PlatformNetworking.get()
         .onServerConfigure(
            (handler, server) -> {
               if (handler.canSend(ImmPtlNetworkConfig.S2CConfigStartPacket.TYPE)) {
                  handler.addTask(new ImmPtlNetworkConfig.ImmPtlConfigurationTask());
               } else if (server.isDedicated()) {
                  if (IPConfig.getConfig().serverRejectClientWithoutImmPtl) {
                     handler.disconnect(
                        Text.literal(
                           "The server detected that client does not install Immersive Portals mod.\nA server with Immersive Portals mod only works with the clients that have it.\n\n(Note: The networking sync may be interfered by Essential mod or other mods. When you are using these mods, the detection may malfunction. In this case, you can disable networking check in the server side by changing `serverRejectClientWithoutImmPtl` to `false` in the server's config file `config/immersive_portals.json` and restart.)\n"
                        )
                     );
                  } else {
                     GameProfile gameProfile = handler.gameProfile();
                     LOGGER.warn("Fabric API's sendable channel sync detected that client does not install ImmPtl. {} {}", gameProfile.name(), gameProfile.id());
                  }
               } else {
                  LOGGER.error(
                     "ImmPtl configuration channel is non-sendable from Fabric API in integrated server. Fabric API sendable channel sync is interfered."
                  );
               }
            }
         );
      PlatformNetworking.get()
         .registerServerConfigurationReceiver(ImmPtlNetworkConfig.C2SConfigCompletePacket.TYPE, ImmPtlNetworkConfig.C2SConfigCompletePacket::handle);
   }

   @Environment(EnvType.CLIENT)
   public static void initClient() {
      PlatformNetworking.get()
         .registerClientConfigurationReceiver(ImmPtlNetworkConfig.S2CConfigStartPacket.TYPE, ImmPtlNetworkConfig.S2CConfigStartPacket::handle);
      ClientPlatform.get().onClientLoginInit(() -> {
         LOGGER.info("Client login init");
         serverVersion = null;
      });
      ClientPlatform.get().onClientPlayJoin(handler -> onClientJoin());
   }

   private static void onClientJoin() {
      if (serverVersion == null) {
         warnServerMissingImmPtl();
      } else if (serverVersion.isNormalVersion()
         && immPtlVersion.isNormalVersion()
         && !serverVersion.equals(immPtlVersion)
         && IPConfig.getConfig().shouldDisplayWarning("mod_version_mismatch")) {
         MutableText text = Text.translatable(
               "imm_ptl.mod_patch_version_mismatch",
               new Object[]{
                  Text.literal(serverVersion.toString()).formatted(Formatting.GOLD), Text.literal(immPtlVersion.toString()).formatted(Formatting.GOLD)
               }
            )
            .append(IPMcHelper.getDisableWarningText("mod_version_mismatch"));
         CHelper.printChat(text);
      }
   }

   public static boolean doesServerHaveImmPtl() {
      return serverVersion != null;
   }

   private static void warnServerMissingImmPtl() {
      MinecraftClient.getInstance().execute(() -> CHelper.printChat(Text.translatable("imm_ptl.server_missing_immptl")));
   }

   public record C2SConfigCompletePacket(ImmPtlNetworkConfig.ModVersion versionFromClient, boolean clientTolerantVersionMismatch) implements CustomPayload {
      public static final Id<ImmPtlNetworkConfig.C2SConfigCompletePacket> TYPE = new Id(McHelper.newResourceLocation("iportal:configure_complete"));
      public static final PacketCodec<PacketByteBuf, ImmPtlNetworkConfig.C2SConfigCompletePacket> CODEC = PacketCodec.ofStatic(
         (b, p) -> p.write(b), ImmPtlNetworkConfig.C2SConfigCompletePacket::read
      );

      public static ImmPtlNetworkConfig.C2SConfigCompletePacket read(PacketByteBuf buf) {
         ImmPtlNetworkConfig.ModVersion info = ImmPtlNetworkConfig.ModVersion.read(buf);
         boolean clientTolerantVersionMismatch = buf.readBoolean();
         return new ImmPtlNetworkConfig.C2SConfigCompletePacket(info, clientTolerantVersionMismatch);
      }

      public void write(PacketByteBuf buf) {
         this.versionFromClient.write(buf);
         buf.writeBoolean(this.clientTolerantVersionMismatch);
      }

      public void handle(PlatformNetworking.ServerConfigurationContext context) {
         GameProfile gameProfile = context.gameProfile();
         ImmPtlNetworkConfig.LOGGER
            .info(
               "Server received ImmPtl config packet. Mod version: {} Player: {} {}",
               new Object[]{this.versionFromClient, gameProfile.name(), gameProfile.id()}
            );
         if (this.versionFromClient.isNormalVersion()
            && ImmPtlNetworkConfig.immPtlVersion.isNormalVersion()
            && (
               this.versionFromClient.major != ImmPtlNetworkConfig.immPtlVersion.major
                  || this.versionFromClient.minor != ImmPtlNetworkConfig.immPtlVersion.minor
            )
            && !IPConfig.getConfig().serverTolerantVersionMismatchWithClient
            && !this.clientTolerantVersionMismatch) {
            context.disconnect(
               Text.translatable(
                  "imm_ptl.mod_major_minor_version_mismatch", new Object[]{ImmPtlNetworkConfig.immPtlVersion.toString(), this.versionFromClient.toString()}
               )
            );
            ImmPtlNetworkConfig.LOGGER
               .info(
                  "Disconnecting client because of ImmPtl version difference (only patch version difference is tolerated).\nGame Profile: {}\nClient ImmPtl version: {}\nServer ImmPtl version: {}",
                  new Object[]{gameProfile, this.versionFromClient, ImmPtlNetworkConfig.immPtlVersion}
               );
         } else {
            context.completeTask(ImmPtlNetworkConfig.ImmPtlConfigurationTask.TYPE);
         }
      }

      @NotNull
      public Id<? extends CustomPayload> getId() {
         return TYPE;
      }
   }

   public record ImmPtlConfigurationTask() implements ServerPlayerConfigurationTask {
      public static final Key TYPE = new Key("iportal:config");

      public void sendPacket(Consumer<Packet<?>> sender) {
         sender.accept(PlatformNetworking.get().createConfigurationS2CPacket(new ImmPtlNetworkConfig.S2CConfigStartPacket(ImmPtlNetworkConfig.immPtlVersion)));
      }

      @NotNull
      public Key getKey() {
         return TYPE;
      }
   }

   public record ModVersion(int major, int minor, int patch) {
      public static final ImmPtlNetworkConfig.ModVersion OTHER = new ImmPtlNetworkConfig.ModVersion(0, 0, 0);

      public static ImmPtlNetworkConfig.ModVersion read(PacketByteBuf buf) {
         int major = buf.readVarInt();
         int minor = buf.readVarInt();
         int patch = buf.readVarInt();
         return new ImmPtlNetworkConfig.ModVersion(major, minor, patch);
      }

      public void write(PacketByteBuf buf) {
         buf.writeVarInt(this.major);
         buf.writeVarInt(this.minor);
         buf.writeVarInt(this.patch);
      }

      @Override
      public String toString() {
         return "%d.%d.%d".formatted(this.major, this.minor, this.patch);
      }

      public boolean isNormalVersion() {
         return !OTHER.equals(this);
      }

      public boolean isCompatibleWith(ImmPtlNetworkConfig.ModVersion another) {
         return this.major == another.major && this.minor == another.minor;
      }
   }

   public record S2CConfigStartPacket(ImmPtlNetworkConfig.ModVersion versionFromServer) implements CustomPayload {
      public static final Id<ImmPtlNetworkConfig.S2CConfigStartPacket> TYPE = new Id(McHelper.newResourceLocation("iportal:config_packet"));
      public static final PacketCodec<PacketByteBuf, ImmPtlNetworkConfig.S2CConfigStartPacket> CODEC = PacketCodec.ofStatic(
         (b, p) -> p.write(b), ImmPtlNetworkConfig.S2CConfigStartPacket::read
      );

      public static ImmPtlNetworkConfig.S2CConfigStartPacket read(PacketByteBuf buf) {
         ImmPtlNetworkConfig.ModVersion info = ImmPtlNetworkConfig.ModVersion.read(buf);
         return new ImmPtlNetworkConfig.S2CConfigStartPacket(info);
      }

      public void write(PacketByteBuf buf) {
         this.versionFromServer.write(buf);
      }

      @Environment(EnvType.CLIENT)
      public void handle(Consumer<CustomPayload> responseSender) {
         ImmPtlNetworkConfig.LOGGER.info("Client received ImmPtl config packet. Server mod version: {}", this.versionFromServer);
         ImmPtlNetworkConfig.serverVersion = this.versionFromServer;
         responseSender.accept(
            new ImmPtlNetworkConfig.C2SConfigCompletePacket(ImmPtlNetworkConfig.immPtlVersion, IPConfig.getConfig().clientTolerantVersionMismatchWithServer)
         );
      }

      @NotNull
      public Id<? extends CustomPayload> getId() {
         return TYPE;
      }
   }
}
