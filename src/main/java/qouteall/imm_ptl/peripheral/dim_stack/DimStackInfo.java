package qouteall.imm_ptl.peripheral.dim_stack;

import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.api.PortalAPI;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.global_portals.GlobalPortalStorage;
import qouteall.imm_ptl.core.portal.global_portals.VerticalConnectingPortal;
import qouteall.q_misc_util.Helper;

public class DimStackInfo {
   public boolean loop;
   public boolean gravityTransform;
   public List<DimStackEntry> entries;

   public DimStackInfo() {
      this.entries = new ArrayList<>();
      this.loop = false;
      this.gravityTransform = false;
   }

   public DimStackInfo(List<DimStackEntry> entries, boolean loop, boolean gravityTransform) {
      this.entries = entries;
      this.loop = loop;
      this.gravityTransform = gravityTransform;
   }

   public static void initializeFuseViewProperty(Portal portal) {
      portal.setFuseView(true);
   }

   public static void createConnectionBetween(DimStackEntry a, DimStackEntry b, boolean gravityChange) {
      ServerWorld fromWorld = McHelper.getServerWorld(a.getDimension());
      ServerWorld toWorld = McHelper.getServerWorld(b.getDimension());
      boolean xorFlipped = a.flipped ^ b.flipped;
      int fromWorldMinY = McHelper.getMinY(fromWorld);
      if (a.bottomY != null) {
         fromWorldMinY = a.bottomY;
      }

      int fromWorldMaxY = McHelper.getMaxContentYExclusive(fromWorld);
      if (a.topY != null) {
         fromWorldMaxY = a.topY;
      }

      int toWorldMinY = McHelper.getMinY(toWorld);
      if (b.bottomY != null) {
         toWorldMinY = b.bottomY;
      }

      int toWorldMaxY = McHelper.getMaxContentYExclusive(toWorld);
      if (b.topY != null) {
         toWorldMaxY = b.topY;
      }

      VerticalConnectingPortal connectingPortal = VerticalConnectingPortal.createConnectingPortal(
         fromWorld,
         a.flipped ? VerticalConnectingPortal.ConnectorType.ceil : VerticalConnectingPortal.ConnectorType.floor,
         toWorld,
         b.scale / a.scale,
         xorFlipped,
         b.horizontalRotation - a.horizontalRotation,
         fromWorldMinY,
         fromWorldMaxY,
         toWorldMinY,
         toWorldMaxY
      );
      VerticalConnectingPortal reverse = PortalAPI.createReversePortal(connectingPortal);
      initializeFuseViewProperty(connectingPortal);
      initializeFuseViewProperty(reverse);
      if (gravityChange) {
         connectingPortal.setTeleportChangesGravity(true);
         reverse.setTeleportChangesGravity(true);
      }

      if (a.connectsNext) {
         PortalAPI.addGlobalPortal(fromWorld, connectingPortal);
      }

      if (b.connectsPrevious) {
         PortalAPI.addGlobalPortal(toWorld, reverse);
      }
   }

