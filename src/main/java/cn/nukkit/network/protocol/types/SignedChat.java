package cn.nukkit.network.protocol.types;

import cn.nukkit.utils.BinaryStream;
import lombok.ToString;

/**
 * 托管聊天（Mojang Managed Chat）的签名消息体，服务端必须原样转发。
 * <p>
 * Signed message block for Mojang Managed Chat; servers must relay it unmodified.
 *
 * @since v2259 1.26.70
 */
@ToString
public class SignedChat {

    public String keyId = "";
    public String senderId = "";
    public String messageId = "";
    public String scanText = "";
    public String redactedText = "";
    public String authorName = "";
    public String signature = "";

    public static void put(BinaryStream stream, SignedChat chat) {
        stream.putString(chat.keyId);
        stream.putString(chat.senderId);
        stream.putString(chat.messageId);
        stream.putString(chat.scanText);
        stream.putString(chat.redactedText);
        stream.putString(chat.authorName);
        stream.putString(chat.signature);
    }

    public static SignedChat get(BinaryStream stream) {
        SignedChat chat = new SignedChat();
        chat.keyId = stream.getString(128);
        chat.senderId = stream.getString(64);
        chat.messageId = stream.getString(64);
        chat.scanText = stream.getString(512);
        chat.redactedText = stream.getString(512);
        chat.authorName = stream.getString(192);
        chat.signature = stream.getString(256);
        return chat;
    }
}
