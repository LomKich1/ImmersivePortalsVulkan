package qouteall.imm_ptl.core.mixin.common;

import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerChunkManager;
import net.minecraft.server.world.ServerEntityManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.level.ServerWorldProperties;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.chunk_loading.ImmPtlChunkTracking;
import qouteall.imm_ptl.core.ducks.IEEntity;
import qouteall.imm_ptl.core.ducks.IEServerWorld;

@Mixin({ServerWorld.class})
public abstract class MixinServerLevel implements IEServerWorld {
   @Shadow
   @Final
   private ServerWorldProperties worldProperties;
   @Shadow
   @Final
   private ServerEntityManager<Entity> entityManager;

   @Shadow
   public abstract PersistentStateManager getPersistentStateManager();

   @Shadow
   public abstract ServerChunkManager getChunkManager();

   @Redirect(
      method = {"Lnet/minecraft/server/world/ServerWorld;tick(Ljava/util/function/BooleanSupplier;)V"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/server/world/ServerChunkManager;shouldResetIdleTimeout()Z"
      )
   )
   private boolean redirectHasActiveTickets(ServerChunkManager chunkSource) {
      ServerWorld this_ = (ServerWorld)(Object)this;
      return ImmPtlChunkTracking.shouldLoadDimension(this_.getRegistryKey()) ? true : chunkSource.shouldResetIdleTimeout();
   }

   @Inject(
      method = {"Lnet/minecraft/server/world/ServerWorld;toString()Ljava/lang/String;"},
      at = @At("HEAD"),
      cancellable = true
   )
   private void onToString(CallbackInfoReturnable<String> cir) {
      ServerWorld this_ = (ServerWorld)(Object)this;
      cir.setReturnValue("ServerWorld " + this_.getRegistryKey().getValue() + " " + this.worldProperties.getLevelName());
   }

   @Inject(
      method = {"tickEntity"},
      at = @At("HEAD")
   )
   private void onTickNonPassenger(Entity entity, CallbackInfo ci) {
      ((IEEntity)(Object)entity).ip_tickCollidingPortal();
   }

   @Override
   public ServerEntityManager<Entity> ip_getEntityManager() {
      return this.entityManager;
   }
}
