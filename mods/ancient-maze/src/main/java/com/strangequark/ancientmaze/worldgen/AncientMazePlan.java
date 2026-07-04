package com.strangequark.ancientmaze.worldgen;

import net.minecraft.util.math.random.Random;

import java.util.ArrayDeque;
import java.util.Queue;

final class AncientMazePlan {
    private static final int GENERATION_ATTEMPTS = 8;

    private final int cellCount;
    private final int corridorWidth;
    private final int wallThickness;
    private final int totalWidth;
    private final boolean[][] eastOpen;
    private final boolean[][] southOpen;
    private final Entrance entrance;
    private final int centerCell;

    private AncientMazePlan(
            int cellCount,
            int corridorWidth,
            int wallThickness,
            boolean[][] eastOpen,
            boolean[][] southOpen,
            Entrance entrance
    ) {
        this.cellCount = cellCount;
        this.corridorWidth = corridorWidth;
        this.wallThickness = wallThickness;
        this.totalWidth = totalWidth(cellCount, corridorWidth, wallThickness);
        this.eastOpen = eastOpen;
        this.southOpen = southOpen;
        this.entrance = entrance;
        this.centerCell = cellCount / 2;
    }

    static AncientMazePlan generate(long seed, int cellCount, int corridorWidth, int wallThickness) {
        int normalizedCellCount = normalizeCellCount(cellCount);
        for (int attempt = 0; attempt < GENERATION_ATTEMPTS; attempt++) {
            AncientMazePlan plan = generateAttempt(seed + attempt * 0x9E3779B97F4A7C15L, normalizedCellCount, corridorWidth, wallThickness);
            if (plan.hasValidEntranceToCenterPath()) {
                return plan;
            }
        }
        return fallback(normalizedCellCount, corridorWidth, wallThickness);
    }

    static int totalWidth(int cellCount, int corridorWidth, int wallThickness) {
        return cellCount * corridorWidth + (cellCount + 1) * wallThickness;
    }

    static int totalHeight(int wallHeight) {
        return wallHeight + 2;
    }

    int totalWidth() {
        return totalWidth;
    }

    int corridorWidth() {
        return corridorWidth;
    }

    private static int normalizeCellCount(int cellCount) {
        int normalized = Math.max(5, cellCount);
        return normalized % 2 == 0 ? normalized + 1 : normalized;
    }

    int centerBlockOffset() {
        return cellStart(centerCell) + corridorWidth / 2;
    }

    int entranceBlockOffsetX() {
        return switch (entrance.side()) {
            case NORTH, SOUTH -> cellStart(entrance.cellX()) + corridorWidth / 2;
            case WEST -> wallThickness / 2;
            case EAST -> totalWidth - 1 - wallThickness / 2;
        };
    }

    int entranceBlockOffsetZ() {
        return switch (entrance.side()) {
            case NORTH -> wallThickness / 2;
            case SOUTH -> totalWidth - 1 - wallThickness / 2;
            case WEST, EAST -> cellStart(entrance.cellZ()) + corridorWidth / 2;
        };
    }

    boolean hasEntranceToCenterPath() {
        return hasPath(entrance.cellX(), entrance.cellZ(), centerCell, centerCell);
    }

    boolean hasValidEntranceToCenterPath() {
        return hasSingleEntranceOpening()
                && hasEntranceOpeningConnectedToEntranceCell()
                && hasEntranceToCenterPath();
    }

    boolean hasSingleEntranceOpening() {
        int openPerimeterBlocks = 0;
        for (int x = 0; x < totalWidth; x++) {
            for (int z = 0; z < totalWidth; z++) {
                if (!isPerimeterBlock(x, z)) {
                    continue;
                }
                boolean open = isEntranceOpening(x, z);
                if (open) {
                    openPerimeterBlocks++;
                }
                if (isOpenBlock(x, z) != open) {
                    return false;
                }
            }
        }
        return openPerimeterBlocks == corridorWidth * wallThickness;
    }

    boolean hasEntranceOpeningConnectedToEntranceCell() {
        int corridorStart = switch (entrance.side()) {
            case NORTH, SOUTH -> cellStart(entrance.cellX());
            case WEST, EAST -> cellStart(entrance.cellZ());
        };

        for (int i = 0; i < corridorWidth; i++) {
            for (int depth = 0; depth < wallThickness; depth++) {
                int openingX = switch (entrance.side()) {
                    case NORTH, SOUTH -> corridorStart + i;
                    case WEST -> depth;
                    case EAST -> totalWidth - 1 - depth;
                };
                int openingZ = switch (entrance.side()) {
                    case NORTH -> depth;
                    case SOUTH -> totalWidth - 1 - depth;
                    case WEST, EAST -> corridorStart + i;
                };
                if (!isOpenBlock(openingX, openingZ)) {
                    return false;
                }
            }

            int interiorX = switch (entrance.side()) {
                case NORTH, SOUTH -> corridorStart + i;
                case WEST -> wallThickness;
                case EAST -> totalWidth - wallThickness - 1;
            };
            int interiorZ = switch (entrance.side()) {
                case NORTH -> wallThickness;
                case SOUTH -> totalWidth - wallThickness - 1;
                case WEST, EAST -> corridorStart + i;
            };
            if (!isOpenBlock(interiorX, interiorZ)) {
                return false;
            }
        }

        return true;
    }


