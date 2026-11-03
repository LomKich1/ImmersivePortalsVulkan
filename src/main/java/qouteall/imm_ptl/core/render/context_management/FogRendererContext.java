package qouteall.imm_ptl.core.render.context_management;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.function.Consumer;
import net.minecraft.util.profiler.Profilers;
import net.minecraft.world.attribute.EnvironmentAttributeInterpolator;
import net.minecraft.world.World;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.registry.RegistryKey;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.render.fog.FogRenderer;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector4f;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.ducks.IECamera;

public class FogRendererContext {
   public float rainFogMultiplier;
   public static Consumer<FogRendererContext> copyContextFromObject;
   public static Consumer<FogRendererContext> copyContextToObject;
   public static StaticFieldsSwappingManager<FogRendererContext> swappingManager;
   @Nullable
   private static FogRenderer fogColorProbe;

   public static void init() {
      swappingManager = new StaticFieldsSwappingManager<>(copyContextFromObject, copyContextToObject, false, FogRendererContext::new);
   }

   public static void update() {
      if (swappingManager != null) {
         swappingManager.setOuterDimension(RenderStates.originalPlayerDimension);
         swappingManager.resetChecks();
         if (ClientWorldLoader.getIsInitialized()) {
            ClientWorldLoader.getClientWorlds()
               .forEach(
                  world -> {
                     RegistryKey<World> dimension = world.getRegistryKey();
                     swappingManager.contextMap
                        .computeIfAbsent(
                           dimension,
                           k -> new StaticFieldsSwappingManager.ContextRecord<>(
                              dimension, new FogRendererContext(), dimension != RenderStates.originalPlayerDimension
                           )
                        );
                  }
               );
         }
      }
   }

   public static Vec3d getCurrentFogColor() {
      return RenderStates.currentFogColor;
   }

   public static Vec3d getFogColorOf(ClientWorld destWorld, Vec3d pos) {
      MinecraftClient client = MinecraftClient.getInstance();
      Profilers.get().push("get_fog_color");
      ClientWorld oldWorld = client.world;
      RegistryKey<World> newWorldKey = destWorld.getRegistryKey();
      swappingManager.contextMap
         .computeIfAbsent(newWorldKey, k -> new StaticFieldsSwappingManager.ContextRecord<>((RegistryKey<World>)k, new FogRendererContext(), true));
      swappingManager.pushSwapping(newWorldKey);
      client.world = destWorld;
      Camera newCamera = new Camera();
      ((IECamera)newCamera).ip_resetState(pos, destWorld);
      ((IECamera)newCamera).portal_setFocusedEntity(client.getCameraEntity());
      ((IECamera)newCamera).portal_tickAttributeProbe();
      GpuBufferSlice oldFog = RenderSystem.getShaderFog();

      Vec3d var8;
      try {
         if (fogColorProbe == null) {
            fogColorProbe = new FogRenderer();
         }

         Vector4f color = fogColorProbe.applyFog(
            newCamera, client.options.getClampedViewDistance(), client.getRenderTickCounter(), client.gameRenderer.getSkyDarkness(RenderStates.getPartialTick()), destWorld
         );
         var8 = new Vec3d(color.x, color.y, color.z);
      } finally {
         RenderSystem.setShaderFog(oldFog);
         swappingManager.popSwapping();
         client.world = oldWorld;
         Profilers.get().pop();
      }

      return var8;
   }

   public static void onPlayerTeleport(RegistryKey<World> from, RegistryKey<World> to) {
      swappingManager.updateOuterDimensionAndChangeContext(to);
      MinecraftClient client = MinecraftClient.getInstance();
      if (client.player != null && client.world != null) {
         EnvironmentAttributeInterpolator probe = client.gameRenderer.getCamera().getEnvironmentAttributeInterpolator();
         probe.clear();
         probe.update(client.world, client.player.getEyePos());
      }
   }
}
