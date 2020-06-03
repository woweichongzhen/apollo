package com.ctrip.framework.apollo.spring.config;

import com.ctrip.framework.apollo.Config;
import com.ctrip.framework.apollo.ConfigService;
import com.ctrip.framework.apollo.build.ApolloInjector;
import com.ctrip.framework.apollo.spring.property.AutoUpdateConfigChangeListener;
import com.ctrip.framework.apollo.spring.util.SpringInjector;
import com.ctrip.framework.apollo.util.ConfigUtil;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.env.*;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * apollo属性源处理器，为了基于spring注解的应用
 * <p>
 * PropertySourcesProcessor之所以实现{@link BeanFactoryPostProcessor}
 * 而不是 {@link org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor}的原因是，
 * Spring的较低版本（例如3.1.1）不支持在 ImportBeanDefinitionRegistrar 中注册 BeanDefinitionRegistryPostProcessor 。
 * -{@link com.ctrip.framework.apollo.spring.annotation.ApolloConfigRegistrar}
 * <p>
 * Apollo Property Sources processor for Spring Annotation Based Application. <br /> <br />
 * <p>
 * The reason why PropertySourcesProcessor implements {@link BeanFactoryPostProcessor} instead of
 * {@link org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor} is that lower versions of
 * Spring (e.g. 3.1.1) doesn't support registering BeanDefinitionRegistryPostProcessor in ImportBeanDefinitionRegistrar
 * - {@link com.ctrip.framework.apollo.spring.annotation.ApolloConfigRegistrar}
 *
 * @author Jason Song(song_s@ctrip.com)
 */
public class PropertySourcesProcessor implements BeanFactoryPostProcessor, EnvironmentAware, PriorityOrdered {

    /**
     * 命名空间集合
     * key：order顺序
     * value：命名空间集合
     */
    private static final Multimap<Integer, String> NAMESPACE_NAMES = LinkedHashMultimap.create();

    /**
     * 自动更新初始化的bean工厂
     */
    private static final Set<BeanFactory> AUTO_UPDATE_INITIALIZED_BEAN_FACTORIES = Sets.newConcurrentHashSet();

    /**
     * 配置属性源工厂，包含多个配置属性源
     * <p>
     * 在 NAMESPACE_NAMES 中的每一个 Namespace ，
     * 都会创建成对应的 {@link ConfigPropertySource} 对象( 基于 Apollo Config 的 PropertySource 实现类 )，
     * 并添加到 environment 中。
     * <p>
     * 通过这样的方式，Spring 的 <property name="" value="" /> 和 @Value 注解，
     * 就可以从 environment 中，直接读取到对应的属性值
     */
    private final ConfigPropertySourceFactory configPropertySourceFactory =
            SpringInjector.getInstance(ConfigPropertySourceFactory.class);

    private final ConfigUtil configUtil = ApolloInjector.getInstance(ConfigUtil.class);

    /**
     * spring环境
     */
    private ConfigurableEnvironment environment;

    /**
     * 添加命名空间缓存
     * xml或注解配置时，都会调用此静态方法把命名空间添加进缓存中
     *
     * @param namespaces 命名空间集合
     * @param order      命名空间熟悉怒
     * @return true添加成功，false添加失败
     */
    public static boolean addNamespaces(Collection<String> namespaces, int order) {
        return NAMESPACE_NAMES.putAll(order, namespaces);
    }

