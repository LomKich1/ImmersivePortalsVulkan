package qouteall.imm_ptl.peripheral.wand;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.ActionResult;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.world.World;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.MutableText;
import net.minecraft.registry.RegistryKey;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.component.ComponentType;
import net.minecraft.item.ItemGroup.class_7704;
import net.minecraft.item.Item.class_1793;
import net.minecraft.item.Item.class_9635;
import net.minecraft.client.render.VertexConsumerProvider.class_4598;
import qouteall.imm_ptl.core.IPCGlobal;
import qouteall.imm_ptl.core.IPMcHelper;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.block_manipulation.BlockManipulationServer;
import qouteall.q_misc_util.platform.ClientPlatform;
import qouteall.q_misc_util.platform.Platform;

public class PortalWandItem extends Item {
   public static final PortalWandItem instance = new PortalWandItem(
      new class_1793().registryKey(RegistryKey.of(RegistryKeys.field_41197, McHelper.newResourceLocation("immersive_portals:portal_wand")))
   );
   public static final Codec<PortalWandItem.Mode> MODE_CODEC = Codec.STRING.xmap(PortalWandItem.Mode::fromStr, PortalWandItem.Mode::toStr);
   public static final ComponentType<PortalWandItem.Mode> COMPONENT_TYPE = ComponentType.builder().codec(MODE_CODEC).build();
   private static boolean instructionInformed = false;

   public static void init() {
      Platform.get().registerBuiltIn(Registries.DATA_COMPONENT_TYPE, McHelper.newResourceLocation("iportal:portal_wand_data"), COMPONENT_TYPE);
      Platform.get()
         .onAttackBlock(
            (player, world, hand, pos, direction) -> (ActionResult)(player.getMainHandStack().getItem() == instance
               ? ActionResult.FAIL
               : ActionResult.PASS)
         );
      BlockManipulationServer.canDoCrossPortalInteractionEvent.register(p -> p.getMainHandStack().getItem() != instance);
   }

   public static void initClient() {
      ClientPlatform.get().onClientTickEnd(client -> {
         if (client.player != null) {
            ItemStack itemStack = client.player.getMainHandStack();
            if (itemStack.getItem() == instance) {
               updateDisplay(itemStack);
            } else {
               ClientPortalWandPortalCreation.clearCursorPointing();
            }
         }

         ClientPortalWandPortalDrag.tick();
      });
      IPCGlobal.CLIENT_CLEANUP_EVENT.register(ClientPortalWandPortalCreation::reset);
      IPCGlobal.CLIENT_CLEANUP_EVENT.register(ClientPortalWandPortalDrag::reset);
      IPCGlobal.CLIENT_CLEANUP_EVENT.register(ClientPortalWandPortalCopy::reset);
   }

   public static void addIntoCreativeTag(class_7704 entries) {
      ItemStack w1 = new ItemStack(instance);
      w1.set(COMPONENT_TYPE, PortalWandItem.Mode.CREATE_PORTAL);
      entries.add(w1);
      ItemStack w2 = new ItemStack(instance);
      w2.set(COMPONENT_TYPE, PortalWandItem.Mode.DRAG_PORTAL);
      entries.add(w2);
      ItemStack w3 = new ItemStack(instance);
      w3.set(COMPONENT_TYPE, PortalWandItem.Mode.COPY_PORTAL);
      entries.add(w3);
   }

   public PortalWandItem(class_1793 properties) {
      super(properties);
   }

   @Environment(EnvType.CLIENT)
   public static void onClientLeftClick(ClientPlayerEntity player, ItemStack itemStack) {
      if (player.isSneaking()) {
         showSettings(player);
      } else {
         PortalWandItem.Mode mode = (PortalWandItem.Mode)itemStack.getOrDefault(COMPONENT_TYPE, PortalWandItem.Mode.FALLBACK);
         switch (mode) {
            case CREATE_PORTAL:
               ClientPortalWandPortalCreation.onLeftClick();
               break;
            case DRAG_PORTAL:
               ClientPortalWandPortalDrag.onLeftClick();
               break;
            case COPY_PORTAL:
               ClientPortalWandPortalCopy.onLeftClick();
         }
      }
   }

   public ActionResult use(World world, PlayerEntity player, Hand hand) {
      ItemStack itemStack = player.getStackInHand(hand);
      PortalWandItem.Mode mode = (PortalWandItem.Mode)itemStack.getOrDefault(COMPONENT_TYPE, PortalWandItem.Mode.FALLBACK);
      if (player.isSneaking() && !world.isClient() && !PortalWandInteraction.isDragging((ServerPlayerEntity)player)) {
         PortalWandItem.Mode nextMode = mode.next();
         itemStack.set(COMPONENT_TYPE, nextMode);
         return ActionResult.SUCCESS.withNewHandStack(itemStack);
      } else {
         if (!player.isSneaking() && world.isClient()) {
            this.onUseClient(mode);
         }

         return super.use(world, player, hand);
      }
   }

   @Environment(EnvType.CLIENT)
   private void onUseClient(PortalWandItem.Mode mode) {
      switch (mode) {
         case CREATE_PORTAL:
            ClientPortalWandPortalCreation.onRightClick();
            break;
         case DRAG_PORTAL:
            ClientPortalWandPortalDrag.onRightClick();
            break;
         case COPY_PORTAL:
            ClientPortalWandPortalCopy.onRightClick();
      }
   }

