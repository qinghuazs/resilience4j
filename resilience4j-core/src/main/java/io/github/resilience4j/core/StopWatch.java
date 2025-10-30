/*
 *
 *  Copyright 2017: Robert Winkler
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 */
package io.github.resilience4j.core;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * 秒表工具类 - 简单的计时器，用于测量操作的执行时长
 *
 * 作用说明：
 * 提供简单易用的计时功能，测量代码块或方法的执行时间。
 * 基于 Java 8 的 java.time API，提供纳秒级精度。
 *
 * 设计理念：
 * - 简单性：只提供 start() 和 stop() 两个方法，使用直观
 * - 不可变性：startTime 在创建时确定，不可修改
 * - 可测试性：通过 Clock 参数化，便于单元测试
 *
 * 使用场景：
 * - 性能测量：测量方法或代码块的执行时间
 * - 超时控制：配合超时检查使用
 * - 度量统计：收集操作耗时用于监控
 * - 断路器：测量调用延迟
 * - 限流器：统计请求处理时间
 *
 * 与其他计时方案的对比：
 * <pre>
 * 1. System.nanoTime()
 *    - 优点：性能最高，纳秒精度
 *    - 缺点：需要手动计算差值，不直观
 *
 * 2. System.currentTimeMillis()
 *    - 优点：简单，毫秒精度
 *    - 缺点：受系统时间调整影响，精度较低
 *
 * 3. Instant + Duration（本类采用）
 *    - 优点：API 直观，返回 Duration 对象，支持多种时间单位
 *    - 缺点：性能略低于 nanoTime()
 *
 * 4. Guava Stopwatch
 *    - 优点：功能丰富，支持暂停/恢复
 *    - 缺点：需要额外依赖
 * </pre>
 *
 * 使用示例：
 * <pre>
 * // 基本用法
 * StopWatch stopWatch = StopWatch.start();
 * performOperation();
 * Duration duration = stopWatch.stop();
 * System.out.println("操作耗时: " + duration.toMillis() + " ms");
 *
 * // 配合断路器使用
 * StopWatch stopWatch = StopWatch.start();
 * try {
 *     String result = remoteService.call();
 *     Duration duration = stopWatch.stop();
 *     circuitBreaker.recordSuccess(duration);
 *     return result;
 * } catch (Exception e) {
 *     Duration duration = stopWatch.stop();
 *     circuitBreaker.recordFailure(duration);
 *     throw e;
 * }
 * </pre>
 *
 * 注意事项：
 * - StopWatch 对象是一次性的，stop() 后可以多次调用，但都是基于同一个 startTime
 * - 使用 Clock.systemUTC() 获取 UTC 时间，避免时区问题
 * - Duration 可以转换为多种时间单位：toNanos()、toMillis()、toSeconds() 等
 * - 构造函数是包私有的，必须通过 start() 工厂方法创建
 *
 * 线程安全性：
 * - 本类是不可变的（startTime 是 final 的）
 * - 但 clock 字段不是 final 的（可能是遗留问题）
 * - 建议不要在多线程间共享 StopWatch 实例
 *
 * @author Robert Winkler
 * @since 0.1.0
 */
public class StopWatch {

    /** 开始时间，在对象创建时确定 */
    private final Instant startTime;

    /** 时钟实例，用于获取当前时间 */
    private Clock clock;

    /**
     * 包私有构造函数 - 通过 start() 工厂方法创建
     *
     * @param clock 时钟实例，用于获取当前时间
     */
    StopWatch(Clock clock) {
        this.clock = clock;
        this.startTime = clock.instant(); // 记录开始时间
    }

    /**
     * 启动秒表 - 创建一个新的 StopWatch 实例并开始计时
     *
     * 功能说明：
     * 这是创建 StopWatch 的唯一公开方式（工厂方法模式）。
     * 使用系统 UTC 时钟，避免时区问题。
     *
     * 使用示例：
     * <pre>
     * StopWatch stopWatch = StopWatch.start();
     * doSomething();
     * Duration duration = stopWatch.stop();
     * </pre>
     *
     * 为什么使用 UTC？
     * - 避免夏令时切换导致的时间跳变
     * - 便于跨时区比较和统计
     * - 与服务器时间保持一致
     *
     * @return 已启动的 StopWatch 实例
     */
    public static StopWatch start() {
        return new StopWatch(Clock.systemUTC());
    }

    /**
     * 停止秒表 - 计算从启动到现在的时间差
     *
     * 功能说明：
     * 获取当前时间，并计算与 startTime 的时间差。
     * 可以多次调用，每次都会基于当前时间重新计算。
     *
     * 返回值说明：
     * Duration 对象提供了丰富的时间单位转换方法：
     * - toNanos()：纳秒（最精确）
     * - toMillis()：毫秒（常用）
     * - toSeconds()：秒
     * - toMinutes()：分钟
     * - getSeconds()：获取秒数部分
     * - getNano()：获取纳秒部分（0-999,999,999）
     *
     * 使用示例：
     * <pre>
     * StopWatch stopWatch = StopWatch.start();
     * performTask();
     * Duration duration = stopWatch.stop();
     *
     * // 获取不同单位的时间
     * long nanos = duration.toNanos();     // 纳秒
     * long millis = duration.toMillis();   // 毫秒
     * long seconds = duration.toSeconds(); // 秒
     *
     * // 格式化输出
     * System.out.println("耗时: " + duration); // 输出: PT0.123S
     * </pre>
     *
     * 注意事项：
     * - 可以多次调用 stop()，每次都会返回新的时间差
     * - 如果需要记录多个时间点，建议创建多个 StopWatch 实例
     * - Duration 的 toString() 返回 ISO-8601 格式（如 PT0.123S）
     *
     * @return 从启动到现在的时间差（Duration 对象）
     */
    public Duration stop() {
        return Duration.between(startTime, clock.instant());
    }
}
