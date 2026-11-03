package qouteall.imm_ptl.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.storage.NbtReadView;
import net.minecraft.storage.NbtWriteView;
import net.minecraft.command.permission.LeveledPermissionPredicate;
import net.minecraft.entity.Entity;
import net.minecraft.util.Util;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.World;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.command.CommandManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.util.Identifier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.world.ChunkHolder;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.resource.Resource;
import net.minecraft.util.math.MathHelper;
import net.minecraft.entity.SpawnReason;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.text.MutableText;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.entity.SectionedEntityCache;
import net.minecraft.util.TypeFilter;
import net.minecraft.world.entity.EntityLookup;
import net.minecraft.util.ErrorReporter;
import net.minecraft.text.ClickEvent.class_10608;
import net.minecraft.world.level.storage.LevelStorage.class_5143;
import net.minecraft.server.world.ServerChunkLoadingManager.class_3208;
import net.minecraft.server.MinecraftServer;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.compat.GravityChangerInterface;
import qouteall.imm_ptl.core.ducks.IEChunkMap;
import qouteall.imm_ptl.core.ducks.IEEntity;
import qouteall.imm_ptl.core.ducks.IEEntityTrackingSection;
import qouteall.imm_ptl.core.ducks.IESectionedEntityCache;
import qouteall.imm_ptl.core.ducks.IEWorld;
import qouteall.imm_ptl.core.mc_utils.MyNbtTextFormatter;
import qouteall.imm_ptl.core.mc_utils.ServerTaskList;
import qouteall.imm_ptl.core.miscellaneous.IPVanillaCopy;
import qouteall.imm_ptl.core.mixin.common.mc_util.IELevelEntityGetterAdapter;
import qouteall.imm_ptl.core.platform_specific.O_O;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.MiscHelper;
import qouteall.q_misc_util.my_util.DQuaternion;
import qouteall.q_misc_util.my_util.IntBox;

public class McHelper {
   private static final Logger LOGGER = LogUtils.getLogger();
   public static final McHelper.Placeholder placeholder = new McHelper.Placeholder();

   public static Identifier newResourceLocation(String a, String b) {
      return Identifier.of(a, b);
   }

   public static Identifier newResourceLocation(String a) {
      return Identifier.of(a);
   }

   @Deprecated
   public static IEChunkMap getIEChunkMap(RegistryKey<World> dimension) {
      return (IEChunkMap)getServerWorld(dimension).method_14178().chunkLoadingManager;
   }

   @Deprecated
   public static List<ServerPlayerEntity> getRawPlayerList() {
      return MiscHelper.getServer().getPlayerManager().getPlayerList();
   }

   public static Vec3d lastTickPosOf(Entity entity) {
      return new Vec3d(entity.lastX, entity.lastY, entity.lastZ);
   }

   @Deprecated
   public static ServerWorld getOverWorldOnServer() {
      return MiscHelper.getServer().getWorld(World.OVERWORLD);
   }

   public static void serverLog(ServerPlayerEntity player, String text) {
      Helper.log(text);
      player.sendMessage(Text.literal(text), false);
   }

   public static long getServerGameTime() {
      return getOverWorldOnServer().getTime();
   }

