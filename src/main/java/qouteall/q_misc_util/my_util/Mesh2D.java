package qouteall.q_misc_util.my_util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.ints.Int2ObjectFunction;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.function.Function;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtDouble;
import net.minecraft.nbt.NbtInt;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.Unit;
import net.minecraft.util.math.MathHelper;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.Nullable;
import qouteall.q_misc_util.Helper;

public class Mesh2D {
   public static final int gridCountForOneSide = 1073741824;
   public final Long2IntOpenHashMap gridToPointIndex = new Long2IntOpenHashMap();
   public final DoubleArrayList pointCoords = new DoubleArrayList();
   public final IntArrayList trianglePointIndexes = new IntArrayList();
   public final ObjectArrayList<IntArrayList> pointToTriangles = new ObjectArrayList();
   @Nullable
   public QuadTree<IntArrayList> triangleLookup;

   public static long encodeToGrid(double x, double y) {
      int gridX = (int)Math.round(x * 1.0737418E9F);
      int gridY = (int)Math.round(y * 1.0737418E9F);
      int gridXClamped = MathHelper.clamp(gridX, -1073741824, 1073741824);
      int gridYClamped = MathHelper.clamp(gridY, -1073741824, 1073741824);
      return (gridXClamped & 4294967295L) << 32 | gridYClamped & 4294967295L;
   }

   public Mesh2D() {
      this.gridToPointIndex.defaultReturnValue(-1);
   }

   public int addTriangle(double x1, double y1, double x2, double y2, double x3, double y3) {
      int pointIndex1 = this.indexPoint(x1, y1);
      int pointIndex2 = this.indexPoint(x2, y2);
      int pointIndex3 = this.indexPoint(x3, y3);
      return this.addTriangle(pointIndex1, pointIndex2, pointIndex3);
   }

   public int addTriangle(int pointIndex0, int pointIndex1, int pointIndex2) {
      if (pointIndex0 != pointIndex1 && pointIndex1 != pointIndex2 && pointIndex2 != pointIndex0) {
         double p0x = this.pointCoords.getDouble(pointIndex0 * 2);
         double p0y = this.pointCoords.getDouble(pointIndex0 * 2 + 1);
         double p1x = this.pointCoords.getDouble(pointIndex1 * 2);
         double p1y = this.pointCoords.getDouble(pointIndex1 * 2 + 1);
         double p2x = this.pointCoords.getDouble(pointIndex2 * 2);
         double p2y = this.pointCoords.getDouble(pointIndex2 * 2 + 1);
         double cross = Helper.crossProduct2D(p1x - p0x, p1y - p0y, p2x - p0x, p2y - p0y);
         if (Math.abs(cross) < 1.0E-9) {
            return -1;
         } else {
            int triangleIndex = this.getStoredTriangleNum();
            this.trianglePointIndexes.add(pointIndex0);
            if (cross > 0.0) {
               this.trianglePointIndexes.add(pointIndex1);
               this.trianglePointIndexes.add(pointIndex2);
            } else {
               this.trianglePointIndexes.add(pointIndex2);
               this.trianglePointIndexes.add(pointIndex1);
            }

            ((IntArrayList)this.pointToTriangles.get(pointIndex0)).add(triangleIndex);
            ((IntArrayList)this.pointToTriangles.get(pointIndex1)).add(triangleIndex);
            ((IntArrayList)this.pointToTriangles.get(pointIndex2)).add(triangleIndex);
            Validate.isTrue(this.isTriangleValid(triangleIndex));
            this.notifyTriangleAddedForQuadTree(triangleIndex);
            return triangleIndex;
         }
      } else {
         return -1;
      }
   }

   public int indexPoint(double x, double y) {
      Validate.isTrue(!Double.isNaN(x));
      Validate.isTrue(!Double.isNaN(y));
      x = MathHelper.clamp(x, -1.0, 1.0);
      y = MathHelper.clamp(y, -1.0, 1.0);
      long grid = encodeToGrid(x, y);
      int pointIndex = this.gridToPointIndex.getOrDefault(grid, -1);
      if (pointIndex == -1) {
         pointIndex = this.pointCoords.size() / 2;
         this.pointCoords.add(x);
         this.pointCoords.add(y);
         this.gridToPointIndex.put(grid, pointIndex);
         this.pointToTriangles.add(new IntArrayList());
      }

      return pointIndex;
   }

   public double getAngleFromOneCorner(int pointIndex, int triangleIndex) {
      int p1Index = this.trianglePointIndexes.getInt(triangleIndex * 3);
      int p2Index = this.trianglePointIndexes.getInt(triangleIndex * 3 + 1);
      int p3Index = this.trianglePointIndexes.getInt(triangleIndex * 3 + 2);
      int otherP1Index;
      int otherP2Index;
      if (pointIndex == p1Index) {
         otherP1Index = p2Index;
         otherP2Index = p3Index;
      } else if (pointIndex == p2Index) {
         otherP1Index = p1Index;
         otherP2Index = p3Index;
      } else {
         if (pointIndex != p3Index) {
            throw new IllegalArgumentException();
         }

         otherP1Index = p1Index;
         otherP2Index = p2Index;
      }

      double x1 = this.pointCoords.getDouble(pointIndex * 2);
      double y1 = this.pointCoords.getDouble(pointIndex * 2 + 1);
      double x2 = this.pointCoords.getDouble(otherP1Index * 2);
      double y2 = this.pointCoords.getDouble(otherP1Index * 2 + 1);
      double x3 = this.pointCoords.getDouble(otherP2Index * 2);
      double y3 = this.pointCoords.getDouble(otherP2Index * 2 + 1);
      double dx1 = x2 - x1;
      double dy1 = y2 - y1;
      double dx2 = x3 - x1;
      double dy2 = y3 - y1;
      return GeometryUtil.getAngle(dx1, dy1, dx2, dy2);
   }

   public double getExposingAngle(int pointIndex) {
      IntArrayList triangles = (IntArrayList)this.pointToTriangles.get(pointIndex);
      if (triangles.size() == 0) {
         return 0.0;
      } else {
         double sumAngle = 0.0;
         IntIterator iter = triangles.intIterator();

         while (iter.hasNext()) {
            int triangleIndex = iter.nextInt();
            sumAngle += this.getAngleFromOneCorner(pointIndex, triangleIndex);
         }

         return sumAngle;
      }
   }

   public boolean isPointInsideMesh(int pointIndex) {
      return Math.abs(this.getExposingAngle(pointIndex) - (Math.PI * 2)) < 1.0E-5;
   }

   public void removeTriangle(int triangleIndex) {
      this.notifyTriangleRemovedForQuadTree(triangleIndex);
      int p1Index = this.trianglePointIndexes.getInt(triangleIndex * 3);
      int p2Index = this.trianglePointIndexes.getInt(triangleIndex * 3 + 1);
      int p3Index = this.trianglePointIndexes.getInt(triangleIndex * 3 + 2);
      ((IntArrayList)this.pointToTriangles.get(p1Index)).rem(triangleIndex);
      ((IntArrayList)this.pointToTriangles.get(p2Index)).rem(triangleIndex);
      ((IntArrayList)this.pointToTriangles.get(p3Index)).rem(triangleIndex);
      this.trianglePointIndexes.set(triangleIndex * 3, -1);
      this.trianglePointIndexes.set(triangleIndex * 3 + 1, -1);
      this.trianglePointIndexes.set(triangleIndex * 3 + 2, -1);
   }

   public boolean isTriangleValid(int triangleIndex) {
      return triangleIndex == -1 ? false : this.trianglePointIndexes.getInt(triangleIndex * 3) != -1;
   }

