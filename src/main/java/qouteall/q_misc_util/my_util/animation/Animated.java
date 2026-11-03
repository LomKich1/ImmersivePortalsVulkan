package qouteall.q_misc_util.my_util.animation;

import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.Nullable;
import qouteall.q_misc_util.my_util.DQuaternion;
import qouteall.q_misc_util.my_util.Plane;
import qouteall.q_misc_util.my_util.Sphere;
import qouteall.q_misc_util.my_util.WithDim;

public class Animated<T> {
   public final Animated.TypeInfo<T> typeInfo;
   public final Animated.TimeSupplier timeSupplier;
   @Nullable
   public T startValue;
   @Nullable
   public T endValue;
   public long startTime = 0L;
   public long duration = 0L;
   private Animated.TimingFunction timingFunction;
   public static final Animated.TypeInfo<Vec3d> VEC3_NULLABLE_TYPE_INFO = new Animated.TypeInfo<Vec3d>() {
      public Vec3d interpolate(Vec3d start, Vec3d end, double progress) {
         return start.lerp(end, progress);
      }

      public boolean isClose(Vec3d a, Vec3d b) {
         return a.squaredDistanceTo(b) < 0.01;
      }
   };
   public static final Animated.TypeInfo<Vec3d> VEC_3_DEFAULT_ZERO_TYPE_INFO = new Animated.TypeInfo<Vec3d>() {
      public Vec3d interpolate(Vec3d start, Vec3d end, double progress) {
         return start.lerp(end, progress);
      }

      public boolean isClose(Vec3d a, Vec3d b) {
         return a.squaredDistanceTo(b) < 0.01;
      }

      public Vec3d getEmpty() {
         return Vec3d.ZERO;
      }
   };
   public static final Animated.TypeInfo<Double> DOUBLE_DEFAULT_ZERO_TYPE_INFO = new Animated.TypeInfo<Double>() {
      public Double interpolate(Double start, Double end, double progress) {
         if (start == null) {
            start = 0.0;
         }

         if (end == null) {
            end = 0.0;
         }

         return start + (end - start) * progress;
      }

      public boolean isClose(Double a, Double b) {
         if (a == null) {
            a = 0.0;
         }

         if (b == null) {
            b = 0.0;
         }

         return Math.abs(a - b) < 0.001;
      }

      public Double getEmpty() {
         return 0.0;
      }
   };
   public static final Animated.TypeInfo<DQuaternion> QUATERNION_TYPE_INFO = new Animated.TypeInfo<DQuaternion>() {
      public DQuaternion interpolate(DQuaternion start, DQuaternion end, double progress) {
         return DQuaternion.interpolate(start, end, progress);
      }

      public boolean isClose(DQuaternion a, DQuaternion b) {
         return DQuaternion.isClose(a, b);
      }
   };
   public static final Animated.TypeInfo<RenderedPlane> RENDERED_PLANE_TYPE_INFO = new Animated.TypeInfo<RenderedPlane>() {
      public RenderedPlane interpolate(RenderedPlane start, RenderedPlane end, double progress) {
         Validate.notNull(start);
         Validate.notNull(end);
         if (start.plane() == null && end.plane() == null) {
            return start;
         } else {
            double destScale = start.scale() + (end.scale() - start.scale()) * progress;
            if (destScale < 0.01) {
               return RenderedPlane.NONE;
            } else if (start.plane() == null) {
               return new RenderedPlane(end.plane(), destScale);
            } else if (end.plane() == null) {
               return new RenderedPlane(start.plane(), destScale);
            } else {
               return start.plane().dimension() != end.plane().dimension()
                  ? end
                  : new RenderedPlane(
                     new WithDim<>(start.plane().dimension(), Plane.interpolate(start.plane().value(), end.plane().value(), progress)), destScale
                  );
            }
         }
      }

      public boolean isClose(RenderedPlane a, RenderedPlane b) {
         if (a.plane() == null && b.plane() == null) {
            return true;
         } else if (a.plane() != null && b.plane() != null) {
            return a.plane().dimension() != b.plane().dimension()
               ? false
               : a.plane().value().pos().squaredDistanceTo(b.plane().value().pos()) < 0.01
                  && a.plane().value().normal().squaredDistanceTo(b.plane().value().normal()) < 0.01
                  && Math.abs(a.scale() - b.scale()) < 0.01;
         } else {
            return false;
         }
      }

      public RenderedPlane getEmpty() {
         return RenderedPlane.NONE;
      }
   };
   public static final Animated.TypeInfo<RenderedSphere> RENDERED_SPHERE_TYPE_INFO = new Animated.TypeInfo<RenderedSphere>() {
      public RenderedSphere interpolate(RenderedSphere start, RenderedSphere end, double progress) {
         Validate.notNull(start);
         Validate.notNull(end);
         if (start.sphere() == null && end.sphere() == null) {
            return start;
         } else {
            double destScale = start.scale() + (end.scale() - start.scale()) * progress;
            if (destScale < 0.01) {
               return RenderedSphere.NONE;
            } else if (start.sphere() == null) {
               return new RenderedSphere(end.sphere(), end.orientation(), destScale);
            } else if (end.sphere() == null) {
               return new RenderedSphere(start.sphere(), start.orientation(), destScale);
            } else {
               return start.sphere().dimension() != end.sphere().dimension()
                  ? end
                  : new RenderedSphere(
                     new WithDim<>(start.sphere().dimension(), Sphere.interpolate(start.sphere().value(), end.sphere().value(), progress)),
                     DQuaternion.interpolate(start.orientation(), end.orientation(), progress),
                     destScale
                  );
            }
         }
      }

      public boolean isClose(RenderedSphere a, RenderedSphere b) {
         if (a.sphere() == null && b.sphere() == null) {
            return true;
         } else if (a.sphere() != null && b.sphere() != null) {
            return a.sphere().dimension() != b.sphere().dimension()
               ? false
               : a.sphere().value().center().squaredDistanceTo(b.sphere().value().center()) < 0.01
                  && Math.abs(a.sphere().value().radius() - b.sphere().value().radius()) < 0.01
                  && Math.abs(a.scale() - b.scale()) < 0.01;
         } else {
            return false;
         }
      }

      public RenderedSphere getEmpty() {
         return RenderedSphere.NONE;
      }
   };
   public static final Animated.TypeInfo<RenderedPoint> RENDERED_POINT_TYPE_INFO = new Animated.TypeInfo<RenderedPoint>() {
      public RenderedPoint interpolate(RenderedPoint start, RenderedPoint end, double progress) {
         if (start.pos() == null && end.pos() == null) {
            return start;
         } else {
            double destScale = start.scale() + (end.scale() - start.scale()) * progress;
            if (destScale < 0.01) {
               return RenderedPoint.EMPTY;
            } else if (start.pos() == null) {
               return new RenderedPoint(end.pos(), destScale);
            } else if (end.pos() == null) {
               return new RenderedPoint(start.pos(), destScale);
            } else {
               return start.pos().dimension() != end.pos().dimension()
                  ? end
                  : new RenderedPoint(new WithDim<>(start.pos().dimension(), start.pos().value().lerp(end.pos().value(), progress)), destScale);
            }
         }
      }

      public boolean isClose(RenderedPoint a, RenderedPoint b) {
         if (a.pos() == null && b.pos() == null) {
            return true;
         } else if (a.pos() != null && b.pos() != null) {
            return a.pos().dimension() != b.pos().dimension()
               ? false
               : a.pos().value().squaredDistanceTo(b.pos().value()) < 0.01 && Math.abs(a.scale() - b.scale()) < 0.01;
         } else {
            return false;
         }
      }

      public RenderedPoint getEmpty() {
         return RenderedPoint.EMPTY;
      }
   };
   public static final Animated.TypeInfo<RenderedLineSegment> RENDERED_LINE_SEGMENT_TYPE_INFO = new Animated.TypeInfo<RenderedLineSegment>() {
      public RenderedLineSegment interpolate(RenderedLineSegment start, RenderedLineSegment end, double progress) {
         if (start.lineSegment() == null && end.lineSegment() == null) {
            return start;
         } else {
            double destScale = start.scale() + (end.scale() - start.scale()) * progress;
            if (destScale < 0.01) {
               return RenderedLineSegment.EMPTY;
            } else if (start.lineSegment() == null) {
               return new RenderedLineSegment(end.lineSegment(), destScale);
            } else if (end.lineSegment() == null) {
               return new RenderedLineSegment(start.lineSegment(), destScale);
            } else {
               return start.lineSegment().dimension() != end.lineSegment().dimension()
                  ? end
                  : new RenderedLineSegment(
                     new WithDim<>(
                        start.lineSegment().dimension(),
                        start.lineSegment().value().interpolate(start.lineSegment().value(), end.lineSegment().value(), progress)
                     ),
                     destScale
                  );
            }
         }
      }

      public boolean isClose(RenderedLineSegment a, RenderedLineSegment b) {
         if (a.lineSegment() == null && b.lineSegment() == null) {
            return true;
         } else if (a.lineSegment() != null && b.lineSegment() != null) {
            return a.lineSegment().dimension() != b.lineSegment().dimension()
               ? false
               : a.lineSegment().value().isClose(b.lineSegment().value(), 0.01) && Math.abs(a.scale() - b.scale()) < 0.01;
         } else {
            return false;
         }
      }

      public RenderedLineSegment getEmpty() {
         return RenderedLineSegment.EMPTY;
      }
   };

