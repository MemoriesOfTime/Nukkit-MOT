package cn.nukkit.metadata;

import cn.nukkit.plugin.Plugin;
import cn.nukkit.utils.PluginException;
import cn.nukkit.utils.ServerException;

import java.util.*;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public abstract class MetadataStore {

    private final Map<String, Map<Plugin, MetadataValue>> metadataMap = new HashMap<>();

    public synchronized void setMetadata(Object subject, String metadataKey, MetadataValue newMetadataValue) {
        if (newMetadataValue == null) {
            throw new ServerException("Value cannot be null");
        }
        Plugin owningPlugin = newMetadataValue.getOwningPlugin();
        if (owningPlugin == null) {
            throw new PluginException("Plugin cannot be null");
        }
        String key = this.disambiguate((Metadatable) subject, metadataKey);
        Map<Plugin, MetadataValue> entry = this.metadataMap.computeIfAbsent(key, k -> new WeakHashMap<>(1));
        entry.put(owningPlugin, newMetadataValue);
    }

    public synchronized List<MetadataValue> getMetadata(Object subject, String metadataKey) {
        String key = this.disambiguate((Metadatable) subject, metadataKey);
        if (this.metadataMap.containsKey(key)) {
            Collection values = ((Map) this.metadataMap.get(key)).values();
            return Collections.unmodifiableList(new ArrayList<>(values));
        }
        return Collections.emptyList();
    }

    public synchronized boolean hasMetadata(Object subject, String metadataKey) {
        return this.metadataMap.containsKey(this.disambiguate((Metadatable) subject, metadataKey));
    }

    public synchronized void removeMetadata(Object subject, String metadataKey, Plugin owningPlugin) {
        if (owningPlugin == null) {
            throw new PluginException("Plugin cannot be null");
        }
        String key = this.disambiguate((Metadatable) subject, metadataKey);
        Map entry = this.metadataMap.get(key);
        if (entry == null) {
            return;
        }
        entry.remove(owningPlugin);
        if (entry.isEmpty()) {
            this.metadataMap.remove(key);
        }
    }

    public void invalidateAll(Plugin owningPlugin) {
        if (owningPlugin == null) {
            throw new PluginException("Plugin cannot be null");
        }
        // 快照持锁、invalidate() 在锁外执行：它是插件可覆写的外来代码，持监视器调用存在死锁面
        // Snapshot under the monitor, invoke invalidate() outside it: it is foreign,
        // plugin-overridable code and must not run while holding this store's monitor
        List<MetadataValue> toInvalidate = new ArrayList<>();
        synchronized (this) {
            for (Map value : this.metadataMap.values()) {
                MetadataValue metadataValue = (MetadataValue) value.get(owningPlugin);
                if (metadataValue != null) {
                    toInvalidate.add(metadataValue);
                }
            }
        }
        for (MetadataValue metadataValue : toInvalidate) {
            metadataValue.invalidate();
        }
    }

    protected abstract String disambiguate(Metadatable subject, String metadataKey);
}
