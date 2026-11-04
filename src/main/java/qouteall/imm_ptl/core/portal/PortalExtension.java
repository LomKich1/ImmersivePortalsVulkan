package qouteall.imm_ptl.core.portal;

import com.mojang.logging.LogUtils;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Uuids;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.ducks.IEWorld;
import qouteall.imm_ptl.core.teleportation.ServerTeleportationManager;

public class PortalExtension {
   private static final Logger LOGGER = LogUtils.getLogger();
   public double motionAffinity = 0.0;
   public boolean adjustPositionAfterTeleport = true;
   public boolean bindCluster = true;
   @Nullable
   public UUID reversePortalId;
   @Nullable
   public UUID flippedPortalId;
   @Nullable
   public UUID parallelPortalId;
   @Nullable
   public Portal reversePortal;
   @Nullable
   public Portal flippedPortal;
   @Nullable
   public Portal parallelPortal;

   public static PortalExtension get(Portal portal) {
      if (portal.extension == null) {
         portal.extension = new PortalExtension();
      }

      return portal.extension;
   }

   public static void init() {
      Portal.CLIENT_PORTAL_TICK_SIGNAL.register(portal -> get(portal).tick(portal));
      Portal.SERVER_PORTAL_TICK_SIGNAL.register(portal -> get(portal).tick(portal));
      Portal.READ_PORTAL_DATA_SIGNAL.register((portal, tag) -> get(portal).readFromNbt(tag));
      Portal.WRITE_PORTAL_DATA_SIGNAL.register((portal, tag) -> get(portal).writeToNbt(tag));
   }

   private void readFromNbt(NbtCompound compoundTag) {
      if (compoundTag.contains("motionAffinity")) {
         this.motionAffinity = compoundTag.getDouble("motionAffinity", 0.0);
      } else {
         this.motionAffinity = 0.0;
      }

      if (compoundTag.contains("adjustPositionAfterTeleport")) {
         this.adjustPositionAfterTeleport = compoundTag.getBoolean("adjustPositionAfterTeleport", false);
      } else {
         this.adjustPositionAfterTeleport = true;
      }

      if (compoundTag.contains("bindCluster")) {
         this.bindCluster = compoundTag.getBoolean("bindCluster", false);
      } else {
         this.bindCluster = true;
      }

      this.reversePortalId = (UUID)compoundTag.get("reversePortalId", Uuids.INT_STREAM_CODEC).orElse(null);
      this.flippedPortalId = (UUID)compoundTag.get("flippedPortalId", Uuids.INT_STREAM_CODEC).orElse(null);
      this.parallelPortalId = (UUID)compoundTag.get("parallelPortalId", Uuids.INT_STREAM_CODEC).orElse(null);
   }

   private void writeToNbt(NbtCompound compoundTag) {
      if (this.motionAffinity != 0.0) {
         compoundTag.putDouble("motionAffinity", this.motionAffinity);
      }

      compoundTag.putBoolean("adjustPositionAfterTeleport", this.adjustPositionAfterTeleport);
      compoundTag.putBoolean("bindCluster", this.bindCluster);
      if (this.reversePortalId != null) {
         compoundTag.put("reversePortalId", Uuids.INT_STREAM_CODEC, this.reversePortalId);
      }

      if (this.flippedPortalId != null) {
         compoundTag.put("flippedPortalId", Uuids.INT_STREAM_CODEC, this.flippedPortalId);
      }

      if (this.parallelPortalId != null) {
         compoundTag.put("parallelPortalId", Uuids.INT_STREAM_CODEC, this.parallelPortalId);
      }
   }

   private void tick(Portal portal) {
      if (portal.getEntityWorld().isClient()) {
         this.updateClusterStatusClient(portal);
      } else {
         this.updateClusterStatusServer(portal);
      }
   }

