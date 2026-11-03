package qouteall.imm_ptl.core.ducks;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.render.BufferBuilderStorage;
import net.minecraft.client.render.BuiltChunkStorage;
import net.minecraft.client.render.DefaultFramebufferSet;
import net.minecraft.client.render.SubmittableBatch;
import net.minecraft.client.render.VertexConsumerProvider.Immediate;
import net.minecraft.client.render.chunk.ChunkBuilder.BuiltChunk;
import net.minecraft.client.render.command.OrderedRenderCommandQueueImpl;
import net.minecraft.client.render.command.RenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.state.WorldRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;

public interface IEWorldRenderer {
   EntityRenderManager ip_getEntityRenderDispatcher();

   BuiltChunkStorage ip_getBuiltChunkStorage();

   void ip_renderSingleEntity(Entity var1, double var2, double var4, double var6, float var8, MatrixStack var9, Immediate var10);

   BufferBuilderStorage ip_getRenderBuffers();

   void ip_setRenderBuffers(BufferBuilderStorage var1);

   void portal_fullyDispose();

   void portal_setChunkInfoList(ObjectArrayList<BuiltChunk> var1);

   ObjectArrayList<BuiltChunk> portal_getChunkInfoList();

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
