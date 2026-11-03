package qouteall.imm_ptl.peripheral.dim_stack;

import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.text.Text;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.screen.Screen;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qouteall.imm_ptl.core.CHelper;
import qouteall.q_misc_util.my_util.GuiHelper;

@Environment(EnvType.CLIENT)
public class DimStackScreen extends Screen {
   private static final Logger LOGGER = LoggerFactory.getLogger(DimStackScreen.class);
   private final DimStackGuiController controller;
   @Nullable
   public final Screen parent;
   private final ButtonWidget finishButton;
   private final ButtonWidget toggleButton;
   private final ButtonWidget addDimensionButton;
   private final ButtonWidget removeDimensionButton;
   private final ButtonWidget editButton;
   private final ButtonWidget helpButton;
   private final ButtonWidget setAsPresetButton;
   private final ButtonWidget loopButton;
   private final ButtonWidget gravityModeButton;
   private int titleY;
   public final DimListWidget dimListWidget;
   private boolean isEnabled = false;

   public DimStackScreen(@Nullable Screen parent, DimStackGuiController controller) {
      super(Text.translatable("imm_ptl.altius_screen"));
      this.parent = parent;
      this.toggleButton = ButtonWidget.builder(Text.literal("..."), buttonWidget -> controller.toggleEnabled()).build();
      this.loopButton = ButtonWidget.builder(Text.literal("..."), buttonWidget -> controller.toggleLoop()).build();
      this.gravityModeButton = ButtonWidget.builder(Text.literal("..."), gravityModeButton -> controller.toggleGravityMode()).build();
      this.finishButton = ButtonWidget.builder(Text.translatable("imm_ptl.finish"), buttonWidget -> controller.onFinish()).build();
      this.controller = controller;
      this.addDimensionButton = ButtonWidget.builder(Text.translatable("imm_ptl.dim_stack_add"), buttonWidget -> this.onAddEntry()).build();
      this.removeDimensionButton = ButtonWidget.builder(Text.translatable("imm_ptl.dim_stack_remove"), buttonWidget -> this.onRemoveEntry())
         .build();
      this.editButton = ButtonWidget.builder(Text.translatable("imm_ptl.dim_stack_edit"), buttonWidget -> this.onEditEntry()).build();
      this.dimListWidget = new DimListWidget(this.width, this.height, 100, 50, this, DimListWidget.Type.mainDimensionList, controller::onDragged);
      this.helpButton = createHelpButton(this);
      this.setAsPresetButton = ButtonWidget.builder(Text.translatable("imm_ptl.set_as_dim_stack_default"), button -> this.controller.setAsDefault())
         .build();
   }

   public void setLoopEnabled(boolean loopEnabled) {
      this.loopButton.setMessage(Text.translatable(loopEnabled ? "imm_ptl.loop_enabled" : "imm_ptl.loop_disabled"));
   }

   public void setGravityTransformEnabled(boolean gravityTransformEnabled) {
      this.gravityModeButton
         .setMessage(
            Text.translatable(gravityTransformEnabled ? "imm_ptl.dim_stack.gravity_transform_enabled" : "imm_ptl.dim_stack.gravity_transform_disabled")
         );
   }

   public static ButtonWidget createHelpButton(Screen parent) {
      return ButtonWidget.builder(
            Text.literal("?"), button -> CHelper.openLinkConfirmScreen(parent, "https://qouteall.fun/immptl/wiki/Dimension-Stack")
         )
         .build();
   }

   public DimEntryWidget createDimEntryWidget(DimStackEntry entry) {
      return new DimEntryWidget(entry.getDimension(), this.dimListWidget, this.getElementSelectCallback(), entry);
   }

