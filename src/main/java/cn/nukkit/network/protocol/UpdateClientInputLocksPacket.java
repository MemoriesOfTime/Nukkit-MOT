package cn.nukkit.network.protocol;

import cn.nukkit.GameVersion;
import cn.nukkit.Server;
import cn.nukkit.math.Vector3f;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

@Data
@EqualsAndHashCode(doNotUseGetters = true, callSuper = false)
@ToString(doNotUseGetters = true)
public class UpdateClientInputLocksPacket extends DataPacket {

    @Deprecated
    public static final int FLAG_CAMERA = 1 << 1;
    @Deprecated
    public static final int FLAG_MOVEMENT = 1 << 2;

    public Set<InputLockType> inputLockType = new HashSet<>();
    public Vector3f serverPosition;

    @Override
    public byte pid() {
        return ProtocolInfo.UPDATE_CLIENT_INPUT_LOCKS;
    }

    @Override
    public void decode() {
        this.inputLockType = InputLockType.fromBitSet((int) this.getUnsignedVarInt());
        this.serverPosition = this.getVector3f();
    }

    @Override
    public void encode() {
        this.reset();
        this.putUnsignedVarInt(InputLockType.toBitSet(inputLockType));
        this.putVector3f(this.serverPosition);
    }

    @Getter
    public enum InputLockType {

        RESET(0, GameVersion.V1_19_80),
        CAMERA(2, GameVersion.V1_19_80),
        MOVEMENT(4, GameVersion.V1_19_80),
        LATERAL_MOVEMENT(16, GameVersion.V1_21_50),
        SNEAK(32, GameVersion.V1_21_50),
        JUMP(64, GameVersion.V1_21_50),
        MOUNT(128, GameVersion.V1_21_50),
        DISMOUNT(256, GameVersion.V1_21_50),
        MOVE_FORWARD(512, GameVersion.V1_21_50),
        MOVE_BACKWARD(1024, GameVersion.V1_21_50),
        MOVE_LEFT(2048, GameVersion.V1_21_50),
        MOVE_RIGHT(4096, GameVersion.V1_21_50);

        private final int data;

        private final GameVersion minimumProtocol;

        InputLockType(int data, GameVersion minimumProtocol) {
            this.data = data;
            this.minimumProtocol = minimumProtocol;
        }

        public static InputLockType fromId(int id) {
            for (InputLockType flag : values()) {
                if (flag.data == id) return flag;
            }
            Server.getInstance().getLogger().error("Error in parsing id for inputLockType, caused by: unknown flag id: " + id);
            return InputLockType.RESET;
        }

        public static Set<InputLockType> fromBitSet(int bitset) {
            EnumSet<InputLockType> set = EnumSet.noneOf(InputLockType.class);
            for (InputLockType flag : values()) {
                if ((bitset & flag.data) != 0) {
                    set.add(flag);
                }
            }
            return set;
        }

        public static int toBitSet(Set<InputLockType> flags) {
            int bitset = 0;
            for (InputLockType flag : flags) {
                bitset |= flag.data;
            }
            return bitset;
        }
    }
}