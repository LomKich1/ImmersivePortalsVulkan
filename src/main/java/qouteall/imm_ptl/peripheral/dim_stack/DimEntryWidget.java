package qouteall.imm_ptl.peripheral.dim_stack;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.world.World;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.registry.RegistryKey;
import net.minecraft.client.gui.Selectable;
import net.minecraft.client.gui.widget.ElementListWidget.class_4266;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.McHelper;

public class DimEntryWidget extends class_4266<DimEntryWidget> {
   private static final Logger LOGGER = LogUtils.getLogger();
   public final RegistryKey<World> dimension;
   public final DimListWidget parent;
   private final Consumer<DimEntryWidget> selectCallback;
   @Nullable
   private final Identifier dimIconPath;
   private final Text dimensionName;
   @Nullable
   public final DimStackEntry entry;
   public int entryIndex;
   DimEntryWidget.ArrowType arrowToPrevious = DimEntryWidget.ArrowType.none;
   DimEntryWidget.ArrowType arrowToNext = DimEntryWidget.ArrowType.none;
   public static final int widgetHeight = 50;
   private final List<Element> children = new ArrayList<>();

   @NotNull
   public List<? extends Selectable> selectableChildren() {
      return List.of();
   }

   public DimEntryWidget(RegistryKey<World> dimension, DimListWidget parent, Consumer<DimEntryWidget> selectCallback, @Nullable DimStackEntry entry) {
      this.dimension = dimension;
      this.parent = parent;
      this.selectCallback = selectCallback;
      this.dimIconPath = CHelper.getDimensionIconPath(this.dimension);
      this.dimensionName = McHelper.getDimensionName(dimension);
      this.entry = entry;
   }

   @NotNull
   public List<? extends Element> children() {
      return this.children;
   }

   public void render(@NotNull DrawContext guiGraphics, int mouseX, int mouseY, boolean hovered, float delta) {
      int x = this.getX();
      int y = this.getY();
      int rowWidth = this.getWidth();
      MinecraftClient client = MinecraftClient.getInstance();
      guiGraphics.drawTextWithShadow(client.textRenderer, this.dimensionName.getString(), x + 50 + 3, y, -1);
      guiGraphics.drawTextWithShadow(client.textRenderer, this.dimension.getValue().toString(), x + 50 + 3, y + 10, -6710887);
      if (this.dimIconPath != null) {
         guiGraphics.getMatrices().pushMatrix();
         guiGraphics.getMatrices().translate(x, y);
         int iconLen = 46;
         if (this.entry != null && this.entry.flipped) {
            guiGraphics.getMatrices().rotateAbout((float)Math.toRadians(180.0), iconLen / 2.0F, iconLen / 2.0F);
         }

         guiGraphics.drawTexture(RenderPipelines.GUI_TEXTURED, this.dimIconPath, 0, 0, 0.0F, 0.0F, iconLen, iconLen, iconLen, iconLen);
         guiGraphics.getMatrices().popMatrix();
      }

      if (this.entry != null) {
         guiGraphics.drawTextWithShadow(client.textRenderer, this.getText1(), x + 50 + 3, y + 20, -6710887);
         guiGraphics.drawTextWithShadow(client.textRenderer, this.getText2(), x + 50 + 3, y + 30, -6710887);
         if (this.arrowToPrevious != DimEntryWidget.ArrowType.none) {
            guiGraphics.getMatrices().pushMatrix();
            guiGraphics.getMatrices().translate(x + rowWidth - 13, y);
            guiGraphics.getMatrices().scale(1.5F, 1.5F);
            guiGraphics.drawTextWithShadow(
               client.textRenderer, Text.literal("↑"), 0, 0, this.arrowToPrevious == DimEntryWidget.ArrowType.enabled ? -6710887 : -65536
            );
            guiGraphics.getMatrices().popMatrix();
         }

         if (this.arrowToNext != DimEntryWidget.ArrowType.none) {
            guiGraphics.getMatrices().pushMatrix();
            guiGraphics.getMatrices().translate(x + rowWidth - 13, y + 50 - 14.5F);
            guiGraphics.getMatrices().scale(1.5F, 1.5F);
            guiGraphics.drawTextWithShadow(
               client.textRenderer, Text.literal("↓"), 0, 0, this.arrowToNext == DimEntryWidget.ArrowType.enabled ? -6710887 : -65536
            );
            guiGraphics.getMatrices().popMatrix();
         }
      }
   }

   private Text getText1() {
      return this.entry.scale != 1.0
         ? Text.translatable("imm_ptl.scale").append(Text.literal(":" + Double.toString(this.entry.scale)))
         : Text.literal("");
   }

   private Text getText2() {
      return this.entry.horizontalRotation != 0.0
         ? Text.translatable("imm_ptl.horizontal_rotation")
            .append(Text.literal(":" + Double.toString(this.entry.horizontalRotation)))
            .append(Text.literal(" "))
         : Text.literal("");
   }

   public boolean mouseClicked(Click mouseButtonEvent, boolean doubleClick) {
      this.selectCallback.accept(this);
      super.mouseClicked(mouseButtonEvent, doubleClick);
      return true;
   }

   public static enum ArrowType {
      none,
      enabled,
      conflicting;
   }
}
