package qouteall.imm_ptl.peripheral.mixin.client.dim_stack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.client.gui.screen.world.CreateWorldCallback;
import net.minecraft.world.World;
import net.minecraft.text.Text;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.gen.WorldPreset;
import net.minecraft.client.world.GeneratorOptionsHolder;
import net.minecraft.world.dimension.DimensionOptionsRegistryHolder;
import net.minecraft.client.gui.screen.world.WorldCreator;
import net.minecraft.registry.DynamicRegistryManager.class_6890;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.peripheral.dim_stack.DimStackGuiController;
import qouteall.imm_ptl.peripheral.dim_stack.DimStackManagement;
import qouteall.imm_ptl.peripheral.dim_stack.DimensionStackAPI;
import qouteall.imm_ptl.peripheral.ducks.IECreateWorldScreen;
import qouteall.q_misc_util.Helper;

@Mixin({CreateWorldScreen.class})
public abstract class MixinCreateWorldScreen_CVB extends Screen implements IECreateWorldScreen {
   @Shadow
   @Final
   private static Logger LOGGER;
   @Shadow
   @Final
   private WorldCreator worldCreator;
   @Nullable
   private DimStackGuiController ip_dimStackController;

   protected MixinCreateWorldScreen_CVB(Text title) {
      super(title);
      throw new RuntimeException();
   }

   @Inject(
      method = {"<init>"},
      at = {@At("RETURN")}
   )
   private void onInitEnd(
      MinecraftClient minecraft,
      Runnable onClose,
      GeneratorOptionsHolder worldCreationContext,
      Optional<RegistryKey<WorldPreset>> optional,
      OptionalLong optionalLong,
      CreateWorldCallback callback,
      CallbackInfo ci
   ) {
      DimStackManagement.dimStackToApply = DimStackManagement.getDimStackPreset();
      if (DimStackManagement.dimStackToApply != null) {
         LOGGER.info("[ImmPtl] Applying dimension stack preset");
      }
   }

   @Override
   public void ip_openDimStackScreen() {
      if (this.ip_dimStackController == null) {
         CreateWorldScreen this_ = (CreateWorldScreen)this;
         this.ip_dimStackController = new DimStackGuiController(this_, () -> this.portal_getDimensionList(), info -> {
            DimStackManagement.dimStackToApply = info;
            MinecraftClient.getInstance().setScreen(this_);
         });
         this.ip_dimStackController.initializeAsDefault();
      }

      MinecraftClient.getInstance().setScreen(this.ip_dimStackController.view);
   }

   private List<RegistryKey<World>> portal_getDimensionList() {
      Helper.log("Getting the dimension list");
      Set<RegistryKey<World>> result = new LinkedHashSet<>();

      try {
         GeneratorOptionsHolder settings = this.worldCreator.getGeneratorOptionsHolder();
         class_6890 registryAccess = settings.getCombinedRegistryManager();
         DimensionOptionsRegistryHolder selectedDimensions = settings.comp_1028();

         for (Entry<RegistryKey<DimensionOptions>, DimensionOptions> entry : selectedDimensions.comp_1014().entrySet()) {
            result.add(Helper.dimIdToKey(entry.getKey().getValue()));
         }

         for (Entry<RegistryKey<DimensionOptions>, DimensionOptions> entry : settings.comp_1027().getEntrySet()) {
            result.add(Helper.dimIdToKey(entry.getKey().getValue()));
         }

         Collection<RegistryKey<World>> other = DimensionStackAPI.DIMENSION_STACK_CANDIDATE_COLLECTION_EVENT
            .invoker()
            .getExtraDimensionKeys(registryAccess, settings.comp_616());
         result.addAll(other);
      } catch (Exception var7) {
         LOGGER.error("ImmPtl getting dimension list", var7);
         if (result.isEmpty()) {
            result.add(Helper.dimIdToKey("error:error"));
         }
      }

      return new ArrayList<>(result);
   }
}
