package qouteall.imm_ptl.core.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Pair;
import java.lang.ref.WeakReference;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.NetherPortalBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtInt;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerLightingProvider;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Direction.Axis;
import net.minecraft.world.EntityList;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkNibbleArray;
import net.minecraft.world.chunk.EmptyChunk;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.IPCGlobal;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.ducks.IEClientWorld;
import qouteall.imm_ptl.core.ducks.IEEntity;
import qouteall.imm_ptl.core.ducks.IEWorldRenderer;
import qouteall.imm_ptl.core.platform_specific.IPConfig;
import qouteall.imm_ptl.core.platform_specific.IPConfigGUI;
import qouteall.imm_ptl.core.platform_specific.O_O;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.render.ForceMainThreadRebuild;
import qouteall.imm_ptl.core.render.ImmPtlViewArea;
import qouteall.imm_ptl.core.render.MyGameRenderer;
import qouteall.imm_ptl.core.render.context_management.RenderStates;
import qouteall.imm_ptl.core.teleportation.ClientTeleportationManager;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.MiscHelper;
import qouteall.q_misc_util.api.McRemoteProcedureCall;
import qouteall.q_misc_util.my_util.MyTaskList;
import qouteall.q_misc_util.platform.ClientPlatform;

@Environment(EnvType.CLIENT)
public class ClientDebugCommand {
   public static <S> void register(CommandDispatcher<S> dispatcher, ClientPlatform.ClientCommandSupport<S> support) {
      LiteralArgumentBuilder<S> builder = (LiteralArgumentBuilder<S>)((LiteralArgumentBuilder)LiteralArgumentBuilder.<S>literal("imm_ptl_client_debug")
            .requires(commandSource -> true))
         .then(
            LiteralArgumentBuilder.<S>literal("set_max_portal_layer")
               .then(
                  RequiredArgumentBuilder.<S, Integer>argument("argMaxPortalLayer", IntegerArgumentType.integer())
                     .executes(context -> setMaxPortalLayer(IntegerArgumentType.getInteger(context, "argMaxPortalLayer")))
               )
         );
      builder = (LiteralArgumentBuilder<S>)builder.then(LiteralArgumentBuilder.<S>literal("list_portals").executes(context -> {
         ClientDebugCommand.RemoteCallables.doListPortals();
         return 0;
      }));
      builder = (LiteralArgumentBuilder<S>)builder.then(
         LiteralArgumentBuilder.<S>literal("is_client_chunk_loaded")
            .then(
               RequiredArgumentBuilder.<S, Integer>argument("chunkX", IntegerArgumentType.integer())
                  .then(RequiredArgumentBuilder.<S, Integer>argument("chunkZ", IntegerArgumentType.integer()).executes(ClientDebugCommand::isClientChunkLoaded))
            )
      );
      builder = (LiteralArgumentBuilder<S>)builder.then(LiteralArgumentBuilder.<S>literal("report_player_status").executes(context -> {
         ClientDebugCommand.RemoteCallables.reportClientPlayerStatus();
         return 0;
      }));
      builder = (LiteralArgumentBuilder<S>)builder.then(LiteralArgumentBuilder.<S>literal("client_remote_ticking_enable").executes(context -> {
         IPCGlobal.isClientRemoteTickingEnabled = true;
         return 0;
      }));
      builder = (LiteralArgumentBuilder<S>)builder.then(LiteralArgumentBuilder.<S>literal("client_remote_ticking_disable").executes(context -> {
         IPCGlobal.isClientRemoteTickingEnabled = false;
         return 0;
      }));
      builder = (LiteralArgumentBuilder<S>)builder.then(LiteralArgumentBuilder.<S>literal("advanced_frustum_culling_enable").executes(context -> {
         IPCGlobal.doUseAdvancedFrustumCulling = true;
         return 0;
      }));
      builder = (LiteralArgumentBuilder<S>)builder.then(LiteralArgumentBuilder.<S>literal("advanced_frustum_culling_disable").executes(context -> {
         IPCGlobal.doUseAdvancedFrustumCulling = false;
         return 0;
      }));
      builder = (LiteralArgumentBuilder<S>)builder.then(LiteralArgumentBuilder.<S>literal("report_resource_consumption").executes(context1 -> {
         ClientDebugCommand.RemoteCallables.reportResourceConsumption();
         return 0;
      }));
      builder = (LiteralArgumentBuilder<S>)builder.then(LiteralArgumentBuilder.<S>literal("get_player_colliding_portal_client").executes(context -> {
         Portal collidingPortal = ((IEEntity)(Object)MinecraftClient.getInstance().player).ip_getCollidingPortal();
         CHelper.printChat(collidingPortal != null ? collidingPortal.toString() : "null");
         return 0;
      }));
      builder = (LiteralArgumentBuilder<S>)builder.then(LiteralArgumentBuilder.<S>literal("report_rendering").executes(context -> {
         StringBuilder sb = new StringBuilder();

         for (List<WeakReference<Portal>> rendering : RenderStates.lastPortalRenderInfos) {
            sb.append("----------\n");

            for (WeakReference<Portal> portalLikeWeakReference : rendering) {
               sb.append(portalLikeWeakReference.get().toString());
               sb.append("\n");
            }
         }

         CHelper.printChat(sb.toString());
         return 0;
      }));
      builder.then(LiteralArgumentBuilder.<S>literal("report_loaded_portals").executes(context -> {
         for (ClientWorld world : ClientWorldLoader.getClientWorlds()) {
            EntityList entityList = ((IEClientWorld)(Object)world).ip_getEntityList();
            entityList.forEach(e -> {
               if (e instanceof Portal portal) {
                  CHelper.printChat(portal.toString());
               }
            });
         }

         return 0;
      }));
      builder = (LiteralArgumentBuilder<S>)builder.then(LiteralArgumentBuilder.<S>literal("vanilla_chunk_culling_enable").executes(context -> {
         MinecraftClient.getInstance().chunkCullingEnabled = true;
         return 0;
      }));
      builder = (LiteralArgumentBuilder<S>)builder.then(LiteralArgumentBuilder.<S>literal("vanilla_chunk_culling_disable").executes(context -> {
         MinecraftClient.getInstance().chunkCullingEnabled = false;
         return 0;
      }));
      builder = (LiteralArgumentBuilder<S>)builder.then(LiteralArgumentBuilder.<S>literal("render_mode_normal").executes(context -> {
         IPGlobal.renderMode = IPGlobal.RenderMode.normal;
         return 0;
      }));
      builder = (LiteralArgumentBuilder<S>)builder.then(LiteralArgumentBuilder.<S>literal("render_mode_compatibility").executes(context -> {
         IPGlobal.renderMode = IPGlobal.RenderMode.compatibility;
         return 0;
      }));
      builder = (LiteralArgumentBuilder<S>)builder.then(LiteralArgumentBuilder.<S>literal("render_mode_debug").executes(context -> {
         IPGlobal.renderMode = IPGlobal.RenderMode.debug;
         return 0;
      }));
      builder = (LiteralArgumentBuilder<S>)builder.then(LiteralArgumentBuilder.<S>literal("render_mode_none").executes(context -> {
         IPGlobal.renderMode = IPGlobal.RenderMode.none;
         return 0;
      }));
      builder.then(
         LiteralArgumentBuilder.<S>literal("check_client_light")
            .executes(
               context -> {
                  MinecraftClient client = MinecraftClient.getInstance();
                  client.execute(
                     () -> client.world
                        .getChunkManager()
                        .getLightingProvider()
                        .setSectionStatus(ChunkSectionPos.from(BlockPos.ofFloored(client.player.getEntityPos())), false)
                  );
                  return 0;
               }
            )
      );
      builder.then(LiteralArgumentBuilder.<S>literal("report_client_entities").executes(context -> {
         ClientWorld world = MinecraftClient.getInstance().world;
         CHelper.printChat("client entity manager:");

         for (Entity entity : world.getEntities()) {
            CHelper.printChat(entity.toString());
         }

         CHelper.printChat("client entity list:");
         EntityList entityList = ((IEClientWorld)(Object)world).ip_getEntityList();
         entityList.forEach(e -> CHelper.printChat(e.toString()));
         return 0;
      }));
      builder.then(
         LiteralArgumentBuilder.<S>literal("check_server_light")
            .executes(
               context -> {
                  MinecraftServer server = MiscHelper.getServer();
                  server.execute(
                     () -> {
                        ServerPlayerEntity player = (ServerPlayerEntity)server.getPlayerManager().getPlayerList().get(0);
                        BlockPos.stream(player.getBlockPos().add(-2, -2, -2), player.getBlockPos().add(2, 2, 2))
                           .forEach(blockPos -> player.getEntityWorld().getLightingProvider().checkBlock(blockPos));
                     }
                  );
                  return 0;
               }
            )
      );
      builder.then(LiteralArgumentBuilder.<S>literal("update_server_light").executes(context -> {
         MinecraftServer server = MiscHelper.getServer();
         server.execute(() -> {
            ServerPlayerEntity player = (ServerPlayerEntity)server.getPlayerManager().getPlayerList().get(0);
            ServerLightingProvider lightingProvider = (ServerLightingProvider)player.getEntityWorld().getLightingProvider();
            lightingProvider.light(player.getEntityWorld().getChunk(player.getBlockPos()), false);
         });
         return 0;
      }));
      builder.then(
         LiteralArgumentBuilder.<S>literal("report_rebuild_status")
            .executes(
               context -> {
                  MinecraftClient.getInstance()
                     .execute(
                        () -> ClientWorldLoader.getClientWorlds()
                           .forEach(
                              world -> {
                                 ImmPtlViewArea builtChunkStorage = (ImmPtlViewArea)((IEWorldRenderer)(Object)ClientWorldLoader.getWorldRenderer(world.getRegistryKey()))
                                    .ip_getBuiltChunkStorage();
                                 CHelper.printChat(world.getRegistryKey().getValue().toString() + builtChunkStorage.getDebugString());
                              }
                           )
                     );
                  return 0;
               }
            )
      );
      builder.then(
         LiteralArgumentBuilder.<S>literal("report_client_light_status")
            .executes(
               context -> {
                  MinecraftClient.getInstance()
                     .execute(
                        () -> {
                           ClientPlayerEntity player = MinecraftClient.getInstance().player;
                           ChunkNibbleArray lightSection = player.getEntityWorld()
                              .getLightingProvider()
                              .get(LightType.BLOCK)
                              .getLightSection(ChunkSectionPos.from(player.getBlockPos()));
                           if (lightSection != null) {
                              boolean uninitialized = lightSection.isUninitialized();
                              byte[] byteArray = lightSection.asByteArray();
                              boolean allZero = true;

                              for (byte b : byteArray) {
                                 if (b != 0) {
                                    allZero = false;
                                    break;
                                 }
                              }

                              support.sendFeedback(
                                 (S)context.getSource(),
                                 Text.literal("has light section " + (allZero ? "all zero" : "not all zero") + (uninitialized ? " uninitialized" : " fine"))
                              );
                           } else {
                              support.sendFeedback((S)context.getSource(), Text.literal("does not have light section"));
                           }
                        }
                     );
                  return 0;
               }
            )
      );
      builder.then(LiteralArgumentBuilder.<S>literal("reload_world_renderer").executes(context -> {
         MinecraftClient.getInstance().execute(() -> {
            ClientWorldLoader.disposeRenderHelpers();
            MinecraftClient.getInstance().worldRenderer.reload();
         });
         return 0;
      }));
      builder.then(LiteralArgumentBuilder.<S>literal("config").executes(context -> {
         MinecraftClient client = MinecraftClient.getInstance();
         IPGlobal.CLIENT_TASK_LIST.addTask(MyTaskList.oneShotTask(() -> client.setScreen(IPConfigGUI.createClothConfigScreen(null))));
         return 0;
      }));
      builder.then(LiteralArgumentBuilder.<S>literal("disable_warning").executes(context -> {
         disableWarning();
         support.sendFeedback((S)context.getSource(), Text.translatable("imm_ptl.warning_disabled"));
         return 0;
      }));
      builder.then(
         LiteralArgumentBuilder.<S>literal("disable_warning_for")
            .then(RequiredArgumentBuilder.<S, String>argument("warningKey", StringArgumentType.string()).executes(context -> {
               disableWarningFor(StringArgumentType.getString(context, "warningKey"));
               support.sendFeedback((S)context.getSource(), Text.translatable("imm_ptl.warning_disabled"));
               return 0;
            }))
      );
      builder.then(LiteralArgumentBuilder.<S>literal("disable_update_check").executes(context -> {
         disableUpdateCheck();
         support.sendFeedback((S)context.getSource(), Text.translatable("imm_ptl.update_check_disabled"));
         return 0;
      }));
      builder.then(LiteralArgumentBuilder.<S>literal("view_portal_data").executes(context -> {
         MinecraftClient client = MinecraftClient.getInstance();
         Pair<Portal, Vec3d> pair = PortalCommand.getPlayerPointingPortalRaw(client.player, 0.0F, 50.0, true).orElse(null);
         if (pair != null) {
            Portal portal = (Portal)pair.getFirst();
            PortalCommand.sendPortalInfo((Consumer<Text>)(c -> {
               support.sendFeedback((S)context.getSource(), c);
               Helper.log(c.getString());
            }), portal);
         } else {
            support.sendFeedback((S)context.getSource(), Text.literal("No pointing to a portal."));
         }

         return 0;
      }));
      if (O_O.isDevEnv()) {
         builder.then(
            LiteralArgumentBuilder.<S>literal("tp_on_client_side")
               .then(
                  RequiredArgumentBuilder.<S, String>argument("dim_id", StringArgumentType.string())
                     .then(
                        RequiredArgumentBuilder.<S, Double>argument("x", DoubleArgumentType.doubleArg())
                           .then(
                              RequiredArgumentBuilder.<S, Double>argument("y", DoubleArgumentType.doubleArg())
                                 .then(RequiredArgumentBuilder.<S, Double>argument("z", DoubleArgumentType.doubleArg()).executes(context -> {
                                    String dimId = StringArgumentType.getString(context, "dim_id");
                                    double x = DoubleArgumentType.getDouble(context, "x");
                                    double y = DoubleArgumentType.getDouble(context, "y");
                                    double z = DoubleArgumentType.getDouble(context, "z");
                                    RegistryKey<World> dimKey = RegistryKey.of(RegistryKeys.WORLD, McHelper.newResourceLocation(dimId));
                                    Vec3d pos = new Vec3d(x, y, z);
                                    ClientTeleportationManager.forceTeleportPlayer(dimKey, pos);
                                    return 0;
                                 }))
                           )
                     )
               )
         );
      }

      builder.then(LiteralArgumentBuilder.<S>literal("show_mod_id_list").executes(context -> {
         List<String> loadedModIds = O_O.getLoadedModIds();
         String str = String.join("\n", loadedModIds);
         support.sendFeedback((S)context.getSource(), Text.literal(str));
         return 0;
      }));
      builder.then(
         LiteralArgumentBuilder.<S>literal("force_main_thread_chunk_rebuild")
            .then(RequiredArgumentBuilder.<S, Integer>argument("frameCount", IntegerArgumentType.integer(0, 10000)).executes(context -> {
               int frameCount = IntegerArgumentType.getInteger(context, "frameCount");
               ForceMainThreadRebuild.forceMainThreadRebuildFor(frameCount);
               return 0;
            }))
      );
      LiteralArgumentBuilder<S> wandBuilder = LiteralArgumentBuilder.<S>literal("wand");
      registerPortalWandCommands(wandBuilder, support);
      builder.then(wandBuilder);
      registerSwitchCommand(builder, "front_clipping", cond -> IPCGlobal.useFrontClipping = cond);
      registerSwitchCommand(builder, "gl_check_error", cond -> IPGlobal.doCheckGlError = cond);
      registerSwitchCommand(builder, "early_light_update", cond -> IPCGlobal.lateClientLightUpdate = cond);
      registerSwitchCommand(builder, "early_remote_upload", cond -> IPCGlobal.earlyRemoteUpload = cond);
      registerSwitchCommand(builder, "super_advanced_frustum_culling", cond -> IPCGlobal.useSuperAdvancedFrustumCulling = cond);
      registerSwitchCommand(builder, "cross_portal_entity_rendering", cond -> IPGlobal.correctCrossPortalEntityRendering = cond);
      registerSwitchCommand(builder, "early_cull_portal", cond -> IPCGlobal.earlyFrustumCullingPortal = cond);
      registerSwitchCommand(builder, "cache_gl_buffer", cond -> IPGlobal.cacheGlBuffer = cond);
      registerSwitchCommand(builder, "secondary_vertex_consumer", cond -> IPGlobal.useSecondaryEntityVertexConsumer = cond);
      registerSwitchCommand(builder, "cull_sections_behind", cond -> IPGlobal.cullSectionsBehind = cond);
      registerSwitchCommand(builder, "offset_occlusion_query", cond -> IPGlobal.offsetOcclusionQuery = cond);
      registerSwitchCommand(builder, "cloud_optimization", cond -> IPGlobal.cloudOptimization = cond);
      registerSwitchCommand(builder, "cross_portal_collision", cond -> IPGlobal.crossPortalCollision = cond);
      registerSwitchCommand(builder, "nofog", cond -> IPGlobal.debugDisableFog = cond);
      registerSwitchCommand(builder, "depth_clamp_for_portal_rendering", cond -> IPGlobal.enableDepthClampForPortalRendering = cond);
      registerSwitchCommand(builder, "shared_block_mesh_builder", cond -> IPGlobal.enableSharedBlockMeshBuffers = cond);
      registerSwitchCommand(builder, "entity_pos_interpolation", cond -> IPGlobal.allowClientEntityPosInterpolation = cond);
      registerSwitchCommand(builder, "always_override_terrain_setup", cond -> IPGlobal.alwaysOverrideTerrainSetup = cond);
      registerSwitchCommand(builder, "view_bob_reduce", cond -> IPGlobal.viewBobbingReduce = cond);
      registerSwitchCommand(builder, "iris_stencil", cond -> IPCGlobal.debugEnableStencilWithIris = cond);
      registerSwitchCommand(builder, "another_stencil", cond -> IPCGlobal.useSeparatedStencilFormat = cond);
      registerSwitchCommand(builder, "portal_rendering_cave_culling", cond -> MyGameRenderer.enablePortalCaveCulling = cond);
      registerSwitchCommand(builder, "log_client_player_colliding_portal_update", cond -> IPGlobal.logClientPlayerCollidingPortalUpdate = cond);
      registerSwitchCommand(builder, "entity_untrack_debug", cond -> IPGlobal.entityUntrackDebug = cond);
      registerSwitchCommand(builder, "entity_track_debug", cond -> IPGlobal.entityTrackDebug = cond);
      registerSwitchCommand(builder, "client_portal_load_debug", cond -> IPGlobal.clientPortalLoadDebug = cond);
      registerSwitchCommand(builder, "mesh_render", cond -> IPGlobal.debugRenderPortalShapeMesh = cond);
      registerSwitchCommand(builder, "teleportation_debug", cond -> IPGlobal.teleportationDebugEnabled = cond);
      registerSwitchCommand(builder, "debug_text_to_top", cond -> IPGlobal.moveDebugTextToTop = cond);
      registerSwitchCommand(builder, "box_portal_special_iteration", cond -> IPGlobal.boxPortalSpecialIteration = cond);
      builder.then(LiteralArgumentBuilder.<S>literal("print_class_path").executes(context -> {
         printClassPath();
         return 0;
      }));
      builder.then(LiteralArgumentBuilder.<S>literal("test_invalid_rpc").executes(context -> {
         McRemoteProcedureCall.tellServerToInvoke("aaa.bbb.WrongClassRemoteCallable.method");
         return 0;
      }));
      dispatcher.register(builder);
   }