   public boolean isPointUsed(int pointIndex) {
      Validate.isTrue(pointIndex != -1);
      return !((IntArrayList)this.pointToTriangles.get(pointIndex)).isEmpty();
   }

   public int getStoredPointNum() {
      return this.pointCoords.size() / 2;
   }

   public int getStoredTriangleNum() {
      return this.trianglePointIndexes.size() / 3;
   }

   public int getTrianglePointIndex(int triangleIndex, int indexInTriangle) {
      return this.trianglePointIndexes.getInt(triangleIndex * 3 + indexInTriangle);
   }

   public double getPointX(int pointIndex) {
      return this.pointCoords.getDouble(pointIndex * 2);
   }

   public double getPointY(int pointIndex) {
      return this.pointCoords.getDouble(pointIndex * 2 + 1);
   }

   public void updateTrianglePoint(int triangleIndex, int pointIndexInTriangle, int newPointIndex) {
      this.notifyTriangleRemovedForQuadTree(triangleIndex);
      int oldPointIndex = this.trianglePointIndexes.getInt(triangleIndex * 3 + pointIndexInTriangle);
      ((IntArrayList)this.pointToTriangles.get(oldPointIndex)).rem(triangleIndex);
      ((IntArrayList)this.pointToTriangles.get(newPointIndex)).add(triangleIndex);
      this.trianglePointIndexes.set(triangleIndex * 3 + pointIndexInTriangle, newPointIndex);
      this.turnTriangleToCounterClockwise(triangleIndex);
      this.notifyTriangleAddedForQuadTree(triangleIndex);
   }

   public boolean turnTriangleToCounterClockwise(int triangleIndex) {
      Validate.isTrue(this.isTriangleValid(triangleIndex));
      int p0Index = this.getTrianglePointIndex(triangleIndex, 0);
      int p1Index = this.getTrianglePointIndex(triangleIndex, 1);
      int p2Index = this.getTrianglePointIndex(triangleIndex, 2);
      double p0x = this.pointCoords.getDouble(p0Index * 2);
      double p0y = this.pointCoords.getDouble(p0Index * 2 + 1);
      double p1x = this.pointCoords.getDouble(p1Index * 2);
      double p1y = this.pointCoords.getDouble(p1Index * 2 + 1);
      double p2x = this.pointCoords.getDouble(p2Index * 2);
      double p2y = this.pointCoords.getDouble(p2Index * 2 + 1);
      double cross = Helper.crossProduct2D(p1x - p0x, p1y - p0y, p2x - p0x, p2y - p0y);
      if (cross < 0.0) {
         this.trianglePointIndexes.set(triangleIndex * 3 + 1, p2Index);
         this.trianglePointIndexes.set(triangleIndex * 3 + 2, p1Index);
         return true;
      } else {
         return false;
      }
   }

   public boolean canCollapseEdge(int destPointIndex, int movingPointIndex) {
      IntArrayList trianglesMoving = (IntArrayList)this.pointToTriangles.get(movingPointIndex);
      IntIterator iter = trianglesMoving.intIterator();

      while (iter.hasNext()) {
         int triangleIndex = iter.nextInt();
         int p0IndexInTriangle = this.trianglePointIndexes.getInt(triangleIndex * 3);
         int p1IndexInTriangle = this.trianglePointIndexes.getInt(triangleIndex * 3 + 1);
         int p2IndexInTriangle = this.trianglePointIndexes.getInt(triangleIndex * 3 + 2);
         if (p0IndexInTriangle == movingPointIndex) {
            p0IndexInTriangle = destPointIndex;
         }

         if (p1IndexInTriangle == movingPointIndex) {
            p1IndexInTriangle = destPointIndex;
         }

         if (p2IndexInTriangle == movingPointIndex) {
            p2IndexInTriangle = destPointIndex;
         }

         if (p0IndexInTriangle != p1IndexInTriangle && p0IndexInTriangle != p2IndexInTriangle && p1IndexInTriangle != p2IndexInTriangle) {
            double np0x = this.pointCoords.getDouble(p0IndexInTriangle * 2);
            double np0y = this.pointCoords.getDouble(p0IndexInTriangle * 2 + 1);
            double np1x = this.pointCoords.getDouble(p1IndexInTriangle * 2);
            double np1y = this.pointCoords.getDouble(p1IndexInTriangle * 2 + 1);
            double np2x = this.pointCoords.getDouble(p2IndexInTriangle * 2);
            double np2y = this.pointCoords.getDouble(p2IndexInTriangle * 2 + 1);
            double cross = Helper.crossProduct2D(np1x - np0x, np1y - np0y, np2x - np0x, np2y - np0y);
            if (cross < 0.0) {
               return false;
            }
         }
      }

      return true;
   }

   public void collapseEdge(int destPointIndex, int movingPointIndex) {
      IntArrayList relevantTriangles = new IntArrayList((IntList)this.pointToTriangles.get(movingPointIndex));
      IntIterator iter = relevantTriangles.intIterator();

      while (iter.hasNext()) {
         int triangleIndex = iter.nextInt();
         int p0IndexInTriangle = this.trianglePointIndexes.getInt(triangleIndex * 3);
         int p1IndexInTriangle = this.trianglePointIndexes.getInt(triangleIndex * 3 + 1);
         int p2IndexInTriangle = this.trianglePointIndexes.getInt(triangleIndex * 3 + 2);
         if (p0IndexInTriangle == movingPointIndex) {
            this.updateTrianglePoint(triangleIndex, 0, destPointIndex);
            p0IndexInTriangle = destPointIndex;
         }

         if (p1IndexInTriangle == movingPointIndex) {
            this.updateTrianglePoint(triangleIndex, 1, destPointIndex);
            p1IndexInTriangle = destPointIndex;
         }

         if (p2IndexInTriangle == movingPointIndex) {
            this.updateTrianglePoint(triangleIndex, 2, destPointIndex);
            p2IndexInTriangle = destPointIndex;
         }

         if (p0IndexInTriangle == p1IndexInTriangle || p0IndexInTriangle == p2IndexInTriangle || p1IndexInTriangle == p2IndexInTriangle) {
            this.removeTriangle(triangleIndex);
         }
      }
   }

   private int tryToCollapseInnerEdge(int startPointIndex) {
      for (int i = startPointIndex; i < this.pointCoords.size() / 2; i++) {
         if (this.isPointUsed(i) && this.isPointInsideMesh(i)) {
            IntArrayList triangles = (IntArrayList)this.pointToTriangles.get(i);
            IntIterator iter = triangles.intIterator();

            while (iter.hasNext()) {
               int triangleIndex = iter.nextInt();
               int p0Index = this.trianglePointIndexes.getInt(triangleIndex * 3);
               int p1Index = this.trianglePointIndexes.getInt(triangleIndex * 3 + 1);
               int p2Index = this.trianglePointIndexes.getInt(triangleIndex * 3 + 2);
               if (p1Index == i && this.canCollapseEdge(p0Index, p1Index)) {
                  this.collapseEdge(p0Index, p1Index);
                  return i;
               }

               if (p2Index == i && this.canCollapseEdge(p1Index, p2Index)) {
                  this.collapseEdge(p1Index, p2Index);
                  return i;
               }

               if (p0Index == i && this.canCollapseEdge(p2Index, p0Index)) {
                  this.collapseEdge(p2Index, p0Index);
                  return i;
               }
            }
         }
      }

      return -1;
   }

