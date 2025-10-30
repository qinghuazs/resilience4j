/*
 * Copyright 2019 Ingyu Hwang
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.resilience4j.core.metrics;

import io.github.resilience4j.core.registry.EntryAddedEvent;
import io.github.resilience4j.core.registry.EntryRemovedEvent;
import io.github.resilience4j.core.registry.EntryReplacedEvent;
import io.github.resilience4j.core.registry.RegistryEventConsumer;

/**
 * MetricsPublisher - 度量指标发布器接口
 *
 * <h2>功能说明</h2>
 * MetricsPublisher 用于将 Resilience4j 组件的度量指标发布到外部监控系统。
 * 当组件在注册表中添加、删除或替换时，自动发布或移除其度量指标。
 *
 * <h2>核心功能</h2>
 * <ul>
 *   <li><b>发布指标</b>：将组件的度量指标发布到监控系统</li>
 *   <li><b>移除指标</b>：从监控系统中移除组件的度量指标</li>
 *   <li><b>自动响应</b>：自动响应注册表事件，无需手动调用</li>
 * </ul>
 *
 * <h2>支持的监控系统</h2>
 * <ul>
 *   <li>Micrometer：集成 Spring Boot Actuator</li>
 *   <li>Dropwizard Metrics：经典的 Java 度量库</li>
 *   <li>Prometheus：时序数据库</li>
 *   <li>自定义系统：实现此接口即可</li>
 * </ul>
 *
 * <h2>工作原理</h2>
 * <pre>
 * 1. 实现 RegistryEventConsumer 接口，监听注册表事件
 * 2. 当组件添加时，调用 publishMetrics() 发布指标
 * 3. 当组件删除时，调用 removeMetrics() 移除指标
 * 4. 当组件替换时，先移除旧指标，再发布新指标
 * </pre>
 *
 * <h2>使用示例</h2>
 * <pre>
 * // 创建自定义度量发布器
 * public class MyMetricsPublisher implements MetricsPublisher&lt;CircuitBreaker&gt; {
 *
 *     private final MeterRegistry meterRegistry;
 *
 *     {@code @Override}
 *     public void publishMetrics(CircuitBreaker circuitBreaker) {
 *         String name = circuitBreaker.getName();
 *         Metrics metrics = circuitBreaker.getMetrics();
 *
 *         // 注册失败率指标
 *         Gauge.builder("circuitbreaker.failure.rate", metrics,
 *             m -> m.getSnapshot().getFailureRate())
 *             .tag("name", name)
 *             .register(meterRegistry);
 *
 *         // 注册慢调用率指标
 *         Gauge.builder("circuitbreaker.slow.call.rate", metrics,
 *             m -> m.getSnapshot().getSlowCallRate())
 *             .tag("name", name)
 *             .register(meterRegistry);
 *     }
 *
 *     {@code @Override}
 *     public void removeMetrics(CircuitBreaker circuitBreaker) {
 *         String name = circuitBreaker.getName();
 *
 *         // 移除指标
 *         meterRegistry.remove(
 *             new MeterFilter.MeterIdPredicate(id ->
 *                 id.getName().startsWith("circuitbreaker") &&
 *                 name.equals(id.getTag("name"))
 *             )
 *         );
 *     }
 * }
 *
 * // 注册到注册表
 * CircuitBreakerRegistry registry = CircuitBreakerRegistry.ofDefaults();
 * MyMetricsPublisher publisher = new MyMetricsPublisher(meterRegistry);
 * registry.getEventPublisher().onEntryAdded(publisher::publishMetrics);
 * registry.getEventPublisher().onEntryRemoved(publisher::removeMetrics);
 *
 * // 或者使用 RegistryEventConsumer
 * registry.getEventPublisher().onEvent(publisher);
 * </pre>
 *
 * <h2>常见实现</h2>
 * <ul>
 *   <li>CircuitBreakerMetricsPublisher - 发布断路器指标</li>
 *   <li>RetryMetricsPublisher - 发布重试指标</li>
 *   <li>RateLimiterMetricsPublisher - 发布限流器指标</li>
 *   <li>BulkheadMetricsPublisher - 发布隔离舱指标</li>
 * </ul>
 *
 * @param <E> 组件类型（如 CircuitBreaker, Retry 等）
 * @author Ingyu Hwang
 * @since 1.2.0
 * @see RegistryEventConsumer
 * @see io.github.resilience4j.core.registry.Registry
 */
