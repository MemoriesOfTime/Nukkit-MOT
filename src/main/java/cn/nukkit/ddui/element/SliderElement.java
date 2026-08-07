package cn.nukkit.ddui.element;

import cn.nukkit.Player;
import cn.nukkit.ddui.Observable;
import cn.nukkit.ddui.element.options.SliderElementOptions;
import cn.nukkit.ddui.properties.BooleanProperty;
import cn.nukkit.ddui.properties.LongProperty;
import cn.nukkit.ddui.properties.ObjectProperty;
import cn.nukkit.ddui.properties.StringProperty;

public class SliderElement extends Element<Long> {

    private final Observable<Long> currentValue;

    private long min = 0L;
    private long max = 100L;

    public SliderElement(String label, Observable<Long> currentValue, long minValue, long maxValue, ObjectProperty parent) {
        this(label, currentValue, minValue, maxValue, SliderElementOptions.builder().build(), parent);
    }

    @SuppressWarnings("unchecked")
    public SliderElement(String label, Observable<Long> currentValue, long minValue, long maxValue, SliderElementOptions options, ObjectProperty parent) {
        super("slider", parent);
        this.currentValue = currentValue;

        setLabel(label);

        if (options.getVisible() instanceof Observable<?> obs) setVisibility((Observable<Boolean>) obs);
        else setVisibility((Boolean) options.getVisible());

        if (options.getDisabled() instanceof Observable<?> obs) setDisabled((Observable<Boolean>) obs);
        else setDisabled((Boolean) options.getDisabled());

        if (options.getStep() instanceof Observable<?> obs) setStep((Observable<Long>) obs);
        else setStep(((Number) options.getStep()).longValue());

        setMinValue(minValue);
        setMaxValue(maxValue);

        setValue(currentValue);

        if (options.getDescription() instanceof Observable<?> obs) setDescription((Observable<String>) obs);
        else setDescription((String) options.getDescription());
    }

    private long clampValue(long value) {
        if (value < min) return min;
        return Math.min(value, max);
    }

    public long getMaxValue() {
        var prop = getProperty("maxValue");
        return (prop instanceof LongProperty lp) ? lp.getValue() : max;
    }

    public void setMaxValue(long maxValue) {
        this.max = maxValue;
        var property = new LongProperty("maxValue", maxValue, this);
        setProperty(property);
        long currentVal = getSliderValue();
        if (currentVal > maxValue) setValueInternal(maxValue);
    }

    public SliderElement setMaxValue(Observable<Long> maxValue) {
        var property = new LongProperty("maxValue", maxValue.getValue(), this);
        this.max = maxValue.getValue();
        maxValue.subscribe(value -> {
            this.max = value;
            property.setValue(value);
            long currentVal = getSliderValue();
            if (currentVal > value) {
                setValueInternal(value);
            }
            return property;
        });
        setProperty(property);
        return this;
    }

    public long getMinValue() {
        var prop = getProperty("minValue");
        return (prop instanceof LongProperty lp) ? lp.getValue() : min;
    }

    public void setMinValue(long minValue) {
        this.min = minValue;
        var property = new LongProperty("minValue", minValue, this);
        setProperty(property);

        long currentVal = getSliderValue();
        if (currentVal < minValue) setValueInternal(minValue);
    }

    public SliderElement setMinValue(Observable<Long> minValue) {
        var property = new LongProperty("minValue", minValue.getValue(), this);
        this.min = minValue.getValue();
        minValue.subscribe(value -> {
            this.min = value;
            property.setValue(value);
            long currentVal = getSliderValue();
            if (currentVal < value) {
                setValueInternal(value);
            }
            return property;
        });
        setProperty(property);
        return this;
    }

    public long getStep() {
        var prop = getProperty("step");
        return (prop instanceof LongProperty lp) ? lp.getValue() : 1L;
    }

    public SliderElement setStep(long step) {
        var property = new LongProperty("step", step, this);
        setProperty(property);
        return this;
    }

    public SliderElement setStep(Observable<Long> step) {
        var property = new LongProperty("step", step.getValue(), this);
        step.subscribe(value -> {
            property.setValue(value);
            return property;
        });
        setProperty(property);
        return this;
    }

    public long getSliderValue() {
        var prop = getProperty("value");
        return (prop instanceof LongProperty lp) ? lp.getValue() : min;
    }

    public SliderElement setValue(long value) {
        return setValueInternal(clampValue(value));
    }

    private SliderElement setValueInternal(long value) {
        var existing = getProperty("value");
        LongProperty property = (existing instanceof LongProperty lp) ? lp : createValueProperty();
        property.setValue(value);
        setProperty(property);

        if (currentValue != null && !currentValue.getValue().equals(value)) {
            currentValue.setValue(value);
        }

        return this;
    }

    public SliderElement setValue(Observable<Long> value) {
        long clampedInitial = clampValue(value.getValue());
        var existing = getProperty("value");
        LongProperty property = (existing instanceof LongProperty lp) ? lp : createValueProperty();
        property.setClientWritable(value.isClientWritable());

        property.setValue(clampedInitial);
        setProperty(property);

        if (!value.getValue().equals(clampedInitial)) {
            Observable.withOutboundSuppressed(() -> value.setValue(clampedInitial));
        }

        value.subscribe(v -> {
            long clamped = clampValue(v);

            if (v != clamped) {
                LongProperty prop = (getProperty("value") instanceof LongProperty lp) ? lp : createValueProperty();
                if (prop.getValue() != clamped) {
                    prop.setValue(clamped);
                    setProperty(prop);
                }

                if (!value.getValue().equals(clamped)) {
                    Observable.withOutboundSuppressed(() -> value.setValue(clamped));
                }

                return prop;
            }

            LongProperty prop = (getProperty("value") instanceof LongProperty lp) ? lp : createValueProperty();
            if (prop.getValue() != clamped) {
                prop.setValue(clamped);
                setProperty(prop);
            }
            return prop;
        });

        return this;
    }

    private LongProperty createValueProperty() {
        LongProperty property = new LongProperty("value", min, this);
        property.addListener((player, data) -> {
            if (data instanceof Long l) {
                setValue(l);
                triggerListeners(player, l);
            }
        });
        return property;
    }

    public String getDescription() {
        var prop = getProperty("description");
        return (prop instanceof StringProperty sp) ? sp.getValue() : "";
    }

    public SliderElement setDescription(String description) {
        var existing = getProperty("description");
        StringProperty property = (existing instanceof StringProperty sp) ? sp : new StringProperty("description", "", this);
        property.setValue(description);
        setProperty(property);
        return this;
    }

    public SliderElement setDescription(Observable<String> description) {
        var existing = getProperty("description");
        StringProperty property = (existing instanceof StringProperty sp) ? sp : new StringProperty("description", "", this);
        property.setValue(description.getValue());
        description.subscribe(value -> {
            property.setValue(value);
            return property;
        });
        setProperty(property);
        return this;
    }

    @Override
    public SliderElement setVisibility(boolean visible) {
        super.setVisibility(visible);
        setProperty(new BooleanProperty("slider_visible", visible, this));
        return this;
    }

    @Override
    public SliderElement setVisibility(Observable<Boolean> visible) {
        super.setVisibility(visible);
        var property = new BooleanProperty("slider_visible", visible.getValue(), this);
        visible.subscribe(value -> {
            property.setValue(value);
            return property;
        });
        setProperty(property);
        return this;
    }

    @Override
    public void triggerListeners(Player player, Object data) {
        super.triggerListeners(player, data);
        if (data instanceof Long l) setValue(l);
    }
}
