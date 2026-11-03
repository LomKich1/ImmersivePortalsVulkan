package qouteall.imm_ptl.core.miscellaneous;

import net.minecraft.client.font.DrawnTextConsumer;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextWidget;
import net.minecraft.client.gui.widget.MultilineTextWidget;
import net.minecraft.client.gui.widget.ThreePartsLayoutWidget;
import net.minecraft.client.gui.widget.IconWidget;
import net.minecraft.client.gui.widget.DirectionalLayoutWidget;
import qouteall.imm_ptl.core.platform_specific.IPConfig;

public class IPortalInitialScreen extends Screen {
   private static final int PAGE_NUM = 4;
   private final Runnable onClose;
   private final ButtonWidget prevButton;
   private final TextWidget pageNumberWidget;
   private final ButtonWidget iKnowButton;
   private final IconWidget iconWidget;
   private final TextWidget titleWidget;
   private final MultilineTextWidget contentWidget;
   private int currentPageIndex = 0;

   public IPortalInitialScreen(Runnable onClose) {
      super(Text.empty());
      this.onClose = onClose;
      this.prevButton = ButtonWidget.builder(Text.translatable("iportal.initial_screen.prev"), button -> this.onPrevious()).build();
      this.pageNumberWidget = new IPortalInitialScreen.CenteredStringWidget(Text.empty(), this.textRenderer);
      this.iKnowButton = ButtonWidget.builder(Text.translatable("iportal.initial_screen.i_know"), button -> this.onIKnow()).build();
      this.iconWidget = IconWidget.create(30, 30, Identifier.of("immersive_portals", "icon.png"), 30, 30);
      this.titleWidget = new IPortalInitialScreen.CenteredStringWidget(Text.translatable("iportal.initial_screen.title"), this.textRenderer);
      this.contentWidget = new MultilineTextWidget(Text.empty(), this.textRenderer);
   }

   private void onPrevious() {
      if (this.currentPageIndex > 0) {
         this.currentPageIndex--;
         this.updateUiStatus(this.currentPageIndex);
      }
   }

   private void onIKnow() {
      if (this.currentPageIndex == 3) {
         IPConfig config = IPConfig.getConfig();
         config.initialScreenShown = true;
         config.saveConfigFile();
         this.close();
      } else {
         this.currentPageIndex++;
         this.updateUiStatus(this.currentPageIndex);
      }
   }

   private void updateUiStatus(int newPageIndex) {
      this.currentPageIndex = newPageIndex;
      this.prevButton.visible = newPageIndex > 0;
      this.pageNumberWidget.setMessage(Text.literal("%d / %d".formatted(newPageIndex + 1, 4)));
      this.contentWidget.setMessage(Text.translatable("iportal.initial_screen.content." + newPageIndex));
   }

   public void close() {
      this.onClose.run();
   }

   public void init() {
      this.contentWidget.setMaxWidth(this.width - 40);
      this.pageNumberWidget.setWidth(50);
      this.pageNumberWidget.setHeight(this.iKnowButton.getHeight());
      this.titleWidget.setHeight(this.iconWidget.getHeight());
      this.addDrawableChild(this.prevButton);
      this.addDrawableChild(this.pageNumberWidget);
      this.addDrawableChild(this.iKnowButton);
      this.addDrawableChild(this.iconWidget);
      this.addDrawableChild(this.titleWidget);
      this.addDrawableChild(this.contentWidget);
      ThreePartsLayoutWidget layout = new ThreePartsLayoutWidget(this, 60, 33);
      DirectionalLayoutWidget headerLayout = (DirectionalLayoutWidget)layout.addHeader(DirectionalLayoutWidget.horizontal());
      headerLayout.add(this.iconWidget, headerLayout.getMainPositioner().margin(5));
      headerLayout.add(this.titleWidget, headerLayout.getMainPositioner().margin(5));
      DirectionalLayoutWidget contentLayout = (DirectionalLayoutWidget)layout.addBody(DirectionalLayoutWidget.vertical());
      contentLayout.add(this.contentWidget, headerLayout.getMainPositioner().margin(5));
      DirectionalLayoutWidget footerLayout = (DirectionalLayoutWidget)layout.addFooter(DirectionalLayoutWidget.horizontal());
      footerLayout.add(this.prevButton, footerLayout.getMainPositioner().margin(5));
      footerLayout.add(this.pageNumberWidget, footerLayout.getMainPositioner().margin(5));
      footerLayout.add(this.iKnowButton, footerLayout.getMainPositioner().margin(5));
      layout.refreshPositions();
      this.updateUiStatus(this.currentPageIndex);
   }

   private static class CenteredStringWidget extends TextWidget {
      public CenteredStringWidget(Text message, TextRenderer font) {
         super(message, font);
      }

      public void draw(DrawnTextConsumer collector) {
         Text message = this.getMessage();
         int textWidth = this.getTextRenderer().getWidth(message);
         int x = this.getX() + Math.round(0.5F * (this.getWidth() - textWidth));
         int y = this.getY() + (this.getHeight() - 9) / 2;
         collector.text(x, y, message.asOrderedText());
      }
   }
}
