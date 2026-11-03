package qouteall.imm_ptl.core.commands;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.command.DefaultPermissions;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.command.argument.DimensionArgumentType;
import net.minecraft.command.argument.Vec3ArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.DynamicRegistryManager.Immutable;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ChunkHolder;
import net.minecraft.server.world.ChunkLevelManager;
import net.minecraft.server.world.ChunkTicket;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerEntityManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.profiler.ProfilerSystem;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.EmptyChunk;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.entity.EntityLookup;
import org.apache.commons.lang3.Validate;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.api.example.ExampleGuiPortalRendering;
import qouteall.imm_ptl.core.chunk_loading.ChunkVisibility;
import qouteall.imm_ptl.core.chunk_loading.ImmPtlChunkTickets;
import qouteall.imm_ptl.core.chunk_loading.ImmPtlChunkTracking;
import qouteall.imm_ptl.core.chunk_loading.PlayerChunkLoading;
import qouteall.imm_ptl.core.ducks.IEDistanceManager;
import qouteall.imm_ptl.core.ducks.IEServerChunkCache;
import qouteall.imm_ptl.core.ducks.IEServerWorld;
import qouteall.imm_ptl.core.ducks.IEWorld;
import qouteall.imm_ptl.core.mc_utils.ServerTaskList;
import qouteall.imm_ptl.core.mixin.common.chunk_sync.IEChunkMap_Accessor;
import qouteall.imm_ptl.core.mixin.common.mc_util.IELevelEntityGetterAdapter;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.shape.SpecialFlatPortalShape;
import qouteall.q_misc_util.MiscHelper;
import qouteall.q_misc_util.api.McRemoteProcedureCall;
import qouteall.q_misc_util.my_util.MyTaskList;

public class PortalDebugCommands {
   private static final Logger LOGGER = LogUtils.getLogger();

   private static String getTicketTypeName(ChunkTicketType ticketType) {
      if (ticketType == ImmPtlChunkTickets.TICKET_TYPE) {
         return "imm_ptl";
      } else {
         Identifier key = Registries.TICKET_TYPE.getId(ticketType);
         return key != null ? key.toString() : ticketType.toString();
      }
   }

