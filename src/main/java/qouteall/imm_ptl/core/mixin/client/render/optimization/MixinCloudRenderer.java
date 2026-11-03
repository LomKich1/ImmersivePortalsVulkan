package qouteall.imm_ptl.core.mixin.client.render.optimization;

import java.lang.reflect.Field;
import java.util.Arrays;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.MappableRingBuffer;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.render.CloudRenderer;
import net.minecraft.client.render.CloudRenderer.CloudCells;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.miscellaneous.IPVanillaCopy;
import qouteall.imm_ptl.core.render.context_management.CloudContext;
import qouteall.imm_ptl.core.render.context_management.RenderStates;

@Mixin({CloudRenderer.class})
public abstract class MixinCloudRenderer {
   @Shadow
   private boolean rebuild;
   @Shadow
   private int centerX;
   @Shadow
   private int centerZ;
   @Shadow
   @Nullable
   private CloudRenderMode renderMode;
   @Shadow
   @Nullable
   private CloudCells cells;
   @Shadow
   private int instanceCount;
   @Shadow
   @Nullable
   private MappableRingBuffer cloudFacesBuffer;
   @Unique
   private static final Field ip_prevRelativeCameraPosField = Arrays.stream(CloudRenderer.class.getDeclaredFields())
      .filter(f -> f.getType().isEnum() && f.getType().getEnclosingClass() == CloudRenderer.class)
      .findFirst()
      .map(f -> {
         f.setAccessible(true);
         return (Field)f;
      })
      .orElseThrow(() -> new IllegalStateException("Cannot find CloudRenderer.prevRelativeCameraPos"));

   @Unique
   private int ip_getPrevRelativeCameraPos() {
      try {
         return ((Enum)ip_prevRelativeCameraPosField.get(this)).ordinal();
      } catch (IllegalAccessException var2) {
         throw new RuntimeException(var2);
      }
   }

   @Unique
   private void ip_setPrevRelativeCameraPos(int ordinal) {
      try {
         Object[] constants = ip_prevRelativeCameraPosField.getType().getEnumConstants();
         ip_prevRelativeCameraPosField.set(this, constants[ordinal]);
      } catch (IllegalAccessException var3) {
         throw new RuntimeException(var3);
      }
   }

   @Inject(
      method = {"renderClouds"},
      at = {@At("HEAD")}
   )
   private void onBeginRenderClouds(
      int color, CloudRenderMode cloudStatus, float cloudHeight, Vec3d cameraPos, long gameTime, float partialTick, CallbackInfo ci
   ) {
      if (RenderStates.getRenderedPortalNum() != 0) {
         if (IPGlobal.cloudOptimization) {
            this.portal_onBeginCloudRendering(color, cloudStatus, cloudHeight, cameraPos, gameTime, partialTick);
         }
      }
   }

   @Inject(
      method = {"renderClouds"},
      at = {@At("RETURN")}
   )
   private void onEndRenderClouds(int color, CloudRenderMode cloudStatus, float cloudHeight, Vec3d cameraPos, long gameTime, float partialTick, CallbackInfo ci) {
      if (RenderStates.getRenderedPortalNum() != 0) {
         if (IPGlobal.cloudOptimization) {
            this.portal_onEndCloudRendering(color);
         }
      }
   }

   @Unique
   private void portal_yieldCloudContext(CloudContext context, int cloudColor) {
      context.prevCellX = this.centerX;
      context.prevCellZ = this.centerZ;
      context.prevRelativeCameraPos = this.ip_getPrevRelativeCameraPos();
      context.prevType = this.renderMode;
      context.quadCount = this.instanceCount;
      context.cloudsBuffer = this.cloudFacesBuffer;
      context.dimension = MinecraftClient.getInstance().world.getRegistryKey();
      context.cloudColor = cloudColor;
      this.cloudFacesBuffer = null;
      this.rebuild = true;
   }

   @Unique
   private void portal_loadCloudContext(CloudContext context) {
      Validate.isTrue(context.dimension == MinecraftClient.getInstance().world.getRegistryKey());
      if (this.cloudFacesBuffer != null && this.cloudFacesBuffer != context.cloudsBuffer) {
         this.cloudFacesBuffer.close();
      }

      this.centerX = context.prevCellX;
      this.centerZ = context.prevCellZ;
      this.ip_setPrevRelativeCameraPos(context.prevRelativeCameraPos);
      this.renderMode = context.prevType;
      this.instanceCount = context.quadCount;
      this.cloudFacesBuffer = context.cloudsBuffer;
      this.rebuild = false;
   }

   @Unique
   @IPVanillaCopy
   private void portal_onBeginCloudRendering(int color, CloudRenderMode cloudStatus, float cloudHeight, Vec3d cameraPos, long gameTime, float partialTick) {
      if (this.cells != null) {
         float f = (float)(cloudHeight - cameraPos.y);
         float f1 = f + 4.0F;
         int relativeCameraPos;
         if (f1 < 0.0F) {
            relativeCameraPos = 0;
         } else if (f > 0.0F) {
            relativeCameraPos = 2;
         } else {
            relativeCameraPos = 1;
         }

         float f2 = (float)(gameTime % (this.cells.width() * 400L)) + partialTick;
         double d0 = cameraPos.x + f2 * 0.030000001F;
         double d1 = cameraPos.z + 3.96F;
         double d2 = this.cells.width() * 12.0;
         double d3 = this.cells.height() * 12.0;
         d0 -= MathHelper.floor(d0 / d2) * d2;
         d1 -= MathHelper.floor(d1 / d3) * d3;
         int cellX = MathHelper.floor(d0 / 12.0);
         int cellZ = MathHelper.floor(d1 / 12.0);
         CloudContext context = CloudContext.findAndTakeContext(
            cellX, cellZ, relativeCameraPos, cloudStatus, MinecraftClient.getInstance().world.getRegistryKey(), color
         );
         if (context != null) {
            this.portal_loadCloudContext(context);
         }
      }
   }

   @Unique
   private void portal_onEndCloudRendering(int color) {
      if (!this.rebuild && this.cloudFacesBuffer != null) {
         CloudContext newContext = new CloudContext();
         this.portal_yieldCloudContext(newContext, color);
         CloudContext.appendContext(newContext);
      }
   }
}
