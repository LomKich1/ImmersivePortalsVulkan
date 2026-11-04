package qouteall.imm_ptl.core.render.context_management;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.world.World;
import qouteall.imm_ptl.core.ducks.IEGameRenderer;
import qouteall.q_misc_util.Helper;

public class DimensionRenderHelper {
   private static final MinecraftClient client = MinecraftClient.getInstance();
   public final World world;
   public final LightmapTextureManager lightmapTexture;

   public DimensionRenderHelper(World world) {
      this.world = world;
      if (client.world == world) {
         IEGameRenderer gameRenderer = (IEGameRenderer)(Object)client.gameRenderer;
         this.lightmapTexture = client.gameRenderer.getLightmapTextureManager();
      } else {
         this.lightmapTexture = new LightmapTextureManager(client.gameRenderer, client);
         Helper.log("Created lightmap texture for " + world.getRegistryKey().getValue());
      }
   }

   public void tick() {
      if (this.lightmapTexture != client.gameRenderer.getLightmapTextureManager()) {
         this.lightmapTexture.tick();
      }
   }

   public void cleanUp() {
      if (this.lightmapTexture != client.gameRenderer.getLightmapTextureManager()) {
         this.lightmapTexture.close();
      }
   }
}
