package qouteall.q_misc_util;

import java.util.TreeMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.Alignment;
import net.minecraft.client.font.MultilineText;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.profiler.Profilers;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class CustomTextOverlay {
   private static final TreeMap<String, CustomTextOverlay.Entry> ENTRIES = new TreeMap<>();
   private static final boolean renderAtBottomCenter = true;
   @Nullable
   private static MultilineText multiLineLabelCache;

   public static void putText(Text component, double durationSeconds, String key) {
      ENTRIES.put(key, new CustomTextOverlay.Entry(component, System.nanoTime() + Helper.secondToNano(durationSeconds)));
      multiLineLabelCache = null;
   }

   public static void putText(Text component, double durationSeconds) {
      putText(component, durationSeconds, "5_defaultKey");
   }

   public static void putText(Text component, String key) {
      putText(component, 0.2, key);
   }

   public static void putText(Text component) {
      putText(component, 0.2, "5_defaultKey");
   }

   public static boolean remove(String key) {
      return ENTRIES.remove(key) != null;
   }

   public static void render(DrawContext guiGraphics, RenderTickCounter deltaTracker) {
      long currTime = System.nanoTime();
      boolean removes = ENTRIES.entrySet().removeIf(e -> e.getValue().clearingTime < currTime);
      if (removes) {
         multiLineLabelCache = null;
      }

      if (!ENTRIES.isEmpty()) {
         if (multiLineLabelCache == null) {
            MutableText component = Text.empty();
            boolean isBeginning = true;

            for (CustomTextOverlay.Entry entry : ENTRIES.values()) {
               if (isBeginning) {
                  isBeginning = false;
               } else {
                  component.append("\n");
               }

               component.append(entry.component());
            }

            multiLineLabelCache = MultilineText.create(
               MinecraftClient.getInstance().textRenderer, component, MinecraftClient.getInstance().getWindow().getScaledWidth() - 20
            );

            assert multiLineLabelCache != null;
         }

         MinecraftClient minecraft = MinecraftClient.getInstance();
         guiGraphics.getMatrices().pushMatrix();
         int guiScaledWidth = minecraft.getWindow().getScaledWidth();
         int guiScaledHeight = minecraft.getWindow().getScaledHeight();
         TextRenderer font = minecraft.inGameHud.getTextRenderer();
         Profilers.get().push("imm_ptl_custom_overlay");
         multiLineLabelCache.draw(Alignment.CENTER, guiScaledWidth / 2, (int)(guiScaledHeight * 0.75), 9, guiGraphics.getTextConsumer());
         guiGraphics.getMatrices().popMatrix();
         Profilers.get().pop();
      }
   }

   public record Entry(Text component, long clearingTime) {
   }
}
