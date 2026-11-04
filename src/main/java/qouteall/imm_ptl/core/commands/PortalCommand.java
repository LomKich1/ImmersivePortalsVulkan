package qouteall.imm_ptl.core.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.DefaultPermissions;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.command.argument.ColumnPosArgumentType;
import net.minecraft.command.argument.DimensionArgumentType;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.NbtCompoundArgumentType;
import net.minecraft.command.argument.RotationArgumentType;
import net.minecraft.command.argument.TextArgumentType;
import net.minecraft.command.argument.Vec3ArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.Entity.RemovalReason;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry.Reference;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ColumnPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec2f;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Direction.Axis;
import net.minecraft.util.math.Direction.AxisDirection;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.api.PortalAPI;
import qouteall.imm_ptl.core.mc_utils.ServerTaskList;
import qouteall.imm_ptl.core.portal.Mirror;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.PortalExtension;
import qouteall.imm_ptl.core.portal.PortalManipulation;
import qouteall.imm_ptl.core.portal.PortalState;
import qouteall.imm_ptl.core.portal.PortalUtils;
import qouteall.imm_ptl.core.portal.animation.UnilateralPortalState;
import qouteall.imm_ptl.core.portal.global_portals.BorderBarrierFiller;
import qouteall.imm_ptl.core.portal.global_portals.GlobalPortalStorage;
import qouteall.imm_ptl.core.portal.global_portals.VerticalConnectingPortal;
import qouteall.imm_ptl.core.portal.global_portals.WorldWrappingPortal;
import qouteall.imm_ptl.core.portal.nether_portal.BreakablePortalEntity;
import qouteall.imm_ptl.core.portal.nether_portal.NetherPortalMatcher;
import qouteall.imm_ptl.core.portal.shape.BoxPortalShape;
import qouteall.imm_ptl.core.portal.shape.PortalShape;
import qouteall.imm_ptl.core.portal.shape.SpecialFlatPortalShape;
import qouteall.imm_ptl.core.teleportation.ServerTeleportationManager;
import qouteall.imm_ptl.peripheral.dim_stack.DimStackManagement;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.MiscHelper;
import qouteall.q_misc_util.my_util.DQuaternion;
import qouteall.q_misc_util.my_util.GeometryUtil;
import qouteall.q_misc_util.my_util.IntBox;
import qouteall.q_misc_util.my_util.Mesh2D;
import qouteall.q_misc_util.my_util.MyTaskList;
import qouteall.q_misc_util.my_util.Plane;
import qouteall.q_misc_util.my_util.RayTraceResult;
import qouteall.q_misc_util.my_util.SignalBiArged;
import qouteall.q_misc_util.my_util.Vec2d;
import qouteall.q_misc_util.my_util.WithDim;

public class PortalCommand {
   public static final SignalBiArged<ServerPlayerEntity, String> createCommandStickCommandSignal = new SignalBiArged<>();
   private static final Logger LOGGER = LogUtils.getLogger();

