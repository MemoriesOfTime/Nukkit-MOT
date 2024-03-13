package cn.nukkit.network.process.processor.common;

import cn.nukkit.Player;
import cn.nukkit.PlayerHandle;
import cn.nukkit.event.player.PlayerFormRespondedEvent;
import cn.nukkit.event.player.PlayerSettingsRespondedEvent;
import cn.nukkit.form.handler.FormResponseHandler;
import cn.nukkit.form.window.FormWindow;
import cn.nukkit.form.window.FormWindowCustom;
import cn.nukkit.network.process.DataPacketProcessor;
import cn.nukkit.network.protocol.ModalFormResponsePacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

/**
 * @author LT_Name
 */
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ModalFormResponseProcessor extends DataPacketProcessor<ModalFormResponsePacket> {
    
    public static final ModalFormResponseProcessor INSTANCE = new ModalFormResponseProcessor();
    
    @Override
    public void handle(@NotNull PlayerHandle playerHandle, @NotNull ModalFormResponsePacket pk) {
        Player player = playerHandle.player;

        player.formOpen = false;

        if (!player.spawned || !player.isAlive()) {
            return;
        }

        if (playerHandle.getFormWindows().containsKey(pk.formId)) {
            FormWindow window = playerHandle.getFormWindows().remove(pk.formId);
            window.setResponse(pk.data.trim());

            for (FormResponseHandler handler : window.getHandlers()) {
                handler.handle(player, pk.formId);
            }

            new PlayerFormRespondedEvent(player, pk.formId, window).call();
        } else if (playerHandle.getServerSettings().containsKey(pk.formId)) {
            FormWindow window = playerHandle.getServerSettings().get(pk.formId);
            window.setResponse(pk.data.trim());

            for (FormResponseHandler handler : window.getHandlers()) {
                handler.handle(player, pk.formId);
            }

            PlayerSettingsRespondedEvent event = new PlayerSettingsRespondedEvent(player, pk.formId, window);
            event.call();

            if (!event.isCancelled() && window instanceof FormWindowCustom) {
                ((FormWindowCustom) window).setElementsFromResponse();
            }
        }
    }
    
    @Override
    public int getPacketId() {
        return ProtocolInfo.toNewProtocolID(ProtocolInfo.MODAL_FORM_RESPONSE_PACKET);
    }
    
}
