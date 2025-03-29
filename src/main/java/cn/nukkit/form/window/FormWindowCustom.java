package cn.nukkit.form.window;

import cn.nukkit.form.element.*;
import cn.nukkit.form.response.FormResponseCustom;
import cn.nukkit.form.response.FormResponseData;
import cn.nukkit.network.protocol.ProtocolInfo;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class FormWindowCustom extends FormWindow {

    @SuppressWarnings("unused")
    private final String type = "custom_form"; // This variable is used for JSON import operations. Do NOT delete :) -- @Snake1999
    private String title = "";
    private ElementButtonImageData icon;
    private List<Element> content;

    private FormResponseCustom response;

    public FormWindowCustom(String title) {
        this(title, new ArrayList<>());
    }

    public FormWindowCustom(String title, List<Element> contents) {
        this(title, contents, (ElementButtonImageData) null);
    }

    public FormWindowCustom(String title, List<Element> contents, String icon) {
        this(title, contents, icon.isEmpty() ? null : new ElementButtonImageData(ElementButtonImageData.IMAGE_DATA_TYPE_URL, icon));
    }

    public FormWindowCustom(String title, List<Element> contents, ElementButtonImageData icon) {
        this.title = title;
        this.content = contents;
        this.icon = icon;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<Element> getElements() {
        return content;
    }

    public void addElement(Element element) {
        content.add(element);
    }

    public ElementButtonImageData getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        if (!icon.isEmpty()) this.icon = new ElementButtonImageData(ElementButtonImageData.IMAGE_DATA_TYPE_URL, icon);
    }

    public void setIcon(ElementButtonImageData icon) {
        this.icon = icon;
    }

    @Override
    public FormResponseCustom getResponse() {
        return response;
    }

    @Override
    public void setResponse(int protocol, String data) {
        if (data.equals("null")) {
            this.closed = true;
            return;
        }

        List<String> elementResponses = GSON.fromJson(data, new ListTypeToken().getType());

        HashMap<Integer, FormResponseData> dropdownResponses = new HashMap<>();
        HashMap<Integer, String> inputResponses = new HashMap<>();
        HashMap<Integer, Float> sliderResponses = new HashMap<>();
        HashMap<Integer, FormResponseData> stepSliderResponses = new HashMap<>();
        HashMap<Integer, Boolean> toggleResponses = new HashMap<>();
        HashMap<Integer, Object> responses = new HashMap<>();
        HashMap<Integer, String> labelResponses = new HashMap<>();
        HashMap<Integer, String> headerResponses = new HashMap<>();
        HashMap<Integer, String> dividerResponses = new HashMap<>();

        int responseIndex = 0;
        for (int i = 0; i < content.size(); i++) {
            Element e = content.get(i);
            String elementData = responseIndex >= elementResponses.size() ? "" : elementResponses.get(responseIndex);
            if (e instanceof ElementLabel) {
                labelResponses.put(i, ((ElementLabel) e).getText());
                responses.put(i, ((ElementLabel) e).getText());
                if (protocol < ProtocolInfo.v1_21_70_24) {
                    // to be compatible with the older response before 1.21.70
                    responseIndex++;
                }
            } else if (e instanceof ElementDropdown elementDropdown) {
                int index = Integer.parseInt(elementData);
                String answer = elementDropdown.getOptions().get(index);
                dropdownResponses.put(i, new FormResponseData(index, answer));
                responses.put(i, answer);
                responseIndex++;
            } else if (e instanceof ElementInput) {
                inputResponses.put(i, elementData);
                responses.put(i, elementData);
                responseIndex++;
            } else if (e instanceof ElementSlider) {
                Float answer = Float.parseFloat(elementData);
                sliderResponses.put(i, answer);
                responses.put(i, answer);
                responseIndex++;
            } else if (e instanceof ElementStepSlider elementStepSlider) {
                int index = Integer.parseInt(elementData);
                String answer = elementStepSlider.getSteps().get(index);
                stepSliderResponses.put(i, new FormResponseData(index, answer));
                responses.put(i, answer);
                responseIndex++;
            } else if (e instanceof ElementToggle) {
                Boolean answer = Boolean.parseBoolean(elementData);
                toggleResponses.put(i, answer);
                responses.put(i, answer);
                responseIndex++;
            } else if (e instanceof ElementHeader elementHeader) {
                headerResponses.put(i, elementHeader.getText());
                responses.put(i, elementHeader.getText());
            } else if (e instanceof ElementDivider elementDivider) {
                dividerResponses.put(i, elementDivider.getText());
                responses.put(i, elementDivider.getText());
            }
        }

        this.response = new FormResponseCustom(responses, dropdownResponses, inputResponses,
                sliderResponses, stepSliderResponses, toggleResponses, labelResponses, headerResponses, dividerResponses);
    }

    /**
     * Set Elements from Response
     * Used on ServerSettings Form Response. After players set settings, we need to sync these settings to the server.
     */
    public void setElementsFromResponse() {
        if (this.response != null) {
            this.response.getResponses().forEach((i, response) -> {
                Element e = content.get(i);
                if (e != null) {
                    if (e instanceof ElementDropdown) {
                        ((ElementDropdown) e).setDefaultOptionIndex(((ElementDropdown) e).getOptions().indexOf(response));
                    } else if (e instanceof ElementInput) {
                        ((ElementInput) e).setDefaultText((String)response);
                    } else if (e instanceof ElementSlider) {
                        ((ElementSlider) e).setDefaultValue((Float)response);
                    } else if (e instanceof ElementStepSlider) {
                        ((ElementStepSlider) e).setDefaultOptionIndex(((ElementStepSlider) e).getSteps().indexOf(response));
                    } else if (e instanceof ElementToggle) {
                        ((ElementToggle) e).setDefaultValue((Boolean)response);
                    }
                }
            });
        }
    }

    private static class ListTypeToken extends TypeToken<List<String>> {
    }
}
