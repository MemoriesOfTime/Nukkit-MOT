package cn.nukkit.utils.bugreport;

import cn.nukkit.Nukkit;
import cn.nukkit.Server;
import cn.nukkit.command.defaults.StatusCommand;
import cn.nukkit.math.NukkitMath;
import cn.nukkit.plugin.Plugin;
import cn.nukkit.utils.TextFormat;
import com.sun.management.OperatingSystemMXBean;
import io.sentry.ScopeCallback;
import io.sentry.Sentry;

import java.lang.management.ManagementFactory;

public class BugReportGenerator extends Thread {

    /**
     * Allow bug reports to be handled by a plugin
     */
    public static BugReportPlugin plugin;
    private final Throwable throwable;
    private final String message;

    BugReportGenerator(Throwable throwable) {
        setName("BugReportGenerator");
        this.throwable = throwable;
        this.message = null;
    }

    public BugReportGenerator(String message) {
        setName("BugReportGenerator");
        this.throwable = null;
        this.message = message;
    }

    public static ScopeCallback getScopeCallback() {
        StringBuilder plugins = new StringBuilder();
        try {
            for (Plugin plugin : Server.getInstance().getPluginManager().getPlugins().values()) {
                if (plugins.length() > 0) {
                    plugins.append(", ");
                }
                if (!plugin.isEnabled()) {
                    plugins.append('*');
                }
                plugins.append(plugin.getDescription().getFullName());
            }
        } catch (Exception ex) {
            Server.getInstance().getLogger().logException(ex);
        }

        String cpuType = System.getenv("PROCESSOR_IDENTIFIER");
        OperatingSystemMXBean osMXBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        Runtime runtime = Runtime.getRuntime();
        double usedMB = NukkitMath.round((double) (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024, 2);
        double maxMB = NukkitMath.round(((double) runtime.maxMemory()) / 1024 / 1024, 2);
        double usage = usedMB / maxMB * 100;

        return scope -> {
            scope.setContexts("Nukkit Version", Nukkit.getBranch() + '/' + Nukkit.VERSION.substring(4));
            scope.setContexts("Java Version", System.getProperty("java.vm.name") + " (" + System.getProperty("java.runtime.version") + ')');
            scope.setContexts("Host OS", osMXBean.getName() + '-' + osMXBean.getArch() + " [" + osMXBean.getVersion() + ']');
            scope.setContexts("Memory", usedMB + " MB (" + NukkitMath.round(usage, 2) + "%) of " + maxMB + " MB");
            scope.setContexts("CPU Type", cpuType == null ? "UNKNOWN" : cpuType);
            scope.setContexts("Available Cores", String.valueOf(osMXBean.getAvailableProcessors()));
            scope.setContexts("Uptime", TextFormat.clean(StatusCommand.formatUptime(System.currentTimeMillis() - Nukkit.START_TIME)));
            scope.setContexts("Players", Server.getInstance().getOnlinePlayersCount() + "/" + Server.getInstance().getMaxPlayers());
            scope.setContexts("Plugins", plugins.toString());
        };
    }

    public static String getCount(long bytes, boolean si) {
        int unit = si ? 1000 : 1024;
        if (bytes < unit) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp - 1) + (si ? "" : "i");
        return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
    }

    @Override
    public void run() {
        if (plugin != null) {
            try {
                plugin.bugReport(throwable, message);
            } catch (Exception ex) {
                Server.getInstance().getLogger().error("[BugReport] External bug report failed", ex);
            }
        }
        try {
            if (Server.getInstance().automaticBugReport) {
                sentry();
            }
        } catch (Exception ex) {
            Server.getInstance().getLogger().error("[BugReport] Sending a bug report failed", ex);
        }
    }

    /**
     * Send a bug report to Sentry
     */
    private void sentry() {
        Server.getInstance().getLogger().info("[BugReport] Sending a bug report ...");

        boolean pluginError;
        if (throwable != null) {
            StackTraceElement[] stackTrace = throwable.getStackTrace();
            if (stackTrace.length > 0) {
                String className = throwable.getStackTrace()[0].getClassName();
                if (!className.startsWith("cn.nukkit")) {
                    pluginError = true;
                    if (className.startsWith("org.hibernate")) {
                        return;
                    }
                } else {
                    pluginError = false;
                }
            } else {
                pluginError = false;
                return; // Don't send empty stack traces
            }
        } else {
            pluginError = false;
        }

        StringBuilder plugins = new StringBuilder();
        try {
            for (Plugin plugin : Server.getInstance().getPluginManager().getPlugins().values()) {
                if (plugins.length() > 0) {
                    plugins.append(", ");
                }
                if (!plugin.isEnabled()) {
                    plugins.append('*');
                }
                plugins.append(plugin.getDescription().getFullName());
            }
        } catch (Exception ex) {
            Server.getInstance().getLogger().logException(ex);
        }

        String cpuType = System.getenv("PROCESSOR_IDENTIFIER");
        OperatingSystemMXBean osMXBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        Runtime runtime = Runtime.getRuntime();
        double usedMB = NukkitMath.round((double) (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024, 2);
        double maxMB = NukkitMath.round(((double) runtime.maxMemory()) / 1024 / 1024, 2);
        double usage = usedMB / maxMB * 100;

        Sentry.configureScope(scope -> {
            scope.setContexts("Java Version", System.getProperty("java.vm.name") + " (" + System.getProperty("java.runtime.version") + ')');
            scope.setContexts("Host OS", osMXBean.getName() + '-' + osMXBean.getArch() + " [" + osMXBean.getVersion() + ']');
            scope.setContexts("Memory", usedMB + " MB (" + NukkitMath.round(usage, 2) + "%) of " + maxMB + " MB");
            scope.setContexts("CPU Type", cpuType == null ? "UNKNOWN" : cpuType);
            scope.setContexts("Available Cores", String.valueOf(osMXBean.getAvailableProcessors()));
            scope.setContexts("Uptime", TextFormat.clean(StatusCommand.formatUptime(System.currentTimeMillis() - Nukkit.START_TIME)));
            scope.setContexts("Players", Server.getInstance().getOnlinePlayersCount() + "/" + Server.getInstance().getMaxPlayers());
            scope.setContexts("plugin_error", pluginError);
            scope.setContexts("Plugins", plugins.toString());
        });

        if (throwable != null) {
            Sentry.captureException(throwable);
        } else if (message != null) {
            Sentry.captureMessage(message);
        } else {
            Server.getInstance().getLogger().error("[BugReport] Failed to send a bug report: content cannot be null");
        }
    }
}
