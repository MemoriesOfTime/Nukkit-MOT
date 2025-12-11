package cn.nukkit.network.protocol;

import cn.nukkit.network.protocol.types.CommandOriginData;
import cn.nukkit.network.protocol.types.CommandOutputMessage;
import cn.nukkit.network.protocol.types.CommandOutputType;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.ToString;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

@ToString
public class CommandOutputPacket extends DataPacket {
    public static final byte NETWORK_ID = ProtocolInfo.COMMAND_OUTPUT_PACKET;

    public final List<CommandOutputMessage> messages = new ObjectArrayList<>();
    public CommandOriginData commandOriginData;
    public CommandOutputType type;
    public int successCount;
    public String data;

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    @Override
    public void decode() {
        //non
    }

    @Override
    public void encode() {
        this.reset();
        if (this.protocol >= ProtocolInfo.v1_21_130_28) {
            this.putString("player");
        } else {
            putUnsignedVarInt(this.commandOriginData.type.ordinal());
        }
        putUUID(this.commandOriginData.uuid);
        putString(this.commandOriginData.requestId);

        if (this.protocol >= ProtocolInfo.v1_21_130_28) {
            this.putLLong(this.commandOriginData.getVarLong().orElse(-1));// unknown
            this.putString(this.type.getNetworkname());
        } else {
            if (this.commandOriginData.type == CommandOriginData.Origin.DEV_CONSOLE || this.commandOriginData.type == CommandOriginData.Origin.TEST) {
                putVarLong(this.commandOriginData.getVarLong().orElse(-1));
            }

            putByte((byte) this.type.ordinal());
            putUnsignedVarInt(this.successCount);
        }
        if (this.protocol >= ProtocolInfo.v1_21_130_28) {
            this.putInt(this.successCount);
        }
        this.putUnsignedVarInt(messages.size());
        for (var msg : messages) {
            if (this.protocol >= ProtocolInfo.v1_21_130_28) {
                this.putString(msg.getMessageId());
                this.putBoolean(msg.isInternal());
            } else {
                this.putBoolean(msg.isInternal());
                this.putString(msg.getMessageId());
            }
            this.putUnsignedVarInt(msg.getParameters().length);
            for (var param : msg.getParameters()) {
                this.putString(param);
            }
        }
        if (this.protocol >= ProtocolInfo.v1_21_130_28) {
            this.putOptional(Objects::nonNull, this.data, this::putString);
        } else {
            if (this.type == CommandOutputType.DATA_SET) {
                putString(this.data);
            }
        }
    }
}
