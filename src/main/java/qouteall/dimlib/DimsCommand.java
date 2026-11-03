package qouteall.dimlib;

import com.google.gson.JsonElement;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import net.minecraft.command.DefaultPermissions;
import net.minecraft.command.argument.DimensionArgumentType;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.SimpleRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import qouteall.dimlib.api.DimensionAPI;

public class DimsCommand {
   private static final Logger LOGGER = LogUtils.getLogger();

   public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
      LiteralArgumentBuilder<ServerCommandSource> builder = (LiteralArgumentBuilder<ServerCommandSource>)CommandManager.literal("dims")
         .requires(source -> source.getPermissions().hasPermission(DefaultPermissions.GAMEMASTERS));
      builder.then(
         CommandManager.literal("clone_dimension")
            .then(
               CommandManager.argument("templateDimension", DimensionArgumentType.dimension())
                  .then(CommandManager.argument("newDimensionID", StringArgumentType.string()).executes(context -> {
                     ServerWorld templateDimension = DimensionArgumentType.getDimensionArgument(context, "templateDimension");
                     String newDimensionId = StringArgumentType.getString(context, "newDimensionID");
                     Identifier newDimId = parseDimensionId(context, newDimensionId);
                     if (newDimId == null) {
                        return 0;
                     } else {
                        cloneDimension(templateDimension, newDimId);
                        ((ServerCommandSource)context.getSource())
                           .sendFeedback(() -> Text.literal("Dynamically added dimension %s".formatted(newDimensionId)), true);
                        return 0;
                     }
                  }))
            )
      );
      RequiredArgumentBuilder<ServerCommandSource, String> addDimensionCommandNode = CommandManager.argument("newDimensionId", StringArgumentType.string());

      for (Entry<String, DimensionTemplate> e : DimensionTemplate.DIMENSION_TEMPLATES.entrySet()) {
         String dimTemplateId = e.getKey();
         DimensionTemplate dimensionTemplate = e.getValue();
         addDimensionCommandNode.then(CommandManager.literal(dimTemplateId).executes(context -> runAddDimension(context, dimensionTemplate)));
      }

      builder.then(CommandManager.literal("add_dimension").then(addDimensionCommandNode));
      builder.then(
         CommandManager.literal("remove_dimension")
            .then(
               CommandManager.argument("dimension", DimensionArgumentType.dimension())
                  .executes(
                     context -> {
                        ServerWorld dimension = DimensionArgumentType.getDimensionArgument(context, "dimension");
                        DimensionAPI.removeDimensionDynamically(dimension);
                        ((ServerCommandSource)context.getSource())
                           .sendFeedback(
                              () -> Text.literal(
                                 "Dynamically removed dimension %s . Its world file is not yet deleted. Note: if the datapack config for that dimension exists, the dimension will be re-added after server restart."
                                    .formatted(dimension.getRegistryKey().getValue())
                              ),
                              true
                           );
                        return 0;
                     }
                  )
            )
      );
      builder.then(CommandManager.literal("list").executes(context -> {
         MinecraftServer server = ((ServerCommandSource)context.getSource()).getServer();
         MutableText text = Text.literal(server.getWorldRegistryKeys().stream().map(k -> k.getValue().toString()).sorted().collect(Collectors.joining("\n")));
         ((ServerCommandSource)context.getSource()).sendFeedback(() -> text, false);
         return 0;
      }));
      builder.then(CommandManager.literal("view_dim_config").then(CommandManager.argument("dim", DimensionArgumentType.dimension()).executes(context -> {
         ServerWorld world = DimensionArgumentType.getDimensionArgument(context, "dim");
         SimpleRegistry<DimensionOptions> dimensionRegistry = DimensionImpl.getDimensionRegistry(world.getServer());
         DimensionOptions levelStem = (DimensionOptions)dimensionRegistry.get(world.getRegistryKey().getValue());
         if (levelStem == null) {
            ((ServerCommandSource)context.getSource()).sendError(Text.literal("Dimension config not found"));
            return 0;
         } else {
            DataResult<JsonElement> encoded = DimensionOptions.CODEC.encodeStart(RegistryOps.of(JsonOps.INSTANCE, world.getRegistryManager()), levelStem);
            if (encoded.result().isPresent()) {
               String jsonStr = DimLibUtil.GSON.toJson((JsonElement)encoded.result().get());
               ((ServerCommandSource)context.getSource()).sendFeedback(() -> Text.literal(jsonStr), true);
            } else {
               ((ServerCommandSource)context.getSource()).sendError(Text.literal(encoded.error().toString()));
            }

            return 0;
         }
      })));
      dispatcher.register(builder);
   }

   @Nullable
   private static Identifier parseDimensionId(CommandContext<ServerCommandSource> context, String newDimensionId) {
      Identifier newDimId;
      try {
         newDimId = Identifier.of(newDimensionId);
      } catch (Exception var4) {
         ((ServerCommandSource)context.getSource()).sendError(Text.literal("Invalid dimension id"));
         return null;
      }

      MinecraftServer server = ((ServerCommandSource)context.getSource()).getServer();
      if (newDimId.getNamespace().equals("minecraft")) {
         ((ServerCommandSource)context.getSource()).sendError(Text.literal("namespace cannot be minecraft"));
         return null;
      } else if (DimensionAPI.dimensionExistsInRegistry(server, newDimId)) {
         ((ServerCommandSource)context.getSource()).sendError(Text.literal("Dimension" + newDimId + " already exists"));
         return null;
      } else {
         return newDimId;
      }
   }

   private static int runAddDimension(CommandContext<ServerCommandSource> context, DimensionTemplate template) {
      String newDimensionId = StringArgumentType.getString(context, "newDimensionId");
      Identifier newDimId = parseDimensionId(context, newDimensionId);
      if (newDimId == null) {
         return 0;
      } else {
         MinecraftServer server = ((ServerCommandSource)context.getSource()).getServer();
         DimensionAPI.addDimensionDynamically(server, newDimId, template.createLevelStem(server));
         return 0;
      }
   }

   private static void cloneDimension(ServerWorld templateDimension, Identifier newDimId) {
      ChunkGenerator generator = templateDimension.getChunkManager().getChunkGenerator();
      DimensionAPI.addDimension(templateDimension.getServer(), newDimId, new DimensionOptions(templateDimension.getDimensionEntry(), generator));
   }
}
