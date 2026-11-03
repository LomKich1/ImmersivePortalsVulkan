package qouteall.imm_ptl.core.chunk_loading;

import java.util.Set;
import net.minecraft.network.packet.s2c.play.GameStateChangeS2CPacket;
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.profiler.Profilers;
import net.minecraft.world.World;
import net.minecraft.world.rule.GameRules;
import org.apache.commons.lang3.Validate;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.network.PacketRedirection;
import qouteall.q_misc_util.platform.Platform;

public class WorldInfoSender {
   public static void init() {
      Platform.get().onServerTickEnd(server -> {
         Profilers.get().push("portal_send_world_info");
         if (McHelper.getServerGameTime() % 100L == 42L) {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
               Set<RegistryKey<World>> visibleDimensions = ImmPtlChunkTracking.getVisibleDimensions(player);
               if (player.getEntityWorld().getRegistryKey() != World.OVERWORLD) {
                  ServerWorld overworld = server.getWorld(World.OVERWORLD);
                  Validate.notNull(overworld, "missing overworld", new Object[0]);
                  sendWorldInfo(player, overworld);
               }

               server.getWorlds().forEach(thisWorld -> {
                  if (isNonOverworldSurfaceDimension(thisWorld) && visibleDimensions.contains(thisWorld.getRegistryKey())) {
                     sendWorldInfo(player, thisWorld);
                  }
               });
            }
         }

         Profilers.get().pop();
      });
   }

   public static void sendWorldInfo(ServerPlayerEntity player, ServerWorld world) {
      RegistryKey<World> remoteDimension = world.getRegistryKey();
      PacketRedirection.sendRedirectedMessage(
         player,
         remoteDimension,
         new WorldTimeUpdateS2CPacket(world.getTime(), world.getTimeOfDay(), (Boolean)world.getGameRules().getValue(GameRules.ADVANCE_TIME))
      );
      if (world.isRaining()) {
         PacketRedirection.sendRedirectedMessage(player, world.getRegistryKey(), new GameStateChangeS2CPacket(GameStateChangeS2CPacket.RAIN_STARTED, 0.0F));
      }

      PacketRedirection.sendRedirectedMessage(
         player, world.getRegistryKey(), new GameStateChangeS2CPacket(GameStateChangeS2CPacket.RAIN_GRADIENT_CHANGED, world.getRainGradient(1.0F))
      );
      PacketRedirection.sendRedirectedMessage(
         player, world.getRegistryKey(), new GameStateChangeS2CPacket(GameStateChangeS2CPacket.THUNDER_GRADIENT_CHANGED, world.getThunderGradient(1.0F))
      );
   }

   public static boolean isNonOverworldSurfaceDimension(World world) {
      return world.getDimension().hasSkyLight() && world.getRegistryKey() != World.OVERWORLD;
   }
}
