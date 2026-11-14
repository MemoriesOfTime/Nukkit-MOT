package cn.nukkit.network.protocol;

import cn.nukkit.network.protocol.types.ChatFormatting;
import cn.nukkit.network.protocol.types.SignedChat;
import cn.nukkit.utils.BinaryStream;
import lombok.ToString;

/**
 * 托管聊天（Mojang Managed Chat）消息，双向使用：客户端上报签名消息，服务端原样转发。
 * <p>
 * Managed-chat message, used in both directions: clients submit signed chat and servers relay it unmodified.
 *
 * @since v2235 1.26.70.20
 */
@ToString
public class ChatPacket extends DataPacket {

    public static final int NETWORK_ID = ProtocolInfo.CHAT_PACKET;

    /**
     * 签名消息体；空字符串表示未签名消息，接收端按策略决定是否展示。
     * <p>
     * Signed message block; empty strings mean an unsigned message that receivers may show or drop.
     */
    public SignedChat signedChat = new SignedChat();
    /**
     * 非默认聊天室 ID；缺省表示默认聊天。
     * <p>
     * Chat room id; absent means the default chat.
     */
    public String roomId;
    /**
     * 服务端可自由设置的装饰文本。
     * <p>
     * Free-form server decoration.
     */
    public ChatFormatting formatting;

    @Override
    @Deprecated
    public byte pid() {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public int packetId() {
        return NETWORK_ID;
    }

    @Override
    public void decode() {
        if (protocol < ProtocolInfo.v1_26_70) {
            throw new UnsupportedOperationException("ChatPacket requires protocol 2259 or newer");
        }
        this.signedChat = SignedChat.get(this);
        this.roomId = this.getOptional(null, s -> s.getString(128));
        this.formatting = this.getOptional(null, ChatFormatting::get);
    }

    @Override
    public void encode() {
        this.reset();
        SignedChat.put(this, this.signedChat);
        this.putOptionalNull(this.roomId, BinaryStream::putString);
        this.putOptionalNull(this.formatting, ChatFormatting::put);
    }
}
