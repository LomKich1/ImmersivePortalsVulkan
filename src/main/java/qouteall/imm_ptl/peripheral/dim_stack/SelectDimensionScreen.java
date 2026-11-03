package qouteall.imm_ptl.peripheral.dim_stack;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.world.World;
import net.minecraft.text.Text;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.registry.RegistryKey;

public class SelectDimensionScreen extends Screen {
   public final DimStackScreen parent;
   private DimListWidget dimListWidget;
   private ButtonWidget confirmButton;
   private final Consumer<RegistryKey<World>> outerCallback;
   private final List<RegistryKey<World>> dimensionList;

   protected SelectDimensionScreen(DimStackScreen parent, Consumer<RegistryKey<World>> callback, List<RegistryKey<World>> dimensionList) {
      super(Text.translatable("imm_ptl.select_dimension"));
      this.parent = parent;
      this.outerCallback = callback;
      this.dimensionList = dimensionList;
   }

   protected void init() {
      this.dimListWidget = new DimListWidget(this.width, this.height - 20 - 40, 20, 50, this, DimListWidget.Type.addDimensionList, null);
      this.addSelectableChild(this.dimListWidget);
      Consumer<DimEntryWidget> callback = w -> this.dimListWidget.setSelected(w);

      for (RegistryKey<World> dim : this.dimensionList) {
         this.dimListWidget.children().add(new DimEntryWidget(dim, this.dimListWidget, callback, new DimStackEntry(dim)));
      }

      this.confirmButton = (ButtonWidget)this.addDrawableChild(ButtonWidget.builder(Text.translatable("imm_ptl.confirm_select_dimension"), buttonWidget -> {
         DimEntryWidget selected = (DimEntryWidget)this.dimListWidget.getSelectedOrNull();
         if (selected != null) {
            MinecraftClient.getInstance().setScreen(this.parent);
            this.outerCallback.accept(selected.dimension);
         }
      }).position(this.width / 2 - 75, this.height - 28).size(150, 20).build());
   }

   public void close() {
      this.client.setScreen(this.parent);
   }

   public void render(DrawContext guiGraphics, int mouseX, int mouseY, float delta) {
      super.render(guiGraphics, mouseX, mouseY, delta);
      this.dimListWidget.render(guiGraphics, mouseX, mouseY, delta);
      guiGraphics.drawCenteredTextWithShadow(this.textRenderer, this.title.getString(), this.width / 2, 10, -1);
   }
}
