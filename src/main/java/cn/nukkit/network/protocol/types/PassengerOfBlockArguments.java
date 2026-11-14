package cn.nukkit.network.protocol.types;

import cn.nukkit.math.BlockVector3;
import cn.nukkit.math.Vector3f;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.utils.BinaryStream;
import lombok.ToString;

import java.util.UUID;

/**
 * @since v2225
 */
@ToString
public class PassengerOfBlockArguments {

    public BlockVector3 blockPos;
    public Vector3f offset;
    public float rotation;
    public float rotationLimit;
    public EmoteType emoteType = EmoteType.STANDING;
    /**
     * 乘客所依附的方块类型标识；v2259 起必填。
     * <p>
     * Block type identifier the passenger is attached to; required since v2259.
     *
     * @since v2259 1.26.70
     */
    public long blockType;
    /**
     * 提供该方块的资源包 UUID；v2259 起必填。
     * <p>
     * Content pack UUID providing the block; required since v2259.
     *
     * @since v2259 1.26.70
     */
    public UUID packUuid;

    /**
     * @deprecated 使用 {@link #put(BinaryStream, PassengerOfBlockArguments, int)} 传入实际协议版本
     * <p>
     * Use {@link #put(BinaryStream, PassengerOfBlockArguments, int)} with the actual protocol version
     */
    @Deprecated
    public static void put(BinaryStream stream, PassengerOfBlockArguments args) {
        put(stream, args, ProtocolInfo.v1_26_60);
    }

    public static void put(BinaryStream stream, PassengerOfBlockArguments args, int protocol) {
        stream.putSignedBlockPosition(args.blockPos);
        stream.putVector3f(args.offset);
        stream.putLFloat(args.rotation);
        stream.putLFloat(args.rotationLimit);
        stream.putByte((byte) args.emoteType.ordinal());
        if (protocol >= ProtocolInfo.v1_26_70) {
            stream.putLLong(args.blockType);
            stream.putUUID(args.packUuid != null ? args.packUuid : new UUID(0, 0));
        }
    }

    /**
     * @deprecated 使用 {@link #get(BinaryStream, int)} 传入实际协议版本
     * <p>
     * Use {@link #get(BinaryStream, int)} with the actual protocol version
     */
    @Deprecated
    public static PassengerOfBlockArguments get(BinaryStream stream) {
        return get(stream, ProtocolInfo.v1_26_60);
    }

    public static PassengerOfBlockArguments get(BinaryStream stream, int protocol) {
        PassengerOfBlockArguments args = new PassengerOfBlockArguments();
        args.blockPos = stream.getSignedBlockPosition();
        args.offset = stream.getVector3f();
        args.rotation = stream.getLFloat();
        args.rotationLimit = stream.getLFloat();
        args.emoteType = EmoteType.byOrdinal(stream.getByte() & 0xff);
        if (protocol >= ProtocolInfo.v1_26_70) {
            args.blockType = stream.getLLong();
            args.packUuid = stream.getUUID();
        }
        return args;
    }

    public enum EmoteType {
        STANDING,
        RIDING,
        LAYING;

        private static final EmoteType[] VALUES = values();

        public static EmoteType byOrdinal(int ordinal) {
            return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : STANDING;
        }
    }
}
