package cn.nukkit.lang;

import cn.nukkit.network.protocol.types.CommandOutputMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link cn.nukkit.network.protocol.CommandOutputPacket CommandOutputPacket} 负载消息的容器，支持同时发送多条消息
 * @author PowerNukkitX Project Team
 */
public class CommandOutputContainer implements Cloneable {
    public static final String[] EMPTY_STRING = new String[]{};
    private final List<CommandOutputMessage> messages;
    private int successCount;

    public CommandOutputContainer() {
        this.messages = new ArrayList<>();
        this.successCount = 0;
    }

    public CommandOutputContainer(String messageId, String[] parameters, int successCount) {
        this(List.of(new CommandOutputMessage(false, messageId, parameters)), successCount);
    }

    public CommandOutputContainer(List<CommandOutputMessage> messages, int successCount) {
        this.messages = messages;
        this.successCount = successCount;
    }

    public int getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(int successCount) {
        this.successCount = successCount;
    }

    public void incrementSuccessCount() {
        this.successCount++;
    }

    public List<CommandOutputMessage> getMessages() {
        return messages;
    }

    @Override
    protected CommandOutputContainer clone() throws CloneNotSupportedException {
        return (CommandOutputContainer) super.clone();
    }
}