package qouteall.imm_ptl.peripheral.portal_generation;

import com.mojang.serialization.MapCodec;
import java.util.function.Predicate;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.text.Text;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.PortalExtension;
import qouteall.imm_ptl.core.portal.PortalManipulation;
import qouteall.imm_ptl.core.portal.custom_portal_gen.PortalGenInfo;
import qouteall.imm_ptl.core.portal.custom_portal_gen.form.AbstractDiligentForm;
import qouteall.imm_ptl.core.portal.custom_portal_gen.form.PortalGenForm;
import qouteall.imm_ptl.core.portal.nether_portal.BlockPortalShape;
import qouteall.imm_ptl.peripheral.PeripheralModMain;

public class PortalHelperForm extends AbstractDiligentForm {
   public PortalHelperForm() {
      super(true);
   }

   @Override
   public void generateNewFrame(ServerWorld fromWorld, BlockPortalShape fromShape, ServerWorld toWorld, BlockPortalShape toShape) {
      for (BlockPos blockPos : toShape.frameAreaWithoutCorner) {
         toWorld.setBlockState(blockPos, PeripheralModMain.portalHelperBlock.getDefaultState());
      }

      McHelper.findEntitiesByBox(ServerPlayerEntity.class, fromWorld, new Box(fromShape.anchor).expand(10.0), 2.0, e -> true)
         .forEach(player -> player.sendMessage(Text.translatable("imm_ptl.portal_helper_not_linked"), false));
   }

   @Override
   public Portal[] generatePortalEntitiesAndPlaceholder(PortalGenInfo info) {
      ServerWorld world = McHelper.getServerWorld(info.from);

      for (BlockPos blockPos : info.fromShape.area) {
         world.setBlockState(blockPos, Blocks.field_10124.getDefaultState());
      }

      world.setBlockState(info.fromShape.firstFramePos, Blocks.field_10124.getDefaultState());
      world.setBlockState(info.toShape.firstFramePos, Blocks.field_10124.getDefaultState());
      Portal portal = info.createTemplatePortal(Portal.ENTITY_TYPE);
      PortalExtension.get(portal).bindCluster = true;
      Portal flipped = PortalManipulation.createFlippedPortal(portal, Portal.ENTITY_TYPE);
      Portal reverse = PortalManipulation.createReversePortal(portal, Portal.ENTITY_TYPE);
      Portal parallel = PortalManipulation.createReversePortal(flipped, Portal.ENTITY_TYPE);
      Portal[] portals = new Portal[]{portal, flipped, reverse, parallel};

      for (Portal p : portals) {
         McHelper.spawnServerEntity(p);
      }

      return portals;
   }

   @Override
   public Predicate<BlockState> getOtherSideFramePredicate() {
      return blockState -> blockState.getBlock() == PeripheralModMain.portalHelperBlock;
   }

   @Override
   public Predicate<BlockState> getThisSideFramePredicate() {
      return blockState -> blockState.getBlock() == PeripheralModMain.portalHelperBlock;
   }

   @Override
   public Predicate<BlockState> getAreaPredicate() {
      return blockState -> blockState.isAir();
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
