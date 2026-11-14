package cn.nukkit.network.protocol.types;

import cn.nukkit.utils.BinaryStream;
import lombok.ToString;

/**
 * 托管聊天中由服务端自由添加的名字/消息装饰，每个字段独立可选。
 * <p>
 * Server-side name/message decorations for managed chat; every field is independently optional.
 *
 * @since v2259 1.26.70
 */
@ToString
public class ChatFormatting {

    public String namePrefix;
    public String namePostfix;
    public String messagePrefix;
    public String messagePostfix;

    public static void put(BinaryStream stream, ChatFormatting formatting) {
        stream.putOptionalNull(formatting.namePrefix, BinaryStream::putString);
        stream.putOptionalNull(formatting.namePostfix, BinaryStream::putString);
        stream.putOptionalNull(formatting.messagePrefix, BinaryStream::putString);
        stream.putOptionalNull(formatting.messagePostfix, BinaryStream::putString);
    }

    public static ChatFormatting get(BinaryStream stream) {
        ChatFormatting formatting = new ChatFormatting();
        formatting.namePrefix = stream.getOptional(null, s -> s.getString(64));
        formatting.namePostfix = stream.getOptional(null, s -> s.getString(64));
        formatting.messagePrefix = stream.getOptional(null, s -> s.getString(64));
        formatting.messagePostfix = stream.getOptional(null, s -> s.getString(64));
        return formatting;
    }
}
