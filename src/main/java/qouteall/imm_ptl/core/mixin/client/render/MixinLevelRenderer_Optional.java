package qouteall.imm_ptl.core.mixin.client.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BuiltChunkStorage;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import qouteall.imm_ptl.core.render.context_management.RenderStates;

@Mixin(
   value = {WorldRenderer.class},
   priority = 1100
)
public class MixinLevelRenderer_Optional {
   @Shadow
   private BuiltChunkStorage chunks;
   @Shadow
   @Final
   private MinecraftClient client;

   @Inject(
      method = {"translucencySort"},
      at = {@At("HEAD")},
      cancellable = true,
      require = 0
   )
   private void onScheduleTranslucentSectionResort(Vec3d cameraPos, CallbackInfo ci) {
      if (PortalRendering.isRendering()) {
         ci.cancel();
      }
   }

   @Redirect(
      method = {"updateCamera"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/render/chunk/ChunkBuilder;setCameraPosition(Lnet/minecraft/util/math/Vec3d;)V"
      )},
      require = 0
   )
   private void onSetChunkBuilderCameraPosition(ChunkBuilder chunkBuilder, Vec3d cameraPosition) {
      if (!PortalRendering.isRendering() || this.client.world.getRegistryKey() != RenderStates.originalPlayerDimension) {
         chunkBuilder.setCameraPosition(cameraPosition);
      }
   }
}
