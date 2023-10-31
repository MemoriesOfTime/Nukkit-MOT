package cn.nukkit.form.response;

import cn.nukkit.form.element.ElementDialogButton;
import cn.nukkit.form.window.FormWindowDialog;
import cn.nukkit.network.protocol.NPCRequestPacket;
import lombok.Getter;

@Getter
public class FormResponseDialog extends FormResponse {

    private long entityRuntimeId;
    private String data;
    private ElementDialogButton clickedButton;//can be null
    private String sceneName;
    private NPCRequestPacket.RequestType requestType;
    private int skinType;

    public FormResponseDialog(NPCRequestPacket packet, FormWindowDialog dialog) {
        this.entityRuntimeId = packet.entityRuntimeId;
        this.data = packet.commandString;
        try {
            this.clickedButton = dialog.getButtons().get(packet.actionType);
        }catch (IndexOutOfBoundsException e) {
            this.clickedButton = null;
        }
        this.sceneName = packet.sceneName;
        this.requestType = packet.requestType;
        this.skinType = packet.actionType;
    }
}