   private int tryToCollapseOuterEdgeAndTooShortEdges(int startPointIndex) {
      for (int i = 0; i < this.pointCoords.size() / 2; i++) {
         if (this.isPointUsed(i)) {
            double x = this.pointCoords.getDouble(i * 2);
            double y = this.pointCoords.getDouble(i * 2 + 1);
            IntOpenHashSet adjacentVertices = this.adjacentVerticesFrom(i);
            double exposingAngle = this.getExposingAngle(i);
            boolean isOnOuterEdgeStraightLine = Math.abs(exposingAngle - Math.PI) < 1.0E-4;
            IntIterator iter1 = adjacentVertices.intIterator();

            while (iter1.hasNext()) {
               int p1 = iter1.nextInt();
               IntIterator iter2 = adjacentVertices.intIterator();

               while (iter2.hasNext()) {
                  int p2 = iter2.nextInt();
                  if (p1 < p2) {
                     double p1x = this.pointCoords.getDouble(p1 * 2);
                     double p1y = this.pointCoords.getDouble(p1 * 2 + 1);
                     double p2x = this.pointCoords.getDouble(p2 * 2);
                     double p2y = this.pointCoords.getDouble(p2 * 2 + 1);
                     boolean collinear = GeometryUtil.isOppositeVec(p1x - x, p1y - y, p2x - x, p2y - y);
                     double lenSq = (p1x - p2x) * (p1x - p2x) + (p1y - p2y) * (p1y - p2y);
                     if ((isOnOuterEdgeStraightLine && collinear || lenSq < 1.0E-8) && this.canCollapseEdge(p1, i)) {
                        this.collapseEdge(p1, i);
                        return i;
                     }
                  }
               }
            }
         }
      }

      return -1;
   }

   public void simplify() {
      this.simplifySteps(this.getStoredTriangleNum());
   }

   public int simplifySteps(int countLimit) {
      this.fixTJunction();
      int count = 0;
      int innerEdgeCollapsePointIndex = 0;
      int outerEdgeCollapsePointIndex = 0;

      while (count < countLimit) {
         if (innerEdgeCollapsePointIndex != -1) {
            innerEdgeCollapsePointIndex = this.tryToCollapseInnerEdge(innerEdgeCollapsePointIndex);
            if (innerEdgeCollapsePointIndex != -1) {
               count++;
            }
         } else {
            if (outerEdgeCollapsePointIndex == -1) {
               break;
            }

            outerEdgeCollapsePointIndex = this.tryToCollapseOuterEdgeAndTooShortEdges(outerEdgeCollapsePointIndex);
            if (outerEdgeCollapsePointIndex != -1) {
               count++;
            }
         }
      }

      return count;
   }

   public static double select(int index, double n1, double n2, double n3) {
      return switch (index) {
         case 0 -> n1;
         case 1 -> n2;
         case 2 -> n3;
         default -> throw new IllegalArgumentException();
      };
   }

   public void subtractTriangleFromMesh(double tp0x, double tp0y, double tp1x, double tp1y, double tp2x, double tp2y) {
      this.enableTriangleLookup();
      double cuttingBbMinX = Math.min(Math.min(tp0x, tp1x), tp2x);
      double cuttingBbMinY = Math.min(Math.min(tp0y, tp1y), tp2y);
      double cuttingBbMaxX = Math.max(Math.max(tp0x, tp1x), tp2x);
      double cuttingBbMaxY = Math.max(Math.max(tp0y, tp1y), tp2y);
      IntArrayList relevantTriangles = new IntArrayList();
      this.traverseTrianglesByBB(cuttingBbMinX, cuttingBbMinY, cuttingBbMaxX, cuttingBbMaxY, ti -> {
         relevantTriangles.add(ti);
         return null;
      });
      relevantTriangles.intStream().forEach(ti -> this.subtractTriangleForOneTriangle(ti, tp0x, tp0y, tp1x, tp1y, tp2x, tp2y));
   }

   @Nullable
   private IntArrayList subtractTriangleForOneTriangle(int targetTriangleIndex, double tp0x, double tp0y, double tp1x, double tp1y, double tp2x, double tp2y) {
      if (!this.triangleIntersects(targetTriangleIndex, tp0x, tp0y, tp1x, tp1y, tp2x, tp2y)) {
         return null;
      } else {
         IntArrayList relevantTriangles = new IntArrayList();
         relevantTriangles.add(targetTriangleIndex);
         IntArrayList tempTriangles = new IntArrayList();
         GeometryUtil.Line2D line01 = GeometryUtil.Line2D.fromTwoPoints(tp0x, tp0y, tp1x, tp1y);
         GeometryUtil.Line2D line12 = GeometryUtil.Line2D.fromTwoPoints(tp1x, tp1y, tp2x, tp2y);
         GeometryUtil.Line2D line20 = GeometryUtil.Line2D.fromTwoPoints(tp2x, tp2y, tp0x, tp0y);
         GeometryUtil.Line2D[] lines = new GeometryUtil.Line2D[]{line01, line12, line20};

         for (GeometryUtil.Line2D line : lines) {
            IntIterator iter = relevantTriangles.intIterator();

            while (iter.hasNext()) {
               int triangleIndex = iter.nextInt();
               IntArrayList r = this.dissectTriangleByLine(triangleIndex, line);
               if (r == null) {
                  tempTriangles.add(triangleIndex);
               } else {
                  tempTriangles.addAll(r);
               }
            }

            relevantTriangles.clear();
            IntArrayList swappingTemp = relevantTriangles;
            relevantTriangles = tempTriangles;
            tempTriangles = swappingTemp;
         }

         relevantTriangles.removeIf(triangleIndex -> {
            Validate.isTrue(triangleIndex != -1);
            int p1Index = this.trianglePointIndexes.getInt(triangleIndex * 3);
            int p2Index = this.trianglePointIndexes.getInt(triangleIndex * 3 + 1);
            int p3Index = this.trianglePointIndexes.getInt(triangleIndex * 3 + 2);
            double p0x = this.pointCoords.getDouble(p1Index * 2);
            double p0y = this.pointCoords.getDouble(p1Index * 2 + 1);
            double p1x = this.pointCoords.getDouble(p2Index * 2);
            double p1y = this.pointCoords.getDouble(p2Index * 2 + 1);
            double p2x = this.pointCoords.getDouble(p3Index * 2);
            double p2y = this.pointCoords.getDouble(p3Index * 2 + 1);
            double centerX = (p0x + p1x + p2x) / 3.0;
            double centerY = (p0y + p1y + p2y) / 3.0;
            boolean shouldRemove = line01.testSideBool(centerX, centerY) && line12.testSideBool(centerX, centerY) && line20.testSideBool(centerX, centerY);
            if (shouldRemove) {
               this.removeTriangle(triangleIndex);
            }

            return shouldRemove;
         });
         return relevantTriangles;
      }
   }

   @Nullable
   public IntArrayList dissectTriangleByLine(int triangleIndex, GeometryUtil.Line2D line) {
      int p1Index = this.trianglePointIndexes.getInt(triangleIndex * 3);
      int p2Index = this.trianglePointIndexes.getInt(triangleIndex * 3 + 1);
      int p3Index = this.trianglePointIndexes.getInt(triangleIndex * 3 + 2);
      double p0x = this.pointCoords.getDouble(p1Index * 2);
      double p0y = this.pointCoords.getDouble(p1Index * 2 + 1);
      double p1x = this.pointCoords.getDouble(p2Index * 2);
      double p1y = this.pointCoords.getDouble(p2Index * 2 + 1);
      double p2x = this.pointCoords.getDouble(p3Index * 2);
      double p2y = this.pointCoords.getDouble(p3Index * 2 + 1);
      boolean side0 = line.testSideBool(p0x, p0y);
      boolean side1 = line.testSideBool(p1x, p1y);
      boolean side2 = line.testSideBool(p2x, p2y);
      if (side0 == side1 && side1 == side2) {
         return null;
      } else if (side0 == side1) {
         return this.dissectTriangleByEdgesFromVertex(triangleIndex, line, 2);
      } else if (side1 == side2) {
         return this.dissectTriangleByEdgesFromVertex(triangleIndex, line, 0);
      } else if (side2 == side0) {
         return this.dissectTriangleByEdgesFromVertex(triangleIndex, line, 1);
      } else {
         throw new IllegalStateException();
      }
   }

