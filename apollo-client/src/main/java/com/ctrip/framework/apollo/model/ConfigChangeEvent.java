package com.ctrip.framework.apollo.model;

import java.util.Map;
import java.util.Set;

/**
 * 当命名空间的配置改变时，发生的时间
 * A change event when a namespace's config is changed.
 *
 * @author Jason Song(song_s@ctrip.com)
 */
public class ConfigChangeEvent {

    /**
     * 改变的命名空间
     */
    private final String namespace;

    /**
     * 某个key的配置改变
     */
    private final Map<String, ConfigChange> changes;

    /**
     * Constructor.
     *
     * @param namespace the namespace of this change
     * @param changes   the actual changes
     */
    public ConfigChangeEvent(String namespace,
                             Map<String, ConfigChange> changes) {
        this.namespace = namespace;
        this.changes = changes;
    }

    /**
     * Get the keys changed.
     *
     * @return the list of the keys
     */
    public Set<String> changedKeys() {
        return changes.keySet();
    }

    /**
     * Get a specific change instance for the key specified.
     *
     * @param key the changed key
     * @return the change instance
     */
    public ConfigChange getChange(String key) {
        return changes.get(key);
    }

    /**
     * Check whether the specified key is changed
     *
     * @param key the key
     * @return true if the key is changed, false otherwise.
     */
    public boolean isChanged(String key) {
        return changes.containsKey(key);
    }

    /**
     * Get the namespace of this change event.
     *
     * @return the namespace
     */
    public String getNamespace() {
        return namespace;
    }
}
