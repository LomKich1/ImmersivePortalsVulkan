package qouteall.imm_ptl.core.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.DebugHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.render.context_management.RenderStates;

@Mixin({DebugHud.class})
public class MixinDebugScreenOverlay {
   @WrapOperation(
      method = {"render"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/gui/hud/DebugHud;drawText(Lnet/minecraft/client/gui/DrawContext;Ljava/util/List;Z)V"
      )}
   )
   private void onRenderLines(DebugHud instance, DrawContext guiGraphics, List<String> lines, boolean leftAlign, Operation<Void> original) {
      if (!leftAlign && MinecraftClient.getInstance().debugHudEntryList.isF3Enabled()) {
         List<String> debugText = RenderStates.collectDebugText();
         if (IPGlobal.moveDebugTextToTop) {
            lines.addAll(0, debugText);
         } else {
            lines.addAll(debugText);
         }
      }

      original.call(new Object[]{instance, guiGraphics, lines, leftAlign});
   }
}
