package cn.nukkit.network.process;

import cn.nukkit.GameVersion;
import cn.nukkit.network.protocol.PlayerActionPacket;
import cn.nukkit.network.protocol.PlayerAuthInputPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.protocol.types.AuthInputAction;
import cn.nukkit.network.protocol.types.AuthInteractionModel;
import cn.nukkit.network.protocol.types.ClientPlayMode;
import cn.nukkit.network.protocol.types.InputMode;
import cn.nukkit.utils.BinaryStream;

/**
 * Tiny in-process communication check used by the local MOT boot test.
 * It encodes/decodes the START_USING_ITEM AuthInput bit the same way a NetEase 860
 * client (and ViaBedrock experimental) would, then asserts MOT now maps that bit
 * onto the setUsingItem receive helper instead of ignoring it.
 */
public final class UsingItemReceiveProbe {

    private UsingItemReceiveProbe() {
    }

    public static String run() {
        PlayerAuthInputPacket packet = decodeNetEase860StartUsingItem();
        boolean decodedStart = UsingItemReceive.authInputStartsUsingItem(packet);
        boolean wouldStart = UsingItemReceive.shouldStartUsingFromAuthInput(true, false, false, true, decodedStart);
        boolean keepSprint = UsingItemReceive.shouldKeepUsingDespiteStartSprinting(true, decodedStart);
        boolean startActionKept = !UsingItemReceive.shouldClearUsingOnUnhandledPlayerAction(PlayerActionPacket.ACTION_START_USING_ITEM);
        boolean useOnKept = !UsingItemReceive.shouldClearUsingOnUnhandledPlayerAction(PlayerActionPacket.ACTION_START_ITEM_USE_ON);
        if (!decodedStart || !wouldStart || !keepSprint || !startActionKept || !useOnKept) {
            throw new IllegalStateException("UsingItem receive probe failed: decodedStart=" + decodedStart
                    + " wouldStart=" + wouldStart
                    + " keepSprint=" + keepSprint
                    + " startActionKept=" + startActionKept
                    + " useOnKept=" + useOnKept
                    + " flags=" + packet.getInputData());
        }
        return "ok protocol=" + packet.protocol
                + " gameVersion=" + packet.gameVersion
                + " START_USING_ITEM_ordinal=" + AuthInputAction.START_USING_ITEM.ordinal()
                + " ACTION_START_USING_ITEM=" + PlayerActionPacket.ACTION_START_USING_ITEM
                + " flags=" + packet.getInputData();
    }

    /**
     * Encode a NetEase 860 PlayerAuthInput payload with START_USING_ITEM on the
     * extra-flag-shifted wire bit (ordinal 53 + 1 extra flag for protocol 860).
     * MOT used to decode that bit and then ignore it.
     */
    static PlayerAuthInputPacket decodeNetEase860StartUsingItem() {
        int extraFlags = 2; // protocol 860 is >= 819, NetEase inserts two hidden flags
        int startUsingOrdinal = AuthInputAction.START_USING_ITEM.ordinal();
        long wireBit = 1L << (startUsingOrdinal + extraFlags);
        BinaryStream stream = new BinaryStream();
        stream.putLFloat(10.5f);
        stream.putLFloat(20.5f);
        stream.putVector3f(1.25f, 64.0f, -3.5f);
        stream.putLFloat(0.25f);
        stream.putLFloat(-0.5f);
        stream.putLFloat(30.5f);
        stream.putUnsignedVarLong(wireBit);
        stream.putUnsignedVarInt(InputMode.TOUCH.ordinal());
        stream.putUnsignedVarInt(ClientPlayMode.NORMAL.ordinal());
        stream.putUnsignedVarInt(AuthInteractionModel.CROSSHAIR.ordinal());
        stream.putVector2f(5.0f, 6.0f);
        stream.putUnsignedVarLong(123L);
        stream.putVector3f(0.1f, 0.2f, 0.3f);
        stream.putBoolean(false); // cameraDeparted
        stream.putVector2f(0.6f, -0.4f);
        stream.putVector3f(0.0f, 1.0f, 0.0f);
        stream.putVector2f(-0.25f, 0.75f);

        PlayerAuthInputPacket packet = new PlayerAuthInputPacket();
        packet.protocol = ProtocolInfo.v1_21_124;
        packet.gameVersion = GameVersion.V1_21_124_NETEASE;
        packet.setBuffer(stream.getBuffer());
        packet.decode();
        return packet;
    }

    public static void main(String[] args) {
        System.out.println("[UsingItemProbe] " + run());
    }
}
