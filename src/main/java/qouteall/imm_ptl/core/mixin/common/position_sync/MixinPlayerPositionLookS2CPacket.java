package qouteall.imm_ptl.core.mixin.common.position_sync;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.ducks.IEPlayerPositionLookS2CPacket;
import qouteall.imm_ptl.core.network.ImmPtlNetworkConfig;

@Mixin({PlayerPositionLookS2CPacket.class})
public class MixinPlayerPositionLookS2CPacket implements IEPlayerPositionLookS2CPacket {
   @Shadow
   @Final
   @Mutable
   public static PacketCodec<PacketByteBuf, PlayerPositionLookS2CPacket> CODEC;
   @Unique
   private RegistryKey<World> playerDimension;

   @Override
   public RegistryKey<World> ip_getPlayerDimension() {
      return this.playerDimension;
   }

   @Override
   public void ip_setPlayerDimension(RegistryKey<World> dimension) {
      this.playerDimension = dimension;
   }

   @Inject(
      method = {"<clinit>"},
      at = {@At("TAIL")}
   )
   private static void onClassInit(CallbackInfo ci) {
      PacketCodec<PacketByteBuf, PlayerPositionLookS2CPacket> vanillaCodec = CODEC;
      CODEC = PacketCodec.ofStatic((buf, packet) -> {
         vanillaCodec.encode(buf, packet);
         buf.writeRegistryKey(((IEPlayerPositionLookS2CPacket)packet).ip_getPlayerDimension());
      }, buf -> {
         PlayerPositionLookS2CPacket packet = (PlayerPositionLookS2CPacket)vanillaCodec.decode(buf);
         if (ImmPtlNetworkConfig.doesServerHaveImmPtl()) {
            RegistryKey<World> dimension = buf.readRegistryKey(RegistryKeys.WORLD);
            ((IEPlayerPositionLookS2CPacket)packet).ip_setPlayerDimension(dimension);
         }

         return packet;
      });
   }
}
