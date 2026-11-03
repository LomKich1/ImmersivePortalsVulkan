package qouteall.imm_ptl.core.mixin.common.collision;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.entity.EntityPose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import qouteall.imm_ptl.core.ducks.IEEntity;

@Mixin({PlayerEntity.class})
public abstract class MixinPlayer_Collision {
   @Overwrite
   public boolean canChangeIntoPose(EntityPose pose) {
      LivingEntity this_ = (LivingEntity)this;
      Box box = this_.getDimensions(pose).getBoxAt(this_.getEntityPos());
      Box activeCollisionBox = ((IEEntity)this_).ip_getActiveCollisionBox(box);
      return activeCollisionBox == null ? true : this_.getEntityWorld().isSpaceEmpty(this_, activeCollisionBox.contract(1.0E-7));
   }
}
