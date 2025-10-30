package io.github.resilience4j.core;

import java.time.Duration;
import java.util.function.Function;
import java.util.stream.LongStream;

import static io.github.resilience4j.core.IntervalFunctionCompanion.*;
import static java.util.Objects.requireNonNull;

/**
 * 间隔函数接口 - 用于计算重试等待间隔
 *
 * 作用说明：
 * 这是一个函数式接口，用于根据尝试次数计算下一次重试应该等待的时间间隔。
 * 输入参数是尝试次数（从 1 开始），输出参数是等待间隔（毫秒）。
 *
 * 设计理念：
 * - 策略模式：将间隔计算策略抽象为可插拔的函数
 * - 函数式编程：作为 Function<Integer, Long> 的特化版本
 * - 灵活配置：提供多种内置策略（固定、指数退避、随机等）
 *
 * 主要应用场景：
 * 1. 重试机制：计算两次重试之间的等待时间
 * 2. 断路器：计算从打开到半开状态的等待时间
 * 3. 限流器：计算被拒绝请求的重试间隔
 * 4. 轮询：计算轮询间隔
 *
 * 内置策略类型：
 *
 * 1. 固定间隔（of）：
 *    - 每次重试都等待相同的时间
 *    - 示例：500ms -> 500ms -> 500ms -> ...
 *    - 适用：快速失败的场景，网络抖动
 *
 * 2. 指数退避（ofExponentialBackoff）：
 *    - 每次重试时间呈指数增长
 *    - 示例：500ms -> 750ms -> 1125ms -> 1687ms -> ...（倍数1.5）
 *    - 适用：防止雪崩，给后端恢复时间
 *
 * 3. 随机间隔（ofRandomized）：
 *    - 在基础间隔上添加随机抖动
 *    - 示例：500ms ± 250ms（随机因子0.5）
 *    - 适用：避免惊群效应，分散重试压力
 *
 * 4. 指数随机退避（ofExponentialRandomBackoff）：
 *    - 结合指数退避和随机抖动
 *    - 示例：(500ms -> 750ms -> ...) 每个值都加随机抖动
 *    - 适用：生产环境最推荐的策略
 *
 * 5. 自定义退避（of(interval, backoffFunction)）：
 *    - 完全自定义退避逻辑
 *    - 可以实现任何复杂的间隔计算策略
 *
 * 使用示例：
 * <pre>
 * // 1. 固定间隔 - 每次等待1秒
 * IntervalFunction fixed = IntervalFunction.of(Duration.ofSeconds(1));
 * System.out.println("第1次重试等待: " + fixed.apply(1) + "ms"); // 1000ms
 * System.out.println("第2次重试等待: " + fixed.apply(2) + "ms"); // 1000ms
 *
 * // 2. 指数退避 - 初始500ms，倍数1.5
 * IntervalFunction exponential = IntervalFunction.ofExponentialBackoff(500, 1.5);
 * System.out.println("第1次重试: " + exponential.apply(1) + "ms"); // 500ms
 * System.out.println("第2次重试: " + exponential.apply(2) + "ms"); // 750ms
 * System.out.println("第3次重试: " + exponential.apply(3) + "ms"); // 1125ms
 *
 * // 3. 带上限的指数退避 - 最多等待10秒
 * IntervalFunction capped = IntervalFunction.ofExponentialBackoff(
 *     500, 1.5, Duration.ofSeconds(10).toMillis()
 * );
 *
 * // 4. 指数随机退避（推荐用于生产环境）
 * IntervalFunction production = IntervalFunction.ofExponentialRandomBackoff(
 *     Duration.ofMillis(500),  // 初始间隔
 *     2.0,                     // 指数倍数
 *     0.5,                     // 随机因子
 *     Duration.ofSeconds(30)   // 最大间隔
 * );
 *
 * // 5. 在 Retry 配置中使用
 * RetryConfig config = RetryConfig.custom()
 *     .maxAttempts(3)
 *     .intervalFunction(IntervalFunction.ofExponentialRandomBackoff())
 *     .build();
 * </pre>
 *
 * 退避策略对比：
 * <pre>
 * 尝试次数 | 固定(500ms) | 指数(500,1.5) | 指数+随机(500,1.5,0.5)
 * --------|------------|--------------|--------------------
 * 1       | 500ms      | 500ms        | 250-750ms
 * 2       | 500ms      | 750ms        | 375-1125ms
 * 3       | 500ms      | 1125ms       | 562-1687ms
 * 4       | 500ms      | 1687ms       | 843-2531ms
 * 5       | 500ms      | 2531ms       | 1265-3796ms
 * </pre>
 *
 * 最佳实践：
 * 1. 生产环境推荐使用 ofExponentialRandomBackoff，避免惊群
 * 2. 设置合理的最大间隔，避免等待时间过长
 * 3. 根据场景调整初始间隔和倍数
 * 4. 快速失败场景可以使用固定间隔
 *
 * @author Resilience4j团队
 * @since 0.1.0
 */
