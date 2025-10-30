package io.github.resilience4j.core;

import io.github.resilience4j.core.functions.Either;

import java.util.function.BiFunction;

/**
 * 双参数间隔函数接口 - 根据尝试次数和结果/异常计算等待间隔
 *
 * 作用说明：
 * 这是 {@link IntervalFunction} 的增强版本，不仅考虑重试次数，
 * 还可以根据上次调用的结果或异常来动态调整等待时间。
 *
 * 与 IntervalFunction 的区别：
 * <pre>
 * IntervalFunction:
 * - 输入：尝试次数（attempt）
 * - 输出：等待毫秒数
 * - 使用场景：简单的固定退避策略
 *
 * IntervalBiFunction（本接口）:
 * - 输入：尝试次数（attempt）+ Either&lt;Throwable, T&gt;（结果或异常）
 * - 输出：等待毫秒数
 * - 使用场景：根据错误类型或结果值动态调整退避策略
 * </pre>
 *
 * 参数说明：
 * 1. Integer attempt - 尝试次数
 *    - 从 1 开始，每次重试递增
 *    - 第一次调用：attempt = 1
 *    - 第二次重试：attempt = 2
 *    - 以此类推...
 *
 * 2. Either&lt;Throwable, T&gt; either - 上次调用的结果或异常
 *    - Left（异常情况）：包含抛出的 Throwable
 *    - Right（成功情况）：包含返回的结果值 T
 *    - 可以根据不同类型的异常或结果值返回不同的等待时间
 *
 * 3. 返回值 Long - 等待时间（毫秒）
 *    - 下次重试前需要等待的毫秒数
 *    - 返回 0 表示立即重试
 *
 * 使用场景：
 * 1. 根据异常类型调整等待时间
 *    - 网络超时：等待较长时间（如 5 秒）
 *    - 限流异常：等待较短时间（如 1 秒）
 *    - 业务异常：立即重试或不重试
 *
 * 2. 根据结果值调整策略
 *    - HTTP 429（限流）：等待 Retry-After 头指定的时间
 *    - HTTP 503（服务不可用）：指数退避
 *    - HTTP 500（服务器错误）：固定间隔
 *
 * 3. 动态退避策略
 *    - 根据服务器返回的建议等待时间
 *    - 根据系统负载动态调整
 *
 * 使用示例：
 * <pre>
 * // 根据异常类型动态调整等待时间
 * IntervalBiFunction&lt;String&gt; dynamicInterval = (attempt, either) -&gt; {
 *     if (either.isLeft()) {
 *         Throwable error = either.getLeft();
 *         if (error instanceof TimeoutException) {
 *             // 超时异常：使用指数退避，最多等待 30 秒
 *             return Math.min(1000L * (1L &lt;&lt; attempt), 30000L);
 *         } else if (error instanceof RateLimitException) {
 *             // 限流异常：固定等待 2 秒
 *             return 2000L;
 *         } else {
 *             // 其他异常：标准退避
 *             return 1000L * attempt;
 *         }
 *     } else {
 *         // 成功情况下不应该被调用，返回 0
 *         return 0L;
 *     }
 * };
 *
 * // 根据 HTTP 响应码调整等待时间
 * IntervalBiFunction&lt;HttpResponse&gt; httpInterval = (attempt, either) -&gt; {
 *     if (either.isRight()) {
 *         HttpResponse response = either.get();
 *         if (response.getStatusCode() == 429) {
 *             // 429 Too Many Requests：使用 Retry-After 头
 *             return response.getRetryAfter() * 1000L;
 *         } else if (response.getStatusCode() == 503) {
 *             // 503 Service Unavailable：指数退避
 *             return 1000L * (1L &lt;&lt; attempt);
 *         }
 *     }
 *     return 1000L * attempt; // 默认策略
 * };
 * </pre>
 *
 * Either 类型说明：
 * Either&lt;Throwable, T&gt; 是一个表示"二选一"的容器：
 * - isLeft()：检查是否为异常（Left 通常表示错误）
 * - getLeft()：获取异常对象
 * - isRight()：检查是否为结果值
 * - get()：获取结果值
 *
 * 设计模式：
 * - 策略模式：根据不同情况选择不同的等待策略
 * - 适配器模式：ofIntervalFunction() 将简单的 IntervalFunction 适配为 IntervalBiFunction
 *
 * 注意事项：
 * - 如果不需要根据结果/异常调整策略，使用 IntervalFunction 更简单
 * - 返回的等待时间应该合理，避免过长或过短
 * - 考虑设置最大等待时间，防止等待时间无限增长
 *
 * @param <T> 操作的返回值类型
 * @author Resilience4j 团队
 * @since 1.0.0
 * @see IntervalFunction
 * @see Either
 */
@FunctionalInterface
public interface IntervalBiFunction<T> extends BiFunction<Integer, Either<Throwable, T>, Long> {

    /**
     * 将 IntervalFunction 适配为 IntervalBiFunction
     *
     * 功能说明：
     * 这是一个适配器方法，将只依赖尝试次数的 IntervalFunction
     * 转换为 IntervalBiFunction。转换后的函数会忽略结果/异常参数。
     *
     * 使用场景：
     * - 当系统要求 IntervalBiFunction 但你只有 IntervalFunction 时
     * - 当不需要根据结果/异常调整策略时
     *
     * 示例：
     * <pre>
     * // 创建一个简单的指数退避函数
     * IntervalFunction exponential = IntervalFunction.ofExponentialBackoff(1000, 2.0);
     *
     * // 适配为 IntervalBiFunction
     * IntervalBiFunction&lt;String&gt; biFunction =
     *     IntervalBiFunction.ofIntervalFunction(exponential);
     *
     * // 使用时会忽略 either 参数
     * long waitTime = biFunction.apply(3, Either.right("success"));
     * // 相当于：exponential.apply(3)
     * </pre>
     *
     * 注意：
     * - 适配后的函数会完全忽略 either 参数
     * - 如果需要根据结果/异常调整策略，不应使用此方法
     *
     * @param <T> 结果类型
     * @param f   要适配的 IntervalFunction
     * @return 适配后的 IntervalBiFunction
     */
    static <T> IntervalBiFunction<T> ofIntervalFunction(IntervalFunction f) {
        return (attempt, either) -> f.apply(attempt);
    }
}
