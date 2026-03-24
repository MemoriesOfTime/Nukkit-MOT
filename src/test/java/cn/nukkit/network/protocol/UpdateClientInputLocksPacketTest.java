package cn.nukkit.network.protocol;

import cn.nukkit.GameVersion;
import cn.nukkit.math.Vector3f;
import cn.nukkit.utils.BinaryStream;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class UpdateClientInputLocksPacketTest {

    @Test
    public void testDecodeZeroBitSetAsReset() {
        BinaryStream stream = new BinaryStream();
        stream.putUnsignedVarInt(0);
        stream.putVector3f(new Vector3f(1.25f, 2.5f, 3.75f));

        UpdateClientInputLocksPacket packet = new UpdateClientInputLocksPacket();
        packet.protocol = GameVersion.V1_21_50_26.getProtocol();
        packet.setBuffer(stream.getBuffer());
        packet.decode();

        assertEquals(EnumSet.of(UpdateClientInputLocksPacket.InputLockType.RESET), packet.getInputLockType());
        assertEquals(1.25f, packet.getServerPosition().x);
        assertEquals(2.5f, packet.getServerPosition().y);
        assertEquals(3.75f, packet.getServerPosition().z);
    }

    @Test
    public void testEncodeResetAsZeroBitSet() {
        UpdateClientInputLocksPacket packet = new UpdateClientInputLocksPacket();
        packet.protocol = GameVersion.V1_21_50_26.getProtocol();
        packet.setInputLockType(EnumSet.of(UpdateClientInputLocksPacket.InputLockType.RESET));
        packet.setServerPosition(new Vector3f(4.5f, 5.5f, 6.5f));
        packet.encode();

        packet.setOffset(0);
        packet.getUnsignedVarInt();

        assertEquals(0, packet.getUnsignedVarInt());

        Vector3f position = packet.getVector3f();
        assertEquals(4.5f, position.x);
        assertEquals(5.5f, position.y);
        assertEquals(6.5f, position.z);
    }

    @Test
    public void testPreviewProtocolKeepsNewInputLocks() {
        int bitSet = UpdateClientInputLocksPacket.InputLockType.toBitSet(EnumSet.of(
                UpdateClientInputLocksPacket.InputLockType.CAMERA,
                UpdateClientInputLocksPacket.InputLockType.LATERAL_MOVEMENT,
                UpdateClientInputLocksPacket.InputLockType.MOVE_FORWARD
        ));

        assertEquals(
                bitSet,
                UpdateClientInputLocksPacket.InputLockType.filterBitSet(GameVersion.V1_21_50_26.getProtocol(), bitSet)
        );
        assertEquals(
                UpdateClientInputLocksPacket.InputLockType.toBitSet(EnumSet.of(UpdateClientInputLocksPacket.InputLockType.CAMERA)),
                UpdateClientInputLocksPacket.InputLockType.filterBitSet(GameVersion.V1_21_40.getProtocol(), bitSet)
        );
    }
}
