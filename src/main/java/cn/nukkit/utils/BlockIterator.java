package cn.nukkit.utils;

import cn.nukkit.block.Block;
import cn.nukkit.level.Level;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.Vector3;

import java.lang.ref.SoftReference;
import java.util.Iterator;
import java.util.Objects;

/**
 * This class performs ray tracing and iterates along blocks on a line.
 *
 * @author MagicDroidX
 * Nukkit Project
 */
public class BlockIterator implements Iterator<Block> {

    private static final int GRID_SIZE = 16777216;

    private final SoftReference<Level> level;

    private final int maxDistance;

    private boolean end = false;

    private final Vector3[] vector3Queue;
    private int currentBlock;

    private int currentDistance;
    private final int maxDistanceInt;

    private int secondError;
    private int thirdError;

    private final int secondStep;
    private final int thirdStep;

    private BlockFace mainFace;
    private BlockFace secondFace;
    private BlockFace thirdFace;

    public BlockIterator(Level level, Vector3 start, Vector3 direction) {
        this(level, start, direction, 0);
    }

    public BlockIterator(Level level, Vector3 start, Vector3 direction, double yOffset) {
        this(level, start, direction, yOffset, 0);
    }

    /**
     * Constructs the BlockIterator.
     * <p>
     * This considers all blocks as 1x1x1 in size.
     *
     * @param level The level to use for tracing
     * @param start A Vector giving the initial location for the trace
     * @param direction A Vector pointing in the direction for the trace
     * @param yOffset The trace begins vertically offset from the start vector by this value
     * @param maxDistance This is the maximum distance in blocks for the trace.
     *                    Setting this value above 140 may lead to problems with unloaded chunks.
     *                    A value of 0 indicates no limit
     *
     */
    public BlockIterator(Level level, Vector3 start, Vector3 direction, double yOffset, int maxDistance) {
        this.level = new SoftReference<>(level);
        this.maxDistance = maxDistance;
        this.vector3Queue = new Vector3[3];

        Vector3 startClone = new Vector3(start.x, start.y, start.z);
        startClone.y += yOffset;

        this.currentDistance = 0;

        double mainDirection = 0;
        double secondDirection = 0;
        double thirdDirection = 0;

        double mainPosition = 0;
        double secondPosition = 0;
        double thirdPosition = 0;

        Block startBlock = level.getBlock(new Vector3(Math.floor(startClone.x), Math.floor(startClone.y), Math.floor(startClone.z)));

        BlockFace xFace = this.getXFace(direction);
        BlockFace yFace = this.getYFace(direction);
        BlockFace zFace = this.getZFace(direction);

        double xLength = this.getXLength(direction);
        double yLength = this.getYLength(direction);
        double zLength = this.getZLength(direction);

        double xPosition = this.getXPosition(direction, startClone, startBlock);
        double yPosition = this.getYPosition(direction, startClone, startBlock);
        double zPosition = this.getZPosition(direction, startClone, startBlock);

        if (xLength > mainDirection) {
            this.mainFace = xFace;
            mainDirection = xLength;
            mainPosition = xPosition;

            this.secondFace = yFace;
            secondDirection = yLength;
            secondPosition = yPosition;

            this.thirdFace = zFace;
            thirdDirection = zLength;
            thirdPosition = zPosition;
        }
        if (yLength > mainDirection) {
            this.mainFace = yFace;
            mainDirection = yLength;
            mainPosition = yPosition;

            this.secondFace = zFace;
            secondDirection = zLength;
            secondPosition = zPosition;

            this.thirdFace = xFace;
            thirdDirection = xLength;
            thirdPosition = xPosition;
        }
        if (zLength > mainDirection) {
            this.mainFace = zFace;
            mainDirection = zLength;
            mainPosition = zPosition;

            this.secondFace = xFace;
            secondDirection = xLength;
            secondPosition = xPosition;

            this.thirdFace = yFace;
            thirdDirection = yLength;
            thirdPosition = yPosition;
        }

        // trace line backwards to find intercept with plane perpendicular to the main axis

        double d = mainPosition / mainDirection;
        double secondd = secondPosition - secondDirection * d;
        double thirdd = thirdPosition - thirdDirection * d;

        // Guarantee that the ray will pass though the start block.
        // It is possible that it would miss due to rounding
        // This should only move the ray by 1 grid position
        this.secondError = (int) Math.floor(secondd * GRID_SIZE);
        this.secondStep = (int) Math.round(secondDirection / mainDirection * GRID_SIZE);
        this.thirdError = (int) Math.floor(thirdd * GRID_SIZE);
        this.thirdStep = (int) Math.round(thirdDirection / mainDirection * GRID_SIZE);

        // This means that when the variables are positive, it means that the coord=1 boundary has been crossed
        if (this.secondError + this.secondStep <= 0) {
            this.secondError = -this.secondStep + 1;
        }

        if (this.thirdError + this.thirdStep <= 0) {
            this.thirdError = -this.thirdStep + 1;
        }

        Vector3 lastVector3 = startBlock.getSideVec(this.mainFace.getOpposite());

        if (this.secondError < 0) {
            this.secondError += GRID_SIZE;
            lastVector3 = lastVector3.getSideVec(this.secondFace.getOpposite());
        }

        if (this.thirdError < 0) {
            this.thirdError += GRID_SIZE;
            lastVector3 = lastVector3.getSideVec(this.thirdFace.getOpposite());
        }

        this.secondError -= GRID_SIZE;
        this.thirdError -= GRID_SIZE;

        this.vector3Queue[0] = lastVector3;

        this.currentBlock = -1;

        this.scan();

        boolean startBlockFound = false;

        for (int cnt = this.currentBlock; cnt >= 0; --cnt) {
            if (this.vector3Queue[cnt].equals(startBlock)) {
                this.currentBlock = cnt;
                startBlockFound = true;
                break;
            }
        }

        if (!startBlockFound) {
            throw new IllegalStateException("Start block missed in BlockIterator");
        }

        this.maxDistanceInt = (int) Math.round(maxDistance / (Math.sqrt(mainDirection * mainDirection + secondDirection * secondDirection + thirdDirection * thirdDirection) / mainDirection));
    }

