package cn.nukkit.form.handler;

import cn.nukkit.Player;
import cn.nukkit.form.response.FormResponseDialog;

import java.util.function.Consumer;

public interface FormDialogHandler {

    static FormDialogHandler withoutPlayer(Consumer<FormResponseDialog> responseConsumer) {
        return (player, response) -> responseConsumer.accept(response);
    }

    void handle(Player player, FormResponseDialog response);
}
