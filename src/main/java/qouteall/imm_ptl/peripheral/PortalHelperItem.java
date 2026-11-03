package qouteall.imm_ptl.peripheral;

import java.util.function.Consumer;
import net.minecraft.block.Block;
import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Item.Settings;
import net.minecraft.item.Item.TooltipContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;

public class PortalHelperItem extends BlockItem {
   private static boolean deprecationInformed = false;

   public PortalHelperItem(Block block, Settings settings) {
      super(block, settings);
   }

   public ActionResult useOnBlock(ItemUsageContext context) {
      if (context.getWorld().isClient() && context.getPlayer() != null && !deprecationInformed) {
         deprecationInformed = true;
         context.getPlayer()
            .sendMessage(
               Text.translatable("imm_ptl.portal_helper_deprecated", new Object[]{Text.literal("/portal shape sculpt").formatted(Formatting.GOLD)}), false
            );
      }

      return super.useOnBlock(context);
   }

   public void appendTooltip(ItemStack stack, TooltipContext context, TooltipDisplayComponent displayComponent, Consumer<Text> textConsumer, TooltipType type) {
      super.appendTooltip(stack, context, displayComponent, textConsumer, type);
      textConsumer.accept(Text.translatable("imm_ptl.portal_helper_tooltip"));
   }
}
