package qouteall.imm_ptl.core.portal.custom_portal_gen.form;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction.AxisDirection;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.api.PortalAPI;
import qouteall.imm_ptl.core.portal.custom_portal_gen.CustomPortalGeneration;
import qouteall.imm_ptl.core.portal.custom_portal_gen.SimpleBlockPredicate;
import qouteall.imm_ptl.core.portal.nether_portal.BlockPortalShape;
import qouteall.imm_ptl.core.portal.nether_portal.GeneralBreakablePortal;
import qouteall.imm_ptl.core.portal.nether_portal.NetherPortalGeneration;

public class OneWayForm extends PortalGenForm {
   public static final MapCodec<OneWayForm> CODEC = RecordCodecBuilder.mapCodec(
      instance -> instance.group(
            SimpleBlockPredicate.CODEC.fieldOf("frame_block").forGetter(o -> o.frameBlock),
            SimpleBlockPredicate.CODEC.fieldOf("area_block").forGetter(o -> o.areaBlock),
            Codec.BOOL.fieldOf("bi_faced").forGetter(o -> o.biFaced),
            Codec.BOOL.optionalFieldOf("breakable", true).forGetter(o -> o.breakable)
         )
         .apply(instance, instance.stable(OneWayForm::new))
   );
   public final SimpleBlockPredicate frameBlock;
   public final SimpleBlockPredicate areaBlock;
   public final boolean biFaced;
   public final boolean breakable;

   public OneWayForm(SimpleBlockPredicate frameBlock, SimpleBlockPredicate areaBlock, boolean biFaced, boolean breakable) {
      this.frameBlock = frameBlock;
      this.areaBlock = areaBlock;
      this.biFaced = biFaced;
      this.breakable = breakable;
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
      if (!NetherPortalGeneration.checkPortalGeneration(fromWorld, startingPos)) {
         return false;
      } else {
         BlockPortalShape fromShape = NetherPortalGeneration.findFrameShape(fromWorld, startingPos, this.areaBlock, this.frameBlock);
         if (fromShape == null) {
            return false;
         } else {
            for (BlockPos areaPos : fromShape.area) {
               fromWorld.setBlockState(areaPos, Blocks.AIR.getDefaultState());
            }

            if (this.breakable) {
               NetherPortalGeneration.fillInPlaceHolderBlocks(fromWorld, fromShape);
            }

            GeneralBreakablePortal portal = (GeneralBreakablePortal)GeneralBreakablePortal.ENTITY_TYPE.create(fromWorld, SpawnReason.TRIGGERED);
            Validate.notNull(portal);
            fromShape.initPortalPosAxisShape(portal, AxisDirection.POSITIVE);
            if (triggeringEntity == null) {
               portal.setDestination(portal.getOriginPos().add(0.0, 10.0, 0.0));
               portal.setDestinationDimension(fromWorld.getRegistryKey());
            } else {
               portal.setDestination(triggeringEntity.getCameraPosVec(1.0F));
               portal.setDestinationDimension(triggeringEntity.getEntityWorld().getRegistryKey());
            }

            portal.blockPortalShape = fromShape;
            portal.markOneWay();
            McHelper.spawnServerEntity(portal);
            GeneralBreakablePortal[] resultPortals = null;
            if (this.biFaced) {
               GeneralBreakablePortal flippedPortal = PortalAPI.createFlippedPortal(portal);
               flippedPortal.blockPortalShape = fromShape;
               flippedPortal.markOneWay();
               McHelper.spawnServerEntity(flippedPortal);
               resultPortals = new GeneralBreakablePortal[]{portal, flippedPortal};
            } else {
               resultPortals = new GeneralBreakablePortal[]{portal};
            }

            if (!this.breakable) {
               for (GeneralBreakablePortal p : resultPortals) {
                  p.unbreakable = true;
               }
            }

            cpg.onPortalsGenerated(resultPortals);
            return true;
         }
      }
   }
}