   static void registerDebugCommands(LiteralArgumentBuilder<ServerCommandSource> builder) {
      builder.then(
         CommandManager.literal("gui_portal")
            .then(
               CommandManager.argument("dim", DimensionArgumentType.dimension())
                  .then(
                     CommandManager.argument("pos", Vec3ArgumentType.vec3(false))
                        .executes(
                           context -> {
                              ExampleGuiPortalRendering.onCommandExecuted(
                                 ((ServerCommandSource)context.getSource()).getPlayerOrThrow(),
                                 DimensionArgumentType.getDimensionArgument(context, "dim"),
                                 Vec3ArgumentType.getVec3(context, "pos")
                              );
                              return 0;
                           }
                        )
                  )
            )
      );
      builder.then(CommandManager.literal("isometric_enable").then(CommandManager.argument("viewLength", FloatArgumentType.floatArg()).executes(context -> {
         ServerPlayerEntity player = ((ServerCommandSource)context.getSource()).getPlayerOrThrow();
         float viewLength = FloatArgumentType.getFloat(context, "viewLength");
         Validate.isTrue(Math.abs(viewLength) > 0.1);
         McRemoteProcedureCall.tellClientToInvoke(player, "qouteall.imm_ptl.core.render.TransformationManager.RemoteCallables.enableIsometricView", viewLength);
         return 0;
      })));
      builder.then(CommandManager.literal("isometric_disable").executes(context -> {
         ServerPlayerEntity player = ((ServerCommandSource)context.getSource()).getPlayerOrThrow();
         McRemoteProcedureCall.tellClientToInvoke(player, "qouteall.imm_ptl.core.render.TransformationManager.RemoteCallables.disableIsometricView");
         return 0;
      }));
      builder.then(CommandManager.literal("align").executes(context -> {
         ServerPlayerEntity player = ((ServerCommandSource)context.getSource()).getPlayerOrThrow();
         Vec3d pos = player.getEntityPos();
         Vec3d newPos = new Vec3d(Math.round(pos.x * 2.0) / 2.0, Math.round(pos.y * 2.0) / 2.0, Math.round(pos.z * 2.0) / 2.0);
         player.networkHandler.requestTeleport(newPos.x, newPos.y, newPos.z, 45.0F, 30.0F);
         return 0;
      }));
      builder.then(
         ((LiteralArgumentBuilder)CommandManager.literal("profile")
               .then(
                  ((LiteralArgumentBuilder)CommandManager.literal("set_lag_logging_threshold")
                        .requires(serverCommandSource -> serverCommandSource.getPermissions().hasPermission(DefaultPermissions.OWNERS)))
                     .then(CommandManager.argument("ms", IntegerArgumentType.integer()).executes(context -> {
                        int ms = IntegerArgumentType.getInteger(context, "ms");
                        ProfilerSystem.TIMEOUT_NANOSECONDS = Duration.ofMillis(ms).toNanos();
                        return 0;
                     }))
               ))
            .then(
               ((LiteralArgumentBuilder)CommandManager.literal("gc")
                     .requires(serverCommandSource -> serverCommandSource.getPermissions().hasPermission(DefaultPermissions.OWNERS)))
                  .executes(
                     context -> {
                        System.gc();
                        long l = Runtime.getRuntime().maxMemory();
                        long m = Runtime.getRuntime().totalMemory();
                        long n = Runtime.getRuntime().freeMemory();
                        long o = m - n;
                        ((ServerCommandSource)context.getSource())
                           .sendFeedback(() -> Text.literal(String.format("Memory: % 2d%% %03d/%03dMB", o * 100L / l, toMiB(o), toMiB(l))), false);
                        return 0;
                     }
                  )
            )
      );
      builder.then(
         ((LiteralArgumentBuilder)CommandManager.literal("create_command_stick")
               .requires(serverCommandSource -> serverCommandSource.getPermissions().hasPermission(DefaultPermissions.GAMEMASTERS)))
            .then(
               CommandManager.argument("command", StringArgumentType.string())
                  .executes(
                     context -> {
                        PortalCommand.createCommandStickCommandSignal
                           .emit(((ServerCommandSource)context.getSource()).getPlayerOrThrow(), StringArgumentType.getString(context, "command"));
                        return 0;
                     }
                  )
            )
      );
      builder.then(
         ((LiteralArgumentBuilder)CommandManager.literal("accelerate").requires(PortalCommand::canUsePortalCommand))
            .then(
               CommandManager.argument("v", DoubleArgumentType.doubleArg())
                  .executes(
                     context -> {
                        double v = DoubleArgumentType.getDouble(context, "v");
                        ServerPlayerEntity player = ((ServerCommandSource)context.getSource()).getPlayer();
                        Vec3d vec = player.getRotationVec(1.0F).multiply(v / 20.0);
                        McRemoteProcedureCall.tellClientToInvoke(
                           ((ServerCommandSource)context.getSource()).getPlayerOrThrow(),
                           "qouteall.imm_ptl.core.commands.PortalCommand.RemoteCallables.clientAccelerate",
                           vec
                        );
                        return 0;
                     }
                  )
            )
      );
      builder.then(
         ((LiteralArgumentBuilder)CommandManager.literal("accelerate_along").requires(PortalCommand::canUsePortalCommand))
            .then(
               CommandManager.argument("vec", Vec3ArgumentType.vec3(false))
                  .executes(
                     context -> {
                        Vec3d vec = Vec3ArgumentType.getVec3(context, "vec");
                        McRemoteProcedureCall.tellClientToInvoke(
                           ((ServerCommandSource)context.getSource()).getPlayerOrThrow(),
                           "qouteall.imm_ptl.core.commands.PortalCommand.RemoteCallables.clientAccelerate",
                           vec.multiply(0.05)
                        );
                        return 0;
                     }
                  )
            )
      );
      builder.then(CommandManager.literal("test").executes(context -> 0));
      builder.then(
         ((LiteralArgumentBuilder)CommandManager.literal("erase_chunk")
               .requires(serverCommandSource -> serverCommandSource.getPermissions().hasPermission(DefaultPermissions.ADMINS)))
            .then(
               ((RequiredArgumentBuilder)CommandManager.argument("rChunks", IntegerArgumentType.integer())
                     .executes(
                        context -> {
                           ServerPlayerEntity player = ((ServerCommandSource)context.getSource()).getPlayerOrThrow();
                           ChunkPos center = new ChunkPos(BlockPos.ofFloored(player.getEntityPos()));
                           invokeEraseChunk(
                              player.getEntityWorld(),
                              center,
                              IntegerArgumentType.getInteger(context, "rChunks"),
                              McHelper.getMinY(player.getEntityWorld()),
                              McHelper.getMaxYExclusive(player.getEntityWorld())
                           );
                           return 0;
                        }
                     ))
                  .then(
                     CommandManager.argument("downY", IntegerArgumentType.integer())
                        .then(
                           CommandManager.argument("upY", IntegerArgumentType.integer())
                              .executes(
                                 context -> {
                                    ServerPlayerEntity player = ((ServerCommandSource)context.getSource()).getPlayerOrThrow();
                                    ChunkPos center = new ChunkPos(BlockPos.ofFloored(player.getEntityPos()));
                                    invokeEraseChunk(
                                       player.getEntityWorld(),
                                       center,
                                       IntegerArgumentType.getInteger(context, "rChunks"),
                                       IntegerArgumentType.getInteger(context, "downY"),
                                       IntegerArgumentType.getInteger(context, "upY")
                                    );
                                    return 0;
                                 }
                              )
                        )
                  )
            )
      );
      builder.then(
         ((LiteralArgumentBuilder)CommandManager.literal("report_chunk_loaders")
               .requires(serverCommandSource -> serverCommandSource.getPermissions().hasPermission(DefaultPermissions.ADMINS)))
            .executes(context -> {
               ServerPlayerEntity player = ((ServerCommandSource)context.getSource()).getPlayerOrThrow();
               ChunkVisibility.foreachBaseChunkLoaders(player, loader -> McHelper.serverLog(player, loader.toString()));
               return 0;
            })
      );
      builder.then(
         ((LiteralArgumentBuilder)CommandManager.literal("report_server_entities_nearby")
               .requires(serverCommandSource -> serverCommandSource.getPermissions().hasPermission(DefaultPermissions.ADMINS)))
            .executes(
               context -> {
                  ServerPlayerEntity player = ((ServerCommandSource)context.getSource()).getPlayerOrThrow();
                  List<Entity> entities = player.getEntityWorld()
                     .getEntitiesByClass(Entity.class, new Box(player.getEntityPos(), player.getEntityPos()).expand(32.0), e -> true);
                  McHelper.serverLog(player, entities.stream().<CharSequence>map(Entity::toString).collect(Collectors.joining("\n")));
                  return 0;
               }
            )
      );
      builder.then(
         ((LiteralArgumentBuilder)CommandManager.literal("report_loaded_portals")
               .requires(serverCommandSource -> serverCommandSource.getPermissions().hasPermission(DefaultPermissions.ADMINS)))
            .executes(context -> {
               ServerCommandSource source = (ServerCommandSource)context.getSource();
               MinecraftServer server = source.getServer();

               for (ServerWorld world : server.getWorlds()) {
                  for (Entity entity : world.iterateEntities()) {
                     if (entity instanceof Portal portal) {
                        source.sendFeedback(() -> Text.literal(entity.toString()), true);
                     }
                  }
               }

               return 0;
            })
      );
      builder.then(
         ((LiteralArgumentBuilder)CommandManager.literal("is_chunk_loaded")
               .requires(serverCommandSource -> serverCommandSource.getPermissions().hasPermission(DefaultPermissions.GAMEMASTERS)))
            .then(
               CommandManager.argument("dim", DimensionArgumentType.dimension())
                  .then(
                     CommandManager.argument("chunkX", IntegerArgumentType.integer())
                        .then(CommandManager.argument("chunkZ", IntegerArgumentType.integer()).executes(context -> {
                           int chunkX = IntegerArgumentType.getInteger(context, "chunkX");
                           int chunkZ = IntegerArgumentType.getInteger(context, "chunkZ");
                           ServerWorld world = DimensionArgumentType.getDimensionArgument(context, "dim");
                           ServerPlayerEntity player = ((ServerCommandSource)context.getSource()).getPlayerOrThrow();
                           doReportChunkStatus(chunkX, chunkZ, world, player);
                           return 0;
                        }))
                  )
            )
      );
      builder.then(
         ((LiteralArgumentBuilder)CommandManager.literal("report_chunk_at")
               .requires(serverCommandSource -> serverCommandSource.getPermissions().hasPermission(DefaultPermissions.GAMEMASTERS)))
            .then(
               CommandManager.argument("dim", DimensionArgumentType.dimension())
                  .then(CommandManager.argument("pos", BlockPosArgumentType.blockPos()).executes(context -> {
                     ServerWorld world = DimensionArgumentType.getDimensionArgument(context, "dim");
                     ServerPlayerEntity player = ((ServerCommandSource)context.getSource()).getPlayerOrThrow();
                     BlockPos pos = BlockPosArgumentType.getBlockPos(context, "pos");
                     ChunkPos chunkPos = new ChunkPos(pos);
                     doReportChunkStatus(chunkPos.x, chunkPos.z, world, player);
                     return 0;
                  }))
            )
      );
      builder.then(
         CommandManager.literal("report_player_status")
            .executes(
               context -> {
                  ServerPlayerEntity player = ((ServerCommandSource)context.getSource()).getPlayerOrThrow();
                  CHelper.printChat(
                     String.format(
                        "On Server %s %s removal:%s added:%s age:%s",
                        player.getEntityWorld().getRegistryKey().getValue(),
                        player.getBlockPos(),
                        player.getRemovalReason(),
                        player.getEntityWorld().getEntityById(player.getId()) != null,
                        player.age
                     )
                  );
                  McRemoteProcedureCall.tellClientToInvoke(player, "qouteall.imm_ptl.core.commands.ClientDebugCommand.RemoteCallables.reportClientPlayerStatus");
                  return 0;
               }
            )
      );
      builder.then(
         ((LiteralArgumentBuilder)CommandManager.literal("list_portals")
               .requires(serverCommandSource -> serverCommandSource.getPermissions().hasPermission(DefaultPermissions.ADMINS)))
            .executes(context -> {
               ServerPlayerEntity player = ((ServerCommandSource)context.getSource()).getPlayerOrThrow();
               StringBuilder result = new StringBuilder();
               result.append("Server Portals\n");

               for (ServerWorld world : MiscHelper.getServer().getWorlds()) {
                  result.append(world.getRegistryKey().getValue().toString() + "\n");

                  for (Entity entity : world.iterateEntities()) {
                     for (Entity e : world.iterateEntities()) {
                        if (e instanceof Portal) {
                           result.append(e.toString());
                           result.append("\n");
                        }
                     }
                  }
               }

               McHelper.serverLog(player, result.toString());
               McRemoteProcedureCall.tellClientToInvoke(player, "qouteall.imm_ptl.core.commands.ClientDebugCommand.RemoteCallables.doListPortals");
               return 0;
            })
      );
      builder.then(
         ((LiteralArgumentBuilder)CommandManager.literal("report_resource_consumption")
               .requires(serverCommandSource -> serverCommandSource.getPermissions().hasPermission(DefaultPermissions.GAMEMASTERS)))
            .executes(
               context -> {
                  StringBuilder str = new StringBuilder();
                  str.append("Server Tracked Chunks:\n");
                  MiscHelper.getServer().getWorlds().forEach(world -> str.append(getServerWorldResourceConsumption(world)));
                  McHelper.serverLog(((ServerCommandSource)context.getSource()).getPlayerOrThrow(), str.toString());
                  McRemoteProcedureCall.tellClientToInvoke(
                     ((ServerCommandSource)context.getSource()).getPlayerOrThrow(),
                     "qouteall.imm_ptl.core.commands.ClientDebugCommand.RemoteCallables.reportResourceConsumption"
                  );
                  return 0;
               }
            )
      );
      builder.then(
         ((LiteralArgumentBuilder)CommandManager.literal("report_chunk_ticket_stat")
               .requires(serverCommandSource -> serverCommandSource.getPermissions().hasPermission(DefaultPermissions.GAMEMASTERS)))
            .executes(
               context -> {
                  ServerWorld world = ((ServerCommandSource)context.getSource()).getWorld();
                  Iterable<ChunkHolder> chunkHolders = ((IEChunkMap_Accessor)world.getChunkManager().chunkLoadingManager).ip_getVisibleChunkMap().values();
                  Object2IntOpenHashMap<ChunkTicketType> stat = new Object2IntOpenHashMap();

                  for (ChunkHolder chunkHolder : chunkHolders) {
                     long chunkPos = chunkHolder.getPos().toLong();

                     for (ChunkTicket ticket : ((IEDistanceManager)ImmPtlChunkTickets.getDistanceManager(world)).portal_getTicketSet(chunkPos)) {
                        stat.addTo(ticket.getType(), 1);
                     }
                  }

                  ((ServerCommandSource)context.getSource()).sendFeedback(() -> Text.literal(""), false);
                  ObjectIterator var11 = stat.object2IntEntrySet().iterator();

                  while (var11.hasNext()) {
                     it.unimi.dsi.fastutil.objects.Object2IntMap.Entry<ChunkTicketType> entry = (it.unimi.dsi.fastutil.objects.Object2IntMap.Entry<ChunkTicketType>)var11.next();
                     ChunkTicketType ticketType = (ChunkTicketType)entry.getKey();
                     ((ServerCommandSource)context.getSource())
                        .sendFeedback(() -> Text.literal(getTicketTypeName(ticketType) + " " + entry.getIntValue()), false);
                  }

                  return 0;
               }
            )
      );
      builder.then(
         ((LiteralArgumentBuilder)CommandManager.literal("report_per_player_chunk_loading")
               .requires(serverCommandSource -> serverCommandSource.getPermissions().hasPermission(DefaultPermissions.GAMEMASTERS)))
            .executes(context -> {
               for (ServerPlayerEntity player : MiscHelper.getServer().getPlayerManager().getPlayerList()) {
                  PlayerChunkLoading playerInfo = ImmPtlChunkTracking.getPlayerInfo(player);
                  String text = "%s %d".formatted(player.getName().getString(), playerInfo.loadedChunks);
                  ((ServerCommandSource)context.getSource()).sendFeedback(() -> Text.literal(text), true);
               }

               return 0;
            })
      );
      builder.then(
         ((LiteralArgumentBuilder)CommandManager.literal("save_all_chunks")
               .requires(serverCommandSource -> serverCommandSource.getPermissions().hasPermission(DefaultPermissions.GAMEMASTERS)))
            .executes(context -> {
               MinecraftServer server = ((ServerCommandSource)context.getSource()).getServer();
               server.save(true, true, false);
               return 0;
            })
      );
      builder.then(
         ((LiteralArgumentBuilder)CommandManager.literal("simplify_portal_mesh")
               .requires(serverCommandSource -> serverCommandSource.getPermissions().hasPermission(DefaultPermissions.GAMEMASTERS)))
            .executes(context -> PortalCommand.processPortalTargetedCommand(context, portal -> {
               if (portal.getPortalShape() instanceof SpecialFlatPortalShape s) {
                  s.mesh.simplifySteps(1);
                  portal.reloadPortal();
               }
            }))
      );
      builder.then(
         ((LiteralArgumentBuilder)CommandManager.literal("check_biome_registry")
               .requires(serverCommandSource -> serverCommandSource.getPermissions().hasPermission(DefaultPermissions.ADMINS)))
            .executes(
               context -> {
                  Immutable registryAccess = MiscHelper.getServer().getRegistryManager();
                  Registry<Biome> biomes = registryAccess.getOrThrow(RegistryKeys.BIOME);
                  Map<String, Integer> map = new HashMap<>();

                  for (Entry<RegistryKey<Biome>, Biome> entry : biomes.getEntrySet()) {
                     String strId = entry.getKey().getValue().toString();
                     int intId = biomes.getRawId(entry.getValue());
                     map.put(strId, intId);
                  }

                  McRemoteProcedureCall.tellClientToInvoke(
                     ((ServerCommandSource)context.getSource()).getPlayerOrThrow(),
                     "qouteall.imm_ptl.core.ClientWorldLoader.RemoteCallables.checkBiomeRegistry",
                     map
                  );
                  return 0;
               }
            )
      );
      builder.then(
         CommandManager.literal("nofog_enable")
            .executes(
               context -> {
                  McRemoteProcedureCall.tellClientToInvoke(
                     ((ServerCommandSource)context.getSource()).getPlayerOrThrow(),
                     "qouteall.imm_ptl.core.commands.ClientDebugCommand.RemoteCallables.setNoFog",
                     true
                  );
                  return 0;
               }
            )
      );
      builder.then(
         CommandManager.literal("nofog_disable")
            .executes(
               context -> {
                  McRemoteProcedureCall.tellClientToInvoke(
                     ((ServerCommandSource)context.getSource()).getPlayerOrThrow(),
                     "qouteall.imm_ptl.core.commands.ClientDebugCommand.RemoteCallables.setNoFog",
                     false
                  );
                  return 0;
               }
            )
      );
      builder.then(CommandManager.literal("report_air").executes(context -> {
         ServerPlayerEntity player = ((ServerCommandSource)context.getSource()).getPlayerOrThrow();
         BlockState blockState = player.getEntityWorld().getBlockState(player.getBlockPos());
         ((ServerCommandSource)context.getSource()).sendFeedback(() -> blockState.getBlock().getName(), false);
         return 0;
      }));
      builder.then(
         CommandManager.literal("test_invalid_rpc")
            .executes(
               context -> {
                  McRemoteProcedureCall.tellClientToInvoke(
                     ((ServerCommandSource)context.getSource()).getPlayerOrThrow(),
                     "qouteall.imm_ptl.core.commands.ClientDebugCommand.RemoteCallables.testInvalidRPC"
                  );
                  return 0;
               }
            )
      );
   }

