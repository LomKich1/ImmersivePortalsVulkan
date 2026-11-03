package qouteall.imm_ptl.core.portal.nether_portal;

import com.mojang.logging.LogUtils;
import java.util.Arrays;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.WorldAccess;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.McHelper;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.my_util.IntBox;

public class NetherPortalMatcher {
   private static final Logger LOGGER = LogUtils.getLogger();

   private static boolean isAir(WorldAccess world, BlockPos pos) {
      return world.isAir(pos);
   }

   static IntBox findVerticalPortalPlacement(BlockPos areaSize, WorldAccess world, BlockPos searchingCenter) {
      int maxY = McHelper.getMaxContentYExclusive(world);
      int minY = McHelper.getMinY(world);
      IntBox airCube = getAirCubeOnGround(areaSize, new BlockPos(6, 0, 6), world, searchingCenter, 8, true, 64, maxY);
      if (airCube == null) {
         airCube = getAirCubeOnGround(areaSize, new BlockPos(2, 0, 2), world, searchingCenter, 10, true, 64, maxY);
      }

      if (airCube == null) {
         airCube = getAirCubeOnGround(areaSize, new BlockPos(2, 0, 2), world, searchingCenter, 10, false, minY, maxY);
      }

      if (airCube == null) {
         LOGGER.info("Cannot Find Portal Placement on Solid Surface");
         return null;
      } else if (world.getBlockState(airCube.l.down()).isSolid()) {
         LOGGER.info("Generated Portal On Ground");
         return pushDownBox(world, airCube.getSubBoxInCenter(areaSize));
      } else {
         LOGGER.info("Generated Portal On Non Solid Surface");
         return levitateBox(world, airCube.getSubBoxInCenter(areaSize), 40);
      }
   }

   private static IntBox expandFromBottomCenter(IntBox box, BlockPos spacing) {
      BlockPos boxSize = box.getSize();
      return box.getAdjusted(-spacing.getX() / 2, 0, -spacing.getZ() / 2, spacing.getX() / 2, spacing.getY(), spacing.getZ() / 2);
   }

   private static IntBox getAirCubeOnGround(
      BlockPos areaSize,
      BlockPos ambientSpaceReserved,
      WorldAccess world,
      BlockPos searchingCenter,
      int findingRadius,
      boolean solidGround,
      int startY,
      int endY
   ) {
      Predicate<BlockPos> isAirOnGroundPredicate = blockPos -> solidGround ? isAirOnSolidGround(world, blockPos) : isAirOnGround(world, blockPos);
      return BlockTraverse.searchColumned(searchingCenter.getX(), searchingCenter.getZ(), findingRadius, startY, endY, mutable -> {
         if (isAirOnGroundPredicate.test(mutable)) {
            IntBox box = IntBox.fromBasePointAndSize(mutable, areaSize);
            IntBox expanded = expandFromBottomCenter(box, ambientSpaceReserved);
            if (isAirCubeMediumPlace(world, expanded)) {
               if (!solidGround) {
                  return box;
               }

               if (BlockTraverse.boxAllMatch(box.getSurfaceLayer(Direction.DOWN), isAirOnGroundPredicate) && isAirOnGroundPredicate.test(expanded.l)) {
                  return box;
               }
            }
         }

         return null;
      });
   }

   static IntBox findHorizontalPortalPlacement(BlockPos areaSize, WorldAccess world, BlockPos searchingCenter) {
      IntBox result = findHorizontalPortalPlacementWithVerticalSpaceReserved(areaSize, world, searchingCenter, 30, 12);
      if (result == null) {
         result = findHorizontalPortalPlacementWithVerticalSpaceReserved(areaSize, world, searchingCenter, 10, 12);
      }

      if (result == null) {
         result = findHorizontalPortalPlacementWithVerticalSpaceReserved(areaSize, world, searchingCenter, 1, 12);
      }

      return result;
   }

   private static IntBox findHorizontalPortalPlacementWithVerticalSpaceReserved(
      BlockPos areaSize, WorldAccess world, BlockPos searchingCenter, int verticalSpaceReserve, int findingRadius
   ) {
      BlockPos growVertically = new BlockPos(areaSize.getX(), verticalSpaceReserve, areaSize.getZ());
      IntBox foundCubeArea = findCubeAirAreaAtAnywhere(growVertically, world, searchingCenter, findingRadius);
      return foundCubeArea == null ? null : foundCubeArea.getSubBoxInCenter(areaSize);
   }

   public static boolean isSolidGroundBlock(BlockState blockState) {
      return blockState.isSolid();
   }

   public static boolean isGroundBlock(BlockState blockState) {
      return !blockState.isAir();
   }

   private static boolean isAirOnSolidGround(WorldAccess world, BlockPos blockPos) {
      return world.isAir(blockPos) && isSolidGroundBlock(world.getBlockState(blockPos.add(0, -1, 0)));
   }

   private static boolean isAirOnGround(WorldAccess world, BlockPos blockPos) {
      return world.isAir(blockPos) && isGroundBlock(world.getBlockState(blockPos.add(0, -1, 0)));
   }

   public static IntBox findCubeAirAreaAtAnywhere(BlockPos areaSize, WorldAccess world, BlockPos searchingCenter, int findingRadius) {
      return BlockTraverse.searchColumned(
         searchingCenter.getX() - areaSize.getX() / 2,
         searchingCenter.getZ() - areaSize.getZ() / 2,
         findingRadius,
         1 + McHelper.getMinY(world),
         McHelper.getMaxYExclusive(world) - 1,
         mutable -> {
            IntBox box = IntBox.fromBasePointAndSize(mutable, areaSize);
            return isAirCubeMediumPlace(world, box) ? box : null;
         }
      );
   }

   public static boolean isAirCubeMediumPlace(WorldAccess world, IntBox box) {
      if (box.h.getY() + 1 >= McHelper.getMaxContentYExclusive(world)) {
         return false;
      } else {
         return box.l.getY() - 1 <= McHelper.getMinY(world) ? false : isAllAir(world, box);
      }
   }

   public static boolean isAllAir(WorldAccess world, IntBox box) {
      boolean roughTest = Arrays.stream(box.getEightVertices()).allMatch(blockPos -> isAir(world, blockPos));
      return !roughTest ? false : box.stream().allMatch(blockPos -> isAir(world, blockPos));
   }

   public static IntBox levitateBox(WorldAccess world, IntBox airCube, int maxOffset) {
      Integer maxUpShift = Helper.getLastSatisfying(
         IntStream.range(1, maxOffset * 3 / 2).boxed(), upShift -> isAirCubeMediumPlace(world, airCube.getMoved(new Vec3i(0, upShift, 0)))
      );
      if (maxUpShift == null) {
         maxUpShift = 0;
      }

      return airCube.getMoved(new Vec3i(0, maxUpShift * 2 / 3, 0));
   }

   public static IntBox pushDownBox(WorldAccess world, IntBox airCube) {
      Integer downShift = Helper.getLastSatisfying(IntStream.range(0, 40).boxed(), i -> isAirCubeMediumPlace(world, airCube.getMoved(new Vec3i(0, -i, 0))));
      if (downShift == null) {
         downShift = 0;
      }

      return airCube.getMoved(new Vec3i(0, -downShift, 0));
   }
}