   @Nullable
   private IntArrayList dissectTriangleByEdgesFromVertex(int triangleIndex, GeometryUtil.Line2D line, int v0) {
      int v1 = (v0 + 1) % 3;
      int v2 = (v0 + 2) % 3;
      int p0Index = this.trianglePointIndexes.getInt(triangleIndex * 3 + v0);
      int p1Index = this.trianglePointIndexes.getInt(triangleIndex * 3 + v1);
      int p2Index = this.trianglePointIndexes.getInt(triangleIndex * 3 + v2);
      double p0x = this.pointCoords.getDouble(p0Index * 2);
      double p0y = this.pointCoords.getDouble(p0Index * 2 + 1);
      double p1x = this.pointCoords.getDouble(p1Index * 2);
      double p1y = this.pointCoords.getDouble(p1Index * 2 + 1);
      double p2x = this.pointCoords.getDouble(p2Index * 2);
      double p2y = this.pointCoords.getDouble(p2Index * 2 + 1);
      GeometryUtil.Line2D line01 = GeometryUtil.Line2D.fromTwoPoints(p0x, p0y, p1x, p1y);
      GeometryUtil.Line2D line02 = GeometryUtil.Line2D.fromTwoPoints(p0x, p0y, p2x, p2y);
      double t01 = line01.getIntersectionWithLine(line);
      double t02 = line02.getIntersectionWithLine(line);
      int intersectionPoint01 = -1;
      if (!Double.isNaN(t01) && t01 >= 0.0 && t01 <= 1.0) {
         double ix = line01.linePX() + t01 * line01.dirX();
         double iy = line01.linePY() + t01 * line01.dirY();
         intersectionPoint01 = this.indexPoint(ix, iy);
         if (intersectionPoint01 == p0Index || intersectionPoint01 == p1Index) {
            intersectionPoint01 = -1;
         }
      }

      int intersectionPoint02 = -1;
      if (!Double.isNaN(t02) && t02 >= 0.0 && t02 <= 1.0) {
         double ix = line02.linePX() + t02 * line02.dirX();
         double iy = line02.linePY() + t02 * line02.dirY();
         intersectionPoint02 = this.indexPoint(ix, iy);
         if (intersectionPoint02 == p0Index || intersectionPoint02 == p2Index) {
            intersectionPoint02 = -1;
         }
      }

      if (intersectionPoint01 == -1 && intersectionPoint02 == -1) {
         return null;
      } else if (intersectionPoint01 != -1 && intersectionPoint02 != -1) {
         this.removeTriangle(triangleIndex);
         int t1 = this.addTriangle(p0Index, intersectionPoint01, intersectionPoint02);
         int t2;
         int t3;
         if (this.getDistanceSq(p1Index, intersectionPoint02) < this.getDistanceSq(p2Index, intersectionPoint01)) {
            t2 = this.addTriangle(p1Index, p2Index, intersectionPoint02);
            t3 = this.addTriangle(p1Index, intersectionPoint02, intersectionPoint01);
         } else {
            t2 = this.addTriangle(p1Index, p2Index, intersectionPoint01);
            t3 = this.addTriangle(p2Index, intersectionPoint02, intersectionPoint01);
         }

         IntArrayList result = new IntArrayList();
         if (t1 != -1) {
            result.add(t1);
         }

         if (t2 != -1) {
            result.add(t2);
         }

         if (t3 != -1) {
            result.add(t3);
         }

         return result;
      } else if (intersectionPoint01 != -1) {
         this.removeTriangle(triangleIndex);
         int t1x = this.addTriangle(p1Index, p2Index, intersectionPoint01);
         int t2x = this.addTriangle(p2Index, p0Index, intersectionPoint01);
         IntArrayList resultx = new IntArrayList();
         if (t1x != -1) {
            resultx.add(t1x);
         }

         if (t2x != -1) {
            resultx.add(t2x);
         }

         return resultx;
      } else if (intersectionPoint02 != -1) {
         this.removeTriangle(triangleIndex);
         int t1xx = this.addTriangle(p0Index, p1Index, intersectionPoint02);
         int t2xx = this.addTriangle(p1Index, p2Index, intersectionPoint02);
         IntArrayList resultxx = new IntArrayList();
         if (t1xx != -1) {
            resultxx.add(t1xx);
         }

         if (t2xx != -1) {
            resultxx.add(t2xx);
         }

         return resultxx;
      } else {
         throw new IllegalStateException();
      }
   }

   public IntOpenHashSet adjacentVerticesFrom(int pointIndex) {
      IntOpenHashSet result = new IntOpenHashSet();
      IntArrayList triangleIds = (IntArrayList)this.pointToTriangles.get(pointIndex);
      IntIterator iter = triangleIds.intIterator();

      while (iter.hasNext()) {
         int ti = iter.nextInt();
         int p0Index = this.trianglePointIndexes.getInt(ti * 3);
         int p1Index = this.trianglePointIndexes.getInt(ti * 3 + 1);
         int p2Index = this.trianglePointIndexes.getInt(ti * 3 + 2);
         if (p0Index == pointIndex) {
            result.add(p1Index);
            result.add(p2Index);
         } else if (p1Index == pointIndex) {
            result.add(p0Index);
            result.add(p2Index);
         } else if (p2Index == pointIndex) {
            result.add(p0Index);
            result.add(p1Index);
         }
      }

      return result;
   }

   private double getDistance(int p1Index, int p2Index) {
      return Math.sqrt(this.getDistanceSq(p1Index, p2Index));
   }

   private double getDistanceSq(int p1Index, int p2Index) {
      double p1x = this.pointCoords.getDouble(p1Index * 2);
      double p1y = this.pointCoords.getDouble(p1Index * 2 + 1);
      double p2x = this.pointCoords.getDouble(p2Index * 2);
      double p2y = this.pointCoords.getDouble(p2Index * 2 + 1);
      return (p1x - p2x) * (p1x - p2x) + (p1y - p2y) * (p1y - p2y);
   }

   public void compactTriangleStorage() {
      int newSize = Helper.compactArrayStorage(this.getStoredTriangleNum(), i -> this.isTriangleValid(i), (valid, invalid) -> this.moveTriangle(valid, invalid));
      this.trianglePointIndexes.removeElements(newSize * 3, this.trianglePointIndexes.size());
   }

   public void compactPointStorage() {
      for (int i = 0; i < this.getStoredPointNum(); i++) {
         if (!this.isPointUsed(i)) {
            double x = this.pointCoords.getDouble(i * 2);
            double y = this.pointCoords.getDouble(i * 2 + 1);
            long encodedGridIndex = encodeToGrid(x, y);
            this.gridToPointIndex.remove(encodedGridIndex);
         }
      }

      int newSize = Helper.compactArrayStorage(this.getStoredPointNum(), ix -> this.isPointUsed(ix), (valid, invalid) -> this.movePoint(valid, invalid));
      this.pointCoords.removeElements(newSize * 2, this.pointCoords.size());
      this.pointToTriangles.removeElements(newSize, this.pointToTriangles.size());
      Validate.isTrue(this.pointToTriangles.size() * 2 == this.pointCoords.size());
   }

   public void compact() {
      this.compactTriangleStorage();
      this.compactPointStorage();
   }

