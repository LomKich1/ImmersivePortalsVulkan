package qouteall.imm_ptl.peripheral.mixin.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.resource.SplashTextResourceSupplier;
import net.minecraft.resource.ResourceManager;
import net.minecraft.text.Text;
import net.minecraft.util.profiler.Profiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({SplashTextResourceSupplier.class})
public class MixinSplashManager_CVB {
   @Shadow
   private List<Text> splashTexts;

   @Inject(
      method = {"apply(Ljava/util/List;Lnet/minecraft/resource/ResourceManager;Lnet/minecraft/util/profiler/Profiler;)V"},
      at = @At("RETURN")
   )
   private void onApply(List<Text> list, ResourceManager resourceManager, Profiler profiler, CallbackInfo ci) {
      List<Text> edited = new ArrayList<>(this.splashTexts);
      if (ip_removeFirst(edited, "Euclidian!")) {
         edited.add(Text.literal("Non-Euclidian!"));
      }

      if (ip_removeFirst(edited, "Slow acting portals!")) {
         edited.add(Text.literal("Fast acting portals!"));
         edited.add(Text.literal("Immersive Portals!"));
      }

      this.splashTexts = List.copyOf(edited);
   }

   private static boolean ip_removeFirst(List<Text> splashes, String text) {
      for (int i = 0; i < splashes.size(); i++) {
         if (splashes.get(i).getString().equals(text)) {
            splashes.remove(i);
            return true;
         }
      }

      return false;
   }
}
