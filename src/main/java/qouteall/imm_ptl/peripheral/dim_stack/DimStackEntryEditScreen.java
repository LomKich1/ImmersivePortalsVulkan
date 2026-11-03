package qouteall.imm_ptl.peripheral.dim_stack;

import java.util.OptionalInt;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.my_util.GuiHelper;

public class DimStackEntryEditScreen extends Screen {
   private final DimStackScreen parent;
   private final DimEntryWidget editing;
   private final TextFieldWidget scaleField;
   private final ButtonWidget flipButton;
   private final TextFieldWidget horizontalRotationField;
   private final TextFieldWidget topYField;
   private final TextFieldWidget bottomYField;
   private final TextFieldWidget bedrockBlockField;
   private final ButtonWidget connectsPreviousButton;
   private final ButtonWidget connectsNextButton;
   private final ButtonWidget finishButton;
   private final GuiHelper.Rect scaleLabelRect = new GuiHelper.Rect();
   private final GuiHelper.Rect flipLabelRect = new GuiHelper.Rect();
   private final GuiHelper.Rect horizontalRotationLabelRect = new GuiHelper.Rect();
   private final GuiHelper.Rect topYLabelRect = new GuiHelper.Rect();
   private final GuiHelper.Rect bottomYLabelRect = new GuiHelper.Rect();
   private final GuiHelper.Rect bedrockLabelRect = new GuiHelper.Rect();
   private final GuiHelper.Rect connectsPreviousRect = new GuiHelper.Rect();
   private final GuiHelper.Rect connectsNextRect = new GuiHelper.Rect();
   private final ButtonWidget helpButton;

   protected DimStackEntryEditScreen(DimStackScreen parent, DimEntryWidget editing, Runnable callback) {
      super(Text.literal("you cannot see me"));
      this.parent = parent;
      this.editing = editing;
      this.scaleField = new TextFieldWidget(MinecraftClient.getInstance().textRenderer, 0, 0, 0, 20, Text.literal("you cannot see me"));
      this.scaleField.setText(Double.toString(editing.entry.scale));
      this.scaleField.setSelectionEnd(0);
      this.scaleField.setSelectionStart(0);
      this.flipButton = ButtonWidget.builder(Text.translatable(editing.entry.flipped ? "imm_ptl.enabled" : "imm_ptl.disabled"), button -> {
         editing.entry.flipped = !editing.entry.flipped;
         button.setMessage(Text.translatable(editing.entry.flipped ? "imm_ptl.enabled" : "imm_ptl.disabled"));
      }).build();
      this.horizontalRotationField = new TextFieldWidget(MinecraftClient.getInstance().textRenderer, 0, 0, 0, 20, Text.literal("you cannot see me"));
      this.horizontalRotationField.setText(Double.toString(editing.entry.horizontalRotation));
      this.horizontalRotationField.setSelectionStart(0);
      this.horizontalRotationField.setSelectionEnd(0);
      this.topYField = new TextFieldWidget(MinecraftClient.getInstance().textRenderer, 0, 0, 0, 20, Text.literal("you cannot see me"));
      if (editing.entry.topY != null) {
         this.topYField.setText(Integer.toString(editing.entry.topY));
      }

      this.topYField.setSelectionStart(0);
      this.topYField.setSelectionEnd(0);
      this.bottomYField = new TextFieldWidget(MinecraftClient.getInstance().textRenderer, 0, 0, 0, 20, Text.literal("you cannot see me"));
      if (editing.entry.bottomY != null) {
         this.bottomYField.setText(Integer.toString(editing.entry.bottomY));
      }

      this.bottomYField.setSelectionStart(0);
      this.bottomYField.setSelectionEnd(0);
      this.bedrockBlockField = new TextFieldWidget(MinecraftClient.getInstance().textRenderer, 0, 0, 0, 20, Text.literal("you cannot see me"));
      this.bedrockBlockField.setMaxLength(200);
      if (editing.entry.bedrockReplacementStr != null) {
         this.bedrockBlockField.setText(editing.entry.bedrockReplacementStr);
      }

      this.bedrockBlockField.setSelectionStart(0);
      this.bedrockBlockField.setSelectionEnd(0);
      this.connectsPreviousButton = ButtonWidget.builder(
            Text.translatable(editing.entry.connectsPrevious ? "imm_ptl.enabled" : "imm_ptl.disabled"), button -> {
               editing.entry.connectsPrevious = !editing.entry.connectsPrevious;
               button.setMessage(Text.translatable(editing.entry.connectsPrevious ? "imm_ptl.enabled" : "imm_ptl.disabled"));
            }
         )
         .build();
      this.connectsNextButton = ButtonWidget.builder(Text.translatable(editing.entry.connectsNext ? "imm_ptl.enabled" : "imm_ptl.disabled"), button -> {
         editing.entry.connectsNext = !editing.entry.connectsNext;
         button.setMessage(Text.translatable(editing.entry.connectsNext ? "imm_ptl.enabled" : "imm_ptl.disabled"));
      }).build();
      this.finishButton = ButtonWidget.builder(Text.translatable("imm_ptl.finish"), button -> {
         editing.entry.horizontalRotation = Helper.parseDouble(this.horizontalRotationField.getText()).orElse(0.0);
         editing.entry.scale = Helper.parseDouble(this.scaleField.getText()).orElse(1.0);
         OptionalInt topY = Helper.parseInt(this.topYField.getText());
         editing.entry.topY = topY.isPresent() ? topY.getAsInt() : null;
         OptionalInt bottomY = Helper.parseInt(this.bottomYField.getText());
         editing.entry.bottomY = bottomY.isPresent() ? bottomY.getAsInt() : null;
         editing.entry.bedrockReplacementStr = this.bedrockBlockField.getText();
         MinecraftClient.getInstance().setScreen(parent);
         callback.run();
      }).build();
      this.helpButton = DimStackScreen.createHelpButton(this);
   }

