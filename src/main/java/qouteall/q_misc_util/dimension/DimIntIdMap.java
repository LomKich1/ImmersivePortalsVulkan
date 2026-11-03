package qouteall.q_misc_util.dimension;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.Collections;
import java.util.Comparator;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtInt;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import qouteall.q_misc_util.Helper;

public class DimIntIdMap {
   public static final int MISSING_ID = Integer.MIN_VALUE;
   final Object2IntOpenHashMap<RegistryKey<World>> toIntegerId;
   final Int2ObjectOpenHashMap<RegistryKey<World>> fromIntegerId;
   private int maxId;

   public DimIntIdMap(Object2IntOpenHashMap<RegistryKey<World>> toIntegerId, Int2ObjectOpenHashMap<RegistryKey<World>> fromIntegerId) {
      this.toIntegerId = toIntegerId;
      this.fromIntegerId = fromIntegerId;
      toIntegerId.defaultReturnValue(Integer.MIN_VALUE);
      this.maxId = toIntegerId.values().intStream().max().orElse(0);
   }

   public DimIntIdMap() {
      this(new Object2IntOpenHashMap(), new Int2ObjectOpenHashMap());
   }

   public RegistryKey<World> fromIntegerId(int integerId) {
      RegistryKey<World> result = (RegistryKey<World>)this.fromIntegerId.get(integerId);
      if (result == null) {
         throw new RuntimeException("Missing Dimension " + integerId);
      } else {
         return result;
      }
   }

   @Nullable
   public RegistryKey<World> fromIntegerIdNullable(int integerId) {
      return (RegistryKey<World>)this.fromIntegerId.get(integerId);
   }

   public int toIntegerId(RegistryKey<World> dim) {
      int result = this.toIntegerId.getInt(dim);
      if (result == Integer.MIN_VALUE) {
         throw new RuntimeException("Missing Dimension " + dim.getValue());
      } else {
         return result;
      }
   }

   public void add(RegistryKey<World> dimId, int intId) {
      if (this.toIntegerId.containsKey(dimId)) {
         throw new RuntimeException("Dimension Id Record already contains " + dimId.getValue() + " " + this);
      } else if (this.fromIntegerId.containsKey(intId)) {
         throw new RuntimeException("Dimension Id Record already contains " + intId + " " + this);
      } else {
         this.toIntegerId.put(dimId, intId);
         this.fromIntegerId.put(intId, dimId);
         this.maxId = Math.max(this.maxId, intId);
      }
   }

   public boolean remove(RegistryKey<World> dimId) {
      int intId = this.toIntegerId.removeInt(dimId);
      if (intId == Integer.MIN_VALUE) {
         return false;
      } else {
         this.fromIntegerId.remove(intId);
         return true;
      }
   }

   public boolean removeUnused(Set<RegistryKey<World>> currentDimIds) {
      boolean changed = false;
      ObjectIterator var3 = this.toIntegerId.keySet().iterator();

      while (var3.hasNext()) {
         RegistryKey<World> dimId = (RegistryKey<World>)var3.next();
         if (!currentDimIds.contains(dimId)) {
            this.remove(dimId);
            changed = true;
         }
      }

      return changed;
   }

   public boolean containsDimId(RegistryKey<World> dimId) {
      return this.toIntegerId.containsKey(dimId);
   }

   public boolean containsIntId(int intId) {
      return this.fromIntegerId.containsKey(intId);
   }

   public static DimIntIdMap fromTag(NbtCompound tag) {
      NbtCompound intids = tag.getCompoundOrEmpty("intids");
      Object2IntOpenHashMap<RegistryKey<World>> toIntegerId = new Object2IntOpenHashMap();
      Int2ObjectOpenHashMap<RegistryKey<World>> fromIntegerId = new Int2ObjectOpenHashMap();
      intids.getKeys().forEach(dim -> {
         if (intids.contains(dim)) {
            int intid = intids.getInt(dim, 0);
            RegistryKey<World> dimId = Helper.dimIdToKey(dim);
            toIntegerId.put(dimId, intid);
            fromIntegerId.put(intid, dimId);
         }
      });
      return new DimIntIdMap(toIntegerId, fromIntegerId);
   }

   public NbtCompound toTag(Predicate<RegistryKey<World>> filter) {
      NbtCompound intids = new NbtCompound();
      this.toIntegerId.forEach((key, intid) -> {
         if (filter.test(key)) {
            intids.put(key.getValue().toString(), NbtInt.of(intid));
         }
      });
      NbtCompound result = new NbtCompound();
      result.put("intids", intids);
      return result;
   }

   public Set<RegistryKey<World>> getDimIdSet() {
      return Collections.unmodifiableSet(this.toIntegerId.keySet());
   }

   public int getNextIntegerId() {
      return this.maxId + 1;
   }

   @Override
   public String toString() {
      return this.toIntegerId
         .object2IntEntrySet()
         .stream()
         .sorted(Comparator.comparingInt(e -> e.getIntValue()))
         .map(e -> ((RegistryKey)e.getKey()).getValue().toString() + " -> " + e.getIntValue())
         .collect(Collectors.joining("\n"));
   }
}
