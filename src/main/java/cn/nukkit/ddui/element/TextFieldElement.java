package cn.nukkit.ddui.element;

import cn.nukkit.Player;
import cn.nukkit.ddui.Observable;
import cn.nukkit.ddui.element.options.TextFieldOptions;
import cn.nukkit.ddui.properties.BooleanProperty;
import cn.nukkit.ddui.properties.ObjectProperty;
import cn.nukkit.ddui.properties.StringProperty;

public class TextFieldElement extends Element<String> {

    private final Observable<String> text;

    public TextFieldElement(String label, Observable<String> text, ObjectProperty parent) {
        this(label, text, TextFieldOptions.builder().build(), parent);
    }

    @SuppressWarnings("unchecked")
    public TextFieldElement(String label, Observable<String> text, TextFieldOptions options, ObjectProperty parent) {
        super("textField", parent);
        this.text = text;

        setLabel(label);

        setText(text);

        if (options.getVisible() instanceof Observable<?> obs) {
            setVisibility((Observable<Boolean>) obs);
        } else {
            setVisibility((Boolean) options.getVisible());
        }

        if (options.getDisabled() instanceof Observable<?> obs) {
            setDisabled((Observable<Boolean>) obs);
        } else {
            setDisabled((Boolean) options.getDisabled());
        }

        if (options.getDescription() instanceof Observable<?> obs) {
            setDescription((Observable<String>) obs);
        } else {
            setDescription((String) options.getDescription());
        }
    }

    public boolean getTextFieldVisible() {
        var prop = getProperty("textfield_visible");
        if (prop instanceof BooleanProperty bp) return bp.getValue();
        return true;
    }

    public TextFieldElement setTextFieldVisible(boolean visible) {
        setProperty(new BooleanProperty("textfield_visible", visible, this));
        return this;
    }

    public TextFieldElement setTextFieldVisible(Observable<Boolean> visible) {
        var existing = getProperty("textfield_visible");
        BooleanProperty property = (existing instanceof BooleanProperty bp)
                ? bp
                : new BooleanProperty("textfield_visible", true, this);
        property.setValue(visible.getValue());
        visible.subscribe(value -> {
            property.setValue(value);
            return property;
        });
        setProperty(property);
        return this;
    }

    @Override
    public TextFieldElement setVisibility(boolean visible) {
        super.setVisibility(visible);
        setProperty(new BooleanProperty("textfield_visible", visible, this));
        return this;
    }

    @Override
    public TextFieldElement setVisibility(Observable<Boolean> visible) {
        super.setVisibility(visible);
        var property = new BooleanProperty("textfield_visible", visible.getValue(), this);
        visible.subscribe(value -> {
            property.setValue(value);
            return property;
        });
        setProperty(property);
        return this;
    }

    public String getText() {
        var prop = getProperty("text");
        if (prop instanceof StringProperty sp) return sp.getValue();
        return "";
    }

    public TextFieldElement setText(String text) {
        var existing = getProperty("text");
        StringProperty property = (existing instanceof StringProperty sp)
                ? sp
                : createTextProperty();
        property.setValue(text);
        setProperty(property);
        return this;
    }

    public TextFieldElement setText(Observable<String> text) {
        var existing = getProperty("text");
        StringProperty property = (existing instanceof StringProperty sp)
                ? sp
                : createTextProperty();
        property.setClientWritable(text.isClientWritable());
        property.setValue(text.getValue());
        text.subscribe(value -> {
            property.setValue(value);
            return property;
        });
        setProperty(property);
        return this;
    }

    private StringProperty createTextProperty() {
        StringProperty property = new StringProperty("text", "", this);
        property.addListener(this::triggerListeners);
        return property;
    }

    public String getDescription() {
        var prop = getProperty("description");
        if (prop instanceof StringProperty sp) return sp.getValue();
        return "";
    }

    public TextFieldElement setDescription(String description) {
        var existing = getProperty("description");
        StringProperty property = (existing instanceof StringProperty sp)
                ? sp
                : new StringProperty("description", "", this);
        property.setValue(description);
        setProperty(property);
        return this;
    }

    public TextFieldElement setDescription(Observable<String> description) {
        var existing = getProperty("description");
        StringProperty property = (existing instanceof StringProperty sp)
                ? sp
                : new StringProperty("description", "", this);
        property.setValue(description.getValue());
        description.subscribe(value -> {
            property.setValue(value);
            return property;
        });
        setProperty(property);
        return this;
    }

    @Override
    public void triggerListeners(Player player, Object data) {
        super.triggerListeners(player, data);

        if (data instanceof String s) {
            text.setValue(s);
        }
    }
}
