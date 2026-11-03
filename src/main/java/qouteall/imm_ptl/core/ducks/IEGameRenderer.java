package qouteall.imm_ptl.core.ducks;

import com.mojang.blaze3d.systems.ProjectionType;
import net.minecraft.client.render.state.WorldRenderState;
import net.minecraft.client.render.command.OrderedRenderCommandQueueImpl;
import net.minecraft.client.render.command.RenderDispatcher;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.fog.FogRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import org.joml.Matrix4f;

public interface IEGameRenderer {
   void ip_setLightmapTextureManager(LightmapTextureManager var1);

   boolean ip_getDoRenderHand();

   void ip_setDoRenderHand(boolean var1);

   void ip_setCamera(Camera var1);

   WorldRenderState ip_getLevelRenderState();

   void ip_setLevelRenderState(WorldRenderState var1);

   OrderedRenderCommandQueueImpl ip_getSubmitNodeStorage();

   void ip_setSubmitNodeStorage(OrderedRenderCommandQueueImpl var1);

   RenderDispatcher ip_getFeatureRenderDispatcher();

   void ip_setFeatureRenderDispatcher(RenderDispatcher var1);

   FogRenderer ip_getFogRenderer();

   void ip_setFogRenderer(FogRenderer var1);

   void ip_resetProjectionMatrix(Matrix4f var1, ProjectionType var2);
}
