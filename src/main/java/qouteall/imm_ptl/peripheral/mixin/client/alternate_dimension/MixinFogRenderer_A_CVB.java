package qouteall.imm_ptl.peripheral.mixin.client.alternate_dimension;

import net.minecraft.util.math.Vec3d;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.render.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import qouteall.imm_ptl.peripheral.alternate_dimension.AlternateDimensions;

@Mixin({FogRenderer.class})
public class MixinFogRenderer_A_CVB {
   @Redirect(
      method = {"getFogColor"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/Camera;getCameraPos()Lnet/minecraft/Vec3d;"
      )}
   )
   private Vec3d redirectCameraGetPos(Camera camera) {
      ClientWorld world = MinecraftClient.getInstance().world;
      return world != null && AlternateDimensions.isAlternateDimension(world)
         ? new Vec3d(camera.getCameraPos().x, Math.max(32.0, camera.getCameraPos().y), camera.getCameraPos().z)
         : camera.getCameraPos();
   }
}
