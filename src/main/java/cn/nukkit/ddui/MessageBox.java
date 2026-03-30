package cn.nukkit.ddui;

import cn.nukkit.Player;
import cn.nukkit.ddui.element.MessageBoxButtonElement;
import cn.nukkit.ddui.properties.StringProperty;

import java.util.function.Consumer;

public class MessageBox extends DataDrivenScreen {

    public MessageBox() { }

    public MessageBox(String title) {
        title(title);
    }

    public MessageBox(Observable<String> title) {
        title(title);
    }

    public MessageBox title(String title) {
        setProperty(new StringProperty("title", title, this));
        return this;
    }

    public MessageBox title(Observable<String> title) {
        StringProperty property = new StringProperty("title", title.getValue(), this);
        title.subscribe(value -> {
            title(value);
            return property;
        });
        setProperty(property);
        return this;
    }

    public MessageBox body(String body) {
        setProperty(new StringProperty("body", body, this));
        return this;
    }

    public MessageBox body(Observable<String> body) {
        StringProperty property = new StringProperty("body", body.getValue(), this);
        body.subscribe(value -> {
            body(value);
            return property;
        });
        setProperty(property);
        return this;
    }

    public MessageBox button1(String label, Consumer<Player> listener) {
        return button1(label, "", listener);
    }

    public MessageBox button1(String label, String tooltip, Consumer<Player> listener) {
        MessageBoxButtonElement button = new MessageBoxButtonElement("button1", label, tooltip, this);
        button.addListener(listener);
        setProperty(button);
        return this;
    }

    public MessageBox button2(String label, Consumer<Player> listener) {
        return button2(label, "", listener);
    }

    public MessageBox button2(String label, String tooltip, Consumer<Player> listener) {
        MessageBoxButtonElement button = new MessageBoxButtonElement("button2", label, tooltip, this);
        button.addListener(listener);
        setProperty(button);
        return this;
    }

    @Override
    public String getIdentifier() {
        return "minecraft:message_box";
    }

    @Override
    public String getProperty() {
        return "message_box_data";
    }
}