   private static <S> void registerPortalWandCommands(LiteralArgumentBuilder<S> builder, ClientPlatform.ClientCommandSupport<S> support) {
      builder.then(
         LiteralArgumentBuilder.<S>literal("set_cursor_alignment")
            .then(RequiredArgumentBuilder.<S, Integer>argument("alignment", IntegerArgumentType.integer(0)).executes(context -> {
               int alignment = IntegerArgumentType.getInteger(context, "alignment");
               IPConfig config = IPConfig.getConfig();
               config.portalWandCursorAlignment = alignment;
               config.saveConfigFile();
               support.sendFeedback((S)context.getSource(), Text.translatable("imm_ptl.wand.alignment_updated"));
               return 0;
            }))
      );
   }

   private static void printClassPath() {
      System.out
         .println(
            Arrays.stream(((URLClassLoader)ClassLoader.getSystemClassLoader()).getURLs())
               .map(url -> "\"" + url.getFile().substring(1).replace("%20", " ") + "\"")
               .collect(Collectors.joining(",\n"))
         );
   }

   private static <S> void registerSwitchCommand(LiteralArgumentBuilder<S> builder, String name, Consumer<Boolean> setFunction) {
      builder = (LiteralArgumentBuilder<S>)builder.then(LiteralArgumentBuilder.<S>literal(name + "_enable").executes(context -> {
         setFunction.accept(true);
         return 0;
      }));
      builder = (LiteralArgumentBuilder<S>)builder.then(LiteralArgumentBuilder.<S>literal(name + "_disable").executes(context -> {
         setFunction.accept(false);
         return 0;
      }));
   }