   public static <T> void performMultiThreadedFindingTaskOnServer(
      MinecraftServer server, Stream<T> stream, Predicate<T> predicate, IntPredicate taskWatcher, Consumer<T> onFound, Runnable onNotFound, Runnable finalizer
   ) {
      int[] progress = new int[1];
      Helper.SimpleBox<Boolean> isAborted = new Helper.SimpleBox<>(false);
      Helper.SimpleBox<Runnable> finishBehavior = new Helper.SimpleBox<>(() -> Helper.err("Error Occured"));
      CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
         try {
            T result = stream.peek(obj -> progress[0]++).filter(predicate).findFirst().orElse(null);
            if (result != null) {
               finishBehavior.obj = () -> onFound.accept(result);
            } else {
               finishBehavior.obj = onNotFound;
            }
         } catch (Throwable var7x) {
            var7x.printStackTrace();
            finishBehavior.obj = () -> var7x.printStackTrace();
         }
      }, Util.getMainWorkerExecutor());
      ServerTaskList.of(server).addTask(() -> {
         if (future.isDone()) {
            if (!isAborted.obj) {
               finishBehavior.obj.run();
               finalizer.run();
            } else {
               Helper.log("Future done but the task is aborted");
            }

            return true;
         } else if (future.isCancelled()) {
            Helper.err("The future is cancelled");
            finalizer.run();
            return true;
         } else if (future.isCompletedExceptionally()) {
            Helper.err("The future is completed exceptionally");
            finalizer.run();
            return true;
         } else {
            boolean shouldContinue = taskWatcher.test(progress[0]);
            if (!shouldContinue) {
               isAborted.obj = true;
               future.cancel(true);
               finalizer.run();
               return true;
            } else {
               return false;
            }
         }
      });
   }

   public static <ENTITY extends Entity> List<ENTITY> getEntitiesNearby(World world, Vec3d center, Class<ENTITY> entityClass, double range) {
      return findEntitiesRough(entityClass, world, center, (int)(range / 16.0 + 1.0), e -> true);
   }

   public static <ENTITY extends Entity> List<ENTITY> getEntitiesNearby(Entity center, Class<ENTITY> entityClass, double range) {
      return getEntitiesNearby(center.getEntityWorld(), center.getEntityPos(), entityClass, range);
   }

   public static int getLoadDistanceOnServer(MinecraftServer server) {
      return server.getPlayerManager().getViewDistance();
   }

   @IPVanillaCopy
   public static int getPlayerLoadDistance(ServerPlayerEntity player) {
      assert player.getServerWorld().getServer() != null;

      int loadDistanceOnServer = getLoadDistanceOnServer(player.getServerWorld().getServer());
      return MathHelper.clamp(player.getViewDistance(), 2, loadDistanceOnServer);
   }

   public static void setPosAndLastTickPos(Entity entity, Vec3d pos, Vec3d lastTickPos) {
      entity.setPos(pos.x, pos.y, pos.z);
      entity.lastRenderX = lastTickPos.x;
      entity.lastRenderY = lastTickPos.y;
      entity.lastRenderZ = lastTickPos.z;
      entity.lastX = lastTickPos.x;
      entity.lastY = lastTickPos.y;
      entity.lastZ = lastTickPos.z;
   }

   public static void setPosAndLastTickPosWithoutTriggeringCallback(Entity entity, Vec3d pos, Vec3d lastTickPos) {
      ((IEEntity)entity).ip_setPositionWithoutTriggeringCallback(pos);
      entity.lastRenderX = lastTickPos.x;
      entity.lastRenderY = lastTickPos.y;
      entity.lastRenderZ = lastTickPos.z;
      entity.lastX = lastTickPos.x;
      entity.lastY = lastTickPos.y;
      entity.lastZ = lastTickPos.z;
   }

   public static Vec3d getEyePos(Entity entity) {
      Vec3d eyeOffset = GravityChangerInterface.invoker.getEyeOffset(entity);
      return entity.getEntityPos().add(eyeOffset);
   }

   public static Vec3d getLastTickEyePos(Entity entity) {
      Vec3d eyeOffset = GravityChangerInterface.invoker.getEyeOffset(entity);
      return lastTickPosOf(entity).add(eyeOffset);
   }

   public static void setEyePos(Entity entity, Vec3d eyePos, Vec3d lastTickEyePos) {
      Vec3d eyeOffset = GravityChangerInterface.invoker.getEyeOffset(entity);
      setPosAndLastTickPos(entity, eyePos.subtract(eyeOffset), lastTickEyePos.subtract(eyeOffset));
   }

   public static Vec3d getVehicleOffsetFromPassenger(Entity vehicle, Entity passenger) {
      return passenger.getVehicleAttachmentPos(vehicle);
   }

   public static void adjustVehicle(Entity entity) {
      Entity vehicle = entity.getVehicle();
      if (vehicle != null) {
         Vec3d vehicleOffset = getVehicleOffsetFromPassenger(vehicle, entity);
         Vec3d currVelocity = vehicle.getVelocity();
         Vec3d newVehiclePos = entity.getEntityPos().add(vehicleOffset);
         Vec3d newVehicleLastTickPos = lastTickPosOf(entity).add(vehicleOffset);
         vehicle.setPosition(newVehiclePos.getX(), newVehiclePos.getY(), newVehiclePos.getZ());
         vehicle.updateTrackedPositionAndAngles(newVehiclePos, vehicle.getYaw(), vehicle.getPitch());
         setPosAndLastTickPos(vehicle, newVehiclePos, newVehicleLastTickPos);
         vehicle.setVelocity(currVelocity);
      }
   }

   public static WorldChunk getServerChunkIfPresent(RegistryKey<World> dimension, int x, int z) {
      ChunkHolder chunkHolder_ = getIEChunkMap(dimension).ip_getChunkHolder(ChunkPos.toLong(x, z));
      return chunkHolder_ == null ? null : chunkHolder_.getWorldChunk();
   }

   public static WorldChunk getServerChunkIfPresent(ServerWorld world, int x, int z) {
      ChunkHolder chunkHolder_ = ((IEChunkMap)world.method_14178().chunkLoadingManager).ip_getChunkHolder(ChunkPos.toLong(x, z));
      return chunkHolder_ == null ? null : chunkHolder_.getWorldChunk();
   }

   @Deprecated
   public static <ENTITY extends Entity> Stream<ENTITY> getServerEntitiesNearbyWithoutLoadingChunk(
      World world, Vec3d center, Class<ENTITY> entityClass, double range
   ) {
      return findEntitiesRough(entityClass, world, center, (int)(range / 16.0), e -> true).stream();
   }

   public static void updateBoundingBox(Entity player) {
      player.setPosition(player.getX(), player.getY(), player.getZ());
   }

   public static void updatePosition(Entity entity, Vec3d pos) {
      entity.setPosition(pos.x, pos.y, pos.z);
   }

   public static <T extends Entity> List<T> getEntitiesRegardingLargeEntities(
      World world, Box box, double maxEntitySizeHalf, Class<T> entityClass, Predicate<T> predicate
   ) {
      return findEntitiesByBox(entityClass, world, box, maxEntitySizeHalf, predicate);
   }

   public static Portal copyEntity(Portal portal) {
      Portal newPortal = (Portal)portal.getType().create(portal.getEntityWorld(), SpawnReason.field_52444);
      Validate.notNull(newPortal);
      loadEntityFromNbt(newPortal, saveEntityWithoutIdToNbt(portal));
      return newPortal;
   }

   public static NbtCompound saveEntityWithoutIdToNbt(Entity entity) {
      NbtWriteView output = NbtWriteView.create(ErrorReporter.EMPTY, entity.getRegistryManager());
      entity.writeData(output);
      return output.getNbt();
   }

   public static void loadEntityFromNbt(Entity entity, NbtCompound tag) {
      entity.readData(NbtReadView.create(ErrorReporter.EMPTY, entity.getRegistryManager(), tag));
   }

   public static boolean getDoesRegionFileExist(RegistryKey<World> toDimension, BlockPos toPos) {
      ChunkPos chunkPos = new ChunkPos(toPos);
      class_5143 storageSource = MiscHelper.getServer().session;
      Path regionFilePath = storageSource.getWorldDirectory(toDimension)
         .resolve("region")
         .resolve("r." + chunkPos.getRegionX() + "." + chunkPos.getRegionZ() + ".mca");
      return regionFilePath.toFile().exists();
   }

   public static MutableText getLinkText(String link) {
      return Text.literal(link).styled(style -> style.withClickEvent(new class_10608(URI.create(link))).withUnderline(true));
   }

   public static void validateOnServerThread() {
      Validate.isTrue(Thread.currentThread() == MiscHelper.getServer().getThread(), "must be on server thread", new Object[0]);
   }

   public static void invokeCommandAs(Entity commandSender, List<String> commandList) {
      ServerCommandSource commandSource = (commandSender instanceof ServerPlayerEntity serverPlayer
            ? serverPlayer.getCommandSource()
            : commandSender.getCommandSource((ServerWorld)commandSender.getEntityWorld()))
         .withPermissions(LeveledPermissionPredicate.GAMEMASTERS)
         .withSilent();
      MinecraftServer server = commandSender.getEntityWorld().getServer();

      assert server != null;

      CommandManager commandManager = server.getCommandManager();

      for (String command : commandList) {
         commandManager.parseAndExecute(commandSource, command);
      }
   }

   public static void resendSpawnPacketToTrackers(Entity entity) {
      getIEChunkMap(entity.getEntityWorld().getRegistryKey()).ip_resendSpawnPacketToTrackers(entity);
   }

   public static void sendToTrackers(Entity entity, Packet<? super ClientPlayPacketListener> packet) {
      class_3208 entityTracker = (class_3208)getIEChunkMap(entity.getEntityWorld().getRegistryKey()).ip_getEntityTrackerMap().get(entity.getId());
      if (entityTracker != null) {
         entityTracker.sendToSelfAndListeners(packet);
      }
   }

   @Nullable
   public static Box getWallBox(World world, IntBox glassArea) {
      Stream<BlockPos> blockPosStream = glassArea.stream();
      return getWallBox(world, blockPosStream);
   }

   @Nullable
   public static Box getWallBox(World world, Stream<BlockPos> blockPosStream) {
      return blockPosStream.<Box>map(blockPos -> {
         VoxelShape collisionShape = world.getBlockState(blockPos).getCollisionShape(world, blockPos);
         return collisionShape.isEmpty() ? null : collisionShape.getBoundingBox().offset(Vec3d.of(blockPos));
      }).filter(b -> b != null).reduce(Box::union).orElse(null);
   }

   public static boolean isServerChunkFullyLoaded(ServerWorld world, ChunkPos chunkPos) {
      WorldChunk chunk = getServerChunkIfPresent(world.getRegistryKey(), chunkPos.x, chunkPos.z);
      return chunk == null ? false : world.isChunkLoaded(chunkPos.toLong());
   }

   public static McHelper.ChunkAccessor getChunkAccessor(World world) {
      return world.isClient() ? world::method_8497 : (x, z) -> getServerChunkIfPresent((ServerWorld)world, x, z);
   }

   public static <T extends Entity> List<T> findEntities(
      Class<T> entityClass,
      EntityLookup<Entity> entityLookup,
      int chunkXStart,
      int chunkXEnd,
      int chunkYStart,
      int chunkYEnd,
      int chunkZStart,
      int chunkZEnd,
      Predicate<T> predicate
   ) {
      ArrayList<T> result = new ArrayList<>();
      foreachEntities(entityClass, entityLookup, chunkXStart, chunkXEnd, chunkYStart, chunkYEnd, chunkZStart, chunkZEnd, entity -> {
         if (predicate.test(entity)) {
            result.add(entity);
         }
      });
      return result;
   }

   @Nullable
   public static <T extends Entity, R> R traverseEntities(
      Class<T> entityClass,
      EntityLookup<Entity> entityLookup,
      int chunkXStart,
      int chunkXEnd,
      int chunkYStart,
      int chunkYEnd,
      int chunkZStart,
      int chunkZEnd,
      Function<T, R> function
   ) {
      Validate.isTrue(chunkXEnd >= chunkXStart);
      Validate.isTrue(chunkYEnd >= chunkYStart);
      Validate.isTrue(chunkZEnd >= chunkZStart);
      Validate.isTrue(chunkXEnd - chunkXStart < 1000, "range too big", new Object[0]);
      Validate.isTrue(chunkZEnd - chunkZStart < 1000, "range too big", new Object[0]);
      TypeFilter<Entity, T> typeFilter = TypeFilter.instanceOf(entityClass);
      SectionedEntityCache<Entity> cache = (SectionedEntityCache<Entity>)((IELevelEntityGetterAdapter)entityLookup).getCache();
      return ((IESectionedEntityCache)cache)
         .ip_traverseSectionInBox(
            chunkXStart,
            chunkXEnd,
            chunkYStart,
            chunkYEnd,
            chunkZStart,
            chunkZEnd,
            entityTrackingSection -> ((IEEntityTrackingSection)entityTrackingSection).ip_traverse((TypeFilter<T, T>)typeFilter, function)
         );
   }

   public static <E extends Entity, R> R traverseEntitiesByBox(
      Class<E> entityClass, World world, Box box, double maxEntityRadius, Function<E, R> function
   ) {
      return traverseEntitiesByApproximateRegion(
         entityClass, world, box, maxEntityRadius, entity -> entity.getBoundingBox().intersects(box) ? function.apply(entity) : null
      );
   }

   public static <T extends Entity> void foreachEntities(
      Class<T> entityClass,
      EntityLookup<Entity> entityLookup,
      int chunkXStart,
      int chunkXEnd,
      int chunkYStart,
      int chunkYEnd,
      int chunkZStart,
      int chunkZEnd,
      Consumer<T> consumer
   ) {
      traverseEntities(entityClass, entityLookup, chunkXStart, chunkXEnd, chunkYStart, chunkYEnd, chunkZStart, chunkZEnd, e -> {
         consumer.accept((T)e);
         return null;
      });
   }

   public static <T extends Entity> List<T> findEntitiesRough(
      Class<T> entityClass, World world, Vec3d center, int radiusChunks, Predicate<T> predicate
   ) {
      if (radiusChunks <= 0) {
         radiusChunks = 1;
      }

      if (radiusChunks > 32) {
         radiusChunks = 32;
      }

      ChunkSectionPos sectionPos = ChunkSectionPos.from(center);
      return findEntities(
         entityClass,
         ((IEWorld)world).portal_getEntityLookup(),
         sectionPos.getSectionX() - radiusChunks,
         sectionPos.getSectionX() + radiusChunks,
         sectionPos.getSectionY() - radiusChunks,
         sectionPos.getSectionY() + radiusChunks,
         sectionPos.getSectionZ() - radiusChunks,
         sectionPos.getSectionZ() + radiusChunks,
         predicate
      );
   }

   public static <T extends Entity> List<T> findEntitiesByBox(
      Class<T> entityClass, World world, Box box, double maxEntityRadius, Predicate<T> predicate
   ) {
      ArrayList<T> result = new ArrayList<>();
      foreachEntitiesByBox(entityClass, world, box, maxEntityRadius, predicate, result::add);
      return result;
   }

   public static <T extends Entity> void foreachEntitiesByBox(
      Class<T> entityClass, World world, Box box, double maxEntityRadius, Predicate<T> predicate, Consumer<T> consumer
   ) {
      foreachEntitiesByBoxApproximateRegions(entityClass, world, box, maxEntityRadius, entity -> {
         if (entity.getBoundingBox().intersects(box) && predicate.test(entity)) {
            consumer.accept(entity);
         }
      });
   }

   public static <T extends Entity> void foreachEntitiesByBoxApproximateRegions(
      Class<T> entityClass, World world, Box box, double maxEntityRadius, Consumer<T> consumer
   ) {
      int xMin = (int)Math.floor(box.minX - maxEntityRadius);
      int yMin = (int)Math.floor(box.minY - maxEntityRadius);
      int zMin = (int)Math.floor(box.minZ - maxEntityRadius);
      int xMax = (int)Math.ceil(box.maxX + maxEntityRadius);
      int yMax = (int)Math.ceil(box.maxY + maxEntityRadius);
      int zMax = (int)Math.ceil(box.maxZ + maxEntityRadius);
      foreachEntities(entityClass, ((IEWorld)world).portal_getEntityLookup(), xMin >> 4, xMax >> 4, yMin >> 4, yMax >> 4, zMin >> 4, zMax >> 4, consumer);
   }

   public static <E extends Entity, R> R traverseEntitiesByApproximateRegion(
      Class<E> entityClass, World world, Box box, double maxEntityRadius, Function<E, R> function
   ) {
      int xMin = (int)Math.floor(box.minX - maxEntityRadius);
      int yMin = (int)Math.floor(box.minY - maxEntityRadius);
      int zMin = (int)Math.floor(box.minZ - maxEntityRadius);
      int xMax = (int)Math.ceil(box.maxX + maxEntityRadius);
      int yMax = (int)Math.ceil(box.maxY + maxEntityRadius);
      int zMax = (int)Math.ceil(box.maxZ + maxEntityRadius);
      return traverseEntities(
         entityClass, ((IEWorld)world).portal_getEntityLookup(), xMin >> 4, xMax >> 4, yMin >> 4, yMax >> 4, zMin >> 4, zMax >> 4, function
      );
   }

   public static <T extends Entity> void foreachEntitiesByPointAndRoughRadius(
      Class<T> entityClass, World world, Vec3d point, int roughRadius, Consumer<T> consumer
   ) {
      traverseEntitiesByPointAndRoughRadius(entityClass, world, point, roughRadius, entity -> {
         consumer.accept((T)entity);
         return null;
      });
   }

   public static <T extends Entity, R> void traverseEntitiesByPointAndRoughRadius(
      Class<T> entityClass, World world, Vec3d point, int roughRadius, Function<T, R> function
   ) {
      ChunkSectionPos sectionPos = ChunkSectionPos.from(BlockPos.ofFloored(point));
      int roughRadiusChunks = (int)Math.ceil(roughRadius / 16.0);
      if (roughRadiusChunks == 0) {
         roughRadiusChunks = 1;
      }

      traverseEntities(
         entityClass,
         ((IEWorld)world).portal_getEntityLookup(),
         sectionPos.getSectionX() - roughRadiusChunks,
         sectionPos.getSectionX() + roughRadiusChunks,
         sectionPos.getSectionY() - roughRadiusChunks,
         sectionPos.getSectionY() + roughRadiusChunks,
         sectionPos.getSectionZ() - roughRadiusChunks,
         sectionPos.getSectionZ() + roughRadiusChunks,
         function
      );
   }

   public static Identifier dimensionTypeId(RegistryKey<World> dimType) {
      return dimType.getValue();
   }

   public static <T> String serializeToJson(T object, Codec<T> codec) {
      DataResult<JsonElement> r = codec.encode(object, JsonOps.INSTANCE, new JsonObject());
      JsonElement result = (JsonElement)r.getOrThrow();
      return IPGlobal.gson.toJson(result);
   }

   public static <T, Serialized> T decodeFailHard(Codec<T> codec, DynamicOps<Serialized> ops, Serialized target) {
      return (T)((Pair)codec.decode(ops, target).getOrThrow(s -> {
         throw new McHelper.MyDecodeException("Cannot decode" + s + target);
      })).getFirst();
   }

   public static <Serialized> Serialized getElementFailHard(DynamicOps<Serialized> ops, Serialized target, String key) {
      return (Serialized)ops.get(target, key).getOrThrow(s -> {
         throw new McHelper.MyDecodeException("Cannot find" + key + s + target);
      });
   }

   public static <T, Serialized> void encode(Codec<T> codec, DynamicOps<Serialized> ops, Serialized target, T object) {
      codec.encode(object, ops, target);
   }

   public static <Serialized, T> T decodeElementFailHard(DynamicOps<Serialized> ops, Serialized input, Codec<T> codec, String key) {
      return decodeFailHard(codec, ops, getElementFailHard(ops, input, key));
   }

   public static void sendMessageToFirstLoggedPlayer(MinecraftServer server, Text text) {
      LOGGER.info("Message: {}", text.getContent());
      ServerTaskList.of(server).addTask(() -> {
         List<ServerPlayerEntity> playerList = server.getPlayerManager().getPlayerList();
         if (playerList.isEmpty()) {
            return false;
         } else {
            for (ServerPlayerEntity player : playerList) {
               player.sendMessage(text, false);
            }

            return true;
         }
      });
   }

   public static Iterable<Entity> getWorldEntityList(World world) {
      if (world.isClient()) {
         return CHelper.getWorldEntityList(world);
      } else {
         return world instanceof ServerWorld ? ((ServerWorld)world).iterateEntities() : (Iterable)Collections.<Entity>emptyList().iterator();
      }
   }

   public static void spawnServerEntity(Entity entity) {
      Validate.isTrue(!entity.getEntityWorld().isClient());
      boolean spawned = entity.getEntityWorld().spawnEntity(entity);
      if (!spawned) {
         LOGGER.error("Failed to spawn {} {}", entity, entity.getEntityWorld());
      }
   }

   @Deprecated
   public static ServerWorld getServerWorld(RegistryKey<World> dim) {
      return getServerWorld(MiscHelper.getServer(), dim);
   }

   @NotNull
   public static ServerWorld getServerWorld(MinecraftServer server, RegistryKey<World> dim) {
      return server.getWorld(dim);
   }

   public static Text compoundTagToTextSorted(NbtCompound tag, String indent, int depth) {
      return new MyNbtTextFormatter(" ", 0).apply(tag);
   }

   public static int getMinY(WorldAccess world) {
      return world.getBottomY();
   }

   public static int getMaxYExclusive(WorldAccess world) {
      return world.getTopYInclusive() + 1;
   }

   public static int getMaxContentYExclusive(WorldAccess world) {
      return world.getDimension().comp_653() + getMinY(world);
   }

   public static int getMinSectionY(WorldAccess world) {
      return world.getBottomSectionCoord();
   }

   public static int getMaxSectionYExclusive(WorldAccess world) {
      return world.getTopSectionCoord() + 1;
   }

   public static int getYSectionNumber(WorldAccess world) {
      return getMaxSectionYExclusive(world) - getMinSectionY(world);
   }

   public static Box getBoundingBoxWithMovedPosition(Entity entity, Vec3d newPos) {
      return entity.getBoundingBox().offset(newPos.subtract(entity.getEntityPos()));
   }

   public static String readTextResource(Identifier identifier) {
      String result = null;

      try {
         InputStream inputStream = ((Resource)MinecraftClient.getInstance().getResourceManager().getResource(identifier).get()).getInputStream();
         return IOUtils.toString(inputStream, Charset.defaultCharset());
      } catch (IOException var3) {
         throw new RuntimeException("Error loading " + identifier, var3);
      }
   }

   public static Vec3d getWorldVelocity(Entity entity) {
      return GravityChangerInterface.invoker.getWorldVelocity(entity);
   }

   public static void setWorldVelocity(Entity entity, Vec3d newVelocity) {
      GravityChangerInterface.invoker.setWorldVelocity(entity, newVelocity);
   }

   public static Vec3d getEyeOffset(Entity entity) {
      return GravityChangerInterface.invoker.getEyeOffset(entity);
   }

   public static Vec3d getAxisWFromOrientation(DQuaternion quaternion) {
      return quaternion.getAxisW();
   }

   public static Vec3d getAxisHFromOrientation(DQuaternion quaternion) {
      return quaternion.getAxisH();
   }

   public static Vec3d getNormalFromOrientation(DQuaternion quaternion) {
      return quaternion.getNormal();
   }

   @Nullable
   public static Entity getEntityByUUID(World world, UUID portalId) {
      return (Entity)((IEWorld)world).portal_getEntityLookup().get(portalId);
   }

   public static Text getDimensionName(RegistryKey<World> dimension) {
      String namespace = dimension.getValue().getNamespace();
      String path = dimension.getValue().getPath();
      String translationkey = "dimension." + namespace + "." + path;
      MutableText component = Text.translatable(translationkey);
      if (component.getString().equals(translationkey)) {
         String modName = O_O.getModName(namespace);
         return Text.translatable("imm_ptl.a_dimension_of", new Object[]{modName != null ? modName : namespace})
            .append(" (" + dimension.getValue() + ")");
      } else {
         return component;
      }
   }

   public interface ChunkAccessor {
      WorldChunk getChunk(int var1, int var2);
   }

   public static class MyDecodeException extends RuntimeException {
      public MyDecodeException(String message) {
         super(message);
      }
   }

   public static class Placeholder {
   }
}
