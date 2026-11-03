package qouteall.imm_ptl.core.render.optimization;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.function.Consumer;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;

public class GLResourceCache {
   public static MinecraftClient client = MinecraftClient.getInstance();
   private final Consumer<int[]> generator;
   private final IntList bufferIds = new IntArrayList();
   public static GLResourceCache bufferCache = new GLResourceCache(GL15::glGenBuffers);
   public static GLResourceCache vertexArrayCache = new GLResourceCache(GL30::glGenVertexArrays);

   public GLResourceCache(Consumer<int[]> generator) {
      this.generator = generator;
   }

   public int getNewResourceId() {
      if (this.bufferIds.isEmpty()) {
         this.reserve(1000);
      }

      return this.bufferIds.removeInt(this.bufferIds.size() - 1);
   }

   public static void init() {
   }

   private void reserve(int num) {
      int[] buf = new int[num];
      this.generator.accept(buf);
      this.bufferIds.addElements(this.bufferIds.size(), buf);
   }

   private int getExpectedBufferSize() {
      int viewDistance = client.options.getClampedViewDistance();
      int diameter = viewDistance * 2 + 1;
      return diameter * diameter * 16 * 5 * 4;
   }
}
