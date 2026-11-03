package qouteall.imm_ptl.core.mixin.client.render;

import net.minecraft.client.texture.Sprite;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.ducks.IEEntity;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import qouteall.imm_ptl.core.teleportation.ClientTeleportationManager;

@Mixin({InGameOverlayRenderer.class})
public class MixinScreenEffectRenderer {
   @Inject(
      method = {"Lnet/minecraft/InGameOverlayRenderer;renderInWallOverlay(Lnet/minecraft/Sprite;Lnet/minecraft/MatrixStack;Lnet/minecraft/VertexConsumerProvider;)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private static void onRenderInWallOverlay(Sprite sprite, MatrixStack matrices, VertexConsumerProvider bufferSource, CallbackInfo ci) {
      if (PortalRendering.isRendering()) {
         ci.cancel();
      }

      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      if (player != null && ((IEEntity)player).ip_getCollidingPortal() != null) {
         ci.cancel();
      }

      if (ClientTeleportationManager.isTeleportingFrequently()) {
         ci.cancel();
      }
   }
}
