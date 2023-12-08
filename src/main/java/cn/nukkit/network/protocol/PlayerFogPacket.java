package cn.nukkit.network.protocol;

import cn.nukkit.utils.Identifier;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@ToString
public class PlayerFogPacket extends DataPacket {

    /**
     * Fog stack containing fog effects from the /fog command
     */
    @Getter
    @Setter
    private List<Fog> fogStack = new ArrayList<>();

    @Override
    public byte pid() {
        return ProtocolInfo.PLAYER_FOG_PACKET;
    }

    @Override
    public void decode() {
        //unused
    }

    @Override
    public void encode() {
        this.reset();
        this.putArray(fogStack, fog -> this.putString(fog.identifier().toString()));
    }

    /**
     *
     */
    public static final class Fog {
        private final Identifier identifier;
        private final String userProvidedId;

        /**
         * @param identifier     这个迷雾的命名空间id
         * @param userProvidedId 用户指定的特征id
         */
        public Fog(Identifier identifier, String userProvidedId) {
            this.identifier = identifier;
            this.userProvidedId = userProvidedId;
        }

        public Identifier identifier() {
            return identifier;
        }

        public String userProvidedId() {
            return userProvidedId;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            Fog that = (Fog) obj;
            return Objects.equals(this.identifier, that.identifier) &&
                    Objects.equals(this.userProvidedId, that.userProvidedId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(identifier, userProvidedId);
        }

        @Override
        public String toString() {
            return "Fog[" +
                    "identifier=" + identifier + ", " +
                    "userProvidedId=" + userProvidedId + ']';
        }

    }
}
