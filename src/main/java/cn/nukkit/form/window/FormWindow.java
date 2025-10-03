package cn.nukkit.form.window;

import cn.nukkit.GameVersion;
import cn.nukkit.Server;
import cn.nukkit.form.element.*;
import cn.nukkit.form.handler.FormResponseHandler;
import cn.nukkit.form.response.FormResponse;
import cn.nukkit.network.protocol.ProtocolInfo;
import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.ArrayList;
import java.util.List;

public abstract class FormWindow {

    protected static final Gson GSON = new Gson();
    protected static final Gson GSON_FORM_WINDOW_SIMPLE = new GsonBuilder().setExclusionStrategies(new ExclusionStrategyFormWindowSimple()).create();
    protected static final Gson GSON_FORM_WINDOW_SIMPLE_785 = new GsonBuilder().setExclusionStrategies(new ExclusionStrategyFormWindowSimple785()).create();

    protected transient boolean closed = false;
    protected final transient List<FormResponseHandler> handlers = new ObjectArrayList<>();

    public String getJSONData() {
        Server.mvw("FormWindow#getJSONData()");
        return this.getJSONData(GameVersion.getLastVersion());
    }

    @Deprecated
    public String getJSONData(int protocol) {
        return this.getJSONData(GameVersion.byProtocol(protocol, Server.getInstance().onlyNetEaseMode));
    }

    public String getJSONData(GameVersion gameVersion) {
        if (this instanceof FormWindowSimple) {
            if (gameVersion.getProtocol() >= ProtocolInfo.v1_21_70_24) {
                return GSON_FORM_WINDOW_SIMPLE_785.toJson(this);
            }
            return GSON_FORM_WINDOW_SIMPLE.toJson(this);
        }

        if (this instanceof FormWindowCustom original && gameVersion.getProtocol() < ProtocolInfo.v1_21_70_24) {
            FormWindowCustom compatibleForm = new FormWindowCustom(original.getTitle(), new ArrayList<>(), original.getIcon());
            for (Element element : original.getElements()) {
                if (element instanceof ElementHeader header) {
                    compatibleForm.addElement(new ElementLabel("§l" + header.getText() + "§r"));
                } else if (element instanceof ElementDivider) {
                    compatibleForm.addElement(new ElementLabel("§7------------------------§r"));
                } else {
                    compatibleForm.addElement(element);
                }
            }
            return FormWindow.GSON.toJson(compatibleForm);
        }

        return FormWindow.GSON.toJson(this);
    }

    public void setResponse(String data) {
        this.setResponse(ProtocolInfo.CURRENT_PROTOCOL, data);
    }

    public void setResponse(int protocol, String data) {
        // no-op
    }

    public abstract FormResponse getResponse();

    public boolean wasClosed() {
        return closed;
    }

    public void addHandler(FormResponseHandler handler) {
        this.handlers.add(handler);
    }

    public List<FormResponseHandler> getHandlers() {
        return handlers;
    }

    static class ExclusionStrategyFormWindowSimple implements ExclusionStrategy {

        private static final String FIELD_ELEMENTS = "elements";
        private static final String FIELD_TYPE = "type";
        private static final String FIELD_TOOLTIP = "tooltip";

        @Override
        public boolean shouldSkipField(FieldAttributes fieldAttributes) {
            String fieldName  = fieldAttributes.getName();
            Class<?> declaring = fieldAttributes.getDeclaringClass();

            if (declaring == FormWindowSimple.class && FIELD_ELEMENTS.equals(fieldName)) {
                return true;
            }

            if (declaring == ElementButton.class && FIELD_TYPE.equals(fieldName)) {
                return true;
            }

            return FIELD_TOOLTIP.equals(fieldName);
        }

        @Override
        public boolean shouldSkipClass(Class<?> aClass) {
            return false;
        }
    }

    static class ExclusionStrategyFormWindowSimple785 implements ExclusionStrategy {
        @Override
        public boolean shouldSkipField(FieldAttributes fieldAttributes) {
            return fieldAttributes.getDeclaringClass() == FormWindowSimple.class
                    && fieldAttributes.getName().equals("buttons");
        }

        @Override
        public boolean shouldSkipClass(Class<?> aClass) {
            return false;
        }
    }
}
