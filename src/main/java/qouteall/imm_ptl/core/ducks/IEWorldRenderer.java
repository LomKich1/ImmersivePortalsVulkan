package qouteall.imm_ptl.core.ducks;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.render.state.WorldRenderState;
import net.minecraft.client.render.command.OrderedRenderCommandQueueImpl;
import net.minecraft.client.render.command.RenderDispatcher;
import net.minecraft.client.render.SubmittableBatch;
import net.minecraft.entity.Entity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.BufferBuilderStorage;
import net.minecraft.client.render.BuiltChunkStorage;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.DefaultFramebufferSet;
import net.minecraft.client.render.VertexConsumerProvider.class_4598;
import net.minecraft.client.render.chunk.ChunkBuilder.class_851;

public interface IEWorldRenderer {
   EntityRenderManager ip_getEntityRenderDispatcher();

   BuiltChunkStorage ip_getBuiltChunkStorage();

   void ip_renderSingleEntity(Entity var1, double var2, double var4, double var6, float var8, MatrixStack var9, class_4598 var10);

   BufferBuilderStorage ip_getRenderBuffers();

   void ip_setRenderBuffers(BufferBuilderStorage var1);

   void portal_fullyDispose();

   void portal_setChunkInfoList(ObjectArrayList<class_851> var1);

   ObjectArrayList<class_851> portal_getChunkInfoList();

   WorldRenderState ip_getLevelRenderState();

   void ip_setLevelRenderState(WorldRenderState var1);

   OrderedRenderCommandQueueImpl ip_getSubmitNodeStorage();

   void ip_setSubmitNodeStorage(OrderedRenderCommandQueueImpl var1);

   RenderDispatcher ip_getFeatureRenderDispatcher();

   void ip_setFeatureRenderDispatcher(RenderDispatcher var1);

   SubmittableBatch ip_getParticlesRenderState();

   void ip_setParticlesRenderState(SubmittableBatch var1);

   DefaultFramebufferSet ip_getTargets();
}
