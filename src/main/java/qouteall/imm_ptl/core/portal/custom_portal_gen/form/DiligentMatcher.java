package qouteall.imm_ptl.core.portal.custom_portal_gen.form;

import com.google.common.collect.UnmodifiableIterator;
import com.google.common.math.IntMath;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.Pair;
import net.minecraft.util.math.Direction.class_2351;
import net.minecraft.util.math.Direction.class_2352;
import org.apache.commons.lang3.Validate;
import qouteall.imm_ptl.core.portal.nether_portal.BlockPortalShape;
import qouteall.imm_ptl.core.portal.nether_portal.FastBlockPortalShape;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.my_util.AARotation;
import qouteall.q_misc_util.my_util.IntBox;
import qouteall.q_misc_util.my_util.IntMatrix3;

public class DiligentMatcher {
   public static List<DiligentMatcher.TransformedShape> getMatchableShapeVariants(BlockPortalShape original, int maxShapeLen) {
      List<DiligentMatcher.TransformedShape> result = new ArrayList<>();
      HashSet<BlockPortalShape> shapeSet = new HashSet<>();
      int divFactor = getShapeShrinkFactor(original);
      BlockPortalShape shrinked = shrinkShapeBy(original, divFactor);
      int shrinkedShapeLen = shrinked.getShapeInnerLength();
      int maxMultiplyFactor = (int)Math.floor((double)maxShapeLen / shrinkedShapeLen);
      UnmodifiableIterator var8 = AARotation.rotationsSortedByAngle.iterator();

      while (var8.hasNext()) {
         AARotation rotation = (AARotation)var8.next();
         IntMatrix3 rotationMatrix = rotation.matrix;
         BlockPortalShape rotatedShape = rotateShape(shrinked, rotationMatrix);
         BlockPortalShape newShape = regularizeShape(rotatedShape);
         boolean isNew = shapeSet.add(newShape);
         if (isNew) {
            result.add(new DiligentMatcher.TransformedShape(original, newShape, rotationMatrix, 1.0 / divFactor));

            for (int mul = 2; mul <= maxMultiplyFactor; mul++) {
               BlockPortalShape expanded = regularizeShape(upscaleShape(rotatedShape, mul));
               isNew = shapeSet.add(expanded);
               if (isNew) {
                  result.add(new DiligentMatcher.TransformedShape(original, expanded, rotationMatrix, (double)mul / divFactor));
               }
            }
         }
      }

      return result;
   }

   public static BlockPortalShape regularizeShape(BlockPortalShape rotatedShape) {
      return rotatedShape.getShapeWithMovedAnchor(BlockPos.ORIGIN);
   }

   public static BlockPortalShape rotateShape(BlockPortalShape shape, IntMatrix3 t) {
      Set<BlockPos> newArea = shape.area.stream().map(b -> t.transform(b)).collect(Collectors.toSet());
      class_2351 newAxis = t.transformDirection(Direction.from(shape.axis, class_2352.field_11056)).getAxis();
      return new BlockPortalShape(newArea, newAxis);
   }

   public static int getShapeShrinkFactor(BlockPortalShape shape) {
      HashSet<BlockPos> area = new HashSet<>(shape.area);
      ArrayList<IntBox> boxList = decomposeShape(shape, area);
      IntArrayList sideLenList = new IntArrayList();
      Pair<class_2351, class_2351> axs = Helper.getAnotherTwoAxis(shape.axis);

      for (IntBox box : boxList) {
         BlockPos boxSize = box.getSize();
         int a = Helper.getCoordinate(boxSize, (class_2351)axs.getLeft());
         int b = Helper.getCoordinate(boxSize, (class_2351)axs.getRight());
         sideLenList.add(a);
         sideLenList.add(b);
      }

      return (Integer)sideLenList.stream().reduce((ax, bx) -> IntMath.gcd(ax, bx)).get();
   }

   public static BlockPortalShape shrinkShapeBy(BlockPortalShape shape, int div) {
      Validate.isTrue(div != 0);
      BlockPortalShape regularized = regularizeShape(shape);
      if (div == 1) {
         return regularized;
      } else {
         Set<BlockPos> newArea = regularized.area
            .stream()
            .map(b -> new BlockPos(Math.floorDiv(b.getX(), div), Math.floorDiv(b.getY(), div), Math.floorDiv(b.getZ(), div)))
            .collect(Collectors.toSet());
         return new BlockPortalShape(newArea, regularized.axis);
      }
   }

   public static ArrayList<IntBox> decomposeShape(BlockPortalShape shape, HashSet<BlockPos> area) {
      ArrayList<IntBox> boxList = new ArrayList<>();

      while (!area.isEmpty()) {
         IntBox box = splitBoxFromArea(area, shape.axis);
         if (box == null) {
            break;
         }

         boxList.add(box);
      }

      return boxList;
   }

   public static BlockPortalShape upscaleShape(BlockPortalShape shape, int multiplyFactor) {
      Pair<class_2351, class_2351> axs = Helper.getAnotherTwoAxis(shape.axis);
      Vec3i v1 = Direction.from((class_2351)axs.getLeft(), class_2352.field_11056).getVector();
      Vec3i v2 = Direction.from((class_2351)axs.getRight(), class_2352.field_11056).getVector();
      return new BlockPortalShape(
         shape.area
            .stream()
            .flatMap(
               basePos -> IntStream.range(0, multiplyFactor)
                  .boxed()
                  .flatMap(
                     dx -> IntStream.range(0, multiplyFactor)
                        .mapToObj(dy -> Helper.scale(basePos, multiplyFactor).method_10081(Helper.scale(v1, dx).method_10081(Helper.scale(v2, dy))))
                  )
            )
            .collect(Collectors.toSet()),
         shape.axis
      );
   }

   private static IntBox splitBoxFromArea(Set<BlockPos> area, class_2351 axis) {
      Iterator<BlockPos> iterator = area.iterator();
      if (!iterator.hasNext()) {
         return null;
      } else {
         BlockPos firstElement = iterator.next();
         IntBox expanded = Helper.expandRectangle(firstElement, area::contains, axis);
         expanded.stream().forEach(b -> area.remove(b));
         return expanded;
      }
   }

   public static class TransformedShape {
      public final BlockPortalShape originalShape;
      public final BlockPortalShape transformedShape;
      public final IntMatrix3 rotation;
      public final double scale;
      public final FastBlockPortalShape fastOriginalShape;
      public final FastBlockPortalShape fastTransformedShape;

      public TransformedShape(BlockPortalShape originalShape, BlockPortalShape transformedShape, IntMatrix3 rotation, double scale) {
         this.originalShape = originalShape;
         this.transformedShape = transformedShape;
         this.rotation = rotation;
         this.scale = scale;
         this.fastOriginalShape = FastBlockPortalShape.fromBlockPortalShape(originalShape);
         this.fastTransformedShape = FastBlockPortalShape.fromBlockPortalShape(transformedShape);
      }
   }
}
