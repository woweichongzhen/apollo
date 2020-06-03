package com.ctrip.framework.apollo.spring.property;

import com.google.common.base.Strings;
import com.google.common.collect.Sets;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanExpressionContext;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.config.Scope;
import org.springframework.util.StringUtils;

import java.util.Set;
import java.util.Stack;

/**
 * 占位符辅助处理工具类
 * <p>
 * Placeholder helper functions.
 */
public class PlaceholderHelper {

    private static final String PLACEHOLDER_PREFIX = "${";
    private static final String PLACEHOLDER_SUFFIX = "}";
    private static final String VALUE_SEPARATOR = ":";
    private static final String SIMPLE_PLACEHOLDER_PREFIX = "{";
    private static final String EXPRESSION_PREFIX = "#{";
    private static final String EXPRESSION_SUFFIX = "}";

    /**
     * 解析占位符属性
     * <p>
     * 比如：
     * "${somePropertyValue}" -> "the actual property value"
     * <p>
     * Resolve placeholder property values, e.g.
     * <br />
     * <br />
     * "${somePropertyValue}" -> "the actual property value"
     */
    public Object resolvePropertyValue(ConfigurableBeanFactory beanFactory, String beanName, String placeholder) {
        // 解析出占位符的String值
        String strVal = beanFactory.resolveEmbeddedValue(placeholder);

        // 如果包含该bean，调用合并后的bean定义，如果不包含，null
        BeanDefinition bd = (beanFactory.containsBean(beanName)
                ? beanFactory.getMergedBeanDefinition(beanName)
                : null);

        // 解析表达式比如  "#{systemProperties.myProp}"
        return this.evaluateBeanDefinitionString(beanFactory, strVal, bd);
    }

    /**
     * 解析表达式比如  "#{systemProperties.myProp}"
     *
     * @param beanFactory    bean工厂
     * @param value          表达式值
     * @param beanDefinition bean定义
     * @return 解析后的字符串值
     */
    private Object evaluateBeanDefinitionString(ConfigurableBeanFactory beanFactory, String value,
                                                BeanDefinition beanDefinition) {
        // 如果没有bean表达式解析器，直接不处理了，返回原值
        if (beanFactory.getBeanExpressionResolver() == null) {
            return value;
        }

        // 获取bean的作用域
        Scope scope = beanDefinition != null
                ? beanFactory.getRegisteredScope(beanDefinition.getScope())
                : null;

        // 获取表达式解析器，解析指定的bean表达式值
        return beanFactory.getBeanExpressionResolver().evaluate(value, new BeanExpressionContext(beanFactory, scope));
    }

    /**
     * 解析占位符中额外的key
     * 比如：
     * Extract keys from placeholder, e.g.
     * <ul>
     * <li>${some.key} => "some.key"</li>
     * <li>${some.key:${some.other.key:100}} => "some.key", "some.other.key"</li>
     * <li>${${some.key}} => "some.key"</li>
     * <li>${${some.key:other.key}} => "some.key"</li>
     * <li>${${some.key}:${another.key}} => "some.key", "another.key"</li>
     * <li>#{new java.text.SimpleDateFormat('${some.key}').parse('${another.key}')} => "some.key", "another.key"</li>
     * </ul>
     */
    public Set<String> extractPlaceholderKeys(String propertyString) {
        Set<String> placeholderKeys = Sets.newHashSet();

        if (Strings.isNullOrEmpty(propertyString) || (!isNormalizedPlaceholder(propertyString) && !isExpressionWithPlaceholder(propertyString))) {
            return placeholderKeys;
        }

        Stack<String> stack = new Stack<>();
        stack.push(propertyString);

        while (!stack.isEmpty()) {
            String strVal = stack.pop();
            int startIndex = strVal.indexOf(PLACEHOLDER_PREFIX);
            if (startIndex == -1) {
                placeholderKeys.add(strVal);
                continue;
            }
            int endIndex = findPlaceholderEndIndex(strVal, startIndex);
            if (endIndex == -1) {
                // invalid placeholder?
                continue;
            }

            String placeholderCandidate = strVal.substring(startIndex + PLACEHOLDER_PREFIX.length(), endIndex);

            // ${some.key:other.key}
            if (placeholderCandidate.startsWith(PLACEHOLDER_PREFIX)) {
                stack.push(placeholderCandidate);
            } else {
                // some.key:${some.other.key:100}
                int separatorIndex = placeholderCandidate.indexOf(VALUE_SEPARATOR);

                if (separatorIndex == -1) {
                    stack.push(placeholderCandidate);
                } else {
                    stack.push(placeholderCandidate.substring(0, separatorIndex));
                    String defaultValuePart =
                            normalizeToPlaceholder(placeholderCandidate.substring(separatorIndex + VALUE_SEPARATOR.length()));
                    if (!Strings.isNullOrEmpty(defaultValuePart)) {
                        stack.push(defaultValuePart);
                    }
                }
            }

            // has remaining part, e.g. ${a}.${b}
            if (endIndex + PLACEHOLDER_SUFFIX.length() < strVal.length() - 1) {
                String remainingPart = normalizeToPlaceholder(strVal.substring(endIndex + PLACEHOLDER_SUFFIX.length()));
                if (!Strings.isNullOrEmpty(remainingPart)) {
                    stack.push(remainingPart);
                }
            }
        }

        return placeholderKeys;
    }

    private boolean isNormalizedPlaceholder(String propertyString) {
        return propertyString.startsWith(PLACEHOLDER_PREFIX) && propertyString.endsWith(PLACEHOLDER_SUFFIX);
    }

    private boolean isExpressionWithPlaceholder(String propertyString) {
        return propertyString.startsWith(EXPRESSION_PREFIX) && propertyString.endsWith(EXPRESSION_SUFFIX)
                && propertyString.contains(PLACEHOLDER_PREFIX);
    }

    private String normalizeToPlaceholder(String strVal) {
        int startIndex = strVal.indexOf(PLACEHOLDER_PREFIX);
        if (startIndex == -1) {
            return null;
        }
        int endIndex = strVal.lastIndexOf(PLACEHOLDER_SUFFIX);
        if (endIndex == -1) {
            return null;
        }

        return strVal.substring(startIndex, endIndex + PLACEHOLDER_SUFFIX.length());
    }

    private int findPlaceholderEndIndex(CharSequence buf, int startIndex) {
        int index = startIndex + PLACEHOLDER_PREFIX.length();
        int withinNestedPlaceholder = 0;
        while (index < buf.length()) {
            if (StringUtils.substringMatch(buf, index, PLACEHOLDER_SUFFIX)) {
                if (withinNestedPlaceholder > 0) {
                    withinNestedPlaceholder--;
                    index = index + PLACEHOLDER_SUFFIX.length();
                } else {
                    return index;
                }
            } else if (StringUtils.substringMatch(buf, index, SIMPLE_PLACEHOLDER_PREFIX)) {
                withinNestedPlaceholder++;
                index = index + SIMPLE_PLACEHOLDER_PREFIX.length();
            } else {
                index++;
            }
        }
        return -1;
    }
}
