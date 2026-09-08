package cn.nukkit.network.rcon;

import cn.nukkit.Server;
import cn.nukkit.command.RemoteConsoleCommandSender;
import cn.nukkit.event.server.RemoteServerCommandEvent;
import cn.nukkit.utils.TextFormat;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Implementation of Source RCON protocol.
 * https://developer.valvesoftware.com/wiki/Source_RCON_Protocol
 * 
 * Wrapper for RCONServer. Handles data.
 *
 * @author Tee7even
 */
public class RCON {

    /**
     * How long a command with no immediate output may keep the connection waiting.
     *
     * <p>Plugin commands that talk to a database answer a few ticks after dispatch returns.
     * Responding straight away hands the operator an empty string, which reads as a failure
     * even when the command did its job.
     */
    private static final int DEFERRED_ANSWER_TICKS = 40;

    private final Server server;
    private final RCONServer serverThread;
    private final List<DeferredAnswer> deferred = new ArrayList<>();
    private long tick;

    public RCON(Server server, String password, String address, int port) {
        if (password.isEmpty()) {
            throw new IllegalArgumentException("nukkit.server.rcon.emptyPasswordError");
        }

        this.server = server;

        try {
            this.serverThread = new RCONServer(address, port, password);
            this.serverThread.start();
        } catch (IOException e) {
            throw new IllegalArgumentException("nukkit.server.rcon.startupError", e);
        }

        this.server.getLogger().info(this.server.getLanguage().translateString("nukkit.server.rcon.running", new String[]{address, String.valueOf(port)}));
    }

    public void check() {
        if (this.serverThread == null) {
            return;
        } else if (!this.serverThread.isAlive()) {
            return;
        }

        this.tick++;
        this.flushDeferred();

        RCONCommand command;
        while ((command = serverThread.receive()) != null) {
            RemoteConsoleCommandSender sender = new RemoteConsoleCommandSender();
            RemoteServerCommandEvent event = new RemoteServerCommandEvent(sender, command.getCommand());
            this.server.getPluginManager().callEvent(event);

            if (!event.isCancelled()) {
                this.server.dispatchCommand(sender, command.getCommand());
            }

            String answer = TextFormat.clean(sender.getMessages());
            if (!answer.isEmpty()) {
                this.serverThread.respond(command.getSender(), command.getId(), answer);
                continue;
            }

            this.deferred.add(new DeferredAnswer(command.getSender(), command.getId(), sender, this.tick + DEFERRED_ANSWER_TICKS));
        }
    }

    /**
     * Answers the commands that had nothing to say when they returned.
     *
     * <p>A late answer is only sent once it stopped growing: multi line replies arrive
     * over several ticks, and responding to the first line would cut the rest off.
     */
    private void flushDeferred() {
        if (this.deferred.isEmpty()) {
            return;
        }

        Iterator<DeferredAnswer> answers = this.deferred.iterator();
        while (answers.hasNext()) {
            DeferredAnswer answer = answers.next();
            String text = TextFormat.clean(answer.sender.getMessages());
            boolean settled = !text.isEmpty() && text.length() == answer.length;
            if (!settled && this.tick < answer.deadline) {
                answer.length = text.length();
                continue;
            }

            answers.remove();
            this.serverThread.respond(answer.channel, answer.id, text);
        }
    }

    public void close() {
        try {
            synchronized (serverThread) {
                serverThread.close();
                serverThread.wait(5000);
            }
        } catch (InterruptedException ignored) {}
    }

    /** A command that returned without output and may still be answered. */
    private static final class DeferredAnswer {

        private final SocketChannel channel;
        private final int id;
        private final RemoteConsoleCommandSender sender;
        private final long deadline;
        private int length;

        private DeferredAnswer(SocketChannel channel, int id, RemoteConsoleCommandSender sender, long deadline) {
            this.channel = channel;
            this.id = id;
            this.sender = sender;
            this.deadline = deadline;
        }
    }
}
