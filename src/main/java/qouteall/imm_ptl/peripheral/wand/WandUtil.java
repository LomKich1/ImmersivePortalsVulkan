package qouteall.imm_ptl.peripheral.wand;

import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.mc_utils.WireRenderingHelper;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.my_util.IntBox;

public class WandUtil {
   @Nullable
   @Environment(EnvType.CLIENT)
   public static Portal getClientPortalByUUID(UUID portalId) {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      if (player == null) {
         return null;
      } else {
         World world = player.getEntityWorld();
         return getPortalByUUID(world, portalId);
      }
   }

   @Nullable
   public static Portal getPortalByUUID(World world, UUID portalId) {
      if (portalId == null) {
         return null;
      } else {
         return McHelper.getEntityByUUID(world, portalId) instanceof Portal portal ? portal : null;
      }
   }

   public static Vec3d alignOnBlocks(World world, Vec3d vec3, int gridCount) {
      if (gridCount == 0) {
         return vec3;
      } else {
         BlockPos blockPos = BlockPos.ofFloored(vec3);
         return new IntBox(blockPos.add(-1, -1, -1), blockPos.add(1, 1, 1)).stream().flatMap(pos -> {
            BlockState blockState = world.getBlockState(pos);
            VoxelShape collisionShape = blockState.getCollisionShape(world, pos).offset(pos.getX(), pos.getY(), pos.getZ());
            List<Box> aabbs = collisionShape.getBoundingBoxes();
            if (aabbs.size() != 1) {
               aabbs.add(new Box(pos));
            }

            return aabbs.stream();
         }).map(box -> Helper.alignToBoxSurface(box, vec3, gridCount)).min(Comparator.comparingDouble(p -> p.squaredDistanceTo(vec3))).orElse(null);
      }
   }

   @Environment(EnvType.CLIENT)
   public static void renderPortalAreaGridNew(
      VertexConsumer vertexConsumer, Vec3d cameraPos, ProtoPortalSide protoPortalSide, int color1, int color2, MatrixStack matrixStack
   ) {
      int separation = 8;
      Vec3d leftBottom = protoPortalSide.leftBottom;
      Vec3d rightBottom = protoPortalSide.rightBottom;
      Vec3d leftTop = protoPortalSide.leftTop;
      Vec3d xAxis = rightBottom.subtract(leftBottom);
      Vec3d yAxis = leftTop.subtract(leftBottom);
      Vec3d normal = xAxis.crossProduct(yAxis).normalize();
      matrixStack.push();
      matrixStack.translate(leftBottom.x - cameraPos.x, leftBottom.y - cameraPos.y, leftBottom.z - cameraPos.z);
      Matrix4f matrix = matrixStack.peek().getPositionMatrix();
      Matrix3f normalMatrix = matrixStack.peek().getNormalMatrix();
      WireRenderingHelper.putLine(vertexConsumer, color1, matrix, normalMatrix, Vec3d.ZERO, xAxis);
      WireRenderingHelper.putLine(vertexConsumer, color1, matrix, normalMatrix, yAxis, yAxis.add(xAxis));
      WireRenderingHelper.putLine(vertexConsumer, color1, matrix, normalMatrix, Vec3d.ZERO, yAxis);
      WireRenderingHelper.putLine(vertexConsumer, color1, matrix, normalMatrix, xAxis, yAxis.add(xAxis));
      int flowCount = 3;
      Random random = new Random(color1);
      double scaling = 0.994;
      double offset1 = (1.0 - scaling) / 2.0;
      double offset2 = -offset1;

      for (int cx = 1; cx < separation; cx++) {
         double lx = (double)cx / separation;
         WireRenderingHelper.renderFlowLines(
            vertexConsumer,
            new Vec3d[]{xAxis.multiply(lx * scaling + offset1), xAxis.multiply(lx * scaling + offset1).add(yAxis)},
            flowCount,
            color1,
            1,
            matrixStack,
            () -> random.nextInt(10, 100)
         );
         WireRenderingHelper.renderFlowLines(
            vertexConsumer,
            new Vec3d[]{xAxis.multiply(lx * scaling + offset2), xAxis.multiply(lx * scaling + offset2).add(yAxis)},
            flowCount,
            color2,
            -1,
            matrixStack,
            () -> random.nextInt(10, 100)
         );
      }

      for (int cy = 1; cy < separation; cy++) {
         double ly = (double)cy / separation;
         WireRenderingHelper.renderFlowLines(
            vertexConsumer,
            new Vec3d[]{yAxis.multiply(ly * scaling + offset1), yAxis.multiply(ly * scaling + offset1).add(xAxis)},
            flowCount,
            color1,
            1,
            matrixStack,
            () -> random.nextInt(10, 100)
         );
         WireRenderingHelper.renderFlowLines(
            vertexConsumer,
            new Vec3d[]{yAxis.multiply(ly * scaling + offset2), yAxis.multiply(ly * scaling + offset2).add(xAxis)},
            flowCount,
            color2,
            -1,
            matrixStack,
            () -> random.nextInt(10, 100)
         );
      }

      matrixStack.pop();
   }

   @Environment(EnvType.CLIENT)
   public static void renderPortalAreaGrid(VertexConsumer vertexConsumer, Vec3d cameraPos, ProtoPortalSide protoPortalSide, int color, MatrixStack matrixStack) {
      int separation = 8;
      Vec3d leftBottom = protoPortalSide.leftBottom;
      Vec3d rightBottom = protoPortalSide.rightBottom;
      Vec3d leftTop = protoPortalSide.leftTop;
      Vec3d xAxis = rightBottom.subtract(leftBottom);
      Vec3d yAxis = leftTop.subtract(leftBottom);
      Vec3d normal = xAxis.crossProduct(yAxis).normalize();
      matrixStack.push();
      matrixStack.translate(leftBottom.x - cameraPos.x, leftBottom.y - cameraPos.y, leftBottom.z - cameraPos.z);
      Matrix4f matrix = matrixStack.peek().getPositionMatrix();
      Matrix3f normalMatrix = matrixStack.peek().getNormalMatrix();

      for (int i = 0; i <= separation; i++) {
         double ratio = (double)i / separation;
         Vec3d lineStart = xAxis.multiply(ratio);
         Vec3d lineEnd = xAxis.multiply(ratio).add(yAxis);
         WireRenderingHelper.putLine(vertexConsumer, color, yAxis.normalize(), matrix, normalMatrix, lineStart, lineEnd);
      }

      for (int i = 0; i <= separation; i++) {
         double ratio = (double)i / separation;
         Vec3d lineStart = yAxis.multiply(ratio);
         Vec3d lineEnd = yAxis.multiply(ratio).add(xAxis);
         WireRenderingHelper.putLine(vertexConsumer, color, xAxis.normalize(), matrix, normalMatrix, lineStart, lineEnd);
      }

      matrixStack.pop();
   }
}
