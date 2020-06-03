package com.ctrip.framework.apollo.core.spi;

/**
 * 排序接口，用于排序的对象实现，比如在集合中优先级排序
 * 最小的ordered值有最高的优先级
 * <p>
 * {@code Ordered} is an interface that can be implemented by objects that
 * should be <em>orderable</em>, for example in a {@code Collection}.
 *
 * <p>The actual {@link #getOrder() order} can be interpreted as prioritization,
 * with the first object (with the lowest order value) having the highest
 * priority.
 *
 * @since 1.0.0
 */
public interface Ordered {
    /**
     * 最高优先级
     * Useful constant for the highest precedence value.
     *
     * @see java.lang.Integer#MIN_VALUE
     */
    int HIGHEST_PRECEDENCE = Integer.MIN_VALUE;

    /**
     * 最低优先级
     * Useful constant for the lowest precedence value.
     *
     * @see java.lang.Integer#MAX_VALUE
     */
    int LOWEST_PRECEDENCE = Integer.MAX_VALUE;


    /**
     * 获取对象的排序
     * <p>
     * Get the order value of this object.
     * <p>Higher values are interpreted as lower priority. As a consequence,
     * the object with the lowest value has the highest priority (somewhat
     * analogous to Servlet {@code load-on-startup} values).
     * <p>Same order values will result in arbitrary sort positions for the
     * affected objects.
     *
     * @return the order value
     * @see #HIGHEST_PRECEDENCE
     * @see #LOWEST_PRECEDENCE
     */
    int getOrder();
}
