package qouteall.imm_ptl.core.portal.custom_portal_gen.form;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import qouteall.imm_ptl.core.portal.custom_portal_gen.PortalGenInfo;
import qouteall.imm_ptl.core.portal.nether_portal.BlockPortalShape;
import qouteall.imm_ptl.core.portal.nether_portal.FastBlockPortalShape;
import qouteall.imm_ptl.core.portal.nether_portal.FrameSearching;

public abstract class AbstractDiligentForm extends NetherPortalLikeForm {
   public AbstractDiligentForm(boolean generateFrameIfNotFound) {
      super(generateFrameIfNotFound);
   }

   @Override
   public FrameSearching.FrameSearchingFunc<PortalGenInfo> getFrameMatchingFunc(ServerWorld fromWorld, ServerWorld toWorld, BlockPortalShape fromShape) {
      List<DiligentMatcher.TransformedShape> matchableShapeVariants = DiligentMatcher.getMatchableShapeVariants(fromShape, BlockPortalShape.defaultLengthLimit);
      Predicate<BlockState> areaPredicate = this.getAreaPredicate();
      Predicate<BlockState> otherSideFramePredicate = this.getOtherSideFramePredicate();
      return (blockAccess, x, y, z) -> {
         for (DiligentMatcher.TransformedShape matchableShapeVariant : matchableShapeVariants) {
            FastBlockPortalShape template = matchableShapeVariant.fastTransformedShape;
            boolean matches = template.matchShape(
               x,
               y,
               z,
               (px, py, pz) -> otherSideFramePredicate.test(blockAccess.getBlockState(px, py, pz)),
               (px, py, pz) -> areaPredicate.test(blockAccess.getBlockState(px, py, pz))
            );
            if (matches) {
               boolean matchToSelf = fromWorld == toWorld && x == template.basePosX() && y == template.basePosY() && z == template.basePosZ();
               if (!matchToSelf) {
                  FastBlockPortalShape moved = template.withNewBase(x, y, z);
                  return new PortalGenInfo(
                     fromWorld.getRegistryKey(),
                     toWorld.getRegistryKey(),
                     fromShape,
                     moved.toBlockPortalShape(),
                     matchableShapeVariant.rotation.toQuaternion(),
                     matchableShapeVariant.scale
                  );
               }
            }
         }

         return null;
      };
   }
}
