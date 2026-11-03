package qouteall.imm_ptl.peripheral.dim_stack;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.EntryListWidget;
import org.jetbrains.annotations.Nullable;
import qouteall.q_misc_util.Helper;

public class DimListWidget extends EntryListWidget<DimEntryWidget> {
   public static final int ROW_WIDTH = 300;
   public final Screen parent;
   private final DimListWidget.Type type;
   @Nullable
   private final DimListWidget.DraggingCallback draggingCallback;

   public DimListWidget(
      int width, int height, int top, int itemHeight, Screen parent, DimListWidget.Type type, @Nullable DimListWidget.DraggingCallback draggingCallback
   ) {
      super(MinecraftClient.getInstance(), width, height, top, itemHeight);
      this.parent = parent;
      this.type = type;
      this.draggingCallback = draggingCallback;
   }

   public boolean mouseDragged(Click click, double offsetX, double offsetY) {
      double mouseX = click.x();
      double mouseY = click.y();
      if (this.type == DimListWidget.Type.mainDimensionList && this.draggingCallback != null) {
         DimEntryWidget selected = (DimEntryWidget)this.getSelectedOrNull();
         if (selected != null) {
            DimEntryWidget mouseOn = (DimEntryWidget)this.getEntryAtPosition(mouseX, mouseY);
            if (mouseOn != null && mouseOn != selected) {
               int selectedIndex = this.children().indexOf(selected);
               int mouseOnIndex = this.children().indexOf(mouseOn);
               if (selectedIndex != -1 && mouseOnIndex != -1) {
                  this.draggingCallback.run(selectedIndex, mouseOnIndex);
               } else {
                  Helper.err("Invalid dragging");
               }
            }
         }
      }

      return super.mouseDragged(click, offsetX, offsetY);
   }

   protected void appendClickableNarrations(NarrationMessageBuilder builder) {
   }

   public int getRowWidth() {
      return 300;
   }

   protected int getScrollbarX() {
      return (this.width - 300) / 2 + 300;
   }

   protected void drawMenuListBackground(DrawContext context) {
   }

   public interface DraggingCallback {
      void run(int var1, int var2);
   }

   public static enum Type {
      mainDimensionList,
      addDimensionList;
   }
}
