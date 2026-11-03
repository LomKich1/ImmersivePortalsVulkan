package qouteall.imm_ptl.core.portal.custom_portal_gen;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.util.ActionResult;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Position;
import net.minecraft.registry.Registry;
import net.minecraft.util.math.Vec3d;
import net.minecraft.text.Text;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.IPPerServerInfo;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.mc_utils.ServerTaskList;
import qouteall.q_misc_util.my_util.WithDim;
import qouteall.q_misc_util.platform.Platform;

public class CustomPortalGenManager {
   private static final Logger LOGGER = LogUtils.getLogger();
   private final Multimap<Item, CustomPortalGeneration> useItemGen = HashMultimap.create();
   private final Multimap<Item, CustomPortalGeneration> throwItemGen = HashMultimap.create();
   private final ArrayList<CustomPortalGeneration> convGen = new ArrayList<>();
   private final Map<UUID, WithDim<Vec3d>> playerPosBeforeTravel = new HashMap<>();

   public static void init() {
      registerDataPackRegistries();
      Platform.get().onServerDataPackReloadEnd(server -> onDataPackReloaded(server));
      Platform.get().onServerStarted(CustomPortalGenManager::onDataPackReloaded);
   }

   public static void registerDataPackRegistries() {
      Platform.get().registerDynamicRegistry(CustomPortalGeneration.REGISTRY_KEY, CustomPortalGeneration.CODEC);
      Platform.get().registerDynamicRegistry(CustomPortalGeneration.LEGACY_REGISTRY_KEY, CustomPortalGeneration.CODEC);
   }

   private static void onDataPackReloaded(MinecraftServer server) {
      if (IPGlobal.enableDatapackPortalGen) {
         LOGGER.info("Processing custom portal generation");
         CustomPortalGenManager manager = new CustomPortalGenManager();
         Registry<CustomPortalGeneration> registry = server.getRegistryManager().get(CustomPortalGeneration.REGISTRY_KEY);
         Registry<CustomPortalGeneration> legacyRegistry = server.getRegistryManager().get(CustomPortalGeneration.LEGACY_REGISTRY_KEY);

         for (Entry<RegistryKey<CustomPortalGeneration>, CustomPortalGeneration> entry : registry.getEntrySet()) {
            manager.addEntry(server, entry.getKey(), entry.getValue());
         }

         for (Entry<RegistryKey<CustomPortalGeneration>, CustomPortalGeneration> entry : legacyRegistry.getEntrySet()) {
            manager.addEntry(server, entry.getKey(), entry.getValue());
         }

         IPPerServerInfo perServerInfo = IPPerServerInfo.of(server);
         perServerInfo.customPortalGenManager = manager;
      }
   }

   private void addEntry(MinecraftServer server, RegistryKey<CustomPortalGeneration> key, CustomPortalGeneration gen) {
      gen.identifier = key.getValue();
      CustomPortalGeneration.InitializationResult r1 = gen.initAndCheck(server);
      if (!(r1 instanceof CustomPortalGeneration.InitializationOk)) {
         LOGGER.info("Custom portal generation is not activated: {}\n{}", r1, gen.toString());
      } else {
         LOGGER.info("Loaded Custom Portal Generation {}", key.getValue());
         this.load(gen);
         if (gen.reversible) {
            CustomPortalGeneration reverse = gen.getReverse();
            if (reverse != null) {
               reverse.identifier = key.getValue();
               CustomPortalGeneration.InitializationResult r2 = reverse.initAndCheck(server);
               if (!(r2 instanceof CustomPortalGeneration.InitializationOk)) {
                  LOGGER.info("Reverse custom portal generation is not activated: {}\n{}", r2, reverse.toString());
                  return;
               }
            } else {
               McHelper.sendMessageToFirstLoggedPlayer(server, Text.literal("Cannot create reverse generation of " + gen));
            }
         }
      }
   }

   private void load(CustomPortalGeneration gen) {
      PortalGenTrigger trigger = gen.trigger;
      if (trigger instanceof PortalGenTrigger.UseItemTrigger) {
         this.useItemGen.put(((PortalGenTrigger.UseItemTrigger)trigger).item, gen);
      } else if (trigger instanceof PortalGenTrigger.ThrowItemTrigger) {
         this.throwItemGen.put(((PortalGenTrigger.ThrowItemTrigger)trigger).item, gen);
      } else if (trigger instanceof PortalGenTrigger.ConventionalDimensionChangeTrigger) {
         this.convGen.add(gen);
      }
   }

   public void onItemUse(ItemUsageContext context, ActionResult actionResult) {
      if (!context.getWorld().isClient()) {
         MinecraftServer server = context.getWorld().getServer();
         Item item = context.getStack().getItem();
         if (this.useItemGen.containsKey(item)) {
            ServerTaskList.of(server)
               .addTask(
                  () -> {
                     for (CustomPortalGeneration gen : this.useItemGen.get(item)) {
                        boolean result = gen.perform(
                           (ServerWorld)context.getWorld(), context.getBlockPos().method_10093(context.getSide()), context.getPlayer()
                        );
                        if (result) {
                           if (gen.trigger instanceof PortalGenTrigger.UseItemTrigger trigger && trigger.shouldConsume(context)) {
                              context.getStack().decrement(1);
                           }
                           break;
                        }
                     }

                     return true;
                  }
               );
         }
      }
   }

   public void onItemTick(ItemEntity entity) {
      Validate.isTrue(!entity.getEntityWorld().isClient());
      if (entity.cannotPickup()) {
         Item item = entity.getStack().getItem();
         Collection<CustomPortalGeneration> gens = this.throwItemGen.get(item);
         if (!gens.isEmpty()) {
            ServerTaskList.of(entity.getEntityWorld().getServer()).addTask(() -> {
               for (CustomPortalGeneration gen : gens) {
                  boolean result = gen.perform((ServerWorld)entity.getEntityWorld(), entity.getBlockPos(), entity);
                  if (result) {
                     entity.getStack().decrement(1);
                     break;
                  }
               }

               return true;
            });
         }
      }
   }

   public void onBeforeConventionalDimensionChange(ServerPlayerEntity player) {
      this.playerPosBeforeTravel.put(player.getUuid(), new WithDim<>(player.getServerWorld().getRegistryKey(), player.getEntityPos()));
   }

   public void onAfterConventionalDimensionChange(ServerPlayerEntity player) {
      UUID uuid = player.getUuid();
      if (this.playerPosBeforeTravel.containsKey(uuid)) {
         WithDim<Vec3d> startCoord = this.playerPosBeforeTravel.get(uuid);
         ServerWorld startWorld = player.server.getWorld(startCoord.dimension());
         if (startWorld == null) {
            LOGGER.error("Cannot find world {}", startCoord.dimension());
            return;
         }

         BlockPos startPos = BlockPos.ofFloored((Position)startCoord.value());

         for (CustomPortalGeneration gen : this.convGen) {
            boolean succeeded = gen.perform(startWorld, startPos, player);
            if (succeeded) {
               this.playerPosBeforeTravel.remove(uuid);
               return;
            }
         }
      }

      this.playerPosBeforeTravel.remove(uuid);
   }
}
