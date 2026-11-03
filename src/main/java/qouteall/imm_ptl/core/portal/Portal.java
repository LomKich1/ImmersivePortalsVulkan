package qouteall.imm_ptl.core.portal;

import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.MovementType;
import net.minecraft.util.Util;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.EntityTrackerEntry;
import net.minecraft.util.math.MathHelper;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.entity.Entity.class_5529;
import net.minecraft.entity.EntityType.class_1300;
import net.minecraft.entity.EntityType.class_4049;
import net.minecraft.entity.data.DataTracker.class_9222;
import net.minecraft.server.MinecraftServer;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4d;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.api.ImmPtlEntityExtension;
import qouteall.imm_ptl.core.api.PortalAPI;
import qouteall.imm_ptl.core.compat.iris_compatibility.IrisInterface;
import qouteall.imm_ptl.core.mc_utils.IPEntityEventListenableEntity;
import qouteall.imm_ptl.core.mc_utils.ServerTaskList;
import qouteall.imm_ptl.core.network.ImmPtlNetworking;
import qouteall.imm_ptl.core.platform_specific.IPConfig;
import qouteall.imm_ptl.core.platform_specific.O_O;
import qouteall.imm_ptl.core.portal.animation.AnimationView;
import qouteall.imm_ptl.core.portal.animation.DefaultPortalAnimation;
import qouteall.imm_ptl.core.portal.animation.PortalAnimation;
import qouteall.imm_ptl.core.portal.animation.PortalAnimationDriver;
import qouteall.imm_ptl.core.portal.animation.UnilateralPortalState;
import qouteall.imm_ptl.core.portal.shape.PortalShape;
import qouteall.imm_ptl.core.portal.shape.PortalShapeSerialization;
import qouteall.imm_ptl.core.portal.shape.RectangularPortalShape;
import qouteall.imm_ptl.core.portal.shape.SpecialFlatPortalShape;
import qouteall.imm_ptl.core.render.renderer.PortalRenderer;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.my_util.DQuaternion;
import qouteall.q_misc_util.my_util.Mesh2D;
import qouteall.q_misc_util.my_util.MyTaskList;
import qouteall.q_misc_util.my_util.Plane;
import qouteall.q_misc_util.my_util.RayTraceResult;
import qouteall.q_misc_util.my_util.TriangleConsumer;
import qouteall.q_misc_util.platform.Platform;
import qouteall.q_misc_util.platform.PlatformNetworking;
import qouteall.q_misc_util.platform.event.Event;

