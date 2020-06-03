package com.ctrip.framework.apollo.core.utils;

import com.google.common.base.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.net.URLDecoder;

/**
 * 类加载器工具类
 *
 * @author Jason Song(song_s@ctrip.com)
 */
public class ClassLoaderUtil {

    private static final Logger logger = LoggerFactory.getLogger(ClassLoaderUtil.class);

    /**
     * 当前线程的类加载器
     */
    private static ClassLoader loader = Thread.currentThread().getContextClassLoader();

    private static String classPath = "";

    static {
        if (loader == null) {
            logger.warn("Using system class loader");
            loader = ClassLoader.getSystemClassLoader();
        }

        try {
            URL url = loader.getResource("");
            // get class path
            if (url != null) {
                classPath = url.getPath();
                classPath = URLDecoder.decode(classPath, "utf-8");
            }

            // 如果是jar包内的，则返回当前路径
            if (Strings.isNullOrEmpty(classPath) || classPath.contains(".jar!")) {
                classPath = System.getProperty("user.dir");
            }
        } catch (Throwable ex) {
            classPath = System.getProperty("user.dir");
            logger.warn("Failed to locate class path, fallback to user.dir: {}", classPath, ex);
        }
    }

    /**
     * 获取类加载器
     *
     * @return 累加载器
     */
    public static ClassLoader getLoader() {
        return loader;
    }

    /**
     * 获取类加载路径
     *
     * @return 类加载路径
     */
    public static String getClassPath() {
        return classPath;
    }

    public static boolean isClassPresent(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException ex) {
            return false;
        }
    }
}
