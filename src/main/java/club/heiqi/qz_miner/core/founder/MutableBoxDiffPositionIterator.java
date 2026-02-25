package club.heiqi.qz_miner.core.founder;

import org.joml.Vector3i;

/**
 * 遍历两个包围盒的差集（newBox - prevBox），用于壳层增量扫描。
 */
final class MutableBoxDiffPositionIterator {
    private final MutableBoxPositionIterator rangeIterator = new MutableBoxPositionIterator();
    private int minX;
    private int maxX;
    private int minY;
    private int maxY;
    private int minZ;
    private int maxZ;
    private int prevMinX;
    private int prevMaxX;
    private int prevMinY;
    private int prevMaxY;
    private int prevMinZ;
    private int prevMaxZ;
    private int stage;
    private boolean hasRange;

    public void reset(
            int minX, int maxX, int minY, int maxY, int minZ, int maxZ,
            int prevMinX, int prevMaxX, int prevMinY, int prevMaxY, int prevMinZ, int prevMaxZ,
            boolean hasPrev
    ) {
        this.minX = minX;
        this.maxX = maxX;
        this.minY = minY;
        this.maxY = maxY;
        this.minZ = minZ;
        this.maxZ = maxZ;
        this.prevMinX = prevMinX;
        this.prevMaxX = prevMaxX;
        this.prevMinY = prevMinY;
        this.prevMaxY = prevMaxY;
        this.prevMinZ = prevMinZ;
        this.prevMaxZ = prevMaxZ;

        if (!hasPrev) {
            hasRange = resetRangeIfValid(minX, maxX, minY, maxY, minZ, maxZ);
            stage = 6;
            return;
        }

        stage = 0;
        hasRange = moveToNextRange();
    }

    public boolean next(Vector3i outPos) {
        while (hasRange) {
            if (rangeIterator.next(outPos)) {
                return true;
            }
            hasRange = moveToNextRange();
        }
        return false;
    }

    private boolean moveToNextRange() {
        while (stage < 6) {
            switch (stage++) {
                case 0:
                    if (resetRangeIfValid(minX, prevMinX - 1, minY, maxY, minZ, maxZ)) {
                        return true;
                    }
                    break;
                case 1:
                    if (resetRangeIfValid(prevMaxX + 1, maxX, minY, maxY, minZ, maxZ)) {
                        return true;
                    }
                    break;
                case 2:
                    if (resetRangeIfValid(prevMinX, prevMaxX, minY, prevMinY - 1, minZ, maxZ)) {
                        return true;
                    }
                    break;
                case 3:
                    if (resetRangeIfValid(prevMinX, prevMaxX, prevMaxY + 1, maxY, minZ, maxZ)) {
                        return true;
                    }
                    break;
                case 4:
                    if (resetRangeIfValid(prevMinX, prevMaxX, prevMinY, prevMaxY, minZ, prevMinZ - 1)) {
                        return true;
                    }
                    break;
                case 5:
                    if (resetRangeIfValid(prevMinX, prevMaxX, prevMinY, prevMaxY, prevMaxZ + 1, maxZ)) {
                        return true;
                    }
                    break;
                default:
                    break;
            }
        }
        return false;
    }

    private boolean resetRangeIfValid(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            return false;
        }
        rangeIterator.reset(minX, maxX, minY, maxY, minZ, maxZ);
        return true;
    }
}