public interface MetricsPublisher<E> extends RegistryEventConsumer<E> {

    /**
     * 发布度量指标 - 将组件的度量指标发布到监控系统
     *
     * 功能说明：
     * 当组件被添加到注册表时，此方法被调用以发布其度量指标。
     * 实现类应该将组件的关键指标注册到监控系统（如 Micrometer）。
     *
     * 典型发布的指标：
     * - 断路器：失败率、慢调用率、状态、调用次数
     * - 重试：重试次数、成功重试、失败重试
     * - 限流器：可用许可数、等待线程数
     * - 隔离舱：可用并发数、队列容量
     *
     * 使用示例：
     * <pre>
     * {@code @Override}
     * public void publishMetrics(CircuitBreaker cb) {
     *     String name = cb.getName();
     *     Metrics metrics = cb.getMetrics();
     *
     *     // 使用 Micrometer 发布指标
     *     Gauge.builder("failure_rate", metrics,
     *         m -> m.getSnapshot().getFailureRate())
     *         .tag("circuit_breaker", name)
     *         .register(meterRegistry);
     * }
     * </pre>
     *
     * @param entry 要发布指标的组件实例
     */
    void publishMetrics(E entry);

    /**
     * 移除度量指标 - 从监控系统中移除组件的度量指标
     *
     * 功能说明：
     * 当组件从注册表中删除时，此方法被调用以移除其度量指标。
     * 实现类应该清理之前注册的所有相关指标，避免内存泄漏。
     *
     * 重要性：
     * 如果不正确移除指标，可能导致：
     * - 内存泄漏（Meter 对象累积）
     * - 指标混乱（旧组件的指标仍然存在）
     * - 性能下降（无用的指标计算）
     *
     * 使用示例：
     * <pre>
     * {@code @Override}
     * public void removeMetrics(CircuitBreaker cb) {
     *     String name = cb.getName();
     *
     *     // 从 Micrometer 移除指标
     *     meterRegistry.remove(
     *         Meter.id("failure_rate",
     *             "circuit_breaker", name)
     *     );
     * }
     * </pre>
     *
     * @param entry 要移除指标的组件实例
     */
    void removeMetrics(E entry);

    /**
     * 处理组件添加事件 - 默认实现
     *
     * 功能说明：
     * 当组件添加到注册表时自动调用，默认实现调用 publishMetrics()。
     * 通常不需要重写此方法，除非需要额外的处理逻辑。
     *
     * @param entryAddedEvent 组件添加事件
     */
    @Override
    default void onEntryAddedEvent(EntryAddedEvent<E> entryAddedEvent) {
        publishMetrics(entryAddedEvent.getAddedEntry());
    }

    /**
     * 处理组件删除事件 - 默认实现
     *
     * 功能说明：
     * 当组件从注册表删除时自动调用，默认实现调用 removeMetrics()。
     * 通常不需要重写此方法，除非需要额外的清理逻辑。
     *
     * @param entryRemoveEvent 组件删除事件
     */
    @Override
    default void onEntryRemovedEvent(EntryRemovedEvent<E> entryRemoveEvent) {
        removeMetrics(entryRemoveEvent.getRemovedEntry());
    }

    /**
     * 处理组件替换事件 - 默认实现
     *
     * 功能说明：
     * 当组件被替换时自动调用，默认实现先移除旧组件指标，再发布新组件指标。
     * 确保指标的平滑过渡，避免指标丢失或重复。
     *
     * 执行顺序：
     * 1. 移除旧组件的指标（removeMetrics）
     * 2. 发布新组件的指标（publishMetrics）
     *
     * @param entryReplacedEvent 组件替换事件
     */
    @Override
    default void onEntryReplacedEvent(EntryReplacedEvent<E> entryReplacedEvent) {
        removeMetrics(entryReplacedEvent.getOldEntry());
        publishMetrics(entryReplacedEvent.getNewEntry());
    }

}
