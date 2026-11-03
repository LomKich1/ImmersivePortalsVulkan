package qouteall.imm_ptl.peripheral.portal_generation;

import com.mojang.serialization.MapCodec;
import java.util.function.Predicate;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.block.AbstractBlock.class_4971;
import qouteall.imm_ptl.core.platform_specific.O_O;
import qouteall.imm_ptl.core.portal.custom_portal_gen.PortalGenInfo;
import qouteall.imm_ptl.core.portal.custom_portal_gen.form.AbstractDiligentForm;
import qouteall.imm_ptl.core.portal.custom_portal_gen.form.PortalGenForm;
import qouteall.imm_ptl.core.portal.nether_portal.BlockPortalShape;
import qouteall.imm_ptl.core.portal.nether_portal.BreakablePortalEntity;
import qouteall.imm_ptl.core.portal.nether_portal.NetherPortalEntity;

public class DiligentNetherPortalForm extends AbstractDiligentForm {
   public DiligentNetherPortalForm() {
      super(true);
   }

   @Override
   public void generateNewFrame(ServerWorld fromWorld, BlockPortalShape fromShape, ServerWorld toWorld, BlockPortalShape toShape) {
      for (BlockPos blockPos : toShape.frameAreaWithCorner) {
         toWorld.setBlockState(blockPos, Blocks.field_10540.getDefaultState());
      }
   }

   public BreakablePortalEntity[] generatePortalEntitiesAndPlaceholder(PortalGenInfo info) {
      info.generatePlaceholderBlocks();
      return info.generateBiWayBiFacedPortal(NetherPortalEntity.ENTITY_TYPE);
   }

   @Override
   public Predicate<BlockState> getOtherSideFramePredicate() {
      return O_O::isObsidian;
   }

   @Override
   public Predicate<BlockState> getThisSideFramePredicate() {
      return O_O::isObsidian;
   }

   @Override
   public Predicate<BlockState> getAreaPredicate() {
      return class_4971::isAir;
   }

   @Override
   public MapCodec<? extends PortalGenForm> getCodec() {
      throw new RuntimeException();
   }

   @Override
   public PortalGenForm getReverse() {
      return this;
   }
}