   private static <S> int isClientChunkLoaded(CommandContext<S> context) throws CommandSyntaxException {
      int chunkX = IntegerArgumentType.getInteger(context, "chunkX");
      int chunkZ = IntegerArgumentType.getInteger(context, "chunkZ");
      ClientDebugCommand.RemoteCallables.reportClientChunkLoadStatus(MinecraftClient.getInstance().world.getRegistryKey(), chunkX, chunkZ);
      return 0;
   }

   private static int setMaxPortalLayer(int m) {
      IPGlobal.maxPortalLayer = m;
      return 0;
   }

   private static void testRemoteProcedureCall(ServerPlayerEntity player) {
      MinecraftClient.getInstance()
         .execute(
            () -> {
               NbtCompound compoundTag = new NbtCompound();
               compoundTag.put("test", NbtInt.of(7));
               McRemoteProcedureCall.tellServerToInvoke(
                  "qouteall.imm_ptl.core.commands.ClientDebugCommand.TestRemoteCallable.clientToServer",
                  new UUID(3L, 3L),
                  Blocks.ACACIA_PLANKS,
                  Blocks.NETHER_PORTAL.getDefaultState().with(NetherPortalBlock.AXIS, Axis.Z),
                  Items.COMPASS,
                  new ItemStack(Items.ACACIA_LOG, 2),
                  compoundTag,
                  Text.literal("test"),
                  new int[]{777, 765}
               );
            }
         );
      player.server
         .execute(
            () -> McRemoteProcedureCall.tellClientToInvoke(
               player,
               "qouteall.imm_ptl.core.commands.ClientDebugCommand.TestRemoteCallable.serverToClient",
               "string",
               2,
               3.5,
               McHelper.newResourceLocation("imm_ptl:oops"),
               World.NETHER,
               BiomeKeys.JUNGLE,
               new BlockPos(3, 5, 4),
               new Vec3d(7.0, 4.0, 1.0)
            )
         );
   }