   private void updateClusterStatusServer(Portal portal) {
      if (!(portal instanceof Mirror)) {
         boolean needsUpdate = false;
         if (this.bindCluster) {
            if (this.flippedPortal != null && this.flippedPortal.isRemoved()) {
               this.flippedPortal = null;
            }

            if (this.reversePortal != null && this.reversePortal.isRemoved()) {
               this.reversePortal = null;
            }

            if (this.parallelPortal != null && this.parallelPortal.isRemoved()) {
               this.parallelPortal = null;
            }

            if (this.flippedPortalId != null && this.flippedPortal == null) {
               Entity e = (Entity)((IEWorld)(Object)portal.getEntityWorld()).portal_getEntityLookup().get(this.flippedPortalId);
               if (e instanceof Portal p) {
                  this.flippedPortal = p;
               } else {
                  this.flippedPortalId = null;
                  needsUpdate = true;
               }
            }

            if (this.flippedPortalId == null) {
               this.flippedPortal = PortalManipulation.findFlippedPortal(portal);
               if (this.flippedPortal != null) {
                  this.flippedPortalId = this.flippedPortal.getUuid();
                  needsUpdate = true;
               }
            }

            if (this.reversePortalId != null && this.reversePortal == null) {
               Entity e = (Entity)((IEWorld)(Object)portal.getDestWorld()).portal_getEntityLookup().get(this.reversePortalId);
               if (e instanceof Portal p) {
                  this.reversePortal = p;
               } else if (portal.isOtherSideChunkLoaded()) {
                  LOGGER.info("portal linking break {}", portal);
                  this.reversePortalId = null;
                  needsUpdate = true;
               }
            }

            if (this.reversePortalId == null) {
               this.reversePortal = PortalManipulation.findReversePortal(portal);
               if (this.reversePortal != null) {
                  this.reversePortalId = this.reversePortal.getUuid();
                  needsUpdate = true;
               }
            }

            if (this.parallelPortalId != null && this.parallelPortal == null) {
               Entity e = (Entity)((IEWorld)(Object)portal.getDestWorld()).portal_getEntityLookup().get(this.parallelPortalId);
               if (e instanceof Portal p) {
                  this.parallelPortal = p;
               } else if (portal.isOtherSideChunkLoaded()) {
                  LOGGER.info("portal linking break {}", portal);
                  this.parallelPortalId = null;
                  needsUpdate = true;
               }
            }

            if (this.parallelPortalId == null) {
               this.parallelPortal = PortalManipulation.findParallelPortal(portal);
               if (this.parallelPortal != null) {
                  this.parallelPortalId = this.parallelPortal.getUuid();
                  needsUpdate = true;
               }
            }
         } else {
            this.flippedPortal = null;
            this.reversePortal = null;
            this.parallelPortal = null;
            this.flippedPortalId = null;
            this.reversePortalId = null;
            this.parallelPortalId = null;
         }

         if (this.flippedPortal != null) {
            get(this.flippedPortal).bindCluster = true;
         }

         if (this.reversePortal != null) {
            get(this.reversePortal).bindCluster = true;
         }

         if (this.parallelPortal != null) {
            get(this.parallelPortal).bindCluster = true;
         }

         if (this.flippedPortal == portal) {
            this.flippedPortal = null;
            this.flippedPortalId = null;
            needsUpdate = true;
         }

         if (this.reversePortal == portal) {
            this.reversePortal = null;
            this.reversePortalId = null;
            needsUpdate = true;
         }

         if (this.parallelPortal == portal) {
            this.parallelPortal = null;
            this.parallelPortalId = null;
            needsUpdate = true;
         }

         if (needsUpdate) {
            portal.reloadAndSyncToClient();
         }
      }
   }

