package qouteall.imm_ptl.peripheral.mixin.common.dim_stack;

import java.util.concurrent.CompletableFuture;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkGenerating;
import net.minecraft.world.chunk.ChunkGenerationContext;
import net.minecraft.world.chunk.AbstractChunkHolder;
import net.minecraft.util.collection.BoundedRegionArray;
import net.minecraft.world.chunk.ChunkGenerationStep;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.peripheral.dim_stack.DimStackManagement;

@Mixin({ChunkGenerating.class})
public class MixinChunkStatusTasks_BedrockReplacement {
   @Inject(
      method = {"generateEntities"},
      at = {@At("HEAD")}
   )
   private static void onGenerateSpawn(
      ChunkGenerationContext worldGenContext,
      ChunkGenerationStep chunkStep,
      BoundedRegionArray<AbstractChunkHolder> staticCache2D,
      Chunk chunkAccess,
      CallbackInfoReturnable<CompletableFuture<Chunk>> cir
   ) {
      DimStackManagement.replaceBedrock(worldGenContext.comp_2434(), chunkAccess);
   }
}
