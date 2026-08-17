package cn.nukkit.event.player;

import cn.nukkit.Player;
import cn.nukkit.event.HandlerList;
import cn.nukkit.form.response.FormResponseDialog;
import cn.nukkit.form.window.FormWindowDialog;

public class PlayerDialogRespondedEvent extends PlayerEvent {
    private static final HandlerList handlers = new HandlerList();

    public static HandlerList getHandlers() {
        return handlers;
    }

    protected FormWindowDialog dialog;

    protected FormResponseDialog response;

    public PlayerDialogRespondedEvent(Player player, FormWindowDialog dialog, FormResponseDialog response) {
        this.dialog = dialog;
        this.response = response;
    }

    public FormWindowDialog getDialog() {
        return dialog;
    }

    public FormResponseDialog getResponse() {
        return response;
    }
}