    boolean isOpenBlock(int localX, int localZ) {
        if (localX < 0 || localZ < 0 || localX >= totalWidth || localZ >= totalWidth) {
            return false;
        }
        if (isPerimeterBlock(localX, localZ)) {
            return isEntranceOpening(localX, localZ);
        }

        Axis xAxis = locateAxis(localX);
        Axis zAxis = locateAxis(localZ);
        if (xAxis.kind() == AxisKind.CORRIDOR && zAxis.kind() == AxisKind.CORRIDOR) {
            return true;
        }
        if (xAxis.kind() == AxisKind.WALL_AFTER_CELL && zAxis.kind() == AxisKind.CORRIDOR) {
            return xAxis.cell() >= 0 && xAxis.cell() < cellCount - 1 && eastOpen[xAxis.cell()][zAxis.cell()];
        }
        if (zAxis.kind() == AxisKind.WALL_AFTER_CELL && xAxis.kind() == AxisKind.CORRIDOR) {
            return zAxis.cell() >= 0 && zAxis.cell() < cellCount - 1 && southOpen[xAxis.cell()][zAxis.cell()];
        }
        return false;
    }

    private static AncientMazePlan generateAttempt(long seed, int cellCount, int corridorWidth, int wallThickness) {
        Random random = Random.create(seed);
        boolean[][] eastOpen = new boolean[cellCount][cellCount];
        boolean[][] southOpen = new boolean[cellCount][cellCount];
        carvePerfectMaze(random, cellCount, eastOpen, southOpen);
        carveEntranceRunToCenter(cellCount, southOpen);
        carveCenterChamber(cellCount, eastOpen, southOpen);
        Entrance entrance = entrance(cellCount);
        return new AncientMazePlan(cellCount, corridorWidth, wallThickness, eastOpen, southOpen, entrance);
    }

    private static void carvePerfectMaze(Random random, int cellCount, boolean[][] eastOpen, boolean[][] southOpen) {
        boolean[][] visited = new boolean[cellCount][cellCount];
        int[] stackX = new int[cellCount * cellCount];
        int[] stackZ = new int[cellCount * cellCount];
        int top = 0;
        int start = cellCount / 2;
        stackX[top] = start;
        stackZ[top] = start;
        visited[start][start] = true;

        int[] directions = {0, 1, 2, 3};
        while (top >= 0) {
            int x = stackX[top];
            int z = stackZ[top];
            shuffleDirections(directions, random);

            boolean advanced = false;
            for (int direction : directions) {
                int nx = x + offsetX(direction);
                int nz = z + offsetZ(direction);
                if (nx < 0 || nz < 0 || nx >= cellCount || nz >= cellCount || visited[nx][nz]) {
                    continue;
                }

                openBetween(x, z, nx, nz, eastOpen, southOpen);
                visited[nx][nz] = true;
                top++;
                stackX[top] = nx;
                stackZ[top] = nz;
                advanced = true;
                break;
            }

            if (!advanced) {
                top--;
            }
        }
    }

    private static void carveCenterChamber(int cellCount, boolean[][] eastOpen, boolean[][] southOpen) {
        int center = cellCount / 2;
        int min = Math.max(0, center - 1);
        int max = Math.min(cellCount - 1, center + 1);
        for (int x = min; x <= max; x++) {
            for (int z = min; z <= max; z++) {
                if (x < max) {
                    eastOpen[x][z] = true;
                }
                if (z < max) {
                    southOpen[x][z] = true;
                }
            }
        }
    }

    private static void carveEntranceRunToCenter(int cellCount, boolean[][] southOpen) {
        int center = cellCount / 2;
        for (int z = 0; z < center - 1; z++) {
            southOpen[center][z] = true;
        }
    }

    private static Entrance entrance(int cellCount) {
        return new Entrance(EntranceSide.NORTH, cellCount / 2, 0);
    }

    private static AncientMazePlan fallback(int cellCount, int corridorWidth, int wallThickness) {
        boolean[][] eastOpen = new boolean[cellCount][cellCount];
        boolean[][] southOpen = new boolean[cellCount][cellCount];
        carveEntranceRunToCenter(cellCount, southOpen);
        carveCenterChamber(cellCount, eastOpen, southOpen);
        return new AncientMazePlan(cellCount, corridorWidth, wallThickness, eastOpen, southOpen, entrance(cellCount));
    }

