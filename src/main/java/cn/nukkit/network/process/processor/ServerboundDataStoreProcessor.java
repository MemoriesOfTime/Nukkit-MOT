package cn.nukkit.network.process.processor;

import cn.nukkit.Player;
import cn.nukkit.PlayerHandle;
import cn.nukkit.ddui.DataDrivenScreen;
import cn.nukkit.ddui.Observable;
import cn.nukkit.ddui.properties.BooleanProperty;
import cn.nukkit.ddui.properties.DataDrivenProperty;
import cn.nukkit.ddui.properties.LongProperty;
import cn.nukkit.ddui.properties.StringProperty;
import cn.nukkit.network.process.DataPacketProcessor;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.protocol.ServerboundDataStorePacket;
import org.jetbrains.annotations.NotNull;

public class ServerboundDataStoreProcessor extends DataPacketProcessor<ServerboundDataStorePacket> {

    public static final ServerboundDataStoreProcessor INSTANCE = new ServerboundDataStoreProcessor();

    @Override
    public void handle(@NotNull PlayerHandle playerHandle, @NotNull ServerboundDataStorePacket pk) {
        Player player = playerHandle.player;

        Observable.withOutboundSuppressed(() -> {
            var update = pk.getUpdate();
            if (update == null) return;

            DataDrivenScreen screen = DataDrivenScreen.getActiveScreen(player);
            if (screen == null) return;

            String dataStore = screen.getIdentifier().split(":")[0];
            if (!dataStore.equals(update.getDataStoreName())) return;
            if (!screen.getProperty().equals(update.getProperty())) return;

            DataDrivenProperty<?, ?> property = screen.resolvePath(update.getPath());
            if (property == null) return;

            Object data = update.getData();
            if (property instanceof LongProperty) {
                if (data instanceof Number) {
                    property.triggerListeners(player, ((Number) data).longValue());
                } else {
                    property.triggerListeners(player, 0L);
                }
            } else if (property instanceof BooleanProperty) {
                if (data instanceof Boolean) {
                    property.triggerListeners(player, data);
                }
            } else if (property instanceof StringProperty) {
                if (data instanceof String) {
                    property.triggerListeners(player, data);
                }
            }
        });
    }

    @Override
    public int getPacketId() {
        return ProtocolInfo.SERVERBOUND_DATA_STORE_PACKET;
    }

    @Override
    public Class<? extends cn.nukkit.network.protocol.DataPacket> getPacketClass() {
        return ServerboundDataStorePacket.class;
    }
}
