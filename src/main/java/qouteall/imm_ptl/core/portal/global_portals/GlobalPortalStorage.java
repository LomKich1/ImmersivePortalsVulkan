package qouteall.imm_ptl.core.portal.global_portals;

import com.mojang.logging.LogUtils;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.Entity.RemovalReason;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.listener.ClientCommonPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;
import net.minecraft.world.World;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import qouteall.dimlib.api.DimensionAPI;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.IPCGlobal;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.api.PortalAPI;
import qouteall.imm_ptl.core.ducks.IEClientWorld;
import qouteall.imm_ptl.core.network.ImmPtlNetworking;
import qouteall.imm_ptl.core.platform_specific.O_O;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.MiscHelper;
import qouteall.q_misc_util.platform.Platform;
import qouteall.q_misc_util.platform.PlatformNetworking;

public class GlobalPortalStorage extends PersistentState {
   private static final Logger LOGGER = LogUtils.getLogger();
   public List<Portal> data;
   public final WeakReference<ServerWorld> world;
   private int version = 1;
   private boolean shouldReSync = false;
   @Nullable
   public BlockState bedrockReplacement;

   public static void init() {
      Platform.get().onServerTickEnd(server -> server.getWorlds().forEach(world1 -> {
         GlobalPortalStorage gps = get(world1);
         gps.tick();
      }));
      IPGlobal.SERVER_CLEANUP_EVENT.register(s -> {
         for (ServerWorld world : s.getWorlds()) {
            get(world).onServerClose();
         }
      });
      DimensionAPI.SERVER_DIMENSION_DYNAMIC_UPDATE_EVENT.register((server, dims) -> {
         for (ServerWorld world : server.getWorlds()) {
            GlobalPortalStorage gps = get(world);
            gps.clearAbnormalPortals(server);
            gps.syncToAllPlayers();
         }
      });
      if (!O_O.isDedicatedServer()) {
         initClient();
      }
   }

   public static GlobalPortalStorage get(ServerWorld world) {
      return (GlobalPortalStorage)world.getPersistentStateManager().getOrCreate(new PersistentStateType("global_portal", () -> {
         LOGGER.info("Global portal storage initialized {}", world.getRegistryKey().getValue());
         return new GlobalPortalStorage(world);
      }, NbtCompound.CODEC.xmap(nbt -> {
         GlobalPortalStorage globalPortalStorage = new GlobalPortalStorage(world);
         globalPortalStorage.fromNbt(nbt);
         return globalPortalStorage;
      }, storage -> storage.save(new NbtCompound())), null));
   }

   @Environment(EnvType.CLIENT)
   private static void initClient() {
      IPCGlobal.CLIENT_CLEANUP_EVENT.register(GlobalPortalStorage::onClientCleanup);
   }

   @Environment(EnvType.CLIENT)
   private static void onClientCleanup() {
      if (ClientWorldLoader.getIsInitialized()) {
         for (ClientWorld clientWorld : ClientWorldLoader.getClientWorlds()) {
            for (Portal globalPortal : getGlobalPortals(clientWorld)) {
               globalPortal.remove(RemovalReason.UNLOADED_TO_CHUNK);
            }
         }
      }
   }

   public GlobalPortalStorage(ServerWorld world_) {
      this.world = new WeakReference<>(world_);
      this.data = new ArrayList<>();
   }

   public static void onPlayerLoggedIn(ServerPlayerEntity player) {
      MiscHelper.getServer().getWorlds().forEach(world -> {
         GlobalPortalStorage storage = get(world);
         if (!storage.data.isEmpty()) {
            Packet<ClientCommonPacketListener> packet = createSyncPacket(world, storage);
            player.networkHandler.sendPacket(packet);
         }
      });
   }

   public static Packet<ClientCommonPacketListener> createSyncPacket(ServerWorld world, GlobalPortalStorage storage) {
      return PlatformNetworking.get()
         .createS2CPacket(
            new ImmPtlNetworking.GlobalPortalSyncPacket(PortalAPI.serverDimKeyToInt(world.getServer(), world.getRegistryKey()), storage.save(new NbtCompound()))
         );
   }

   public void onDataChanged() {
      this.setDirty(true);
      this.shouldReSync = true;
   }

   public void removePortal(Portal portal) {
      this.data.remove(portal);
      portal.remove(RemovalReason.KILLED);
      this.onDataChanged();
   }

   public void addPortal(Portal portal) {
      Validate.isTrue(!this.data.contains(portal));
      Validate.isTrue(portal.isPortalValid());
      portal.isGlobalPortal = true;
      portal.myUnsetRemoved();
      this.data.add(portal);
      this.onDataChanged();
   }

   public void removePortals(Predicate<Portal> predicate) {
      this.data.removeIf(portal -> {
         boolean shouldRemove = predicate.test(portal);
         if (shouldRemove) {
            portal.remove(RemovalReason.KILLED);
         }

         return shouldRemove;
      });
      this.onDataChanged();
   }

   private void syncToAllPlayers() {
      ServerWorld currWorld = this.world.get();
      Validate.notNull(currWorld);
      Packet packet = createSyncPacket(currWorld, this);
      McHelper.getRawPlayerList().forEach(player -> player.networkHandler.sendPacket(packet));
   }

