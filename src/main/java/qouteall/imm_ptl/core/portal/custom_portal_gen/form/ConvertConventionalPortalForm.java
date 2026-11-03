package qouteall.imm_ptl.core.portal.custom_portal_gen.form;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import net.minecraft.util.math.BlockPos;
import net.minecraft.text.Text;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.Direction.class_2351;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.portal.custom_portal_gen.CustomPortalGeneration;
import qouteall.imm_ptl.core.portal.custom_portal_gen.PortalGenInfo;
import qouteall.imm_ptl.core.portal.custom_portal_gen.SimpleBlockPredicate;
import qouteall.imm_ptl.core.portal.nether_portal.BlockPortalShape;
import qouteall.imm_ptl.core.portal.nether_portal.BlockTraverse;
import qouteall.imm_ptl.core.portal.nether_portal.BreakablePortalEntity;
import qouteall.imm_ptl.core.portal.nether_portal.GeneralBreakablePortal;
import qouteall.imm_ptl.core.portal.nether_portal.NetherPortalGeneration;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.my_util.IntBox;

public class ConvertConventionalPortalForm extends PortalGenForm {
   public static final MapCodec<ConvertConventionalPortalForm> CODEC = RecordCodecBuilder.mapCodec(
      instance -> instance.group(SimpleBlockPredicate.CODEC.fieldOf("portal_block").forGetter(o -> o.portalBlock))
         .apply(instance, instance.stable(ConvertConventionalPortalForm::new))
   );
   public final SimpleBlockPredicate portalBlock;

   public ConvertConventionalPortalForm(SimpleBlockPredicate portalBlock) {
      this.portalBlock = portalBlock;
   }

   @Override
   public MapCodec<? extends PortalGenForm> getCodec() {
      return CODEC;
   }

   @Override
   public PortalGenForm getReverse() {
      return this;
   }

   @Override
   public boolean perform(CustomPortalGeneration cpg, ServerWorld fromWorld, BlockPos startingPos, ServerWorld toWorld, @Nullable Entity triggeringEntity) {
      if (triggeringEntity == null) {
         Helper.err("Null triggering entity for portal conversion");
         return false;
      } else if (!(triggeringEntity instanceof ServerPlayerEntity player)) {
         Helper.err("Non player entity triggers portal conversion");
         return false;
      } else if (player.getServerWorld() != toWorld) {
         Helper.err("The player is not in the correct world " + player.getServerWorld().getRegistryKey().getValue());
         return false;
      } else {
         BlockPos playerCurrentPos = player.getBlockPos().toImmutable();
         BlockPos startFramePos = findBlockAround(fromWorld, startingPos, this.portalBlock);
         if (startFramePos == null) {
            return false;
         } else {
            BlockPos toFramePos = findBlockAround(toWorld, playerCurrentPos, this.portalBlock);
            if (toFramePos == null) {
               return false;
            } else {
               Helper.log(
                  String.format(
                     "Trying to convert conventional portal %s -> %s by %s (%d %d %d)",
                     fromWorld.getRegistryKey().getValue(),
                     toWorld.getRegistryKey().getValue(),
                     player.getName().getContent(),
                     (int)player.getX(),
                     (int)player.getY(),
                     (int)player.getZ()
                  )
               );
               BlockPortalShape fromShape = NetherPortalGeneration.findFrameShape(fromWorld, startFramePos, this.portalBlock, s -> !s.isAir());
               if (fromShape == null) {
                  Helper.err("Cannot find from side shape");
                  return false;
               } else {
                  BlockPortalShape toShape = NetherPortalGeneration.findFrameShape(toWorld, toFramePos, this.portalBlock, s -> !s.isAir());
                  if (toShape == null) {
                     Helper.err("Cannot fine to side shape");
                     return false;
                  } else {
                     Helper.log(fromShape.innerAreaBox + " " + toShape.innerAreaBox);
                     PortalGenInfo portalGenInfo = tryToMatch(fromWorld.getRegistryKey(), toWorld.getRegistryKey(), fromShape, toShape);
                     if (portalGenInfo == null) {
                        Helper.err("Shapes are incompatible");
                        player.sendMessage(Text.translatable("imm_ptl.incompatible_shape"), false);
                        return false;
                     } else {
                        portalGenInfo.generatePlaceholderBlocks();
                        if (fromShape.axis == class_2351.field_11052
                           && toShape.axis == class_2351.field_11052
                           && portalGenInfo.scale == 1.0
                           && portalGenInfo.rotation == null) {
                           GeneralBreakablePortal[] portals = FlippingFloorSquareForm.createPortals(
                              fromWorld, toWorld, portalGenInfo.fromShape, portalGenInfo.toShape
                           );
                           cpg.onPortalsGenerated(portals);
                           Helper.log("Created flipping floor portal");
                        } else {
                           BreakablePortalEntity[] portals = portalGenInfo.generateBiWayBiFacedPortal(GeneralBreakablePortal.ENTITY_TYPE);
                           cpg.onPortalsGenerated(portals);
                           Helper.log("Created normal bi-way bi-faced portal");
                        }

                        return true;
                     }
                  }
               }
            }
         }
      }
   }

   @Deprecated
   @Nullable
   public static IntBox findBlockBoxArea(World world, BlockPos pos, Predicate<BlockState> predicate) {
      BlockPos startingPos = findBlockAround(world, pos, predicate);
      if (startingPos == null) {
         return null;
      } else {
         IntBox result = Helper.expandBoxArea(startingPos, p -> predicate.test(world.getBlockState(p)));
         return result.getSize().equals(new BlockPos(1, 1, 1)) ? null : result;
      }
   }

   @Nullable
   public static BlockPos findBlockAround(World world, BlockPos pos, Predicate<BlockState> predicate) {
      BlockState blockState = world.getBlockState(pos);
      return predicate.test(blockState)
         ? pos
         : BlockTraverse.searchInBox(new IntBox(pos.method_10069(-2, -2, -2), pos.method_10069(2, 2, 2)), p -> predicate.test(world.getBlockState(p)) ? p : null);
   }

   @Deprecated
   @Nullable
   public static BlockPortalShape convertToPortalShape(IntBox box) {
      BlockPos size = box.getSize();
      class_2351 axis = null;
      if (size.getX() == 1) {
         axis = class_2351.field_11048;
      } else if (size.getY() == 1) {
         axis = class_2351.field_11052;
      } else {
         if (size.getZ() != 1) {
            Helper.err("The box is not flat " + box);
            return null;
         }

         axis = class_2351.field_11051;
      }

      return new BlockPortalShape(box.stream().collect(Collectors.toSet()), axis);
   }

   @Nullable
   public static PortalGenInfo tryToMatch(RegistryKey<World> fromDim, RegistryKey<World> toDim, BlockPortalShape a, BlockPortalShape b) {
      for (DiligentMatcher.TransformedShape variant : DiligentMatcher.getMatchableShapeVariants(a, BlockPortalShape.defaultLengthLimit)) {
         BlockPortalShape variantMoved = variant.transformedShape.getShapeWithMovedAnchor(b.anchor);
         if (variantMoved.equals(b)) {
            return new PortalGenInfo(fromDim, toDim, a, b, variant.rotation.toQuaternion(), variant.scale);
         }
      }

      return null;
   }
}
