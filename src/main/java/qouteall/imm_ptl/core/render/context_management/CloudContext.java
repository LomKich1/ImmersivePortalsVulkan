package qouteall.imm_ptl.core.render.context_management;

import java.util.ArrayList;
import net.minecraft.client.gl.MappableRingBuffer;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.IPCGlobal;
import qouteall.q_misc_util.Helper;

public class CloudContext {
   public int prevCellX = 0;
   public int prevCellZ = 0;
   public int prevRelativeCameraPos = 0;
   @Nullable
   public CloudRenderMode prevType = null;
   public RegistryKey<World> dimension = null;
   public int cloudColor;
   public int quadCount = 0;
   @Nullable
   public MappableRingBuffer cloudsBuffer = null;
   public static final ArrayList<CloudContext> contexts = new ArrayList<>();

   public static void init() {
      IPCGlobal.CLIENT_CLEANUP_EVENT.register(CloudContext::cleanup);
      ClientWorldLoader.CLIENT_DIMENSION_DYNAMIC_REMOVE_EVENT.register(dim -> cleanup());
   }

   private static void cleanup() {
      for (CloudContext context : contexts) {
         context.dispose();
      }

      contexts.clear();
   }

   public void dispose() {
      if (this.cloudsBuffer != null) {
         this.cloudsBuffer.close();
         this.cloudsBuffer = null;
      }
   }

   @Nullable
   public static CloudContext findAndTakeContext(
      int prevCellX, int prevCellZ, int prevRelativeCameraPos, CloudRenderMode prevType, RegistryKey<World> dimension, int cloudColor
   ) {
      int i = Helper.indexOf(
         contexts,
         c -> c.prevCellX == prevCellX
            && c.prevCellZ == prevCellZ
            && c.prevRelativeCameraPos == prevRelativeCameraPos
            && c.prevType == prevType
            && c.dimension == dimension
            && c.cloudColor == cloudColor
      );
      if (i == -1) {
         return null;
      } else {
         CloudContext result = contexts.get(i);
         contexts.remove(i);
         return result;
      }
   }

   public static void appendContext(CloudContext context) {
      contexts.add(context);
      if (contexts.size() > 15) {
         contexts.remove(0).dispose();
      }
   }
}
