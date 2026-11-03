package qouteall.imm_ptl.core.portal.nether_portal;

import com.mojang.logging.LogUtils;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.text.Text;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.entity.SpawnReason;
import net.minecraft.registry.RegistryKey;
import net.minecraft.entity.Entity.class_5529;
import net.minecraft.util.math.Direction.class_2351;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.chunk_loading.ChunkLoader;
import qouteall.imm_ptl.core.chunk_loading.DimensionalChunkPos;
import qouteall.imm_ptl.core.chunk_loading.ImmPtlChunkTracking;
import qouteall.imm_ptl.core.mc_utils.ServerTaskList;
import qouteall.imm_ptl.core.platform_specific.O_O;
import qouteall.imm_ptl.core.portal.LoadingIndicatorEntity;
import qouteall.imm_ptl.core.portal.PortalPlaceholderBlock;
import qouteall.imm_ptl.core.portal.custom_portal_gen.PortalGenInfo;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.my_util.IntBox;
import qouteall.q_misc_util.my_util.LimitedLogger;

public class NetherPortalGeneration {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final LimitedLogger limitedLogger = new LimitedLogger(50);

   @Nullable
   public static IntBox findAirCubePlacement(
      ServerWorld toWorld, BlockPos mappedPosInOtherDimension, class_2351 axis, BlockPos neededAreaSize, boolean allowForcePlacement
   ) {
      BlockPos randomShift = new BlockPos(toWorld.getRandom().nextBoolean() ? 1 : -1, 0, toWorld.getRandom().nextBoolean() ? 1 : -1);
      IntBox foundAirCube = axis == class_2351.field_11052
         ? NetherPortalMatcher.findHorizontalPortalPlacement(neededAreaSize, toWorld, mappedPosInOtherDimension.method_10081(randomShift))
         : NetherPortalMatcher.findVerticalPortalPlacement(neededAreaSize, toWorld, mappedPosInOtherDimension.method_10081(randomShift));
      if (foundAirCube == null) {
         LOGGER.info("Cannot find normal portal placement");
         foundAirCube = NetherPortalMatcher.findCubeAirAreaAtAnywhere(neededAreaSize, toWorld, mappedPosInOtherDimension, 32);
         if (foundAirCube != null && isFloating(toWorld, foundAirCube)) {
            foundAirCube = NetherPortalMatcher.levitateBox(toWorld, foundAirCube, 50);
         }
      }

      if (foundAirCube == null) {
         if (allowForcePlacement) {
            Helper.err("Cannot find air cube within 32 blocks? Force placed portal. It will occupy normal blocks.");
            return IntBox.fromBasePointAndSize(mappedPosInOtherDimension, neededAreaSize);
         } else {
            return null;
         }
      } else {
         return foundAirCube;
      }
   }

   private static boolean isFloating(ServerWorld toWorld, IntBox foundAirCube) {
      return foundAirCube.getSurfaceLayer(Direction.field_11033).stream().noneMatch(blockPos -> toWorld.getBlockState(blockPos.method_10074()).isSolid());
   }

   public static void setPortalContentBlock(ServerWorld world, BlockPos pos, class_2351 normalAxis) {
      world.setBlockState(pos, (BlockState)PortalPlaceholderBlock.instance.getDefaultState().with(PortalPlaceholderBlock.AXIS, normalAxis));
   }

