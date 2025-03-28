package cn.nukkit.form.window;

import cn.nukkit.form.element.ElementButton;
import cn.nukkit.form.handler.FormResponseHandler;
import cn.nukkit.form.response.FormResponse;
import cn.nukkit.network.protocol.ProtocolInfo;
import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.List;

public abstract class FormWindow {

    protected static final Gson GSON = new Gson();
    protected static final Gson GSON_FORM_WINDOW_SIMPLE = new GsonBuilder().setExclusionStrategies(new ExclusionStrategyFormWindowSimple()).create();
    protected static final Gson GSON_FORM_WINDOW_SIMPLE_785 = new GsonBuilder().setExclusionStrategies(new ExclusionStrategyFormWindowSimple785()).create();

    protected transient boolean closed = false;
    protected final transient List<FormResponseHandler> handlers = new ObjectArrayList<>();

    public String getJSONData() {
        return this.getJSONData(ProtocolInfo.CURRENT_PROTOCOL);
    }

    public String getJSONData(int protocol) {
        if (this instanceof FormWindowSimple) {
            if (protocol >= ProtocolInfo.v1_21_70_25) {
                return GSON_FORM_WINDOW_SIMPLE_785.toJson(this);
            }
            return GSON_FORM_WINDOW_SIMPLE.toJson(this);
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
        @Override
        public boolean shouldSkipField(FieldAttributes fieldAttributes) {
            return (fieldAttributes.getDeclaringClass() == FormWindowSimple.class && fieldAttributes.getName().equals("response"))
                    || (fieldAttributes.getDeclaringClass() == ElementButton.class && fieldAttributes.getName().equals("image"));
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
                    && fieldAttributes.getName().equals("response");
        }

        @Override
        public boolean shouldSkipClass(Class<?> aClass) {
            return false;
        }
    }
}
