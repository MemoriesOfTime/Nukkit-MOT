package cn.nukkit.network.protocol;

import cn.nukkit.network.protocol.types.PlayerAbility;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * @since 527 1.19.0
 */
@ToString
@Getter
@Setter
public class RequestAbilityPacket extends DataPacket {
    protected static final PlayerAbility[] ABILITIES = PlayerAbility.values();
    protected static final AbilityType[] ABILITY_TYPES = AbilityType.values();

    public PlayerAbility ability;
    public AbilityType type;
    public boolean boolValue;
    public float floatValue;

    @Override
    public void decode() {
        this.setAbility(ABILITIES[this.getVarInt()]);
        this.setType(ABILITY_TYPES[this.getByte()]);
        this.setBoolValue(this.getBoolean());
        this.setFloatValue(this.getLFloat());
    }

    @Override
    public void encode() {
        throw new UnsupportedOperationException();
    }

    @Override
    public byte pid() {
        return ProtocolInfo.REQUEST_ABILITY_PACKET;
    }

    public enum AbilityType {
        NONE,
        BOOLEAN,
        FLOAT
    }
}