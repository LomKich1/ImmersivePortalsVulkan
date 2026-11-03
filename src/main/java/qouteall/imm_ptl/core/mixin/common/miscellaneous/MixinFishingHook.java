package qouteall.imm_ptl.core.mixin.common.miscellaneous;

import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.FishingBobberEntity;
import org.spongepowered.asm.mixin.Mixin;
import qouteall.imm_ptl.core.api.ImmPtlEntityExtension;

@Mixin({FishingBobberEntity.class})
public class MixinFishingHook implements ImmPtlEntityExtension {
   @Override
   public boolean imm_ptl_canTeleportThroughPortal(Entity portal) {
      return false;
   }
}