   public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess ctx) {
      LiteralArgumentBuilder<ServerCommandSource> builder = (LiteralArgumentBuilder<ServerCommandSource>)CommandManager.literal("portal")
         .requires(PortalCommand::canUsePortalCommand);
      registerPortalTargetedCommands(builder, ctx);
      LiteralArgumentBuilder<ServerCommandSource> animation = CommandManager.literal("animation");
      PortalAnimationCommand.registerPortalAnimationCommands(animation);
      builder.then(animation);
      registerCBPortalCommands(builder);
      registerUtilityCommands(builder);
      LiteralArgumentBuilder<ServerCommandSource> global = (LiteralArgumentBuilder<ServerCommandSource>)CommandManager.literal("global")
         .requires(commandSource -> commandSource.getPermissions().hasPermission(DefaultPermissions.GAMEMASTERS));
      registerGlobalPortalCommands(global);
      builder.then(global);
      LiteralArgumentBuilder<ServerCommandSource> debugBuilder = (LiteralArgumentBuilder<ServerCommandSource>)CommandManager.literal("debug")
         .requires(PortalCommand::canUsePortalCommand);
      PortalDebugCommands.registerDebugCommands(debugBuilder);
      builder.then(debugBuilder);
      LiteralArgumentBuilder<ServerCommandSource> euler = CommandManager.literal("euler");
      registerEulerCommands(euler);
      builder.then(euler);
      dispatcher.register(builder);
   }

   public static boolean canUsePortalCommand(ServerCommandSource commandSource) {
      Entity entity = commandSource.getEntity();
      return entity instanceof ServerPlayerEntity && IPGlobal.easeCreativePermission && ((ServerPlayerEntity)entity).isCreative()
         ? true
         : commandSource.getPermissions().hasPermission(DefaultPermissions.GAMEMASTERS);
   }

   private static void registerGlobalPortalCommands(LiteralArgumentBuilder<ServerCommandSource> builder) {
      builder.then(
         CommandManager.literal("create_inward_wrapping")
            .then(
               CommandManager.argument("p1", ColumnPosArgumentType.columnPos())
                  .then(
                     CommandManager.argument("p2", ColumnPosArgumentType.columnPos())
                        .executes(
                           context -> {
                              ColumnPos p1 = ColumnPosArgumentType.getColumnPos(context, "p1");
                              ColumnPos p2 = ColumnPosArgumentType.getColumnPos(context, "p2");
                              WorldWrappingPortal.invokeAddWrappingZone(
                                 ((ServerCommandSource)context.getSource()).getWorld(),
                                 p1.x(),
                                 p1.z(),
                                 p2.x(),
                                 p2.z(),
                                 true,
                                 text -> ((ServerCommandSource)context.getSource()).sendFeedback(() -> text, false)
                              );
                              return 0;
                           }
                        )
                  )
            )
      );
      builder.then(
         CommandManager.literal("create_outward_wrapping")
            .then(
               CommandManager.argument("p1", ColumnPosArgumentType.columnPos())
                  .then(
                     CommandManager.argument("p2", ColumnPosArgumentType.columnPos())
                        .executes(
                           context -> {
                              ColumnPos p1 = ColumnPosArgumentType.getColumnPos(context, "p1");
                              ColumnPos p2 = ColumnPosArgumentType.getColumnPos(context, "p2");
                              WorldWrappingPortal.invokeAddWrappingZone(
                                 ((ServerCommandSource)context.getSource()).getWorld(),
                                 p1.x(),
                                 p1.z(),
                                 p2.x(),
                                 p2.z(),
                                 false,
                                 text -> ((ServerCommandSource)context.getSource()).sendFeedback(() -> text, false)
                              );
                              return 0;
                           }
                        )
                  )
            )
      );
      builder.then(
         ((LiteralArgumentBuilder)CommandManager.literal("remove_wrapping_zone")
               .executes(
                  context -> {
                     WorldWrappingPortal.invokeRemoveWrappingZone(
                        ((ServerCommandSource)context.getSource()).getWorld(),
                        ((ServerCommandSource)context.getSource()).getPosition(),
                        text -> ((ServerCommandSource)context.getSource()).sendFeedback(() -> text, false)
                     );
                     return 0;
                  }
               ))
            .then(
               CommandManager.argument("id", IntegerArgumentType.integer())
                  .executes(
                     context -> {
                        int id = IntegerArgumentType.getInteger(context, "id");
                        WorldWrappingPortal.invokeRemoveWrappingZone(
                           ((ServerCommandSource)context.getSource()).getWorld(),
                           id,
                           text -> ((ServerCommandSource)context.getSource()).sendFeedback(() -> text, false)
                        );
                        return 0;
                     }
                  )
            )
      );
      builder.then(
         CommandManager.literal("view_wrapping_zones")
            .executes(
               context -> {
                  WorldWrappingPortal.invokeViewWrappingZones(
                     ((ServerCommandSource)context.getSource()).getWorld(), text -> ((ServerCommandSource)context.getSource()).sendFeedback(() -> text, false)
                  );
                  return 0;
               }
            )
      );
      builder.then(((LiteralArgumentBuilder)CommandManager.literal("clear_wrapping_border").executes(context -> {
         BorderBarrierFiller.onCommandExecuted(((ServerCommandSource)context.getSource()).getPlayerOrThrow());
         return 0;
      })).then(CommandManager.argument("id", IntegerArgumentType.integer()).executes(context -> {
         int id = IntegerArgumentType.getInteger(context, "id");
         BorderBarrierFiller.onCommandExecuted(((ServerCommandSource)context.getSource()).getPlayerOrThrow(), id);
         return 0;
      })));
      builder.then(
         CommandManager.literal("connect_floor")
            .then(
               CommandManager.argument("from", DimensionArgumentType.dimension())
                  .then(CommandManager.argument("to", DimensionArgumentType.dimension()).executes(context -> {
                     RegistryKey<World> from = DimensionArgumentType.getDimensionArgument(context, "from").getRegistryKey();
                     RegistryKey<World> to = DimensionArgumentType.getDimensionArgument(context, "to").getRegistryKey();
                     VerticalConnectingPortal.connect(from, VerticalConnectingPortal.ConnectorType.floor, to);
                     return 0;
                  }))
            )
      );
      builder.then(
         CommandManager.literal("connect_ceil")
            .then(
               CommandManager.argument("from", DimensionArgumentType.dimension())
                  .then(CommandManager.argument("to", DimensionArgumentType.dimension()).executes(context -> {
                     RegistryKey<World> from = DimensionArgumentType.getDimensionArgument(context, "from").getRegistryKey();
                     RegistryKey<World> to = DimensionArgumentType.getDimensionArgument(context, "to").getRegistryKey();
                     VerticalConnectingPortal.connect(from, VerticalConnectingPortal.ConnectorType.ceil, to);
                     return 0;
                  }))
            )
      );
      builder.then(
         CommandManager.literal("connection_floor_remove").then(CommandManager.argument("dim", DimensionArgumentType.dimension()).executes(context -> {
            RegistryKey<World> dim = DimensionArgumentType.getDimensionArgument(context, "dim").getRegistryKey();
            VerticalConnectingPortal.removeConnectingPortal(VerticalConnectingPortal.ConnectorType.floor, dim);
            return 0;
         }))
      );
      builder.then(
         CommandManager.literal("connection_ceil_remove").then(CommandManager.argument("dim", DimensionArgumentType.dimension()).executes(context -> {
            RegistryKey<World> dim = DimensionArgumentType.getDimensionArgument(context, "dim").getRegistryKey();
            VerticalConnectingPortal.removeConnectingPortal(VerticalConnectingPortal.ConnectorType.ceil, dim);
            return 0;
         }))
      );
      builder.then(CommandManager.literal("view_global_portals").executes(context -> {
         ServerPlayerEntity player = ((ServerCommandSource)context.getSource()).getPlayerOrThrow();
         sendMessage(context, Helper.myToString(GlobalPortalStorage.getGlobalPortals(player.getEntityWorld()).stream()));
         return 0;
      }));
      builder.then(
         CommandManager.literal("convert_normal_portal_to_global_portal")
            .executes(context -> processPortalTargetedCommand(context, GlobalPortalStorage::convertNormalPortalIntoGlobalPortal))
      );
      builder.then(CommandManager.literal("convert_global_portal_to_normal_portal").executes(context -> {
         ServerPlayerEntity player = ((ServerCommandSource)context.getSource()).getPlayerOrThrow();
         Portal portal = getPlayerPointingPortal(player, true);
         if (portal == null) {
            ((ServerCommandSource)context.getSource()).sendFeedback(() -> Text.literal("You are not pointing to any portal"), false);
            return 0;
         } else if (!portal.getIsGlobal()) {
            ((ServerCommandSource)context.getSource()).sendFeedback(() -> Text.literal("You are not pointing to a global portal"), false);
            return 0;
         } else if (player.getEntityPos().distanceTo(portal.getOriginPos()) > 64.0) {
            ((ServerCommandSource)context.getSource()).sendFeedback(() -> Text.literal("You are too far away from the portal's center " + portal), false);
            return 0;
         } else {
            GlobalPortalStorage.convertGlobalPortalIntoNormalPortal(portal);
            return 0;
         }
      }));
      builder.then(CommandManager.literal("delete_global_portal").executes(context -> {
         ServerPlayerEntity player = ((ServerCommandSource)context.getSource()).getPlayerOrThrow();
         Portal portal = getPlayerPointingPortal(player, true);
         if (portal == null) {
            ((ServerCommandSource)context.getSource()).sendFeedback(() -> Text.literal("You are not pointing to any portal"), false);
            return 0;
         } else if (!portal.getIsGlobal()) {
            ((ServerCommandSource)context.getSource()).sendFeedback(() -> Text.literal("You are not pointing to a global portal"), false);
            return 0;
         } else {
            GlobalPortalStorage.get((ServerWorld)portal.getEntityWorld()).removePortal(portal);
            return 0;
         }
      }));
   }

   private static void registerPortalTargetedCommands(LiteralArgumentBuilder<ServerCommandSource> builder, CommandRegistryAccess ctx) {
      builder.then(
         CommandManager.literal("view_portal_data").executes(context -> processPortalTargetedCommand(context, portal -> sendPortalInfo(context, portal)))
      );
      builder.then(
         CommandManager.literal("set_portal_custom_name")
            .then(CommandManager.argument("name", TextArgumentType.text(ctx)).executes(context -> processPortalTargetedCommand(context, portal -> {
               Text name = TextArgumentType.getTextArgument(context, "name");
               portal.setCustomName(name);
            })))
      );
      builder.then(CommandManager.literal("delete_portal").executes(context -> processPortalTargetedCommand(context, portal -> {
         sendMessage(context, "deleted " + portal);
         portal.remove(RemovalReason.KILLED);
      })));
      builder.then(
         CommandManager.literal("set_portal_nbt")
            .then(CommandManager.argument("nbt", NbtCompoundArgumentType.nbtCompound()).executes(context -> processPortalTargetedCommand(context, portal -> {
               NbtCompound newNbt = NbtCompoundArgumentType.getNbtCompound(context, "nbt");
               invokeSetPortalNbt(context, portal, newNbt);
            })))
      );
      builder.then(
         CommandManager.literal("nbt")
            .then(CommandManager.argument("nbt", NbtCompoundArgumentType.nbtCompound()).executes(context -> processPortalTargetedCommand(context, portal -> {
               NbtCompound newNbt = NbtCompoundArgumentType.getNbtCompound(context, "nbt");
               invokeSetPortalNbt(context, portal, newNbt);
            })))
      );
      builder.then(
         CommandManager.literal("set_portal_destination")
            .then(
               CommandManager.argument("dim", DimensionArgumentType.dimension())
                  .then(CommandManager.argument("dest", Vec3ArgumentType.vec3(false)).executes(context -> processPortalTargetedCommand(context, portal -> {
                     sendEditBiWayPortalWarning(context, portal);
                     portal.setDestDim(DimensionArgumentType.getDimensionArgument(context, "dim").getRegistryKey());
                     portal.setDestination(Vec3ArgumentType.getVec3(context, "dest"));
                     reloadPortal(portal);
                     sendMessage(context, portal.toString());
                  })))
            )
      );
      registerPortalTargetedCommandWithRotationArgument(builder, "set_portal_rotation", (p, r) -> p.setRotation(r));
      registerPortalTargetedCommandWithRotationArgument(builder, "rotate_portal_body", (p, r) -> {
         if (r != null) {
            PortalManipulation.rotatePortalBody(p, r);
         }
      });
      registerPortalTargetedCommandWithRotationArgument(builder, "rotate_portal_rotation", (portal, rot) -> {
         if (rot != null) {
            DQuaternion rotation = portal.getRotation();
            if (rotation == null) {
               portal.setRotation(rot);
            } else {
               portal.setRotation(rotation.hamiltonProduct(rot));
            }
         }
      });
      builder.then(
         CommandManager.literal("complete_bi_way_portal")
            .executes(context -> processPortalTargetedCommand(context, portal -> invokeCompleteBiWayPortal(context, portal)))
      );
      builder.then(
         CommandManager.literal("complete_bi_faced_portal")
            .executes(context -> processPortalTargetedCommand(context, portal -> invokeCompleteBiFacedPortal(context, portal)))
      );
      builder.then(
         CommandManager.literal("complete_bi_way_bi_faced_portal")
            .executes(context -> processPortalTargetedCommand(context, portal -> invokeCompleteBiWayBiFacedPortal(context, portal)))
      );
      builder.then(CommandManager.literal("remove_connected_portals").executes(context -> processPortalTargetedCommand(context, portal -> {
         PortalExtension.get(portal).bindCluster = false;
         reloadPortal(portal);
         PortalManipulation.removeConnectedPortals(portal, p -> sendMessage(context, "Removed " + p));
      })));
      builder.then(CommandManager.literal("eradicate_portal_clutter").executes(context -> processPortalTargetedCommand(context, portal -> {
         PortalManipulation.removeConnectedPortals(portal, p -> sendMessage(context, "Removed " + p));
         portal.remove(RemovalReason.KILLED);
         sendMessage(context, "Deleted " + portal);
      })));
      builder.then(CommandManager.literal("eradicate_portal_cluster").executes(context -> processPortalTargetedCommand(context, portal -> {
         HashSet<Portal> removed = new HashSet<>();
         PortalManipulation.removeConnectedPortals(portal, removed::add);
         portal.remove(RemovalReason.KILLED);
         removed.add(portal);
         sendMessage(context, "Deleted %d portal entities".formatted(removed.size()));
      })));
      builder.then(
         CommandManager.literal("move_portal")
            .then(CommandManager.argument("distance", DoubleArgumentType.doubleArg()).executes(context -> processPortalTargetedCommand(context, portal -> {
               try {
                  sendEditBiWayPortalWarning(context, portal);
                  double distance = DoubleArgumentType.getDouble(context, "distance");
                  ServerPlayerEntity player = ((ServerCommandSource)context.getSource()).getPlayerOrThrow();
                  Vec3d viewVector = player.getRotationVector();
                  Direction facing = Direction.getFacing(viewVector.x, viewVector.y, viewVector.z);
                  Vec3d offset = Vec3d.of(facing.getVector()).multiply(distance);
                  portal.setPosition(portal.getX() + offset.x, portal.getY() + offset.y, portal.getZ() + offset.z);
                  reloadPortal(portal);
               } catch (CommandSyntaxException var8) {
                  sendMessage(context, "This command can only be invoked by player");
               }
            })))
      );
      builder.then(
         CommandManager.literal("move_portal_destination")
            .then(CommandManager.argument("distance", DoubleArgumentType.doubleArg()).executes(context -> processPortalTargetedCommand(context, portal -> {
               try {
                  sendEditBiWayPortalWarning(context, portal);
                  double distance = DoubleArgumentType.getDouble(context, "distance");
                  ServerPlayerEntity player = ((ServerCommandSource)context.getSource()).getPlayerOrThrow();
                  Vec3d viewVector = player.getRotationVector();
                  Direction facing = Direction.getFacing(viewVector.x, viewVector.y, viewVector.z);
                  Vec3d offset = Vec3d.of(facing.getVector()).multiply(distance);
                  portal.setDestination(portal.getDestPos().add(portal.transformLocalVecNonScale(offset)));
                  reloadPortal(portal);
               } catch (CommandSyntaxException var8) {
                  sendMessage(context, "This command can only be invoked by player");
               }
            })))
      );
      builder.then(
         ((LiteralArgumentBuilder)CommandManager.literal("set_portal_specific_accessor")
               .executes(context -> processPortalTargetedCommand(context, portal -> removeSpecificAccessor(context, portal))))
            .then(
               CommandManager.argument("player", EntityArgumentType.player())
                  .executes(
                     context -> processPortalTargetedCommand(
                        context, portal -> setSpecificAccessor(context, portal, EntityArgumentType.getEntity(context, "player"))
                     )
                  )
            )
      );
      builder.then(
         CommandManager.literal("multidest")
            .then(
               ((RequiredArgumentBuilder)CommandManager.argument("player", EntityArgumentType.player())
                     .executes(
                        context -> processPortalTargetedCommand(
                           context, portal -> removeMultidestEntry(context, portal, EntityArgumentType.getPlayer(context, "player"))
                        )
                     ))
                  .then(
                     CommandManager.argument("dimension", DimensionArgumentType.dimension())
                        .then(
                           CommandManager.argument("destination", Vec3ArgumentType.vec3(false))
                              .then(
                                 CommandManager.argument("isBiFaced", BoolArgumentType.bool())
                                    .then(
                                       CommandManager.argument("isBiWay", BoolArgumentType.bool())
                                          .executes(
                                             context -> processPortalTargetedCommand(
                                                context,
                                                portal -> setMultidestEntry(
                                                   context,
                                                   portal,
                                                   EntityArgumentType.getPlayer(context, "player"),
                                                   DimensionArgumentType.getDimensionArgument(context, "dimension").getRegistryKey(),
                                                   Vec3ArgumentType.getVec3(context, "destination"),
                                                   BoolArgumentType.getBool(context, "isBiFaced"),
                                                   BoolArgumentType.getBool(context, "isBiWay")
                                                )
                                             )
                                          )
                                    )
                              )
                        )
                  )
            )
      );
      builder.then(CommandManager.literal("make_portal_round").executes(context -> processPortalTargetedCommand(context, portal -> {
         PortalManipulation.makePortalRound(portal, 30);
         reloadPortal(portal);
      })));
      builder.then(
         CommandManager.literal("set_portal_scale")
            .then(CommandManager.argument("scale", DoubleArgumentType.doubleArg(0.0)).executes(context -> processPortalTargetedCommand(context, portal -> {
               double scale = DoubleArgumentType.getDouble(context, "scale");
               portal.setScaling(scale);
               reloadPortal(portal);
            })))
      );
      builder.then(
         CommandManager.literal("multiply_portal_scale")
            .then(CommandManager.argument("scale", DoubleArgumentType.doubleArg(0.0)).executes(context -> processPortalTargetedCommand(context, portal -> {
               double scale = DoubleArgumentType.getDouble(context, "scale");
               portal.setScaling(portal.getScaling() * scale);
               reloadPortal(portal);
            })))
      );
      builder.then(
         CommandManager.literal("divide_portal_scale")
            .then(CommandManager.argument("scale", DoubleArgumentType.doubleArg(0.0)).executes(context -> processPortalTargetedCommand(context, portal -> {
               double scale = DoubleArgumentType.getDouble(context, "scale");
               portal.setScaling(portal.getScaling() / scale);
               reloadPortal(portal);
            })))
      );
      builder.then(
         CommandManager.literal("set_portal_destination_to")
            .then(CommandManager.argument("entity", EntityArgumentType.entity()).executes(context -> processPortalTargetedCommand(context, portal -> {
               Entity entity = EntityArgumentType.getEntity(context, "entity");
               portal.setDestDim(entity.getEntityWorld().getRegistryKey());
               portal.setDestination(entity.getEntityPos());
               reloadPortal(portal);
            })))
      );
      builder.then(
         CommandManager.literal("set_portal_position")
            .then(
               CommandManager.argument("dim", DimensionArgumentType.dimension())
                  .then(CommandManager.argument("pos", Vec3ArgumentType.vec3(false)).executes(context -> processPortalTargetedCommand(context, portal -> {
                     ServerWorld targetWorld = DimensionArgumentType.getDimensionArgument(context, "dim");
                     Vec3d pos = Vec3ArgumentType.getVec3(context, "pos");
                     if (targetWorld == portal.getEntityWorld()) {
                        portal.setOriginPos(pos);
                        reloadPortal(portal);
                     } else {
                        ServerTeleportationManager.teleportEntityGeneral(portal, pos, targetWorld);
                     }

                     sendMessage(context, portal.toString());
                  })))
            )
      );
      builder.then(
         CommandManager.literal("set_portal_position_to")
            .then(CommandManager.argument("targetEntity", EntityArgumentType.entity()).executes(context -> processPortalTargetedCommand(context, portal -> {
               Entity targetEntity = EntityArgumentType.getEntity(context, "targetEntity");
               if (targetEntity.getEntityWorld() == portal.getEntityWorld()) {
                  portal.setOriginPos(targetEntity.getEntityPos());
                  reloadPortal(portal);
               } else {
                  ServerTeleportationManager.teleportEntityGeneral(portal, targetEntity.getEntityPos(), (ServerWorld)targetEntity.getEntityWorld());
               }

               sendMessage(context, portal.toString());
            })))
      );
      builder.then(CommandManager.literal("reset_portal_orientation").executes(context -> processPortalTargetedCommand(context, portal -> {
         UnilateralPortalState thisSideState = portal.getThisSideState();
         portal.setThisSideState(new UnilateralPortalState.Builder().from(thisSideState).orientation(DQuaternion.identity).build());
         reloadPortal(portal);
      })));
      builder.then(
         CommandManager.literal("relatively_move_portal")
            .then(CommandManager.argument("offset", Vec3ArgumentType.vec3(false)).executes(context -> processPortalTargetedCommand(context, portal -> {
               Vec3d offset = Vec3ArgumentType.getVec3(context, "offset");
               Vec3d offsetTransformed = portal.transformLocalVec(offset);
               portal.setOriginPos(portal.getOriginPos().add(offsetTransformed));
               reloadPortal(portal);
            })))
      );
      builder.then(
         CommandManager.literal("relatively_move_portal_destination")
            .then(
               CommandManager.argument("offset", Vec3ArgumentType.vec3(false))
                  .executes(
                     context -> processPortalTargetedCommand(
                        context,
                        portal -> {
                           Vec3d offset = Vec3ArgumentType.getVec3(context, "offset");
                           portal.setDestination(
                              portal.getDestPos()
                                 .add(portal.transformLocalVec(portal.getAxisW()).multiply(offset.x))
                                 .add(portal.transformLocalVec(portal.getAxisH()).multiply(offset.y))
                                 .add(portal.transformLocalVec(portal.getNormal()).multiply(offset.z))
                           );
                           reloadPortal(portal);
                        }
                     )
                  )
            )
      );
      builder.then(
         CommandManager.literal("set_portal_size")
            .then(
               CommandManager.argument("width", DoubleArgumentType.doubleArg(0.0))
                  .then(
                     CommandManager.argument("height", DoubleArgumentType.doubleArg(0.0)).executes(context -> processPortalTargetedCommand(context, portal -> {
                        double width = DoubleArgumentType.getDouble(context, "width");
                        double height = DoubleArgumentType.getDouble(context, "height");
                        portal.setWidth(width);
                        portal.setHeight(height);
                        portal.setPortalShapeToDefault();
                        reloadPortal(portal);
                     }))
                  )
            )
      );
      builder.then(CommandManager.literal("adjust_portal_to_fit_square_frame").executes(context -> processPortalTargetedCommand(context, portal -> {
         adjustPortalAreaToFitFrame(portal);
         reloadPortal(portal);
      })));
      builder.then(
         ((LiteralArgumentBuilder)CommandManager.literal("add_command_on_teleported")
               .requires(serverCommandSource -> serverCommandSource.getPermissions().hasPermission(DefaultPermissions.GAMEMASTERS)))
            .then(CommandManager.argument("subCommand", SubCommandArgumentType.instance).executes(context -> processPortalTargetedCommand(context, portal -> {
               String subCommand = SubCommandArgumentType.get(context, "subCommand");
               if (portal.getCommandsOnTeleported() == null) {
                  portal.setCommandsOnTeleported(new ArrayList<>());
               }

               portal.getCommandsOnTeleported().add(subCommand);
               portal.reloadAndSyncToClient();
               sendPortalInfo(context, portal);
            })))
      );
      builder.then(
         ((LiteralArgumentBuilder)CommandManager.literal("remove_command_on_teleported_at")
               .requires(serverCommandSource -> serverCommandSource.getPermissions().hasPermission(DefaultPermissions.GAMEMASTERS)))
            .then(
               CommandManager.argument("indexStartingFromZero", IntegerArgumentType.integer(0, 100))
                  .executes(context -> processPortalTargetedCommand(context, portal -> {
                     if (portal.getCommandsOnTeleported() != null) {
                        int index = IntegerArgumentType.getInteger(context, "indexStartingFromZero");
                        if (index >= portal.getCommandsOnTeleported().size()) {
                           ((ServerCommandSource)context.getSource()).sendError(Text.literal("Index out of range"));
                        } else {
                           portal.getCommandsOnTeleported().remove(index);
                           portal.reloadAndSyncToClient();
                           sendPortalInfo(context, portal);
                        }
                     }
                  }))
            )
      );
      builder.then(
         ((LiteralArgumentBuilder)CommandManager.literal("set_command_on_teleported_at")
               .requires(serverCommandSource -> serverCommandSource.getPermissions().hasPermission(DefaultPermissions.GAMEMASTERS)))
            .then(
               CommandManager.argument("indexStartingFromZero", IntegerArgumentType.integer(0, 100))
                  .then(
                     CommandManager.argument("subCommand", SubCommandArgumentType.instance)
                        .executes(context -> processPortalTargetedCommand(context, portal -> {
                           if (portal.getCommandsOnTeleported() != null) {
                              int index = IntegerArgumentType.getInteger(context, "indexStartingFromZero");
                              if (index >= portal.getCommandsOnTeleported().size()) {
                                 ((ServerCommandSource)context.getSource()).sendError(Text.literal("Index out of range"));
                              } else {
                                 String subCommand = SubCommandArgumentType.get(context, "subCommand");
                                 portal.getCommandsOnTeleported().set(index, subCommand);
                                 portal.reloadAndSyncToClient();
                                 sendPortalInfo(context, portal);
                              }
                           }
                        }))
                  )
            )
      );
      builder.then(
         ((LiteralArgumentBuilder)CommandManager.literal("clear_commands_on_teleported")
               .requires(serverCommandSource -> serverCommandSource.getPermissions().hasPermission(DefaultPermissions.GAMEMASTERS)))
            .executes(context -> processPortalTargetedCommand(context, portal -> {
               portal.setCommandsOnTeleported(null);
               portal.reloadAndSyncToClient();
               sendPortalInfo(context, portal);
            }))
      );
      builder.then(
         CommandManager.literal("turn_info_fake_enterable_mirror")
            .executes(context -> processPortalTargetedCommand(context, portal -> invokeTurnIntoFakeEnterableMirror(context, portal)))
      );
      LiteralArgumentBuilder<ServerCommandSource> shapeBuilder = CommandManager.literal("shape");
      shapeBuilder.then(
         ((LiteralArgumentBuilder)CommandManager.literal("sculpt")
               .executes(context -> processPortalTargetedCommand(context, portal -> invokeSculpt(context, portal, true))))
            .then(CommandManager.argument("adjustPortalBounds", BoolArgumentType.bool()).executes(context -> processPortalTargetedCommand(context, portal -> {
               boolean adjustPortalBounds = BoolArgumentType.getBool(context, "adjustPortalBounds");
               invokeSculpt(context, portal, adjustPortalBounds);
            })))
      );
      shapeBuilder.then(CommandManager.literal("reset").executes(context -> processPortalTargetedCommand(context, portal -> {
         portal.setPortalShapeToDefault();
         reloadPortal(portal);
      })));
      builder.then(shapeBuilder);
      LiteralArgumentBuilder<ServerCommandSource> toggleBuilder = CommandManager.literal("toggle");
      registerToggleCommand(toggleBuilder, "interactable", Portal::isInteractable, Portal::setInteractable);
      registerToggleCommand(toggleBuilder, "teleportable", Portal::isTeleportable, Portal::setTeleportable);
      registerToggleCommand(toggleBuilder, "teleportChangesScale", Portal::isTeleportChangesScale, Portal::setTeleportChangesScale);
      registerToggleCommand(toggleBuilder, "teleportChangesGravity", Portal::isTeleportChangesGravity, Portal::setTeleportChangesGravity);
      registerToggleCommand(toggleBuilder, "fuseView", Portal::isFuseView, Portal::setFuseView);
      registerToggleCommand(toggleBuilder, "visible", Portal::isVisible, Portal::setIsVisible);
      registerToggleCommand(toggleBuilder, "crossPortalCollisionEnabled", Portal::isCrossPortalCollisionEnabled, Portal::setCrossPortalCollisionEnabled);
      registerToggleCommand(toggleBuilder, "doRenderPayer", Portal::getDoRenderPlayer, Portal::setDoRenderPlayer);
      builder.then(toggleBuilder);
   }

   private static void registerToggleCommand(
      LiteralArgumentBuilder<ServerCommandSource> builder, String property, Function<Portal, Boolean> getter, BiConsumer<Portal, Boolean> setter
   ) {
      builder.then(CommandManager.literal(property).executes(context -> processPortalTargetedCommand(context, portal -> {
         boolean newValue = !getter.apply(portal);
         setter.accept(portal, newValue);
         reloadPortal(portal);
         sendMessage(context, "Set %s to %s".formatted(property, newValue));
      })));
   }

   private static void invokeSetPortalNbt(CommandContext<ServerCommandSource> context, Portal portal, NbtCompound newNbt) {
      if (newNbt.contains("commandsOnTeleported") && !((ServerCommandSource)context.getSource()).getPermissions().hasPermission(DefaultPermissions.GAMEMASTERS)
         )
       {
         ((ServerCommandSource)context.getSource()).sendError(Text.literal("You do not have the permission to set commandsOnTeleported"));
      } else if (newNbt.contains("dimensionTo")) {
         ((ServerCommandSource)context.getSource()).sendError(Text.literal("Cannot change tag dimensionTo. use command /portal set_portal_destination"));
      } else {
         portal.updatePortalFromNbt(newNbt);
         reloadPortal(portal);
         sendPortalInfo(context, portal);
      }
   }

   private static void adjustPortalAreaToFitFrame(Portal portal) {
      portal.setPortalShapeToDefault();
      BlockPos origin = BlockPos.ofFloored(portal.getOriginPos());
      Direction portalNormalDirection = Direction.getFacing(portal.getNormal().x, portal.getNormal().y, portal.getNormal().z);
      World world = portal.getEntityWorld();
      IntBox boxArea = Helper.expandRectangle(origin, pos -> world.getBlockState(pos).isAir(), portalNormalDirection.getAxis());
      Box portalBox = new Box(0.0, 0.0, 0.0, 0.0, 0.0, 0.0);

      for (Direction direction : Direction.values()) {
         IntBox outerSurface = boxArea.getSurfaceLayer(direction).getMoved(direction.getVector());
         Box collisionBox = McHelper.getWallBox(world, outerSurface);
         if (collisionBox == null) {
            collisionBox = outerSurface.toRealNumberBox();
         }

         portalBox = Helper.replaceBoxCoordinate(portalBox, direction, Helper.getBoxCoordinate(collisionBox, direction.getOpposite()));
      }

      double portalNormalCoordinate = Helper.getCoordinate(portal.getOriginPos(), portalNormalDirection.getAxis());
      portalBox = Helper.replaceBoxCoordinate(portalBox, portalNormalDirection, portalNormalCoordinate);
      portalBox = Helper.replaceBoxCoordinate(portalBox, portalNormalDirection.getOpposite(), portalNormalCoordinate);
      PortalAPI.setPortalOrthodoxShape(portal, portalNormalDirection, portalBox);
   }

   public static void reloadPortal(Portal portal) {
      portal.reloadPortal();
   }

   private static void registerPortalTargetedCommandWithRotationArgument(
      LiteralArgumentBuilder<ServerCommandSource> builder, String literal, BiConsumer<Portal, DQuaternion> func
   ) {
      builder.then(
         CommandManager.literal(literal)
            .then(
               CommandManager.argument("rotatingAxis", Vec3ArgumentType.vec3(false))
                  .then(
                     CommandManager.argument("angleDegrees", DoubleArgumentType.doubleArg())
                        .executes(context -> processPortalTargetedCommand(context, portal -> {
                           Vec3d rotatingAxis = Vec3ArgumentType.getVec3(context, "rotatingAxis").normalize();
                           doInvokeRotationCommandWithAngleArgument(func, context, portal, rotatingAxis);
                        }))
                  )
            )
      );
      builder.then(
         ((LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal(literal + "_along")
                  .then(
                     CommandManager.literal("x")
                        .then(
                           CommandManager.argument("angleDegrees", DoubleArgumentType.doubleArg())
                              .executes(
                                 context -> processPortalTargetedCommand(
                                    context, portal -> doInvokeRotationCommandWithAngleArgument(func, context, portal, new Vec3d(1.0, 0.0, 0.0))
                                 )
                              )
                        )
                  ))
               .then(
                  CommandManager.literal("y")
                     .then(
                        CommandManager.argument("angleDegrees", DoubleArgumentType.doubleArg())
                           .executes(
                              context -> processPortalTargetedCommand(
                                 context, portal -> doInvokeRotationCommandWithAngleArgument(func, context, portal, new Vec3d(0.0, 1.0, 0.0))
                              )
                           )
                     )
               ))
            .then(
               CommandManager.literal("z")
                  .then(
                     CommandManager.argument("angleDegrees", DoubleArgumentType.doubleArg())
                        .executes(
                           context -> processPortalTargetedCommand(
                              context, portal -> doInvokeRotationCommandWithAngleArgument(func, context, portal, new Vec3d(0.0, 0.0, 1.0))
                           )
                        )
                  )
            )
      );
   }

   private static void doInvokeRotationCommandWithAngleArgument(
      BiConsumer<Portal, DQuaternion> func, CommandContext<ServerCommandSource> context, Portal portal, Vec3d rotatingAxis
   ) {
      sendEditBiWayPortalWarning(context, portal);
      double angleDegrees = DoubleArgumentType.getDouble(context, "angleDegrees");
      DQuaternion rot = angleDegrees != 0.0 ? DQuaternion.rotationByDegrees(rotatingAxis, (float)angleDegrees) : null;
      func.accept(portal, rot);
      reloadPortal(portal);
   }

   private static void registerCBPortalCommands(LiteralArgumentBuilder<ServerCommandSource> builder) {
      builder.then(
         CommandManager.literal("cb_make_portal")
            .then(
               CommandManager.argument("width", DoubleArgumentType.doubleArg())
                  .then(
                     CommandManager.argument("height", DoubleArgumentType.doubleArg())
                        .then(
                           CommandManager.argument("from", EntityArgumentType.entity())
                              .then(((RequiredArgumentBuilder)CommandManager.argument("to", EntityArgumentType.entity()).executes(context -> {
                                 double width = DoubleArgumentType.getDouble(context, "width");
                                 double height = DoubleArgumentType.getDouble(context, "height");
                                 Entity fromEntity = EntityArgumentType.getEntity(context, "from");
                                 Entity toEntity = EntityArgumentType.getEntity(context, "to");
                                 invokeCbMakePortal(width, height, fromEntity, toEntity, "");
                                 return 0;
                              })).then(CommandManager.argument("portalName", StringArgumentType.string()).executes(context -> {
                                 double width = DoubleArgumentType.getDouble(context, "width");
                                 double height = DoubleArgumentType.getDouble(context, "height");
                                 Entity fromEntity = EntityArgumentType.getEntity(context, "from");
                                 Entity toEntity = EntityArgumentType.getEntity(context, "to");
                                 String portalName = StringArgumentType.getString(context, "portalName");
                                 invokeCbMakePortal(width, height, fromEntity, toEntity, portalName);
                                 return 0;
                              })))
                        )
                  )
            )
      );
   }

   private static void invokeCbMakePortal(double width, double height, Entity fromEntity, Entity toEntity, String portalName) {
      Portal portal = (Portal)Portal.ENTITY_TYPE.create(fromEntity.getEntityWorld(), SpawnReason.COMMAND);
      portal.setPosition(fromEntity.getX(), fromEntity.getY(), fromEntity.getZ());
      portal.setDestDim(toEntity.getEntityWorld().getRegistryKey());
      portal.setDestination(toEntity.getEntityPos());
      portal.setWidth(width);
      portal.setHeight(height);
      Vec3d normal = fromEntity.getRotationVec(1.0F);
      Vec3d rightVec = getRightVec(fromEntity);
      Vec3d axisH = rightVec.crossProduct(normal).normalize();
      portal.setAxisW(rightVec);
      portal.setAxisH(axisH);
      portal.setCustomName(Text.literal(portalName));
      McHelper.spawnServerEntity(portal);
   }

   private static void registerUtilityCommands(LiteralArgumentBuilder<ServerCommandSource> builder) {
      builder.then(
         ((LiteralArgumentBuilder)((LiteralArgumentBuilder)CommandManager.literal("tpme")
                  .then(
                     CommandManager.argument("target", EntityArgumentType.entity())
                        .executes(
                           context -> {
                              Entity entity = EntityArgumentType.getEntity(context, "target");
                              ServerTeleportationManager.of(((ServerCommandSource)context.getSource()).getServer())
                                 .forceTeleportPlayer(
                                    ((ServerCommandSource)context.getSource()).getPlayerOrThrow(),
                                    entity.getEntityWorld().getRegistryKey(),
                                    entity.getEntityPos()
                                 );
                              ((ServerCommandSource)context.getSource())
                                 .sendFeedback(() -> Text.translatable("imm_ptl.command.tpme.success", new Object[]{entity.getDisplayName()}), true);
                              return 1;
                           }
                        )
                  ))
               .then(
                  CommandManager.argument("dest", Vec3ArgumentType.vec3())
                     .executes(
                        context -> {
                           Vec3d dest = Vec3ArgumentType.getVec3(context, "dest");
                           ServerPlayerEntity player = ((ServerCommandSource)context.getSource()).getPlayerOrThrow();
                           ServerTeleportationManager.of(((ServerCommandSource)context.getSource()).getServer())
                              .forceTeleportPlayer(player, player.getEntityWorld().getRegistryKey(), dest);
                           ((ServerCommandSource)context.getSource())
                              .sendFeedback(() -> Text.translatable("imm_ptl.command.tpme.success", new Object[]{dest.toString()}), true);
                           return 1;
                        }
                     )
               ))
            .then(
               CommandManager.argument("dim", DimensionArgumentType.dimension())
                  .then(
                     CommandManager.argument("dest", Vec3ArgumentType.vec3())
                        .executes(
                           context -> {
                              RegistryKey<World> dim = DimensionArgumentType.getDimensionArgument(context, "dim").getRegistryKey();
                              Vec3d dest = Vec3ArgumentType.getVec3(context, "dest");
                              ServerTeleportationManager.of(((ServerCommandSource)context.getSource()).getServer())
                                 .forceTeleportPlayer(((ServerCommandSource)context.getSource()).getPlayerOrThrow(), dim, dest);
                              ((ServerCommandSource)context.getSource())
                                 .sendFeedback(
                                    () -> Text.translatable(
                                       "imm_ptl.command.tpme.success", new Object[]{McHelper.dimensionTypeId(dim).toString() + dest.toString()}
                                    ),
                                    true
                                 );
                              return 1;
                           }
                        )
                  )
            )
      );
      builder.then(
         ((LiteralArgumentBuilder)CommandManager.literal("tp")
               .requires(commandSource -> commandSource.getPermissions().hasPermission(DefaultPermissions.GAMEMASTERS)))
            .then(
               ((RequiredArgumentBuilder)((RequiredArgumentBuilder)CommandManager.argument("from", EntityArgumentType.entities())
                        .then(
                           CommandManager.argument("to", EntityArgumentType.entity())
                              .executes(
                                 context -> {
                                    Collection<? extends Entity> entities = EntityArgumentType.getEntities(context, "from");
                                    Entity target = EntityArgumentType.getEntity(context, "to");
                                    int numTeleported = teleport(entities, target.getEntityWorld().getRegistryKey(), target.getEntityPos());
                                    ((ServerCommandSource)context.getSource())
                                       .sendFeedback(
                                          () -> Text.translatable("imm_ptl.command.tp.success", new Object[]{numTeleported, target.getDisplayName()}), true
                                       );
                                    return numTeleported;
                                 }
                              )
                        ))
                     .then(
                        CommandManager.argument("dest", Vec3ArgumentType.vec3())
                           .executes(
                              context -> {
                                 Collection<? extends Entity> entities = EntityArgumentType.getEntities(context, "from");
                                 Vec3d dest = Vec3ArgumentType.getVec3(context, "dest");
                                 int numTeleported = teleport(entities, ((ServerCommandSource)context.getSource()).getWorld().getRegistryKey(), dest);
                                 ((ServerCommandSource)context.getSource())
                                    .sendFeedback(() -> Text.translatable("imm_ptl.command.tp.success", new Object[]{numTeleported, dest.toString()}), true);
                                 return numTeleported;
                              }
                           )
                     ))
                  .then(
                     CommandManager.argument("dim", DimensionArgumentType.dimension())
                        .then(
                           CommandManager.argument("dest", Vec3ArgumentType.vec3())
                              .executes(
                                 context -> {
                                    Collection<? extends Entity> entities = EntityArgumentType.getEntities(context, "from");
                                    RegistryKey<World> dim = DimensionArgumentType.getDimensionArgument(context, "dim").getRegistryKey();
                                    Vec3d dest = Vec3ArgumentType.getVec3(context, "dest");
                                    int numTeleported = teleport(entities, ((ServerCommandSource)context.getSource()).getWorld().getRegistryKey(), dest);
                                    ((ServerCommandSource)context.getSource())
                                       .sendFeedback(
                                          () -> Text.translatable(
                                             "imm_ptl.command.tp.success",
                                             new Object[]{numTeleported, McHelper.dimensionTypeId(dim).toString() + dest.toString()}
                                          ),
                                          true
                                       );
                                    return numTeleported;
                                 }
                              )
                        )
                  )
            )
      );
      builder.then(
         CommandManager.literal("make_portal")
            .then(
               CommandManager.argument("width", DoubleArgumentType.doubleArg())
                  .then(
                     CommandManager.argument("height", DoubleArgumentType.doubleArg())
                        .then(
                           ((RequiredArgumentBuilder)CommandManager.argument("to", DimensionArgumentType.dimension())
                                 .then(CommandManager.argument("dest", Vec3ArgumentType.vec3(false)).executes(PortalCommand::placePortalAbsolute)))
                              .then(
                                 CommandManager.literal("shift")
                                    .then(CommandManager.argument("dist", DoubleArgumentType.doubleArg()).executes(PortalCommand::placePortalShift))
                              )
                        )
                  )
            )
      );
      builder.then(CommandManager.literal("goback").executes(context -> {
         ServerPlayerEntity player = ((ServerCommandSource)context.getSource()).getPlayerOrThrow();
         ServerTeleportationManager serverTeleportationManager = ServerTeleportationManager.of(((ServerCommandSource)context.getSource()).getServer());
         WithDim<Vec3d> lastPos = serverTeleportationManager.lastPosition.get(player);
         if (lastPos == null) {
            sendMessage(context, "You haven't teleported");
         } else {
            serverTeleportationManager.forceTeleportPlayer(player, lastPos.dimension(), lastPos.value());
         }

         return 0;
      }));
      builder.then(
         CommandManager.literal("create_small_inward_wrapping")
            .then(
               CommandManager.argument("p1", Vec3ArgumentType.vec3(false))
                  .then(CommandManager.argument("p2", Vec3ArgumentType.vec3(false)).executes(context -> {
                     Vec3d p1 = Vec3ArgumentType.getVec3(context, "p1");
                     Vec3d p2 = Vec3ArgumentType.getVec3(context, "p2");
                     Box box = new Box(p1, p2);
                     ServerWorld world = ((ServerCommandSource)context.getSource()).getWorld();
                     addSmallWorldWrappingPortals(box, world, true);
                     return 0;
                  }))
            )
      );
      builder.then(
         CommandManager.literal("create_small_outward_wrapping")
            .then(
               CommandManager.argument("p1", Vec3ArgumentType.vec3(false))
                  .then(CommandManager.argument("p2", Vec3ArgumentType.vec3(false)).executes(context -> {
                     Vec3d p1 = Vec3ArgumentType.getVec3(context, "p1");
                     Vec3d p2 = Vec3ArgumentType.getVec3(context, "p2");
                     Box box = new Box(p1, p2);
                     ServerWorld world = ((ServerCommandSource)context.getSource()).getWorld();
                     addSmallWorldWrappingPortals(box, world, false);
                     return 0;
                  }))
            )
      );
      builder.then(
         CommandManager.literal("create_scaled_box_view")
            .then(
               CommandManager.argument("p1", BlockPosArgumentType.blockPos())
                  .then(
                     CommandManager.argument("p2", BlockPosArgumentType.blockPos())
                        .then(
                           CommandManager.argument("scale", DoubleArgumentType.doubleArg())
                              .then(
                                 CommandManager.argument("placeTargetEntity", EntityArgumentType.entity())
                                    .then(((RequiredArgumentBuilder)CommandManager.argument("biWay", BoolArgumentType.bool()).executes(context -> {
                                       invokeOldCreateScaledViewCommand(context, false, false, false, false, BoolArgumentType.getBool(context, "biWay"));
                                       return 0;
                                    })).then(CommandManager.argument("teleportChangesScale", BoolArgumentType.bool()).executes(context -> {
                                       boolean teleportChangesScale = BoolArgumentType.getBool(context, "teleportChangesScale");
                                       invokeOldCreateScaledViewCommand(
                                          context, teleportChangesScale, false, false, false, BoolArgumentType.getBool(context, "biWay")
                                       );
                                       return 0;
                                    })))
                              )
                        )
                  )
            )
      );
      builder.then(
         CommandManager.literal("create_scaled_box_view_optimized")
            .then(
               CommandManager.argument("p1", BlockPosArgumentType.blockPos())
                  .then(
                     CommandManager.argument("p2", BlockPosArgumentType.blockPos())
                        .then(
                           CommandManager.argument("scale", DoubleArgumentType.doubleArg())
                              .then(CommandManager.argument("placeTargetEntity", EntityArgumentType.entity()).executes(context -> {
                                 BlockPos bp1 = BlockPosArgumentType.getValidBlockPos(context, "p1");
                                 BlockPos bp2 = BlockPosArgumentType.getValidBlockPos(context, "p2");
                                 IntBox intBox = new IntBox(bp1, bp2);
                                 Entity placeTargetEntity = EntityArgumentType.getEntity(context, "placeTargetEntity");
                                 ServerWorld boxWorld = (ServerWorld)placeTargetEntity.getEntityWorld();
                                 Box area = intBox.toRealNumberBox();
                                 ServerWorld areaWorld = ((ServerCommandSource)context.getSource()).getWorld();
                                 double scale = DoubleArgumentType.getDouble(context, "scale");
                                 double thisSideWidth = area.getLengthX() / scale;
                                 double thisSideHeight = area.getLengthY() / scale;
                                 double thisSideThickness = area.getLengthZ() / scale;
                                 Portal portal = (Portal)Portal.ENTITY_TYPE.create(boxWorld, SpawnReason.COMMAND);

                                 assert portal != null;

                                 portal.setDestinationDimension(areaWorld.getRegistryKey());
                                 portal.setOriginPos(placeTargetEntity.getEntityPos().add(0.0, thisSideHeight / 2.0, 0.0));
                                 portal.setDestination(area.getCenter());
                                 portal.setOrientationRotation(DQuaternion.identity);
                                 portal.setPortalSize(thisSideWidth, thisSideHeight, thisSideThickness);
                                 portal.setPortalShape(BoxPortalShape.FACING_OUTWARDS);
                                 portal.setScaleTransformation(scale);
                                 portal.setFuseView(true);
                                 portal.setTeleportChangesScale(false);
                                 McHelper.spawnServerEntity(portal);
                                 Portal reverse = PortalAPI.createReversePortal(portal);
                                 McHelper.spawnServerEntity(reverse);
                                 return 0;
                              }))
                        )
                  )
            )
      );
      builder.then(
         CommandManager.literal("create_connected_rooms")
            .then(
               CommandManager.literal("roomSize")
                  .then(
                     CommandManager.argument("roomSize", BlockPosArgumentType.blockPos())
                        .then(
                           CommandManager.literal("roomNumber")
                              .then(
                                 CommandManager.argument("roomNumber", IntegerArgumentType.integer(2, 500))
                                    .executes(
                                       context -> {
                                          BlockPos roomSize = BlockPosArgumentType.getValidBlockPos(context, "roomSize");
                                          int roomNumber = IntegerArgumentType.getInteger(context, "roomNumber");
                                          createConnectedRooms(
                                             ((ServerCommandSource)context.getSource()).getWorld(),
                                             BlockPos.ofFloored(((ServerCommandSource)context.getSource()).getPosition()),
                                             roomSize,
                                             roomNumber,
                                             text -> ((ServerCommandSource)context.getSource()).sendFeedback(() -> text, false)
                                          );
                                          return 0;
                                       }
                                    )
                              )
                        )
                  )
            )
      );
      builder.then(
         CommandManager.literal("create_cube_surface_unwrapping")
            .then(
               CommandManager.argument("boxL", BlockPosArgumentType.blockPos())
                  .then(
                     CommandManager.argument("boxH", BlockPosArgumentType.blockPos())
                        .then(CommandManager.argument("length", IntegerArgumentType.integer(1, 100)).executes(context -> {
                           BlockPos boxL = BlockPosArgumentType.getValidBlockPos(context, "boxL");
                           BlockPos boxH = BlockPosArgumentType.getValidBlockPos(context, "boxH");
                           IntBox box = new IntBox(boxL, boxH);
                           int length = IntegerArgumentType.getInteger(context, "length");
                           BlockPos size = box.getSize();
                           createCubeSurfaceUnwrapping(((ServerCommandSource)context.getSource()).getWorld(), box.toRealNumberBox(), length);
                           return 0;
                        }))
                  )
            )
      );
      builder.then(
         CommandManager.literal("adjust_rotation_to_connect")
            .then(
               CommandManager.argument("portal1", EntityArgumentType.entity())
                  .then(CommandManager.argument("portal2", EntityArgumentType.entity()).executes(context -> {
                     Entity e1 = EntityArgumentType.getEntity(context, "portal1");
                     Entity e2 = EntityArgumentType.getEntity(context, "portal2");
                     if (!(e1 instanceof Portal)) {
                        ((ServerCommandSource)context.getSource()).sendError(Text.literal("portal1 is not a portal entity"));
                        return 0;
                     } else if (!(e2 instanceof Portal)) {
                        ((ServerCommandSource)context.getSource()).sendError(Text.literal("portal2 is not a portal entity"));
                        return 0;
                     } else {
                        Portal portal1 = (Portal)e1;
                        Portal portal2 = (Portal)e2;
                        portal1.setDestination(portal2.getOriginPos());
                        portal2.setDestination(portal1.getOriginPos());
                        PortalManipulation.adjustRotationToConnect(portal1, portal2);
                        portal1.reloadAndSyncToClient();
                        portal2.reloadAndSyncToClient();
                        return 0;
                     }
                  }))
            )
      );
      builder.then(
         CommandManager.literal("rotate_portals_around")
            .then(
               CommandManager.argument("portals", EntityArgumentType.entities())
                  .then(
                     CommandManager.argument("origin", Vec3ArgumentType.vec3())
                        .then(
                           CommandManager.argument("axis", Vec3ArgumentType.vec3(false))
                              .then(CommandManager.argument("angle", DoubleArgumentType.doubleArg()).executes(context -> {
                                 Collection<? extends Entity> portals = EntityArgumentType.getEntities(context, "portals");
                                 Vec3d origin = Vec3ArgumentType.getVec3(context, "origin");
                                 Vec3d axis = Vec3ArgumentType.getVec3(context, "axis").normalize();
                                 double angle = DoubleArgumentType.getDouble(context, "angle");
                                 DQuaternion quaternion = DQuaternion.rotationByDegrees(axis, angle);

                                 for (Entity entity : portals) {
                                    if (entity instanceof Portal portal) {
                                       Vec3d offset = portal.getOriginPos().subtract(origin);
                                       Vec3d offsetRotated = quaternion.rotate(offset);
                                       portal.setOriginPos(offsetRotated.add(origin));
                                       portal.setAxisW(quaternion.rotate(portal.getAxisW()));
                                       portal.setAxisH(quaternion.rotate(portal.getAxisH()));
                                       portal.setRotationTransformationD(portal.getRotationD().hamiltonProduct(quaternion.getConjugated()));
                                    } else {
                                       ((ServerCommandSource)context.getSource()).sendError(Text.literal("the entity is not a portal"));
                                    }
                                 }

                                 for (Entity entityx : portals) {
                                    if (entityx instanceof Portal portal) {
                                       reloadPortal(portal);
                                    }
                                 }

                                 return 0;
                              }))
                        )
                  )
            )
      );
      builder.then(
         CommandManager.literal("scale_portals_relative_to_point")
            .then(
               CommandManager.argument("portals", EntityArgumentType.entities())
                  .then(
                     CommandManager.argument("origin", Vec3ArgumentType.vec3())
                        .then(CommandManager.argument("scale", DoubleArgumentType.doubleArg()).executes(context -> {
                           Collection<? extends Entity> portals = EntityArgumentType.getEntities(context, "portals");
                           Vec3d origin = Vec3ArgumentType.getVec3(context, "origin");
                           double scale = DoubleArgumentType.getDouble(context, "scale");
                           List<Portal> portalsToReload = new ArrayList<>();

                           for (Entity entity : portals) {
                              if (entity instanceof Portal portal) {
                                 Vec3d offset = portal.getOriginPos().subtract(origin);
                                 Vec3d newOrigin = offset.multiply(scale).add(origin);
                                 PortalExtension extension = PortalExtension.get(portal);
                                 if (extension.reversePortal != null) {
                                    extension.reversePortal.setDestination(newOrigin);
                                    extension.reversePortal.setScaling(extension.reversePortal.getScaling() / scale);
                                    portalsToReload.add(extension.reversePortal);
                                 } else {
                                    portal.setOriginPos(newOrigin);
                                    portal.setWidth(portal.getPortalWidth() * scale);
                                    portal.setHeight(portal.getPortalHeight() * scale);
                                    portalsToReload.add(portal);
                                 }
                              } else {
                                 ((ServerCommandSource)context.getSource()).sendError(Text.literal("the entity is not a portal"));
                              }
                           }

                           for (Portal portalx : portalsToReload) {
                              reloadPortal(portalx);
                           }

                           return 0;
                        }))
                  )
            )
      );
      builder.then(
         CommandManager.literal("create_diagonal_portal")
            .then(
               CommandManager.argument("fromPos", Vec3ArgumentType.vec3(false))
                  .then(
                     CommandManager.argument("toPos", Vec3ArgumentType.vec3(false))
                        .then(CommandManager.argument("axis", AxisArgumentType.instance).executes(context -> {
                           Vec3d fromPos = Vec3ArgumentType.getVec3(context, "fromPos");
                           Vec3d toPos = Vec3ArgumentType.getVec3(context, "toPos");
                           Axis axis = AxisArgumentType.getAxis(context, "axis");
                           Vec3d axisVec = Vec3d.of(Direction.from(axis, AxisDirection.POSITIVE).getVector());
                           Vec3d delta = toPos.subtract(fromPos);
                           Vec3d vecAlongAxis = axisVec.multiply(delta.dotProduct(axisVec));
                           Vec3d vecNotAlongAxis = delta.subtract(vecAlongAxis);
                           Vec3d center = fromPos.add(toPos).multiply(0.5);
                           Portal portal = (Portal)Portal.ENTITY_TYPE.create(((ServerCommandSource)context.getSource()).getWorld(), SpawnReason.COMMAND);

                           assert portal != null;

                           portal.setOriginPos(center);
                           portal.setOrientation(vecAlongAxis.normalize(), vecNotAlongAxis.normalize());
                           portal.setWidth(vecAlongAxis.length());
                           portal.setHeight(vecNotAlongAxis.length());
                           portal.setDestination(center.add(0.0, 10.0, 0.0));
                           portal.setDestinationDimension(((ServerCommandSource)context.getSource()).getWorld().getRegistryKey());
                           if (!(portal.getPortalWidth() > 64.0) && !(portal.getPortalHeight() > 64.0)) {
                              McHelper.spawnServerEntity(portal);
                              Portal flippedPortal = PortalManipulation.createFlippedPortal(portal, Portal.ENTITY_TYPE);
                              McHelper.spawnServerEntity(flippedPortal);
                              return 0;
                           } else {
                              ((ServerCommandSource)context.getSource()).sendError(Text.literal("portal size is too large"));
                              return 0;
                           }
                        }))
                  )
            )
      );
      builder.then(
         ((LiteralArgumentBuilder)CommandManager.literal("dimension_stack")
               .requires(commandSource -> commandSource.getPermissions().hasPermission(DefaultPermissions.GAMEMASTERS)))
            .executes(context -> {
               ServerPlayerEntity player = ((ServerCommandSource)context.getSource()).getPlayerOrThrow();
               DimStackManagement.onDimensionStackCommandExecute(player);
               return 0;
            })
      );
      builder.then(CommandManager.literal("wiki").executes(context -> {
         ((ServerCommandSource)context.getSource()).getPlayerOrThrow().sendMessage(McHelper.getLinkText("https://qouteall.fun/immptl/wiki/Commands-Reference"));
         return 0;
      }));
      builder.then(
         ((LiteralArgumentBuilder)CommandManager.literal("create_command_stick")
               .requires(serverCommandSource -> serverCommandSource.getPermissions().hasPermission(DefaultPermissions.GAMEMASTERS)))
            .then(CommandManager.argument("command", SubCommandArgumentType.instance).executes(context -> {
               createCommandStickCommandSignal.emit(
                  ((ServerCommandSource)context.getSource()).getPlayerOrThrow(), SubCommandArgumentType.get(context, "command")
               );
               return 0;
            }))
      );
   }

   private static void createCubeSurfaceUnwrapping(ServerWorld world, Box box, double length) {
      Vec3d boxSize = Helper.getBoxSize(box);
      Vec3d boxCenter = box.getCenter();

      for (Direction face : Direction.values()) {
         Vec3d facingVec = Vec3d.of(face.getVector());

         for (Direction sideDirection : Helper.getAnotherFourDirections(face.getAxis())) {
            Vec3d sideDirectionVec = Vec3d.of(sideDirection.getVector());
            Vec3d edgeCenter = facingVec.multiply(0.5).add(sideDirectionVec.multiply(0.5)).multiply(boxSize).add(boxCenter);
            Vec3d portalOrigin = edgeCenter.add(facingVec.multiply(length / 2.0));
            Vec3d portalDestination = edgeCenter.add(sideDirectionVec.multiply(length / 2.0));
            Vec3d portalWAxis = sideDirectionVec.crossProduct(facingVec);
            double width = Math.abs(boxSize.dotProduct(portalWAxis));
            DQuaternion rotationTransform = DQuaternion.getRotationBetween(facingVec, sideDirectionVec);
            Portal portal = (Portal)Portal.ENTITY_TYPE.create(world, SpawnReason.COMMAND);
            portal.setOriginPos(portalOrigin);
            portal.setDestination(portalDestination);
            portal.setDestinationDimension(world.getRegistryKey());
            portal.setOrientationAndSize(portalWAxis, facingVec, width, length);
            portal.setRotationTransformationD(rotationTransform);
            portal.setTeleportChangesGravity(true);
            portal.portalTag = "imm_ptl:cube_surface_unwrapping";
            McHelper.spawnServerEntity(portal);
         }
      }
   }

   private static void createConnectedRooms(ServerWorld world, BlockPos startingPos, BlockPos roomSize, int roomNumber, Consumer<Text> feedbackSender) {
      BlockPos roomAreaSize = roomSize.add(2, 2, 2);
      List<IntBox> roomAreaList = new ArrayList<>();
      Helper.SimpleBox<BlockPos> currentSearchingCenter = new Helper.SimpleBox<>(startingPos);
      ServerTaskList.of(world.getServer())
         .addTask(MyTaskList.chainTask(MyTaskList.repeat(roomNumber, () -> MyTaskList.withDelay(20, MyTaskList.oneShotTask(() -> {
            currentSearchingCenter.obj = currentSearchingCenter.obj.add(getRandomShift(20));
            IntBox airCube = NetherPortalMatcher.findCubeAirAreaAtAnywhere(roomAreaSize.add(6, 6, 6), world, currentSearchingCenter.obj, 128);
            if (airCube == null) {
               feedbackSender.accept(Text.literal("Cannot find space for placing room"));
            } else {
               airCube = airCube.getSubBoxInCenter(roomAreaSize);
               fillRoomFrame(world, airCube, getRandomBlock());
               roomAreaList.add(airCube);
            }
         }))), MyTaskList.oneShotTask(() -> {
            Stream<IntBox> roomsStream = Stream.concat(roomAreaList.stream(), Stream.of(roomAreaList.get(0)));
            Helper.<IntBox, Pair>wrapAdjacentAndMap(roomsStream, Pair::new).forEach(pair -> {
               IntBox room1Area = (IntBox)pair.getFirst();
               IntBox room2Area = (IntBox)pair.getSecond();
               IntBox room1 = room1Area.getAdjusted(1, 1, 1, -1, -1, -1);
               IntBox room2 = room2Area.getAdjusted(1, 1, 1, -1, -1, -1);
               Portal portal = (Portal)Portal.ENTITY_TYPE.create(world, SpawnReason.COMMAND);
               Validate.notNull(portal);
               portal.setOriginPos(room1.getCenterVec().add(roomSize.getX() / 4.0, 0.0, 0.0));
               portal.setDestinationDimension(world.getRegistryKey());
               portal.setDestination(room2.getCenterVec().add(roomSize.getX() / 4.0, 0.0, 0.0));
               portal.setOrientationAndSize(new Vec3d(1.0, 0.0, 0.0), new Vec3d(0.0, 1.0, 0.0), roomSize.getX() / 2.0, roomSize.getY());
               portal.portalTag = "imm_ptl:room_connection";
               McHelper.spawnServerEntity(portal);
               Portal reversePortal = PortalAPI.createReversePortal(portal);
               McHelper.spawnServerEntity(reversePortal);
            });
            feedbackSender.accept(Text.literal("finished"));
         })));
   }

   private static BlockState getRandomBlock() {
      BlockState state;
      do {
         Block block = (Block)((Reference)Registries.BLOCK.getRandom(Random.create()).get()).value();
         state = block.getDefaultState();
      } while (!state.isSolid());

      return state;
   }

   private static void fillRoomFrame(ServerWorld world, IntBox roomArea, BlockState blockState) {
      for (Direction direction : Direction.values()) {
         IntBox surface = roomArea.getSurfaceLayer(direction);
         surface.fastStream().forEach(blockPos -> world.setBlockState(blockPos, blockState));
      }
   }

   private static void invokeOldCreateScaledViewCommand(
      CommandContext<ServerCommandSource> context,
      boolean teleportChangesScale,
      boolean outerFuseView,
      boolean outerRenderingMergable,
      boolean innerRenderingMergable,
      boolean isBiWay
   ) throws CommandSyntaxException {
      BlockPos bp1 = BlockPosArgumentType.getValidBlockPos(context, "p1");
      BlockPos bp2 = BlockPosArgumentType.getValidBlockPos(context, "p2");
      IntBox intBox = new IntBox(bp1, bp2);
      Entity placeTargetEntity = EntityArgumentType.getEntity(context, "placeTargetEntity");
      ServerWorld boxWorld = (ServerWorld)placeTargetEntity.getEntityWorld();
      Vec3d boxBottomCenter = placeTargetEntity.getEntityPos();
      Box area = intBox.toRealNumberBox();
      ServerWorld areaWorld = ((ServerCommandSource)context.getSource()).getWorld();
      double scale = DoubleArgumentType.getDouble(context, "scale");
      PortalManipulation.createScaledBoxView(
         areaWorld, area, boxWorld, boxBottomCenter, scale, isBiWay, teleportChangesScale, outerFuseView, outerRenderingMergable, innerRenderingMergable, false
      );
   }

   private static void addSmallWorldWrappingPortals(Box box, ServerWorld world, boolean isInward) {
      for (Direction direction : Direction.values()) {
         Portal portal = (Portal)Portal.ENTITY_TYPE.create(world, SpawnReason.COMMAND);
         WorldWrappingPortal.initWrappingPortal(world, box, direction, isInward, portal);
         McHelper.spawnServerEntity(portal);
      }
   }

   private static int processPortalArgumentedCBCommand(
      CommandContext<ServerCommandSource> context, PortalCommand.PortalConsumerThrowsCommandSyntaxException invoker
   ) throws CommandSyntaxException {
      for (Entity portalEntity : EntityArgumentType.getEntities(context, "portal")) {
         if (portalEntity instanceof Portal portal) {
            invoker.accept(portal);
         } else {
            sendMessage(context, "The target should be portal");
         }
      }

      return 0;
   }

   private static void sendEditBiWayPortalWarning(CommandContext<ServerCommandSource> context, Portal portal) {
      if (!PortalExtension.get(portal).bindCluster && PortalManipulation.findReversePortal(portal) != null) {
         sendMessage(
            context,
            "You are editing a bi-way portal. It's recommended to enable bindCluster or you will get unlinked portal entities. Use command /portal set_portal_nbt {bindCluster:true}"
         );
      }

      if (portal instanceof BreakablePortalEntity breakablePortalEntity && !breakablePortalEntity.unbreakable) {
         sendMessage(
            context,
            "You are editing a breakable portal. It may break if its state is abnormal. It's recommended to make it unbreakable by /portal set_portal_nbt {unbreakable:true}"
         );
      }
   }

   private static void invokeCompleteBiWayBiFacedPortal(CommandContext<ServerCommandSource> context, Portal portal) {
      PortalExtension.get(portal).bindCluster = true;
      reloadPortal(portal);
      PortalManipulation.completeBiWayBiFacedPortal(
         portal, p -> sendMessage(context, "Removed " + p), p -> sendMessage(context, "Added " + p), Portal.ENTITY_TYPE
      );
   }

   private static void invokeCompleteBiFacedPortal(CommandContext<ServerCommandSource> context, Portal portal) {
      PortalManipulation.removeOverlappedPortals(
         (ServerWorld)portal.getEntityWorld(),
         portal.getOriginPos(),
         portal.getNormal().multiply(-1.0),
         p -> Objects.equals(portal.specificPlayerId, p.specificPlayerId),
         p -> sendMessage(context, "Removed " + p)
      );
      PortalExtension.get(portal).bindCluster = true;
      reloadPortal(portal);
      Portal result = PortalManipulation.completeBiFacedPortal(portal, Portal.ENTITY_TYPE);
      sendMessage(context, "Added " + result);
   }

   private static void invokeCompleteBiWayPortal(CommandContext<ServerCommandSource> context, Portal portal) {
      PortalManipulation.removeOverlappedPortals(
         MiscHelper.getServer().getWorld(portal.getDestDim()),
         portal.getDestPos(),
         portal.transformLocalVecNonScale(portal.getNormal().multiply(-1.0)),
         p -> Objects.equals(portal.specificPlayerId, p.specificPlayerId),
         p -> sendMessage(context, "Removed " + p)
      );
      PortalExtension.get(portal).bindCluster = true;
      reloadPortal(portal);
      Portal result = PortalManipulation.completeBiWayPortal(portal, Portal.ENTITY_TYPE);
      sendMessage(context, "Added " + result);
   }

   private static void removeSpecificAccessor(CommandContext<ServerCommandSource> context, Portal portal) {
      portal.specificPlayerId = null;
      sendMessage(context, "This portal can be accessed by all players now");
      sendMessage(context, portal.toString());
   }

   private static void setSpecificAccessor(CommandContext<ServerCommandSource> context, Portal portal, Entity player) {
      portal.specificPlayerId = player.getUuid();
      sendMessage(context, "This portal can only be accessed by " + player.getName().getContent() + " now");
      sendMessage(context, portal.toString());
   }

   private static void removeMultidestEntry(CommandContext<ServerCommandSource> context, Portal pointedPortal, ServerPlayerEntity player) {
      PortalManipulation.getPortalCluster(pointedPortal.getEntityWorld(), pointedPortal.getOriginPos(), pointedPortal.getNormal(), p -> true)
         .stream()
         .filter(portal -> player.getUuid().equals(portal.specificPlayerId) || portal.specificPlayerId == null)
         .forEach(portal -> {
            PortalManipulation.removeConnectedPortals(portal, p -> sendMessage(context, "removed " + p.toString()));
            sendMessage(context, "removed " + portal.toString());
            portal.remove(RemovalReason.KILLED);
         });
   }

   private static void setMultidestEntry(
      CommandContext<ServerCommandSource> context,
      Portal pointedPortal,
      ServerPlayerEntity player,
      RegistryKey<World> dimension,
      Vec3d destination,
      boolean biFaced,
      boolean biWay
   ) {
      Portal newPortal = PortalManipulation.copyPortal(pointedPortal, Portal.ENTITY_TYPE);
      removeMultidestEntry(context, pointedPortal, player);
      newPortal.setDestDim(dimension);
      newPortal.setDestination(destination);
      newPortal.specificPlayerId = player.getUuid();
      McHelper.spawnServerEntity(newPortal);
      configureBiWayBiFaced(newPortal, biWay, biFaced);
   }

   private static void configureBiWayBiFaced(Portal newPortal, boolean biWay, boolean biFaced) {
      if (biFaced && biWay) {
         PortalManipulation.completeBiWayBiFacedPortal(newPortal, p -> {}, p -> {}, Portal.ENTITY_TYPE);
      } else if (biFaced) {
         PortalManipulation.completeBiFacedPortal(newPortal, Portal.ENTITY_TYPE);
      } else if (biWay) {
         PortalManipulation.completeBiWayPortal(newPortal, Portal.ENTITY_TYPE);
      }
   }

   public static void sendPortalInfo(CommandContext<ServerCommandSource> context, Portal portal) {
      sendPortalInfo((Consumer<Text>)(c -> {
         sendMessage(context, c);
         Helper.log(c.getString());
      }), portal);
   }

   public static void sendPortalInfo(Consumer<Text> func, Portal portal) {
      func.accept(McHelper.compoundTagToTextSorted(McHelper.saveEntityWithoutIdToNbt(portal), " ", 0));
      func.accept(Text.literal(portal.toString()));
      func.accept(Text.literal(String.format("Orientation: %s", PortalAPI.getPortalOrientationQuaternion(portal))));
      if (portal.getRotation() != null) {
         func.accept(Text.literal(String.format("Rotating Transformation: %s", portal.getRotation())));
      }
   }

   public static void sendMessage(CommandContext<ServerCommandSource> context, Text component) {
      ((ServerCommandSource)context.getSource()).sendFeedback(() -> component, false);
   }

   public static void sendMessage(CommandContext<ServerCommandSource> context, String message) {
      sendMessage(context, Text.literal(message));
   }

   private static Text getMakePortalSuccess(Portal portal) {
      return Text.translatable(
         "imm_ptl.command.make_portal.success",
         new Object[]{
            Double.toString(portal.getPortalWidth()),
            Double.toString(portal.getPortalHeight()),
            McHelper.dimensionTypeId(portal.getEntityWorld().getRegistryKey()).toString(),
            portal.getOriginPos().toString(),
            McHelper.dimensionTypeId(portal.getDestDim()).toString(),
            portal.getDestPos().toString()
         }
      );
   }

   private static int placePortalAbsolute(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
      double width = DoubleArgumentType.getDouble(context, "width");
      double height = DoubleArgumentType.getDouble(context, "height");
      RegistryKey<World> to = DimensionArgumentType.getDimensionArgument(context, "to").getRegistryKey();
      Vec3d dest = Vec3ArgumentType.getVec3(context, "dest");
      Portal portal = PortalManipulation.placePortal(width, height, ((ServerCommandSource)context.getSource()).getPlayerOrThrow());
      if (portal == null) {
         return 0;
      } else {
         portal.setDestDim(to);
         portal.setDestination(dest);
         McHelper.spawnServerEntity(portal);
         ((ServerCommandSource)context.getSource()).sendFeedback(() -> getMakePortalSuccess(portal), true);
         return 1;
      }
   }

   private static int placePortalShift(CommandContext<ServerCommandSource> context) throws CommandSyntaxException {
      double width = DoubleArgumentType.getDouble(context, "width");
      double height = DoubleArgumentType.getDouble(context, "height");
      RegistryKey<World> to = DimensionArgumentType.getDimensionArgument(context, "to").getRegistryKey();
      double dist = DoubleArgumentType.getDouble(context, "dist");
      Portal portal = PortalManipulation.placePortal(width, height, ((ServerCommandSource)context.getSource()).getPlayerOrThrow());
      if (portal == null) {
         return 0;
      } else {
         portal.setDestDim(to);
         portal.setDestination(portal.getOriginPos().add(portal.getAxisW().crossProduct(portal.getAxisH()).multiply(-dist)));
         McHelper.spawnServerEntity(portal);
         ((ServerCommandSource)context.getSource()).sendFeedback(() -> getMakePortalSuccess(portal), true);
         return 1;
      }
   }

   public static int teleport(Collection<? extends Entity> entities, RegistryKey<World> targetDim, Vec3d targetPos) {
      ServerWorld targetWorld = MiscHelper.getServer().getWorld(targetDim);
      int numTeleported = 0;

      for (Entity entity : entities) {
         ServerTeleportationManager.teleportEntityGeneral(entity, targetPos, targetWorld);
         numTeleported++;
      }

      return numTeleported;
   }

   public static BlockPos getRandomShift(int len) {
      java.util.Random rand = new java.util.Random();
      return BlockPos.ofFloored((rand.nextDouble() * 2.0 - 1.0) * len, (rand.nextDouble() * 2.0 - 1.0) * len, (rand.nextDouble() * 2.0 - 1.0) * len);
   }

   public static int processPortalTargetedCommand(
      CommandContext<ServerCommandSource> context, PortalCommand.PortalConsumerThrowsCommandSyntaxException processCommand
   ) throws CommandSyntaxException {
      ServerCommandSource source = (ServerCommandSource)context.getSource();
      Entity entity = source.getEntity();
      if (entity instanceof ServerPlayerEntity player) {
         Portal portal = getPlayerPointingPortal(player, false);
         if (portal == null) {
            source.sendFeedback(() -> Text.literal("You are not pointing to any non-global portal. (This command cannot process global portals)"), false);
            return 0;
         }

         processCommand.accept(portal);
      } else if (entity instanceof Portal) {
         processCommand.accept((Portal)entity);
      } else {
         source.sendFeedback(() -> Text.literal("The command executor should be either a player or a portal entity"), false);
      }

      return 0;
   }

   @Deprecated
   public static Portal getPlayerPointingPortal(ServerPlayerEntity player, boolean includeGlobalPortal) {
      return getPlayerPointingPortalRaw(player, 1.0F, 100.0, includeGlobalPortal).<Portal>map(Pair::getFirst).orElse(null);
   }

   @Deprecated
   public static Optional<Pair<Portal, Vec3d>> getPlayerPointingPortalRaw(
      PlayerEntity player, float partialTick, double maxDistance, boolean includeGlobalPortal
   ) {
      return PortalUtils.raytracePortalFromEntityView(player, partialTick, maxDistance, includeGlobalPortal, p -> true)
         .map(p -> Pair.of((Portal)p.getFirst(), ((RayTraceResult)p.getSecond()).hitPos()));
   }

   public static Optional<Pair<Portal, Vec3d>> raytracePortals(World world, Vec3d from, Vec3d to, boolean includeGlobalPortal) {
      return PortalUtils.raytracePortals(world, from, to, includeGlobalPortal, p -> true)
         .map(p -> Pair.of((Portal)p.getFirst(), ((RayTraceResult)p.getSecond()).hitPos()));
   }

   private static Vec3d getRightVec(Entity entity) {
      float yaw = entity.getYaw() + 90.0F;
      float radians = -yaw * (float) (Math.PI / 180.0);
      return new Vec3d(Math.sin(radians), 0.0, Math.cos(radians));
   }

   private static void updateEntityFullNbt(Entity entity, NbtCompound nbt) {
      nbt.remove("id");
      nbt.remove("UUID");
      NbtCompound result = McHelper.saveEntityWithoutIdToNbt(entity);
      result.copyFrom(nbt);
      McHelper.loadEntityFromNbt(entity, result);
   }

   private static void registerEulerCommands(LiteralArgumentBuilder<ServerCommandSource> builder) {
      builder.then(
         ((LiteralArgumentBuilder)CommandManager.literal("make_portal").requires(s -> s.getPermissions().hasPermission(DefaultPermissions.GAMEMASTERS)))
            .then(
               CommandManager.argument("origin", Vec3ArgumentType.vec3(false))
                  .then(
                     CommandManager.argument("rotation", RotationArgumentType.rotation())
                        .then(
                           CommandManager.argument("width", DoubleArgumentType.doubleArg(0.0))
                              .then(
                                 CommandManager.argument("height", DoubleArgumentType.doubleArg(0.0))
                                    .then(
                                       CommandManager.argument("scale", DoubleArgumentType.doubleArg())
                                          .then(CommandManager.argument("nbt", NbtCompoundArgumentType.nbtCompound()).executes(context -> {
                                             Vec3d origin = Vec3ArgumentType.getVec3(context, "origin");
                                             Vec2f rotation = RotationArgumentType.getRotation(context, "rotation")
                                                .getRotation((ServerCommandSource)context.getSource());
                                             double width = DoubleArgumentType.getDouble(context, "width");
                                             double height = DoubleArgumentType.getDouble(context, "height");
                                             double scale = DoubleArgumentType.getDouble(context, "scale");
                                             NbtCompound nbt = NbtCompoundArgumentType.getNbtCompound(context, "nbt");
                                             ServerWorld world = ((ServerCommandSource)context.getSource()).getWorld();
                                             Portal portal = (Portal)Portal.ENTITY_TYPE.create(world, SpawnReason.COMMAND);
                                             Validate.notNull(portal);
                                             portal.setOriginPos(origin);
                                             portal.setDestination(origin.add(0.0, 10.0, 0.0));
                                             portal.setDestinationDimension(world.getRegistryKey());
                                             DQuaternion orientationRotation = DQuaternion.fromEulerAngle(new Vec3d(rotation.x, rotation.y, 0.0));
                                             portal.setOrientationRotation(orientationRotation);
                                             portal.setWidth(width);
                                             portal.setHeight(height);
                                             portal.setScaleTransformation(scale);
                                             updateEntityFullNbt(portal, nbt);
                                             McHelper.spawnServerEntity(portal);
                                             return 0;
                                          }))
                                    )
                              )
                        )
                  )
            )
      );
      builder.then(
         CommandManager.literal("set_orientation")
            .then(CommandManager.argument("rotation", RotationArgumentType.rotation()).executes(context -> processPortalTargetedCommand(context, portal -> {
               Vec2f rotation = RotationArgumentType.getRotation(context, "rotation").getRotation((ServerCommandSource)context.getSource());
               DQuaternion orientationRotation = DQuaternion.fromEulerAngle(new Vec3d(rotation.x, rotation.y, 0.0));
               portal.setOrientationRotation(orientationRotation);
               reloadPortal(portal);
            })))
      );
      builder.then(
         ((LiteralArgumentBuilder)CommandManager.literal("set_this_side").requires(s -> s.getPermissions().hasPermission(DefaultPermissions.GAMEMASTERS)))
            .then(
               CommandManager.argument("origin", Vec3ArgumentType.vec3(false))
                  .then(
                     CommandManager.argument("rotation", RotationArgumentType.rotation())
                        .then(
                           CommandManager.argument("width", DoubleArgumentType.doubleArg(0.0))
                              .then(
                                 CommandManager.argument("height", DoubleArgumentType.doubleArg(0.0))
                                    .then(
                                       CommandManager.argument("nbt", NbtCompoundArgumentType.nbtCompound())
                                          .executes(context -> processPortalTargetedCommand(context, portal -> {
                                             Vec3d origin = Vec3ArgumentType.getVec3(context, "origin");
                                             Vec2f rotation = RotationArgumentType.getRotation(context, "rotation")
                                                .getRotation((ServerCommandSource)context.getSource());
                                             double width = DoubleArgumentType.getDouble(context, "width");
                                             double height = DoubleArgumentType.getDouble(context, "height");
                                             NbtCompound nbt = NbtCompoundArgumentType.getNbtCompound(context, "nbt");
                                             portal.setOriginPos(origin);
                                             DQuaternion orientationRotation = DQuaternion.fromEulerAngle(new Vec3d(rotation.x, rotation.y, 0.0));
                                             portal.setOrientationRotation(orientationRotation);
                                             portal.setWidth(width);
                                             portal.setHeight(height);
                                             updateEntityFullNbt(portal, nbt);
                                             reloadPortal(portal);
                                          }))
                                    )
                              )
                        )
                  )
            )
      );
      builder.then(
         CommandManager.literal("set_other_side")
            .then(
               CommandManager.argument("destination", Vec3ArgumentType.vec3(false))
                  .then(
                     CommandManager.argument("rotation", RotationArgumentType.rotation()).executes(context -> processPortalTargetedCommand(context, portal -> {
                        Vec3d destination = Vec3ArgumentType.getVec3(context, "destination");
                        Vec2f rotation = RotationArgumentType.getRotation(context, "rotation").getRotation((ServerCommandSource)context.getSource());
                        portal.setDestination(destination);
                        portal.setDestinationDimension(((ServerCommandSource)context.getSource()).getWorld().getRegistryKey());
                        DQuaternion otherSideRotation = DQuaternion.fromEulerAngle(new Vec3d(rotation.x, rotation.y, 0.0));
                        PortalState portalState = portal.getPortalState();
                        UnilateralPortalState thisSide = UnilateralPortalState.extractThisSide(portalState);
                        UnilateralPortalState otherSide = UnilateralPortalState.extractOtherSide(portalState);
                        UnilateralPortalState newOtherSide = new UnilateralPortalState.Builder().from(otherSide).orientation(otherSideRotation).build();
                        PortalState newPortalState = UnilateralPortalState.combine(thisSide, newOtherSide);
                        portal.setPortalState(newPortalState);
                        reloadPortal(portal);
                     }))
                  )
            )
      );
   }

   private static void invokeTurnIntoFakeEnterableMirror(CommandContext<ServerCommandSource> context, Portal portal) {
      if (portal instanceof Mirror) {
         ((ServerCommandSource)context.getSource()).sendError(Text.literal("This command targets non-mirror portals"));
      } else {
         PortalState portalState = portal.getPortalState();

         assert portalState != null;

         UnilateralPortalState thisSideState = UnilateralPortalState.extractThisSide(portalState);
         UnilateralPortalState otherSideState = UnilateralPortalState.extractOtherSide(portalState);
         World fromWorld = portal.getEntityWorld();
         World toWorld = portal.getDestinationWorld();
         PortalShape specialShape = portal.getPortalShape();
         DQuaternion spacialRotation = portal.getRotationD();
         PortalManipulation.removeConnectedPortals(portal, p -> {});
         portal.remove(RemovalReason.KILLED);
         Mirror thisSideMirror = (Mirror)Mirror.ENTITY_TYPE.create(fromWorld, SpawnReason.COMMAND);

         assert thisSideMirror != null;

         thisSideMirror.setDestDim(thisSideMirror.getEntityWorld().getRegistryKey());
         thisSideMirror.setOriginPos(thisSideState.position());
         thisSideMirror.setOrientationRotation(thisSideState.orientation());
         thisSideMirror.setDestination(thisSideState.position());
         thisSideMirror.setWidth(thisSideState.width());
         thisSideMirror.setHeight(thisSideState.height());
         thisSideMirror.setPortalShape(specialShape);
         thisSideMirror.setRotationTransformationForMirror(spacialRotation);
         Mirror otherSideMirror = (Mirror)Mirror.ENTITY_TYPE.create(toWorld, SpawnReason.COMMAND);

         assert otherSideMirror != null;

         otherSideMirror.setDestDim(otherSideMirror.getEntityWorld().getRegistryKey());
         otherSideMirror.setOriginPos(otherSideState.position());
         otherSideMirror.setOrientationRotation(otherSideState.orientation());
         otherSideMirror.setDestination(otherSideState.position());
         otherSideMirror.setWidth(otherSideState.width());
         otherSideMirror.setHeight(otherSideState.height());
         otherSideMirror.setPortalShape(specialShape.getFlipped());
         otherSideMirror.setRotationTransformationForMirror(spacialRotation.getConjugated());
         McHelper.spawnServerEntity(thisSideMirror);
         McHelper.spawnServerEntity(otherSideMirror);
         Portal invisiblePortal = (Portal)Portal.ENTITY_TYPE.create(fromWorld, SpawnReason.COMMAND);

         assert invisiblePortal != null;

         invisiblePortal.setDestDim(toWorld.getRegistryKey());
         invisiblePortal.setPortalState(UnilateralPortalState.combine(thisSideState, otherSideState));
         invisiblePortal.setPortalShape(specialShape);
         invisiblePortal.setIsVisible(false);
         invisiblePortal.setOriginPos(invisiblePortal.getOriginPos().add(portal.getNormal().multiply(0.001)));
         invisiblePortal.setDestination(invisiblePortal.getDestPos().add(portal.getContentDirection().multiply(0.001)));
         Portal reverseInvisiblePortal = PortalManipulation.createReversePortal(invisiblePortal, Portal.ENTITY_TYPE);
         McHelper.spawnServerEntity(invisiblePortal);
         McHelper.spawnServerEntity(reverseInvisiblePortal);
         ((ServerCommandSource)context.getSource())
            .sendFeedback(
               () -> Text.literal(
                  "The portal has been turned into a fake enterable mirror. You need to manually make the two sides symmetric. Two invisible portal entities are generated. If you want to remove that, don't forget to remove the invisible portals."
               ),
               false
            );
      }
   }

   private static void invokeSculpt(CommandContext<ServerCommandSource> context, Portal portal, boolean adjustPortalBounds) {
      MinecraftServer server = ((ServerCommandSource)context.getSource()).getServer();
      ServerPlayerEntity player = ((ServerCommandSource)context.getSource()).getPlayer();
      ObjectArrayList<Box> boxes = gatherCollisionBoxesTouching(portal);
      if (boxes.size() > 40000) {
         ((ServerCommandSource)context.getSource()).sendError(Text.literal("Too many collision boxes to sculpt"));
      } else {
         Mesh2D mesh2D;
         if (portal.getPortalShape() instanceof SpecialFlatPortalShape s) {
            mesh2D = s.mesh.copy();
         } else {
            mesh2D = Mesh2D.createNewFullQuadMesh();
         }

         double halfWidth = portal.getPortalWidth() / 2.0;
         double halfHeight = portal.getPortalHeight() / 2.0;
         Vec3d axisW = portal.getAxisW();
         Vec3d axisH = portal.getAxisH();
         Plane plane = new Plane(portal.getOriginPos(), portal.getNormal());
         double areaBefore = mesh2D.getArea() * halfWidth * halfHeight;
         CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            int count = 0;
            ObjectListIterator var10x = boxes.iterator();

            while (var10x.hasNext()) {
               Box box = (Box)var10x.next();
               ObjectArrayList<Vec2d> polygonVertexes = GeometryUtil.getSlicePolygonOfCube(box, plane, axisW, axisH, halfWidth, halfHeight);
               mesh2D.subtractPolygon(polygonVertexes);
               if (++count % 10000 == 0) {
                  mesh2D.compact();
               }
            }

            mesh2D.simplify();
            mesh2D.compact();
         }, Util.getMainWorkerExecutor());
         future.thenRun(
            () -> server.execute(
               () -> {
                  if (!portal.isRemoved()) {
                     double meshArea = mesh2D.getArea();
                     double areaAfter = meshArea * halfWidth * halfHeight;
                     if (Math.abs(4.0 - meshArea) < 1.0E-5) {
                        portal.setPortalShapeToDefault();
                        if (player != null) {
                           player.sendMessage(Text.literal("Portal shape is still rectangular now."));
                        }
                     } else if (Math.abs(meshArea) < 1.0E-5) {
                        if (player != null) {
                           player.sendMessage(Text.literal("Sculpt failed because the blocks fully cover the portal."));
                        }
                     } else {
                        setPortalShapeByMesh(portal, mesh2D, adjustPortalBounds);
                        portal.getDefaultAnimation().setDisableUntil(portal.getEntityWorld().getTime() + 5L);
                        reloadPortal(portal);
                        if (player != null) {
                           player.sendMessage(
                              Text.translatable(
                                 "imm_ptl.sculpted",
                                 new Object[]{
                                    String.format("%.4f", areaBefore - areaAfter), String.format("%.4f", areaBefore), String.format("%.4f", areaAfter)
                                 }
                              )
                           );
                        }
                     }
                  }
               }
            )
         );
         future.exceptionally(throwable -> {
            LOGGER.error("Error when sculpting portal {}", portal, throwable);
            if (player != null) {
               player.sendMessage(Text.literal("Failed to sculpt the portal. See the server log for detail."));
            }

            return null;
         });
         ServerTaskList.of(server).addTask(MyTaskList.withDelay(5, MyTaskList.oneShotTask(() -> {
            if (!future.isDone() && player != null) {
               player.sendMessage(Text.translatable("imm_ptl.sculpting_in_progress"));
            }
         })));
      }
   }

   private static void setPortalShapeByMesh(Portal portal, Mesh2D mesh2D, boolean adjustPortalBounds) {
      if (adjustPortalBounds) {
         Mesh2D.Rect boundingBox = mesh2D.getBoundingBox();
         double centerLX = (boundingBox.minX() + boundingBox.maxX()) / 2.0;
         double centerLY = (boundingBox.minY() + boundingBox.maxY()) / 2.0;
         double lWidth = boundingBox.maxX() - boundingBox.minX();
         double lHeight = boundingBox.maxY() - boundingBox.minY();
         mesh2D.transformPoints(input -> new Vec2d((input.x() - centerLX) / (lWidth / 2.0), (input.y() - centerLY) / (lHeight / 2.0)));
         double oldHalfWidth = portal.getPortalWidth() / 2.0;
         double oldHalfHeight = portal.getPortalHeight() / 2.0;
         double newPortalWidth = oldHalfWidth * lWidth;
         double newPortalHeight = oldHalfHeight * lHeight;
         Vec3d centerOffset = portal.getAxisW().multiply(centerLX * oldHalfWidth).add(portal.getAxisH().multiply(centerLY * oldHalfHeight));
         Vec3d otherSideCenterOffset = portal.transformLocalVec(centerOffset);
         portal.setOriginPos(portal.getOriginPos().add(centerOffset));
         portal.setDestination(portal.getDestPos().add(otherSideCenterOffset));
         portal.setWidth(newPortalWidth);
         portal.setHeight(newPortalHeight);
      }

      portal.setPortalShape(new SpecialFlatPortalShape(mesh2D));
   }

   @NotNull
   private static ObjectArrayList<Box> gatherCollisionBoxesTouching(Portal portal) {
      Box areaBox = portal.getBoundingBox();
      ObjectArrayList<Box> boxes = new ObjectArrayList();

      for (VoxelShape blockCollision : portal.getEntityWorld().getBlockCollisions(portal, areaBox)) {
         if (!blockCollision.isEmpty()) {
            boxes.addAll(blockCollision.getBoundingBoxes());
         }
      }

      return boxes;
   }

   public interface PortalConsumerThrowsCommandSyntaxException {
      void accept(Portal var1) throws CommandSyntaxException;
   }

   public static class RemoteCallables {
      @Environment(EnvType.CLIENT)
      public static void clientAccelerate(Vec3d vec) {
         MinecraftClient client = MinecraftClient.getInstance();
         ClientPlayerEntity player = client.player;
         McHelper.setWorldVelocity(player, vec);
      }
   }
}