   public Animated(Animated.TypeInfo<T> typeInfo, Animated.TimeSupplier timeSupplier, Animated.TimingFunction timingFunction, @Nullable T initialValue) {
      this.typeInfo = typeInfo;
      this.timeSupplier = timeSupplier;
      this.timingFunction = timingFunction;
      this.startValue = initialValue;
      this.endValue = initialValue;
   }

   public void setTarget(@Nullable T value, long newDuration) {
      if (this.endValue != null && value != null && this.typeInfo.isClose(this.endValue, value)) {
         this.endValue = value;
      } else {
         if (value == null) {
            this.startValue = null;
            this.endValue = null;
            this.startTime = 0L;
         } else {
            long time = this.timeSupplier.getTime();
            if (this.startValue == null) {
               this.startValue = value;
               this.endValue = value;
               this.startTime = time;
            } else {
               this.startValue = this.getCurrent();
               this.endValue = value;
               this.startTime = time;
            }

            this.duration = newDuration;
         }
      }
   }

   public void clearTarget() {
      this.setTarget(this.typeInfo.getEmpty(), this.duration);
   }

   @Nullable
   public T getTarget() {
      return this.endValue;
   }

   @Nullable
   public T getCurrent() {
      if (this.startValue == null || this.endValue == null) {
         return null;
      } else if (this.startTime == 0L) {
         return this.startValue;
      } else if (this.duration == 0L) {
         return this.endValue;
      } else {
         double progress = MathHelper.clamp((double)(this.timeSupplier.getTime() - this.startTime) / this.duration, 0.0, 1.0);
         return progress >= 0.999 ? this.endValue : this.typeInfo.interpolate(this.startValue, this.endValue, this.timingFunction.apply(progress));
      }
   }

   public void setTimingFunction(Animated.TimingFunction timingFunction) {
      this.setTarget(this.getTarget(), this.duration);
      this.timingFunction = timingFunction;
   }

   public interface TimeSupplier {
      long getTime();
   }

   public interface TimingFunction {
      double apply(double var1);
   }

   public interface TypeInfo<T> {
      T interpolate(T var1, T var2, double var3);

      boolean isClose(T var1, T var2);

      default T getEmpty() {
         return null;
      }
   }
}
