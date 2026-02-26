package club.heiqi.qz_miner.core.founder;

import club.heiqi.qz_miner.core.MinerConfig;
import net.minecraft.entity.player.EntityPlayer;
import org.joml.Vector3i;

import java.util.ArrayList;
import java.util.concurrent.LinkedBlockingQueue;

public class TunnelBlastingFounder extends BasePositionFounder {
    public TunnelBlastingFounder(Vector3i center, LinkedBlockingQueue<Vector3i> results, EntityPlayer player, MinerConfig minerConfig) {
        super(center, results, player, minerConfig);
        setName("隧道搜索器");
    }

    @Override
    public void run1() {
        int curRadius = 0;
        Vector3i lookAxisDir = getAxisAlignedLookDir();
        ArrayList<Vector3i> verticals = getVerticalAxisComponent(lookAxisDir);
        Vector3i verticalA = verticals.get(0);
        Vector3i verticalB = verticals.get(1);
        int tunnelRadius = minerConfig.tunnelWidth; // 隧道半径 轴向半径的方形
        MutableBoxPositionIterator crossSectionIterator = new MutableBoxPositionIterator();
        Vector3i crossSectionOffset = new Vector3i();
        Vector3i scanPos = new Vector3i();
        while (curCount < minerConfig.blockLimit && curRadius < minerConfig.bigRadius) {
            // 沿主方向延伸
            int baseX = center.x + lookAxisDir.x * curRadius;
            int baseY = center.y + lookAxisDir.y * curRadius;
            int baseZ = center.z + lookAxisDir.z * curRadius;

            // 遍历横截面
            crossSectionIterator.reset(-tunnelRadius, tunnelRadius, 0, 0, -tunnelRadius, tunnelRadius);
            while (crossSectionIterator.next(crossSectionOffset)) {
                int a = crossSectionOffset.x;
                int b = crossSectionOffset.z;
                scanPos.set(
                        baseX + verticalA.x * a + verticalB.x * b,
                        baseY + verticalA.y * a + verticalB.y * b,
                        baseZ + verticalA.z * a + verticalB.z * b
                );

                if (!checkCanAdd(scanPos)) continue;
                addResult(scanPos);

                waitUntil();
                // 检查方块数量限制
                if (curCount >= minerConfig.blockLimit) {
                    return;
                }
            }

            waitUntil();
            if (Thread.currentThread().isInterrupted()) {
                LOG.info("线程被中断");
                return;
            }
            curRadius++;
        }
        // LOG.info("结束时半径: {}; 找到数量: {}", curRadius, foundedPositions.size());
    }

    public Vector3i getAxisAlignedLookDir() {
        EntityPlayer player = this.player;
        float yaw = (player.rotationYaw % 360 + 360) % 360;
        float pitch = player.rotationPitch;

        if (pitch > 45) return new Vector3i(0,-1,0);
        else if (pitch < -45) return new Vector3i(0,1,0);
        else if (yaw < 45 || yaw >= 315) return new Vector3i(0,0,1);
        else if (yaw < 135) return new Vector3i(-1,0,0);
        else if (yaw < 225) return new Vector3i(0,0,-1);
        else return new Vector3i(1,0,0);
    }

    public ArrayList<Vector3i> getVerticalAxisComponent(Vector3i axisDir) {
        int x = axisDir.x;
        int y = axisDir.y;
        int z = axisDir.z;

        ArrayList<Vector3i> results = new ArrayList<>();
        if (x == 0) {
            results.add(new Vector3i(1,0,0));
        }
        if (y == 0) {
            results.add(new Vector3i(0,1,0));
        }
        if (z == 0) {
            results.add(new Vector3i(0,0,1));
        }
        return results;
    }
}
