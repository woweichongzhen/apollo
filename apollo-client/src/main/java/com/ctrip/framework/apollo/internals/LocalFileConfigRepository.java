package com.ctrip.framework.apollo.internals;

import com.ctrip.framework.apollo.build.ApolloInjector;
import com.ctrip.framework.apollo.core.ConfigConsts;
import com.ctrip.framework.apollo.core.utils.ClassLoaderUtil;
import com.ctrip.framework.apollo.enums.ConfigSourceType;
import com.ctrip.framework.apollo.exceptions.ApolloConfigException;
import com.ctrip.framework.apollo.tracer.Tracer;
import com.ctrip.framework.apollo.tracer.spi.Transaction;
import com.ctrip.framework.apollo.util.ConfigUtil;
import com.ctrip.framework.apollo.util.ExceptionUtil;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * 本地文件仓库
 *
 * @author Jason Song(song_s@ctrip.com)
 */
public class LocalFileConfigRepository extends AbstractConfigRepository implements RepositoryChangeListener {

    private static final Logger logger = LoggerFactory.getLogger(LocalFileConfigRepository.class);

    /**
     * 缓存子目录
     */
    private static final String CONFIG_DIR = "/config-cache";

    /**
     * 当前仓库的命名空间
     */
    private final String namespace;

    /**
     * 本地文件缓存目录
     */
    private File baseDir;

    /**
     * 配置工具
     */
    private final ConfigUtil configUtil;

    /**
     * 文件属性缓存
     */
    private volatile Properties fileProperties;

    /**
     * 负载均衡仓库
     */
    private volatile ConfigRepository upConfigRepository;

    /**
     * 属性来源类型
     */
    private volatile ConfigSourceType sourceType = ConfigSourceType.LOCAL;

    /**
     * @param namespace 命名空间
     */
    public LocalFileConfigRepository(String namespace) {
        this(namespace, null);
    }

    public LocalFileConfigRepository(String namespace, ConfigRepository upConfigRepository) {
        this.namespace = namespace;
        configUtil = ApolloInjector.getInstance(ConfigUtil.class);

        // 查找本地缓存目录，并设置
        this.setLocalCacheDir(this.findLocalCacheDir(), false);

        // 设置备份仓库，尝试从远程仓库第一次同步并设置本地缓存
        this.setUpstreamRepository(upConfigRepository);

        // 尝试第一次同步
        this.trySync();
    }

    /**
     * 设置本地缓存目录File
     *
     * @param baseDir         基本目录File
     * @param syncImmediately 是否立即尝试同步
     */
    void setLocalCacheDir(File baseDir, boolean syncImmediately) {
        this.baseDir = baseDir;
        this.checkLocalConfigCacheDir(this.baseDir);
        if (syncImmediately) {
            this.trySync();
        }
    }

    /**
     * 查找本地缓存目录
     *
     * @return 本地缓存目录file
     */
    private File findLocalCacheDir() {
        try {
            // 获取默认缓存目录
            String defaultCacheDir = configUtil.getDefaultLocalCacheDir();
            Path path = Paths.get(defaultCacheDir);
            // 如果目录不存在，创建
            if (!Files.exists(path)) {
                Files.createDirectories(path);
            }
            // 如果存在了并且可写，返回该目录File持有
            if (Files.exists(path) && Files.isWritable(path)) {
                return new File(defaultCacheDir, CONFIG_DIR);
            }
        } catch (Throwable ex) {
            //ignore
        }

        // 降级使用当前的类加载路径
        return new File(ClassLoaderUtil.getClassPath(), CONFIG_DIR);
    }

    @Override
    public Properties getConfig() {
        // 如果文件属性为空，执行一次同步
        if (fileProperties == null) {
            sync();
        }
        // 把文件属性结果加载到属性中，返回
        Properties result = propertiesFactory.getPropertiesInstance();
        result.putAll(fileProperties);
        return result;
    }

    @Override
    public void setUpstreamRepository(ConfigRepository upstreamConfigRepository) {
        if (upstreamConfigRepository == null) {
            return;
        }

        // 清除上个备份仓库的监听器
        if (upConfigRepository != null) {
            upConfigRepository.removeChangeListener(this);
        }
        upConfigRepository = upstreamConfigRepository;

        // 尝试从新远程仓库拉取配置并同步本地缓存
        trySyncFromUpstream();

        // 添加本地文件变化监听器
        upstreamConfigRepository.addChangeListener(this);
    }

    @Override
    public ConfigSourceType getSourceType() {
        return sourceType;
    }

    @Override
    public void onRepositoryChange(String namespace, Properties newProperties) {
        // 触发仓库改变，如果属性对象相同，直接返回
        if (newProperties.equals(fileProperties)) {
            return;
        }

        // 如果属性对象不同，更新文件属性
        Properties newFileProperties = propertiesFactory.getPropertiesInstance();
        newFileProperties.putAll(newProperties);

        // 同步方法，更新文件属性并持久化本地缓存
        updateFileProperties(newFileProperties, upConfigRepository.getSourceType());

        // 触发仓库改变
        this.fireRepositoryChange(namespace, newProperties);
    }

