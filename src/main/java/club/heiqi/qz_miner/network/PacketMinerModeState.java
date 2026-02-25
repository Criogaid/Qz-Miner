package club.heiqi.qz_miner.network;

import club.heiqi.qz_miner.MyMod;
import club.heiqi.qz_miner.core.MinerModeState;
import club.heiqi.qz_miner.core.Manager;
import club.heiqi.qz_miner.utils.IMath;
import club.heiqi.qz_miner.utils.PlayerUuidCompat;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;

import java.util.UUID;

public class PacketMinerModeState implements IMessage {
    public MinerModeState state;

    public PacketMinerModeState() {
        state = new MinerModeState();
    }
    public PacketMinerModeState(MinerModeState state) {
        this.state = state;
    }

    public void fromBytes(ByteBuf buf) {
        state.mainMode = IMath.clamp(readOptionalInt(buf, 0), 0, MinerModeState.MAIN_MODE.length - 1);
        state.rangeMode = IMath.clamp(readOptionalInt(buf, 0), 0, MinerModeState.RANGE_MODE.length - 1);
        state.chainMode = IMath.clamp(readOptionalInt(buf, 0), 0, MinerModeState.CHAIN_MODE.length - 1);
        state.interactMode = IMath.clamp(readOptionalInt(buf, 0), 0, MinerModeState.INTERACT_MODE.length - 1);
        // 向后兼容旧协议（仅4个int），缺失字段按默认子模式0处理。
        state.mineRevealMode = IMath.clamp(readOptionalInt(buf, 0), 0, MinerModeState.MINE_REVEAL_MODE.length - 1);
    }

    public void toBytes(ByteBuf buf) {
        buf.writeInt(state.mainMode);
        buf.writeInt(state.rangeMode);
        buf.writeInt(state.chainMode);
        buf.writeInt(state.interactMode);
        buf.writeInt(state.mineRevealMode);
    }

    private static int readOptionalInt(ByteBuf buf, int defaultValue) {
        return buf.readableBytes() >= 4 ? buf.readInt() : defaultValue;
    }

    public static class PacketMinerModeStateHandler implements IMessageHandler<PacketMinerModeState, IMessage> {
        public IMessage onMessage(PacketMinerModeState message, MessageContext ctx) {
            if (ctx.side.isServer()) {
                EntityPlayerMP playerMP = ctx.getServerHandler().playerEntity;
                UUID uuid = PlayerUuidCompat.getPlayerUUID(playerMP);
                if (uuid == null) {
                    return null;
                }
                Manager manager = MyMod.playerManager.managers.get(uuid);
                if (manager == null) {
                    return null;
                }
                manager.minerModeState = message.state;
            }
            return null;
        }
    }
}
