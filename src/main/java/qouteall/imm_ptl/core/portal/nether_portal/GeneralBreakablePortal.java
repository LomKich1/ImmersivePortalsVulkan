package qouteall.imm_ptl.core.portal.nether_portal;

import net.minecraft.entity.EntityType;
import net.minecraft.world.World;
import qouteall.imm_ptl.core.portal.PortalPlaceholderBlock;

public class GeneralBreakablePortal extends BreakablePortalEntity {
   public static final EntityType<GeneralBreakablePortal> ENTITY_TYPE = createPortalEntityType(
      GeneralBreakablePortal::new, portalEntityTypeKey("general_breakable_portal")
   );

   public GeneralBreakablePortal(EntityType<?> entityType, World world) {
      super(entityType, world);
   }

   @Override
   protected boolean isPortalIntactOnThisSide() {
      boolean areaIntact = this.blockPortalShape
         .area
         .stream()
         .allMatch(blockPos -> this.getEntityWorld().getBlockState(blockPos).getBlock() == PortalPlaceholderBlock.instance);
      boolean frameIntact = this.blockPortalShape.frameAreaWithoutCorner.stream().allMatch(blockPos -> !this.getEntityWorld().isAir(blockPos));
      return areaIntact && frameIntact;
   }

   @Override
   protected void addSoundAndParticle() {
   }
}
