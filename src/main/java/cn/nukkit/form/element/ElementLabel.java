package cn.nukkit.form.element;

public class ElementLabel extends Element implements SimpleElement {

    @SuppressWarnings("unused")
    private final String type = "label"; //This variable is used for JSON import operations. Do NOT delete :) -- @Snake1999
    private String text = "";
    /**
     * This option will show an exclamation icon that will display a tooltip if it is hovered.
     */
    private String tooltip = "";

    public ElementLabel(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getTooltip() {
        return tooltip;
    }

    public void setTooltip(String tooltip) {
        this.tooltip = tooltip;
    }
}
