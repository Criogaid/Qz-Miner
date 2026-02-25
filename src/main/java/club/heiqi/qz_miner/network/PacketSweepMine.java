package club.heiqi.qz_miner.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import org.joml.Vector3i;

import java.util.ArrayList;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class PacketSweepMine implements IMessage {
    private static final Queue<SweepMinePayload> CLIENT_PENDING = new ConcurrentLinkedQueue<>();

    public ArrayList<Vector3i> mines = new ArrayList<>();
    public double renderSeconds = -1.0D;

    public PacketSweepMine() {
    }

    public PacketSweepMine(ArrayList<Vector3i> mines) {
        this(mines, -1.0D);
    }

    public PacketSweepMine(ArrayList<Vector3i> mines, double renderSeconds) {
        if (mines != null) {
            this.mines.addAll(mines);
        }
        this.renderSeconds = renderSeconds;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        mines.clear();
        int length = buf.readInt();
        for (int i = 0; i < length; i++) {
            int x = buf.readInt();
            int y = buf.readInt();
            int z = buf.readInt();
            mines.add(new Vector3i(x, y, z));
        }
        // 向后兼容旧协议（仅坐标），缺失时长时由客户端本地配置兜底。
        renderSeconds = buf.readableBytes() >= 8 ? buf.readDouble() : -1.0D;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(mines.size());
        for (Vector3i pos : mines) {
            buf.writeInt(pos.x);
            buf.writeInt(pos.y);
            buf.writeInt(pos.z);
        }
        buf.writeDouble(renderSeconds);
    }

    public static SweepMinePayload pollClientPending() {
        return CLIENT_PENDING.poll();
    }

    private SweepMinePayload copyPayload() {
        ArrayList<Vector3i> copy = new ArrayList<>(mines.size());
        for (Vector3i pos : mines) {
            copy.add(new Vector3i(pos));
        }
        return new SweepMinePayload(copy, renderSeconds);
    }

    public static final class SweepMinePayload {
        public final ArrayList<Vector3i> mines;
        public final double renderSeconds;

        private SweepMinePayload(ArrayList<Vector3i> mines, double renderSeconds) {
            this.mines = mines;
            this.renderSeconds = renderSeconds;
        }
    }

    public static class PacketSweepMineHandler implements IMessageHandler<PacketSweepMine, IMessage> {
        @Override
        public IMessage onMessage(PacketSweepMine message, MessageContext ctx) {
            if (ctx.side.isClient()) {
                CLIENT_PENDING.offer(message.copyPayload());
            }
            return null;
        }
    }
}
