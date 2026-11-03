package qouteall.imm_ptl.peripheral;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import net.minecraft.command.DefaultPermissions;
import net.minecraft.command.permission.LeveledPermissionPredicate;
import net.minecraft.component.ComponentType;
import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Item.Settings;
import net.minecraft.item.Item.TooltipContext;
import net.minecraft.item.ItemGroup.Entries;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.commands.PortalCommand;
import qouteall.q_misc_util.platform.Platform;

public class CommandStickItem extends Item {
   private static final Logger LOGGER = LogUtils.getLogger();
   public static final Codec<CommandStickItem.Data> DATA_CODEC = RecordCodecBuilder.create(
      instance -> instance.group(
            Codec.STRING.fieldOf("command").forGetter(CommandStickItem.Data::command),
            Codec.STRING.fieldOf("nameTranslationKey").forGetter(CommandStickItem.Data::nameTranslationKey),
            Codec.STRING.listOf().fieldOf("descriptionTranslationKeys").forGetter(CommandStickItem.Data::descriptionTranslationKeys)
         )
         .apply(instance, CommandStickItem.Data::new)
   );
   public static final ComponentType<CommandStickItem.Data> COMPONENT_TYPE = ComponentType.builder().codec(DATA_CODEC).build();
   public static final LinkedHashMap<String, CommandStickItem.Data> BUILT_IN_COMMAND_STICK_TYPES = new LinkedHashMap<>();
   public static final CommandStickItem instance = new CommandStickItem(
      new Settings().registryKey(RegistryKey.of(RegistryKeys.ITEM, McHelper.newResourceLocation("immersive_portals:command_stick")))
   );

   public static void registerBuiltInCommandStick(CommandStickItem.Data data) {
      BUILT_IN_COMMAND_STICK_TYPES.put(data.command, data);
   }

   public CommandStickItem(Settings settings) {
      super(settings);
   }

   public boolean hasGlint(ItemStack stack) {
      return true;
   }

   public ActionResult use(World world, PlayerEntity user, Hand hand) {
      this.doUse(user, user.getStackInHand(hand));
      return super.use(world, user, hand);
   }

   private void doUse(PlayerEntity player, ItemStack stack) {
      if (!player.getEntityWorld().isClient()) {
         if (canUseCommand(player)) {
            CommandStickItem.Data data = (CommandStickItem.Data)stack.get(COMPONENT_TYPE);
            if (data == null) {
               LOGGER.error("Missing component in command stick item {}", stack);
               return;
            }

            ServerCommandSource commandSource = ((ServerPlayerEntity)player).getCommandSource().withPermissions(LeveledPermissionPredicate.GAMEMASTERS);
            MinecraftServer server = player.getEntityWorld().getServer();

            assert server != null;

            CommandManager commandManager = server.getCommandManager();
            String command = data.command;
            if (command.startsWith("/")) {
               command = command.substring(1);
            }

            commandManager.parseAndExecute(commandSource, command);
         } else {
            sendMessage(player, Text.literal("No Permission"));
         }
      }
   }

   private static boolean canUseCommand(PlayerEntity player) {
      return IPGlobal.easeCommandStickPermission ? true : player.getPermissions().hasPermission(DefaultPermissions.GAMEMASTERS) || player.isCreative();
   }

   public void appendTooltip(ItemStack stack, TooltipContext context, TooltipDisplayComponent displayComponent, Consumer<Text> textConsumer, TooltipType type) {
      super.appendTooltip(stack, context, displayComponent, textConsumer, type);
      CommandStickItem.Data data = (CommandStickItem.Data)stack.get(COMPONENT_TYPE);
      if (data != null) {
         for (String commandPortion : Splitter.fixedLength(40).split(data.command)) {
            textConsumer.accept(Text.literal(commandPortion).formatted(Formatting.GOLD));
         }

         for (String descriptionTranslationKey : data.descriptionTranslationKeys) {
            textConsumer.accept(Text.translatable(descriptionTranslationKey).formatted(Formatting.AQUA));
         }

         textConsumer.accept(Text.translatable("imm_ptl.command_stick").formatted(Formatting.GRAY));
      }
   }

   @NotNull
   public Text getName(ItemStack stack) {
      CommandStickItem.Data data = (CommandStickItem.Data)stack.get(COMPONENT_TYPE);
      return data == null ? Text.empty() : Text.translatable(data.nameTranslationKey);
   }

   public static void sendMessage(PlayerEntity player, Text message) {
      ((ServerPlayerEntity)player).sendMessage(message);
   }

   public static void init() {
      Platform.get().registerBuiltIn(Registries.DATA_COMPONENT_TYPE, McHelper.newResourceLocation("iportal:command_stick_data"), COMPONENT_TYPE);
      PortalCommand.createCommandStickCommandSignal.connect((player, command) -> {
         ItemStack itemStack = new ItemStack(instance, 1);
         CommandStickItem.Data data = new CommandStickItem.Data(command, command, new ArrayList<>());
         itemStack.set(COMPONENT_TYPE, data);
         player.getInventory().insertStack(itemStack);
         player.playerScreenHandler.sendContentUpdates();
      });
   }

   public static void addIntoCreativeTag(Entries entries) {
      for (CommandStickItem.Data data : BUILT_IN_COMMAND_STICK_TYPES.values()) {
         ItemStack stack = new ItemStack(instance);
         stack.set(COMPONENT_TYPE, data);
         entries.add(stack);
      }
   }