   private static void doReportChunkStatus(int chunkX, int chunkZ, ServerWorld world, ServerPlayerEntity player) {
      WorldChunk chunk = McHelper.getServerChunkIfPresent(world, chunkX, chunkZ);
      boolean loaded = chunk != null && !(chunk instanceof EmptyChunk);
      long longChunkPos = ChunkPos.toLong(chunkX, chunkZ);
      if (loaded) {
         boolean shouldTickEntities = ImmPtlChunkTickets.getDistanceManager(world).shouldTickEntities(longChunkPos);
         if (shouldTickEntities) {
            McHelper.serverLog(player, "Server chunk loaded and entity tickable");
         } else {
            McHelper.serverLog(player, "Server chunk loaded but entity not tickable");
         }
      } else {
         McHelper.serverLog(player, "Server chunk not loaded");
      }

      ChunkHolder chunkHolder = McHelper.getIEChunkMap(world.getRegistryKey()).ip_getChunkHolder(longChunkPos);
      if (chunkHolder == null) {
         McHelper.serverLog(player, "no chunk holder");
      } else {
         McHelper.serverLog(player, String.format("chunk holder level:%s %s", chunkHolder.getLevel(), chunk == null ? "" : chunk.getLevelType()));
         ChunkLevelManager distanceManager = ((IEServerChunkCache)world.getChunkManager()).ip_getDistanceManager();

         for (ChunkTicket ticket : ((IEDistanceManager)distanceManager).portal_getTicketSet(longChunkPos)) {
            McHelper.serverLog(player, getTicketTypeName(ticket.getType()) + " " + ticket);
         }
      }

      McRemoteProcedureCall.tellClientToInvoke(
         player, "qouteall.imm_ptl.core.commands.ClientDebugCommand.RemoteCallables.reportClientChunkLoadStatus", world.getRegistryKey(), chunkX, chunkZ
      );
   }

