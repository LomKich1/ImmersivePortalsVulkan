package qouteall.imm_ptl.peripheral;

import java.util.function.Consumer;
import net.minecraft.component.type.TooltipDisplayComponent;
import net.minecraft.util.Formatting;
import net.minecraft.util.ActionResult;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.block.Block;
import net.minecraft.text.Text;
import net.minecraft.item.Item.class_1793;
import net.minecraft.item.Item.class_9635;

public class PortalHelperItem extends BlockItem {
   private static boolean deprecationInformed = false;

   public PortalHelperItem(Block block, class_1793 settings) {
      super(block, settings);
   }

   public ActionResult useOnBlock(ItemUsageContext context) {
      if (context.getWorld().isClient() && context.getPlayer() != null && !deprecationInformed) {
         deprecationInformed = true;
         context.getPlayer()
            .sendMessage(
               Text.translatable(
                  "imm_ptl.portal_helper_deprecated", new Object[]{Text.literal("/portal shape sculpt").formatted(Formatting.field_1065)}
               ),
               false
            );
      }

      return super.useOnBlock(context);
   }

   public void appendTooltip(ItemStack itemStack, class_9635 tooltipContext, TooltipDisplayComponent tooltipDisplay, Consumer<Text> list, TooltipType tooltipFlag) {
      super.appendTooltip(itemStack, tooltipContext, tooltipDisplay, list, tooltipFlag);
      list.accept(Text.translatable("imm_ptl.portal_helper_tooltip"));
   }
}
