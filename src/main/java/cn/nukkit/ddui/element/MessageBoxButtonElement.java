package cn.nukkit.ddui.element;

import cn.nukkit.Player;
import cn.nukkit.ddui.Observable;
import cn.nukkit.ddui.properties.ObjectProperty;
import cn.nukkit.ddui.properties.StringProperty;

import java.util.function.Consumer;

public class MessageBoxButtonElement extends Element<Long> {

    @SuppressWarnings("unchecked")
    public MessageBoxButtonElement(String elementName, ObjectProperty parent) {
        this(elementName, "", parent);
    }

    @SuppressWarnings("unchecked")
    public MessageBoxButtonElement(String elementName, String label, ObjectProperty parent) {
        this(elementName, label, "", parent);
    }

    @SuppressWarnings("unchecked")
    public MessageBoxButtonElement(String elementName, String label, String toolTip, ObjectProperty parent) {
        super(elementName, parent);

        setLabel(label);

        if (!toolTip.isEmpty()) {
            setToolTip(toolTip);
        }

        ButtonClickElement clickElement = new ButtonClickElement(this);
        setProperty(clickElement);
        clickElement.addListener((player, data) -> triggerListeners(player, data));
    }

    public void addListener(Consumer<Player> listener) {
        addListener((player, data) -> listener.accept(player));
    }

    public String getToolTip() {
        var prop = getProperty("tooltip");
        if (prop instanceof StringProperty sp) return sp.getValue();
        return "";
    }

    public MessageBoxButtonElement setToolTip(String tooltip) {
        var property = new StringProperty("tooltip", tooltip, this);
        setProperty(property);
        return this;
    }

    public MessageBoxButtonElement setToolTip(Observable<String> tooltip) {
        var property = new StringProperty("tooltip", tooltip.getValue(), this);
        tooltip.subscribe(value -> {
            property.setValue(value);
            return property;
        });
        setProperty(property);
        return this;
    }
}