   protected void init() {
      this.addDrawableChild(this.toggleButton);
      this.addDrawableChild(this.finishButton);
      this.addDrawableChild(this.addDimensionButton);
      this.addDrawableChild(this.removeDimensionButton);
      this.addDrawableChild(this.editButton);
      this.addDrawableChild(this.helpButton);
      this.addDrawableChild(this.setAsPresetButton);
      this.addDrawableChild(this.loopButton);
      this.addDrawableChild(this.gravityModeButton);
      this.addSelectableChild(this.dimListWidget);
      GuiHelper.layout(
         0,
         this.height,
         GuiHelper.blankSpace(5),
         new GuiHelper.LayoutElement(true, 20, (from, to) -> {
            this.helpButton.setX(this.width - 30);
            this.helpButton.setY(from);
            this.helpButton.setWidth(20);
            this.setAsPresetButton.setX(this.width - 125);
            this.setAsPresetButton.setY(from);
            this.setAsPresetButton.setWidth(90);
         }),
         new GuiHelper.LayoutElement(
            true,
            20,
            GuiHelper.combine(
               GuiHelper.layoutButtonVertically(this.toggleButton),
               GuiHelper.layoutButtonVertically(this.loopButton),
               GuiHelper.layoutButtonVertically(this.gravityModeButton)
            )
         ),
         GuiHelper.blankSpace(5),
         new GuiHelper.LayoutElement(false, 1, (from, to) -> {
            this.dimListWidget.setDimensions(this.width, to - from);
            this.dimListWidget.setPosition(0, from);
         }),
         GuiHelper.blankSpace(5),
         new GuiHelper.LayoutElement(
            true,
            20,
            (from, to) -> {
               this.finishButton.setY(from);
               this.addDimensionButton.setY(from);
               this.removeDimensionButton.setY(from);
               this.editButton.setY(from);
               GuiHelper.layout(
                  0,
                  this.width,
                  GuiHelper.blankSpace(10),
                  new GuiHelper.LayoutElement(false, 1, GuiHelper.layoutButtonHorizontally(this.finishButton)),
                  GuiHelper.blankSpace(5),
                  new GuiHelper.LayoutElement(false, 1, GuiHelper.layoutButtonHorizontally(this.addDimensionButton)),
                  GuiHelper.blankSpace(5),
                  new GuiHelper.LayoutElement(false, 1, GuiHelper.layoutButtonHorizontally(this.removeDimensionButton)),
                  GuiHelper.blankSpace(5),
                  new GuiHelper.LayoutElement(false, 1, GuiHelper.layoutButtonHorizontally(this.editButton)),
                  GuiHelper.blankSpace(10)
               );
            }
         ),
         GuiHelper.blankSpace(5)
      );
      GuiHelper.layout(
         0,
         this.width,
         GuiHelper.blankSpace(10),
         new GuiHelper.LayoutElement(false, 10, GuiHelper.layoutButtonHorizontally(this.toggleButton)),
         GuiHelper.blankSpace(5),
         new GuiHelper.LayoutElement(false, 8, GuiHelper.layoutButtonHorizontally(this.loopButton)),
         GuiHelper.blankSpace(5),
         new GuiHelper.LayoutElement(false, 10, GuiHelper.layoutButtonHorizontally(this.gravityModeButton)),
         GuiHelper.blankSpace(10)
      );
   }

   public void close() {
      this.controller.onFinish();
   }

   private Consumer<DimEntryWidget> getElementSelectCallback() {
      return w -> this.dimListWidget.setSelected(w);
   }

   public void render(DrawContext guiGraphics, int mouseX, int mouseY, float partialTick) {
      super.render(guiGraphics, mouseX, mouseY, partialTick);
      if (this.isEnabled) {
         this.dimListWidget.render(guiGraphics, mouseX, mouseY, partialTick);
      }

      TextRenderer font = MinecraftClient.getInstance().textRenderer;
      guiGraphics.drawTextWithShadow(font, this.title, 20, 10, -1);
   }

   public void setEnabled(boolean cond) {
      this.isEnabled = cond;
      if (this.isEnabled) {
         this.toggleButton.setMessage(Text.translatable("imm_ptl.altius_toggle_true"));
      } else {
         this.toggleButton.setMessage(Text.translatable("imm_ptl.altius_toggle_false"));
      }

      this.addDimensionButton.visible = this.isEnabled;
      this.removeDimensionButton.visible = this.isEnabled;
      this.editButton.visible = this.isEnabled;
      this.loopButton.visible = this.isEnabled;
      this.gravityModeButton.visible = this.isEnabled;
   }

   private void onAddEntry() {
      DimEntryWidget selected = (DimEntryWidget)this.dimListWidget.getSelectedOrNull();
      int position;
      if (selected == null) {
         position = 0;
      } else {
         position = this.dimListWidget.children().indexOf(selected);
      }

      if (position < 0 || position > this.dimListWidget.children().size()) {
         position = -1;
      }

      int insertingPosition = position + 1;
      MinecraftClient.getInstance()
         .setScreen(
            new SelectDimensionScreen(
               this, dimensionType -> this.controller.addEntry(insertingPosition, new DimStackEntry(dimensionType)), this.controller.getDimensionList()
            )
         );
   }

   private void onRemoveEntry() {
      DimEntryWidget selected = (DimEntryWidget)this.dimListWidget.getSelectedOrNull();
      if (selected != null) {
         int position = this.dimListWidget.children().indexOf(selected);
         if (position != -1) {
            this.controller.removeEntry(position);
         }
      }
   }

   private void onEditEntry() {
      DimEntryWidget selected = (DimEntryWidget)this.dimListWidget.getSelectedOrNull();
      if (selected != null) {
         MinecraftClient.getInstance().setScreen(new DimStackEntryEditScreen(this, selected, () -> {
            int newlyChangingEntryIndex = this.dimListWidget.children().indexOf(selected);
            if (newlyChangingEntryIndex == -1) {
               LOGGER.error("The edited entry is missing in the list");
            } else {
               this.controller.editEntry(newlyChangingEntryIndex, selected.entry);
            }
         }));
      }
   }
}