   public void moveTriangle(int triangleIndex, int destTriangleIndex) {
      Validate.isTrue(!this.isTriangleValid(destTriangleIndex));
      this.notifyTriangleRemovedForQuadTree(triangleIndex);
      int p0Index = this.trianglePointIndexes.getInt(triangleIndex * 3);
      int p1Index = this.trianglePointIndexes.getInt(triangleIndex * 3 + 1);
      int p2Index = this.trianglePointIndexes.getInt(triangleIndex * 3 + 2);
      ((IntArrayList)this.pointToTriangles.get(p0Index)).rem(triangleIndex);
      ((IntArrayList)this.pointToTriangles.get(p1Index)).rem(triangleIndex);
      ((IntArrayList)this.pointToTriangles.get(p2Index)).rem(triangleIndex);
      ((IntArrayList)this.pointToTriangles.get(p0Index)).add(destTriangleIndex);
      ((IntArrayList)this.pointToTriangles.get(p1Index)).add(destTriangleIndex);
      ((IntArrayList)this.pointToTriangles.get(p2Index)).add(destTriangleIndex);
      this.trianglePointIndexes.set(destTriangleIndex * 3, p0Index);
      this.trianglePointIndexes.set(destTriangleIndex * 3 + 1, p1Index);
      this.trianglePointIndexes.set(destTriangleIndex * 3 + 2, p2Index);
      this.trianglePointIndexes.set(triangleIndex * 3, -1);
      this.trianglePointIndexes.set(triangleIndex * 3 + 1, -1);
      this.trianglePointIndexes.set(triangleIndex * 3 + 2, -1);
      this.notifyTriangleAddedForQuadTree(destTriangleIndex);
   }

   public void movePoint(int pointIndex, int destPointIndex) {
      Validate.isTrue(!this.isPointUsed(destPointIndex));
      Validate.isTrue(this.isPointUsed(pointIndex));
      double x = this.pointCoords.getDouble(pointIndex * 2);
      double y = this.pointCoords.getDouble(pointIndex * 2 + 1);
      long gridCoord = encodeToGrid(x, y);
      int removedPointIndex = this.gridToPointIndex.remove(gridCoord);
      Validate.isTrue(removedPointIndex == pointIndex, "%d %d %f %f %d", new Object[]{removedPointIndex, pointIndex, x, y, gridCoord});
      this.gridToPointIndex.put(gridCoord, destPointIndex);
      this.pointCoords.set(destPointIndex * 2, x);
      this.pointCoords.set(destPointIndex * 2 + 1, y);
      IntArrayList triangles = (IntArrayList)this.pointToTriangles.get(pointIndex);
      this.pointToTriangles.set(destPointIndex, triangles);
      this.pointToTriangles.set(pointIndex, null);
      IntIterator iter = triangles.intIterator();

      while (iter.hasNext()) {
         int triangleIndex = iter.nextInt();
         int p0IndexInTriangle = this.trianglePointIndexes.getInt(triangleIndex * 3);
         int p1IndexInTriangle = this.trianglePointIndexes.getInt(triangleIndex * 3 + 1);
         int p2IndexInTriangle = this.trianglePointIndexes.getInt(triangleIndex * 3 + 2);
         if (p0IndexInTriangle == pointIndex) {
            this.trianglePointIndexes.set(triangleIndex * 3, destPointIndex);
         }

         if (p1IndexInTriangle == pointIndex) {
            this.trianglePointIndexes.set(triangleIndex * 3 + 1, destPointIndex);
         }

         if (p2IndexInTriangle == pointIndex) {
            this.trianglePointIndexes.set(triangleIndex * 3 + 2, destPointIndex);
         }
      }
   }

   public void enableTriangleLookup() {
      if (this.triangleLookup == null) {
         this.triangleLookup = new QuadTree<>(() -> new IntArrayList(1));

         for (int ti = 0; ti < this.getStoredTriangleNum(); ti++) {
            if (this.isTriangleValid(ti)) {
               this.getTriangleIdListInTriangleLookup(ti).add(ti);
            }
         }
      }
   }

   private IntArrayList getTriangleIdListInTriangleLookup(int ti) {
      Validate.notNull(this.triangleLookup);
      int p0Index = this.trianglePointIndexes.getInt(ti * 3);
      int p1Index = this.trianglePointIndexes.getInt(ti * 3 + 1);
      int p2Index = this.trianglePointIndexes.getInt(ti * 3 + 2);
      double p0x = this.pointCoords.getDouble(p0Index * 2);
      double p0y = this.pointCoords.getDouble(p0Index * 2 + 1);
      double p1x = this.pointCoords.getDouble(p1Index * 2);
      double p1y = this.pointCoords.getDouble(p1Index * 2 + 1);
      double p2x = this.pointCoords.getDouble(p2Index * 2);
      double p2y = this.pointCoords.getDouble(p2Index * 2 + 1);
      double minX = Math.min(p0x, Math.min(p1x, p2x));
      double minY = Math.min(p0y, Math.min(p1y, p2y));
      double maxX = Math.max(p0x, Math.max(p1x, p2x));
      double maxY = Math.max(p0y, Math.max(p1y, p2y));
      return this.triangleLookup.acquireElementForBoundingBox(minX, minY, maxX, maxY);
   }

   @Nullable
   private <U> U traverseNearbyTriangles(int triangleIndex, Int2ObjectFunction<U> func) {
      Validate.notNull(this.triangleLookup);
      int p0Index = this.trianglePointIndexes.getInt(triangleIndex * 3);
      int p1Index = this.trianglePointIndexes.getInt(triangleIndex * 3 + 1);
      int p2Index = this.trianglePointIndexes.getInt(triangleIndex * 3 + 2);
      double p0x = this.pointCoords.getDouble(p0Index * 2);
      double p0y = this.pointCoords.getDouble(p0Index * 2 + 1);
      double p1x = this.pointCoords.getDouble(p1Index * 2);
      double p1y = this.pointCoords.getDouble(p1Index * 2 + 1);
      double p2x = this.pointCoords.getDouble(p2Index * 2);
      double p2y = this.pointCoords.getDouble(p2Index * 2 + 1);
      double minX = Math.min(p0x, Math.min(p1x, p2x));
      double minY = Math.min(p0y, Math.min(p1y, p2y));
      double maxX = Math.max(p0x, Math.max(p1x, p2x));
      double maxY = Math.max(p0y, Math.max(p1y, p2y));
      return this.traverseTrianglesByBB(minX, minY, maxX, maxY, ti -> ti != triangleIndex ? func.apply(ti) : null);
   }

   public boolean boundingBoxIntersects(int triangleIndex, double minX, double minY, double maxX, double maxY) {
      int p0Index = this.trianglePointIndexes.getInt(triangleIndex * 3);
      int p1Index = this.trianglePointIndexes.getInt(triangleIndex * 3 + 1);
      int p2Index = this.trianglePointIndexes.getInt(triangleIndex * 3 + 2);
      double p0x = this.pointCoords.getDouble(p0Index * 2);
      double p0y = this.pointCoords.getDouble(p0Index * 2 + 1);
      double p1x = this.pointCoords.getDouble(p1Index * 2);
      double p1y = this.pointCoords.getDouble(p1Index * 2 + 1);
      double p2x = this.pointCoords.getDouble(p2Index * 2);
      double p2y = this.pointCoords.getDouble(p2Index * 2 + 1);
      double triMinX = Math.min(p0x, Math.min(p1x, p2x));
      double triMinY = Math.min(p0y, Math.min(p1y, p2y));
      double triMaxX = Math.max(p0x, Math.max(p1x, p2x));
      double triMaxY = Math.max(p0y, Math.max(p1y, p2y));
      return Range.rangeIntersects(minX, maxX, triMinX, triMaxX) && Range.rangeIntersects(minY, maxY, triMinY, triMaxY);
   }

