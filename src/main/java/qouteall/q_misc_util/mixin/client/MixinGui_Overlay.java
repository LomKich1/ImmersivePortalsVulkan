package qouteall.q_misc_util.mixin.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.q_misc_util.CustomTextOverlay;

@Mixin({InGameHud.class})
public class MixinGui_Overlay {
   @Shadow
   @Final
   private MinecraftClient client;

   @Inject(
      method = {"render"},
      at = {@At("RETURN")}
   )
   private void onRender(DrawContext guiGraphics, RenderTickCounter deltaTracker, CallbackInfo ci) {
      if (!this.client.options.hudHidden) {
         CustomTextOverlay.render(guiGraphics, deltaTracker);
      }
   }
}
