package qouteall.imm_ptl.core.mixin.client.sync;

import net.minecraft.world.World;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.RegistryKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.ducks.IEPlayerMoveC2SPacket;

@Mixin({PlayerMoveC2SPacket.class})
public class MixinServerBoundMovePlayerPacket {
   @Inject(
      method = {"<init>"},
      at = {@At("RETURN")}
   )
   private void onConstruct(CallbackInfo ci) {
      RegistryKey<World> dimension = MinecraftClient.getInstance().player.getEntityWorld().getRegistryKey();
      ((IEPlayerMoveC2SPacket)this).ip_setPlayerDimension(dimension);
   }
}