@FunctionalInterface
public interface IntervalFunction extends Function<Integer, Long> {

    /**
     * 默认初始间隔：500 毫秒
     * 大多数场景下的合理起始值
     */
    long DEFAULT_INITIAL_INTERVAL = 500;

    /**
     * 默认指数倍数：1.5
     * 既能快速增长，又不会过于激进
     */
    double DEFAULT_MULTIPLIER = 1.5;

    /**
     * 默认随机因子：0.5
     * 表示在基础值的 ±50% 范围内随机
     */
    double DEFAULT_RANDOMIZATION_FACTOR = 0.5;

    /**
     * 创建使用默认配置的间隔函数
     *
     * 功能说明：
     * 返回一个固定间隔的函数，使用默认间隔 500 毫秒。
     * 这是最简单的配置方式，适合快速开始。
     *
     * 等价于：
     * IntervalFunction.of(500)
     *
     * 示例：
     * <pre>
     * IntervalFunction func = IntervalFunction.ofDefaults();
     * System.out.println(func.apply(1)); // 输出：500
     * System.out.println(func.apply(2)); // 输出：500
     * System.out.println(func.apply(3)); // 输出：500
     * </pre>
     *
     * @return 返回固定间隔 500ms 的函数
     */
    static IntervalFunction ofDefaults() {
        return of(DEFAULT_INITIAL_INTERVAL);
    }

    /**
     * 创建自定义退避策略的间隔函数
     *
     * 功能说明：
     * 这是最灵活的方法，允许完全自定义退避逻辑。
     * backoffFunction 接收当前间隔，返回下一次的间隔。
     *
     * 实现原理：
     * 使用流式计算，从初始间隔开始，每次应用 backoffFunction 得到下一个值，
     * 然后跳过 (attempt-1) 个值，得到第 attempt 次尝试的间隔。
     *
     * 使用场景：
     * - 实现自定义的增长策略（如线性、对数、斐波那契等）
     * - 实现更复杂的业务逻辑（如根据时间段调整间隔）
     *
     * 示例：
     * <pre>
     * // 线性退避：每次增加1秒
     * IntervalFunction linear = IntervalFunction.of(1000, x -> x + 1000);
     * // 第1次: 1000ms, 第2次: 2000ms, 第3次: 3000ms
     *
     * // 平方退避：每次乘以当前值
     * IntervalFunction square = IntervalFunction.of(100, x -> x * x);
     * // 第1次: 100ms, 第2次: 10000ms, 第3次: 100000000ms
     *
     * // 有上限的指数退避
     * IntervalFunction capped = IntervalFunction.of(500, x -> Math.min(x * 2, 30000));
     * </pre>
     *
     * @param intervalMillis   初始间隔（毫秒），必须 > 0
     * @param backoffFunction  退避函数，接收当前间隔，返回下一次间隔
     * @return 自定义退避策略的间隔函数
     * @throws IllegalArgumentException 如果 intervalMillis < 1
     * @throws NullPointerException     如果 backoffFunction 为 null
     */
    static IntervalFunction of(long intervalMillis, Function<Long, Long> backoffFunction) {
        // 验证间隔必须大于0
        checkInterval(intervalMillis);
        // 验证退避函数不能为null
        requireNonNull(backoffFunction);

        return (attempt) -> {
            // 验证尝试次数必须从1开始
            checkAttempt(attempt);
            // 使用流式计算：从初始值开始，重复应用退避函数，跳过前 (attempt-1) 个值
            return LongStream.iterate(intervalMillis, n -> backoffFunction.apply(n))
                .skip(attempt - 1L)
                .findFirst()
                .getAsLong();
        };
    }