   public void tick() {
      super.tick();
   }

   protected void init() {
      this.addSelectableChild(this.scaleField);
      this.addDrawableChild(this.flipButton);
      this.addSelectableChild(this.horizontalRotationField);
      this.addSelectableChild(this.topYField);
      this.addSelectableChild(this.bottomYField);
      this.addSelectableChild(this.bedrockBlockField);
      this.addSelectableChild(this.connectsPreviousButton);
      this.addSelectableChild(this.connectsNextButton);
      this.addDrawableChild(this.finishButton);
      this.addDrawableChild(this.helpButton);
      GuiHelper.layout(
         0,
         this.height,
         GuiHelper.blankSpace(5),
         GuiHelper.fixedLength(20, GuiHelper.combine(GuiHelper.layoutRectVertically(this.scaleLabelRect), GuiHelper.layoutButtonVertically(this.scaleField))),
         GuiHelper.elasticBlankSpace(),
         GuiHelper.fixedLength(20, GuiHelper.combine(GuiHelper.layoutRectVertically(this.flipLabelRect), GuiHelper.layoutButtonVertically(this.flipButton))),
         GuiHelper.elasticBlankSpace(),
         GuiHelper.fixedLength(
            20,
            GuiHelper.combine(GuiHelper.layoutRectVertically(this.horizontalRotationLabelRect), GuiHelper.layoutButtonVertically(this.horizontalRotationField))
         ),
         GuiHelper.elasticBlankSpace(),
         GuiHelper.fixedLength(20, GuiHelper.combine(GuiHelper.layoutRectVertically(this.topYLabelRect), GuiHelper.layoutButtonVertically(this.topYField))),
         GuiHelper.elasticBlankSpace(),
         GuiHelper.fixedLength(
            20, GuiHelper.combine(GuiHelper.layoutRectVertically(this.bottomYLabelRect), GuiHelper.layoutButtonVertically(this.bottomYField))
         ),
         GuiHelper.elasticBlankSpace(),
         GuiHelper.fixedLength(
            20, GuiHelper.combine(GuiHelper.layoutRectVertically(this.bedrockLabelRect), GuiHelper.layoutButtonVertically(this.bedrockBlockField))
         ),
         GuiHelper.elasticBlankSpace(),
         GuiHelper.fixedLength(
            20, GuiHelper.combine(GuiHelper.layoutRectVertically(this.connectsPreviousRect), GuiHelper.layoutButtonVertically(this.connectsPreviousButton))
         ),
         GuiHelper.elasticBlankSpace(),
         GuiHelper.fixedLength(
            20, GuiHelper.combine(GuiHelper.layoutRectVertically(this.connectsNextRect), GuiHelper.layoutButtonVertically(this.connectsNextButton))
         ),
         GuiHelper.elasticBlankSpace(),
         GuiHelper.fixedLength(20, GuiHelper.layoutButtonVertically(this.finishButton)),
         GuiHelper.blankSpace(5)
      );
      GuiHelper.layout(
         0,
         this.width,
         GuiHelper.elasticBlankSpace(),
         GuiHelper.fixedLength(
            150,
            GuiHelper.combine(
               GuiHelper.layoutRectHorizontally(this.scaleLabelRect),
               GuiHelper.layoutRectHorizontally(this.flipLabelRect),
               GuiHelper.layoutRectHorizontally(this.horizontalRotationLabelRect),
               GuiHelper.layoutRectHorizontally(this.topYLabelRect),
               GuiHelper.layoutRectHorizontally(this.bottomYLabelRect),
               GuiHelper.layoutRectHorizontally(this.bedrockLabelRect),
               GuiHelper.layoutRectHorizontally(this.connectsPreviousRect),
               GuiHelper.layoutRectHorizontally(this.connectsNextRect)
            )
         ),
         GuiHelper.blankSpace(20),
         GuiHelper.fixedLength(
            100,
            GuiHelper.combine(
               GuiHelper.layoutButtonHorizontally(this.scaleField),
               GuiHelper.layoutButtonHorizontally(this.flipButton),
               GuiHelper.layoutButtonHorizontally(this.horizontalRotationField),
               GuiHelper.layoutButtonHorizontally(this.topYField),
               GuiHelper.layoutButtonHorizontally(this.bottomYField),
               GuiHelper.layoutButtonHorizontally(this.bedrockBlockField),
               GuiHelper.layoutButtonHorizontally(this.connectsPreviousButton),
               GuiHelper.layoutButtonHorizontally(this.connectsNextButton)
            )
         ),
         GuiHelper.elasticBlankSpace()
      );
      GuiHelper.layout(
         0,
         this.width,
         GuiHelper.blankSpace(20),
         new GuiHelper.LayoutElement(true, 100, GuiHelper.layoutButtonHorizontally(this.finishButton)),
         GuiHelper.elasticBlankSpace()
      );
      this.helpButton.setX(this.width - 50);
      this.helpButton.setY(5);
      this.helpButton.setWidth(20);
   }