    /**
     * 实现 BeanFactoryPostProcessor 接口
     * <p>
     * 可以在 spring 的 bean 创建之前，修改 bean 的定义属性。
     * <p>
     * 也就是说，Spring 允许 BeanFactoryPostProcessor 在容器实例化任何其它 bean 之前读取配置元数据，
     * 并可以根据需要进行修改，例如可以把 bean 的 scope 从 singleton 改为 prototype ，也可以把 property 的值给修改掉。
     * <p>
     * 可以同时配置多个BeanFactoryPostProcessor ，并通过设置 order 属性来控制各个BeanFactoryPostProcessor 的执行次序。
     * <p>
     * 注意：BeanFactoryPostProcessor 是在 spring 容器加载了 bean 的定义文件之后，在 bean 实例化之前执行的。
     *
     * @param beanFactory 可配置的list的bean工厂
     * @throws BeansException bean异常
     */
    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        // 初始化bean属性源
        initializePropertySources();
        // 初始化自动更新属性的特性
        initializeAutoUpdatePropertiesFeature(beanFactory);
    }

    /**
     * 初始化bean属性源
     * <p>
     * {@link PropertySourcesConstants#APOLLO_PROPERTY_SOURCE_NAME}
     */
    private void initializePropertySources() {
        // 如果已经包含该属性源，说明已经初始化过了
        if (environment.getPropertySources().contains(PropertySourcesConstants.APOLLO_PROPERTY_SOURCE_NAME)) {
            return;
        }

        // 组合的属性源
        CompositePropertySource composite =
                new CompositePropertySource(PropertySourcesConstants.APOLLO_PROPERTY_SOURCE_NAME);

        // 按order递增排序命名空间，order越小越靠前
        ImmutableSortedSet<Integer> orders = ImmutableSortedSet.copyOf(NAMESPACE_NAMES.keySet());

        for (int order : orders) {
            /*
             * 遍历同样等级的命名空间，获取到配置，并生成配置属性源（同时缓存到配置属性源工厂中），保存到组合的属性源中
             *
             * 通过配置服务-》配置管理器加载指定命名空间的配置 -》
             * 配置管理器 -》 获取配置工厂 -》 通过配置工厂包裹一层属性兼容的配置文件（转换yaml为属性）-》
             * 配置工厂创建配置 -> 从本地仓库获取 -》 从负载均衡备份的远端仓库获取 -》 一层层的仓库缓存，配置改变监听器注册 -》
             * 最后返回
             */
            for (String namespace : NAMESPACE_NAMES.get(order)) {
                Config config = ConfigService.getConfig(namespace);

                composite.addPropertySource(configPropertySourceFactory.getConfigPropertySource(namespace, config));
            }
        }

        // 全部生成完成后，清空命名空间缓存
        NAMESPACE_NAMES.clear();

        // add after the bootstrap property source or to the first
        // 如果包含apollo外部化配置启动属性源，添加到后面；如果没有添加到最前面
        if (environment.getPropertySources().contains(PropertySourcesConstants.APOLLO_BOOTSTRAP_PROPERTY_SOURCE_NAME)) {

            // 确保apollo初始化属性源一直在最前面（如果不是第一，移除掉再添加到头部）
            this.ensureBootstrapPropertyPrecedence(environment);

            // 把命名空间属性源插入到apollo初始化属性源的后面
            environment.getPropertySources().addAfter(
                    PropertySourcesConstants.APOLLO_BOOTSTRAP_PROPERTY_SOURCE_NAME, composite);
        } else {
            environment.getPropertySources().addFirst(composite);
        }
    }

    /**
     * 确保apollo初始化属性源一直在最前面
     *
     * @param environment 可配置的环境
     */
    private void ensureBootstrapPropertyPrecedence(ConfigurableEnvironment environment) {
        // 获取spring可变属性源
        MutablePropertySources propertySources = environment.getPropertySources();

        // 获取apollo初始化属性源
        PropertySource<?> bootstrapPropertySource =
                propertySources.get(PropertySourcesConstants.APOLLO_BOOTSTRAP_PROPERTY_SOURCE_NAME);

        // 如果该属性源不存在，或者该属性源优先级=0，已经是第一了，直接返回
        if (bootstrapPropertySource == null || propertySources.precedenceOf(bootstrapPropertySource) == 0) {
            return;
        }

        // 如果存在并且不是第一，先移除掉，再添加到头部
        propertySources.remove(PropertySourcesConstants.APOLLO_BOOTSTRAP_PROPERTY_SOURCE_NAME);
        propertySources.addFirst(bootstrapPropertySource);
    }

    /**
     * 初始化自动更新属性的特性
     *
     * @param beanFactory 可配置的list bean工厂
     */
    private void initializeAutoUpdatePropertiesFeature(ConfigurableListableBeanFactory beanFactory) {
        // 如果没有开启自动更新注入spring属性，或者添加自动更新bean工厂到集合失败，则返回
        if (!configUtil.isAutoUpdateInjectedSpringPropertiesEnabled() ||
                !AUTO_UPDATE_INITIALIZED_BEAN_FACTORIES.add(beanFactory)) {
            return;
        }

        // 创建自动更新配置改变监听器
        AutoUpdateConfigChangeListener autoUpdateConfigChangeListener =
                new AutoUpdateConfigChangeListener(environment, beanFactory);

        // 获取所有命名空间的配置属性源，遍历添加自动更新配置改变监听器
        List<ConfigPropertySource> configPropertySources = configPropertySourceFactory.getAllConfigPropertySources();
        for (ConfigPropertySource configPropertySource : configPropertySources) {
            configPropertySource.addChangeListener(autoUpdateConfigChangeListener);
        }
    }

    @Override
    public void setEnvironment(Environment environment) {
        // 设置应用环境
        // 由于所有已知环境（所有配置属性）都是从ConfigurableEnvironment派生的，因此足够安全进行投射
        this.environment = (ConfigurableEnvironment) environment;
    }

    @Override
    public int getOrder() {
        // 使此类尽可能早的被加载，拥有最高优先级
        return Ordered.HIGHEST_PRECEDENCE;
    }

    /**
     * 仅供测试使用，情况命名空间缓存，自动更新初始化bean工厂的缓存
     */
    static void reset() {
        NAMESPACE_NAMES.clear();
        AUTO_UPDATE_INITIALIZED_BEAN_FACTORIES.clear();
    }
}