   public static void registerCommandStickTypes() {
      registerPortalSubCommandStick("delete_portal");
      registerPortalSubCommandStick("remove_connected_portals");
      registerPortalSubCommandStick("eradicate_portal_cluster");
      registerPortalSubCommandStick("complete_bi_way_bi_faced_portal");
      registerPortalSubCommandStick("complete_bi_way_portal");
      registerPortalSubCommandStick("move_portal_front", "move_portal 0.5");
      registerPortalSubCommandStick("move_portal_back", "move_portal -0.5");
      registerPortalSubCommandStick("move_portal_back_a_little", "move_portal -0.001");
      registerPortalSubCommandStick("move_portal_destination_front", "move_portal_destination 0.5");
      registerPortalSubCommandStick("move_portal_destination_back", "move_portal_destination -0.5");
      registerPortalSubCommandStick("rotate_x", "rotate_portal_rotation_along x 15");
      registerPortalSubCommandStick("rotate_y", "rotate_portal_rotation_along y 15");
      registerPortalSubCommandStick("rotate_z", "rotate_portal_rotation_along z 15");
      registerPortalSubCommandStick("make_unbreakable", "nbt {unbreakable:true}");
      registerPortalSubCommandStick("make_fuse_view", "nbt {fuseView:true}");
      registerPortalSubCommandStick("enable_pos_adjust", "nbt {adjustPositionAfterTeleport:true}");
      registerPortalSubCommandStick("disable_rendering_yourself", "nbt {doRenderPlayer:false}");
      registerPortalSubCommandStick("enable_isometric", "debug isometric_enable 50");
      registerPortalSubCommandStick("disable_isometric", "debug isometric_disable");
      registerPortalSubCommandStick("create_5_connected_rooms", "create_connected_rooms roomSize 6 4 6 roomNumber 5");
      registerPortalSubCommandStick("accelerate50", "debug accelerate 50");
      registerPortalSubCommandStick("accelerate200", "debug accelerate 200");
      registerPortalSubCommandStick("reverse_accelerate50", "debug accelerate -50");
      registerPortalSubCommandStick("enable_gravity_change", "nbt {teleportChangesGravity:true}");
      registerPortalSubCommandStick("make_invisible", "nbt {isVisible:false}");
      registerPortalSubCommandStick("make_visible", "nbt {isVisible:true}");
      registerPortalSubCommandStick("disable_default_animation", "nbt {defaultAnimation:{durationTicks:0}}");
      registerPortalSubCommandStick("pause_animation", "animation pause");
      registerPortalSubCommandStick("resume_animation", "animation resume");
      registerPortalSubCommandStick("rotate_around_y", "animation rotate_infinitely @s 0 1 0 1.0");
      registerPortalSubCommandStick("rotate_randomly", "animation rotate_infinitely_random");
      registerBuiltInCommandStick(
         new CommandStickItem.Data(
            "execute positioned 0.0 0.0 0.0 run portal animation rotate_infinitely @p ^0.0 ^0.0 ^1.0 1.7",
            "imm_ptl.command.rotate_around_view",
            Lists.newArrayList(new String[]{"imm_ptl.command_desc.rotate_around_view"})
         )
      );
      registerPortalSubCommandStick("expand_from_center", "animation expand_from_center 20");
      registerPortalSubCommandStick("clear_animation", "animation clear");
      registerPortalSubCommandStick("sculpt", "shape sculpt");
      registerPortalSubCommandStick("reset_shape", "shape reset");
      registerBuiltInCommandStick(
         new CommandStickItem.Data(
            "/attribute @s minecraft:generic.scale modifier remove iportal:scaling",
            "imm_ptl.command.reset_iportal_scale",
            Lists.newArrayList(new String[]{"imm_ptl.command_desc.reset_iportal_scale"})
         )
      );
      registerBuiltInCommandStick(
         new CommandStickItem.Data(
            "/attribute @s minecraft:player.block_interaction_range base set 100",
            "imm_ptl.command.long_reach",
            Lists.newArrayList(new String[]{"imm_ptl.command_desc.long_reach"})
         )
      );
      registerBuiltInCommandStick(new CommandStickItem.Data("/effect give @s minecraft:night_vision 9999 1 true", "imm_ptl.command.night_vision", List.of()));
      registerBuiltInCommandStick(new CommandStickItem.Data("/setblock ~ ~ ~ minecraft:grass_block", "imm_ptl.command.block_on_feet", List.of()));
      registerPortalSubCommandStick("goback");
      registerPortalSubCommandStick("show_wiki", "wiki");
   }

   private static CommandStickItem.Data registerPortalSubCommandStick(String name) {
      return registerPortalSubCommandStick(name, name);
   }

   private static CommandStickItem.Data registerPortalSubCommandStick(String name, String subCommand) {
      CommandStickItem.Data data = new CommandStickItem.Data(
         "/portal " + subCommand, "imm_ptl.command." + name, Lists.newArrayList(new String[]{"imm_ptl.command_desc." + name})
      );
      registerBuiltInCommandStick(data);
      return data;
   }

   public record Data(String command, String nameTranslationKey, List<String> descriptionTranslationKeys) {
      public void serialize(NbtCompound tag) {
         tag.putString("command", this.command);
         tag.putString("nameTranslationKey", this.nameTranslationKey);
         NbtList listTag = new NbtList();

         for (String descriptionTK : this.descriptionTranslationKeys) {
            listTag.add(NbtString.of(descriptionTK));
         }

         tag.put("descriptionTranslationKeys", listTag);
      }

      public NbtCompound toTag() {
         NbtCompound tag = new NbtCompound();
         this.serialize(tag);
         return tag;
      }

      public static CommandStickItem.Data deserialize(NbtCompound tag) {
         return new CommandStickItem.Data(
            tag.getString("command", ""),
            tag.getString("nameTranslationKey", ""),
            tag.getListOrEmpty("descriptionTranslationKeys").stream().map(tag1 -> ((NbtString)tag1).value()).collect(Collectors.toList())
         );
      }
   }
}
