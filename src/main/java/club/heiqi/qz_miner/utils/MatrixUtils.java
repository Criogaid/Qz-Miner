package club.heiqi.qz_miner.utils;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import org.joml.*;
import org.lwjgl.BufferUtils;

import java.nio.FloatBuffer;

@SideOnly(Side.CLIENT)
public class MatrixUtils {
    /**
     * 仅计算位移
     */
    public static Matrix4f getModelMatrix(float x, float y, float z) {
        Matrix4f modelMatrix = new Matrix4f();
        modelMatrix.identity();

        modelMatrix.translate(new Vector3f(x, y, z));
        return modelMatrix;
    }

    public static final FloatBuffer floatBuffer = BufferUtils.createFloatBuffer(16);
    public static final Matrix4f modelView = new Matrix4f();
    public static final Matrix4f projection = new Matrix4f();

    public static Matrix4f getModelViewByOriginal() {return modelView;}
    public static Matrix4f getProjectionByOriginal() {return projection;}

    public static Vector3f getCameraPos(float partialTicks) {
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;

        // 位置插值（保持不变）
        double eyeX = player.prevPosX + (player.posX - player.prevPosX) * partialTicks;
        double eyeY = player.prevPosY + (player.posY - player.prevPosY) * partialTicks;
        double eyeZ = player.prevPosZ + (player.posZ - player.prevPosZ) * partialTicks;

        return new Vector3f((float) eyeX, (float) eyeY, (float) eyeZ);
    }
}
