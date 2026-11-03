package qouteall.imm_ptl.core.portal;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.NbtList;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import qouteall.q_misc_util.my_util.Mesh2D;

public class GeometryPortalShape {
   private static final Logger LOGGER = LogUtils.getLogger();
   public static final int MAX_TRIANGLE_NUM = 10000;

   @Nullable
   public static Mesh2D readOldMeshFromTag(NbtList tag) {
      int size = tag.size();
      if (size % 6 != 0) {
         LOGGER.error("Invalid Portal Shape Data {}", tag);
         return null;
      } else {
         Mesh2D mesh = new Mesh2D();
         int triangleNum = size / 6;
         triangleNum = Math.min(triangleNum, 10000);

         for (int i = 0; i < triangleNum; i++) {
            mesh.addTriangle(
               tag.getDouble(i * 6 + 0, 0.0),
               tag.getDouble(i * 6 + 1, 0.0),
               tag.getDouble(i * 6 + 2, 0.0),
               tag.getDouble(i * 6 + 3, 0.0),
               tag.getDouble(i * 6 + 4, 0.0),
               tag.getDouble(i * 6 + 5, 0.0)
            );
         }

         return mesh.getStoredTriangleNum() == 0 ? null : mesh;
      }
   }

   @Nullable
   public static Mesh2D readOldMeshFromTagNonNormalized(NbtList tag, double halfWidth, double halfHeight) {
      int size = tag.size();
      if (size % 6 != 0) {
         LOGGER.error("Invalid Portal Shape Data {}", tag);
         return null;
      } else {
         Mesh2D mesh = new Mesh2D();
         int triangleNum = size / 6;
         triangleNum = Math.min(triangleNum, 10000);

         for (int i = 0; i < triangleNum; i++) {
            mesh.addTriangle(
               tag.getDouble(i * 6 + 0, 0.0) / halfWidth,
               tag.getDouble(i * 6 + 1, 0.0) / halfHeight,
               tag.getDouble(i * 6 + 2, 0.0) / halfWidth,
               tag.getDouble(i * 6 + 3, 0.0) / halfHeight,
               tag.getDouble(i * 6 + 4, 0.0) / halfWidth,
               tag.getDouble(i * 6 + 5, 0.0) / halfHeight
            );
         }

         return mesh.getStoredTriangleNum() == 0 ? null : mesh;
      }
   }
}
