package qouteall.q_misc_util;

import com.google.common.collect.Streams;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.serialization.MapCodec;
import it.unimi.dsi.fastutil.objects.ObjectList;
import java.lang.reflect.Method;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.world.World;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.math.Vec3d;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtDouble;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.nbt.NbtElement;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.math.Direction.class_2351;
import net.minecraft.util.math.Direction.class_2352;
import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.McHelper;
import qouteall.q_misc_util.my_util.DQuaternion;
import qouteall.q_misc_util.my_util.IntBox;
import qouteall.q_misc_util.my_util.LongBlockPos;
import qouteall.q_misc_util.my_util.RayTraceResult;
import qouteall.q_misc_util.platform.event.Event;
import qouteall.q_misc_util.platform.event.EventFactory;

public class Helper {
   public static final Logger LOGGER = LogManager.getLogger("iPortal");
   public static final Gson gson = new GsonBuilder().setPrettyPrinting().setLenient().create();

   public static double getCollidingT(Vec3d planeCenter, Vec3d planeNormal, Vec3d lineOrigin, Vec3d lineDirection) {
      return planeCenter.subtract(lineOrigin).dotProduct(planeNormal) / lineDirection.dotProduct(planeNormal);
   }

   public static double getCollidingT(
      double planeOriginX,
      double planeOriginY,
      double planeOriginZ,
      double planeNormalX,
      double planeNormalY,
      double planeNormalZ,
      double lineOriginX,
      double lineOriginY,
      double lineOriginZ,
      double lineDeltaX,
      double lineDeltaY,
      double lineDeltaZ
   ) {
      double denom = lineDeltaX * planeNormalX + lineDeltaY * planeNormalY + lineDeltaZ * planeNormalZ;
      return Math.abs(denom) < 1.0E-6
         ? Double.NaN
         : ((planeOriginX - lineOriginX) * planeNormalX + (planeOriginY - lineOriginY) * planeNormalY + (planeOriginZ - lineOriginZ) * planeNormalZ) / denom;
   }

   @Nullable
   public static RayTraceResult raytraceAABB(
      boolean boxFacingOutwards,
      double boxMinX,
      double boxMinY,
      double boxMinZ,
      double boxMaxX,
      double boxMaxY,
      double boxMaxZ,
      double lineOriginX,
      double lineOriginY,
      double lineOriginZ,
      double lineDeltaX,
      double lineDeltaY,
      double lineDeltaZ
   ) {
      boolean originInBox = lineOriginX > boxMinX
         && lineOriginX < boxMaxX
         && lineOriginY > boxMinY
         && lineOriginY < boxMaxY
         && lineOriginZ > boxMinZ
         && lineOriginZ < boxMaxZ;
      if (boxFacingOutwards && originInBox) {
         return null;
      } else {
         boolean testXPosi = lineDeltaX > 0.0 ^ boxFacingOutwards;
         boolean testYPosi = lineDeltaY > 0.0 ^ boxFacingOutwards;
         boolean testZPosi = lineDeltaZ > 0.0 ^ boxFacingOutwards;
         boolean normalXPosi = lineDeltaX <= 0.0;
         boolean normalYPosi = lineDeltaY <= 0.0;
         boolean normalZPosi = lineDeltaZ <= 0.0;
         double tX = getCollidingT(
            testXPosi ? boxMaxX : boxMinX,
            0.0,
            0.0,
            normalXPosi ? 1.0 : -1.0,
            0.0,
            0.0,
            lineOriginX,
            lineOriginY,
            lineOriginZ,
            lineDeltaX,
            lineDeltaY,
            lineDeltaZ
         );
         if (!Double.isNaN(tX) && tX >= 0.0 && tX <= 1.0) {
            double y = lineOriginY + tX * lineDeltaY;
            double z = lineOriginZ + tX * lineDeltaZ;
            if (y >= boxMinY && y <= boxMaxY && z >= boxMinZ && z <= boxMaxZ) {
               return new RayTraceResult(tX, new Vec3d(testXPosi ? boxMaxX : boxMinX, y, z), new Vec3d(testXPosi ? 1.0 : -1.0, 0.0, 0.0));
            }
         }

         double tY = getCollidingT(
            0.0,
            testYPosi ? boxMaxY : boxMinY,
            0.0,
            0.0,
            normalYPosi ? 1.0 : -1.0,
            0.0,
            lineOriginX,
            lineOriginY,
            lineOriginZ,
            lineDeltaX,
            lineDeltaY,
            lineDeltaZ
         );
         if (!Double.isNaN(tY) && tY >= 0.0 && tY <= 1.0) {
            double x = lineOriginX + tY * lineDeltaX;
            double z = lineOriginZ + tY * lineDeltaZ;
            if (x >= boxMinX && x <= boxMaxX && z >= boxMinZ && z <= boxMaxZ) {
               return new RayTraceResult(tY, new Vec3d(x, testYPosi ? boxMaxY : boxMinY, z), new Vec3d(0.0, testYPosi ? 1.0 : -1.0, 0.0));
            }
         }

         double tZ = getCollidingT(
            0.0,
            0.0,
            testZPosi ? boxMaxZ : boxMinZ,
            0.0,
            0.0,
            normalZPosi ? 1.0 : -1.0,
            lineOriginX,
            lineOriginY,
            lineOriginZ,
            lineDeltaX,
            lineDeltaY,
            lineDeltaZ
         );
         if (!Double.isNaN(tZ) && tZ >= 0.0 && tZ <= 1.0) {
            double x = lineOriginX + tZ * lineDeltaX;
            double y = lineOriginY + tZ * lineDeltaY;
            if (x >= boxMinX && x <= boxMaxX && y >= boxMinY && y <= boxMaxY) {
               return new RayTraceResult(tZ, new Vec3d(x, y, testZPosi ? boxMaxZ : boxMinZ), new Vec3d(0.0, 0.0, testZPosi ? 1.0 : -1.0));
            }
         }

         return null;
      }
   }

