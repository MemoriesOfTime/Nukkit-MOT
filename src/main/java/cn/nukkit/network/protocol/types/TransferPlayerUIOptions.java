package cn.nukkit.network.protocol.types;

import cn.nukkit.utils.BinaryStream;
import lombok.ToString;

/**
 * Transfer 的服务端转服界面选项；整体与每个字段均可缺省。
 * <p>
 * Server transfer UI options for Transfer; both the struct and each field are optional.
 *
 * @since v2259 1.26.70
 */
@ToString
public class TransferPlayerUIOptions {

    public String titleText;
    public String bodyText;
    public Boolean titleVisible;
    public Boolean bodyVisible;
    public String cancelButtonText;
    public Boolean cancelButtonVisible;
    public Float cancelButtonDelaySeconds;

    public static void put(BinaryStream stream, TransferPlayerUIOptions options) {
        stream.putOptionalNull(options.titleText, BinaryStream::putString);
        stream.putOptionalNull(options.bodyText, BinaryStream::putString);
        stream.putOptionalNull(options.titleVisible, BinaryStream::putBoolean);
        stream.putOptionalNull(options.bodyVisible, BinaryStream::putBoolean);
        stream.putOptionalNull(options.cancelButtonText, BinaryStream::putString);
        stream.putOptionalNull(options.cancelButtonVisible, BinaryStream::putBoolean);
        stream.putOptionalNull(options.cancelButtonDelaySeconds, BinaryStream::putLFloat);
    }

    public static TransferPlayerUIOptions get(BinaryStream stream) {
        TransferPlayerUIOptions options = new TransferPlayerUIOptions();
        options.titleText = stream.getOptional(null, BinaryStream::getString);
        options.bodyText = stream.getOptional(null, BinaryStream::getString);
        options.titleVisible = stream.getOptional(null, s -> s.getBoolean());
        options.bodyVisible = stream.getOptional(null, s -> s.getBoolean());
        options.cancelButtonText = stream.getOptional(null, BinaryStream::getString);
        options.cancelButtonVisible = stream.getOptional(null, s -> s.getBoolean());
        options.cancelButtonDelaySeconds = stream.getOptional(null, BinaryStream::getLFloat);
        return options;
    }
}