   public void fromNbt(NbtCompound tag) {
      ServerWorld currWorld = this.world.get();
      Validate.notNull(currWorld, "world is null", new Object[0]);
      List<Portal> newData = getPortalsFromTag(tag, currWorld);
      this.data = newData;
      if (tag.contains("version")) {
         this.version = tag.getInt("version", 0);
      }

      if (tag.contains("bedrockReplacement")) {
         this.bedrockReplacement = NbtHelper.toBlockState(
            currWorld.createCommandRegistryWrapper(RegistryKeys.BLOCK), tag.getCompoundOrEmpty("bedrockReplacement")
         );
      } else {
         this.bedrockReplacement = null;
      }

      this.clearAbnormalPortals(currWorld.getServer());
   }

   private static List<Portal> getPortalsFromTag(NbtCompound tag, World currWorld) {
      NbtList listTag = tag.getListOrEmpty("data");
      List<Portal> newData = new ArrayList<>();

      for (int i = 0; i < listTag.size(); i++) {
         NbtCompound compoundTag = listTag.getCompoundOrEmpty(i);
         Portal e = readPortalFromTag(currWorld, compoundTag);
         if (e != null) {
            newData.add(e);
         } else {
            Helper.err("error reading portal" + compoundTag);
         }
      }

      return newData;
   }

   private static Portal readPortalFromTag(World currWorld, NbtCompound compoundTag) {
      Identifier entityId = McHelper.newResourceLocation(compoundTag.getString("entity_type", ""));
      EntityType<?> entityType = (EntityType<?>)Registries.ENTITY_TYPE.get(entityId);
      Entity e = entityType.create(currWorld, SpawnReason.LOAD);
      McHelper.loadEntityFromNbt(e, compoundTag);
      ((Portal)e).isGlobalPortal = true;
      ((Portal)e).updateCache();
      return (Portal)e;
   }

   @NotNull
   public NbtCompound save(NbtCompound tag) {
      if (this.data == null) {
         return tag;
      } else {
         NbtList listTag = new NbtList();
         ServerWorld currWorld = this.world.get();
         Validate.notNull(currWorld, "world is null", new Object[0]);

         for (Portal portal : this.data) {
            Validate.isTrue(portal.getEntityWorld() == currWorld);
            NbtCompound portalTag = McHelper.saveEntityWithoutIdToNbt(portal);
            portalTag.putString("entity_type", EntityType.getId(portal.getType()).toString());
            listTag.add(portalTag);
         }

         tag.put("data", listTag);
         tag.putInt("version", this.version);
         if (this.bedrockReplacement != null) {
            tag.put("bedrockReplacement", NbtHelper.fromBlockState(this.bedrockReplacement));
         }

         return tag;
      }
   }

   public void tick() {
      if (this.shouldReSync) {
         this.syncToAllPlayers();
         this.shouldReSync = false;
      }

      if (this.version <= 1) {
         upgradeData(this.world.get());
         this.version = 2;
         this.setDirty(true);
      }
   }

   public void clearAbnormalPortals(MinecraftServer server) {
      this.data.removeIf(e -> {
         RegistryKey<World> dimensionTo = e.getDestDim();
         if (server.getWorld(dimensionTo) == null) {
            LOGGER.error("Missing Dimension for global portal {}", dimensionTo.getValue());
            return true;
         } else {
            return false;
         }
      });
   }

   private static void upgradeData(ServerWorld world) {
   }

   @Environment(EnvType.CLIENT)
   public static void receiveGlobalPortalSync(RegistryKey<World> dimension, NbtCompound compoundTag) {
      ClientWorld world = ClientWorldLoader.getWorld(dimension);
      List<Portal> oldGlobalPortals = ((IEClientWorld)world).ip_getGlobalPortals();
      if (oldGlobalPortals != null) {
         for (Portal p : oldGlobalPortals) {
            p.remove(RemovalReason.KILLED);
         }
      }

      List<Portal> newPortals = getPortalsFromTag(compoundTag, world);

      for (Portal p : newPortals) {
         p.myUnsetRemoved();
         p.isGlobalPortal = true;
         Validate.isTrue(p.isPortalValid());
         ClientWorldLoader.getWorld(p.getDestDim());
      }

      ((IEClientWorld)world).ip_setGlobalPortals(newPortals);
      LOGGER.info("Global Portals Updated {}", dimension.getValue());
   }

   public static void convertNormalPortalIntoGlobalPortal(Portal portal) {
      Validate.isTrue(!portal.getIsGlobal());
      Validate.isTrue(!portal.getEntityWorld().isClient());
      portal.setPortalShapeToDefault();
      portal.remove(RemovalReason.KILLED);
      Portal newPortal = McHelper.copyEntity(portal);
      get((ServerWorld)portal.getEntityWorld()).addPortal(newPortal);
   }

   public static void convertGlobalPortalIntoNormalPortal(Portal portal) {
      Validate.isTrue(portal.getIsGlobal());
      Validate.isTrue(!portal.getEntityWorld().isClient());
      get((ServerWorld)portal.getEntityWorld()).removePortal(portal);
      Portal newPortal = McHelper.copyEntity(portal);
      McHelper.spawnServerEntity(newPortal);
   }

   private void onServerClose() {
      for (Portal portal : this.data) {
         portal.remove(RemovalReason.UNLOADED_TO_CHUNK);
      }
   }

   @NotNull
   public static List<Portal> getGlobalPortals(World world) {
      List<Portal> result;
      if (world.isClient()) {
         result = CHelper.getClientGlobalPortal(world);
      } else if (world instanceof ServerWorld) {
         result = get((ServerWorld)world).data;
      } else {
         result = null;
      }

      return result != null ? result : Collections.emptyList();
   }
}