    private BlockFace getXFace(Vector3 direction) {
        return ((direction.x) > 0) ? BlockFace.EAST : BlockFace.WEST;
    }

    private BlockFace getYFace(Vector3 direction) {
        return ((direction.y) > 0) ? BlockFace.UP : BlockFace.DOWN;
    }

    private BlockFace getZFace(Vector3 direction) {
        return ((direction.z) > 0) ? BlockFace.SOUTH : BlockFace.NORTH;
    }

    private double getXLength(Vector3 direction) {
        return Math.abs(direction.x);
    }

    private double getYLength(Vector3 direction) {
        return Math.abs(direction.y);
    }

    private double getZLength(Vector3 direction) {
        return Math.abs(direction.z);
    }

    private double getPosition(double direction, double position, double blockPosition) {
        return direction > 0 ? (position - blockPosition) : (blockPosition + 1 - position);
    }

    private double getXPosition(Vector3 direction, Vector3 position, Block block) {
        return this.getPosition(direction.x, position.x, block.x);
    }

    private double getYPosition(Vector3 direction, Vector3 position, Block block) {
        return this.getPosition(direction.y, position.y, block.y);
    }

    private double getZPosition(Vector3 direction, Vector3 position, Block block) {
        return this.getPosition(direction.z, position.z, block.z);
    }

    /**
     * Returns the next Block in the trace
     *
     * @return the next Block in the trace
     */
    @Override
    public Block next() {
        this.scan();

        if (this.currentBlock <= -1) {
            throw new IndexOutOfBoundsException();
        }
        return Objects.requireNonNull(this.level.get(), "Level has been unloaded").getBlock(this.vector3Queue[this.currentBlock--]);
    }

    /**
     * Returns true if the iteration has more elements
     */
    @Override
    public boolean hasNext() {
        this.scan();
        return this.currentBlock != -1;
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException("[BlockIterator] doesn't support block removal");
    }

    private void scan() {
        if (this.currentBlock >= 0 || this.end) {
            return;
        }

        if (this.maxDistance != 0 && this.currentDistance > this.maxDistanceInt) {
            this.end = true;
            return;
        }

        ++this.currentDistance;

        this.secondError += this.secondStep;
        this.thirdError += this.thirdStep;

        if (this.secondError > 0 && this.thirdError > 0) {
            this.vector3Queue[2] = this.vector3Queue[0].getSideVec(this.mainFace);

            if ((this.secondStep * this.thirdError) < (this.thirdStep * this.secondError)) {
                this.vector3Queue[1] = this.vector3Queue[2].getSideVec(this.secondFace);
                this.vector3Queue[0] = this.vector3Queue[1].getSideVec(this.thirdFace);
            } else {
                this.vector3Queue[1] = this.vector3Queue[2].getSideVec(this.thirdFace);
                this.vector3Queue[0] = this.vector3Queue[1].getSideVec(this.secondFace);
            }

            this.thirdError -= GRID_SIZE;
            this.secondError -= GRID_SIZE;
            this.currentBlock = 2;
        } else if (this.secondError > 0) {
            this.vector3Queue[1] = this.vector3Queue[0].getSideVec(this.mainFace);
            this.vector3Queue[0] = this.vector3Queue[1].getSideVec(this.secondFace);
            this.secondError -= GRID_SIZE;
            this.currentBlock = 1;
        } else if (this.thirdError > 0) {
            this.vector3Queue[1] = this.vector3Queue[0].getSideVec(this.mainFace);
            this.vector3Queue[0] = this.vector3Queue[1].getSideVec(this.thirdFace);
            this.thirdError -= GRID_SIZE;
            this.currentBlock = 1;
        } else {
            this.vector3Queue[0] = this.vector3Queue[0].getSideVec(this.mainFace);
            this.currentBlock = 0;
        }
    }
}