    /**
     * 创建自定义退避策略的间隔函数（Duration 版本）
     *
     * 功能说明：
     * 与 of(long, Function) 相同，但使用 Duration 指定初始间隔。
     *
     * @param interval        初始间隔（Duration对象）
     * @param backoffFunction 退避函数
     * @return 自定义退避策略的间隔函数
     */
    static IntervalFunction of(Duration interval, Function<Long, Long> backoffFunction) {
        return of(interval.toMillis(), backoffFunction);
    }


    /**
     * 创建固定间隔的函数（毫秒）
     *
     * 功能说明：
     * 每次重试都返回相同的固定间隔。
     * 这是最简单的重试策略，适合快速失败或已知恢复时间的场景。
     *
     * 优点：
     * - 简单可预测
     * - 快速恢复（如果故障是暂时的）
     *
     * 缺点：
     * - 可能导致惊群效应（多个客户端同时重试）
     * - 无法给后端足够的恢复时间
     *
     * 示例：
     * <pre>
     * // 每次等待2秒
     * IntervalFunction fixed = IntervalFunction.of(2000);
     * System.out.println("第1次: " + fixed.apply(1) + "ms"); // 2000ms
     * System.out.println("第2次: " + fixed.apply(2) + "ms"); // 2000ms
     * System.out.println("第3次: " + fixed.apply(3) + "ms"); // 2000ms
     * </pre>
     *
     * @param intervalMillis 固定的间隔时间（毫秒），必须 > 0
     * @return 固定间隔的函数
     * @throws IllegalArgumentException 如果 intervalMillis < 1
     */
    static IntervalFunction of(long intervalMillis) {
        // 验证间隔必须大于0
        checkInterval(intervalMillis);
        return attempt -> {
            // 验证尝试次数必须从1开始
            checkAttempt(attempt);
            // 始终返回固定的间隔
            return intervalMillis;
        };
    }

    /**
     * 创建固定间隔的函数（Duration 版本）
     *
     * 功能说明：
     * 与 of(long) 相同，但使用 Duration 对象指定间隔。
     *
     * 示例：
     * <pre>
     * IntervalFunction fixed = IntervalFunction.of(Duration.ofSeconds(2));
     * </pre>
     *
     * @param interval 固定的间隔时间（Duration对象）
     * @return 固定间隔的函数
     */
    static IntervalFunction of(Duration interval) {
        return of(interval.toMillis());
    }


    static IntervalFunction ofRandomized(long intervalMillis, double randomizationFactor) {
        checkInterval(intervalMillis);
        checkRandomizationFactor(randomizationFactor);
        return attempt -> {
            checkAttempt(attempt);
            return (long) randomize(intervalMillis, randomizationFactor);
        };
    }

    static IntervalFunction ofRandomized(Duration interval, double randomizationFactor) {
        return ofRandomized(interval.toMillis(), randomizationFactor);
    }

    static IntervalFunction ofRandomized(long intervalMillis) {
        return ofRandomized(intervalMillis, DEFAULT_RANDOMIZATION_FACTOR);
    }

    static IntervalFunction ofRandomized(Duration interval) {
        return ofRandomized(interval.toMillis(), DEFAULT_RANDOMIZATION_FACTOR);
    }

    static IntervalFunction ofRandomized() {
        return ofRandomized(DEFAULT_INITIAL_INTERVAL, DEFAULT_RANDOMIZATION_FACTOR);
    }

