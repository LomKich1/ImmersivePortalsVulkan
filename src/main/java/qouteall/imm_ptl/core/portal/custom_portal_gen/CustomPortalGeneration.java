package qouteall.imm_ptl.core.portal.custom_portal_gen;

import com.google.common.collect.Lists;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Lifecycle;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.minecraft.entity.Entity;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.SimpleRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.profiler.Profilers;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.custom_portal_gen.form.PortalGenForm;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.MiscHelper;

public class CustomPortalGeneration {
   public static final RegistryKey<World> THE_SAME_DIMENSION = RegistryKey.of(RegistryKeys.WORLD, McHelper.newResourceLocation("imm_ptl:the_same_dimension"));
   public static final RegistryKey<World> ANY_DIMENSION = RegistryKey.of(RegistryKeys.WORLD, McHelper.newResourceLocation("imm_ptl:any_dimension"));
   public static final Codec<List<RegistryKey<World>>> DIMENSION_LIST_CODEC = World.CODEC.listOf();
   public static final Codec<List<String>> STRING_LIST_CODEC = Codec.STRING.listOf();
   public static final Codec<List<List<String>>> STRING_LIST_LIST_CODEC = STRING_LIST_CODEC.listOf();
   public static final RegistryKey<Registry<MapCodec<CustomPortalGeneration>>> SCHEMA_KEY = RegistryKey.ofRegistry(
      McHelper.newResourceLocation("imm_ptl:custom_portal_gen_schema")
   );
   public static final RegistryKey<Registry<CustomPortalGeneration>> REGISTRY_KEY = RegistryKey.ofRegistry(
      McHelper.newResourceLocation("immersive_portals:custom_portal_generation")
   );
   public static final RegistryKey<Registry<CustomPortalGeneration>> LEGACY_REGISTRY_KEY = RegistryKey.ofRegistry(
      McHelper.newResourceLocation("custom_portal_generation")
   );
   public static final MapCodec<CustomPortalGeneration> codecV1 = RecordCodecBuilder.mapCodec(
      instance -> instance.group(
            DIMENSION_LIST_CODEC.fieldOf("from").forGetter(o -> o.fromDimensions),
            World.CODEC.fieldOf("to").forGetter(o -> o.toDimension),
            Codec.INT.optionalFieldOf("space_ratio_from", 1).forGetter(o -> o.spaceRatioFrom),
            Codec.INT.optionalFieldOf("space_ratio_to", 1).forGetter(o -> o.spaceRatioTo),
            Codec.BOOL.optionalFieldOf("reversible", true).forGetter(o -> o.reversible),
            PortalGenForm.GENERAL_CODEC.fieldOf("form").forGetter(o -> o.form),
            PortalGenTrigger.triggerCodec.fieldOf("trigger").forGetter(o -> o.trigger),
            STRING_LIST_CODEC.optionalFieldOf("post_invoke_commands", Collections.emptyList()).forGetter(o -> o.postInvokeCommands),
            STRING_LIST_LIST_CODEC.optionalFieldOf("commands_on_generated", Collections.emptyList()).forGetter(o -> o.commandsOnGenerated)
         )
         .apply(instance, instance.stable(CustomPortalGeneration::new))
   );
   private static final SimpleRegistry<MapCodec<CustomPortalGeneration>> SCHEMA_REGISTRY = (SimpleRegistry<MapCodec<CustomPortalGeneration>>)Util.make(() -> {
      SimpleRegistry<MapCodec<CustomPortalGeneration>> registry = new SimpleRegistry(SCHEMA_KEY, Lifecycle.stable());
      Registry.register(registry, McHelper.newResourceLocation("imm_ptl:v1"), codecV1);
      return registry;
   });
   public static final MapCodec<CustomPortalGeneration> MAP_CODEC = SCHEMA_REGISTRY.getCodec().dispatchMap("schema_version", e -> codecV1, Function.identity());
   public static final Codec<CustomPortalGeneration> CODEC = MAP_CODEC.codec();
   public final List<RegistryKey<World>> fromDimensions;
   public final RegistryKey<World> toDimension;
   public final int spaceRatioFrom;
   public final int spaceRatioTo;
   public final boolean reversible;
   public final PortalGenForm form;
   public final PortalGenTrigger trigger;
   public final List<String> postInvokeCommands;
   public final List<List<String>> commandsOnGenerated;
   public Identifier identifier = null;

   public CustomPortalGeneration(
      List<RegistryKey<World>> fromDimensions,
      RegistryKey<World> toDimension,
      int spaceRatioFrom,
      int spaceRatioTo,
      boolean reversible,
      PortalGenForm form,
      PortalGenTrigger trigger,
      List<String> postInvokeCommands,
      List<List<String>> commandsOnGenerated
   ) {
      this.fromDimensions = fromDimensions;
      this.toDimension = toDimension;
      this.spaceRatioFrom = spaceRatioFrom;
      this.spaceRatioTo = spaceRatioTo;
      this.reversible = reversible;
      this.form = form;
      this.trigger = trigger;
      this.postInvokeCommands = postInvokeCommands;
      this.commandsOnGenerated = commandsOnGenerated;
   }

