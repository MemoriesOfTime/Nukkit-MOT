package cn.nukkit.form.window;

import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.form.element.ElementDialogButton;
import cn.nukkit.form.handler.FormDialogHandler;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class FormWindowDialog implements Dialog {

    protected static final Gson GSON = new Gson();

    private static long dialogId = 0;

    private String title = "";

    private String content = "";

    private String skinData = "{\"picker_offsets\":{\"scale\":[1.70,1.70,1.70],\"translate\":[0,20,0]},\"portrait_offsets\":{\"scale\":[1.750,1.750,1.750],\"translate\":[-7,50,0]},\"skin_list\":[{\"variant\":0},{\"variant\":1},{\"variant\":2},{\"variant\":3},{\"variant\":4},{\"variant\":5},{\"variant\":6},{\"variant\":7},{\"variant\":8},{\"variant\":9},{\"variant\":10},{\"variant\":11},{\"variant\":12},{\"variant\":13},{\"variant\":14},{\"variant\":15},{\"variant\":16},{\"variant\":17},{\"variant\":18},{\"variant\":19},{\"variant\":20},{\"variant\":21},{\"variant\":22},{\"variant\":23},{\"variant\":24},{\"variant\":25},{\"variant\":26},{\"variant\":27},{\"variant\":28},{\"variant\":29},{\"variant\":30},{\"variant\":31},{\"variant\":32},{\"variant\":33},{\"variant\":34}]}";

    //usually you shouldn't edit this
    //this value is used to be an identifier
    private String sceneName = String.valueOf(dialogId++);

    private List<ElementDialogButton> buttons;

    private Entity bindEntity;

    protected final transient List<FormDialogHandler> handlers = new ObjectArrayList<>();

    public FormWindowDialog(String title, String content, Entity bindEntity) {
        this(title, content,bindEntity, new ArrayList<>());
    }

    public FormWindowDialog(String title, String content, Entity bindEntity, List<ElementDialogButton> buttons) {
        this.title = title;
        this.content = content;
        this.buttons = buttons;
        this.bindEntity = bindEntity;
        if (this.bindEntity == null)
            throw new IllegalArgumentException("bindEntity cannot be null!");
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public List<ElementDialogButton> getButtons() {
        return buttons;
    }

    public void setButtons(@NotNull List<ElementDialogButton> buttons) {
        this.buttons = buttons;
    }

    public void addButton(String text) {
        this.addButton(new ElementDialogButton(text,text));
    }

    public void addButton(ElementDialogButton button) {
        this.buttons.add(button);
    }

    public long getEntityId() {
        return bindEntity.getId();
    }

    public Entity getBindEntity() {
        return bindEntity;
    }

    public void setBindEntity(Entity bindEntity) {
        this.bindEntity = bindEntity;
    }

    public String getSkinData(){
        return this.skinData;
    }

    public void setSkinData(String data){
        this.skinData = data;
    }

    public void addHandler(FormDialogHandler handler) {
        this.handlers.add(handler);
    }

    public List<FormDialogHandler> getHandlers() {
        return handlers;
    }

    public String getButtonJSONData() {
        return GSON.toJson(this.buttons);
    }

    public void setButtonJSONData(String json){
        var buttons = GSON.<List<ElementDialogButton>>fromJson(json, new TypeToken<List<ElementDialogButton>>(){}.getType());
        //Cannot be null
        if (buttons == null) buttons = new ArrayList<>();
        this.setButtons(buttons);
    }

    public String getSceneName() {
        return sceneName;
    }

    //请不要随意调用此方法，否则可能会导致潜在的bug
    protected void setSceneName(String sceneName) {
        this.sceneName = sceneName;
    }

    public void updateSceneName() {
        this.sceneName = String.valueOf(dialogId++);
    }

    @Override
    public void send(@NotNull Player player){
        player.showDialogWindow(this);
    }
}
