package qouteall.imm_ptl.core.mixin.common.collision;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCollisionHandler.Impl;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.IPMcHelper;
import qouteall.imm_ptl.core.api.ImmPtlEntityExtension;
import qouteall.imm_ptl.core.collision.PortalCollisionHandler;
import qouteall.imm_ptl.core.ducks.IEEntity;
import qouteall.imm_ptl.core.miscellaneous.IPVanillaCopy;
import qouteall.imm_ptl.core.portal.EndPortalEntity;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.my_util.CountDownInt;

@Mixin({Entity.class})
public abstract class MixinEntity implements IEEntity, ImmPtlEntityExtension {
   @Unique
   @Nullable
   private PortalCollisionHandler ip_portalCollisionHandler;
   @Shadow
   private World world;
   @Shadow
   public int age;
   @Shadow
   private Vec3d pos;
   @Shadow
   private BlockPos blockPos;
   @Shadow
   private ChunkPos chunkPos;
   @Shadow
   @Final
   private static Logger LOGGER;
   @Shadow
   @Nullable
   private BlockState stateAtPos;
   @Unique
   private static final CountDownInt IMM_PTL_LOG_COUNTER = new CountDownInt(20);

   @Shadow
   protected abstract Vec3d adjustMovementForCollisions(Vec3d var1);

   @Shadow
   public abstract Text getName();

   @Shadow
   public abstract double getX();

   @Shadow
   public abstract double getY();

   @Shadow
   public abstract double getZ();

   @Shadow
   protected abstract void unsetRemoved();

   @Redirect(
      method = {"Lnet/minecraft/entity/Entity;move(Lnet/minecraft/entity/MovementType;Lnet/minecraft/util/math/Vec3d;)V"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/entity/Entity;adjustMovementForCollisions(Lnet/minecraft/util/math/Vec3d;)Lnet/minecraft/util/math/Vec3d;"
      )
   )
   private Vec3d redirectHandleCollisions(Entity entity, Vec3d attemptedMove) {
      if (IPGlobal.enableServerCollision || entity.getEntityWorld().isClient()) {
         if (attemptedMove.lengthSquared() > 3600.0) {
            if (IMM_PTL_LOG_COUNTER.tryDecrement()) {
               LOGGER.error(
                  "[ImmPtl] Skipping collision calculation because entity moves too fast {} {} {}",
                  new Object[]{entity, attemptedMove, entity.getEntityWorld().getTime(), new Throwable()}
               );
            }

            return Vec3d.ZERO;
         } else if (IPGlobal.crossPortalCollision && this.ip_portalCollisionHandler != null && this.ip_portalCollisionHandler.hasCollisionEntry()) {
            Vec3d result = this.ip_portalCollisionHandler.handleCollision((Entity)(Object)this, attemptedMove);
            if (result.lengthSquared() > 400.0) {
               if (IMM_PTL_LOG_COUNTER.tryDecrement()) {
                  LOGGER.error("[ImmPtl] cross portal collision result too large {} {} {}", new Object[]{this, attemptedMove, result});
               }

               return Vec3d.ZERO;
            } else {
               return result;
            }
         } else {
            return this.adjustMovementForCollisions(attemptedMove);
         }
      } else {
         return entity instanceof PlayerEntity ? attemptedMove : Vec3d.ZERO;
      }
   }

   @Inject(
      method = {"Lnet/minecraft/entity/Entity;isFireImmune()Z"},
      at = @At("HEAD"),
      cancellable = true
   )
   private void onIsFireImmune(CallbackInfoReturnable<Boolean> cir) {
      if (this.ip_getCollidingPortal() instanceof EndPortalEntity) {
         cir.setReturnValue(true);
         cir.cancel();
      }
   }

   @Shadow
   protected abstract Box calculateDefaultBoundingBox(Vec3d var1);

   @WrapOperation(
      method = {"checkBlockCollision(Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/entity/EntityCollisionHandler$Impl;Lit/unimi/dsi/fastutil/longs/LongSet;I)I"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/entity/Entity;calculateDefaultBoundingBox(Lnet/minecraft/util/math/Vec3d;)Lnet/minecraft/util/math/Box;"
      )
   )
   private Box redirectBoundingBoxInCheckingBlockCollision(Entity entity, Vec3d pos, Operation<Box> original) {
      return this.ip_getActiveCollisionBox((Box)original.call(new Object[]{entity, pos}));
   }

   @Inject(
      method = {"checkBlockCollision(Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/entity/EntityCollisionHandler$Impl;Lit/unimi/dsi/fastutil/longs/LongSet;I)I"},
      at = @At("HEAD"),
      cancellable = true
   )
   private void onCheckInsideBlocks(Vec3d from, Vec3d to, Impl collector, LongSet visited, int steps, CallbackInfoReturnable<Integer> cir) {
      if (this.ip_getActiveCollisionBox(this.calculateDefaultBoundingBox(to)) == null) {
         cir.setReturnValue(0);
      }
   }

   @Inject(
      method = {"Lnet/minecraft/entity/Entity;isInsideWall()Z"},
      at = @At("HEAD"),
      cancellable = true
   )
   private void onIsInsideWall(CallbackInfoReturnable<Boolean> cir) {
      if (this.ip_isRecentlyCollidingWithPortal()) {
         cir.setReturnValue(false);
      }
   }

