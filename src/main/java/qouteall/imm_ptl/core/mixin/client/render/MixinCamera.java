package qouteall.imm_ptl.core.mixin.client.render;

import net.minecraft.world.attribute.EnvironmentAttributeInterpolator;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.render.Camera;
import net.minecraft.block.enums.CameraSubmersionType;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.ducks.IECamera;
import qouteall.imm_ptl.core.render.CrossPortalEntityRenderer;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;

@Mixin({Camera.class})
public abstract class MixinCamera implements IECamera {
   @Shadow
   private Vec3d pos;
   @Shadow
   private World area;
   @Shadow
   private Entity focusedEntity;
   @Shadow
   private float cameraY;
   @Shadow
   private float lastCameraY;
   @Shadow
   @Final
   private EnvironmentAttributeInterpolator environmentAttributeInterpolator;

   @Shadow
   protected abstract void setPos(Vec3d var1);

   @Shadow
   public abstract Entity getFocusedEntity();

   @Inject(
      method = {"Lnet/minecraft/Camera;update(Lnet/minecraft/World;Lnet/minecraft/Entity;ZZF)V"},
      at = {@At("RETURN")}
   )
   private void onUpdateFinished(World area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float partialTick, CallbackInfo ci) {
      Camera this_ = (Camera)this;
      WorldRenderInfo.adjustCameraPos(this_);
   }

   @Inject(
      method = {"Lnet/minecraft/Camera;getSubmersionType()Lnet/minecraft/CameraSubmersionType;"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void getSubmergedFluidState(CallbackInfoReturnable<CameraSubmersionType> cir) {
      if (PortalRendering.isRendering()) {
         cir.setReturnValue(CameraSubmersionType.field_27888);
         cir.cancel();
      }
   }

   @Inject(
      method = {"Lnet/minecraft/Camera;isThirdPerson()Z"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onIsThirdPerson(CallbackInfoReturnable<Boolean> cir) {
      if (CrossPortalEntityRenderer.shouldRenderPlayerDefault()) {
         cir.setReturnValue(true);
      }
   }

   @Override
   public void ip_resetState(Vec3d pos, ClientWorld currWorld) {
      this.setPos(pos);
      this.area = currWorld;
   }

   @Override
   public float ip_getCameraY() {
      return this.cameraY;
   }

   @Override
   public float ip_getLastCameraY() {
      return this.lastCameraY;
   }

   @Override
   public void ip_setCameraY(float cameraY_, float lastCameraY_) {
      this.cameraY = cameraY_;
      this.lastCameraY = lastCameraY_;
   }

   @Override
   public void portal_setPos(Vec3d pos) {
      this.setPos(pos);
   }

   @Override
   public void portal_setFocusedEntity(Entity arg) {
      this.focusedEntity = arg;
   }

   @Override
   public void portal_tickAttributeProbe() {
      if (this.area != null && this.pos != null) {
         this.environmentAttributeInterpolator.update(this.area, this.pos);
      }
   }
}