    private boolean hasPath(int startX, int startZ, int targetX, int targetZ) {
        boolean[][] visited = new boolean[cellCount][cellCount];
        Queue<Cell> queue = new ArrayDeque<>();
        queue.add(new Cell(startX, startZ));
        visited[startX][startZ] = true;

        while (!queue.isEmpty()) {
            Cell cell = queue.remove();
            if (cell.x() == targetX && cell.z() == targetZ) {
                return true;
            }
            addReachableNeighbor(queue, visited, cell.x() + 1, cell.z(), isEastOpen(cell.x(), cell.z()));
            addReachableNeighbor(queue, visited, cell.x() - 1, cell.z(), isEastOpen(cell.x() - 1, cell.z()));
            addReachableNeighbor(queue, visited, cell.x(), cell.z() + 1, isSouthOpen(cell.x(), cell.z()));
            addReachableNeighbor(queue, visited, cell.x(), cell.z() - 1, isSouthOpen(cell.x(), cell.z() - 1));
        }
        return false;
    }

    private void addReachableNeighbor(Queue<Cell> queue, boolean[][] visited, int nx, int nz, boolean open) {
        if (!open || nx < 0 || nz < 0 || nx >= cellCount || nz >= cellCount || visited[nx][nz]) {
            return;
        }
        visited[nx][nz] = true;
        queue.add(new Cell(nx, nz));
    }

    private boolean isEastOpen(int x, int z) {
        return x >= 0 && z >= 0 && x < cellCount - 1 && z < cellCount && eastOpen[x][z];
    }

    private boolean isSouthOpen(int x, int z) {
        return x >= 0 && z >= 0 && x < cellCount && z < cellCount - 1 && southOpen[x][z];
    }

    private boolean isEntranceOpening(int localX, int localZ) {
        return switch (entrance.side()) {
            case NORTH -> localZ < wallThickness && isWithinCellCorridor(localX, entrance.cellX());
            case SOUTH -> localZ >= totalWidth - wallThickness && isWithinCellCorridor(localX, entrance.cellX());
            case WEST -> localX < wallThickness && isWithinCellCorridor(localZ, entrance.cellZ());
            case EAST -> localX >= totalWidth - wallThickness && isWithinCellCorridor(localZ, entrance.cellZ());
        };
    }

    private boolean isPerimeterBlock(int localX, int localZ) {
        return localX < wallThickness
                || localZ < wallThickness
                || localX >= totalWidth - wallThickness
                || localZ >= totalWidth - wallThickness;
    }

    private boolean isWithinCellCorridor(int local, int cell) {
        int start = cellStart(cell);
        return local >= start && local < start + corridorWidth;
    }

    private Axis locateAxis(int local) {
        if (local < wallThickness) {
            return new Axis(AxisKind.BOUNDARY_WALL, -1);
        }

        int inner = local - wallThickness;
        int period = corridorWidth + wallThickness;
        int cell = inner / period;
        if (cell >= cellCount) {
            return new Axis(AxisKind.BOUNDARY_WALL, cellCount);
        }

        int offset = inner % period;
        return offset < corridorWidth
                ? new Axis(AxisKind.CORRIDOR, cell)
                : new Axis(AxisKind.WALL_AFTER_CELL, cell);
    }

    private int cellStart(int cell) {
        return wallThickness + cell * (corridorWidth + wallThickness);
    }

    private static void openBetween(int x, int z, int nx, int nz, boolean[][] eastOpen, boolean[][] southOpen) {
        if (nx == x + 1) {
            eastOpen[x][z] = true;
        } else if (nx == x - 1) {
            eastOpen[nx][nz] = true;
        } else if (nz == z + 1) {
            southOpen[x][z] = true;
        } else if (nz == z - 1) {
            southOpen[nx][nz] = true;
        }
    }

    private static void shuffleDirections(int[] directions, Random random) {
        for (int i = directions.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int value = directions[i];
            directions[i] = directions[j];
            directions[j] = value;
        }
    }

    private static int offsetX(int direction) {
        return switch (direction) {
            case 1 -> 1;
            case 3 -> -1;
            default -> 0;
        };
    }

    private static int offsetZ(int direction) {
        return switch (direction) {
            case 0 -> -1;
            case 2 -> 1;
            default -> 0;
        };
    }

    private enum AxisKind {
        BOUNDARY_WALL,
        CORRIDOR,
        WALL_AFTER_CELL
    }

    private enum EntranceSide {
        NORTH,
        SOUTH,
        WEST,
        EAST
    }

    private record Axis(AxisKind kind, int cell) {
    }

    private record Entrance(EntranceSide side, int cellX, int cellZ) {
    }

    private record Cell(int x, int z) {
    }
}