   @Inject(
      method = {"Lnet/minecraft/entity/Entity;setPos(DDD)V"},
      at = @At("HEAD")
   )
   private void onSetPos(double nx, double ny, double nz, CallbackInfo ci) {
      Entity this_ = (Entity)(Object)this;
      if (this_ instanceof PlayerEntity
         && IPGlobal.teleportationDebugEnabled
         && (Math.abs(this.getX() - nx) > 10.0 || Math.abs(this.getY() - ny) > 10.0 || Math.abs(this.getZ() - nz) > 10.0)) {
         Helper.log(
            String.format(
               "%s %s teleported from %s %s %s to %s %s %s",
               this.getName().getContent(),
               this.world.getRegistryKey(),
               (int)this.getX(),
               (int)this.getY(),
               (int)this.getZ(),
               (int)nx,
               (int)ny,
               (int)nz
            )
         );
         new Throwable().printStackTrace();
      }
   }

   @Inject(
      method = {"getBlockStateAtPos"},
      at = @At("HEAD"),
      cancellable = true
   )
   private void onGetInBlockState(CallbackInfoReturnable<BlockState> cir) {
      Portal collidingPortal = this.ip_getCollidingPortal();
      Entity this_ = (Entity)(Object)this;
      if (collidingPortal != null && collidingPortal.getNormal().y > 0.0) {
         BlockPos remoteLandingPos = BlockPos.ofFloored(collidingPortal.transformPoint(this_.getEntityPos()));
         World destinationWorld = collidingPortal.getDestinationWorld();
         if (destinationWorld.isChunkLoaded(remoteLandingPos)) {
            BlockState result = destinationWorld.getBlockState(remoteLandingPos);
            if (!result.isAir()) {
               cir.setReturnValue(result);
               cir.cancel();
            }
         }
      }
   }

   @Override
   public Portal ip_getCollidingPortal() {
      if (this.ip_portalCollisionHandler == null) {
         return null;
      } else {
         return this.ip_portalCollisionHandler.portalCollisions.isEmpty() ? null : this.ip_portalCollisionHandler.portalCollisions.get(0).portal;
      }
   }

   @Override
   public void ip_tickCollidingPortal() {
      Entity this_ = (Entity)(Object)this;
      if (this.ip_portalCollisionHandler != null) {
         this.ip_portalCollisionHandler.update(this_);
      }

      if (this.world.isClient()) {
         IPMcHelper.onClientEntityTick(this_);
      }
   }

   @Override
   public void ip_notifyCollidingWithPortal(Entity portal) {
      Entity this_ = (Entity)(Object)this;
      if (this.ip_portalCollisionHandler == null) {
         this.ip_portalCollisionHandler = new PortalCollisionHandler();
      }

      this.ip_portalCollisionHandler.notifyCollidingWithPortal(this_, (Portal)portal);
   }

   @Override
   public boolean ip_isCollidingWithPortal() {
      return this.ip_portalCollisionHandler == null ? false : this.ip_portalCollisionHandler.hasCollisionEntry();
   }

   @Override
   public boolean ip_isRecentlyCollidingWithPortal() {
      return this.ip_portalCollisionHandler == null ? false : this.ip_portalCollisionHandler.isRecentlyCollidingWithPortal((Entity)(Object)this);
   }

   @Override
   public void ip_unsetRemoved() {
      this.unsetRemoved();
   }

   @IPVanillaCopy
   @Override
   public void ip_setPositionWithoutTriggeringCallback(Vec3d newPos) {
      double x = newPos.x;
      double y = newPos.y;
      double z = newPos.z;
      if (this.pos.x != x || this.pos.y != y || this.pos.z != z) {
         this.pos = new Vec3d(x, y, z);
         int bx = MathHelper.floor(x);
         int by = MathHelper.floor(y);
         int bz = MathHelper.floor(z);
         if (bx != this.blockPos.getX() || by != this.blockPos.getY() || bz != this.blockPos.getZ()) {
            this.blockPos = new BlockPos(bx, by, bz);
            this.stateAtPos = null;
            if (ChunkSectionPos.getSectionCoord(bx) != this.chunkPos.x || ChunkSectionPos.getSectionCoord(bz) != this.chunkPos.z) {
               this.chunkPos = new ChunkPos(this.blockPos);
            }
         }
      }
   }

   @Override
   public void ip_clearCollidingPortal() {
      this.ip_portalCollisionHandler = null;
   }

   @Nullable
   @Override
   public Box ip_getActiveCollisionBox(Box originalBox) {
      Entity this_ = (Entity)(Object)this;
      return this.ip_portalCollisionHandler == null ? originalBox : this.ip_portalCollisionHandler.getActiveCollisionBox(this_, originalBox);
   }

   @Nullable
   @Override
   public PortalCollisionHandler ip_getPortalCollisionHandler() {
      return this.ip_portalCollisionHandler;
   }

   @Override
   public PortalCollisionHandler ip_getOrCreatePortalCollisionHandler() {
      if (this.ip_portalCollisionHandler == null) {
         this.ip_portalCollisionHandler = new PortalCollisionHandler();
      }

      return this.ip_portalCollisionHandler;
   }

   @Override
   public void ip_setPortalCollisionHandler(@Nullable PortalCollisionHandler handler) {
      this.ip_portalCollisionHandler = handler;
   }

   @Override
   public void ip_setWorld(World world) {
      this.world = world;
   }

   private long ip_getStableTiming() {
      return this.age;
   }
}
