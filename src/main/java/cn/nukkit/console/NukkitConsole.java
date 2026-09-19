package cn.nukkit.console;

import cn.nukkit.Nukkit;
import cn.nukkit.Server;
import cn.nukkit.event.server.ServerCommandEvent;
import cn.nukkit.plugin.InternalPlugin;
import cn.nukkit.scheduler.ServerScheduler;
import lombok.RequiredArgsConstructor;
import net.minecrell.terminalconsole.SimpleTerminalConsole;
import net.minecrell.terminalconsole.TerminalConsoleAppender;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 服务器控制台输入循环：经 jline 读取命令并通过调度器投递。
 * <p>
 * Console input loop for the server: reads commands via jline and dispatches them through the scheduler.
 * <p>
 * Adapted from terminalconsoleappender (<a href="https://github.com/Minecrell/terminalconsoleappender">terminalconsoleappender</a>):
 * the read loop replicates SimpleTerminalConsole with one fix — EOF on a non-TTY stdin (pipes or
 * redirection) is permanent, so the loop stops instead of re-printing the prompt forever.
 */
@RequiredArgsConstructor
public class NukkitConsole extends SimpleTerminalConsole {

    private static final Logger LOGGER = LogManager.getLogger("TerminalConsole");

    private final BlockingQueue<String> consoleQueue = new LinkedBlockingQueue<>();
    private final AtomicBoolean executingCommands = new AtomicBoolean(false);

    @Override
    protected boolean isRunning() {
        return Server.getInstance().isRunning();
    }

    @Override
    protected void runCommand(String command) {
        if (executingCommands.get()) {
            ServerCommandEvent event = new ServerCommandEvent(Server.getInstance().getConsoleSender(), command);
            if (Server.getInstance().getPluginManager() != null) {
                Server.getInstance().getPluginManager().callEvent(event);
            }
            if (!event.isCancelled()) {
                ServerScheduler scheduler = Server.getInstance().getScheduler();
                if (scheduler != null) { //忽略服务器启动之前输入的命令
                    scheduler.scheduleTask(
                            InternalPlugin.INSTANCE.isEnabled() ? InternalPlugin.INSTANCE : null,
                            () -> Server.getInstance().dispatchCommand(event.getSender(), event.getCommand())
                    );
                }
            }
        } else {
            consoleQueue.add(command);
        }
    }

    public String readLine() {
        try {
            return consoleQueue.take();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void shutdown() {
        Server.getInstance().shutdown();
    }

    @Override
    protected LineReader buildReader(LineReaderBuilder builder) {
        builder.completer(new NukkitConsoleCompleter());
        builder.appName(Nukkit.NUKKIT);
        builder.option(LineReader.Option.HISTORY_BEEP, false);
        builder.option(LineReader.Option.HISTORY_IGNORE_DUPS, true);
        builder.option(LineReader.Option.HISTORY_IGNORE_SPACE, true);
        return super.buildReader(builder);
    }

    public boolean isExecutingCommands() {
        return executingCommands.get();
    }

    public void setExecutingCommands(boolean executingCommands) {
        if (this.executingCommands.compareAndSet(!executingCommands, executingCommands) && executingCommands) {
            consoleQueue.clear();
        }
    }

    /**
     * 复刻父类 start()，仅为把读取循环换成本类修复了 EOF 死循环的版本。
     * <p>
     * Replicates the parent start() only to substitute the read loop with the EOF-safe variant below.
     */
    @Override
    public void start() {
        try {
            Terminal terminal = TerminalConsoleAppender.getTerminal();
            if (terminal != null) {
                readCommands(terminal);
            } else {
                readCommands(System.in);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to read console input", e);
        }
    }

    /**
     * 复刻 SimpleTerminalConsole 的终端读取循环，唯一差异：非 TTY 终端（管道/重定向，脚本部署常见）
     * 的 EOF 是永久关闭，直接退出而不是继续循环重复打印 "> " 提示符（上游无限忙转，日志以 MB 级膨胀）。
     * <p>
     * Mirrors SimpleTerminalConsole's terminal loop with one difference: on a non-TTY terminal
     * (piped or redirected stdin) EOF is permanent, so the loop returns instead of spinning and
     * re-printing the "> " prompt forever.
     */
    private void readCommands(Terminal terminal) {
        LineReader reader = buildReader(LineReaderBuilder.builder().terminal(terminal));
        TerminalConsoleAppender.setReader(reader);
        try {
            String line;
            while (isRunning()) {
                try {
                    line = reader.readLine("> ");
                } catch (EndOfFileException e) {
                    String type = terminal.getType();
                    if (type == null || type.startsWith("dumb")) {
                        return; // 非 TTY EOF 永久关闭 / EOF on a non-TTY stream is permanent
                    }
                    continue; // 真实 TTY 的 Ctrl+D 之后仍可能有输入 / a real TTY may deliver more input after Ctrl+D
                }
                if (line == null) {
                    return;
                }
                processInput(line);
            }
        } catch (UserInterruptException e) {
            shutdown();
        } finally {
            TerminalConsoleAppender.setReader(null);
        }
    }

    /** 复刻 SimpleTerminalConsole 的流读取回退（无终端时用 BufferedReader，无提示符）。 */
    private void readCommands(InputStream stream) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
            String line;
            while (isRunning() && (line = reader.readLine()) != null) {
                processInput(line);
            }
        }
    }
}