   public static boolean isInFrontOfPlane(Vec3d pos, Vec3d planePos, Vec3d planeNormal) {
      return pos.subtract(planePos).dotProduct(planeNormal) > 0.0;
   }

   public static Vec3d fallPointOntoPlane(Vec3d point, Vec3d planePos, Vec3d planeNormal) {
      double t = getCollidingT(planePos, planeNormal, point, planeNormal);
      return point.add(planeNormal.multiply(t));
   }

   public static Vec3i getUnitFromAxis(class_2351 axis) {
      return Direction.get(class_2352.field_11056, axis).getVector();
   }

   public static int getCoordinate(Vec3i v, class_2351 axis) {
      return axis.choose(v.getX(), v.getY(), v.getZ());
   }

   public static double getCoordinate(Vec3d v, class_2351 axis) {
      return axis.choose(v.x, v.y, v.z);
   }

   public static int getCoordinate(Vec3i v, Direction direction) {
      return getCoordinate(v, direction.getAxis()) * (direction.getDirection() == class_2352.field_11056 ? 1 : -1);
   }

   public static Vec3d putCoordinate(Vec3d v, class_2351 axis, double value) {
      return switch (axis) {
         case field_11048 -> new Vec3d(value, v.y, v.z);
         case field_11052 -> new Vec3d(v.x, value, v.z);
         default -> new Vec3d(v.x, v.y, value);
      };
   }

   public static BlockPos putCoordinate(Vec3i v, class_2351 axis, int value) {
      return switch (axis) {
         case field_11048 -> new BlockPos(value, v.getY(), v.getZ());
         case field_11052 -> new BlockPos(v.getX(), value, v.getZ());
         default -> new BlockPos(v.getX(), v.getY(), value);
      };
   }

   public static Vec3d putSignedCoordinate(Vec3d vec, Direction direction, double value) {
      return switch (direction) {
         case field_11033 -> new Vec3d(vec.x, -value, vec.z);
         case field_11036 -> new Vec3d(vec.x, value, vec.z);
         case field_11043 -> new Vec3d(vec.x, vec.y, -value);
         case field_11035 -> new Vec3d(vec.x, vec.y, value);
         case field_11039 -> new Vec3d(-value, vec.y, vec.z);
         case field_11034 -> new Vec3d(value, vec.y, vec.z);
         default -> throw new RuntimeException();
      };
   }

   public static double getSignedCoordinate(Vec3d vec, Direction direction) {
      return switch (direction) {
         case field_11033 -> -vec.y;
         case field_11036 -> vec.y;
         case field_11043 -> -vec.z;
         case field_11035 -> vec.z;
         case field_11039 -> -vec.x;
         case field_11034 -> vec.x;
         default -> throw new RuntimeException();
      };
   }

   public static double getDistanceSqrOnAxisPlane(Vec3d vec, class_2351 axis) {
      return switch (axis) {
         case field_11048 -> vec.y * vec.y + vec.z * vec.z;
         case field_11052 -> vec.x * vec.x + vec.z * vec.z;
         case field_11051 -> vec.x * vec.x + vec.y * vec.y;
         default -> throw new MatchException(null, null);
      };
   }

   public static double getBoxCoordinate(Box box, Direction direction) {
      switch (direction) {
         case field_11033:
            return box.minY;
         case field_11036:
            return box.maxY;
         case field_11043:
            return box.minZ;
         case field_11035:
            return box.maxZ;
         case field_11039:
            return box.minX;
         case field_11034:
            return box.maxX;
         default:
            throw new RuntimeException();
      }
   }

   public static Box replaceBoxCoordinate(Box box, Direction direction, double coordinate) {
      switch (direction) {
         case field_11033:
            return new Box(box.minX, coordinate, box.minZ, box.maxX, box.maxY, box.maxZ);
         case field_11036:
            return new Box(box.minX, box.minY, box.minZ, box.maxX, coordinate, box.maxZ);
         case field_11043:
            return new Box(box.minX, box.minY, coordinate, box.maxX, box.maxY, box.maxZ);
         case field_11035:
            return new Box(box.minX, box.minY, box.minZ, box.maxX, box.maxY, coordinate);
         case field_11039:
            return new Box(coordinate, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
         case field_11034:
            return new Box(box.minX, box.minY, box.minZ, coordinate, box.maxY, box.maxZ);
         default:
            throw new RuntimeException();
      }
   }

   public static <A, B> Pair<B, A> swaped(Pair<A, B> p) {
      return new Pair(p.getRight(), p.getLeft());
   }

   public static <T> T uniqueOfThree(T a, T b, T c) {
      if (a.equals(b)) {
         return c;
      } else if (b.equals(c)) {
         return a;
      } else {
         assert a.equals(c);

         return b;
      }
   }

   public static BlockPos max(BlockPos a, BlockPos b) {
      return new BlockPos(
         Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ())
      );
   }