    /**
     * 创建带上限的指数退避函数
     *
     * 功能说明：
     * 创建指数退避策略，但设置最大间隔上限。
     * 当计算出的间隔超过上限时，使用上限值。
     *
     * 为什么需要上限？
     * - 防止等待时间过长，影响用户体验
     * - 及时发现持续性故障，触发告警
     * - 控制总重试时间在可接受范围内
     *
     * 计算公式：
     * interval(n) = min(initialInterval * (multiplier ^ (n-1)), maxInterval)
     *
     * 示例：
     * <pre>
     * // 初始500ms，倍数2，最大10秒
     * IntervalFunction func = IntervalFunction.ofExponentialBackoff(500, 2.0, 10000);
     * System.out.println("第1次: " + func.apply(1)); // 500ms
     * System.out.println("第2次: " + func.apply(2)); // 1000ms
     * System.out.println("第3次: " + func.apply(3)); // 2000ms
     * System.out.println("第4次: " + func.apply(4)); // 4000ms
     * System.out.println("第5次: " + func.apply(5)); // 8000ms
     * System.out.println("第6次: " + func.apply(6)); // 10000ms (达到上限)
     * System.out.println("第7次: " + func.apply(7)); // 10000ms (保持上限)
     * </pre>
     *
     * @param initialIntervalMillis 初始间隔（毫秒）
     * @param multiplier            指数倍数（如 1.5, 2.0）
     * @param maxIntervalMillis     最大间隔上限（毫秒）
     * @return 带上限的指数退避函数
     */
    static IntervalFunction ofExponentialBackoff(long initialIntervalMillis, double multiplier, long maxIntervalMillis) {
        checkInterval(maxIntervalMillis);
        return attempt -> {
            checkAttempt(attempt);
            // 先计算指数退避的间隔
            final long interval = ofExponentialBackoff(initialIntervalMillis, multiplier)
                .apply(attempt);
            // 取计算值和上限的最小值
            return Math.min(interval, maxIntervalMillis);
        };
    }

    /**
     * 创建带上限的指数退避函数（Duration 版本）
     */
    static IntervalFunction ofExponentialBackoff(Duration initialInterval, double multiplier, Duration maxInterval) {
        return ofExponentialBackoff(initialInterval.toMillis(), multiplier, maxInterval.toMillis());
    }

    /**
     * 创建指数退避函数
     *
     * 功能说明：
     * 创建标准的指数退避策略，每次间隔是上次的 multiplier 倍。
     *
     * 计算公式：
     * interval(n) = initialInterval * (multiplier ^ (n-1))
     *
     * 常用倍数：
     * - 1.5：温和增长，适合大部分场景
     * - 2.0：快速增长，适合需要快速拉大间隔的场景
     * - 1.2：缓慢增长，适合延迟敏感的场景
     *
     * 示例：
     * <pre>
     * // 初始1秒，倍数1.5
     * IntervalFunction func = IntervalFunction.ofExponentialBackoff(1000, 1.5);
     * System.out.println("第1次: " + func.apply(1)); // 1000ms
     * System.out.println("第2次: " + func.apply(2)); // 1500ms
     * System.out.println("第3次: " + func.apply(3)); // 2250ms
     * System.out.println("第4次: " + func.apply(4)); // 3375ms
     * System.out.println("第5次: " + func.apply(5)); // 5062ms
     * </pre>
     *
     * @param initialIntervalMillis 初始间隔（毫秒）
     * @param multiplier            指数倍数
     * @return 指数退避函数
     */
    static IntervalFunction ofExponentialBackoff(long initialIntervalMillis, double multiplier) {
        // 实现：使用自定义退避函数，每次乘以倍数
        return of(initialIntervalMillis, x -> (long) (x * multiplier));
    }

    /**
     * 创建指数退避函数（Duration 版本）
     */
    static IntervalFunction ofExponentialBackoff(Duration initialInterval, double multiplier) {
        return ofExponentialBackoff(initialInterval.toMillis(), multiplier);
    }

    /**
     * 创建指数退避函数（使用默认倍数 1.5）
     */
    static IntervalFunction ofExponentialBackoff(long initialIntervalMillis) {
        return ofExponentialBackoff(initialIntervalMillis, DEFAULT_MULTIPLIER);
    }

    /**
     * 创建指数退避函数（Duration 版本，使用默认倍数 1.5）
     */
    static IntervalFunction ofExponentialBackoff(Duration initialInterval) {
        return ofExponentialBackoff(initialInterval.toMillis(), DEFAULT_MULTIPLIER);
    }

    /**
     * 创建指数退避函数（使用所有默认值）
     *
     * 默认配置：
     * - 初始间隔：500ms
     * - 倍数：1.5
     *
     * 这是快速开始使用指数退避的便捷方法。
     */
    static IntervalFunction ofExponentialBackoff() {
        return ofExponentialBackoff(DEFAULT_INITIAL_INTERVAL, DEFAULT_MULTIPLIER);
    }

    static IntervalFunction ofExponentialRandomBackoff(
        long initialIntervalMillis,
        double multiplier,
        double randomizationFactor,
        long maxIntervalMillis
    ) {
        checkInterval(maxIntervalMillis);
        checkRandomizationFactor(randomizationFactor);
        return attempt -> {
            checkAttempt(attempt);
            final long interval = ofExponentialRandomBackoff(initialIntervalMillis, multiplier, randomizationFactor)
                .apply(attempt);
            return Math.min(interval, maxIntervalMillis);
        };
    }

