package cn.nukkit.inventory.request;

import cn.nukkit.network.protocol.types.inventory.itemstack.response.ItemStackResponseContainer;

import java.util.Collections;
import java.util.List;

public record ActionResponse(boolean success, List<ItemStackResponseContainer> containers) {

    private static final ActionResponse ERROR = new ActionResponse(false, Collections.emptyList());

    public static ActionResponse error() {
        return ERROR;
    }

    public static ActionResponse ok(List<ItemStackResponseContainer> containers) {
        return new ActionResponse(true, containers);
    }

    public static ActionResponse ok() {
        return new ActionResponse(true, Collections.emptyList());
    }
}
