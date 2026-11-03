package qouteall.dimlib.mixin.common;

import com.mojang.serialization.Lifecycle;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.dimension.DimensionOptionsRegistryHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.dimlib.DimensionImpl;

@Mixin({DimensionOptionsRegistryHolder.class})
public class MixinWorldDimensions {
   @Inject(
      method = {"isVanilla"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private static void onIsVanillaLike(RegistryKey<DimensionOptions> resourceKey, DimensionOptions levelStem, CallbackInfoReturnable<Boolean> cir) {
      String namespace = resourceKey.getValue().getNamespace();
      if (DimensionImpl.STABLE_NAMESPACES.contains(namespace)) {
         cir.setReturnValue(true);
      }
   }

   @Redirect(
      method = {"toConfig"},
      at = {@At(
         value = "INVOKE",
         target = "Lcom/mojang/serialization/Lifecycle;experimental()Lcom/mojang/serialization/Lifecycle;",
         remap = false
      )}
   )
   private Lifecycle redirectLifecycle() {
      return Lifecycle.stable();
   }
}
