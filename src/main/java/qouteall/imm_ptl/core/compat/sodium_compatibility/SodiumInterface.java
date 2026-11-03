package qouteall.imm_ptl.core.compat.sodium_compatibility;






import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.world.ClientWorld;
import org.jetbrains.annotations.Nullable;

import qouteall.imm_ptl.core.render.FrustumCuller;

@Environment(EnvType.CLIENT)
public class SodiumInterface {
   @Nullable
   public static FrustumCuller frustumCuller = null;
   public static SodiumInterface.Invoker invoker = new SodiumInterface.Invoker();

   public static class Invoker {
      public boolean isSodiumPresent() {
         return false;
      }

      public Object createNewContext(int renderDistance) {
         return null;
      }

      public void switchContextWithCurrentWorldRenderer(Object context) {
      }

      public void markSpriteActive(Sprite sprite) {
      }

      public void onClientChunkLoaded(ClientWorld world, int chunkX, int chunkZ) {
      }

      public void onClientChunkUnloaded(ClientWorld world, int chunkX, int chunkZ) {
      }
   }

}