   @Nullable
   private <U> U traverseTrianglesByBB(double minX, double minY, double maxX, double maxY, Int2ObjectFunction<U> func) {
      Validate.notNull(this.triangleLookup);
      return this.triangleLookup.traverse(minX, minY, maxX, maxY, triangleIndexList -> {
         IntIterator iterator = triangleIndexList.intIterator();

         while (iterator.hasNext()) {
            int ti = iterator.nextInt();
            if (this.boundingBoxIntersects(ti, minX, minY, maxX, maxY)) {
               U result = (U)func.apply(ti);
               if (result != null) {
                  return result;
               }
            }
         }

         return null;
      });
   }

   public void notifyTriangleAddedForQuadTree(int triangleIndex) {
      if (this.triangleLookup != null) {
         this.getTriangleIdListInTriangleLookup(triangleIndex).add(triangleIndex);
      }
   }

   public void notifyTriangleRemovedForQuadTree(int triangleIndex) {
      if (this.triangleLookup != null) {
         boolean removed = this.getTriangleIdListInTriangleLookup(triangleIndex).rem(triangleIndex);
         Validate.isTrue(removed, "triangle %d not found in quad tree", triangleIndex);
      }
   }

   public void checkStorageIntegrity() {
      Validate.isTrue(this.pointCoords.size() % 2 == 0);
      Validate.isTrue(this.pointCoords.size() / 2 == this.pointToTriangles.size());
      Validate.isTrue(this.trianglePointIndexes.size() % 3 == 0);

      for (int triangleIndex = 0; triangleIndex < this.getStoredTriangleNum(); triangleIndex++) {
         if (this.isTriangleValid(triangleIndex)) {
            int p0Index = this.trianglePointIndexes.getInt(triangleIndex * 3);
            int p1Index = this.trianglePointIndexes.getInt(triangleIndex * 3 + 1);
            int p2Index = this.trianglePointIndexes.getInt(triangleIndex * 3 + 2);
            Validate.isTrue(((IntArrayList)this.pointToTriangles.get(p0Index)).contains(triangleIndex));
            Validate.isTrue(((IntArrayList)this.pointToTriangles.get(p1Index)).contains(triangleIndex));
            Validate.isTrue(((IntArrayList)this.pointToTriangles.get(p2Index)).contains(triangleIndex));
            if (this.triangleLookup != null) {
               Validate.isTrue(this.getTriangleIdListInTriangleLookup(triangleIndex).contains(triangleIndex));
            }

            double p0x = this.pointCoords.getDouble(p0Index * 2);
            double p0y = this.pointCoords.getDouble(p0Index * 2 + 1);
            double p1x = this.pointCoords.getDouble(p1Index * 2);
            double p1y = this.pointCoords.getDouble(p1Index * 2 + 1);
            double p2x = this.pointCoords.getDouble(p2Index * 2);
            double p2y = this.pointCoords.getDouble(p2Index * 2 + 1);
            double w = Helper.crossProduct2D(p1x - p0x, p1y - p0y, p2x - p0x, p2y - p0y);
            Validate.isTrue(w > 0.0, "%f", w);
         }
      }

      ObjectListIterator var19 = this.pointToTriangles.iterator();

      while (var19.hasNext()) {
         IntArrayList triangleIds = (IntArrayList)var19.next();
         triangleIds.intStream().forEach(triangleIndexx -> Validate.isTrue(this.isTriangleValid(triangleIndexx)));
      }

      if (this.triangleLookup != null) {
         this.triangleLookup.traverse(-1.0, -1.0, 1.0, 1.0, triangleList -> {
            triangleList.intStream().forEach(triangleIndexx -> Validate.isTrue(this.isTriangleValid(triangleIndexx)));
            return null;
         });
      }
   }

   public boolean triangleIntersects(int ti1, int ti2) {
      int t1p0Index = this.trianglePointIndexes.getInt(ti1 * 3);
      int t1p1Index = this.trianglePointIndexes.getInt(ti1 * 3 + 1);
      int t1p2Index = this.trianglePointIndexes.getInt(ti1 * 3 + 2);
      double t1p0x = this.pointCoords.getDouble(t1p0Index * 2);
      double t1p0y = this.pointCoords.getDouble(t1p0Index * 2 + 1);
      double t1p1x = this.pointCoords.getDouble(t1p1Index * 2);
      double t1p1y = this.pointCoords.getDouble(t1p1Index * 2 + 1);
      double t1p2x = this.pointCoords.getDouble(t1p2Index * 2);
      double t1p2y = this.pointCoords.getDouble(t1p2Index * 2 + 1);
      return this.triangleIntersects(ti2, t1p0x, t1p0y, t1p1x, t1p1y, t1p2x, t1p2y);
   }

   public boolean triangleIntersects(int ti, double t1p0x, double t1p0y, double t1p1x, double t1p1y, double t1p2x, double t1p2y) {
      int t2p0Index = this.trianglePointIndexes.getInt(ti * 3);
      int t2p1Index = this.trianglePointIndexes.getInt(ti * 3 + 1);
      int t2p2Index = this.trianglePointIndexes.getInt(ti * 3 + 2);
      double t2p0x = this.pointCoords.getDouble(t2p0Index * 2);
      double t2p0y = this.pointCoords.getDouble(t2p0Index * 2 + 1);
      double t2p1x = this.pointCoords.getDouble(t2p1Index * 2);
      double t2p1y = this.pointCoords.getDouble(t2p1Index * 2 + 1);
      double t2p2x = this.pointCoords.getDouble(t2p2Index * 2);
      double t2p2y = this.pointCoords.getDouble(t2p2Index * 2 + 1);
      return GeometryUtil.triangleIntersects(t1p0x, t1p0y, t1p1x, t1p1y, t1p2x, t1p2y, t2p0x, t2p0y, t2p1x, t2p1y, t2p2x, t2p2y);
   }

   public void fixIntersectedTriangle() {
      int maxOperationNum = this.getStoredTriangleNum();
      int operationNum = 0;

      for (int startTriangleIndex = 0; operationNum < maxOperationNum && startTriangleIndex < this.getStoredTriangleNum(); operationNum++) {
         startTriangleIndex = this.tryFixIntersectedTriangle(startTriangleIndex);
      }
   }

   private int tryFixIntersectedTriangle(int startTriangleIndex) {
      this.enableTriangleLookup();

      assert this.triangleLookup != null;

      int storedTriangleNum = this.getStoredTriangleNum();

      for (int ti = startTriangleIndex; ti < storedTriangleNum; ti++) {
         if (this.isTriangleValid(ti)) {
            int p0Index = this.trianglePointIndexes.getInt(ti * 3);
            int p1Index = this.trianglePointIndexes.getInt(ti * 3 + 1);
            int p2Index = this.trianglePointIndexes.getInt(ti * 3 + 2);
            double p0x = this.pointCoords.getDouble(p0Index * 2);
            double p0y = this.pointCoords.getDouble(p0Index * 2 + 1);
            double p1x = this.pointCoords.getDouble(p1Index * 2);
            double p1y = this.pointCoords.getDouble(p1Index * 2 + 1);
            double p2x = this.pointCoords.getDouble(p2Index * 2);
            double p2y = this.pointCoords.getDouble(p2Index * 2 + 1);
            int ti_ = ti;
            Unit unit = this.traverseNearbyTriangles(ti, anotherTi -> {
               if (ti_ < anotherTi && this.triangleIntersects(ti_, anotherTi)) {
                  this.subtractTriangleForOneTriangle(anotherTi, p0x, p0y, p1x, p1y, p2x, p2y);
                  return Unit.INSTANCE;
               } else {
                  return null;
               }
            });
            if (unit != null) {
               return ti;
            }
         }
      }

      return storedTriangleNum;
   }

