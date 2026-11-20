package cn.nukkit.network.protocol.types;

import cn.nukkit.math.BlockVector3;
import cn.nukkit.math.Vector3f;
import cn.nukkit.utils.BinaryStream;
import lombok.ToString;

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

    public static void put(BinaryStream stream, PassengerOfBlockArguments args) {
        stream.putSignedBlockPosition(args.blockPos);
        stream.putVector3f(args.offset);
        stream.putLFloat(args.rotation);
        stream.putLFloat(args.rotationLimit);
        stream.putByte((byte) args.emoteType.ordinal());
    }

    public static PassengerOfBlockArguments get(BinaryStream stream) {
        PassengerOfBlockArguments args = new PassengerOfBlockArguments();
        args.blockPos = stream.getSignedBlockPosition();
        args.offset = stream.getVector3f();
        args.rotation = stream.getLFloat();
        args.rotationLimit = stream.getLFloat();
        args.emoteType = EmoteType.byOrdinal(stream.getByte() & 0xff);
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
