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
 * 事件发布器接口
 *
 * 作用说明：
 * 这是 Resilience4j 的核心接口之一，定义了事件发布能力。
 * 所有需要发布事件的组件（如 CircuitBreaker、RateLimiter 等）都会实现或使用这个接口。
 *
 * 设计理念：
 * - 采用观察者模式（Observer Pattern），实现事件驱动架构
 * - 提供松耦合的事件通知机制，让使用者可以订阅感兴趣的事件
 * - 支持多个消费者同时订阅同一个事件源
 *
 * 使用场景：
 * 1. 监控：订阅断路器状态变化事件，用于监控告警
 * 2. 日志：订阅所有失败事件，记录到日志系统
 * 3. 度量：订阅事件并导出到 Prometheus、Micrometer 等监控系统
 * 4. 审计：记录所有限流、熔断等操作，用于审计追踪
 *
 * 使用示例：
 * <pre>
 * // 获取断路器的事件发布器
 * EventPublisher&lt;CircuitBreakerEvent&gt; eventPublisher =
 *     circuitBreaker.getEventPublisher();
 *
 * // 订阅事件：当有事件发生时，打印事件信息
 * eventPublisher.onEvent(event -&gt; {
 *     System.out.println("收到事件: " + event.getEventType());
 * });
 * </pre>
 *
 * @param <T> 事件类型，通常是具体的事件类（如 CircuitBreakerEvent、RetryEvent 等）
 *
 * @author Robert Winkler
 * @since 0.1.0
 */
public interface EventPublisher<T> {

    /**
     * 注册事件消费者
     *
     * 功能说明：
     * 当有事件发生时，会自动调用注册的消费者的 consumeEvent 方法。
     * 可以注册多个消费者，每个消费者都会收到事件通知。
     *
     * 执行流程：
     * 1. 调用此方法注册一个事件消费者
     * 2. 当组件内部产生事件时（如断路器状态变化）
     * 3. 自动调用所有已注册消费者的 consumeEvent 方法
     * 4. 将事件对象作为参数传递给消费者
     *
     * 注意事项：
     * - 消费者的处理逻辑应该尽量简短，避免阻塞事件发布
     * - 如果消费者抛出异常，不会影响其他消费者的执行
     * - 消费者的执行顺序不保证，不要依赖执行顺序
     *
     * @param onEventConsumer 事件消费者，用于处理事件的回调函数
     *                        不能为 null，否则可能抛出 NullPointerException
     */
    void onEvent(EventConsumer<T> onEventConsumer);
}