   private void updateClusterStatusClient(Portal portal) {
      if (this.bindCluster) {
         if (this.flippedPortalId != null) {
            Entity e = (Entity)((IEWorld)(Object)portal.getEntityWorld()).portal_getEntityLookup().get(this.flippedPortalId);
            if (e instanceof Portal p) {
               this.flippedPortal = p;
            }
         } else {
            this.flippedPortal = null;
         }

         if (this.reversePortalId != null) {
            Entity e = (Entity)((IEWorld)(Object)portal.getDestWorld()).portal_getEntityLookup().get(this.reversePortalId);
            if (e instanceof Portal p) {
               this.reversePortal = p;
            }
         } else {
            this.reversePortal = null;
         }

         if (this.parallelPortalId != null) {
            Entity e = (Entity)((IEWorld)(Object)portal.getDestWorld()).portal_getEntityLookup().get(this.parallelPortalId);
            if (e instanceof Portal p) {
               this.parallelPortal = p;
            }
         } else {
            this.parallelPortal = null;
         }
      } else {
         this.flippedPortal = null;
         this.reversePortal = null;
         this.parallelPortal = null;
      }
   }

   public void rectifyClusterPortals(Portal portal, boolean sync) {
      portal.animation.defaultAnimation.inverseScale = false;
      if (this.flippedPortal != null) {
         this.flippedPortal = ServerTeleportationManager.teleportRegularEntityTo(
            this.flippedPortal, portal.getEntityWorld().getRegistryKey(), portal.getOriginPos()
         );
         this.flippedPortal.setDestDim(portal.getDestDim());
         this.flippedPortal.setOriginPos(portal.getOriginPos());
         this.flippedPortal.setDestination(portal.getDestPos());
         this.flippedPortal.setAxisW(portal.getAxisW().multiply(-1.0));
         this.flippedPortal.setAxisH(portal.getAxisH());
         this.flippedPortal.setScaling(portal.getScaling());
         this.flippedPortal.setRotation(portal.getRotation());
         this.flippedPortal.setWidth(portal.getPortalWidth());
         this.flippedPortal.setHeight(portal.getPortalHeight());
         this.flippedPortal.setThickness(portal.getThickness());
         PortalManipulation.copyAdditionalProperties(this.flippedPortal, portal, false);
         this.flippedPortal.animation.defaultAnimation.inverseScale = false;
         this.flippedPortal.setPortalShape(portal.getPortalShape().getFlipped());
         if (sync) {
            this.flippedPortal.reloadAndSyncToClientNextTick();
         }
      }

      if (this.reversePortal != null) {
         if (portal.getDestDim() != this.reversePortal.getEntityWorld().getRegistryKey()) {
            LOGGER.info("Moving reverse portal across dimension {} {}", portal, this.reversePortal);
         }

         this.reversePortal = ServerTeleportationManager.teleportRegularEntityTo(this.reversePortal, portal.getDestDim(), portal.getDestPos());
         this.reversePortalId = this.reversePortal.getUuid();
         this.reversePortal.setDestDim(portal.getOriginDim());
         this.reversePortal.setOriginPos(portal.getDestPos());
         this.reversePortal.setDestination(portal.getOriginPos());
         this.reversePortal.setAxisW(portal.transformLocalVecNonScale(portal.getAxisW().multiply(-1.0)));
         this.reversePortal.setAxisH(portal.transformLocalVecNonScale(portal.getAxisH()));
         this.reversePortal.setScaling(1.0 / portal.getScaling());
         if (portal.getRotation() != null) {
            this.reversePortal.setRotation(portal.getRotation().getConjugated());
         } else {
            this.reversePortal.setRotation(null);
         }

         this.reversePortal.setWidth(portal.getPortalWidth() * portal.getScale());
         this.reversePortal.setHeight(portal.getPortalHeight() * portal.getScale());
         this.reversePortal.setThickness(portal.getThickness() * portal.getScale());
         PortalManipulation.copyAdditionalProperties(this.reversePortal, portal, false);
         this.reversePortal.animation.defaultAnimation.inverseScale = true;
         this.reversePortal.setPortalShape(portal.getPortalShape().getReverse());
         if (sync) {
            this.reversePortal.reloadAndSyncToClientNextTick();
         }
      }

      if (this.parallelPortal != null) {
         if (portal.getDestDim() != this.parallelPortal.getEntityWorld().getRegistryKey()) {
            LOGGER.info("Moving parallel portal across dimension {} {}", portal, this.parallelPortal);
         }

         this.parallelPortal = ServerTeleportationManager.teleportRegularEntityTo(this.parallelPortal, portal.getDestDim(), portal.getDestPos());
         this.parallelPortalId = this.parallelPortal.getUuid();
         this.parallelPortal.setDestDim(portal.getOriginDim());
         this.parallelPortal.setOriginPos(portal.getDestPos());
         this.parallelPortal.setDestination(portal.getOriginPos());
         this.parallelPortal.setAxisW(portal.transformLocalVecNonScale(portal.getAxisW()));
         this.parallelPortal.setAxisH(portal.transformLocalVecNonScale(portal.getAxisH()));
         this.parallelPortal.setScaling(1.0 / portal.getScaling());
         if (portal.getRotation() != null) {
            this.parallelPortal.setRotation(portal.getRotation().getConjugated());
         } else {
            this.parallelPortal.setRotation(null);
         }

         this.parallelPortal.setWidth(portal.getPortalWidth() * portal.getScale());
         this.parallelPortal.setHeight(portal.getPortalHeight() * portal.getScale());
         this.parallelPortal.setThickness(portal.getThickness() * portal.getScale());
         PortalManipulation.copyAdditionalProperties(this.parallelPortal, portal, false);
         this.parallelPortal.animation.defaultAnimation.inverseScale = true;
         this.parallelPortal.setPortalShape(portal.getPortalShape().cloneIfNecessary());
         if (sync) {
            this.parallelPortal.reloadAndSyncToClientNextTick();
         }
      }
   }

