package club.heiqi.qz_miner.utils;


import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

public class GLDebugUtils {
    /**
     * 单次查询错误（不循环）
     */
    public static void checkGLErrorSingle() {
        int error = GL11.glGetError();
        if (error != GL11.GL_NO_ERROR) {
            throw new RuntimeException("OpenGL Error: " + getErrorString(error));
        }
    }

    /**
     * 获取错误描述
     */
    private static String getErrorString(int error) {
        return switch (error) {
            case GL11.GL_INVALID_ENUM -> "无效枚举参数";
            case GL11.GL_INVALID_VALUE -> "无效数值参数";
            case GL11.GL_INVALID_OPERATION -> "非法操作（状态不匹配）";
            case GL11.GL_OUT_OF_MEMORY -> "内存不足";
            case GL11.GL_STACK_OVERFLOW -> "栈溢出";
            case GL11.GL_STACK_UNDERFLOW -> "栈下溢";
            case GL30.GL_INVALID_FRAMEBUFFER_OPERATION -> "帧缓冲不完整";
            default -> "未知错误 (0x" + Integer.toHexString(error) + ")";
        };
    }
}