   public int fixTJunction() {
      this.enableTriangleLookup();
      int operationCount = 0;
      int i = 0;

      while (i < this.getStoredPointNum()) {
         if (!this.isPointUsed(i)) {
            i++;
         } else {
            double x = this.pointCoords.getDouble(i * 2);
            double y = this.pointCoords.getDouble(i * 2 + 1);
            int i_ = i;

            while (true) {
               double tiny = 1.0E-5;
               Unit result = this.traverseTrianglesByBB(x - tiny, y - tiny, x + tiny, y + tiny, triangleIndex -> {
                  for (int v = 0; v < 3; v++) {
                     if (this.getTrianglePointIndex(triangleIndex, v) == i_) {
                        return null;
                     }
                  }

                  for (int edgeIndex = 0; edgeIndex < 3; edgeIndex++) {
                     int p0Index = this.getTrianglePointIndex(triangleIndex, edgeIndex);
                     int p1Index = this.getTrianglePointIndex(triangleIndex, (edgeIndex + 1) % 3);
                     int p2Index = this.getTrianglePointIndex(triangleIndex, (edgeIndex + 2) % 3);
                     if (this.isOnLineSegment(p0Index, i_, p1Index)) {
                        this.removeTriangle(triangleIndex);
                        this.addTriangle(p0Index, i_, p2Index);
                        this.addTriangle(i_, p1Index, p2Index);
                        return Unit.INSTANCE;
                     }
                  }

                  return null;
               });
               if (result == null) {
                  i++;
                  break;
               }

               operationCount++;
            }
         }
      }

      return operationCount;
   }

   public boolean isOnLineSegment(int p1Index, int p2Index, int p3Index) {
      double p1x = this.pointCoords.getDouble(p1Index * 2);
      double p1y = this.pointCoords.getDouble(p1Index * 2 + 1);
      double p2x = this.pointCoords.getDouble(p2Index * 2);
      double p2y = this.pointCoords.getDouble(p2Index * 2 + 1);
      double p3x = this.pointCoords.getDouble(p3Index * 2);
      double p3y = this.pointCoords.getDouble(p3Index * 2 + 1);
      return GeometryUtil.isOppositeVec(p1x - p2x, p1y - p2y, p3x - p2x, p3y - p2y);
   }

   @Override
   public String toString() {
      StringBuilder sb = new StringBuilder();

      for (int triangleIndex = 0; triangleIndex < this.getStoredTriangleNum(); triangleIndex++) {
         if (this.isTriangleValid(triangleIndex)) {
            int p0Index = this.trianglePointIndexes.getInt(triangleIndex * 3);
            int p1Index = this.trianglePointIndexes.getInt(triangleIndex * 3 + 1);
            int p2Index = this.trianglePointIndexes.getInt(triangleIndex * 3 + 2);
            double p0x = this.pointCoords.getDouble(p0Index * 2);
            double p0y = this.pointCoords.getDouble(p0Index * 2 + 1);
            double p1x = this.pointCoords.getDouble(p1Index * 2);
            double p1y = this.pointCoords.getDouble(p1Index * 2 + 1);
            double p2x = this.pointCoords.getDouble(p2Index * 2);
            double p2y = this.pointCoords.getDouble(p2Index * 2 + 1);
            sb.append(String.format("%d: (%.3f, %.3f) (%.3f, %.3f) (%.3f, %.3f)\n", triangleIndex, p0x, p0y, p1x, p1y, p2x, p2y));
         }
      }

      return sb.toString();
   }

   public JsonObject toJson() {
      JsonObject json = new JsonObject();
      JsonArray points = new JsonArray();
      JsonArray triangles = new JsonArray();

      for (int i = 0; i < this.getStoredPointNum(); i++) {
         if (!this.isPointUsed(i)) {
            points.add((JsonElement)null);
         } else {
            double x = this.pointCoords.getDouble(i * 2);
            double y = this.pointCoords.getDouble(i * 2 + 1);
            JsonArray point = new JsonArray();
            point.add(x);
            point.add(y);
            points.add(point);
         }
      }

      for (int ix = 0; ix < this.getStoredTriangleNum(); ix++) {
         if (this.isTriangleValid(ix)) {
            int p0Index = this.trianglePointIndexes.getInt(ix * 3);
            int p1Index = this.trianglePointIndexes.getInt(ix * 3 + 1);
            int p2Index = this.trianglePointIndexes.getInt(ix * 3 + 2);
            JsonArray triangle = new JsonArray();
            triangle.add(p0Index);
            triangle.add(p1Index);
            triangle.add(p2Index);
            triangles.add(triangle);
         }
      }

      json.add("points", points);
      json.add("triangles", triangles);
      return json;
   }

   public Vec2d getBarycenter() {
      double sumX = 0.0;
      double sumY = 0.0;
      double sumWeight = 0.0;

      for (int i = 0; i < this.getStoredTriangleNum(); i++) {
         if (this.isTriangleValid(i)) {
            int p0Index = this.trianglePointIndexes.getInt(i * 3);
            int p1Index = this.trianglePointIndexes.getInt(i * 3 + 1);
            int p2Index = this.trianglePointIndexes.getInt(i * 3 + 2);
            double p0x = this.pointCoords.getDouble(p0Index * 2);
            double p0y = this.pointCoords.getDouble(p0Index * 2 + 1);
            double p1x = this.pointCoords.getDouble(p1Index * 2);
            double p1y = this.pointCoords.getDouble(p1Index * 2 + 1);
            double p2x = this.pointCoords.getDouble(p2Index * 2);
            double p2y = this.pointCoords.getDouble(p2Index * 2 + 1);
            double weight = Helper.crossProduct2D(p1x - p0x, p1y - p0y, p2x - p0x, p2y - p0y);
            sumX += (p0x + p1x + p2x) * weight;
            sumY += (p0y + p1y + p2y) * weight;
            sumWeight += weight;
         }
      }

      return sumWeight == 0.0 ? new Vec2d(0.0, 0.0) : new Vec2d(sumX / sumWeight / 3.0, sumY / sumWeight / 3.0);
   }

   public Mesh2D.Rect getBoundingBox() {
      double minX = 0.0;
      double minY = 0.0;
      double maxX = 0.0;
      double maxY = 0.0;
      boolean firstPoint = true;

      for (int i = 0; i < this.getStoredPointNum(); i++) {
         if (this.isPointUsed(i)) {
            double x = this.pointCoords.getDouble(i * 2);
            double y = this.pointCoords.getDouble(i * 2 + 1);
            if (firstPoint) {
               minX = x;
               minY = y;
               maxX = x;
               maxY = y;
               firstPoint = false;
            } else {
               minX = Math.min(minX, x);
               minY = Math.min(minY, y);
               maxX = Math.max(maxX, x);
               maxY = Math.max(maxY, y);
            }
         }
      }

      return new Mesh2D.Rect(minX, minY, maxX, maxY);
   }

   public double getArea() {
      double sumAreaDoubled = 0.0;

      for (int i = 0; i < this.getStoredTriangleNum(); i++) {
         if (this.isTriangleValid(i)) {
            int p0Index = this.trianglePointIndexes.getInt(i * 3);
            int p1Index = this.trianglePointIndexes.getInt(i * 3 + 1);
            int p2Index = this.trianglePointIndexes.getInt(i * 3 + 2);
            double p0x = this.pointCoords.getDouble(p0Index * 2);
            double p0y = this.pointCoords.getDouble(p0Index * 2 + 1);
            double p1x = this.pointCoords.getDouble(p1Index * 2);
            double p1y = this.pointCoords.getDouble(p1Index * 2 + 1);
            double p2x = this.pointCoords.getDouble(p2Index * 2);
            double p2y = this.pointCoords.getDouble(p2Index * 2 + 1);
            double triangleAreaDoubled = Helper.crossProduct2D(p1x - p0x, p1y - p0y, p2x - p0x, p2y - p0y);
            sumAreaDoubled += triangleAreaDoubled;
         }
      }

      return sumAreaDoubled / 2.0;
   }

