package cn.nukkit.network.protocol;

import cn.nukkit.camera.instruction.CameraInstruction;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import lombok.Getter;
import lombok.Setter;

import java.io.IOException;
import java.nio.ByteOrder;

@Getter
@Setter
public class CameraInstructionPacket extends DataPacket {
    private CompoundTag data;

    @Override
    @Deprecated
    public byte pid() {
        return ProtocolInfo.__INTERNAL__CAMERA_INSTRUCTION_PACKET;
    }

    @Override
    public int packetId() {
        return ProtocolInfo.CAMERA_INSTRUCTION_PACKET;
    }

    @Override
    public void decode() {
        this.data = this.getTag();
    }

    @Override
    public void encode() {
        this.reset();
        try {
            this.put(NBTIO.write(this.data, ByteOrder.LITTLE_ENDIAN, true));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void setInstruction(CameraInstruction instruction) {
        var tag = instruction.serialize();
        data = new CompoundTag().put(tag.getName(), tag);
    }
}
