package qouteall.imm_ptl.core.portal.custom_portal_gen.form;

import java.util.function.Predicate;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.custom_portal_gen.CustomPortalGeneration;
import qouteall.imm_ptl.core.portal.custom_portal_gen.PortalGenInfo;
import qouteall.imm_ptl.core.portal.nether_portal.BlockPortalShape;
import qouteall.imm_ptl.core.portal.nether_portal.FastBlockPortalShape;
import qouteall.imm_ptl.core.portal.nether_portal.FrameSearching;
import qouteall.imm_ptl.core.portal.nether_portal.GeneralBreakablePortal;
import qouteall.imm_ptl.core.portal.nether_portal.NetherPortalGeneration;
import qouteall.q_misc_util.my_util.IntBox;

public abstract class NetherPortalLikeForm extends PortalGenForm {
   public final boolean generateFrameIfNotFound;

   public NetherPortalLikeForm(boolean generateFrameIfNotFound) {
      this.generateFrameIfNotFound = generateFrameIfNotFound;
   }

   @Override
   public boolean perform(CustomPortalGeneration cpg, ServerWorld fromWorld, BlockPos startingPos, ServerWorld toWorld, @Nullable Entity triggeringEntity) {
      if (!NetherPortalGeneration.checkPortalGeneration(fromWorld, startingPos)) {
         return false;
      } else {
         Predicate<BlockState> areaPredicate = this.getAreaPredicate();
         Predicate<BlockState> thisSideFramePredicate = this.getThisSideFramePredicate();
         Predicate<BlockState> otherSideFramePredicate = this.getOtherSideFramePredicate();
         BlockPortalShape fromShape = NetherPortalGeneration.findFrameShape(fromWorld, startingPos, areaPredicate, thisSideFramePredicate);
         if (fromShape == null) {
            return false;
         } else if (!this.testThisSideShape(fromWorld, fromShape)) {
            return false;
         } else if (NetherPortalGeneration.isOtherGenerationRunning(fromWorld, fromShape.innerAreaBox.getCenterVec())) {
            return false;
         } else {
            if (this.generateFrameIfNotFound) {
               for (BlockPos areaPos : fromShape.area) {
                  fromWorld.setBlockState(areaPos, Blocks.AIR.getDefaultState());
               }
            }

            BlockPos toPos = cpg.mapPosition(fromShape.innerAreaBox.getCenter(), fromWorld, toWorld);
            FrameSearching.FrameSearchingFunc<PortalGenInfo> frameMatchingFunc = this.getFrameMatchingFunc(fromWorld, toWorld, fromShape);
            NetherPortalGeneration.startGeneratingPortal(
               fromWorld,
               toWorld,
               fromShape,
               toPos,
               IPGlobal.netherPortalFindingRadius,
               otherSideFramePredicate,
               toShape -> this.generateNewFrame(fromWorld, fromShape, toWorld, toShape),
               info -> {
                  Portal[] result = this.generatePortalEntitiesAndPlaceholder(info);
                  cpg.onPortalsGenerated(result);
               },
               () -> !this.generateFrameIfNotFound ? null : this.getNewPortalPlacement(toWorld, toPos, fromWorld, fromShape, triggeringEntity),
               () -> fromShape.frameAreaWithoutCorner.stream().allMatch(bp -> !fromWorld.isAir(bp)),
               frameMatchingFunc
            );
            return true;
         }
      }
   }

   public FrameSearching.FrameSearchingFunc<PortalGenInfo> getFrameMatchingFunc(ServerWorld fromWorld, ServerWorld toWorld, BlockPortalShape fromShape) {
      Predicate<BlockState> areaPredicate = this.getAreaPredicate();
      Predicate<BlockState> otherSideFramePredicate = this.getOtherSideFramePredicate();
      FastBlockPortalShape fastFromShape = FastBlockPortalShape.fromBlockPortalShape(fromShape);
      return (blockAccess, x, y, z) -> {
         boolean matches = fastFromShape.matchShape(
            x,
            y,
            z,
            (px, py, pz) -> otherSideFramePredicate.test(blockAccess.getBlockState(px, py, pz)),
            (px, py, pz) -> areaPredicate.test(blockAccess.getBlockState(px, py, pz))
         );
         if (matches) {
            boolean matchToSelf = fromWorld == toWorld && x == fastFromShape.basePosX() && y == fastFromShape.basePosY() && z == fastFromShape.basePosZ();
            if (!matchToSelf) {
               FastBlockPortalShape moved = fastFromShape.withNewBase(x, y, z);
               return new PortalGenInfo(fromWorld.getRegistryKey(), toWorld.getRegistryKey(), fastFromShape.toBlockPortalShape(), moved.toBlockPortalShape());
            }
         }

         return null;
      };
   }

   @Nullable
   public PortalGenInfo getNewPortalPlacement(
      ServerWorld toWorld, BlockPos toPos, ServerWorld fromWorld, BlockPortalShape fromShape, @Nullable Entity triggeringEntity
   ) {
      boolean canForcePlace = false;
      if (triggeringEntity instanceof ServerPlayerEntity player && player.isCreative()) {
         canForcePlace = true;
      }

      IntBox airCubePlacement = NetherPortalGeneration.findAirCubePlacement(toWorld, toPos, fromShape.axis, fromShape.totalAreaBox.getSize(), canForcePlace);
      if (!canForcePlace && airCubePlacement == null && triggeringEntity instanceof ServerPlayerEntity player) {
         player.sendMessage(Text.translatable("imm_ptl.no_place_to_generate_portal"), false);
      }

      if (airCubePlacement == null) {
         return null;
      } else {
         BlockPortalShape placedShape = fromShape.getShapeWithMovedTotalAreaBox(airCubePlacement);
         return new PortalGenInfo(fromWorld.getRegistryKey(), toWorld.getRegistryKey(), fromShape, placedShape);
      }
   }

   public Portal[] generatePortalEntitiesAndPlaceholder(PortalGenInfo info) {
      info.generatePlaceholderBlocks();
      return info.generateBiWayBiFacedPortal(GeneralBreakablePortal.ENTITY_TYPE);
   }

   public abstract void generateNewFrame(ServerWorld var1, BlockPortalShape var2, ServerWorld var3, BlockPortalShape var4);

   public abstract Predicate<BlockState> getOtherSideFramePredicate();

   public abstract Predicate<BlockState> getThisSideFramePredicate();

   public abstract Predicate<BlockState> getAreaPredicate();

   public boolean testThisSideShape(ServerWorld fromWorld, BlockPortalShape fromShape) {
      return true;
   }
}
