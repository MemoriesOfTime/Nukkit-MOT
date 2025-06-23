package cn.nukkit.form.element;

import com.google.gson.annotations.SerializedName;

import javax.annotation.Nullable;

public class ElementToggle extends Element {

    @SuppressWarnings("unused")
    private final String type = "toggle"; //This variable is used for JSON import operations. Do NOT delete :) -- @Snake1999
    private String text;
    @SerializedName("default")
    private boolean defaultValue;
    /**
     * This option will show an exclamation icon that will display a tooltip if it is hovered.
     * @since 1.21.80
     */
    @Nullable
    private String tooltip;

    public ElementToggle(String text) {
        this(text, false);
    }

    public ElementToggle(String text, boolean defaultValue) {
        this.text = text;
        this.defaultValue = defaultValue;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public boolean isDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(boolean defaultValue) {
        this.defaultValue = defaultValue;
    }

    public String getTooltip() {
        return tooltip;
    }

    public void setTooltip(String tooltip) {
        this.tooltip = tooltip;
    }
}
