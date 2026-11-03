package qouteall.imm_ptl.core.mixin.client.render;

import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import qouteall.imm_ptl.core.render.ForceMainThreadRebuild;

@Mixin({WorldRenderer.class})
public class MixinLevelRenderer_ForceMainThreadRebuild {
   @ModifyVariable(
      method = {"updateChunks"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/option/GameOptions;getChunkBuilderMode()Lnet/minecraft/client/option/SimpleOption;",
         ordinal = 0
      )},
      ordinal = 1,
      require = 0
   )
   private boolean modifyShouldImmediatelyRebuild(boolean originalValue) {
      return ForceMainThreadRebuild.isCurrentFrameForceMainThreadRebuild() ? true : originalValue;
   }
}
