package qouteall.q_misc_util.my_util;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.function.Function;
import java.util.function.Supplier;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.Nullable;

public class QuadTree<T> {
   public static final int MAX_LEVEL = 8;
   public final IntArrayList children = new IntArrayList();
   public final ObjectArrayList<T> elements = new ObjectArrayList();
   public final Supplier<T> elementFactory;

   public QuadTree(Supplier<T> elementFactory) {
      this.elementFactory = elementFactory;
      this.allocateNode();
   }

   private int allocateNode() {
      Validate.isTrue(this.elements.size() * 4 == this.children.size());
      int index = this.elements.size();
      this.elements.add(this.elementFactory.get());
      this.children.add(-1);
      this.children.add(-1);
      this.children.add(-1);
      this.children.add(-1);
      return index;
   }

   public int acquireNodeForBoundingBox(double nodeRelativeMinX, double nodeRelativeMinY, double nodeRelativeMaxX, double nodeRelativeMaxY) {
      return this.acquireNodeForBoundingBoxInternal(0, 0, nodeRelativeMinX, nodeRelativeMinY, nodeRelativeMaxX, nodeRelativeMaxY);
   }

   public T acquireElementForBoundingBox(double nodeRelativeMinX, double nodeRelativeMinY, double nodeRelativeMaxX, double nodeRelativeMaxY) {
      int node = this.acquireNodeForBoundingBox(nodeRelativeMinX, nodeRelativeMinY, nodeRelativeMaxX, nodeRelativeMaxY);
      return (T)this.elements.get(node);
   }

   private int acquireNodeForBoundingBoxInternal(
      int level, int startingNode, double nodeRelativeMinX, double nodeRelativeMinY, double nodeRelativeMaxX, double nodeRelativeMaxY
   ) {
      if (level >= 8) {
         return startingNode;
      } else {
         boolean minXSig = nodeRelativeMinX >= 0.0;
         boolean minYSig = nodeRelativeMinY >= 0.0;
         boolean maxXSig = nodeRelativeMaxX >= 0.0;
         boolean maxYSig = nodeRelativeMaxY >= 0.0;
         if (minXSig == maxXSig && minYSig == maxYSig) {
            int childNodeIndex = startingNode * 4 + (minXSig ? 2 : 0) + (minYSig ? 1 : 0);
            int childNode = this.children.getInt(childNodeIndex);
            if (childNode == -1) {
               childNode = this.allocateNode();
               this.children.set(childNodeIndex, childNode);
            }

            double childNodeCenterX = minXSig ? 0.5 : -0.5;
            double childNodeCenterY = minYSig ? 0.5 : -0.5;
            return this.acquireNodeForBoundingBoxInternal(
               level + 1,
               childNode,
               (nodeRelativeMinX - childNodeCenterX) * 2.0,
               (nodeRelativeMinY - childNodeCenterY) * 2.0,
               (nodeRelativeMaxX - childNodeCenterX) * 2.0,
               (nodeRelativeMaxY - childNodeCenterY) * 2.0
            );
         } else {
            return startingNode;
         }
      }
   }

   @Nullable
   public <U> U traverse(double bbMinX, double bbMinY, double bbMaxX, double bbMaxY, Function<T, U> searchingFunc) {
      return this.traverseInternal(0, 0, bbMinX, bbMinY, bbMaxX, bbMaxY, searchingFunc);
   }

   @Nullable
   private <U> U traverseInternal(int level, int startingNode, double bbMinX, double bbMinY, double bbMaxX, double bbMaxY, Function<T, U> func) {
      U r1 = func.apply((T)this.elements.get(startingNode));
      if (r1 != null) {
         return r1;
      } else {
         int bxMin = bbMinX > 0.0 ? 1 : 0;
         int byMin = bbMinY > 0.0 ? 1 : 0;
         int bxMax = bbMaxX > 0.0 ? 1 : 0;
         int byMax = bbMaxY > 0.0 ? 1 : 0;

         for (int bx = bxMin; bx <= bxMax; bx++) {
            for (int by = byMin; by <= byMax; by++) {
               int childNodeIndex = startingNode * 4 + bx * 2 + by;
               int childNode = this.children.getInt(childNodeIndex);
               if (childNode != -1) {
                  double childNodeCenterX = bx == 1 ? 0.5 : -0.5;
                  double childNodeCenterY = by == 1 ? 0.5 : -0.5;
                  U r2 = this.traverseInternal(
                     level + 1,
                     childNode,
                     (bbMinX - childNodeCenterX) * 2.0,
                     (bbMinY - childNodeCenterY) * 2.0,
                     (bbMaxX - childNodeCenterX) * 2.0,
                     (bbMaxY - childNodeCenterY) * 2.0,
                     func
                  );
                  if (r2 != null) {
                     return r2;
                  }
               }
            }
         }

         return null;
      }
   }

   @Override
   public String toString() {
      StringBuilder sb = new StringBuilder();
      this.appendInfoToStringBuilder(0, 0, sb, "root");
      return sb.toString();
   }

   private void appendInfoToStringBuilder(int nodeIndex, int level, StringBuilder sb, String prefix) {
      sb.append(" ".repeat(level * 2));
      sb.append(prefix);
      sb.append(" ");
      sb.append(nodeIndex);
      sb.append(" ");
      sb.append(this.elements.get(nodeIndex));
      sb.append("\n");
      int childIndex = nodeIndex * 4;

      for (int i = 0; i < 4; i++) {
         int child = this.children.getInt(childIndex + i);
         if (child != -1) {
            String childPrefix = switch (i) {
               case 0 -> "--";
               case 1 -> "-+";
               case 2 -> "+-";
               case 3 -> "++";
               default -> throw new IllegalStateException("Unexpected value: " + i);
            };
            this.appendInfoToStringBuilder(child, level + 1, sb, childPrefix);
         }
      }
   }
}