   public static BlockPos min(BlockPos a, BlockPos b) {
      return new BlockPos(
         Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ())
      );
   }

   public static Pair<class_2351, class_2351> getAnotherTwoAxis(class_2351 axis) {
      switch (axis) {
         case field_11048:
            return new Pair(class_2351.field_11052, class_2351.field_11051);
         case field_11052:
            return new Pair(class_2351.field_11051, class_2351.field_11048);
         case field_11051:
            return new Pair(class_2351.field_11048, class_2351.field_11052);
         default:
            throw new IllegalArgumentException();
      }
   }

   public static BlockPos scale(Vec3i v, int m) {
      return new BlockPos(v.getX() * m, v.getY() * m, v.getZ() * m);
   }

   public static BlockPos divide(Vec3i v, int d) {
      return new BlockPos(v.getX() / d, v.getY() / d, v.getZ() / d);
   }

   public static BlockPos floorDiv(Vec3i v, int d) {
      return new BlockPos(Math.floorDiv(v.getX(), d), Math.floorDiv(v.getY(), d), Math.floorDiv(v.getZ(), d));
   }

   public static Direction[] getAnotherFourDirections(class_2351 axisOfNormal) {
      Pair<class_2351, class_2351> anotherTwoAxis = getAnotherTwoAxis(axisOfNormal);
      return new Direction[]{
         Direction.get(class_2352.field_11056, (class_2351)anotherTwoAxis.getLeft()),
         Direction.get(class_2352.field_11056, (class_2351)anotherTwoAxis.getRight()),
         Direction.get(class_2352.field_11060, (class_2351)anotherTwoAxis.getLeft()),
         Direction.get(class_2352.field_11060, (class_2351)anotherTwoAxis.getRight())
      };
   }

   public static Pair<Direction, Direction> getPerpendicularDirections(Direction facing) {
      Pair<class_2351, class_2351> axises = getAnotherTwoAxis(facing.getAxis());
      if (facing.getDirection() == class_2352.field_11060) {
         axises = new Pair((class_2351)axises.getRight(), (class_2351)axises.getLeft());
      }

      return new Pair(
         Direction.get(class_2352.field_11056, (class_2351)axises.getLeft()),
         Direction.get(class_2352.field_11056, (class_2351)axises.getRight())
      );
   }

   public static Vec3d getBoxSize(Box box) {
      return new Vec3d(box.getLengthX(), box.getLengthY(), box.getLengthZ());
   }

   public static Box getBoxSurfaceInversed(Box box, Direction direction) {
      double size = getCoordinate(getBoxSize(box), direction.getAxis());
      Vec3d shrinkVec = Vec3d.of(direction.getVector()).multiply(size);
      return box.shrink(shrinkVec.x, shrinkVec.y, shrinkVec.z);
   }

   public static Box getBoxSurface(Box box, Direction direction) {
      return getBoxSurfaceInversed(box, direction.getOpposite());
   }

   public static IntBox expandRectangle(BlockPos startingPos, Predicate<BlockPos> blockPosPredicate, class_2351 axis) {
      IntBox wallArea = new IntBox(startingPos, startingPos);

      for (Direction direction : getAnotherFourDirections(axis)) {
         wallArea = expandArea(wallArea, blockPosPredicate, direction);
      }

      return wallArea;
   }

   public static IntBox expandBoxArea(BlockPos startingPos, Predicate<BlockPos> blockPosPredicate) {
      IntBox box = new IntBox(startingPos, startingPos);

      for (Direction direction : Direction.values()) {
         box = expandArea(box, blockPosPredicate, direction);
      }

      return box;
   }

   public static int getChebyshevDistance(int x1, int z1, int x2, int z2) {
      return Math.max(Math.abs(x1 - x2), Math.abs(z1 - z2));
   }

   public static Box getBoxByBottomPosAndSize(Vec3d boxBottomCenter, Vec3d viewBoxSize) {
      return new Box(
         boxBottomCenter.subtract(viewBoxSize.x / 2.0, 0.0, viewBoxSize.z / 2.0),
         boxBottomCenter.add(viewBoxSize.x / 2.0, viewBoxSize.y, viewBoxSize.z / 2.0)
      );
   }

   public static Vec3d getBoxBottomCenter(Box box) {
      return new Vec3d((box.maxX + box.minX) / 2.0, box.minY, (box.maxZ + box.minZ) / 2.0);
   }

   public static double getDistanceToRectangle(double pointX, double pointY, double rectAX, double rectAY, double rectBX, double rectBY) {
      assert rectAX <= rectBX;

      assert rectAY <= rectBY;

      double wx1 = rectAX - pointX;
      double wx2 = rectBX - pointX;
      double dx = wx1 * wx2 < 0.0 ? 0.0 : Math.min(Math.abs(wx1), Math.abs(wx2));
      double wy1 = rectAY - pointY;
      double wy2 = rectBY - pointY;
      double dy = wy1 * wy2 < 0.0 ? 0.0 : Math.min(Math.abs(wy1), Math.abs(wy2));
      return Math.sqrt(dx * dx + dy * dy);
   }

   public static <T> void swapListElement(List<T> entries, int i1, int i2) {
      T temp = entries.get(i1);
      entries.set(i1, entries.get(i2));
      entries.set(i2, temp);
   }

   public static double crossProduct2D(double x1, double y1, double x2, double y2) {
      return x1 * y2 - x2 * y1;
   }

   public static RegistryKey<World> dimIdToKey(Identifier identifier) {
      return RegistryKey.of(RegistryKeys.WORLD, identifier);
   }

   public static RegistryKey<World> dimIdToKey(String str) {
      return dimIdToKey(McHelper.newResourceLocation(str));
   }

   public static void putWorldId(NbtCompound tag, String tagName, RegistryKey<World> dim) {
      tag.putString(tagName, dim.getValue().toString());
   }

   public static RegistryKey<World> getWorldId(NbtCompound tag, String tagName) {
      NbtElement term = tag.get(tagName);
      if (term instanceof NbtString) {
         String id = ((NbtString)term).comp_3831();
         return dimIdToKey(id);
      } else {
         LOGGER.error("Cannot read world id from {}. Fallback to overworld", tag);
         return World.OVERWORLD;
      }
   }

   @Nullable
   public static <T> T getLastSatisfying(Stream<T> stream, Predicate<T> predicate) {
      T last = null;
      Iterator<T> iterator = stream.iterator();

      while (iterator.hasNext()) {
         T obj = iterator.next();
         if (!predicate.test(obj)) {
            return last;
         }

         last = obj;
      }

      return last;
   }

   public static void doNotEatExceptionMessage(Runnable func) {
      try {
         func.run();
      } catch (Exception var2) {
         var2.printStackTrace();
         throw var2;
      }
   }

   public static <T> String myToString(Stream<T> stream) {
      StringBuilder stringBuilder = new StringBuilder();
      stream.forEach(obj -> {
         stringBuilder.append(obj.toString());
         stringBuilder.append('\n');
      });
      return stringBuilder.toString();
   }

   public static <A, B> Stream<Pair<A, B>> composeTwoStreamsWithEqualLength(Stream<A> a, Stream<B> b) {
      final Iterator<A> aIterator = a.iterator();
      final Iterator<B> bIterator = b.iterator();
      Iterator<Pair<A, B>> iterator = new Iterator<Pair<A, B>>() {
         @Override
         public boolean hasNext() {
            assert aIterator.hasNext() == bIterator.hasNext();

            return aIterator.hasNext();
         }

         public Pair<A, B> next() {
            return new Pair(aIterator.next(), bIterator.next());
         }
      };
      return Streams.stream(iterator);
   }

   @Deprecated
   public static void log(Object str) {
      LOGGER.info(str);
   }

   @Deprecated
   public static void err(Object str) {
      LOGGER.error(str);
   }

   public static Vec3d[] eightVerticesOf(Box box) {
      return new Vec3d[]{
         new Vec3d(box.minX, box.minY, box.minZ),
         new Vec3d(box.minX, box.minY, box.maxZ),
         new Vec3d(box.minX, box.maxY, box.minZ),
         new Vec3d(box.minX, box.maxY, box.maxZ),
         new Vec3d(box.maxX, box.minY, box.minZ),
         new Vec3d(box.maxX, box.minY, box.maxZ),
         new Vec3d(box.maxX, box.maxY, box.minZ),
         new Vec3d(box.maxX, box.maxY, box.maxZ)
      };
   }

   public static void putVec3d(NbtCompound compoundTag, String name, Vec3d vec3d) {
      compoundTag.putDouble(name + "X", vec3d.x);
      compoundTag.putDouble(name + "Y", vec3d.y);
      compoundTag.putDouble(name + "Z", vec3d.z);
   }

   public static Vec3d getVec3d(NbtCompound compoundTag, String name) {
      return new Vec3d(compoundTag.getDouble(name + "X", 0.0), compoundTag.getDouble(name + "Y", 0.0), compoundTag.getDouble(name + "Z", 0.0));
   }

   @Nullable
   public static Vec3d getVec3dOptional(NbtCompound compoundTag, String name) {
      return compoundTag.contains(name + "X") ? getVec3d(compoundTag, name) : null;
   }

   public static void putVec3i(NbtCompound compoundTag, String name, Vec3i vec3i) {
      compoundTag.putInt(name + "X", vec3i.getX());
      compoundTag.putInt(name + "Y", vec3i.getY());
      compoundTag.putInt(name + "Z", vec3i.getZ());
   }

   public static BlockPos getVec3i(NbtCompound compoundTag, String name) {
      return new BlockPos(compoundTag.getInt(name + "X", 0), compoundTag.getInt(name + "Y", 0), compoundTag.getInt(name + "Z", 0));
   }

   public static void putQuaternion(NbtCompound compoundTag, String name, @Nullable DQuaternion quaternion) {
      if (quaternion != null) {
         compoundTag.putDouble(name + "X", quaternion.getX());
         compoundTag.putDouble(name + "Y", quaternion.getY());
         compoundTag.putDouble(name + "Z", quaternion.getZ());
         compoundTag.putDouble(name + "W", quaternion.getW());
      }
   }

   @Nullable
   public static DQuaternion getQuaternion(NbtCompound compoundTag, String name) {
      return compoundTag.contains(name + "X")
         ? new DQuaternion(
            compoundTag.getDouble(name + "X", 0.0),
            compoundTag.getDouble(name + "Y", 0.0),
            compoundTag.getDouble(name + "Z", 0.0),
            compoundTag.getDouble(name + "W", 0.0)
         )
         : null;
   }

   public static NbtList getCompoundList(NbtCompound tag, String name) {
      return tag.getListOrEmpty(name);
   }

   public static <X> ArrayList<X> listTagToList(NbtList listTag, Function<NbtCompound, X> deserializer) {
      return listTagDeserialize(listTag, deserializer, NbtCompound.class);
   }

   public static <X> NbtList listToListTag(List<X> list, Function<X, NbtCompound> serializer) {
      return listTagSerialize(list, serializer);
   }

   public static <X, TT extends NbtElement> ArrayList<X> listTagDeserialize(NbtList listTag, Function<TT, X> deserializer, Class<TT> tagClass) {
      ArrayList<X> result = new ArrayList<>();
      listTag.forEach(tag -> {
         if (tag.getClass() == tagClass) {
            X obj = deserializer.apply((TT)tag);
            if (obj != null) {
               result.add(obj);
            }
         } else {
            LOGGER.error("Unexpected tag class: {}", tag.getClass(), new Throwable());
         }
      });
      return result;
   }

   public static <X, TT extends NbtElement> NbtList listTagSerialize(List<X> list, Function<X, TT> serializer) {
      NbtList listTag = new NbtList();

      for (X x : list) {
         listTag.add(serializer.apply(x));
      }

      return listTag;
   }

   public static <T> void compareOldAndNew(Set<T> oldSet, Set<T> newSet, Consumer<T> forRemoved, Consumer<T> forAdded) {
      oldSet.stream().filter(e -> !newSet.contains(e)).forEach(forRemoved);
      newSet.stream().filter(e -> !oldSet.contains(e)).forEach(forAdded);
   }

   public static long secondToNano(double second) {
      return (long)(second * 1.0E9);
   }

   public static double nanoToSecond(long nano) {
      return nano / 1.0E9;
   }

   public static IntBox expandArea(IntBox originalArea, Predicate<BlockPos> predicate, Direction direction) {
      IntBox currentBox = originalArea;

      for (int i = 1; i < 42; i++) {
         IntBox expanded = currentBox.getExpanded(direction, 1);
         if (!expanded.getSurfaceLayer(direction).stream().allMatch(predicate)) {
            return currentBox;
         }

         currentBox = expanded;
      }

      return currentBox;
   }

   public static <A, B> B reduce(B start, Stream<A> stream, BiFunction<B, A, B> func) {
      return stream.reduce(start, func, (a, b) -> {
         throw new IllegalStateException("combiner should only be used in parallel");
      });
   }

   public static <T> T noError(Callable<T> func) {
      try {
         return func.call();
      } catch (Exception var2) {
         throw new IllegalStateException(var2);
      }
   }

   public static <T> void removeIf(ObjectList<T> list, Predicate<T> predicate) {
      int placingIndex = 0;

      for (int i = 0; i < list.size(); i++) {
         T curr = (T)list.get(i);
         if (!predicate.test(curr)) {
            list.set(placingIndex, curr);
            placingIndex++;
         }
      }

      list.removeElements(placingIndex, list.size());
   }

   public static <T> void removeIfWithEarlyExit(ObjectList<T> list, BiPredicate<T, MutableBoolean> predicate) {
      MutableBoolean shouldStop = new MutableBoolean(false);
      int placingIndex = 0;

      for (int i = 0; i < list.size(); i++) {
         T curr = (T)list.get(i);
         if (shouldStop.booleanValue() || !predicate.test(curr, shouldStop)) {
            list.set(placingIndex, curr);
            placingIndex++;
         }
      }

      list.removeElements(placingIndex, list.size());
   }

   public static <T, S> Stream<S> wrapAdjacentAndMap(Stream<T> stream, BiFunction<T, T, S> function) {
      final Iterator<T> iterator = stream.iterator();
      return Streams.stream(new Iterator<S>() {
         private boolean isBuffered = false;
         private T buffer;

         private void fillBuffer() {
            if (!this.isBuffered) {
               assert iterator.hasNext();

               this.isBuffered = true;
               this.buffer = iterator.next();
            }
         }

         private T takeBuffer() {
            assert this.isBuffered;

            this.isBuffered = false;
            return this.buffer;
         }

         @Override
         public boolean hasNext() {
            if (!iterator.hasNext()) {
               return false;
            } else {
               this.fillBuffer();
               return iterator.hasNext();
            }
         }

         @Override
         public S next() {
            this.fillBuffer();
            T a = (T)this.takeBuffer();
            this.fillBuffer();
            return function.apply(a, this.buffer);
         }
      });
   }

   public static <A, B> Stream<B> mapReduce(Stream<A> stream, BiFunction<B, A, B> func, Helper.SimpleBox<B> startValue) {
      return stream.map(a -> {
         startValue.obj = func.apply(startValue.obj, (A)a);
         return startValue.obj;
      });
   }

   public static <T, S> Stream<S> wrapAdjacentAndMap1(Stream<T> stream, BiFunction<T, T, S> function) {
      Iterator<T> iterator = stream.iterator();
      if (!iterator.hasNext()) {
         return Stream.empty();
      } else {
         T firstValue = iterator.next();
         Stream<T> newStream = Streams.stream(iterator);
         return mapReduce(
               newStream,
               (lastPair, curr) -> new Pair(curr, function.apply((T)lastPair.getLeft(), curr)),
               new Helper.SimpleBox<>(new Pair(firstValue, null))
            )
            .map(pair -> (S)pair.getRight());
      }
   }

   public static <T> T makeIntoExpression(T t, Consumer<T> func) {
      func.accept(t);
      return t;
   }

   public static void putUuid(NbtCompound tag, String key, UUID uuid) {
      tag.putLong(key + "Most", uuid.getMostSignificantBits());
      tag.putLong(key + "Least", uuid.getLeastSignificantBits());
   }

   @Nullable
   public static UUID getUuid(NbtCompound tag, String key) {
      String key1 = key + "Most";
      return !tag.contains(key1) ? null : new UUID(tag.getLong(key1, 0L), tag.getLong(key + "Least", 0L));
   }

   public static Vec3d getFlippedVec(Vec3d vec, Vec3d flippingAxis) {
      Vec3d component = getProjection(vec, flippingAxis);
      return vec.subtract(component).multiply(-1.0).add(component);
   }

   public static Vec3d getProjection(Vec3d vec, Vec3d direction) {
      return direction.multiply(vec.dotProduct(direction));
   }

   public static Direction getFacingExcludingAxis(Vec3d vec, class_2351 axis) {
      return Arrays.stream(Direction.values())
         .filter(d -> d.getAxis() != axis)
         .max(
            Comparator.comparingDouble(dir -> vec.x * dir.getOffsetX() + vec.y * dir.getOffsetY() + vec.z * dir.getOffsetZ())
         )
         .orElse(null);
   }

   public static <T> Supplier<T> cached(Supplier<T> supplier) {
      return new Supplier<T>() {
         T cache = (T)null;

         @Override
         public T get() {
            if (this.cache == null) {
               this.cache = supplier.get();
            }

            Validate.notNull(this.cache);
            return this.cache;
         }
      };
   }

   public static <T> int indexOf(List<T> list, Predicate<T> predicate) {
      for (int i = 0; i < list.size(); i++) {
         T ele = list.get(i);
         if (predicate.test(ele)) {
            return i;
         }
      }

      return -1;
   }

   public static List<String> splitStringByLen(String str, int len) {
      List<String> result = new ArrayList<>();

      for (int i = 0; i * len < str.length(); i++) {
         result.add(str.substring(i * len, Math.min(str.length(), (i + 1) * len)));
      }

      return result;
   }

   public static Box transformBox(Box box, Function<Vec3d, Vec3d> function) {
      List<Vec3d> result = Arrays.stream(eightVerticesOf(box)).map(function).collect(Collectors.toList());
      return new Box(
         result.stream().mapToDouble(b -> b.x).min().getAsDouble(),
         result.stream().mapToDouble(b -> b.y).min().getAsDouble(),
         result.stream().mapToDouble(b -> b.z).min().getAsDouble(),
         result.stream().mapToDouble(b -> b.x).max().getAsDouble(),
         result.stream().mapToDouble(b -> b.y).max().getAsDouble(),
         result.stream().mapToDouble(b -> b.z).max().getAsDouble()
      );
   }

   public static double getDistanceToRange(double start, double end, double pos) {
      Validate.isTrue(end >= start);
      if (pos >= start) {
         return pos <= end ? 0.0 : pos - end;
      } else {
         return start - pos;
      }
   }

   public static double getSignedDistanceToRange(double start, double end, double pos) {
      Validate.isTrue(end >= start);
      if (pos >= start) {
         return pos <= end ? -Math.min(pos - start, end - pos) : pos - end;
      } else {
         return start - pos;
      }
   }

   public static double getDistanceToBox(Box box, Vec3d point) {
      double dx = getDistanceToRange(box.minX, box.maxX, point.x);
      double dy = getDistanceToRange(box.minY, box.maxY, point.y);
      double dz = getDistanceToRange(box.minZ, box.maxZ, point.z);
      return Math.sqrt(dx * dx + dy * dy + dz * dz);
   }

   public static double getSignedDistanceToBox(Box aabb, Vec3d point) {
      double dx = getSignedDistanceToRange(aabb.minX, aabb.maxX, point.x);
      double dy = getSignedDistanceToRange(aabb.minY, aabb.maxY, point.y);
      double dz = getSignedDistanceToRange(aabb.minZ, aabb.maxZ, point.z);
      double l = Math.sqrt(dx * dx + dy * dy + dz * dz);
      return dx < 0.0 && dy < 0.0 && dz < 0.0 ? -l : l;
   }

   public static <T> T firstOf(List<T> list) {
      Validate.isTrue(!list.isEmpty());
      return list.get(0);
   }

   public static <T> T lastOf(List<T> list) {
      Validate.isTrue(!list.isEmpty());
      return list.get(list.size() - 1);
   }

   @Nullable
   public static <T> T combineNullable(@Nullable T a, @Nullable T b, BiFunction<T, T, T> combiner) {
      if (a == null) {
         return b;
      } else {
         return b == null ? a : combiner.apply(a, b);
      }
   }

   public static <T> T arrayListComputeIfAbsent(List<T> arrayList, int index, Supplier<T> supplier) {
      if (arrayList.size() <= index) {
         while (arrayList.size() <= index) {
            arrayList.add(null);
         }
      }

      T value = arrayList.get(index);
      if (value == null) {
         value = supplier.get();
         arrayList.set(index, value);
      }

      return value;
   }

   public static <T> void arrayListKeyValueForeach(ArrayList<T> arrayList, Helper.IntObjectConsumer<T> func) {
      for (int i = 0; i < arrayList.size(); i++) {
         T value = arrayList.get(i);
         if (value != null) {
            func.consume(i, value);
         }
      }
   }

   public static Object reflectionInvoke(Object target, String methodName) {
      return noError(() -> {
         Method method = target.getClass().getDeclaredMethod(methodName);
         return method.invoke(target);
      });
   }

   public static Vec3d interpolatePos(Vec3d from, Vec3d to, double progress) {
      return from.lerp(to, progress);
   }

   public static <T> T getFirstNullable(List<T> list) {
      return list.isEmpty() ? null : list.get(0);
   }

   public static <T> T minBy(T a, T b, Comparator<T> comparator) {
      return comparator.compare(a, b) <= 0 ? a : b;
   }

   public static <T> T maxBy(T a, T b, Comparator<T> comparator) {
      return comparator.compare(a, b) >= 0 ? a : b;
   }

   public static boolean boxContains(Box outer, Box inner) {
      return outer.contains(inner.minX, inner.minY, inner.minZ) && outer.contains(inner.maxX, inner.maxY, inner.maxZ);
   }

   public static <A, B> List<B> mappedListView(List<A> originalList, Function<A, B> mapping) {
      return new AbstractList<B>() {
         @Override
         public B get(int index) {
            return mapping.apply(originalList.get(index));
         }

         @Override
         public int size() {
            return originalList.size();
         }
      };
   }

   public static OptionalDouble parseDouble(String str) {
      try {
         return OptionalDouble.of(Double.parseDouble(str));
      } catch (NumberFormatException var2) {
         return OptionalDouble.empty();
      }
   }

   public static OptionalInt parseInt(String str) {
      try {
         return OptionalInt.of(Integer.parseInt(str));
      } catch (NumberFormatException var2) {
         return OptionalInt.empty();
      }
   }

   public static List<Vec3d> deduplicateWithPrecision(Collection<Vec3d> points, int precision) {
      HashSet<LongBlockPos> set = new HashSet<>();

      for (Vec3d point : points) {
         set.add(new LongBlockPos(Math.round(point.x * precision), Math.round(point.y * precision), Math.round(point.z * precision)));
      }

      return set.stream()
         .map(blockPos -> new Vec3d((double)blockPos.x() / precision, (double)blockPos.y() / precision, (double)blockPos.z() / precision))
         .collect(Collectors.toList());
   }

   public static double getDistanceFromPointToLine(Vec3d point, Vec3d lineOrigin, Vec3d lineDirection) {
      Vec3d lineDirectionNormalized = lineDirection.normalize();
      Vec3d pointToLineOrigin = lineOrigin.subtract(point);
      Vec3d pointToLineOriginProjected = pointToLineOrigin.subtract(
         lineDirectionNormalized.multiply(pointToLineOrigin.dotProduct(lineDirectionNormalized))
      );
      return pointToLineOriginProjected.length();
   }

   public static void traverseBoxEdge(Helper.BoxEdgeConsumer consumer) {
      for (int dx = 0; dx <= 1; dx++) {
         for (int dy = 0; dy <= 1; dy++) {
            consumer.consume(dx, dy, 0, dx, dy, 1);
         }
      }

      for (int dx = 0; dx <= 1; dx++) {
         for (int dz = 0; dz <= 1; dz++) {
            consumer.consume(dx, 0, dz, dx, 1, dz);
         }
      }

      for (int dy = 0; dy <= 1; dy++) {
         for (int dz = 0; dz <= 1; dz++) {
            consumer.consume(0, dy, dz, 1, dy, dz);
         }
      }
   }

   public static List<Vec3d> verticesAndEdgeMidpoints(Box box) {
      List<Vec3d> result = new ArrayList<>();

      for (int xi = 0; xi <= 2; xi++) {
         for (int yi = 0; yi <= 2; yi++) {
            for (int zi = 0; zi <= 2; zi++) {
               if (xi != 1 || yi != 1 || zi != 1) {
                  double x = box.minX + xi / 2.0 * (box.maxX - box.minX);
                  double y = box.minY + yi / 2.0 * (box.maxY - box.minY);
                  double z = box.minZ + zi / 2.0 * (box.maxZ - box.minZ);
                  result.add(new Vec3d(x, y, z));
               }
            }
         }
      }

      return result;
   }

   public static Vec3d alignToBoxSurface(Box box, Vec3d pos, int gridCount) {
      double x = pos.x;
      double y = pos.y;
      double z = pos.z;
      if (x < box.minX) {
         x = box.minX;
      }

      if (y < box.minY) {
         y = box.minY;
      }

      if (z < box.minZ) {
         z = box.minZ;
      }

      if (x > box.maxX) {
         x = box.maxX;
      }

      if (y > box.maxY) {
         y = box.maxY;
      }

      if (z > box.maxZ) {
         z = box.maxZ;
      }

      double boxSizeX = box.getLengthX();
      double boxSizeY = box.getLengthY();
      double boxSizeZ = box.getLengthZ();
      double xOffset = x - box.minX;
      double yOffset = y - box.minY;
      double zOffset = z - box.minZ;
      xOffset = (double)Math.round(xOffset * gridCount) / gridCount;
      yOffset = (double)Math.round(yOffset * gridCount) / gridCount;
      zOffset = (double)Math.round(zOffset * gridCount) / gridCount;
      double distanceToXMax = boxSizeX - xOffset;
      double distanceToYMax = boxSizeY - yOffset;
      double distanceToZMax = boxSizeZ - zOffset;
      double minDistance = Math.min(Math.min(xOffset, distanceToXMax), Math.min(Math.min(yOffset, distanceToYMax), Math.min(zOffset, distanceToZMax)));
      if (minDistance == xOffset) {
         xOffset = 0.0;
      } else if (minDistance == distanceToXMax) {
         xOffset = boxSizeX;
      } else if (minDistance == yOffset) {
         yOffset = 0.0;
      } else if (minDistance == distanceToYMax) {
         yOffset = boxSizeY;
      } else if (minDistance == zOffset) {
         zOffset = 0.0;
      } else if (minDistance == distanceToZMax) {
         zOffset = boxSizeZ;
      }

      return new Vec3d(box.minX + xOffset, box.minY + yOffset, box.minZ + zOffset);
   }

   public static int compactArrayStorage(int arraySize, IntPredicate isElementValid, Helper.SwappingFunc swap) {
      int validElementCount = 0;
      int invalidElementCount = 0;

      while (validElementCount + invalidElementCount < arraySize) {
         if (isElementValid.test(validElementCount)) {
            validElementCount++;
         } else {
            invalidElementCount++;

            for (int invalidElementIndex = arraySize - invalidElementCount;
               invalidElementIndex > validElementCount;
               invalidElementIndex = arraySize - invalidElementCount
            ) {
               if (isElementValid.test(invalidElementIndex)) {
                  swap.swap(invalidElementIndex, validElementCount);
                  validElementCount++;
                  break;
               }

               invalidElementCount++;
            }
         }
      }

      return validElementCount;
   }

   public static <T> Stream<T> listReverseStream(List<T> list) {
      return Streams.stream(new Iterator<T>() {
         int index = list.size() - 1;

         @Override
         public boolean hasNext() {
            return this.index >= 0;
         }

         @Override
         public T next() {
            int i = this.index--;
            return list.get(i);
         }
      });
   }

   public static Event<Runnable> createRunnableEvent() {
      return EventFactory.createArrayBacked(Runnable.class, listeners -> () -> {
         for (Runnable listener : listeners) {
            listener.run();
         }
      });
   }

   public static <T> Event<Consumer<T>> createConsumerEvent() {
      return EventFactory.createArrayBacked(Consumer.class, listeners -> t -> {
         for (Consumer<T> listener : listeners) {
            listener.accept(t);
         }
      });
   }

   public static <A, B> Event<BiConsumer<A, B>> createBiConsumerEvent() {
      return EventFactory.createArrayBacked(BiConsumer.class, listeners -> (a, b) -> {
         for (BiConsumer<A, B> listener : listeners) {
            listener.accept(a, b);
         }
      });
   }

   public static NbtList vec3ToListTag(Vec3d vec) {
      NbtList listTag = new NbtList();
      listTag.add(NbtDouble.of(vec.x));
      listTag.add(NbtDouble.of(vec.y));
      listTag.add(NbtDouble.of(vec.z));
      return listTag;
   }

   @Nullable
   public static Vec3d vec3FromListTag(NbtElement tag) {
      return tag instanceof NbtList listTag
            && listTag.size() == 3
            && listTag.method_10534(0) instanceof NbtDouble
            && listTag.method_10534(1) instanceof NbtDouble
            && listTag.method_10534(2) instanceof NbtDouble
         ? new Vec3d(listTag.getDouble(0, 0.0), listTag.getDouble(1, 0.0), listTag.getDouble(2, 0.0))
         : null;
   }

   public static NbtCompound readWholeCompound(ReadView input) {
      return (NbtCompound)input.read(MapCodec.assumeMapUnsafe(NbtCompound.CODEC)).orElseGet(NbtCompound::new);
   }

   public static void writeWholeCompound(WriteView output, NbtCompound tag) {
      output.put(MapCodec.assumeMapUnsafe(NbtCompound.CODEC), tag);
   }

   @Nullable
   public static Direction directionFromDelta(int x, int y, int z) {
      for (Direction direction : Direction.values()) {
         Vec3i normal = direction.getVector();
         if (normal.getX() == x && normal.getY() == y && normal.getZ() == z) {
            return direction;
         }
      }

      return null;
   }

   public static Box boundingBoxOfPoints(Vec3d[] arr) {
      Validate.isTrue(arr.length != 0, "arr must not be empty", new Object[0]);
      double minX = arr[0].x;
      double minY = arr[0].y;
      double minZ = arr[0].z;
      double maxX = arr[0].x;
      double maxY = arr[0].y;
      double maxZ = arr[0].z;

      for (int i = 1; i < arr.length; i++) {
         Vec3d vec3 = arr[i];
         minX = Math.min(minX, vec3.x);
         minY = Math.min(minY, vec3.y);
         minZ = Math.min(minZ, vec3.z);
         maxX = Math.max(maxX, vec3.x);
         maxY = Math.max(maxY, vec3.y);
         maxZ = Math.max(maxZ, vec3.z);
      }

      return new Box(minX, minY, minZ, maxX, maxY, maxZ);
   }

   public interface BoxEdgeConsumer {
      void consume(int var1, int var2, int var3, int var4, int var5, int var6);
   }

   public interface IntObjectConsumer<T> {
      void consume(int var1, T var2);
   }

   public static class SimpleBox<T> {
      public T obj;

      public SimpleBox(T obj) {
         this.obj = obj;
      }
   }

   @FunctionalInterface
   public interface SwappingFunc {
      void swap(int var1, int var2);
   }
}