public class Portal extends Entity implements PortalLike, IPEntityEventListenableEntity {
   private static final Logger LOGGER = LogUtils.getLogger();
   public static final EntityType<Portal> ENTITY_TYPE = createPortalEntityType(Portal::new, portalEntityTypeKey("portal"));
   public static final Event<Consumer<Portal>> CLIENT_PORTAL_ACCEPT_SYNC_EVENT = Helper.createConsumerEvent();
   public static final Event<Consumer<Portal>> CLIENT_PORTAL_SPAWN_EVENT = Helper.createConsumerEvent();
   private static final Box NULL_BOX = new Box(0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
   protected double width = 0.0;
   protected double height = 0.0;
   protected double thickness = 0.0;
   protected Vec3d axisW;
   protected Vec3d axisH;
   protected RegistryKey<World> dimensionTo;
   protected Vec3d destination;
   protected boolean teleportable = true;
   @Nullable
   protected PortalShape portalShape;
   @Nullable
   public UUID specificPlayerId;
   @Nullable
   protected DQuaternion rotation;
   protected double scaling = 1.0;
   protected boolean teleportChangesScale = true;
   protected boolean teleportChangesGravity = IPConfig.getConfig().portalsChangeGravityByDefault;
   protected boolean interactable = true;
   PortalExtension extension;
   @Nullable
   public String portalTag;
   public boolean isGlobalPortal = false;
   protected boolean fuseView = false;
   @Deprecated
   public boolean renderingMergable = false;
   protected boolean crossPortalCollisionEnabled = true;
   protected boolean doRenderPlayer = true;
   protected boolean visible = true;
   @Nullable
   protected List<String> commandsOnTeleported;
   @Environment(EnvType.CLIENT)
   PortalRenderInfo portalRenderInfo;
   public final PortalAnimation animation = new PortalAnimation();
   @Nullable
   protected PortalState lastTickPortalState;
   protected boolean reloadAndSyncNextTick = false;
   @Nullable
   private Box thinBoundingBoxCache;
   @Nullable
   private Box boundingBoxCache;
   @Nullable
   private Vec3d normalCache;
   @Nullable
   private Vec3d contentDirectionCache;
   @Nullable
   private PortalState portalStateCache;
   @Nullable
   private VoxelShape thisSideCollisionExclusion;
   @Nullable
   private UnilateralPortalState thisSideStateCache;
   @Nullable
   private UnilateralPortalState otherSideStateCache;
   public static final Event<Consumer<Portal>> CLIENT_PORTAL_TICK_SIGNAL = Helper.createConsumerEvent();
   public static final Event<Consumer<Portal>> SERVER_PORTAL_TICK_SIGNAL = Helper.createConsumerEvent();
   public static final Event<Consumer<Portal>> PORTAL_DISPOSE_SIGNAL = Helper.createConsumerEvent();
   public static final Event<BiConsumer<Portal, NbtCompound>> READ_PORTAL_DATA_SIGNAL = Helper.createBiConsumerEvent();
   public static final Event<BiConsumer<Portal, NbtCompound>> WRITE_PORTAL_DATA_SIGNAL = Helper.createBiConsumerEvent();

   public static RegistryKey<EntityType<?>> portalEntityTypeKey(String path) {
      return RegistryKey.of(RegistryKeys.field_41266, McHelper.newResourceLocation("immersive_portals", path));
   }

   public static <T extends Portal> EntityType<T> createPortalEntityType(class_4049<T> constructor, RegistryKey<EntityType<?>> entityTypeKey) {
      return Platform.get()
         .forceTrackedVelocityUpdates(
            class_1300.create(constructor, SpawnGroup.field_17715).dimensions(0.0F, 0.0F).makeFireImmune().maxTrackingRange(6).trackingTickInterval(20), true
         )
         .build(entityTypeKey);
   }

   public Portal(EntityType<?> entityType, World world) {
      super(entityType, world);
   }

   protected void initDataTracker(class_9222 builder) {
   }

   public boolean damage(ServerWorld level, DamageSource source, float amount) {
      return false;
   }

   protected void readCustomData(ReadView input) {
      this.readAdditionalSaveData(Helper.readWholeCompound(input));
   }

   protected void writeCustomData(WriteView output) {
      NbtCompound compoundTag = new NbtCompound();
      this.addAdditionalSaveData(compoundTag);
      Helper.writeWholeCompound(output, compoundTag);
   }

   protected void readAdditionalSaveData(NbtCompound compoundTag) {
      this.width = compoundTag.getDouble("width", 0.0);
      this.height = compoundTag.getDouble("height", 0.0);
      this.thickness = compoundTag.getDouble("thickness", 0.0);
      this.axisW = Helper.getVec3d(compoundTag, "axisW").normalize();
      this.axisH = Helper.getVec3d(compoundTag, "axisH").normalize();
      this.dimensionTo = Helper.getWorldId(compoundTag, "dimensionTo");
      this.destination = Helper.getVec3d(compoundTag, "destination");
      this.specificPlayerId = Helper.getUuid(compoundTag, "specificPlayer");
      if (compoundTag.contains("portalShape")) {
         NbtCompound portalShapeTag = compoundTag.getCompoundOrEmpty("portalShape");
         PortalShape portalShape = PortalShapeSerialization.deserialize(portalShapeTag);
         if (portalShape == null) {
            LOGGER.error("Cannot deserialize portal shape {}", portalShapeTag);
            this.portalShape = RectangularPortalShape.INSTANCE;
         } else {
            this.portalShape = portalShape;
         }
      } else {
         Mesh2D mesh2D;
         if (compoundTag.contains("specialShape")) {
            boolean shapeNormalized = compoundTag.getBoolean("shapeNormalized", false);
            if (shapeNormalized) {
               mesh2D = GeometryPortalShape.readOldMeshFromTag(compoundTag.getListOrEmpty("specialShape"));
            } else {
               mesh2D = GeometryPortalShape.readOldMeshFromTagNonNormalized(compoundTag.getListOrEmpty("specialShape"), this.width / 2.0, this.height / 2.0);
            }
         } else {
            mesh2D = null;
         }

         if (mesh2D == null) {
            this.portalShape = RectangularPortalShape.INSTANCE;
         } else {
            this.portalShape = new SpecialFlatPortalShape(mesh2D);
         }
      }

      if (compoundTag.contains("teleportable")) {
         this.teleportable = compoundTag.getBoolean("teleportable", false);
      }

      if (compoundTag.contains("rotationA")) {
         this.setRotationTransformationD(
            new DQuaternion(
               compoundTag.getFloat("rotationB", 0.0F),
               compoundTag.getFloat("rotationC", 0.0F),
               compoundTag.getFloat("rotationD", 0.0F),
               compoundTag.getFloat("rotationA", 0.0F)
            )
         );
      } else {
         this.rotation = null;
      }

      if (compoundTag.contains("interactable")) {
         this.interactable = compoundTag.getBoolean("interactable", false);
      }

      if (compoundTag.contains("scale")) {
         this.scaling = compoundTag.getDouble("scale", 0.0);
      }

      if (compoundTag.contains("teleportChangesScale")) {
         this.teleportChangesScale = compoundTag.getBoolean("teleportChangesScale", false);
      }

      if (compoundTag.contains("teleportChangesGravity")) {
         this.teleportChangesGravity = compoundTag.getBoolean("teleportChangesGravity", false);
      } else {
         this.teleportChangesGravity = IPConfig.getConfig().portalsChangeGravityByDefault;
      }

      if (compoundTag.contains("portalTag")) {
         this.portalTag = compoundTag.getString("portalTag", "");
      }

      if (compoundTag.contains("fuseView")) {
         this.fuseView = compoundTag.getBoolean("fuseView", false);
      }

      if (compoundTag.contains("renderingMergable")) {
         this.renderingMergable = compoundTag.getBoolean("renderingMergable", false);
      }

      if (compoundTag.contains("hasCrossPortalCollision")) {
         this.crossPortalCollisionEnabled = compoundTag.getBoolean("hasCrossPortalCollision", false);
      }

      if (compoundTag.contains("commandsOnTeleported")) {
         NbtList list = compoundTag.getListOrEmpty("commandsOnTeleported");
         this.commandsOnTeleported = list.stream().map(t -> ((NbtString)t).comp_3831()).collect(Collectors.toList());
      } else {
         this.commandsOnTeleported = null;
      }

      if (compoundTag.contains("doRenderPlayer")) {
         this.doRenderPlayer = compoundTag.getBoolean("doRenderPlayer", false);
      } else {
         this.doRenderPlayer = true;
      }

      if (compoundTag.contains("isVisible")) {
         this.visible = compoundTag.getBoolean("isVisible", false);
      } else {
         this.visible = true;
      }

      this.animation.readFromTag(compoundTag);
      READ_PORTAL_DATA_SIGNAL.invoker().accept(this, compoundTag);
      this.updateCache();
   }

   protected void addAdditionalSaveData(NbtCompound compoundTag) {
      compoundTag.putDouble("width", this.width);
      compoundTag.putDouble("height", this.height);
      compoundTag.putDouble("thickness", this.thickness);
      Helper.putVec3d(compoundTag, "axisW", this.axisW);
      Helper.putVec3d(compoundTag, "axisH", this.axisH);
      Helper.putWorldId(compoundTag, "dimensionTo", this.dimensionTo);
      Helper.putVec3d(compoundTag, "destination", this.getDestPos());
      if (this.specificPlayerId != null) {
         Helper.putUuid(compoundTag, "specificPlayer", this.specificPlayerId);
      }

      NbtCompound portalShapeTag = PortalShapeSerialization.serialize(this.getPortalShape());
      compoundTag.put("portalShape", portalShapeTag);
      compoundTag.putBoolean("teleportable", this.teleportable);
      if (this.rotation != null) {
         compoundTag.putDouble("rotationA", this.rotation.w);
         compoundTag.putDouble("rotationB", this.rotation.x);
         compoundTag.putDouble("rotationC", this.rotation.y);
         compoundTag.putDouble("rotationD", this.rotation.z);
      }

      compoundTag.putBoolean("interactable", this.interactable);
      compoundTag.putDouble("scale", this.scaling);
      compoundTag.putBoolean("teleportChangesScale", this.teleportChangesScale);
      compoundTag.putBoolean("teleportChangesGravity", this.teleportChangesGravity);
      if (this.portalTag != null) {
         compoundTag.putString("portalTag", this.portalTag);
      }

      compoundTag.putBoolean("fuseView", this.fuseView);
      compoundTag.putBoolean("renderingMergable", this.renderingMergable);
      compoundTag.putBoolean("hasCrossPortalCollision", this.crossPortalCollisionEnabled);
      compoundTag.putBoolean("doRenderPlayer", this.doRenderPlayer);
      compoundTag.putBoolean("isVisible", this.visible);
      if (this.commandsOnTeleported != null) {
         NbtList list = new NbtList();

         for (String command : this.commandsOnTeleported) {
            list.add(NbtString.of(command));
         }

         compoundTag.put("commandsOnTeleported", list);
      }

      this.animation.writeToTag(compoundTag);
      WRITE_PORTAL_DATA_SIGNAL.invoker().accept(this, compoundTag);
   }

   @NotNull
   public PortalShape getPortalShape() {
      if (this.portalShape == null) {
         this.portalShape = RectangularPortalShape.INSTANCE;
      }

      return this.portalShape;
   }

   public void setPortalShape(PortalShape portalShape) {
      this.portalShape = portalShape;
      if (portalShape.isPlanar()) {
         this.thickness = 0.0;
      }

      this.updateCache();
   }

   public void setPortalShapeToDefault() {
      this.setPortalShape(RectangularPortalShape.INSTANCE);
   }

   @Override
   public void ip_onEntityPositionUpdated() {
      this.updateCache();
   }

   @Override
   public void ip_onRemoved(class_5529 reason) {
      PORTAL_DISPOSE_SIGNAL.invoker().accept(this);
   }

   @Override
   public Vec3d transformPoint(Vec3d pos) {
      Vec3d localPos = pos.subtract(this.getOriginPos());
      return this.transformLocalVec(localPos).add(this.getDestPos());
   }

   @Override
   public Vec3d transformLocalVec(Vec3d localVec) {
      return this.transformLocalVecNonScale(localVec).multiply(this.scaling);
   }

   public Vec3d getNormal() {
      if (this.normalCache == null) {
         this.normalCache = this.axisW.crossProduct(this.axisH).normalize();
      }

      return this.normalCache;
   }

   public Vec3d getContentDirection() {
      if (this.contentDirectionCache == null) {
         this.contentDirectionCache = this.transformLocalVecNonScale(this.getNormal().multiply(-1.0));
      }

      return this.contentDirectionCache;
   }

   public void onEntityTeleportedOnServer(Entity entity) {
      if (this.commandsOnTeleported != null) {
         McHelper.invokeCommandAs(entity, this.commandsOnTeleported);
      }
   }

   public void reloadAndSyncToClient() {
      this.reloadAndSyncNextTick = false;
      Validate.isTrue(!this.isGlobalPortal, "global portal is not synced by this", new Object[0]);
      Validate.isTrue(!this.getEntityWorld().isClient(), "must be used on server side", new Object[0]);
      this.updateCache();
      Packet<ClientPlayPacketListener> packet = this.createSyncPacket();
      McHelper.sendToTrackers(this, packet);
   }

   public void reloadAndSyncToClientNextTick() {
      Validate.isTrue(!this.getEntityWorld().isClient(), "must be used on server side", new Object[0]);
      this.reloadAndSyncNextTick = true;
   }

   public void reloadAndSyncClusterToClientNextTick() {
      PortalExtension.forClusterPortals(this, Portal::reloadAndSyncToClientNextTick);
   }

   public void reloadAndSyncToClientWithTickDelay(int tickDelay) {
      Validate.isTrue(!this.getEntityWorld().isClient(), "must be used on server side", new Object[0]);
      ServerTaskList.of(this.getEntityWorld().getServer()).addTask(MyTaskList.withDelay(tickDelay, () -> {
         this.reloadAndSyncToClientNextTick();
         return true;
      }));
   }

   public void updateCache() {
      if (this.axisW != null && this.axisH != null) {
         this.portalStateCache = null;
         this.boundingBoxCache = null;
         this.thinBoundingBoxCache = null;
         this.normalCache = null;
         this.contentDirectionCache = null;
         this.thisSideCollisionExclusion = null;
         this.thisSideStateCache = null;
         this.otherSideStateCache = null;
      }
   }

   @NotNull
   public Box getBoundingBox() {
      if (this.boundingBoxCache == null) {
         this.boundingBoxCache = this.calculateBoundingBox();
      }

      return this.boundingBoxCache;
   }

   @Override
   public Vec3d getOriginPos() {
      return this.getEntityPos();
   }

   @Override
   public Vec3d getDestPos() {
      return this.destination;
   }

   public void setOriginPos(Vec3d pos) {
      this.setPosition(pos);
   }

   public void setDestinationDimension(RegistryKey<World> dim) {
      this.dimensionTo = dim;
   }

   public void setDestination(Vec3d destination) {
      this.destination = destination;
      this.updateCache();
   }

   @Override
   public boolean isFuseView() {
      return this.fuseView;
   }

   @Deprecated
   public boolean isRenderingMergable() {
      return this.renderingMergable;
   }

   public boolean isInteractable() {
      return this.interactable;
   }

   public void setInteractable(boolean interactable) {
      this.interactable = interactable;
   }

   @Override
   public World getOriginWorld() {
      return this.getEntityWorld();
   }

   @Override
   public World getDestWorld() {
      return this.getDestinationWorld();
   }

   @Override
   public RegistryKey<World> getDestDim() {
      return this.dimensionTo;
   }

   @Override
   public double getScale() {
      return this.scaling;
   }

   @Override
   public boolean getIsGlobal() {
      return this.isGlobalPortal;
   }

   @Override
   public boolean isVisible() {
      return this.visible;
   }

   public void setIsVisible(boolean visible) {
      this.visible = visible;
   }

   public boolean canTeleportEntity(Entity entity) {
      if (!this.teleportable) {
         return false;
      } else if (entity instanceof Portal) {
         return false;
      } else {
         if (entity instanceof PlayerEntity) {
            if (this.specificPlayerId != null && !entity.getUuid().equals(this.specificPlayerId)) {
               return false;
            }
         } else if (this.specificPlayerId != null && !this.specificPlayerId.equals(Util.NIL_UUID)) {
            return false;
         }

         return !O_O.allowTeleportingEntity(entity, this) ? false : ((ImmPtlEntityExtension)entity).imm_ptl_canTeleportThroughPortal(this);
      }
   }

   public boolean canCollideWithEntity(Entity entity) {
      return this.canTeleportEntity(entity);
   }

   public boolean isInteractableBy(PlayerEntity player) {
      if (!IPConfig.getConfig().enableCrossPortalInteraction) {
         return false;
      } else if (!this.isInteractable()) {
         return false;
      } else {
         return !this.isVisible() ? false : this.canTeleportEntity(player);
      }
   }

   @Nullable
   @Override
   public DQuaternion getRotation() {
      return this.rotation;
   }

   @NotNull
   public DQuaternion getRotationD() {
      return DQuaternion.fromNullable(this.getRotation());
   }

   @Override
   public boolean getDoRenderPlayer() {
      return this.doRenderPlayer;
   }

   public boolean getTeleportable() {
      return this.teleportable;
   }

   public void setTeleportable(boolean teleportable) {
      this.teleportable = teleportable;
   }

   public void setOrientationAndSize(Vec3d newAxisW, Vec3d newAxisH, double newWidth, double newHeight) {
      this.setOrientation(newAxisW, newAxisH);
      this.width = newWidth;
      this.height = newHeight;
      this.updateCache();
   }

   public void setOrientation(Vec3d newAxisW, Vec3d newAxisH) {
      this.axisW = newAxisW.normalize();
      this.axisH = newAxisH.normalize();
      this.updateCache();
   }

   public void setWidth(double newWidth) {
      this.width = newWidth;
      this.updateCache();
   }

   public void setHeight(double newHeight) {
      this.height = newHeight;
      this.updateCache();
   }

   public void setThickness(double newThickness) {
      this.thickness = newThickness;
      this.updateCache();
   }

   public void setPortalSize(double newWidth, double newHeight, double newThickness) {
      this.width = newWidth;
      this.height = newHeight;
      this.thickness = newThickness;
      this.updateCache();
   }

   public DQuaternion getOrientationRotation() {
      return PortalManipulation.getPortalOrientationQuaternion(this.axisW, this.axisH);
   }

   public void setOrientationRotation(DQuaternion quaternion) {
      DQuaternion fixed = this.getEntityWorld().isClient() ? quaternion : quaternion.fixFloatingPointErrorAccumulation();
      this.setOrientation(McHelper.getAxisWFromOrientation(fixed), McHelper.getAxisHFromOrientation(fixed));
   }

   public void setRotation(@Nullable DQuaternion quaternion) {
      this.setRotationTransformationD(quaternion);
   }

   public void setRotationTransformation(@Nullable DQuaternion quaternion) {
      this.setRotationTransformationD(quaternion);
   }

   public void setRotationTransformationD(@Nullable DQuaternion quaternion) {
      if (quaternion == null) {
         this.rotation = null;
      } else {
         this.rotation = quaternion.fixFloatingPointErrorAccumulation();
      }

      this.updateCache();
   }

   public void setOtherSideOrientation(DQuaternion otherSideOrientation) {
      this.setRotation(PortalManipulation.computeDeltaTransformation(this.getOrientationRotation(), otherSideOrientation));
   }

   public void setScaleTransformation(double newScale) {
      this.scaling = newScale;
   }

   @Environment(EnvType.CLIENT)
   public void renderViewAreaMesh(Vec3d portalPosRelativeToCamera, TriangleConsumer vertexOutput) {
      if (this instanceof Mirror) {
         boolean offsetFront = IrisInterface.invoker.isShaders() || IPGlobal.pureMirror;
         double mirrorOffset = offsetFront ? 0.01 : -0.01;
         portalPosRelativeToCamera = portalPosRelativeToCamera.add(((Mirror)this).getNormal().multiply(mirrorOffset));
      }

      this.getPortalShape().renderViewAreaMesh(portalPosRelativeToCamera, this.getThisSideState(), vertexOutput, this.getIsGlobal());
   }

   @NotNull
   public Packet<ClientPlayPacketListener> createSpawnPacket(EntityTrackerEntry serverEntity) {
      return this.createSyncPacket();
   }

   private Packet<ClientPlayPacketListener> createSyncPacket() {
      Validate.isTrue(!this.getEntityWorld().isClient());
      NbtCompound compoundTag = new NbtCompound();
      this.addAdditionalSaveData(compoundTag);
      return PlatformNetworking.get()
         .createS2CPacket(
            new ImmPtlNetworking.PortalSyncPacket(
               this.getId(),
               this.getUuid(),
               this.getType(),
               PortalAPI.serverDimKeyToInt(this.getEntityWorld().getServer(), this.getOriginDim()),
               this.getX(),
               this.getY(),
               this.getZ(),
               compoundTag
            )
         );
   }

   public boolean canBeSpectated(ServerPlayerEntity spectator) {
      return this.specificPlayerId == null ? true : spectator.getUuid().equals(this.specificPlayerId);
   }

   public void tick() {
      if (this.getBoundingBox().equals(NULL_BOX)) {
         LOGGER.error("Abnormal bounding box {}", this);
      }

      this.lastTickPortalState = this.getThisTickPortalState();
      if (!this.getEntityWorld().isClient() && this.reloadAndSyncNextTick) {
         this.reloadAndSyncToClient();
      }

      if (this.getEntityWorld().isClient()) {
         CLIENT_PORTAL_TICK_SIGNAL.invoker().accept(this);
      } else {
         if (!this.isPortalValid()) {
            LOGGER.info("Removed invalid portal {}", this);
            this.remove(class_5529.KILLED);
            return;
         }

         SERVER_PORTAL_TICK_SIGNAL.invoker().accept(this);
      }

      this.animation.tick(this);
      super.tick();
   }

   @NotNull
   protected Box calculateDefaultBoundingBox(Vec3d position) {
      if (this.axisW == null) {
         this.boundingBoxCache = null;
         return NULL_BOX;
      } else {
         if (this.boundingBoxCache == null) {
            this.boundingBoxCache = this.getPortalShape().getBoundingBox(this.getThisSideState(), this.shouldLimitBoundingBox(), 0.2);
         }

         return this.boundingBoxCache;
      }
   }

   protected boolean shouldLimitBoundingBox() {
      return !this.getIsGlobal();
   }

   public void move(MovementType type, Vec3d movement) {
   }

   public boolean isPortalValid() {
      boolean valid = this.dimensionTo != null
         && this.width != 0.0
         && this.height != 0.0
         && this.axisW != null
         && this.axisH != null
         && this.getDestPos() != null
         && this.axisW.lengthSquared() > 0.9
         && this.axisH.lengthSquared() > 0.9
         && this.getY() > McHelper.getMinY(this.getEntityWorld()) - 100;
      if (valid) {
         if (this.getEntityWorld() instanceof ServerWorld serverLevel) {
            ServerWorld destWorld = serverLevel.getServer().getWorld(this.dimensionTo);
            if (destWorld == null) {
               LOGGER.error("Portal Dest Dimension Missing {}", this.dimensionTo.getValue());
               return false;
            }

            boolean inWorldBorder = destWorld.getWorldBorder().contains(BlockPos.ofFloored(this.getDestPos()));
            if (!inWorldBorder) {
               LOGGER.error("Destination out of World Border {}", this);
               return false;
            }
         }

         return this.getEntityWorld().isClient() ? this.isPortalValidClient() : true;
      } else {
         return false;
      }
   }

   @Environment(EnvType.CLIENT)
   private boolean isPortalValidClient() {
      boolean contains = ClientWorldLoader.getServerDimensions().contains(this.dimensionTo);
      if (!contains) {
         LOGGER.error("Client Portal Dest Dimension Missing {}", this.dimensionTo.getValue());
      }

      return contains;
   }

   @Nullable
   @Override
   public UUID getDiscriminator() {
      return this.getUuid();
   }

   @NotNull
   @Override
   public String toString() {
      return String.format(
         "%s{%s,%s,(%s %.1f %.1f %.1f)->(%s %.1f %.1f %.1f)%s%s%s}",
         this.getClass().getSimpleName(),
         this.getId(),
         this.getApproximateFacingDirection(),
         this.getEntityWorld().getRegistryKey().getValue(),
         this.getX(),
         this.getY(),
         this.getZ(),
         this.dimensionTo.getValue(),
         this.getDestPos().x,
         this.getDestPos().y,
         this.getDestPos().z,
         this.specificPlayerId != null ? ",specificAccessor:" + this.specificPlayerId.toString() : "",
         this.hasScaling() ? ",scale:" + this.scaling : "",
         this.portalTag != null ? "," + this.portalTag : ""
      );
   }

   public Direction getApproximateFacingDirection() {
      return Direction.getFacing(this.getNormal().x, this.getNormal().y, this.getNormal().z);
   }

   public Vec3d transformVelocityRelativeToPortal(Vec3d originalVelocityRelativeToPortal, Entity entity, Vec3d oldEntityPos) {
      Vec3d result = this.transformLocalVec(originalVelocityRelativeToPortal);
      int maxVelocity = 15;
      if (originalVelocityRelativeToPortal.length() > 15.0) {
         result = result.normalize().multiply(15.0);
      }

      if (entity instanceof AbstractMinecartEntity && result.lengthSquared() < 0.5) {
         result = result.multiply(2.0);
      }

      return result;
   }

   public double getDistanceToPlane(Vec3d pos) {
      return pos.subtract(this.getOriginPos()).dotProduct(this.getNormal());
   }

   public boolean isInFrontOfPortal(Vec3d pos) {
      return this.getDistanceToPlane(pos) > 0.0;
   }

   public Vec3d getPointInPlane(double xInPlane, double yInPlane) {
      return this.getOriginPos().add(this.getPointInPlaneLocal(xInPlane, yInPlane));
   }

   public Vec3d getPointInPlaneLocal(double xInPlane, double yInPlane) {
      return this.axisW.multiply(xInPlane).add(this.axisH.multiply(yInPlane));
   }

   public Vec3d getPointInPlaneLocalClamped(double xInPlane, double yInPlane) {
      return this.getPointInPlaneLocal(
         MathHelper.clamp(xInPlane, -this.width / 2.0, this.width / 2.0), MathHelper.clamp(yInPlane, -this.height / 2.0, this.height / 2.0)
      );
   }

   public Vec3d[] getFourVerticesLocal(double shrinkFactor) {
      return new Vec3d[]{
         this.getPointInPlaneLocal(this.width / 2.0 - shrinkFactor, -this.height / 2.0 + shrinkFactor),
         this.getPointInPlaneLocal(-this.width / 2.0 + shrinkFactor, -this.height / 2.0 + shrinkFactor),
         this.getPointInPlaneLocal(this.width / 2.0 - shrinkFactor, this.height / 2.0 - shrinkFactor),
         this.getPointInPlaneLocal(-this.width / 2.0 + shrinkFactor, this.height / 2.0 - shrinkFactor)
      };
   }

   private Vec3d[] getFourVerticesLocalRotated(double shrinkFactor) {
      Vec3d[] fourVerticesLocal = this.getFourVerticesLocal(shrinkFactor);
      fourVerticesLocal[0] = this.transformLocalVec(fourVerticesLocal[0]);
      fourVerticesLocal[1] = this.transformLocalVec(fourVerticesLocal[1]);
      fourVerticesLocal[2] = this.transformLocalVec(fourVerticesLocal[2]);
      fourVerticesLocal[3] = this.transformLocalVec(fourVerticesLocal[3]);
      return fourVerticesLocal;
   }

   private Vec3d[] getFourVerticesLocalCullable(double shrink) {
      double xStart = -this.width / 2.0;
      double xEnd = this.width / 2.0;
      double yStart = -this.height / 2.0;
      double yEnd = this.height / 2.0;
      return new Vec3d[]{
         this.getPointInPlaneLocal(xEnd - shrink, yStart + shrink),
         this.getPointInPlaneLocal(xStart + shrink, yStart + shrink),
         this.getPointInPlaneLocal(xEnd - shrink, yEnd - shrink),
         this.getPointInPlaneLocal(xStart + shrink, yEnd - shrink)
      };
   }

   public final Vec3d transformPointRough(Vec3d pos) {
      Vec3d offset = this.getDestPos().subtract(this.getOriginPos());
      return pos.add(offset);
   }

   @Override
   public Vec3d transformLocalVecNonScale(Vec3d localVec) {
      return this.rotation == null ? localVec : this.rotation.rotate(localVec);
   }

   public Vec3d inverseTransformLocalVecNonScale(Vec3d localVec) {
      return this.rotation == null ? localVec : this.rotation.getConjugated().rotate(localVec);
   }

   @Override
   public Vec3d inverseTransformLocalVec(Vec3d localVec) {
      return this.inverseTransformLocalVecNonScale(localVec).multiply(1.0 / this.scaling);
   }

   @Override
   public Vec3d inverseTransformPoint(Vec3d point) {
      return this.getOriginPos().add(this.inverseTransformLocalVec(point.subtract(this.getDestPos())));
   }

   public boolean isMovedThroughPortal(Vec3d lastTickPos, Vec3d pos) {
      return this.rayTrace(lastTickPos, pos) != null;
   }

   @Nullable
   public Vec3d rayTrace(Vec3d from, Vec3d to) {
      return this.lenientRayTrace(from, to, 0.001);
   }

   @Nullable
   public Vec3d lenientRayTrace(Vec3d from, Vec3d to, double leniency) {
      RayTraceResult rayTraceResult = this.getPortalShape().raytracePortalShape(this.getThisSideState(), from, to, leniency);
      return rayTraceResult == null ? null : rayTraceResult.hitPos();
   }

   @Nullable
   public RayTraceResult generalRayTrace(Vec3d from, Vec3d to, double leniency) {
      return this.getPortalShape().raytracePortalShape(this.getThisSideState(), from, to, leniency);
   }

   @Override
   public double getDistanceToNearestPointInPortal(Vec3d point) {
      return this.getPortalShape().roughDistanceToPortalShape(this.getThisSideState(), point);
   }

   public Vec3d getPointProjectedToPlane(Vec3d pos) {
      Vec3d originPos = this.getOriginPos();
      return this.getLocalVecProjectedToPlane(pos.subtract(originPos)).add(originPos);
   }

   public Vec3d getLocalVecProjectedToPlane(Vec3d offset) {
      double yInPlane = offset.dotProduct(this.axisH);
      double xInPlane = offset.dotProduct(this.axisW);
      return this.axisW.multiply(xInPlane).add(this.axisH.multiply(yInPlane));
   }

   public Vec3d getNearestPointInPortal(Vec3d pos) {
      Vec3d originPos = this.getOriginPos();
      Vec3d offset = pos.subtract(originPos);
      double yInPlane = offset.dotProduct(this.axisH);
      double xInPlane = offset.dotProduct(this.axisW);
      xInPlane = MathHelper.clamp(xInPlane, -this.width / 2.0, this.width / 2.0);
      yInPlane = MathHelper.clamp(yInPlane, -this.height / 2.0, this.height / 2.0);
      return originPos.add(this.axisW.multiply(xInPlane)).add(this.axisH.multiply(yInPlane));
   }

   public World getDestinationWorld() {
      return this.getDestinationWorld(this.getEntityWorld().isClient());
   }

   private World getDestinationWorld(boolean isClient) {
      if (isClient) {
         return CHelper.getClientWorld(this.dimensionTo);
      } else {
         MinecraftServer server = this.getEntityWorld().getServer();

         assert server != null;

         return server.getWorld(this.dimensionTo);
      }
   }

   public static boolean isParallelPortal(Portal a, Portal b) {
      return a == b
         ? false
         : a.dimensionTo == b.getEntityWorld().getRegistryKey()
            && a.getEntityWorld().getRegistryKey() == b.dimensionTo
            && a.getOriginPos().distanceTo(b.getDestPos()) < 0.1
            && a.getDestPos().distanceTo(b.getOriginPos()) < 0.1
            && a.getNormal().dotProduct(b.getContentDirection()) < -0.9;
   }

   public static boolean isParallelOrientedPortal(Portal currPortal, Portal outerPortal) {
      double dot = currPortal.getOriginPos().subtract(outerPortal.getDestPos()).dotProduct(outerPortal.getContentDirection());
      return currPortal.getEntityWorld().getRegistryKey() == outerPortal.dimensionTo
         && currPortal.getNormal().dotProduct(outerPortal.getContentDirection()) < -0.9
         && Math.abs(dot) < 0.001;
   }

   public static boolean isReversePortal(Portal a, Portal b) {
      return a.dimensionTo == b.getEntityWorld().getRegistryKey()
         && a.getEntityWorld().getRegistryKey() == b.dimensionTo
         && a.getOriginPos().distanceTo(b.getDestPos()) < 0.1
         && a.getDestPos().distanceTo(b.getOriginPos()) < 0.1
         && a.getNormal().dotProduct(b.getContentDirection()) > 0.9;
   }

   public static boolean isFlippedPortal(Portal a, Portal b) {
      return a == b
         ? false
         : a.getEntityWorld() == b.getEntityWorld()
            && a.dimensionTo == b.dimensionTo
            && a.getOriginPos().distanceTo(b.getOriginPos()) < 0.1
            && a.getDestPos().distanceTo(b.getDestPos()) < 0.1
            && a.getNormal().dotProduct(b.getNormal()) < -0.9;
   }

   @Override
   public double getDestAreaRadiusEstimation() {
      return Math.max(this.width, this.height) * this.scaling;
   }

   @Override
   public boolean isConventionalPortal() {
      return true;
   }

   @Override
   public Box getThinBoundingBox() {
      if (this.thinBoundingBoxCache == null) {
         this.thinBoundingBoxCache = this.getPortalShape().getBoundingBox(this.getThisSideState(), false, 0.001);
      }

      return this.thinBoundingBoxCache;
   }

   @Environment(EnvType.CLIENT)
   @Override
   public boolean isRoughlyVisibleTo(Vec3d cameraPos) {
      return this.getPortalShape().roughTestVisibility(this.getThisSideState(), cameraPos, IrisInterface.invoker.isShaders());
   }

   @Nullable
   @Override
   public Plane getInnerClipping() {
      return this.getPortalShape().getInnerClipping(this.getThisSideState(), this.getOtherSideState(), this);
   }

   @Nullable
   @Override
   public Vec3d[] getOuterFrustumCullingVertices() {
      return this.getFourVerticesLocalCullable(0.0);
   }

   public Matrix4d getFullSpaceTransformation() {
      Vec3d originPos = this.getOriginPos();
      Vec3d destPos = this.getDestPos();
      DQuaternion rot = this.getRotationD();
      return new Matrix4d()
         .translation(destPos.x, destPos.y, destPos.z)
         .scale(this.getScale())
         .rotate(rot.toMcQuaternion())
         .translate(-originPos.x, -originPos.y, -originPos.z);
   }

   public double getWidth() {
      return this.width;
   }

   public double getHeight() {
      return this.height;
   }

   public double getThickness() {
      return this.thickness;
   }

   public Vec3d getAxisW() {
      return this.axisW;
   }

   public void setAxisW(Vec3d axisW) {
      this.axisW = axisW;
      this.updateCache();
   }

   public Vec3d getAxisH() {
      return this.axisH;
   }

   public void setAxisH(Vec3d axisH) {
      this.axisH = axisH;
      this.updateCache();
   }

   public void setDestDim(RegistryKey<World> dimensionTo) {
      this.dimensionTo = dimensionTo;
   }

   public Vec3d getDestination() {
      return this.destination;
   }

   public double getScaling() {
      return this.scaling;
   }

   public void setScaling(double scaling) {
      this.scaling = scaling;
      this.updateCache();
   }

   public boolean isTeleportChangesScale() {
      return this.teleportChangesScale;
   }

   public void setFuseView(boolean fuseView) {
      this.fuseView = fuseView;
   }

   public boolean isCrossPortalCollisionEnabled() {
      return this.crossPortalCollisionEnabled;
   }

   public void setCrossPortalCollisionEnabled(boolean crossPortalCollisionEnabled) {
      this.crossPortalCollisionEnabled = crossPortalCollisionEnabled;
   }

   public boolean isDoRenderPlayer() {
      return this.doRenderPlayer;
   }

   public void setDoRenderPlayer(boolean doRenderPlayer) {
      this.doRenderPlayer = doRenderPlayer;
   }

   @Nullable
   public List<String> getCommandsOnTeleported() {
      return this.commandsOnTeleported;
   }

   public void setCommandsOnTeleported(@Nullable List<String> commandsOnTeleported) {
      this.commandsOnTeleported = commandsOnTeleported;
   }

   public Portal.TransformationDesc getTransformationDesc() {
      return new Portal.TransformationDesc(this.getDestDim(), this.getFullSpaceTransformation(), this.getRotationD(), this.getScale());
   }

   @Override
   public boolean cannotRenderInMe(Portal portal) {
      return this.respectParallelOrientedPortal() ? isParallelPortal(portal, this) : isParallelOrientedPortal(portal, this);
   }

   public void myUnsetRemoved() {
      this.unsetRemoved();
   }

   public void calculateDimensions() {
      this.boundingBoxCache = null;
   }

   @Nullable
   @Override
   public Matrix4f getAdditionalCameraTransformation() {
      return PortalRenderer.getPortalTransformation(this);
   }

   public boolean canDoOuterFrustumCulling() {
      if (this.isFuseView()) {
         return false;
      } else {
         return !this.isVisible() ? false : this.getPortalShape().canDoOuterFrustumCulling();
      }
   }

   public boolean isTeleportable() {
      return this.teleportable;
   }

   public boolean respectParallelOrientedPortal() {
      return false;
   }

   public void onCollidingWithEntity(Entity entity) {
   }

   @Override
   public boolean getHasCrossPortalCollision() {
      return this.crossPortalCollisionEnabled;
   }

   public boolean getTeleportChangesScale() {
      return this.teleportChangesScale;
   }

   public void setTeleportChangesScale(boolean teleportChangesScale) {
      this.teleportChangesScale = teleportChangesScale;
   }

   public boolean getTeleportChangesGravity() {
      return this.teleportChangesGravity;
   }

   public boolean isTeleportChangesGravity() {
      return this.teleportChangesGravity;
   }

   public void setTeleportChangesGravity(boolean cond) {
      this.teleportChangesGravity = cond;
   }

   public Direction getTeleportedGravityDirection(Direction oldGravityDir) {
      return !this.getTeleportChangesGravity() ? oldGravityDir : this.getTransformedGravityDirection(oldGravityDir);
   }

   public Direction getTransformedGravityDirection(Direction oldGravityDir) {
      Vec3d oldGravityVec = Vec3d.of(oldGravityDir.getVector());
      Vec3d newGravityVec = this.transformLocalVecNonScale(oldGravityVec);
      return Direction.getFacing(newGravityVec.x, newGravityVec.y, newGravityVec.z);
   }

   @NotNull
   public PortalState getPortalState() {
      Validate.isTrue(this.axisH != null, "the portal is not yet initialized", new Object[0]);
      return new PortalState(
         this.getEntityWorld().getRegistryKey(),
         this.getOriginPos(),
         this.dimensionTo,
         this.getDestPos(),
         this.getScale(),
         this.getRotationD(),
         this.getOrientationRotation(),
         this.width,
         this.height,
         this.thickness,
         this instanceof Mirror
      );
   }

   public void setPortalState(PortalState state) {
      Validate.isTrue(this.getEntityWorld().getRegistryKey() == state.fromWorld);
      Validate.isTrue(this.dimensionTo == state.toWorld);
      this.width = state.width;
      this.height = state.height;
      this.thickness = state.thickness;
      this.setOriginPos(state.fromPos);
      this.setDestination(state.toPos);
      PortalManipulation.setPortalOrientationQuaternion(this, state.orientation);
      if (DQuaternion.isClose(state.rotation, DQuaternion.identity)) {
         this.setRotationTransformationD(null);
      } else {
         this.setRotationTransformationD(state.rotation);
      }

      this.setScaleTransformation(state.scaling);
   }

   public UnilateralPortalState getThisSideState() {
      if (this.thisSideStateCache == null) {
         this.thisSideStateCache = new UnilateralPortalState(
            this.getOriginDim(), this.getOriginPos(), this.getOrientationRotation(), this.width, this.height, this.thickness
         );
      }

      return this.thisSideStateCache;
   }

   public UnilateralPortalState getOtherSideState() {
      if (this.otherSideStateCache == null) {
         PortalState portalState = this.getPortalState();
         this.otherSideStateCache = UnilateralPortalState.extractOtherSide(portalState);
      }

      return this.otherSideStateCache;
   }

   public void setThisSideState(UnilateralPortalState ups) {
      this.setThisSideState(ups, false);
   }

   public void setThisSideState(UnilateralPortalState ups, boolean lockScale) {
      Validate.notNull(ups);
      PortalState portalState = this.getPortalState();
      PortalState newPortalState = portalState.withThisSideUpdated(ups, lockScale);
      this.setPortalState(newPortalState);
   }

   @Environment(EnvType.CLIENT)
   public void acceptDataSync(Vec3d pos, NbtCompound customData) {
      PortalState oldState = this.getPortalState();
      this.setPosition(pos);
      this.readAdditionalSaveData(customData);
      if (this.animation.defaultAnimation.durationTicks > 0) {
         this.animation.defaultAnimation.startClientDefaultAnimation(this, oldState);
      }

      CLIENT_PORTAL_ACCEPT_SYNC_EVENT.invoker().accept(this);
   }

   public NbtCompound writePortalDataToNbt() {
      NbtCompound nbtCompound = new NbtCompound();
      this.addAdditionalSaveData(nbtCompound);
      return nbtCompound;
   }

   public void readPortalDataFromNbt(NbtCompound compound) {
      try {
         this.readAdditionalSaveData(compound);
      } catch (Exception var3) {
         LOGGER.error("Failed to read portal data from nbt {}", compound, var3);
         if (!this.isPortalValid()) {
            this.setRemoved(class_5529.KILLED);
         }
      }
   }

   public void updatePortalFromNbt(NbtCompound newNbt) {
      NbtCompound data = this.writePortalDataToNbt();
      newNbt.getKeys().forEach(key -> data.put(key, newNbt.get(key)));
      this.readPortalDataFromNbt(data);
   }

   public void rectifyClusterPortals(boolean sync) {
      PortalExtension.get(this).rectifyClusterPortals(this, sync);
   }

   public void reloadPortal() {
      Validate.isTrue(!this.getEntityWorld().isClient());
      this.updateCache();
      this.rectifyClusterPortals(true);
      this.reloadAndSyncToClientNextTick();
   }

   public DefaultPortalAnimation getDefaultAnimation() {
      return this.animation.defaultAnimation;
   }

   public void clearAnimationDrivers(boolean clearThisSide, boolean clearOtherSide) {
      this.animation.clearAnimationDrivers(this, clearThisSide, clearOtherSide);
   }

   public void addThisSideAnimationDriver(PortalAnimationDriver driver) {
      this.getAnimationView().addThisSideAnimation(driver);
      this.reloadAndSyncClusterToClientNextTick();
   }

   public void addOtherSideAnimationDriver(PortalAnimationDriver driver) {
      this.getAnimationView().addOtherSideAnimation(driver);
      this.reloadAndSyncClusterToClientNextTick();
   }

   public void pauseAnimation() {
      this.animation.setPaused(this, true);
   }

   public void resumeAnimation() {
      this.animation.setPaused(this, false);
   }

   public void resetAnimationReferenceState(boolean resetThisSide, boolean resetOtherSide) {
      this.animation.resetReferenceState(this, resetThisSide, resetOtherSide);
   }

   public AnimationView getAnimationView() {
      PortalExtension extension = PortalExtension.get(this);
      if (extension.flippedPortal != null && extension.flippedPortal.animation.hasAnimationDriver()) {
         return new AnimationView(this, extension.flippedPortal, IntraClusterRelation.FLIPPED);
      } else if (extension.reversePortal != null && extension.reversePortal.animation.hasAnimationDriver()) {
         return new AnimationView(this, extension.reversePortal, IntraClusterRelation.REVERSE);
      } else {
         return extension.parallelPortal != null && extension.parallelPortal.animation.hasAnimationDriver()
            ? new AnimationView(this, extension.parallelPortal, IntraClusterRelation.PARALLEL)
            : new AnimationView(this, this, IntraClusterRelation.SAME);
      }
   }

   public boolean isOtherSideChunkLoaded() {
      Validate.isTrue(!this.getEntityWorld().isClient());
      return McHelper.isServerChunkFullyLoaded((ServerWorld)this.getDestWorld(), new ChunkPos(BlockPos.ofFloored(this.getDestPos())));
   }

   @Nullable
   public PortalState getLastTickPortalState() {
      return this.lastTickPortalState == null ? this.getThisTickPortalState() : this.lastTickPortalState;
   }

   @Nullable
   public PortalState getThisTickPortalState() {
      if (this.portalStateCache == null) {
         this.portalStateCache = this.getPortalState();
      }

      return this.portalStateCache;
   }

   public PortalState getAnimationEndingState() {
      return this.animation.getAnimationEndingState(this);
   }

   public Vec3d transformFromPortalLocalToWorld(Vec3d localPos) {
      return this.axisW
         .multiply(localPos.x)
         .add(this.axisH.multiply(localPos.y))
         .add(this.getNormal().multiply(localPos.z))
         .add(this.getOriginPos());
   }

   public Vec3d transformFromWorldToPortalLocal(Vec3d worldPos) {
      Vec3d relativePos = worldPos.subtract(this.getOriginPos());
      return new Vec3d(relativePos.dotProduct(this.axisW), relativePos.dotProduct(this.axisH), relativePos.dotProduct(this.getNormal()));
   }

   @Nullable
   public Portal getAnimationHolder() {
      if (this.animation.hasAnimationDriver()) {
         return this;
      } else {
         PortalExtension portalExtension = PortalExtension.get(this);
         if (portalExtension.flippedPortal != null && portalExtension.flippedPortal.animation.hasAnimationDriver()) {
            return portalExtension.flippedPortal;
         } else if (portalExtension.reversePortal != null && portalExtension.reversePortal.animation.hasAnimationDriver()) {
            return portalExtension.reversePortal;
         } else {
            return portalExtension.parallelPortal != null && portalExtension.parallelPortal.animation.hasAnimationDriver()
               ? portalExtension.parallelPortal
               : null;
         }
      }
   }

   public Portal getPossibleAnimationHolder() {
      Portal holder = this.getAnimationHolder();
      return holder != null ? holder : this;
   }

   public long getAnimationEffectiveTime() {
      Portal holder = this.getPossibleAnimationHolder();
      return holder.animation.getEffectiveTime(holder.getEntityWorld().getTime());
   }

   public void disableDefaultAnimation() {
      this.animation.defaultAnimation.durationTicks = 0;
      this.reloadAndSyncToClientNextTick();
   }

   public VoxelShape getThisSideCollisionExclusion() {
      if (this.thisSideCollisionExclusion == null) {
         this.thisSideCollisionExclusion = this.getPortalShape().getThisSideCollisionExclusion(this.getThisSideState());
      }

      return this.thisSideCollisionExclusion;
   }

   public record TransformationDesc(RegistryKey<World> dimensionTo, Matrix4d fullSpaceTransformation, DQuaternion rotation, double scaling) {
      public static boolean isRoughlyEqual(Portal.TransformationDesc a, Portal.TransformationDesc b) {
         if (a.dimensionTo != b.dimensionTo) {
            return false;
         } else {
            Matrix4d diff = new Matrix4d().set(a.fullSpaceTransformation).sub(b.fullSpaceTransformation);
            double diffSquareSum = diff.m00() * diff.m00()
               + diff.m01() * diff.m01()
               + diff.m02() * diff.m02()
               + diff.m03() * diff.m03()
               + diff.m10() * diff.m10()
               + diff.m11() * diff.m11()
               + diff.m12() * diff.m12()
               + diff.m13() * diff.m13()
               + diff.m20() * diff.m20()
               + diff.m21() * diff.m21()
               + diff.m22() * diff.m22()
               + diff.m23() * diff.m23()
               + diff.m30() * diff.m30()
               + diff.m31() * diff.m31()
               + diff.m32() * diff.m32()
               + diff.m33() * diff.m33();
            return diffSquareSum < 0.1;
         }
      }
   }
}