   public static String getServerWorldResourceConsumption(ServerWorld world) {
      StringBuilder subStr = new StringBuilder();
      ImmPtlChunkTickets dimTicketManager = ImmPtlChunkTickets.get(world);
      EntityLookup<Entity> entityLookup = ((IEWorld)world).portal_getEntityLookup();
      subStr.append(
         String.format(
            "%s:\nImmPtl Tracked Chunks: %s\nImmPtl Loading Ticket:%s\nChunks: %s\nEntities:%s Entity Sections:%s\n",
            world.getRegistryKey().getValue(),
            ImmPtlChunkTracking.getLoadedChunkNum(world.getRegistryKey()),
            dimTicketManager.getLoadedChunkNum(),
            world.getChunkManager().chunkLoadingManager.getLoadedChunkCount(),
            ((IELevelEntityGetterAdapter)entityLookup).getIndex().size(),
            ((IELevelEntityGetterAdapter)entityLookup).getCache().sectionCount()
         )
      );
      ServerEntityManager<Entity> entityManager = ((IEServerWorld)world).ip_getEntityManager();
      entityManager.flush();
      subStr.append(String.format("Entity Manager: %s\n", entityManager.getDebugString()));
      subStr.append("\n");
      return subStr.toString();
   }

   public static long toMiB(long bytes) {
      return bytes / 1024L / 1024L;
   }

   public static void invokeEraseChunk(World world, ChunkPos center, int r, int downY, int upY) {
      ArrayList<ChunkPos> poses = new ArrayList<>();

      for (int x = -r; x <= r; x++) {
         for (int z = -r; z <= r; z++) {
            poses.add(new ChunkPos(x + center.x, z + center.z));
         }
      }

      poses.sort(Comparator.comparingDouble(c -> Vec3d.of(center.getStartPos()).distanceTo(Vec3d.of(c.getStartPos()))));
      ServerTaskList.of(world.getServer()).addTask(MyTaskList.chainTasks(poses.stream().map(chunkPos -> () -> {
         eraseChunk(chunkPos, world, downY, upY);
         return true;
      }).iterator()));
   }

   public static void eraseChunk(ChunkPos chunkPos, World world, int yStart, int yEnd) {
      for (int x = 0; x < 16; x++) {
         for (int z = 0; z < 16; z++) {
            for (int y = yStart; y < yEnd; y++) {
               world.setBlockState(new BlockPos(chunkPos.getStartX() + x, y, chunkPos.getStartZ() + z), Blocks.AIR.getDefaultState());
            }
         }
      }
   }
}
