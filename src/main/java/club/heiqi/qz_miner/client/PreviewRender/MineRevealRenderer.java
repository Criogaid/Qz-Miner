package club.heiqi.qz_miner.client.PreviewRender;

import club.heiqi.qz_miner.ClientProxy;
import club.heiqi.qz_miner.Config;
import club.heiqi.qz_miner.MyMod;
import club.heiqi.qz_miner.network.PacketSweepMine;
import club.heiqi.qz_miner.utils.MatrixUtils;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import org.joml.Vector3f;
import org.joml.Vector3i;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;

/**
 * 扫雷揭示渲染器，和连锁预览渲染职责分离。
 */
public class MineRevealRenderer {
    private final ArrayList<Vector3i> revealedMines = new ArrayList<>();
    private long revealExpireNanos = 0L;

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        consumeSweepMinePackets();
        if (!isMineRevealMode()) {
            revealedMines.clear();
            return;
        }
        renderSweepMine(event.partialTicks);
    }

    private void consumeSweepMinePackets() {
        if (!Config.enableLootGameMineReveal) {
            PacketSweepMine.SweepMinePayload discarded = PacketSweepMine.pollClientPending();
            while (discarded != null) {
                // 配置关闭时主动清理积压的网络结果。
                discarded = PacketSweepMine.pollClientPending();
            }
            revealedMines.clear();
            revealExpireNanos = 0L;
            return;
        }

        PacketSweepMine.SweepMinePayload latest = null;
        PacketSweepMine.SweepMinePayload polled;
        while ((polled = PacketSweepMine.pollClientPending()) != null) {
            latest = polled;
        }
        if (latest == null) {
            return;
        }
        revealedMines.clear();
        revealedMines.addAll(latest.mines());
        long now = System.nanoTime();
        long renderNanos = resolveRenderNanos(latest.renderSeconds());
        revealExpireNanos = now + renderNanos;
    }

    private long resolveRenderNanos(double packetRenderSeconds) {
        double renderSeconds = packetRenderSeconds >= 0.0D ? packetRenderSeconds : Config.lootGameMineRevealRenderSeconds;
        return (long) (Math.max(0.0D, renderSeconds) * 1_000_000_000L);
    }

    private void renderSweepMine(float partialTicks) {
        if (!Config.enableLootGameMineReveal || revealedMines.isEmpty()) {
            return;
        }
        if (System.nanoTime() > revealExpireNanos) {
            revealedMines.clear();
            return;
        }
        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        if (player == null || player.worldObj == null) {
            return;
        }
        Vector3i closest = findClosestMine(player);
        if (closest == null) {
            return;
        }
        renderMineBox(closest, partialTicks);
    }

    private Vector3i findClosestMine(EntityPlayer player) {
        Vector3i closest = null;
        double bestDistance = Double.MAX_VALUE;
        for (Vector3i mine : revealedMines) {
            double dx = (mine.x + 0.5D) - player.posX;
            double dy = (mine.y + 0.5D) - player.posY;
            double dz = (mine.z + 0.5D) - player.posZ;
            double distance = dx * dx + dy * dy + dz * dz;
            if (distance < bestDistance) {
                bestDistance = distance;
                closest = mine;
            }
        }
        return closest;
    }

    private void renderMineBox(Vector3i pos, float partialTicks) {
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        try {
            Vector3f camera = MatrixUtils.getCameraPos(partialTicks);
            GL11.glLineWidth((float) Math.max(0.1D, Config.lootGameMineRevealLineWidth));
            GL11.glTranslated(pos.x - camera.x, pos.y - camera.y + 0.05D, pos.z - camera.z);
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

            GL11.glBegin(GL11.GL_LINES);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glVertex3f(1, 1, 1); GL11.glVertex3f(1, 1, 0);
            GL11.glVertex3f(1, 1, 0); GL11.glVertex3f(1, 0, 0);
            GL11.glVertex3f(1, 0, 0); GL11.glVertex3f(1, 0, 1);
            GL11.glVertex3f(1, 0, 1); GL11.glVertex3f(1, 1, 1);

            GL11.glVertex3f(0, 0, 1); GL11.glVertex3f(0, 0, 0);
            GL11.glVertex3f(0, 0, 0); GL11.glVertex3f(0, 1, 0);
            GL11.glVertex3f(0, 1, 0); GL11.glVertex3f(0, 1, 1);
            GL11.glVertex3f(0, 1, 1); GL11.glVertex3f(0, 0, 1);

            GL11.glVertex3f(1, 0, 1); GL11.glVertex3f(0, 0, 1);
            GL11.glVertex3f(0, 1, 1); GL11.glVertex3f(1, 1, 1);
            GL11.glVertex3f(0, 0, 0); GL11.glVertex3f(1, 0, 0);
            GL11.glVertex3f(1, 1, 0); GL11.glVertex3f(0, 1, 0);
            GL11.glEnd();
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
        }
    }

    private boolean isMineRevealMode() {
        return ((ClientProxy) MyMod.proxy).clientState.minerModeState.isMineRevealMode();
    }

    public void registry() {
        MinecraftForge.EVENT_BUS.register(this);
    }
}