   public void apply(MinecraftServer server) {
      if (this.entries.isEmpty()) {
         McHelper.sendMessageToFirstLoggedPlayer(server, Text.literal("Error: No dimension for dimension stack"));
      } else {
         for (DimStackEntry entry : this.entries) {
            if (server.getWorld(entry.getDimension()) == null) {
               McHelper.sendMessageToFirstLoggedPlayer(
                  server, Text.literal("Failed to apply dimension stack. Missing dimension " + entry.dimensionIdStr).formatted(Formatting.RED)
               );
               return;
            }
         }

         if (!GlobalPortalStorage.getGlobalPortals(McHelper.getServerWorld(this.entries.get(0).getDimension())).isEmpty()) {
            Helper.err("There are already global portals when initializing dimension stack");
            McHelper.sendMessageToFirstLoggedPlayer(
               server,
               Text.literal("Failed to apply dimension stack because there are already global portals when initializing dimension stack")
                  .formatted(Formatting.RED)
            );
         } else {
            Map<DimStackInfo.PortalInfo, List<DimStackEntry>> portalInfoMap = this.getPortalInfoMap();

            for (Entry<DimStackInfo.PortalInfo, List<DimStackEntry>> entryx : portalInfoMap.entrySet()) {
               DimStackInfo.PortalInfo key = entryx.getKey();
               List<DimStackEntry> value = entryx.getValue();
               if (value != null && value.size() > 1) {
                  McHelper.sendMessageToFirstLoggedPlayer(
                     server,
                     Text.literal(
                           "Failed to apply dimension stack because of connection conflict. There are multiple connections in the %s of %s"
                              .formatted(key.connectorType, key.dimension.getValue())
                        )
                        .formatted(Formatting.RED)
                  );
                  return;
               }
            }

            Helper.<DimStackEntry, Pair>wrapAdjacentAndMap(this.entries.stream(), Pair::of).forEach(pair -> {
               DimStackEntry before = (DimStackEntry)pair.getFirst();
               DimStackEntry after = (DimStackEntry)pair.getSecond();
               createConnectionBetween(before, after, this.gravityTransform);
            });
            if (this.loop) {
               createConnectionBetween(this.entries.get(this.entries.size() - 1), this.entries.get(0), this.gravityTransform);
            }

            Map<RegistryKey<World>, BlockState> bedrockReplacementMap = new HashMap<>();

            for (DimStackEntry entryxx : this.entries) {
               String bedrockReplacementStr = entryxx.bedrockReplacementStr;
               BlockState bedrockReplacement = parseBlockString(bedrockReplacementStr);
               RegistryKey<World> dimId = entryxx.getDimension();
               if (bedrockReplacement != null) {
                  bedrockReplacementMap.put(dimId, bedrockReplacement);
               }

               ServerWorld world = server.getWorld(dimId);
               Validate.notNull(world, "Missing dimension %s", new Object[]{dimId.getValue()});
               GlobalPortalStorage gps = GlobalPortalStorage.get(world);
               gps.bedrockReplacement = bedrockReplacement;
               gps.onDataChanged();
            }

            DimStackManagement.bedrockReplacementMap = bedrockReplacementMap;
            McHelper.sendMessageToFirstLoggedPlayer(server, Text.translatable("imm_ptl.dim_stack_initialized"));
         }
      }
   }

   public boolean isEffectivelyConnectingPrevious(int index) {
      DimStackEntry entry = this.entries.get(index);
      if (!entry.connectsPrevious) {
         return false;
      } else {
         return index == 0 ? this.loop : true;
      }
   }

   public boolean isEffectivelyConnectionNext(int index) {
      DimStackEntry entry = this.entries.get(index);
      if (!entry.connectsNext) {
         return false;
      } else {
         return index == this.entries.size() - 1 ? this.loop : true;
      }
   }

   public boolean isEffectivelyConnectingCeil(int index) {
      DimStackEntry entry = this.entries.get(index);
      return !entry.flipped ? this.isEffectivelyConnectingPrevious(index) : this.isEffectivelyConnectionNext(index);
   }

   public boolean isEffectivelyConnectingFloor(int index) {
      DimStackEntry entry = this.entries.get(index);
      return !entry.flipped ? this.isEffectivelyConnectionNext(index) : this.isEffectivelyConnectingPrevious(index);
   }

   public Map<DimStackInfo.PortalInfo, List<DimStackEntry>> getPortalInfoMap() {
      Map<DimStackInfo.PortalInfo, List<DimStackEntry>> map = new HashMap<>();

      for (int i = 0; i < this.entries.size(); i++) {
         DimStackEntry entry = this.entries.get(i);
         if (this.isEffectivelyConnectingCeil(i)) {
            DimStackInfo.PortalInfo portalInfo = new DimStackInfo.PortalInfo(entry.getDimension(), VerticalConnectingPortal.ConnectorType.ceil);
            map.computeIfAbsent(portalInfo, k -> new ArrayList<>()).add(entry);
         }

         if (this.isEffectivelyConnectingFloor(i)) {
            DimStackInfo.PortalInfo portalInfo = new DimStackInfo.PortalInfo(entry.getDimension(), VerticalConnectingPortal.ConnectorType.floor);
            map.computeIfAbsent(portalInfo, k -> new ArrayList<>()).add(entry);
         }
      }

      return map;
   }

   @Nullable
   public static BlockState parseBlockString(@Nullable String str) {
      if (str == null) {
         return null;
      } else if (str.isEmpty()) {
         return null;
      } else {
         try {
            Optional<Block> block = Registries.BLOCK.getOptionalValue(McHelper.newResourceLocation(str));
            return block.<BlockState>map(Block::getDefaultState).orElse(null);
         } catch (Exception var2) {
            var2.printStackTrace();
            return null;
         }
      }
   }

   public boolean hasDimension(RegistryKey<World> dimension) {
      return this.entries.stream().anyMatch(e -> e.getDimension() == dimension);
   }

   public record PortalInfo(RegistryKey<World> dimension, VerticalConnectingPortal.ConnectorType connectorType) {
   }
}
