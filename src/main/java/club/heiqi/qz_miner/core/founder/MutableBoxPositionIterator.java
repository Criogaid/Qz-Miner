package club.heiqi.qz_miner.core.founder;

import org.joml.Vector3i;

/**
 * 复用式包围盒坐标迭代器，按 x -> y -> z 顺序遍历，避免在热路径中构造 Stream/临时集合。
 */
final class MutableBoxPositionIterator {
    private int minX;
    private int maxX;
    private int minY;
    private int maxY;
    private int minZ;
    private int maxZ;
    private int x;
    private int y;
    private int z;
    private boolean hasNext;

    public void reset(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        this.minX = minX;
        this.maxX = maxX;
        this.minY = minY;
        this.maxY = maxY;
        this.minZ = minZ;
        this.maxZ = maxZ;

        this.x = minX;
        this.y = minY;
        this.z = minZ;
        this.hasNext = minX <= maxX && minY <= maxY && minZ <= maxZ;
    }

    public boolean next(Vector3i outPos) {
        if (!hasNext) {
            return false;
        }
        outPos.set(x, y, z);
        advance();
        return true;
    }

    private void advance() {
        if (z < maxZ) {
            z++;
            return;
        }
        z = minZ;

        if (y < maxY) {
            y++;
            return;
        }
        y = minY;

        if (x < maxX) {
            x++;
            return;
        }
        hasNext = false;
    }
}