   public void close() {
      MinecraftClient.getInstance().setScreen(this.parent);
   }

   public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
      this.renderBackground(context, mouseX, mouseY, deltaTicks);
      super.render(context, mouseX, mouseY, deltaTicks);
      this.scaleField.render(context, mouseX, mouseY, deltaTicks);
      this.horizontalRotationField.render(context, mouseX, mouseY, deltaTicks);
      this.topYField.render(context, mouseX, mouseY, deltaTicks);
      this.bottomYField.render(context, mouseX, mouseY, deltaTicks);
      this.bedrockBlockField.render(context, mouseX, mouseY, deltaTicks);
      this.connectsPreviousButton.render(context, mouseX, mouseY, deltaTicks);
      this.connectsNextButton.render(context, mouseX, mouseY, deltaTicks);
      this.scaleLabelRect.renderTextLeft(Text.translatable("imm_ptl.scale"), context);
      this.flipLabelRect.renderTextLeft(Text.translatable("imm_ptl.flipped"), context);
      this.horizontalRotationLabelRect.renderTextLeft(Text.translatable("imm_ptl.horizontal_rotation"), context);
      this.topYLabelRect.renderTextLeft(Text.translatable("imm_ptl.top_y"), context);
      this.bottomYLabelRect.renderTextLeft(Text.translatable("imm_ptl.bottom_y"), context);
      this.bedrockLabelRect.renderTextLeft(Text.translatable("imm_ptl.bedrock_replacement"), context);
      this.connectsPreviousRect.renderTextLeft(Text.translatable("imm_ptl.connects_previous"), context);
      this.connectsNextRect.renderTextLeft(Text.translatable("imm_ptl.connects_next"), context);
   }
}
