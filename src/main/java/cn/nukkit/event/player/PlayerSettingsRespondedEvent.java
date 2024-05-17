package cn.nukkit.event.player;

import cn.nukkit.Player;
import cn.nukkit.event.Cancellable;
import cn.nukkit.event.HandlerList;
import cn.nukkit.form.response.FormResponse;
import cn.nukkit.form.window.FormWindow;

public class PlayerSettingsRespondedEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();
    protected int formID;
    protected FormWindow window;
    public PlayerSettingsRespondedEvent(Player player, int formID, FormWindow window) {
        this.player = player;
        this.formID = formID;
        this.window = window;
    }

    public static HandlerList getHandlers() {
        return handlers;
    }

    public int getFormID() {
        return this.formID;
    }

    public FormWindow getWindow() {
        return window;
    }

    /**
     * Can be null if player closed the window instead of submitting it
     */
    public FormResponse getResponse() {
        return window.getResponse();
    }

    /**
     * Defines if player closed the window or submitted it
     */
    public boolean wasClosed() {
        return window.wasClosed();
    }
}
