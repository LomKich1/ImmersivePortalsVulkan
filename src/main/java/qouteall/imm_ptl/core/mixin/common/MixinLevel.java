package qouteall.imm_ptl.core.mixin.common;

import net.minecraft.entity.Entity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.MutableWorldProperties;
import net.minecraft.world.World;
import net.minecraft.world.entity.EntityLookup;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.ducks.IEWorld;

@Mixin({World.class})
public abstract class MixinLevel implements IEWorld {
   @Shadow
   @Final
   protected MutableWorldProperties properties;
   @Shadow
   protected float rainGradient;
   @Shadow
   protected float thunderGradient;
   @Shadow
   protected float lastRainGradient;
   @Shadow
   protected float lastThunderGradient;
   @Shadow
   @Final
   private Thread thread;

   @Shadow
   public abstract RegistryKey<World> getRegistryKey();

   @Shadow
   protected abstract EntityLookup<Entity> getEntityLookup();

   @Inject(
      method = {"Lnet/minecraft/world/World;initWeatherGradients()V"},
      at = @At("TAIL")
   )
   private void onInitWeatherGradients(CallbackInfo ci) {
      if (this.getRegistryKey() == World.NETHER) {
         this.rainGradient = 0.0F;
         this.lastRainGradient = 0.0F;
         this.thunderGradient = 0.0F;
         this.lastThunderGradient = 0.0F;
      }
   }

   @Override
   public MutableWorldProperties ip_getLevelData() {
      return this.properties;
   }

   @Override
   public void portal_setWeather(float rainGradPrev, float rainGrad, float thunderGradPrev, float thunderGrad) {
      this.lastRainGradient = rainGradPrev;
      this.rainGradient = rainGrad;
      this.lastThunderGradient = thunderGradPrev;
      this.thunderGradient = thunderGrad;
   }

   @Override
   public EntityLookup<Entity> portal_getEntityLookup() {
      return this.getEntityLookup();
   }

   @Override
   public Thread portal_getThread() {
      return this.thread;
   }
}
