/*
 *
 *  Copyright 2024 Florentin Simion and Rares Vlasceanu
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

/**
 * 时钟抽象接口 - 用于测量绝对时间和相对时间
 *
 * 作用说明：
 * 这是一个时钟抽象层，将时间获取逻辑与系统时钟解耦。
 * 提供两种时间概念：墙上时钟时间（wall time）和单调时钟时间（monotonic time）。
 *
 * 设计理念：
 * - 抽象时间源：方便测试时使用模拟时钟
 * - 区分两种时间：绝对时间用于时间戳，相对时间用于测量时长
 * - 避免系统时钟调整问题：单调时钟不受系统时间调整影响
 *
 * 两种时间的区别：
 * 1. 墙上时钟（Wall Time）：
 *    - 表示真实世界的时间（日期和时间）
 *    - 会受系统时钟调整影响（如NTP同步、用户手动调整）
 *    - 用途：记录事件发生的时间戳、日志记录
 *    - 示例：2024-01-01 10:30:00
 *
 * 2. 单调时钟（Monotonic Time）：
 *    - 表示从某个固定时间点开始的时间流逝（通常是系统启动时间）
 *    - 只增不减，不受系统时钟调整影响
 *    - 用途：测量时间间隔、超时控制、性能测量
 *    - 示例：距离启动后的纳秒数
 *
 * 为什么需要这个抽象？
 * 1. 可测试性：在单元测试中可以注入模拟时钟，控制时间流逝
 * 2. 精确性：使用纳秒级精度的单调时钟测量时间间隔
 * 3. 可靠性：避免系统时钟调整导致的异常行为
 *
 * 使用场景：
 * - 断路器：测量半开状态的等待时间
 * - 限流器：测量时间窗口
 * - 重试：测量重试间隔
 * - 超时控制：测量操作执行时长
 *
 * 使用示例：
 * <pre>
 * // 使用系统时钟
 * Clock clock = Clock.SYSTEM;
 *
 * // 记录事件时间戳（使用墙上时钟）
 * long timestamp = clock.wallTime();
 * System.out.println("事件发生时间: " + new Date(timestamp));
 *
 * // 测量操作耗时（使用单调时钟）
 * long startTime = clock.monotonicTime();
 * performOperation();
 * long endTime = clock.monotonicTime();
 * long durationNanos = endTime - startTime;
 * System.out.println("操作耗时: " + durationNanos / 1_000_000 + " ms");
 * </pre>
 *
 * 测试示例：
 * <pre>
 * // 创建可控制的模拟时钟
 * class TestClock implements Clock {
 *     private long currentTime = 0;
 *
 *     public void advance(long nanos) {
 *         currentTime += nanos;
 *     }
 *
 *     {@literal @}Override
 *     public long wallTime() {
 *         return currentTime / 1_000_000; // 转换为毫秒
 *     }
 *
 *     {@literal @}Override
 *     public long monotonicTime() {
 *         return currentTime;
 *     }
 * }
 * </pre>
 *
 * @author Florentin Simion
 * @author Rares Vlasceanu
 * @since 2.0.0
 */
public interface Clock {
    /**
     * 获取墙上时钟时间（绝对时间）
     *
     * 功能说明：
     * 返回当前的墙上时钟时间，以 Unix 纪元（1970-01-01 00:00:00 UTC）以来的毫秒数表示。
     * 这个时间与真实世界的日期时间对应，可以转换为具体的日期。
     *
     * 重要提示：
     * - 不应该用于测量时间间隔！系统时钟可能会被调整（向前或向后）
     * - 应该用于时间戳记录、日志输出等需要绝对时间的场景
     *
     * 使用场景：
     * - 记录事件发生的时间
     * - 生成日志时间戳
     * - 计算过期时间（如缓存过期）
     *
     * 注意事项：
     * - 如果系统时间被调整（如NTP同步），这个值可能会跳变
     * - 两次调用的差值可能为负数（如果系统时间向后调整）
     * - 精度为毫秒级
     *
     * 示例：
     * <pre>
     * long timestamp = clock.wallTime();
     * System.out.println("当前时间: " + new Date(timestamp));
     * // 输出: 当前时间: Mon Jan 01 10:30:00 CST 2024
     * </pre>
     *
     * @return 自 Unix 纪元以来的毫秒数（墙上时钟时间）
     */
    long wallTime();

    /**
     * 获取单调时钟时间（相对时间）
     *
     * 功能说明：
     * 返回当前的单调时钟时间，以纳秒为单位。
     * 这个时间只会增加，不会受系统时钟调整的影响。
     *
     * 重要提示：
     * - 应该用于测量时间间隔！这是测量时长的正确方式
     * - 不应该用于时间戳记录！这个值没有实际意义，只有相对意义
     * - 不同系统或不同 JVM 启动的起始点不同，不可比较
     *
     * 单调性保证：
     * - 每次调用返回的值都 >= 上一次的值
     * - 不受系统时钟调整影响
     * - 不受夏令时影响
     * - 不受闰秒影响
     *
     * 使用场景：
     * - 测量操作执行时长
     * - 实现超时控制
     * - 计算速率（如 QPS）
     * - 断路器等待时间计算
     *
     * 精度说明：
     * - 返回值以纳秒为单位（1秒 = 1,000,000,000纳秒）
     * - 实际精度取决于操作系统和硬件
     * - 通常精度高于墙上时钟
     *
     * 示例：
     * <pre>
     * // 测量方法执行时间
     * long start = clock.monotonicTime();
     * doSomething();
     * long end = clock.monotonicTime();
     *
     * long durationNanos = end - start;
     * long durationMillis = durationNanos / 1_000_000;
     * System.out.println("执行耗时: " + durationMillis + " ms");
     *
     * // 实现超时检查
     * long deadline = clock.monotonicTime() + timeoutNanos;
     * while (clock.monotonicTime() < deadline) {
     *     // 继续执行
     * }
     * </pre>
     *
     * @return 单调时钟时间，以纳秒为单位
     */
    long monotonicTime();

    /**
     * 系统默认时钟实现
     *
     * 这是基于 JVM 系统调用的时钟实现：
     * - wallTime() 使用 System.currentTimeMillis()
     * - monotonicTime() 使用 System.nanoTime()
     *
     * 这是生产环境中使用的标准实现。
     * 在测试环境中可以替换为可控制的模拟时钟。
     */
    Clock SYSTEM = new Clock() {
        @Override
        public long wallTime() {
            // 返回系统墙上时钟时间（毫秒）
            return System.currentTimeMillis();
        }

        @Override
        public long monotonicTime() {
            // 返回系统单调时钟时间（纳秒）
            return System.nanoTime();
        }
    };
}
