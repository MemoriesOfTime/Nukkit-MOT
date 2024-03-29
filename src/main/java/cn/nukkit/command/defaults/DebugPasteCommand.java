package cn.nukkit.command.defaults;

import cn.nukkit.Nukkit;
import cn.nukkit.Server;
import cn.nukkit.command.CommandSender;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.plugin.InternalPlugin;
import cn.nukkit.plugin.Plugin;
import cn.nukkit.plugin.PluginDescription;
import cn.nukkit.scheduler.AsyncTask;
import cn.nukkit.utils.HastebinUtility;
import cn.nukkit.utils.MainLogger;
import cn.nukkit.utils.Utils;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;

public class DebugPasteCommand extends VanillaCommand {

    public DebugPasteCommand(String name) {
        super(name, "%nukkit.command.debug.description", "%commands.debug.usage");
        this.setPermission("nukkit.command.debug.perform");
        this.commandParameters.clear();
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        if (!this.testPermission(sender)) {
            return true;
        }

        Server server = Server.getInstance();
        sender.sendMessage("Uploading...");
        server.getScheduler().scheduleAsyncTask(InternalPlugin.INSTANCE, new DebugPasteTask(server, sender));
        return true;
    }

    private static class DebugPasteTask extends AsyncTask {

        private final Server server;
        private final CommandSender sender;

        public DebugPasteTask(Server server, CommandSender sender) {
            this.server = server;
            this.sender = sender;
        }

        @Override
        public void onRun() {
            try {
                server.dispatchCommand(sender, "status");
                String dataPath = server.getDataPath();
                String serverProperties = HastebinUtility.upload(new File(dataPath, "server.properties"));
                String latestLog = HastebinUtility.upload(new File(dataPath, "/logs/server.log"));
                String threadDump = HastebinUtility.upload(Utils.getAllThreadDumps());

                StringBuilder b = new StringBuilder();
                b.append("# Files\n");
                b.append("links.server_properties: ").append(serverProperties).append('\n');
                b.append("links.server_log: ").append(latestLog).append('\n');
                b.append("links.thread_dump: ").append(threadDump).append('\n');
                b.append("\n# Server Information\n");

                //b.append("version.api: ").append(server.getApiVersion()).append('\n');
                b.append("version.nukkit: ").append(Nukkit.NUKKIT).append('\n');
                b.append("version.build: ").append(Nukkit.getBranch()).append('/').append(Nukkit.VERSION.substring(4)).append('\n');
                b.append("version.minecraft: ").append(ProtocolInfo.MINECRAFT_VERSION).append('\n');
                b.append("version.protocol: ").append(ProtocolInfo.CURRENT_PROTOCOL).append('\n');
                b.append("plugins:");

                for (Plugin plugin : server.getPluginManager().getPlugins().values()) {
                    boolean enabled = plugin.isEnabled();
                    String name = plugin.getName();
                    PluginDescription desc = plugin.getDescription();
                    String version = desc.getVersion();
                    b.append("\n  ")
                            .append(name)
                            .append(":\n    ")
                            .append("version: '")
                            .append(version)
                            .append('\'')
                            .append("\n    enabled: ")
                            .append(enabled);
                }

                b.append("\n\n# Java Details\n");
                Runtime runtime = Runtime.getRuntime();
                b.append("memory.free: ").append(runtime.freeMemory()).append('\n');
                b.append("memory.max: ").append(runtime.maxMemory()).append('\n');
                b.append("cpu.runtime: ").append(ManagementFactory.getRuntimeMXBean().getUptime()).append('\n');
                b.append("cpu.processors: ").append(runtime.availableProcessors()).append('\n');
                b.append("java.specification.version: '").append(System.getProperty("java.specification.version")).append("'\n");
                b.append("java.vendor: '").append(System.getProperty("java.vendor")).append("'\n");
                b.append("java.version: '").append(System.getProperty("java.version")).append("'\n");
                b.append("os.arch: '").append(System.getProperty("os.arch")).append("'\n");
                b.append("os.name: '").append(System.getProperty("os.name")).append("'\n");
                b.append("os.version: '").append(System.getProperty("os.version")).append("'\n\n");
                String link = HastebinUtility.upload(b.toString());
                sender.sendMessage(link);
            } catch (IOException e) {
                MainLogger.getLogger().logException(e);
            }
        }
    }
}