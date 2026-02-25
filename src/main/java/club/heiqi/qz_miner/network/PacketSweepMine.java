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
    private static final Queue<ArrayList<Vector3i>> CLIENT_PENDING = new ConcurrentLinkedQueue<>();

    public ArrayList<Vector3i> mines = new ArrayList<>();

    public PacketSweepMine() {
    }

    public PacketSweepMine(ArrayList<Vector3i> mines) {
        if (mines != null) {
            this.mines.addAll(mines);
        }
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
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(mines.size());
        for (Vector3i pos : mines) {
            buf.writeInt(pos.x);
            buf.writeInt(pos.y);
            buf.writeInt(pos.z);
        }
    }

    public static ArrayList<Vector3i> pollClientPending() {
        return CLIENT_PENDING.poll();
    }

    private ArrayList<Vector3i> copyMines() {
        ArrayList<Vector3i> copy = new ArrayList<>(mines.size());
        for (Vector3i pos : mines) {
            copy.add(new Vector3i(pos));
        }
        return copy;
    }

    public static class PacketSweepMineHandler implements IMessageHandler<PacketSweepMine, IMessage> {
        @Override
        public IMessage onMessage(PacketSweepMine message, MessageContext ctx) {
            if (ctx.side.isClient()) {
                CLIENT_PENDING.offer(message.copyMines());
            }
            return null;
        }
    }
}
