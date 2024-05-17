package cn.nukkit.network.protocol;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.Getter;
import lombok.ToString;

import java.util.List;

@Getter
@ToString
public class UnlockedRecipesPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.UNLOCKED_RECIPES_PACKET;
    public final List<String> unlockedRecipes = new ObjectArrayList<>();
    public ActionType action;

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    @Override
    public void decode() {
        if (this.protocol >= ProtocolInfo.v1_20_0_23) {
            this.action = ActionType.values()[this.getLInt()];
        } else {
            this.action = this.getBoolean() ? ActionType.NEWLY_UNLOCKED : ActionType.INITIALLY_UNLOCKED;
        }
        int count = (int) this.getUnsignedVarInt();
        for (int i = 0; i < count; i++) {
            this.unlockedRecipes.add(this.getString());
        }
    }

    @Override
    public void encode() {
        this.reset();
        if (this.protocol >= ProtocolInfo.v1_20_0_23) {
            this.putLInt(this.action.ordinal());
        } else {
            this.putBoolean(this.action == ActionType.NEWLY_UNLOCKED);
        }
        this.putUnsignedVarInt(this.unlockedRecipes.size());
        for (String recipe : this.unlockedRecipes) {
            this.putString(recipe);
        }
    }

    public enum ActionType {
        EMPTY,
        INITIALLY_UNLOCKED,
        NEWLY_UNLOCKED,
        REMOVE_UNLOCKED,
        REMOVE_ALL
    }
}
