package qouteall.imm_ptl.peripheral.mixin.client.dim_stack;

import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.gui.screen.world.CreateWorldScreen.MoreTab;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.GridWidget.Adder;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;
import qouteall.imm_ptl.peripheral.ducks.IECreateWorldScreen;

@Mixin({MoreTab.class})
public class MixinCreateWorldScreenMoreTab_CVB {
   @Inject(
      method = {"<init>"},
      at = @At("RETURN"),
      locals = LocalCapture.CAPTURE_FAILHARD
   )
   private void onInitEnd(CreateWorldScreen createWorldScreen, CallbackInfo ci, Adder rowHelper) {
      rowHelper.add(
         ButtonWidget.builder(Text.translatable("imm_ptl.altius_screen_button"), button -> ((IECreateWorldScreen)(Object)createWorldScreen).ip_openDimStackScreen())
            .width(210)
            .build()
      );
   }
}