   public static void disableWarning() {
      IPConfig ipConfig = IPConfig.getConfig();
      ipConfig.enableWarning = false;
      ipConfig.saveConfigFile();
   }

   public static void disableWarningFor(String warningKey) {
      IPConfig ipConfig = IPConfig.getConfig();
      ipConfig.disabledWarnings.add(warningKey);
      ipConfig.saveConfigFile();
   }

   public static void disableUpdateCheck() {
      IPConfig ipConfig = IPConfig.getConfig();
      ipConfig.enableUpdateNotification = false;
      ipConfig.saveConfigFile();
   }

   public static class RemoteCallables {
      public static void reportClientChunkLoadStatus(RegistryKey<World> dimension, int chunkX, int chunkZ) {
         ClientWorld world = ClientWorldLoader.getWorld(dimension);
         Chunk chunk = world.getChunk(chunkX, chunkZ);
         CHelper.printChat(chunk != null && !(chunk instanceof EmptyChunk) ? "client loaded" : "client not loaded");
      }

      public static void reportClientPlayerStatus() {
         ClientPlayerEntity playerSP = MinecraftClient.getInstance().player;
         CHelper.printChat(
            String.format(
               "On Client %s %s removal:%s added:%s age:%s",
               playerSP.getEntityWorld().getRegistryKey().getValue(),
               playerSP.getBlockPos(),
               playerSP.getRemovalReason(),
               playerSP.getEntityWorld().getEntityById(playerSP.getId()) != null,
               playerSP.age
            )
         );
      }