   @Environment(EnvType.CLIENT)
   public void appendTooltip(ItemStack stack, class_9635 tooltipContext, TooltipDisplayComponent tooltipDisplay, Consumer<Text> tooltip, TooltipType tooltipFlag) {
      super.appendTooltip(stack, tooltipContext, tooltipDisplay, tooltip, tooltipFlag);
      tooltip.accept(
         Text.translatable(
            "imm_ptl.wand.item_desc_1",
            new Object[]{MinecraftClient.getInstance().options.sneakKey.getBoundKeyLocalizedText(), MinecraftClient.getInstance().options.useKey.getBoundKeyLocalizedText()}
         )
      );
      tooltip.accept(
         Text.translatable(
            "imm_ptl.wand.item_desc_2",
            new Object[]{MinecraftClient.getInstance().options.sneakKey.getBoundKeyLocalizedText(), MinecraftClient.getInstance().options.attackKey.getBoundKeyLocalizedText()}
         )
      );
   }

   public Text getName(ItemStack stack) {
      PortalWandItem.Mode mode = (PortalWandItem.Mode)stack.getOrDefault(COMPONENT_TYPE, PortalWandItem.Mode.FALLBACK);
      MutableText baseText = Text.translatable("item.immersive_portals.portal_wand");
      return baseText.append(Text.literal(" : ")).append(mode.getText().formatted(Formatting.field_1065));
   }

   public static void showSettings(PlayerEntity player) {
      player.sendMessage(Text.translatable("imm_ptl.wand.settings_1"), false);
      player.sendMessage(Text.translatable("imm_ptl.wand.settings_alignment"), false);
      int[] alignments = new int[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 16, 32, 64};
      List<MutableText> alignmentSettingTexts = new ArrayList<>();

      for (int alignment : alignments) {
         MutableText textWithCommand = IPMcHelper.getTextWithCommand(
            Text.literal("1/" + alignment), "/imm_ptl_client_debug wand set_cursor_alignment " + alignment
         );
         alignmentSettingTexts.add(textWithCommand);
      }

      alignmentSettingTexts.add(
         IPMcHelper.getTextWithCommand(Text.translatable("imm_ptl.wand.no_alignment"), "/imm_ptl_client_debug wand set_cursor_alignment 0")
      );
      player.sendMessage((Text)alignmentSettingTexts.stream().reduce(Text.literal(""), (a, b) -> a.append(" ").append(b)), false);
      player.sendMessage(Text.translatable("imm_ptl.wand.settings_2", new Object[]{MinecraftClient.getInstance().options.chatKey.getBoundKeyLocalizedText()}), false);
   }

   @Environment(EnvType.CLIENT)
   private static void updateDisplay(ItemStack itemStack) {
      PortalWandItem.Mode mode = (PortalWandItem.Mode)itemStack.getOrDefault(COMPONENT_TYPE, PortalWandItem.Mode.FALLBACK);
      switch (mode) {
         case CREATE_PORTAL:
            ClientPortalWandPortalCreation.updateDisplay();
            break;
         case DRAG_PORTAL:
            ClientPortalWandPortalDrag.updateDisplay();
            break;
         case COPY_PORTAL:
            ClientPortalWandPortalCopy.updateDisplay();
      }
   }

   @Environment(EnvType.CLIENT)
   public static void clientRender(ClientPlayerEntity player, ItemStack itemStack, MatrixStack poseStack, class_4598 bufferSource, double camX, double camY, double camZ) {
      if (!instructionInformed) {
         instructionInformed = true;
      }

      PortalWandItem.Mode mode = (PortalWandItem.Mode)itemStack.getOrDefault(COMPONENT_TYPE, PortalWandItem.Mode.FALLBACK);
      switch (mode) {
         case CREATE_PORTAL:
            ClientPortalWandPortalCreation.render(poseStack, bufferSource, camX, camY, camZ);
            break;
         case DRAG_PORTAL:
            ClientPortalWandPortalDrag.render(poseStack, bufferSource, camX, camY, camZ);
            break;
         case COPY_PORTAL:
            ClientPortalWandPortalCopy.render(poseStack, bufferSource, camX, camY, camZ);
      }
   }

   public static enum Mode {
      CREATE_PORTAL,
      DRAG_PORTAL,
      COPY_PORTAL;

      public static final PortalWandItem.Mode FALLBACK = CREATE_PORTAL;

      public static PortalWandItem.Mode fromTag(NbtCompound tag) {
         String mode = tag.getString("mode", "");
         return fromStr(mode);
      }

      public static PortalWandItem.Mode fromStr(String mode) {
         return switch (mode) {
            case "create_portal" -> CREATE_PORTAL;
            case "drag_portal" -> DRAG_PORTAL;
            case "copy_portal" -> COPY_PORTAL;
            default -> FALLBACK;
         };
      }

      public PortalWandItem.Mode next() {
         return switch (this) {
            case CREATE_PORTAL -> DRAG_PORTAL;
            case DRAG_PORTAL -> COPY_PORTAL;
            case COPY_PORTAL -> CREATE_PORTAL;
         };
      }

      public NbtCompound toTag() {
         NbtCompound tag = new NbtCompound();
         String modeString = this.toStr();
         tag.putString("mode", modeString);
         return tag;
      }

      public String toStr() {
         return switch (this) {
            case CREATE_PORTAL -> "create_portal";
            case DRAG_PORTAL -> "drag_portal";
            case COPY_PORTAL -> "copy_portal";
         };
      }

      public MutableText getText() {
         return switch (this) {
            case CREATE_PORTAL -> Text.translatable("imm_ptl.wand.mode.create_portal");
            case DRAG_PORTAL -> Text.translatable("imm_ptl.wand.mode.drag_portal");
            case COPY_PORTAL -> Text.translatable("imm_ptl.wand.mode.copy_portal");
         };
      }
   }
}