   @Nullable
   public CustomPortalGeneration getReverse() {
      if (this.toDimension == THE_SAME_DIMENSION) {
         return new CustomPortalGeneration(
            this.fromDimensions,
            THE_SAME_DIMENSION,
            this.spaceRatioTo,
            this.spaceRatioFrom,
            false,
            this.form.getReverse(),
            this.trigger,
            this.postInvokeCommands,
            this.commandsOnGenerated
         );
      } else if (!this.fromDimensions.isEmpty()) {
         return new CustomPortalGeneration(
            Lists.newArrayList(new RegistryKey[]{this.toDimension}),
            this.fromDimensions.get(0),
            this.spaceRatioTo,
            this.spaceRatioFrom,
            false,
            this.form.getReverse(),
            this.trigger,
            this.postInvokeCommands,
            this.commandsOnGenerated
         );
      } else {
         Helper.err("Cannot get reverse custom portal gen");
         return null;
      }
   }

   public BlockPos mapPosition(BlockPos from, ServerWorld fromWorld, ServerWorld toWorld) {
      BlockPos newPosition = Helper.divide(Helper.scale(from, this.spaceRatioTo), this.spaceRatioFrom);
      boolean withinBounds = toWorld.getWorldBorder().contains(newPosition);
      if (!withinBounds) {
         Helper.log("Tries to spawn a portal outside of world border");
         BlockPos clamped = toWorld.getWorldBorder().clampFloored(newPosition.getX(), newPosition.getY(), newPosition.getZ());
         newPosition = new BlockPos((int)(clamped.getX() * 0.9), clamped.getY(), (int)(clamped.getZ() * 0.9));
      }

      return newPosition;
   }

   public CustomPortalGeneration.InitializationResult initAndCheck(MinecraftServer server) {
      RegistryKey<World> toDimension = this.toDimension;
      if (toDimension != THE_SAME_DIMENSION && server.getWorld(toDimension) == null) {
         return new CustomPortalGeneration.NotLoadedBecauseOfDstDimensionInvalid(toDimension);
      } else {
         Set<RegistryKey<World>> effectiveSrcDimensions = this.fromDimensions
            .stream()
            .filter(dim -> dim == ANY_DIMENSION || server.getWorld(dim) != null)
            .collect(Collectors.toSet());
         return (CustomPortalGeneration.InitializationResult)(effectiveSrcDimensions.isEmpty()
            ? new CustomPortalGeneration.NotLoadedBecauseNoSrcDimensionValid(this.fromDimensions)
            : new CustomPortalGeneration.InitializationOk());
      }
   }

   @Override
   public String toString() {
      return McHelper.serializeToJson(this, MAP_CODEC.codec());
   }

   public boolean perform(ServerWorld world, BlockPos startPos, @Nullable Entity triggeringEntity) {
      if (!this.fromDimensions.contains(world.getRegistryKey()) && this.fromDimensions.get(0) != ANY_DIMENSION) {
         return false;
      } else if (!world.isChunkLoaded(startPos)) {
         Helper.log("Skip custom portal generation because chunk not loaded");
         return false;
      } else {
         RegistryKey<World> destDimension = this.toDimension;
         if (destDimension == THE_SAME_DIMENSION) {
            destDimension = world.getRegistryKey();
         }

         ServerWorld toWorld = MiscHelper.getServer().getWorld(destDimension);
         if (toWorld == null) {
            Helper.err("Missing dimension " + destDimension.getValue());
            return false;
         } else {
            Profilers.get().push("custom_portal_gen_perform");
            boolean result = this.form.perform(this, world, startPos, toWorld, triggeringEntity);
            Profilers.get().pop();
            return result;
         }
      }
   }

   public void onPortalsGenerated(Portal[] portals) {
      for (int i = 0; i < portals.length; i++) {
         Portal portal = portals[i];
         if (this.identifier != null) {
            portal.portalTag = this.identifier.toString();
         }

         if (!this.postInvokeCommands.isEmpty()) {
            McHelper.invokeCommandAs(portal, this.postInvokeCommands);
         }

         if (i < this.commandsOnGenerated.size()) {
            List<String> commandsForThisPortal = this.commandsOnGenerated.get(i);
            McHelper.invokeCommandAs(portal, commandsForThisPortal);
         }
      }
   }

   public record InitializationOk() implements CustomPortalGeneration.InitializationResult {
   }

   public sealed interface InitializationResult
      permits CustomPortalGeneration.InitializationOk,
      CustomPortalGeneration.NotLoadedBecauseOfDstDimensionInvalid,
      CustomPortalGeneration.NotLoadedBecauseNoSrcDimensionValid {
   }

   public record NotLoadedBecauseNoSrcDimensionValid(Collection<RegistryKey<World>> srcDimIds) implements CustomPortalGeneration.InitializationResult {
      @Override
      public String toString() {
         return "No source dimension is loaded %s".formatted(this.srcDimIds.stream().map(RegistryKey::getValue).collect(Collectors.toList()));
      }
   }

   public record NotLoadedBecauseOfDstDimensionInvalid(RegistryKey<World> dimId) implements CustomPortalGeneration.InitializationResult {
      @Override
      public String toString() {
         return "Destination dimension %s not loaded".formatted(this.dimId.getValue());
      }
   }
}
