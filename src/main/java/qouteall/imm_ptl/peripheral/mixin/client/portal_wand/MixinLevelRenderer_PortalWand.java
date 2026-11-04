package qouteall.imm_ptl.peripheral.mixin.client.portal_wand;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.VertexConsumerProvider.Immediate;
import net.minecraft.client.render.state.WorldRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.peripheral.wand.PortalWandItem;

@Mixin({WorldRenderer.class})
public class MixinLevelRenderer_PortalWand {
   @Inject(
      method = {"lambda$addMainPass$1", "method_62214"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/render/gizmo/GizmoDrawerImpl;draw(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;Lnet/minecraft/client/render/state/CameraRenderState;Lorg/joml/Matrix4f;)V",
         shift = Shift.AFTER
      ),
      require = 1
   )
   private void onRender(
      CallbackInfo ci, @Local MatrixStack poseStack, @Local(ordinal = 0) Immediate bufferSource, @Local(argsOnly = true) WorldRenderState levelRenderState
   ) {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      if (player != null) {
         ItemStack itemStack = player.getMainHandStack();
         if (itemStack.getItem() == PortalWandItem.instance) {
            Vec3d camPos = levelRenderState.cameraRenderState.pos;
            PortalWandItem.clientRender(player, itemStack, poseStack, bufferSource, camPos.x, camPos.y, camPos.z);
         }
      }
   }
}
