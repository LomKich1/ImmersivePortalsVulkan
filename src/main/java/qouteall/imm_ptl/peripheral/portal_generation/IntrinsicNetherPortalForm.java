package qouteall.imm_ptl.peripheral.portal_generation;

import com.mojang.serialization.MapCodec;
import java.util.function.Predicate;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.AbstractBlock.AbstractBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.platform_specific.O_O;
import qouteall.imm_ptl.core.portal.custom_portal_gen.PortalGenInfo;
import qouteall.imm_ptl.core.portal.custom_portal_gen.form.NetherPortalLikeForm;
import qouteall.imm_ptl.core.portal.custom_portal_gen.form.PortalGenForm;
import qouteall.imm_ptl.core.portal.nether_portal.BlockPortalShape;
import qouteall.imm_ptl.core.portal.nether_portal.BreakablePortalEntity;
import qouteall.imm_ptl.core.portal.nether_portal.NetherPortalEntity;

public class IntrinsicNetherPortalForm extends NetherPortalLikeForm {
   private static volatile boolean encounteredVanillaPortalBlock = false;

   public IntrinsicNetherPortalForm() {
      super(true);
   }

   @Override
   public void generateNewFrame(ServerWorld fromWorld, BlockPortalShape fromShape, ServerWorld toWorld, BlockPortalShape toShape) {
      for (BlockPos blockPos : toShape.frameAreaWithCorner) {
         toWorld.setBlockState(blockPos, Blocks.OBSIDIAN.getDefaultState());
      }
   }

   @Override
   public PortalGenInfo getNewPortalPlacement(
      ServerWorld toWorld, BlockPos toPos, ServerWorld fromWorld, BlockPortalShape fromShape, @Nullable Entity triggeringEntity
   ) {
      if (encounteredVanillaPortalBlock) {
         encounteredVanillaPortalBlock = false;
         if (IPGlobal.enableWarning && triggeringEntity instanceof ServerPlayerEntity player) {
            player.sendMessage(Text.translatable("imm_ptl.cannot_connect_to_vanilla_portal"), false);
         }
      }

      return super.getNewPortalPlacement(toWorld, toPos, fromWorld, fromShape, triggeringEntity);
   }

   public BreakablePortalEntity[] generatePortalEntitiesAndPlaceholder(PortalGenInfo info) {
      info.generatePlaceholderBlocks();
      return info.generateBiWayBiFacedPortal(NetherPortalEntity.ENTITY_TYPE);
   }

   @Override
   public Predicate<BlockState> getOtherSideFramePredicate() {
      return blockState -> {
         if (O_O.isObsidian(blockState)) {
            return true;
         } else {
            Block block = blockState.getBlock();
            if (block == Blocks.NETHER_PORTAL) {
               encounteredVanillaPortalBlock = true;
            }

            return false;
         }
      };
   }

   @Override
   public Predicate<BlockState> getThisSideFramePredicate() {
      return O_O::isObsidian;
   }

   @Override
   public Predicate<BlockState> getAreaPredicate() {
      return AbstractBlockState::isAir;
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
