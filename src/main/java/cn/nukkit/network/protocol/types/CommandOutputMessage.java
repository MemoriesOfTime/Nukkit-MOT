package cn.nukkit.network.protocol.types;

import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Value
public class CommandOutputMessage {
    public CommandOutputMessage(@NotNull String messageId) {
        this(false, messageId, new String[]{});
    }

    public CommandOutputMessage(@NotNull String messageId, @NotNull String... parameters) {
        this(false, messageId, parameters);
    }

    public CommandOutputMessage(boolean internal, @NotNull String messageId, @NotNull String[] parameters) {
        this(internal, messageId, parameters, null);
    }

    public CommandOutputMessage(boolean internal, @NotNull String messageId, @NotNull String[] parameters,
                                @Nullable String rawMessage) {
        this.internal = internal;
        this.messageId = messageId;
        this.parameters = parameters;
        this.rawMessage = rawMessage;
    }

    boolean internal;
    @NotNull
    String messageId;
    @NotNull
    String[] parameters;
    /**
     * 客户端可直接解析的 rawtext JSON；为空时回退到 Message ID + Parameters。
     * <p>
     * Rawtext JSON resolved by the client; when absent Message ID + Parameters are used.
     *
     * @since v2259 1.26.70
     */
    @Nullable
    String rawMessage;
}