    @Override
    protected void sync() {
        // 立即从备份仓库（远程仓库）拉取配置并缓存，拉取成功直接返回
        boolean syncFromUpstreamResultSuccess = trySyncFromUpstream();

        if (syncFromUpstreamResultSuccess) {
            return;
        }

        Transaction transaction = Tracer.newTransaction("Apollo.ConfigService", "syncLocalConfig");
        Throwable exception = null;
        try {
            transaction.addData("Basedir", baseDir.getAbsolutePath());
            // 从远程仓库拉取失败，则从本地缓存文件获取属性，并设置来源类型为本地
            fileProperties = this.loadFromLocalCacheFile(baseDir, namespace);
            sourceType = ConfigSourceType.LOCAL;
            transaction.setStatus(Transaction.SUCCESS);
        } catch (Throwable ex) {
            Tracer.logEvent("ApolloConfigException", ExceptionUtil.getDetailMessage(ex));
            transaction.setStatus(ex);
            exception = ex;
            //ignore
        } finally {
            transaction.complete();
        }

        // 如果从远程仓库和本地缓存都没获取到，则为空
        if (fileProperties == null) {
            sourceType = ConfigSourceType.NONE;
            throw new ApolloConfigException(
                    "Load config from local config failed!", exception);
        }
    }

    /**
     * 尝试获取备份仓库（即远程仓库）
     *
     * @return 同步流
     */
    private boolean trySyncFromUpstream() {
        if (upConfigRepository == null) {
            return false;
        }
        try {
            // 从远程仓库拉取更新文件属性
            updateFileProperties(upConfigRepository.getConfig(), upConfigRepository.getSourceType());
            return true;
        } catch (Throwable ex) {
            Tracer.logError(ex);
            logger
                    .warn("Sync config from upstream repository {} failed, reason: {}", upConfigRepository.getClass(),
                            ExceptionUtil.getDetailMessage(ex));
        }
        return false;
    }

    /**
     * 同步方法，更新文件属性
     *
     * @param newProperties 新属性
     * @param sourceType    来源类型
     */
    private synchronized void updateFileProperties(Properties newProperties, ConfigSourceType sourceType) {
        this.sourceType = sourceType;
        // 一个对象，直接返回
        if (newProperties.equals(fileProperties)) {
            return;
        }
        // 缓存新属性
        this.fileProperties = newProperties;
        // 持久化本地缓存文件
        persistLocalCacheFile(baseDir, namespace);
    }

    /**
     * 从本地缓存文件获取属性
     *
     * @param baseDir   缓存目录
     * @param namespace 命名空间
     * @return 本地缓存文件属性
     */
    private Properties loadFromLocalCacheFile(File baseDir, String namespace) {
        Preconditions.checkNotNull(baseDir, "Basedir cannot be null");

        File file = assembleLocalCacheFile(baseDir, namespace);
        Properties properties;

        if (file.isFile() && file.canRead()) {

            try (InputStream in = new FileInputStream(file)) {
                // 从本地缓存文件加载属性
                properties = propertiesFactory.getPropertiesInstance();
                properties.load(in);
                logger.debug("Loading local config file {} successfully!", file.getAbsolutePath());
            } catch (IOException ex) {
                Tracer.logError(ex);
                throw new ApolloConfigException(
                        String.format("Loading config from local cache file %s failed", file.getAbsolutePath()), ex);
            }
            // ignore
        } else {
            throw new ApolloConfigException(
                    String.format("Cannot read from local cache file %s", file.getAbsolutePath()));
        }

        return properties;
    }

    /**
     * 持久化本地缓存文件
     *
     * @param baseDir   文件目录
     * @param namespace 命名空间
     */
    void persistLocalCacheFile(File baseDir, String namespace) {
        if (baseDir == null) {
            return;
        }

        // 获取本地缓存文件
        File file = assembleLocalCacheFile(baseDir, namespace);

        OutputStream out = null;

        Transaction transaction = Tracer.newTransaction("Apollo.ConfigService", "persistLocalConfigFile");
        transaction.addData("LocalConfigFile", file.getAbsolutePath());
        try {
            // 获取文件输出流，并将属性存储到文件中
            out = new FileOutputStream(file);
            fileProperties.store(out, "Persisted by DefaultConfig");
            transaction.setStatus(Transaction.SUCCESS);
        } catch (IOException ex) {
            ApolloConfigException exception = new ApolloConfigException(
                    String.format("Persist local cache file %s failed", file.getAbsolutePath()), ex);
            Tracer.logError(exception);
            transaction.setStatus(exception);
            logger.warn("Persist local cache file {} failed, reason: {}.",
                    file.getAbsolutePath(), ExceptionUtil.getDetailMessage(ex));
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException ex) {
                    //ignore
                }
            }
            transaction.complete();
        }
    }

    /**
     * 检查缓存基本目录
     *
     * @param baseDir 缓存基本目录
     */
    private void checkLocalConfigCacheDir(File baseDir) {
        // 存在直接返回
        if (baseDir.exists()) {
            return;
        }
        Transaction transaction = Tracer.newTransaction("Apollo.ConfigService", "createLocalConfigDir");
        transaction.addData("BaseDir", baseDir.getAbsolutePath());
        try {
            // 不存在尝试创建文件夹
            Files.createDirectory(baseDir.toPath());
            transaction.setStatus(Transaction.SUCCESS);
        } catch (IOException ex) {
            ApolloConfigException exception =
                    new ApolloConfigException(
                            String.format("Create local config directory %s failed", baseDir.getAbsolutePath()),
                            ex);
            Tracer.logError(exception);
            transaction.setStatus(exception);
            logger.warn(
                    "Unable to create local config cache directory {}, reason: {}. Will not able to cache config file.",
                    baseDir.getAbsolutePath(), ExceptionUtil.getDetailMessage(ex));
        } finally {
            transaction.complete();
        }
    }

    /**
     * 组装本地缓存文件File
     *
     * @param baseDir   缓存目录
     * @param namespace 命名空间
     * @return 缓存文件file
     */
    File assembleLocalCacheFile(File baseDir, String namespace) {
        String fileName = String.format(
                "%s.properties",
                Joiner.on(ConfigConsts.CLUSTER_NAMESPACE_SEPARATOR)
                        .join(configUtil.getAppId(), configUtil.getCluster(), namespace));
        return new File(baseDir, fileName);
    }
}
