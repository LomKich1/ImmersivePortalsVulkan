package qouteall.imm_ptl.peripheral.portal_generation;

import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Optional;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.dimension.NetherPortal;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.Direction.class_2351;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.portal.custom_portal_gen.CustomPortalGeneration;

public class IntrinsicPortalGeneration {
   public static final IntrinsicNetherPortalForm intrinsicNetherPortalForm = new IntrinsicNetherPortalForm();
   public static final DiligentNetherPortalForm diligentNetherPortalForm = new DiligentNetherPortalForm();
   public static final PortalHelperForm portalHelperForm = new PortalHelperForm();
   public static final CustomPortalGeneration intrinsicToNether = new CustomPortalGeneration(
      Lists.newArrayList(new RegistryKey[]{World.OVERWORLD}),
      World.NETHER,
      8,
      1,
      true,
      intrinsicNetherPortalForm,
      null,
      new ArrayList<>(),
      new ArrayList<>()
   );
   public static final CustomPortalGeneration intrinsicFromNether = intrinsicToNether.getReverse();
   public static final CustomPortalGeneration diligentToNether = new CustomPortalGeneration(
      Lists.newArrayList(new RegistryKey[]{World.OVERWORLD}),
      World.NETHER,
      8,
      1,
      true,
      diligentNetherPortalForm,
      null,
      new ArrayList<>(),
      new ArrayList<>()
   );
   public static final CustomPortalGeneration diligentFromNether = diligentToNether.getReverse();
   public static final CustomPortalGeneration portalHelper = new CustomPortalGeneration(
      Lists.newArrayList(new RegistryKey[]{CustomPortalGeneration.ANY_DIMENSION}),
      CustomPortalGeneration.THE_SAME_DIMENSION,
      1,
      1,
      false,
      portalHelperForm,
      null,
      new ArrayList<>(),
      new ArrayList<>()
   );

   public static void init() {
      intrinsicToNether.identifier = McHelper.newResourceLocation("imm_ptl:intrinsic_nether_portal");
      intrinsicFromNether.identifier = McHelper.newResourceLocation("imm_ptl:intrinsic_nether_portal");
      diligentFromNether.identifier = McHelper.newResourceLocation("imm_ptl:intrinsic_diligent_nether_portal");
      diligentToNether.identifier = McHelper.newResourceLocation("imm_ptl:intrinsic_diligent_nether_portal");
      portalHelper.identifier = McHelper.newResourceLocation("imm_ptl:intrinsic_portal_helper");
   }

   public static boolean onFireLitOnObsidian(ServerWorld fromWorld, BlockPos firePos, @Nullable Entity triggeringEntity) {
      IPGlobal.NetherPortalMode mode = IPGlobal.netherPortalMode;
      if (mode == IPGlobal.NetherPortalMode.normal || mode == IPGlobal.NetherPortalMode.adaptive) {
         RegistryKey<World> fromDimension = fromWorld.getRegistryKey();
         if (fromDimension == World.OVERWORLD) {
            CustomPortalGeneration gen = mode == IPGlobal.NetherPortalMode.normal ? intrinsicToNether : diligentToNether;
            return gen.perform(fromWorld, firePos, triggeringEntity);
         }

         if (fromDimension == World.NETHER) {
            CustomPortalGeneration gen = mode == IPGlobal.NetherPortalMode.normal ? intrinsicFromNether : diligentFromNether;
            return gen.perform(fromWorld, firePos, triggeringEntity);
         }
      }

      return false;
   }

   public static boolean activatePortalHelper(ServerWorld fromWorld, BlockPos firePos) {
      return portalHelper.perform(fromWorld, firePos, null);
   }

   public static boolean onCrouchingPlayerIgnite(ServerWorld world, ServerPlayerEntity player, BlockPos firePos) {
      if (IPGlobal.netherPortalMode == IPGlobal.NetherPortalMode.disabled) {
         return false;
      } else if (!IPGlobal.lightVanillaNetherPortalWhenCrouching) {
         return false;
      } else {
         boolean dimensionCorrect = world.getRegistryKey() == World.OVERWORLD || world.getRegistryKey() == World.NETHER;
         if (!dimensionCorrect) {
            return false;
         } else {
            Optional<NetherPortal> newPortal = NetherPortal.getNewPortal(world, firePos, class_2351.field_11048);
            if (newPortal.isEmpty()) {
               return false;
            } else {
               NetherPortal areaHelper = newPortal.get();
               areaHelper.createPortal(world);
               return true;
            }
         }
      }
   }
}
