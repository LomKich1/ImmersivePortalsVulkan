package qouteall.imm_ptl.core.portal.custom_portal_gen.form;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Lifecycle;
import com.mojang.serialization.MapCodec;
import java.util.function.Function;
import net.minecraft.entity.Entity;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.registry.SimpleRegistry;
import net.minecraft.registry.Registry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.registry.RegistryKey;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.portal.custom_portal_gen.CustomPortalGeneration;

public abstract class PortalGenForm {
   public static final Registry<MapCodec<? extends PortalGenForm>> CODEC_REGISTRY = (Registry<MapCodec<? extends PortalGenForm>>)Util.make(
      () -> {
         SimpleRegistry<MapCodec<? extends PortalGenForm>> registry = new SimpleRegistry(
            RegistryKey.ofRegistry(McHelper.newResourceLocation("imm_ptl:custom_portal_gen_form")), Lifecycle.stable()
         );
         Registry.register(registry, McHelper.newResourceLocation("imm_ptl:classical"), ClassicalForm.CODEC);
         Registry.register(registry, McHelper.newResourceLocation("imm_ptl:heterogeneous"), HeterogeneousForm.CODEC);
         Registry.register(registry, McHelper.newResourceLocation("imm_ptl:flipping_floor_square"), FlippingFloorSquareForm.CODEC);
         Registry.register(registry, McHelper.newResourceLocation("imm_ptl:scaling_square"), ScalingSquareForm.CODEC);
         Registry.register(registry, McHelper.newResourceLocation("imm_ptl:flipping_floor_square_new"), FlippingFloorSquareNewForm.CODEC);
         Registry.register(registry, McHelper.newResourceLocation("imm_ptl:try_hard_to_match"), DiligentForm.CODEC);
         Registry.register(registry, McHelper.newResourceLocation("imm_ptl:convert_conventional_portal"), ConvertConventionalPortalForm.CODEC);
         Registry.register(registry, McHelper.newResourceLocation("imm_ptl:one_way"), OneWayForm.CODEC);
         return registry;
      }
   );
   public static final Codec<PortalGenForm> GENERAL_CODEC = CODEC_REGISTRY.getCodec().dispatchStable(PortalGenForm::getCodec, Function.identity());

   public abstract MapCodec<? extends PortalGenForm> getCodec();

   public abstract PortalGenForm getReverse();

   public abstract boolean perform(CustomPortalGeneration var1, ServerWorld var2, BlockPos var3, ServerWorld var4, @Nullable Entity var5);
}