   public static void startGeneratingPortal(
      ServerWorld fromWorld,
      ServerWorld toWorld,
      BlockPortalShape fromShape,
      BlockPos toPos,
      int existingFrameSearchingRadius,
      Predicate<BlockState> otherSideFramePredicate,
      Consumer<BlockPortalShape> newFrameGenerateFunc,
      Consumer<PortalGenInfo> portalEntityGeneratingFunc,
      Supplier<PortalGenInfo> newFramePlacer,
      BooleanSupplier portalIntegrityChecker,
      FrameSearching.FrameSearchingFunc<PortalGenInfo> matchShapeByFramePos
   ) {
      RegistryKey<World> fromDimension = fromWorld.getRegistryKey();
      RegistryKey<World> toDimension = toWorld.getRegistryKey();
      MinecraftServer server = fromWorld.getServer();
      Vec3d indicatorPos = fromShape.innerAreaBox.getCenterVec();
      LoadingIndicatorEntity indicatorEntity = (LoadingIndicatorEntity)LoadingIndicatorEntity.entityType.create(fromWorld, SpawnReason.field_16461);
      indicatorEntity.isValid = true;
      indicatorEntity.setPosition(indicatorPos.x, indicatorPos.y, indicatorPos.z);
      indicatorEntity.setBox(fromShape.innerAreaBox);
      fromWorld.spawnEntity(indicatorEntity);
      Runnable onGenerateNewFrame = () -> {
         indicatorEntity.inform(Text.translatable("imm_ptl.generating_new_frame"));
         PortalGenInfo info = newFramePlacer.get();
         if (info != null) {
            newFrameGenerateFunc.accept(info.toShape);
            portalEntityGeneratingFunc.accept(info);
            O_O.postPortalSpawnEventForge(info);
         }
      };
      boolean otherSideChunkAlreadyGenerated = McHelper.getDoesRegionFileExist(toDimension, toPos);
      int frameSearchingRadius = Math.floorDiv(existingFrameSearchingRadius, 16) + 1;
      int loaderRadius = otherSideChunkAlreadyGenerated ? frameSearchingRadius : (fromShape.getShapeInnerLength() < 16 ? 1 : 2);
      ChunkLoader chunkLoader = new ChunkLoader(new DimensionalChunkPos(toDimension, new ChunkPos(toPos)), loaderRadius);
      ImmPtlChunkTracking.addGlobalAdditionalChunkLoader(server, chunkLoader);
      Runnable finalizer = () -> {
         indicatorEntity.remove(class_5529.KILLED);
         ImmPtlChunkTracking.removeGlobalAdditionalChunkLoader(server, chunkLoader);
      };
      ServerTaskList.of(server).addTask(() -> {
         boolean isPortalIntact = portalIntegrityChecker.getAsBoolean();
         if (!isPortalIntact) {
            finalizer.run();
            return true;
         } else {
            int loadedChunks = chunkLoader.getLoadedChunkNum(server);
            int allChunksNeedsLoading = chunkLoader.getChunkNum();
            if (loadedChunks < allChunksNeedsLoading) {
               indicatorEntity.inform(Text.translatable("imm_ptl.loading_chunks", new Object[]{loadedChunks, allChunksNeedsLoading}));
               return false;
            } else if (!otherSideChunkAlreadyGenerated) {
               onGenerateNewFrame.run();
               finalizer.run();
               return true;
            } else {
               ChunkLoader chunkLoader1 = new ChunkLoader(chunkLoader.getCenter(), frameSearchingRadius);
               ServerWorld world = McHelper.getServerWorld(server, chunkLoader1.dimension());
               FastBlockAccess chunkRegion = chunkLoader1.createFastBlockAccess(world);
               indicatorEntity.inform(Text.translatable("imm_ptl.searching_for_frame"));
               FrameSearching.startSearchingPortalFrameAsync(chunkRegion, frameSearchingRadius, toPos, otherSideFramePredicate, matchShapeByFramePos, info -> {
                  portalEntityGeneratingFunc.accept(info);
                  finalizer.run();
                  O_O.postPortalSpawnEventForge(info);
               }, () -> {
                  onGenerateNewFrame.run();
                  finalizer.run();
               });
               return true;
            }
         }
      });
   }

   public static boolean isOtherGenerationRunning(ServerWorld fromWorld, Vec3d indicatorPos) {
      boolean isOtherGenerationRunning = McHelper.getEntitiesNearby(fromWorld, indicatorPos, LoadingIndicatorEntity.class, 1.0).stream().findAny().isPresent();
      if (isOtherGenerationRunning) {
         Helper.log("Aborted Portal Generation Because Another Generation is Running Nearby");
         return true;
      } else {
         return false;
      }
   }

   public static boolean checkPortalGeneration(ServerWorld fromWorld, BlockPos startingPos) {
      if (!fromWorld.isChunkLoaded(startingPos)) {
         Helper.log("Cancel Portal Generation Because Chunk Not Loaded");
         return false;
      } else {
         limitedLogger.log(
            String.format(
               "Portal Generation Attempted %s %s %s %s",
               fromWorld.getRegistryKey().getValue(),
               startingPos.getX(),
               startingPos.getY(),
               startingPos.getZ()
            )
         );
         return true;
      }
   }

   public static BlockPortalShape findFrameShape(
      ServerWorld fromWorld, BlockPos startingPos, Predicate<BlockState> thisSideAreaPredicate, Predicate<BlockState> thisSideFramePredicate
   ) {
      return Arrays.stream(class_2351.values())
         .map(
            axis -> BlockPortalShape.findShapeWithoutRegardingStartingPos(
               startingPos, axis, pos -> thisSideAreaPredicate.test(fromWorld.getBlockState(pos)), pos -> thisSideFramePredicate.test(fromWorld.getBlockState(pos))
            )
         )
         .filter(Objects::nonNull)
         .findFirst()
         .orElse(null);
   }

   public static void embodyNewFrame(ServerWorld toWorld, BlockPortalShape toShape, BlockState frameBlockState) {
      toShape.frameAreaWithCorner.forEach(blockPos -> toWorld.setBlockState(blockPos, frameBlockState));
   }

   public static void fillInPlaceHolderBlocks(ServerWorld world, BlockPortalShape blockPortalShape) {
      blockPortalShape.area.forEach(blockPos -> setPortalContentBlock(world, blockPos, blockPortalShape.axis));
   }
}