   public static void initializeClusterBind(Portal f1, Portal f2, Portal t1, Portal t2) {
      get(f1).bindCluster = true;
      get(f2).bindCluster = true;
      get(t1).bindCluster = true;
      get(t2).bindCluster = true;
      get(f1).flippedPortalId = f2.getUuid();
      get(f2).flippedPortalId = f1.getUuid();
      get(t1).flippedPortalId = t2.getUuid();
      get(t2).flippedPortalId = t1.getUuid();
      get(f1).reversePortalId = t1.getUuid();
      get(t1).reversePortalId = f1.getUuid();
      get(f2).reversePortalId = t2.getUuid();
      get(t2).reversePortalId = f2.getUuid();
      get(f1).parallelPortalId = t2.getUuid();
      get(t2).parallelPortalId = f1.getUuid();
      get(f2).parallelPortalId = t1.getUuid();
      get(t1).parallelPortalId = f2.getUuid();
   }

   public static void forClusterPortals(Portal portal, Consumer<Portal> func) {
      func.accept(portal);
      forConnectedPortals(portal, func);
   }

   public static void forConnectedPortals(Portal portal, Consumer<Portal> func) {
      PortalExtension extension = get(portal);
      if (extension.flippedPortal != null) {
         func.accept(extension.flippedPortal);
      }

      if (extension.reversePortal != null) {
         func.accept(extension.reversePortal);
      }

      if (extension.parallelPortal != null) {
         func.accept(extension.parallelPortal);
      }
   }

   public static void forEachClusterPortal(
      Portal portal, Consumer<Portal> forThis, Consumer<Portal> forFlipped, Consumer<Portal> forReverse, Consumer<Portal> forParallel
   ) {
      forThis.accept(portal);
      PortalExtension extension = get(portal);
      if (extension.flippedPortal != null) {
         forFlipped.accept(extension.flippedPortal);
      }

      if (extension.reversePortal != null) {
         forReverse.accept(extension.reversePortal);
      }

      if (extension.parallelPortal != null) {
         forParallel.accept(extension.parallelPortal);
      }
   }
}
