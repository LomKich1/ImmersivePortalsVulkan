package qouteall.imm_ptl.peripheral.mixin.client.alternate_dimension;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.world.ClientWorld.class_5271;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.peripheral.alternate_dimension.AlternateDimensions;

@Mixin({class_5271.class})
public class MixinClientLevelData_CVB {
   @Inject(
      method = {"Lnet/minecraft/ClientWorld.Properties;getSkyDarknessHeight(Lnet/minecraft/HeightLimitView;)D"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onGetSkyDarknessHeight(CallbackInfoReturnable<Double> cir) {
      ClientWorld world = MinecraftClient.getInstance().world;

      assert world != null;

      boolean isAlternateDimension = AlternateDimensions.isAlternateDimension(world);
      if (isAlternateDimension) {
         cir.setReturnValue(-10000.0);
      }
   }
}
