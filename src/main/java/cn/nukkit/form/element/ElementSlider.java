package cn.nukkit.form.element;

import com.google.gson.annotations.SerializedName;

import org.jetbrains.annotations.Nullable;

public class ElementSlider extends Element {

    @SuppressWarnings("unused")
    private final String type = "slider"; //This variable is used for JSON import operations. Do NOT delete :) -- @Snake1999
    private String text = "";
    private float min = 0f;
    private float max = 100f;
    private int step;
    @SerializedName("default")
    private float defaultValue;
    /**
     * This option will show an exclamation icon that will display a tooltip if it is hovered.
     * @since 1.21.80
     */
    @Nullable
    private String tooltip;

    public ElementSlider(String text, float min, float max) {
        this(text, min, max, -1);
    }

    public ElementSlider(String text, float min, float max, int step) {
        this(text, min, max, step, -1);
    }

    public ElementSlider(String text, float min, float max, int step, float defaultValue) {
        this.text = text;
        this.min = Math.max(min, 0f);
        this.max = Math.max(max, this.min);
        if (step > 0) this.step = step;
        if (defaultValue != -1.0f) this.defaultValue = defaultValue;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public float getMin() {
        return min;
    }

    public void setMin(float min) {
        this.min = min;
    }

    public float getMax() {
        return max;
    }

    public void setMax(float max) {
        this.max = max;
    }

    public int getStep() {
        return step;
    }

    public void setStep(int step) {
        this.step = step;
    }

    public float getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(float defaultValue) {
        this.defaultValue = defaultValue;
    }

    @Nullable
    public String getTooltip() {
        return tooltip;
    }

    public void setTooltip(String tooltip) {
        this.tooltip = tooltip;
    }
}
