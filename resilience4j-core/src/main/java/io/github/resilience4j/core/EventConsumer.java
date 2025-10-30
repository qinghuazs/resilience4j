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

/**
 * 事件消费者接口
 *
 * 作用说明：
 * 这是 Resilience4j 事件机制的核心接口，用于定义事件处理的回调逻辑。
 * 配合 EventPublisher 使用，形成完整的事件发布-订阅模式。
 *
 * 设计理念：
 * - 函数式接口：标记了 @FunctionalInterface，可以使用 Lambda 表达式
 * - 单一职责：只有一个方法，专门用于消费事件
 * - 类型安全：通过泛型 T 确保事件类型安全
 *
 * 与 Java 标准库的关系：
 * 虽然功能类似于 java.util.function.Consumer，但专门为事件处理设计，
 * 方法名更明确（consumeEvent 比 accept 更清晰地表达意图）。
 *
 * 使用场景：
 * 1. 日志记录：当断路器状态变化时，记录日志
 * 2. 监控告警：当失败率超过阈值时，发送告警
 * 3. 度量统计：统计各类事件的发生次数
 * 4. 业务逻辑：根据事件触发特定的业务处理
 *
 * 使用示例：
 * <pre>
 * // 方式1：使用 Lambda 表达式（推荐）
 * EventConsumer&lt;CircuitBreakerEvent&gt; consumer = event -&gt; {
 *     System.out.println("断路器事件: " + event.getEventType());
 *     // 处理事件的业务逻辑
 * };
 *
 * // 方式2：使用方法引用
 * EventConsumer&lt;RetryEvent&gt; consumer = this::handleRetryEvent;
 *
 * // 方式3：实现接口（不推荐，代码冗长）
 * EventConsumer&lt;RateLimiterEvent&gt; consumer = new EventConsumer&lt;&gt;() {
 *     {@literal @}Override
 *     public void consumeEvent(RateLimiterEvent event) {
 *         // 处理逻辑
 *     }
 * };
 *
 * // 注册到事件发布器
 * eventPublisher.onEvent(consumer);
 * </pre>
 *
 * 线程安全说明：
 * 事件消费者可能在不同线程中被调用，因此实现时需要注意线程安全。
 * 如果消费者内部有共享状态，需要进行适当的同步。
 *
 * @param <T> 事件类型，通常是具体的事件类（如 CircuitBreakerEvent、RetryEvent 等）
 *
 * @author Robert Winkler
 * @since 0.1.0
 */
@FunctionalInterface
public interface EventConsumer<T> {

    /**
     * 消费（处理）事件
     *
     * 功能说明：
     * 当事件发布器发布事件时，会调用此方法来处理事件。
     * 这是事件处理的核心方法，用户需要在这里实现具体的事件处理逻辑。
     *
     * 执行时机：
     * - 当组件内部状态发生变化时（如断路器打开、限流器拒绝请求等）
     * - 当操作完成时（如重试成功、失败等）
     * - 根据不同组件的事件定义，在特定的时间点触发
     *
     * 注意事项：
     * 1. 性能：此方法应该快速返回，避免执行耗时操作（如网络请求、数据库操作）
     *    如果需要执行耗时操作，建议使用异步方式或消息队列
     * 2. 异常：如果此方法抛出异常，通常会被捕获并记录，不会影响其他消费者
     * 3. 顺序：不要依赖事件的处理顺序，多个消费者的执行顺序是不确定的
     * 4. 线程安全：可能在不同线程中调用，需要确保线程安全
     *
     * 常见用法：
     * <pre>
     * // 简单的日志记录
     * consumer.consumeEvent(event);
     *
     * // 在 Lambda 中使用
     * eventPublisher.onEvent(event -&gt; consumeEvent(event));
     * </pre>
     *
     * @param event 要处理的事件对象，包含事件的详细信息
     *              事件对象通常是不可变的（immutable），可以安全地读取其属性
     */
    void consumeEvent(T event);
}
