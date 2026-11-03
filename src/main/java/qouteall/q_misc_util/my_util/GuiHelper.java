package qouteall.q_misc_util.my_util;

import java.util.Arrays;
import net.minecraft.text.Text;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;

public class GuiHelper {
   public static GuiHelper.LayoutFunc layoutButtonHorizontally(ClickableWidget widget) {
      return (a, b) -> {
         widget.setX(a);
         widget.setWidth(b - a);
      };
   }

   public static GuiHelper.LayoutFunc layoutRectHorizontally(GuiHelper.Rect widget) {
      return (a, b) -> {
         widget.xMin = a;
         widget.xMax = b;
      };
   }

   public static GuiHelper.LayoutFunc layoutButtonVertically(ClickableWidget widget) {
      return (a, b) -> widget.setY(a);
   }

   public static GuiHelper.LayoutFunc layoutRectVertically(GuiHelper.Rect widget) {
      return (a, b) -> {
         widget.yMin = a;
         widget.yMax = b;
      };
   }

   public static void layout(int from, int to, GuiHelper.LayoutElement... elements) {
      int totalElasticWeight = Arrays.stream(elements).filter(e -> !e.fixedLength).mapToInt(e -> e.length).sum();
      int totalFixedLen = Arrays.stream(elements).filter(e -> e.fixedLength).mapToInt(e -> e.length).sum();
      int totalEscalateLen = to - from - totalFixedLen;
      int currCoordinate = from;

      for (GuiHelper.LayoutElement element : elements) {
         int currLen;
         if (element.fixedLength) {
            currLen = element.length;
         } else {
            currLen = element.length * totalEscalateLen / totalElasticWeight;
         }

         element.apply.apply(currCoordinate, currCoordinate + currLen);
         currCoordinate += currLen;
      }
   }

   public static GuiHelper.LayoutElement blankSpace(int length) {
      return new GuiHelper.LayoutElement(true, length, (a, b) -> {});
   }

   public static GuiHelper.LayoutElement elasticBlankSpace() {
      return new GuiHelper.LayoutElement(false, 1, (a, b) -> {});
   }

   public static GuiHelper.LayoutFunc combine(GuiHelper.LayoutFunc... args) {
      return (from, to) -> {
         for (GuiHelper.LayoutFunc layoutFunc : args) {
            layoutFunc.apply(from, to);
         }
      };
   }

   public static GuiHelper.LayoutElement elastic(int weight, GuiHelper.LayoutFunc layoutFunc) {
      return new GuiHelper.LayoutElement(false, weight, layoutFunc);
   }

   public static GuiHelper.LayoutElement fixedLength(int length, GuiHelper.LayoutFunc layoutFunc) {
      return new GuiHelper.LayoutElement(true, length, layoutFunc);
   }

   public static class LayoutElement {
      public boolean fixedLength;
      public int length;
      public GuiHelper.LayoutFunc apply;

      public LayoutElement(boolean fixedLength, int length, GuiHelper.LayoutFunc apply) {
         this.fixedLength = fixedLength;
         this.length = length;
         this.apply = apply;
      }
   }

   public interface LayoutFunc {
      void apply(int var1, int var2);
   }

   public static class Rect {
      public float xMin;
      public float yMin;
      public float xMax;
      public float yMax;

      public Rect(float xMin, float yMin, float xMax, float yMax) {
         this.xMin = xMin;
         this.yMin = yMin;
         this.xMax = xMax;
         this.yMax = yMax;
      }

      public Rect() {
         this(0.0F, 0.0F, 0.0F, 0.0F);
      }

      public void renderTextLeft(Text text, DrawContext guiGraphics) {
         TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
         guiGraphics.drawTextWithShadow(textRenderer, text, (int)this.xMin, (int)this.yMin + 5, -1);
      }
   }
}
