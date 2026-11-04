package qouteall.imm_ptl.core.mixin.client.multiworld_awareness;

import net.minecraft.client.render.fog.AtmosphericFogModifier;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.render.context_management.FogRendererContext;

@Mixin(
   value = {AtmosphericFogModifier.class},
   priority = 1100
)
public class MixinAtmosphericFogEnvironment {
   @Shadow
   private float fogMultiplier;
   @Unique
   @Nullable
   private static AtmosphericFogModifier ip_instance;

   @Inject(
      method = {"<init>"},
      at = @At("RETURN")
   )
   private void onInit(CallbackInfo ci) {
      ip_instance = (AtmosphericFogModifier)(Object)this;
   }

   static {
      FogRendererContext.copyContextFromObject = context -> {
         if (ip_instance != null) {
            ((MixinAtmosphericFogEnvironment)(Object)ip_instance).fogMultiplier = context.rainFogMultiplier;
         }
      };
      FogRendererContext.copyContextToObject = context -> {
         if (ip_instance != null) {
            context.rainFogMultiplier = ((MixinAtmosphericFogEnvironment)(Object)ip_instance).fogMultiplier;
         }
      };
      FogRendererContext.init();
   }
}
