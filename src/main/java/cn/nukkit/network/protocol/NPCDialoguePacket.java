package cn.nukkit.network.protocol;

import org.jetbrains.annotations.NotNull;

public class NPCDialoguePacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.NPC_DIALOGUE_PACKET;

    private static final Action[] ACTIONS = Action.values();

    private long uniqueEntityId;
    private Action action = Action.OPEN;
    private String dialogue = "";
    private String sceneName = "";
    private String npcName = "";
    private String actionJson = "";

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    @Override
    public void decode() {
        uniqueEntityId = getLLong();
        action = ACTIONS[getVarInt()];
        dialogue = getString();
        sceneName = getString();
        npcName = getString();
        actionJson = getString();
    }

    @Override
    public void encode() {
        reset();
        putLLong(uniqueEntityId);
        putVarInt(action.ordinal());
        putString(dialogue);
        putString(sceneName);
        putString(npcName);
        putString(actionJson);
    }

    public long getUniqueEntityId() {
        return uniqueEntityId;
    }

    public void setUniqueEntityId(long uniqueEntityId) {
        this.uniqueEntityId = uniqueEntityId;
    }

    @NotNull
    public Action getAction() {
        return action;
    }

    public void setAction(@NotNull Action action) {
        this.action = action;
    }

    @NotNull
    public String getDialogue() {
        return dialogue;
    }

    public void setDialogue(@NotNull String dialogue) {
        this.dialogue = dialogue;
    }

    @NotNull
    public String getSceneName() {
        return sceneName;
    }

    public void setSceneName(@NotNull String sceneName) {
        this.sceneName = sceneName;
    }

    @NotNull
    public String getNpcName() {
        return npcName;
    }

    public void setNpcName(@NotNull String npcName) {
        this.npcName = npcName;
    }

    @NotNull
    public String getActionJson() {
        return actionJson;
    }

    public void setActionJson(@NotNull String actionJson) {
        this.actionJson = actionJson;
    }

    public enum Action {
        OPEN,
        CLOSE
    }
}
