package com.ctrip.framework.apollo.spring.config;

import com.ctrip.framework.apollo.core.ConfigConsts;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.AbstractSingleBeanDefinitionParser;
import org.springframework.beans.factory.xml.NamespaceHandlerSupport;
import org.springframework.core.Ordered;
import org.w3c.dom.Element;

/**
 * apollo xml命名空间处理器
 *
 * @author Jason Song(song_s@ctrip.com)
 */
public class NamespaceHandler extends NamespaceHandlerSupport {

    /**
     * 命名空间逗号分割器
     */
    private static final Splitter NAMESPACE_SPLITTER = Splitter.on(",").omitEmptyStrings().trimResults();

    @Override
    public void init() {
        // 注册bean定义（apollo:config）解析器
        registerBeanDefinitionParser("config", new BeanParser());
    }

    /**
     * bean定义解析器
     */
    static class BeanParser extends AbstractSingleBeanDefinitionParser {

        @Override
        protected Class<?> getBeanClass(Element element) {
            // 返回bean对应的类，配置属性源处理器
            return ConfigPropertySourcesProcessor.class;
        }

        @Override
        protected boolean shouldGenerateId() {
            return true;
        }

        @Override
        protected void doParse(Element element, BeanDefinitionBuilder builder) {
            // 解析xml的 namespace 属性
            String namespaces = element.getAttribute("namespaces");
            // 如果为空，默认使用 application 命名空间
            if (Strings.isNullOrEmpty(namespaces)) {
                namespaces = ConfigConsts.NAMESPACE_APPLICATION;
            }

            // 解析xml的 order 属性，获取顺序属性，默认最低
            int order = Ordered.LOWEST_PRECEDENCE;
            String orderAttribute = element.getAttribute("order");

            if (!Strings.isNullOrEmpty(orderAttribute)) {
                try {
                    order = Integer.parseInt(orderAttribute);
                } catch (Throwable ex) {
                    throw new IllegalArgumentException(
                            String.format("Invalid order: %s for namespaces: %s", orderAttribute, namespaces));
                }
            }

            // 添加各个命名空间到属性源处理器中
            PropertySourcesProcessor.addNamespaces(NAMESPACE_SPLITTER.splitToList(namespaces), order);
        }
    }
}
