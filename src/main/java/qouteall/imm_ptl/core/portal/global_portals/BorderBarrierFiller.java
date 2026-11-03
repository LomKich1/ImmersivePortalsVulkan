package qouteall.imm_ptl.core.portal.global_portals;

import com.google.common.base.Supplier;
import com.google.common.collect.Streams;
import java.util.List;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import net.minecraft.block.Blocks;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerLightingProvider;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos.Mutable;
import net.minecraft.world.chunk.Chunk;
import qouteall.imm_ptl.core.McHelper;
import qouteall.q_misc_util.my_util.IntBox;

public class BorderBarrierFiller {
   private static final WeakHashMap<ServerPlayerEntity, Object> warnedPlayers = new WeakHashMap<>();

   public static void onCommandExecuted(ServerPlayerEntity player) {
      ServerWorld world = player.getEntityWorld();
      Vec3d playerPos = player.getEntityPos();
      List<WorldWrappingPortal.WrappingZone> wrappingZones = WorldWrappingPortal.getWrappingZones(world);
      WorldWrappingPortal.WrappingZone zone = wrappingZones.stream()
         .filter(wrappingZone -> wrappingZone.getArea().contains(playerPos))
         .findFirst()
         .orElse(null);
      if (zone == null) {
         player.sendMessage(Text.translatable("imm_ptl.cannot_find_zone"), false);
      } else {
         doInvoke(player, world, zone);
      }
   }

   public static void onCommandExecuted(ServerPlayerEntity player, int zoneId) {
      ServerWorld world = player.getEntityWorld();
      List<WorldWrappingPortal.WrappingZone> wrappingZones = WorldWrappingPortal.getWrappingZones(world);
      WorldWrappingPortal.WrappingZone zone = wrappingZones.stream().filter(wrappingZone -> wrappingZone.id == zoneId).findFirst().orElse(null);
      if (zone == null) {
         player.sendMessage(Text.translatable("imm_ptl.cannot_find_zone"), false);
      } else {
         doInvoke(player, world, zone);
      }
   }

   private static void doInvoke(ServerPlayerEntity player, ServerWorld world, WorldWrappingPortal.WrappingZone zone) {
      IntBox borderBox = zone.getBorderBox();
      boolean warned = warnedPlayers.containsKey(player);
      if (!warned) {
         warnedPlayers.put(player, null);
         BlockPos size = borderBox.getSize();
         int totalColumns = size.getX() * 2 + size.getZ() * 2;
         double sizeEstimationGB = totalColumns / 80000.0 * 0.5;
         player.sendMessage(Text.translatable("imm_ptl.clear_border_warning", new Object[]{sizeEstimationGB < 0.01 ? 0.0 : sizeEstimationGB}), false);
      } else {
         warnedPlayers.remove(player);
         player.sendMessage(Text.translatable("imm_ptl.start_clearing_border"), false);
         startFillingBorder(world, borderBox, l -> player.sendMessage(l, false));
      }
   }

   private static void startFillingBorder(ServerWorld world, IntBox borderBox, Consumer<Text> informer) {
      Supplier<IntStream> xStream = () -> IntStream.range(borderBox.l.getX(), borderBox.h.getX() + 1);
      Supplier<IntStream> zStream = () -> IntStream.range(borderBox.l.getZ(), borderBox.h.getZ() + 1);
      Mutable temp = new Mutable();
      Mutable temp1 = new Mutable();
      Stream<Mutable> stream = Streams.concat(
         new Stream[]{
            ((IntStream)xStream.get()).mapToObj(x -> temp.set(x, 0, borderBox.l.getZ())),
            ((IntStream)xStream.get()).mapToObj(x -> temp.set(x, 0, borderBox.h.getZ())),
            ((IntStream)zStream.get()).mapToObj(z -> temp.set(borderBox.l.getX(), 0, z)),
            ((IntStream)zStream.get()).mapToObj(z -> temp.set(borderBox.h.getX(), 0, z))
         }
      );
      BlockPos size = borderBox.getSize();
      int totalColumns = size.getX() * 2 + size.getZ() * 2;
      int minY = McHelper.getMinY(world);
      int maxYEx = McHelper.getMaxYExclusive(world);
      ServerLightingProvider lightingProvider = world.getChunkManager().getLightingProvider();
      McHelper.performMultiThreadedFindingTaskOnServer(world.getServer(), stream, columnPos -> {
         Chunk chunk = world.getChunk(columnPos);

         for (int y = minY; y < maxYEx; y++) {
            temp1.set(columnPos.getX(), y, columnPos.getZ());
            chunk.setBlockState(temp1, Blocks.AIR.getDefaultState(), 1);
            lightingProvider.checkBlock(temp1);
         }

         return false;
      }, columns -> {
         if (McHelper.getServerGameTime() % 20L == 0L) {
            informer.accept(Text.literal(String.format("Progress: %d / %d", columns, totalColumns)));
         }

         return true;
      }, e -> {}, () -> informer.accept(Text.translatable("imm_ptl.finished_clearing_border")), () -> {});
   }
}
