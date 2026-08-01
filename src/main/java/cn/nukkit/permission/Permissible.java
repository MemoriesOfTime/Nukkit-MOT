package cn.nukkit.permission;

import cn.nukkit.plugin.Plugin;

import java.util.Map;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public interface Permissible extends ServerOperator {

    boolean isPermissionSet(String name);

    boolean isPermissionSet(Permission permission);

    boolean hasPermission(String name);

    boolean hasPermission(Permission permission);

    PermissionAttachment addAttachment(Plugin plugin);

    PermissionAttachment addAttachment(Plugin plugin, String name);

    PermissionAttachment addAttachment(Plugin plugin, String name, Boolean value);

    void removeAttachment(PermissionAttachment attachment);

    /**
     * 移除指定插件添加的全部权限附件（用于插件卸载时清理）。默认空实现，由 {@link PermissibleBase} 覆写。<br>
     * Removes all permission attachments owned by the given plugin on unload. Default no-op, overridden by {@link PermissibleBase}.
     *
     * @param plugin 要清理附件的插件。<br>The plugin whose attachments should be removed.
     */
    default void clearAttachments(Plugin plugin) {
    }

    void recalculatePermissions();

    Map<String, PermissionAttachmentInfo> getEffectivePermissions();
}
