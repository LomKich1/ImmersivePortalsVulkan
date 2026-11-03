package qouteall.imm_ptl.core.mixin.common.collision;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.LazyEntityReference;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.world.World;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin({ProjectileEntity.class})
public abstract class MixinProjectile extends MixinEntity {
   @WrapOperation(
      method = {"getOwner"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/LazyEntityReference;getEntity(Lnet/minecraft/LazyEntityReference;Lnet/minecraft/World;)Lnet/minecraft/Entity;"
      )}
   )
   private Entity wrapGetOwnerEntity(LazyEntityReference<Entity> owner, World level, Operation<Entity> original) {
      Entity entity = (Entity)original.call(new Object[]{owner, level});
      if (entity == null && owner != null) {
         if (level instanceof ServerWorld serverLevel) {
            MinecraftServer server = serverLevel.getServer();

            for (ServerWorld world : server.getWorlds()) {
               if (world != serverLevel) {
                  Entity found = LazyEntityReference.getEntity(owner, world);
                  if (found != null) {
                     return found;
                  }
               }
            }
         }

         return null;
      } else {
         return entity;
      }
   }
}
