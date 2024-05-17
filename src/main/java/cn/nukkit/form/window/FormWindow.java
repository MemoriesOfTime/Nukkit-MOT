package cn.nukkit.form.window;

import cn.nukkit.form.handler.FormResponseHandler;
import cn.nukkit.form.response.FormResponse;
import com.google.gson.Gson;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.List;

public abstract class FormWindow {

    protected static final Gson GSON = new Gson();
    protected final transient List<FormResponseHandler> handlers = new ObjectArrayList<>();
    protected transient boolean closed = false;

    public String getJSONData() {
        return FormWindow.GSON.toJson(this);
    }

    public abstract FormResponse getResponse();

    public abstract void setResponse(String data);

    public boolean wasClosed() {
        return closed;
    }

    public void addHandler(FormResponseHandler handler) {
        this.handlers.add(handler);
    }

    public List<FormResponseHandler> getHandlers() {
        return handlers;
    }
}
