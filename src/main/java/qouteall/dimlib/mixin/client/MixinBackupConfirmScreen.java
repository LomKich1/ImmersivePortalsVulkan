package qouteall.dimlib.mixin.client;

import eu.midnightdust.lib.config.MidnightConfig;
import java.util.Objects;
import net.minecraft.text.Text;
import net.minecraft.client.gui.screen.world.BackupPromptScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CheckboxWidget;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.font.MultilineText;
import net.minecraft.client.gui.screen.world.BackupPromptScreen.class_406;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.dimlib.config.DimLibConfig;

@Mixin({BackupPromptScreen.class})
public class MixinBackupConfirmScreen extends Screen {
   @Shadow
   @Final
   protected class_406 callback;
   @Shadow
   private CheckboxWidget eraseCacheCheckbox;
   @Shadow
   private MultilineText wrappedText;
   @Unique
   private boolean dimlib_isExperimentalWarning = false;

   protected MixinBackupConfirmScreen(Text title) {
      super(title);
      throw new RuntimeException();
   }

   @Inject(
      method = {"<init>(Ljava/lang/Runnable;Lnet/minecraft/BackupPromptScreen.Callback;Lnet/minecraft/Text;Lnet/minecraft/Text;Lnet/minecraft/Text;Z)V"},
      at = {@At("RETURN")}
   )
   private void onInitEnd(
      Runnable runnable, class_406 listener, Text component, Text component2, Text confirmation, boolean bl, CallbackInfo ci
   ) {
      this.dimlib_isExperimentalWarning = Objects.equals(component, Text.translatable("selectWorld.backupQuestion.experimental"));
   }

   @Inject(
      method = {"init"},
      at = {@At("RETURN")}
   )
   private void onInitEnd(CallbackInfo ci) {
      if (this.dimlib_isExperimentalWarning) {
         int i = (this.wrappedText.getLineCount() + 1) * 9;
         this.addDrawableChild(ButtonWidget.builder(Text.translatable("dimlib.i_know_what_i_am_doing_and_disable_warning"), button -> {
            DimLibConfig.suppressExperimentalWarning = true;
            MidnightConfig.write("dimlib");
            this.callback.proceed(false, this.eraseCacheCheckbox.isChecked());
         }).dimensions(this.width / 2 - 200, 124 + i + 40, 400, 20).build());
      }
   }
}