      public static void doListPortals() {
         StringBuilder result = new StringBuilder();
         result.append("Client Portals\n");
         ClientWorldLoader.getClientWorlds().forEach(world -> {
            result.append(world.getRegistryKey().getValue().toString() + "\n");

            for (Entity e : world.getEntities()) {
               if (e instanceof Portal) {
                  result.append(e.toString());
                  result.append("\n");
               }
            }
         });
         CHelper.printChat(result.toString());
      }

      public static void reportResourceConsumption() {
         StringBuilder str = new StringBuilder();
         str.append("Client Chunk:\n");
         ClientWorldLoader.getClientWorlds()
            .forEach(world -> str.append(String.format("%s %s\n", world.getRegistryKey().getValue(), world.getChunkManager().getLoadedChunkCount())));
         str.append("Chunk Mesh Sections:\n");
         ClientWorldLoader.WORLD_RENDERER_MAP
            .forEach(
               (dimension, worldRenderer) -> str.append(
                  String.format(
                     "%s %s\n", dimension.getValue(), ((ImmPtlViewArea)((IEWorldRenderer)(Object)worldRenderer).ip_getBuiltChunkStorage()).getManagedSectionNum()
                  )
               )
            );
         String result = str.toString();
         CHelper.printChat(result);
      }

      public static void setNoFog(boolean cond) {
         IPGlobal.debugDisableFog = cond;
      }
   }

   public static class TestRemoteCallable {
      public static void serverToClient(
         String str,
         int integer,
         double doubleNum,
         Identifier identifier,
         RegistryKey<World> dimension,
         RegistryKey<Biome> biomeKey,
         BlockPos blockPos,
         Vec3d vec3d
      ) {
         Helper.log(str + integer + doubleNum + identifier + dimension + biomeKey + blockPos + vec3d);
      }

      public static void clientToServer(
         ServerPlayerEntity player,
         UUID uuid,
         Block block,
         BlockState blockState,
         Item item,
         ItemStack itemStack,
         NbtCompound compoundTag,
         Text text,
         int[] intArray
      ) {
         Helper.log(player.getName().getString() + uuid + block + blockState + item + itemStack + compoundTag + text.getString() + Arrays.toString(intArray));
      }
   }
}
