package qouteall.imm_ptl.core.mixin.client.render;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.SkyRendering;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;

@Mixin({SkyRendering.class})
public class MixinSkyRenderer {
   @Redirect(
      method = {"isSkyDark"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/network/ClientPlayerEntity;getCameraPosVec(F)Lnet/minecraft/util/math/Vec3d;"
      )}
   )
   private Vec3d redirectGetEyePositionInSkyRendering(ClientPlayerEntity player, float partialTicks) {
      return WorldRenderInfo.isRendering() ? WorldRenderInfo.getCameraPos() : player.getCameraPosVec(partialTicks);
   }
}
