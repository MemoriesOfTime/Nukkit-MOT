package cn.nukkit.ddui;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.ddui.element.LayoutElement;
import cn.nukkit.ddui.properties.DataDrivenProperty;
import cn.nukkit.ddui.properties.ObjectProperty;
import cn.nukkit.network.protocol.ClientboundDataDrivenUICloseScreenPacket;
import cn.nukkit.network.protocol.ClientboundDataDrivenUIShowScreenPacket;
import cn.nukkit.network.protocol.ClientboundDataStorePacket;
import cn.nukkit.network.protocol.types.datastore.DataStoreChange;
import cn.nukkit.plugin.InternalPlugin;
import cn.nukkit.scheduler.TaskHandler;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Abstract base for all data-driven UI screens.
 */
public abstract class DataDrivenScreen extends ObjectProperty<Object> {

    private static final AtomicInteger DDUI_FORM_ID_COUNTER = new AtomicInteger(0);

    private static final Map<Player, Map<Integer, DataDrivenScreen>> PLAYER_DDUI_SCREENS =
            new ConcurrentHashMap<>();

    private static final Cache<Player, TaskHandler> PENDING_SHOW_TASKS = Caffeine.newBuilder()
            .expireAfterWrite(30, TimeUnit.SECONDS)
            .build();

    public abstract String getIdentifier();

    public abstract String getProperty();

    private final Set<Player> viewers = new CopyOnWriteArraySet<>();

    private final Map<Player, Integer> playerFormIds = new ConcurrentHashMap<>();

    protected final LayoutElement layout;

    protected DataDrivenScreen() {
        super("");
        this.layout = new LayoutElement(this);
        this.setProperty(layout);
    }

    public void show(Player player) {
        // Cancel any pending delayed show for this player
        TaskHandler pending = PENDING_SHOW_TASKS.asMap().remove(player);
        if (pending != null) {
            pending.cancel();
        }

        Map<Integer, DataDrivenScreen> oldScreens = PLAYER_DDUI_SCREENS.remove(player);
        boolean hasExistingScreen = oldScreens != null && !oldScreens.isEmpty();

        if (hasExistingScreen) {
            for (DataDrivenScreen screen : oldScreens.values()) {
                Integer oldFormId = screen.playerFormIds.remove(player);
                if (oldFormId != null) {
                    ClientboundDataDrivenUICloseScreenPacket closePacket = new ClientboundDataDrivenUICloseScreenPacket();
                    closePacket.formId = oldFormId;
                    player.dataPacket(closePacket);
                }
                screen.viewers.remove(player);
            }
            // Delay the new screen to allow the client to fully process the close.
            // The Bedrock client rejects ShowScreen with USER_BUSY if it arrives
            // too soon after closing a DDUI screen.
            TaskHandler task = Server.getInstance().getScheduler().scheduleDelayedTask(InternalPlugin.INSTANCE, () -> {
                PENDING_SHOW_TASKS.invalidate(player);
                doShow(player);
            }, 20);
            PENDING_SHOW_TASKS.put(player, task);
        } else {
            doShow(player);
        }
    }

    private void doShow(Player player) {
        if (!player.isConnected()) return;

        // Allocate a new unique formId for this screen on this player
        int formId = DDUI_FORM_ID_COUNTER.updateAndGet(v -> (v == Integer.MAX_VALUE) ? 0 : v + 1);

        String dataStore = getIdentifier().split(":")[0];
        DataStoreChange change = new DataStoreChange();
        change.setDataStoreName(dataStore);
        change.setProperty(getProperty());
        change.setUpdateCount(1);
        change.setNewValue(toChangeValue());

        ClientboundDataStorePacket data = new ClientboundDataStorePacket();
        data.setUpdates(List.of(change));

        ClientboundDataDrivenUIShowScreenPacket show = new ClientboundDataDrivenUIShowScreenPacket();
        show.screenId = getIdentifier();
        show.formId = formId;

        // Send new screen packets
        player.dataPacket(data);
        player.dataPacket(show);

        // Update server-side tracking
        viewers.add(player);
        playerFormIds.put(player, formId);
        PLAYER_DDUI_SCREENS
                .computeIfAbsent(player, k -> new ConcurrentHashMap<>())
                .put(formId, this);
    }

    public void close(Player player) {
        Integer formId = playerFormIds.get(player);
        if (formId == null) return;

        removeViewer(player);

        ClientboundDataDrivenUICloseScreenPacket packet = new ClientboundDataDrivenUICloseScreenPacket();
        packet.formId = formId;
        player.dataPacket(packet);
    }

    public List<Player> getAllViewers() {
        return new ArrayList<>(viewers);
    }

    public static DataDrivenScreen getActiveScreen(Player player) {
        Map<Integer, DataDrivenScreen> screens = PLAYER_DDUI_SCREENS.get(player);
        if (screens == null || screens.isEmpty()) return null;
        return screens.entrySet().stream()
                .max(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .orElse(null);
    }

    public static DataDrivenScreen getScreenByFormId(Player player, int formId) {
        return PLAYER_DDUI_SCREENS.getOrDefault(player, Collections.emptyMap()).get(formId);
    }

    public void removeViewer(Player player) {
        Integer formId = playerFormIds.get(player);
        if (formId != null) {
            Map<Integer, DataDrivenScreen> screens = PLAYER_DDUI_SCREENS.get(player);
            if (screens != null) {
                screens.remove(formId);
                if (screens.isEmpty()) {
                    PLAYER_DDUI_SCREENS.remove(player);
                }
            }
            playerFormIds.remove(player);
        }
        viewers.remove(player);
    }

    public static void removeActiveScreen(Player player) {
        TaskHandler pending = PENDING_SHOW_TASKS.asMap().remove(player);
        if (pending != null) {
            pending.cancel();
        }
        DataDrivenScreen screen = getActiveScreen(player);
        if (screen != null) {
            screen.close(player);
        }
    }

    public DataDrivenProperty<?, ?> resolvePath(String path) {
        if (path == null || path.isEmpty()) {
            return this;
        }

        DataDrivenProperty<?, ?> current = this;
        int i = 0;
        while (i < path.length()) {
            char c = path.charAt(i);
            if (c == '.') {
                i++;
                continue;
            }
            String token;
            if (c == '[') {
                int end = path.indexOf(']', i + 1);
                if (end < 0) {
                    return null;
                }
                token = path.substring(i + 1, end);
                i = end + 1;
            } else {
                int end = i;
                while (end < path.length() && path.charAt(end) != '.' && path.charAt(end) != '[') {
                    end++;
                }
                token = path.substring(i, end);
                i = end;
            }

            if (!(current instanceof ObjectProperty<?> obj)) {
                return null;
            }
            current = obj.getProperty(token);
            if (current == null) {
                return null;
            }
        }

        return current;
    }

    @Override
    public DataDrivenScreen getRootScreen() {
        return this;
    }
}
