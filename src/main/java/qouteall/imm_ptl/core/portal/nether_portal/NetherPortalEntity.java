package qouteall.imm_ptl.core.portal.nether_portal;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.EntityType;
import net.minecraft.world.World;
import net.minecraft.block.Blocks;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.block.NetherPortalBlock;
import net.minecraft.util.math.Vec3d;
import net.minecraft.block.BlockState;
import net.minecraft.sound.SoundEvents;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.math.Direction.class_2351;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.platform_specific.O_O;
import qouteall.imm_ptl.core.portal.PortalPlaceholderBlock;
import qouteall.q_misc_util.my_util.DQuaternion;

public class NetherPortalEntity extends BreakablePortalEntity {
   private static final BreakablePortalEntity.OverlayInfo overlay_x = new BreakablePortalEntity.OverlayInfo(
      (BlockState)Blocks.field_10316.getDefaultState().with(NetherPortalBlock.AXIS, class_2351.field_11051), 0.5, 0.0, null
   );
   private static final BreakablePortalEntity.OverlayInfo overlay_y_up = new BreakablePortalEntity.OverlayInfo(
      (BlockState)Blocks.field_10316.getDefaultState().with(NetherPortalBlock.AXIS, class_2351.field_11048),
      0.5,
      1.0,
      DQuaternion.rotationByDegrees(new Vec3d(1.0, 0.0, 0.0), 90.0)
   );
   private static final BreakablePortalEntity.OverlayInfo overlay_y_down = new BreakablePortalEntity.OverlayInfo(
      (BlockState)Blocks.field_10316.getDefaultState().with(NetherPortalBlock.AXIS, class_2351.field_11048),
      0.5,
      -1.0,
      DQuaternion.rotationByDegrees(new Vec3d(1.0, 0.0, 0.0), 90.0)
   );
   private static final BreakablePortalEntity.OverlayInfo overlay_z = new BreakablePortalEntity.OverlayInfo(
      (BlockState)Blocks.field_10316.getDefaultState().with(NetherPortalBlock.AXIS, class_2351.field_11048), 0.5, 0.0, null
   );
   public static final EntityType<NetherPortalEntity> ENTITY_TYPE = createPortalEntityType(NetherPortalEntity::new, portalEntityTypeKey("nether_portal_new"));

   public NetherPortalEntity(EntityType<?> entityType, World world) {
      super(entityType, world);
   }

   @Override
   public void tick() {
      super.tick();
   }

   @Override
   protected boolean isPortalIntactOnThisSide() {
      return this.blockPortalShape
            .area
            .stream()
            .allMatch(blockPos -> this.getEntityWorld().getBlockState(blockPos).getBlock() == PortalPlaceholderBlock.instance)
         && this.blockPortalShape.frameAreaWithoutCorner.stream().allMatch(blockPos -> O_O.isObsidian(this.getEntityWorld().getBlockState(blockPos)));
   }

   @Environment(EnvType.CLIENT)
   @Override
   protected void addSoundAndParticle() {
      if (IPGlobal.enableNetherPortalEffect) {
         Random random = this.getEntityWorld().getRandom();

         for (int i = 0; i < (int)Math.ceil(this.getWidth() * this.getHeight() / 20.0); i++) {
            if (random.nextInt(10) == 0) {
               double px = (random.nextDouble() * 2.0 - 1.0) * (this.getWidth() / 2.0);
               double py = (random.nextDouble() * 2.0 - 1.0) * (this.getHeight() / 2.0);
               Vec3d pos = this.getPointInPlane(px, py);
               double speedMultiplier = 20.0;
               double vx = speedMultiplier * (random.nextFloat() - 0.5) * 0.5;
               double vy = speedMultiplier * (random.nextFloat() - 0.5) * 0.5;
               double vz = speedMultiplier * (random.nextFloat() - 0.5) * 0.5;
               this.getEntityWorld().addParticleClient(ParticleTypes.field_11214, pos.x, pos.y, pos.z, vx, vy, vz);
            }
         }

         if (random.nextInt(800) == 0) {
            this.getEntityWorld()
               .playSoundClient(
                  this.getX(),
                  this.getY(),
                  this.getZ(),
                  SoundEvents.field_14802,
                  SoundCategory.field_15245,
                  0.5F,
                  random.nextFloat() * 0.4F + 0.8F,
                  false
               );
         }
      }
   }

   @Override
   public BreakablePortalEntity.OverlayInfo getActualOverlay() {
      if (IPGlobal.netherPortalOverlay) {
         switch (this.blockPortalShape.axis) {
            case field_11048:
               return overlay_x;
            case field_11052:
               return this.getNormal().y > 0.0 ? overlay_y_up : overlay_y_down;
            case field_11051:
               return overlay_z;
         }
      }

      return super.getActualOverlay();
   }
}
