package cn.nukkit.network.protocol;

import cn.nukkit.GameVersion;
import cn.nukkit.math.Vector3f;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.jetbrains.annotations.ApiStatus;

import java.util.EnumSet;
import java.util.Set;

@Data
@EqualsAndHashCode(doNotUseGetters = true, callSuper = false)
@ToString(doNotUseGetters = true)
public class UpdateClientInputLocksPacket extends DataPacket {

    /**
     * @deprecated Use {@link InputLockType#CAMERA} through {@link #inputLockType} instead.
     * Scheduled for removal in 1.27.0.
     */
    @Deprecated(since = "1.26.0", forRemoval = true)
    @ApiStatus.ScheduledForRemoval(inVersion = "1.27.0")
    public static final int FLAG_CAMERA = 1 << 1;

    /**
     * @deprecated Use {@link InputLockType#MOVEMENT} through {@link #inputLockType} instead.
     * Scheduled for removal in 1.27.0.
     */
    @Deprecated(since = "1.26.0", forRemoval = true)
    @ApiStatus.ScheduledForRemoval(inVersion = "1.27.0")
    public static final int FLAG_MOVEMENT = 1 << 2;

    /**
     * @deprecated Use {@link #inputLockType} instead.
     * Scheduled for removal in 1.27.0.
     */
    @Deprecated(since = "1.26.0", forRemoval = true)
    @ApiStatus.ScheduledForRemoval(inVersion = "1.27.0")
    public int lockComponentData;

    public Set<InputLockType> inputLockType = EnumSet.noneOf(InputLockType.class);
    public Vector3f serverPosition;

    @Override
    public byte pid() {
        return ProtocolInfo.UPDATE_CLIENT_INPUT_LOCKS;
    }

    @Override
    public void decode() {
        this.lockComponentData = (int) this.getUnsignedVarInt();
        this.inputLockType = InputLockType.fromBitSet(this.lockComponentData);
        if (this.protocol < ProtocolInfo.v1_26_10) {
            this.serverPosition = this.getVector3f();
        }
    }

    @Override
    public void encode() {
        this.reset();
        this.putUnsignedVarInt(InputLockType.filterBitSet(this.protocol, this.resolveLockComponentData()));
        if (this.protocol < ProtocolInfo.v1_26_10) {
            this.putVector3f(this.serverPosition);
        }
    }

    private int resolveLockComponentData() {
        if (this.inputLockType == null || this.inputLockType.isEmpty()) {
            return this.lockComponentData;
        }
        return InputLockType.toBitSet(this.inputLockType);
    }

    @Getter
    public enum InputLockType {

        RESET(0, GameVersion.V1_19_50),
        CAMERA(2, GameVersion.V1_19_50),
        MOVEMENT(4, GameVersion.V1_19_50),
        LATERAL_MOVEMENT(16, GameVersion.V1_21_50_26),
        SNEAK(32, GameVersion.V1_21_50_26),
        JUMP(64, GameVersion.V1_21_50_26),
        MOUNT(128, GameVersion.V1_21_50_26),
        DISMOUNT(256, GameVersion.V1_21_50_26),
        MOVE_FORWARD(512, GameVersion.V1_21_50_26),
        MOVE_BACKWARD(1024, GameVersion.V1_21_50_26),
        MOVE_LEFT(2048, GameVersion.V1_21_50_26),
        MOVE_RIGHT(4096, GameVersion.V1_21_50_26);

        private final int data;

        private final GameVersion minimumProtocol;

        InputLockType(int data, GameVersion minimumProtocol) {
            this.data = data;
            this.minimumProtocol = minimumProtocol;
        }

        public static Set<InputLockType> fromBitSet(int bitset) {
            if (bitset == RESET.data) {
                return EnumSet.of(RESET);
            }

            EnumSet<InputLockType> set = EnumSet.noneOf(InputLockType.class);
            for (InputLockType flag : values()) {
                if (flag == RESET) {
                    continue;
                }
                if ((bitset & flag.data) != 0) {
                    set.add(flag);
                }
            }
            return set;
        }

        public static Set<InputLockType> copyOf(Set<InputLockType> flags) {
            if (flags == null || flags.isEmpty()) {
                return EnumSet.noneOf(InputLockType.class);
            }
            return EnumSet.copyOf(flags);
        }

        public static int toBitSet(Set<InputLockType> flags) {
            int bitset = 0;
            for (InputLockType flag : copyOf(flags)) {
                bitset |= flag.data;
            }
            return bitset;
        }

        public static int filterBitSet(int protocol, int bitset) {
            int filteredBitSet = bitset;
            for (InputLockType flag : values()) {
                if (protocol < flag.getMinimumProtocol().getProtocol()) {
                    filteredBitSet &= ~flag.data;
                }
            }
            return filteredBitSet;
        }
    }
}