    static IntervalFunction ofExponentialRandomBackoff(
        long initialIntervalMillis,
        double multiplier,
        double randomizationFactor
    ) {
        checkInterval(initialIntervalMillis);
        checkRandomizationFactor(randomizationFactor);
        return attempt -> {
            checkAttempt(attempt);
            final long interval = of(initialIntervalMillis, x -> (long) (x * multiplier))
                .apply(attempt);
            return (long) randomize(interval, randomizationFactor);
        };
    }

    static IntervalFunction ofExponentialRandomBackoff(
        Duration initialInterval,
        double multiplier,
        double randomizationFactor,
        Duration maxInterval
    ) {
        return ofExponentialRandomBackoff(initialInterval.toMillis(), multiplier,
            randomizationFactor, maxInterval.toMillis());
    }

    static IntervalFunction ofExponentialRandomBackoff(
        Duration initialInterval,
        double multiplier,
        double randomizationFactor
    ) {
        return ofExponentialRandomBackoff(initialInterval.toMillis(), multiplier,
            randomizationFactor);
    }

    static IntervalFunction ofExponentialRandomBackoff(
        long initialIntervalMillis,
        double multiplier,
        long maxIntervalMillis
    ) {
        return ofExponentialRandomBackoff(initialIntervalMillis, multiplier,
            DEFAULT_RANDOMIZATION_FACTOR, maxIntervalMillis);
    }

    static IntervalFunction ofExponentialRandomBackoff(
        long initialIntervalMillis,
        double multiplier
    ) {
        return ofExponentialRandomBackoff(initialIntervalMillis, multiplier,
            DEFAULT_RANDOMIZATION_FACTOR);
    }

    static IntervalFunction ofExponentialRandomBackoff(
        Duration initialInterval,
        double multiplier,
        Duration maxInterval
    ) {
        return ofExponentialRandomBackoff(initialInterval.toMillis(), multiplier,
            DEFAULT_RANDOMIZATION_FACTOR, maxInterval.toMillis());
    }

    static IntervalFunction ofExponentialRandomBackoff(
        Duration initialInterval,
        double multiplier
    ) {
        return ofExponentialRandomBackoff(initialInterval.toMillis(), multiplier,
            DEFAULT_RANDOMIZATION_FACTOR);
    }

    static IntervalFunction ofExponentialRandomBackoff(
        long initialIntervalMillis
    ) {
        return ofExponentialRandomBackoff(initialIntervalMillis, DEFAULT_MULTIPLIER);
    }

    static IntervalFunction ofExponentialRandomBackoff(
        Duration initialInterval
    ) {
        return ofExponentialRandomBackoff(initialInterval.toMillis(), DEFAULT_MULTIPLIER);
    }

    static IntervalFunction ofExponentialRandomBackoff() {
        return ofExponentialRandomBackoff(DEFAULT_INITIAL_INTERVAL, DEFAULT_MULTIPLIER,
            DEFAULT_RANDOMIZATION_FACTOR);
    }

}

final class IntervalFunctionCompanion {

    private IntervalFunctionCompanion() {
    }

    @SuppressWarnings("squid:S2245") // this is not security-sensitive code
    static double randomize(final double current, final double randomizationFactor) {
        final double delta = randomizationFactor * current;
        final double min = current - delta;
        final double max = current + delta;
        final double randomizedValue = min + (Math.random() * (max - min + 1));

        return Math.max(1.0, randomizedValue);
    }

    static void checkInterval(long intervalMillis) {
        if (intervalMillis < 1) {
            throw new IllegalArgumentException(
                "Illegal argument interval: " + intervalMillis + " millis is less than 1");
        }
    }

    static void checkRandomizationFactor(double randomizationFactor) {
        if (randomizationFactor < 0.0 || randomizationFactor > 1.0) {
            throw new IllegalArgumentException(
                "Illegal argument randomizationFactor: " + randomizationFactor);
        }
    }

    static void checkAttempt(long attempt) {
        if (attempt < 1) {
            throw new IllegalArgumentException("Illegal argument attempt: " + attempt);
        }
    }
}
