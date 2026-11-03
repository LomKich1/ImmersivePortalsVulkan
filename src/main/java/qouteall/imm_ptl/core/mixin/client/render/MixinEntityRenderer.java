package qouteall.imm_ptl.core.mixin.client.render;

import net.minecraft.entity.Entity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;

@Mixin({EntityRenderer.class})
public class MixinEntityRenderer {
   @Redirect(
      method = {"updateRenderState"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/MinecraftClient;hasOutline(Lnet/minecraft/Entity;)Z"
      )}
   )
   private boolean redirectGlowing(MinecraftClient client, Entity entity) {
      return WorldRenderInfo.isRendering() ? false : client.hasOutline(entity);
   }
}
