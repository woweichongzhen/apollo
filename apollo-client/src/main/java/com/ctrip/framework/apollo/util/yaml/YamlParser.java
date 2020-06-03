package com.ctrip.framework.apollo.util.yaml;

import com.ctrip.framework.apollo.build.ApolloInjector;
import com.ctrip.framework.apollo.core.utils.StringUtils;
import com.ctrip.framework.apollo.util.factory.PropertiesFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.parser.ParserException;

import java.util.*;

/**
 * yaml解析器
 * 自动apollo不直接依赖spring后，转换spring的 {@link org.springframework.beans.factory.config.YamlProcessor} 为自己实现
 * <p>
 * Transplanted from org.springframework.beans.factory.config.YamlProcessor since apollo can't depend on Spring directly
 *
 * @since 1.3.0
 */
public class YamlParser {

    private static final Logger logger = LoggerFactory.getLogger(YamlParser.class);

    /**
     * 属性工厂
     */
    private final PropertiesFactory propertiesFactory = ApolloInjector.getInstance(PropertiesFactory.class);

    /**
     * 转换yaml内容为属性
     */
    public Properties yamlToProperties(String yamlContent) {
        // 创建yaml实例
        Yaml yaml = createYaml();
        // 获取新属性实例
        final Properties result = propertiesFactory.getPropertiesInstance();
        process(new MatchCallback() {
            @Override
            public void process(Properties properties, Map<String, Object> map) {
                // 把处理好的属性放到结果中
                result.putAll(properties);
            }
        }, yaml, yamlContent);
        return result;
    }

    /**
     * 创建{@link Yaml}实例
     */
    private Yaml createYaml() {
        return new Yaml(new StrictMapAppenderConstructor());
    }

    /**
     * 处理yaml文件
     *
     * @param callback 处理yaml回调（整合属性结果）
     * @param yaml     yaml实例
     * @param content  属性中的yaml内容
     * @return true处理成功，false处理失败
     */
    private boolean process(MatchCallback callback, Yaml yaml, String content) {
        int count = 0;
        if (logger.isDebugEnabled()) {
            logger.debug("Loading from YAML: " + content);
        }
        // yaml加载所有内容，转换内容为map进行处理，处理后执行回调，合并到result属性文件中
        for (Object object : yaml.loadAll(content)) {
            if (object != null
                    && process(asMap(object), callback)) {
                count++;
            }
        }
        if (logger.isDebugEnabled()) {
            logger.debug("Loaded " + count + " document" + (count > 1 ? "s" : "") + " from YAML resource: " + content);
        }

        // 只要处理数量大于0，说明处理成功
        return (count > 0);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object object) {
        // YAML can have numbers as keys
        Map<String, Object> result = new LinkedHashMap<>();
        if (!(object instanceof Map)) {
            // A document can be a text literal
            result.put("document", object);
            return result;
        }

        Map<Object, Object> map = (Map<Object, Object>) object;
        for (Map.Entry<Object, Object> entry : map.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map) {
                value = asMap(value);
            }
            Object key = entry.getKey();
            if (key instanceof CharSequence) {
                result.put(key.toString(), value);
            } else {
                // It has to be a map key in this case
                result.put("[" + key.toString() + "]", value);
            }
        }
        return result;
    }

    /**
     * 处理属性map
     *
     * @param map      属性map
     * @param callback 合并回调
     * @return true处理成功
     */
    private boolean process(Map<String, Object> map, MatchCallback callback) {
        Properties properties = propertiesFactory.getPropertiesInstance();
        properties.putAll(getFlattenedMap(map));

        if (logger.isDebugEnabled()) {
            logger.debug("Merging document (no matchers set): " + map);
        }
        // 处理回调，即合并
        callback.process(properties, map);
        return true;
    }

    private Map<String, Object> getFlattenedMap(Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        buildFlattenedMap(result, source, null);
        return result;
    }

    private void buildFlattenedMap(Map<String, Object> result, Map<String, Object> source, String path) {
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = entry.getKey();
            if (!StringUtils.isBlank(path)) {
                if (key.startsWith("[")) {
                    key = path + key;
                } else {
                    key = path + '.' + key;
                }
            }
            Object value = entry.getValue();
            if (value instanceof String) {
                result.put(key, value);
            } else if (value instanceof Map) {
                // Need a compound key
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) value;
                buildFlattenedMap(result, map, key);
            } else if (value instanceof Collection) {
                // Need a compound key
                @SuppressWarnings("unchecked")
                Collection<Object> collection = (Collection<Object>) value;
                int count = 0;
                for (Object object : collection) {
                    buildFlattenedMap(result, Collections.singletonMap("[" + (count++) + "]", object), key);
                }
            } else {
                result.put(key, (value != null ? value.toString() : ""));
            }
        }
    }

    /**
     * 匹配回调接口
     */
    private interface MatchCallback {

        /**
         * 处理属性文件回调方法
         *
         * @param properties 属性
         * @param map        map
         */
        void process(Properties properties, Map<String, Object> map);
    }

    /**
     * 严厉的Map附加构造器
     */
    private static class StrictMapAppenderConstructor extends Constructor {

        StrictMapAppenderConstructor() {
            super();
        }

        @Override
        protected Map<Object, Object> constructMapping(MappingNode node) {
            try {
                // 构造节点映射
                return super.constructMapping(node);
            } catch (IllegalStateException ex) {
                throw new ParserException("while parsing MappingNode", node.getStartMark(), ex.getMessage(),
                        node.getEndMark());
            }
        }

        @Override
        protected Map<Object, Object> createDefaultMap() {
            // 创建默认map
            final Map<Object, Object> delegate = super.createDefaultMap();
            return new AbstractMap<Object, Object>() {
                @Override
                public Object put(Object key, Object value) {
                    // 如果委托包含同样的key，抛异常，否则通过委托缓存到map中
                    if (delegate.containsKey(key)) {
                        throw new IllegalStateException("Duplicate key: " + key);
                    }
                    return delegate.put(key, value);
                }

                @Override
                public Set<Entry<Object, Object>> entrySet() {
                    // 返回委托的entrySet
                    return delegate.entrySet();
                }
            };
        }
    }

}