   public void subtractPolygon(ObjectArrayList<Vec2d> polygonVertexes) {
      for (int i = 1; i < polygonVertexes.size() - 1; i++) {
         int p0Index = 0;
         int p2Index = i + 1;
         this.subtractTriangleFromMesh(
            ((Vec2d)polygonVertexes.get(p0Index)).x(),
            ((Vec2d)polygonVertexes.get(p0Index)).y(),
            ((Vec2d)polygonVertexes.get(i)).x(),
            ((Vec2d)polygonVertexes.get(i)).y(),
            ((Vec2d)polygonVertexes.get(p2Index)).x(),
            ((Vec2d)polygonVertexes.get(p2Index)).y()
         );
      }
   }

   public void transformPoints(Function<Vec2d, Vec2d> transform) {
      this.gridToPointIndex.clear();

      for (int i = 0; i < this.getStoredPointNum(); i++) {
         double x = this.pointCoords.getDouble(i * 2);
         double y = this.pointCoords.getDouble(i * 2 + 1);
         Vec2d transformed = transform.apply(new Vec2d(x, y));
         double transformedX = transformed.x();
         double transformedY = transformed.y();
         this.pointCoords.set(i * 2, transformedX);
         this.pointCoords.set(i * 2 + 1, transformedY);
         long newGridIndex = encodeToGrid(transformedX, transformedY);
         this.gridToPointIndex.put(newGridIndex, i);
      }
   }

   public boolean boxIntersects(double minX, double minY, double maxX, double maxY) {
      this.enableTriangleLookup();

      assert this.triangleLookup != null;

      Unit result = this.triangleLookup.traverse(minX, minY, maxX, maxY, triangleList -> {
         for (int i = 0; i < triangleList.size(); i++) {
            int triangleId = triangleList.getInt(i);
            int p0Index = this.getTrianglePointIndex(triangleId, 0);
            int p1Index = this.getTrianglePointIndex(triangleId, 1);
            int p2Index = this.getTrianglePointIndex(triangleId, 2);
            double p0x = this.pointCoords.getDouble(p0Index * 2);
            double p0y = this.pointCoords.getDouble(p0Index * 2 + 1);
            double p1x = this.pointCoords.getDouble(p1Index * 2);
            double p1y = this.pointCoords.getDouble(p1Index * 2 + 1);
            double p2x = this.pointCoords.getDouble(p2Index * 2);
            double p2y = this.pointCoords.getDouble(p2Index * 2 + 1);
            if (GeometryUtil.triangleIntersectsWithAABB(p0x, p0y, p1x, p1y, p2x, p2y, minX, minY, maxX, maxY)) {
               return Unit.INSTANCE;
            }
         }

         return null;
      });
      return result != null;
   }

   public Mesh2D copy() {
      Mesh2D mesh = new Mesh2D();

      for (int ti = 0; ti < this.getStoredTriangleNum(); ti++) {
         if (this.isTriangleValid(ti)) {
            int p0Index = this.trianglePointIndexes.getInt(ti * 3);
            int p1Index = this.trianglePointIndexes.getInt(ti * 3 + 1);
            int p2Index = this.trianglePointIndexes.getInt(ti * 3 + 2);
            mesh.addTriangle(
               this.pointCoords.getDouble(p0Index * 2),
               this.pointCoords.getDouble(p0Index * 2 + 1),
               this.pointCoords.getDouble(p1Index * 2),
               this.pointCoords.getDouble(p1Index * 2 + 1),
               this.pointCoords.getDouble(p2Index * 2),
               this.pointCoords.getDouble(p2Index * 2 + 1)
            );
         }
      }

      return mesh;
   }

   public void addQuad(double x1, double y1, double x2, double y2) {
      this.addTriangle(x1, y1, x2, y1, x2, y2);
      this.addTriangle(x2, y2, x1, y2, x1, y1);
   }

   public static Mesh2D createNewFullQuadMesh() {
      Mesh2D mesh2D = new Mesh2D();
      mesh2D.addQuad(-1.0, -1.0, 1.0, 1.0);
      return mesh2D;
   }

   public NbtCompound toTag() {
      NbtList pointCoordsTag = new NbtList();
      NbtList trianglesTag = new NbtList();

      for (int i = 0; i < this.getStoredPointNum(); i++) {
         double x = this.pointCoords.getDouble(i * 2);
         double y = this.pointCoords.getDouble(i * 2 + 1);
         pointCoordsTag.add(NbtDouble.of(x));
         pointCoordsTag.add(NbtDouble.of(y));
      }

      for (int i = 0; i < this.getStoredTriangleNum(); i++) {
         if (this.isTriangleValid(i)) {
            int p0Index = this.trianglePointIndexes.getInt(i * 3);
            int p1Index = this.trianglePointIndexes.getInt(i * 3 + 1);
            int p2Index = this.trianglePointIndexes.getInt(i * 3 + 2);
            trianglesTag.add(NbtInt.of(p0Index));
            trianglesTag.add(NbtInt.of(p1Index));
            trianglesTag.add(NbtInt.of(p2Index));
         }
      }

      NbtCompound tag = new NbtCompound();
      tag.put("pointCoords", pointCoordsTag);
      tag.put("triangles", trianglesTag);
      return tag;
   }

   @Nullable
   public static Mesh2D fromTag(NbtCompound tag) {
      NbtList pointCoords = tag.getListOrEmpty("pointCoords");
      NbtList triangles = tag.getListOrEmpty("triangles");
      if (pointCoords.isEmpty() || pointCoords.size() % 2 != 0) {
         return null;
      } else if (!triangles.isEmpty() && triangles.size() % 3 == 0) {
         int pointNum = pointCoords.size() / 2;
         Mesh2D mesh = new Mesh2D();

         for (int i = 0; i < triangles.size() / 3; i++) {
            int p0Index = triangles.getInt(i * 3, 0);
            int p1Index = triangles.getInt(i * 3 + 1, 0);
            int p2Index = triangles.getInt(i * 3 + 2, 0);
            mesh.addTriangle(
               pointCoords.getDouble(p0Index * 2, 0.0),
               pointCoords.getDouble(p0Index * 2 + 1, 0.0),
               pointCoords.getDouble(p1Index * 2, 0.0),
               pointCoords.getDouble(p1Index * 2 + 1, 0.0),
               pointCoords.getDouble(p2Index * 2, 0.0),
               pointCoords.getDouble(p2Index * 2 + 1, 0.0)
            );
         }

         return mesh;
      } else {
         return null;
      }
   }

   public void debugVisualize() {
      JsonObject jsonObject = this.toJson();
      GsonBuilder gsonBuilder = new GsonBuilder();
      Gson gson = gsonBuilder.setPrettyPrinting().create();
      File file = new File("./misc/mesh_to_visualize.json");

      try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
         writer.append(gson.toJson(jsonObject));
      } catch (IOException var11) {
         throw new RuntimeException(var11);
      }

      try {
         Process var12 = Runtime.getRuntime().exec("python ./misc/visualize_mesh.py");
      } catch (IOException var9) {
         throw new RuntimeException(var9);
      }
   }

   public record Rect(double minX, double minY, double maxX, double maxY) {
   }
}
