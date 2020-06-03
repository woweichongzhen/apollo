package com.ctrip.framework.apollo.spring.boot;

import com.ctrip.framework.apollo.spring.config.ConfigPropertySourcesProcessor;
import com.ctrip.framework.apollo.spring.config.PropertySourcesConstants;
import com.ctrip.framework.apollo.spring.config.PropertySourcesProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * apollo自动配置，如果配置了 apollo.bootstrap.enabled = true ，并且未加载属性源处理器bean
 * 则会调用此配置类创建 配置 xml 属性源处理器
 */
@Configuration
@ConditionalOnProperty(PropertySourcesConstants.APOLLO_BOOTSTRAP_ENABLED)
@ConditionalOnMissingBean(PropertySourcesProcessor.class)
public class ApolloAutoConfiguration {

  @Bean
  public ConfigPropertySourcesProcessor configPropertySourcesProcessor() {
    return new ConfigPropertySourcesProcessor();
  }
}
