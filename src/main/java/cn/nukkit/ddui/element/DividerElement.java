package cn.nukkit.ddui.element;

import cn.nukkit.ddui.Observable;
import cn.nukkit.ddui.element.options.DividerOptions;
import cn.nukkit.ddui.properties.BooleanProperty;
import cn.nukkit.ddui.properties.ObjectProperty;

public class DividerElement extends Element<Boolean> {

    public DividerElement(ObjectProperty parent) {
        this(DividerOptions.builder().build(), parent);
    }

    public DividerElement(DividerOptions options, ObjectProperty parent) {
        super("divider", parent);
        applyVisibility(options);
    }

    @Override
    public DividerElement setVisibility(boolean visible) {
        super.setVisibility(visible);
        setProperty(new BooleanProperty("divider_visible", visible, this));
        return this;
    }

    @Override
    public DividerElement setVisibility(Observable<Boolean> visible) {
        super.setVisibility(visible);
        var property = new BooleanProperty("divider_visible", visible.getValue(), this);
        visible.subscribe(value -> {
            property.setValue(value);
            return property;
        });
        setProperty(property);
        return this;
    }

    @SuppressWarnings("unchecked")
    private void applyVisibility(DividerOptions options) {
        if (options.getVisible() instanceof Observable<?> obs) {
            setVisibility((Observable<Boolean>) obs);
        } else {
            setVisibility((Boolean) options.getVisible());
        }
    }
}
