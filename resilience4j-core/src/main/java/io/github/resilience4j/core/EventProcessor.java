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

import io.github.resilience4j.core.lang.Nullable;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 事件处理器 - EventPublisher 接口的标准实现
 *
 * 作用说明：
 * 这是 Resilience4j 中事件发布-订阅机制的核心实现类。
 * 负责管理事件消费者的注册，并在事件发生时通知所有订阅者。
 *
 * 设计理念：
 * - 支持两种订阅方式：
 *   1. 全局订阅：订阅所有类型的事件（通过 onEvent 方法）
 *   2. 精确订阅：只订阅特定类型的事件（通过 registerConsumer 方法）
 * - 线程安全：使用 CopyOnWriteArraySet 和 ConcurrentHashMap 确保并发安全
 * - 高性能：读多写少的场景下性能优异
 *
 * 数据结构说明：
 * - onEventConsumers：存储全局事件消费者，会接收所有类型的事件
 * - eventConsumerMap：存储按事件类型分类的消费者，key 是事件类名，value 是该类型的消费者集合
 *
 * 线程安全策略：
 * - 使用 CopyOnWriteArraySet 存储消费者，适合读多写少的场景
 * - 使用 ReentrantLock 保护注册操作，确保注册过程的原子性
 * - 事件处理不加锁，保证高性能
 *
 * 使用示例：
 * <pre>
 * // 创建事件处理器
 * EventProcessor&lt;CircuitBreakerEvent&gt; eventProcessor = new EventProcessor&lt;&gt;();
 *
 * // 订阅所有事件
 * eventProcessor.onEvent(event -&gt; {
 *     System.out.println("收到事件: " + event);
 * });
 *
 * // 订阅特定类型的事件
 * eventProcessor.registerConsumer(
 *     CircuitBreakerOnStateTransitionEvent.class.getName(),
 *     event -&gt; System.out.println("状态变化: " + event)
 * );
 *
 * // 发布事件
 * CircuitBreakerEvent event = new CircuitBreakerOnStateTransitionEvent(...);
 * eventProcessor.processEvent(event);
 * </pre>
 *
 * 性能特征：
 * - 时间复杂度：O(n)，n 为订阅者数量
 * - 空间复杂度：O(m + k)，m 为全局订阅者数量，k 为分类订阅者总数
 * - 并发性能：读操作无锁，写操作有锁
 *
 * @param <T> 事件基类型，所有具体事件类型都应该继承此类型
 *
 * @author Robert Winkler
 * @since 0.1.0
 */
public class EventProcessor<T> implements EventPublisher<T> {

    /**
     * 全局事件消费者集合
     * 使用 CopyOnWriteArraySet 确保线程安全，并且在遍历时不需要加锁
     * 这些消费者会接收所有类型的事件
     */
    final Set<EventConsumer<T>> onEventConsumers = new CopyOnWriteArraySet<>();

    /**
     * 按事件类型分类的消费者映射表
     * key：事件类的完整类名（Class.getName()）
     * value：订阅该类型事件的消费者集合
     * 使用 ConcurrentHashMap 确保线程安全
     */
    final ConcurrentMap<String, Set<EventConsumer<T>>> eventConsumerMap = new ConcurrentHashMap<>();

    /**
     * 标记是否有消费者已注册
     * 用于快速判断是否需要处理事件，避免不必要的遍历
     */
    private boolean consumerRegistered;

    /**
     * 检查是否有消费者已注册
     *
     * 功能说明：
     * 快速检查是否有任何消费者注册。如果没有消费者，可以跳过事件处理，提升性能。
     *
     * @return true 如果至少有一个消费者注册，false 否则
     */
    public boolean hasConsumers() {
        return consumerRegistered;
    }

    /**
     * 可重入锁，用于保护注册操作
     * 确保多线程环境下注册操作的原子性
     */
    private final ReentrantLock lock = new ReentrantLock();

    /**
     * 注册特定类型的事件消费者
     *
     * 功能说明：
     * 注册一个只接收特定类型事件的消费者。
     * 只有当事件的类名与 className 参数匹配时，该消费者才会被调用。
     *
     * 与 onEvent 的区别：
     * - onEvent：订阅所有类型的事件
     * - registerConsumer：只订阅特定类型的事件，更精确，性能更好
     *
     * 使用场景：
     * - 只关心特定类型的事件（如只关心状态转换事件）
     * - 避免处理不需要的事件，提升性能
     * - 实现更细粒度的事件订阅
     *
     * 线程安全：
     * 使用 ReentrantLock 确保注册过程的原子性，多线程同时注册也是安全的。
     *
     * 示例：
     * <pre>
     * // 只订阅断路器状态转换事件
     * eventProcessor.registerConsumer(
     *     CircuitBreakerOnStateTransitionEvent.class.getName(),
     *     event -&gt; {
     *         CircuitBreakerOnStateTransitionEvent stateEvent =
     *             (CircuitBreakerOnStateTransitionEvent) event;
     *         System.out.println("状态从 " + stateEvent.getStateTransition().getFromState() +
     *                            " 转换到 " + stateEvent.getStateTransition().getToState());
     *     }
     * );
     * </pre>
     *
     * @param className     事件类的完整类名，通过 Class.getName() 获取
     * @param eventConsumer 事件消费者，只会接收指定类型的事件
     */
    @SuppressWarnings("unchecked")
    public void registerConsumer(String className, EventConsumer<? extends T> eventConsumer) {
        // 加锁保护注册过程
        lock.lock();

        try {
            // 使用 compute 方法原子性地更新 map
            this.eventConsumerMap.compute(className, (k, consumers) -> {
                if (consumers == null) {
                    // 如果该类型还没有消费者，创建新的集合
                    consumers = new CopyOnWriteArraySet<>();
                    consumers.add((EventConsumer<T>) eventConsumer);
                    return consumers;
                } else {
                    // 如果已有消费者，添加到现有集合
                    consumers.add((EventConsumer<T>) eventConsumer);
                    return consumers;
                }
            });
            // 标记已有消费者注册
            this.consumerRegistered = true;
        } finally {
            // 确保锁被释放
            lock.unlock();
        }
    }

    /**
     * 处理事件，通知所有相关的消费者
     *
     * 功能说明：
     * 当事件发生时，调用此方法将事件分发给所有订阅者。
     * 分发策略：
     * 1. 首先通知所有全局消费者（通过 onEvent 注册的）
     * 2. 然后通知该事件类型的特定消费者（通过 registerConsumer 注册的）
     *
     * 执行流程：
     * 1. 检查全局消费者集合是否为空
     * 2. 遍历全局消费者，依次调用 consumeEvent 方法
     * 3. 根据事件的类名，从 eventConsumerMap 中查找特定类型的消费者
     * 4. 遍历特定类型的消费者，依次调用 consumeEvent 方法
     *
     * 性能优化：
     * - 使用 isEmpty() 快速判断，避免不必要的遍历
     * - 事件处理不加锁，保证高吞吐量
     * - CopyOnWriteArraySet 支持无锁遍历
     *
     * 异常处理：
     * 如果某个消费者抛出异常，不会影响其他消费者的执行。
     * （注意：当前实现未显式捕获异常，实际使用时可能需要在调用处处理）
     *
     * 示例：
     * <pre>
     * CircuitBreakerEvent event = new CircuitBreakerOnSuccessEvent(...);
     * boolean consumed = eventProcessor.processEvent(event);
     * if (consumed) {
     *     System.out.println("事件已被至少一个消费者处理");
     * }
     * </pre>
     *
     * @param event 要处理的事件对象
     * @param <E>   事件的具体类型，必须是 T 或 T 的子类
     * @return true 如果至少有一个消费者处理了该事件，false 如果没有消费者
     */
    public <E extends T> boolean processEvent(E event) {
        boolean consumed = false; // 标记是否有消费者处理了事件

        // 步骤1：通知所有全局消费者
        if (!onEventConsumers.isEmpty()) {
            // 遍历全局消费者集合
            for (EventConsumer<T> onEventConsumer : onEventConsumers) {
                // 调用消费者的处理方法
                onEventConsumer.consumeEvent(event);
            }
            consumed = true; // 标记事件已被消费
        }

        // 步骤2：通知特定类型的消费者
        if (!eventConsumerMap.isEmpty()) {
            // 根据事件的类名查找对应的消费者集合
            final Set<EventConsumer<T>> consumers = this.eventConsumerMap.get(event.getClass().getName());
            if (consumers != null && !consumers.isEmpty()) {
                // 遍历该类型的所有消费者
                for (EventConsumer<T> consumer : consumers) {
                    // 调用消费者的处理方法
                    consumer.consumeEvent(event);
                }
                consumed = true; // 标记事件已被消费
            }
        }
        return consumed; // 返回是否有消费者处理了该事件
    }

    /**
     * 注册全局事件消费者
     *
     * 功能说明：
     * 实现 EventPublisher 接口的方法，注册一个接收所有类型事件的消费者。
     * 无论发生什么类型的事件，这个消费者都会被通知。
     *
     * 使用场景：
     * - 需要记录所有事件的日志
     * - 需要统计所有事件的数量
     * - 需要对所有事件做通用处理
     *
     * 注意事项：
     * - 全局消费者会接收大量事件，处理逻辑应该尽量简单
     * - 如果只关心特定类型的事件，建议使用 registerConsumer 方法
     *
     * 线程安全：
     * 使用 ReentrantLock 确保注册过程的原子性。
     *
     * 示例：
     * <pre>
     * // 订阅所有事件，打印事件类型
     * eventProcessor.onEvent(event -&gt; {
     *     System.out.println("事件类型: " + event.getClass().getSimpleName());
     * });
     * </pre>
     *
     * @param onEventConsumer 事件消费者，会接收所有类型的事件
     */
    @Override
    public void onEvent(@Nullable EventConsumer<T> onEventConsumer) {
        // 加锁保护注册过程
        lock.lock();

        try {
            // 将消费者添加到全局消费者集合
            this.onEventConsumers.add(onEventConsumer);
            // 标记已有消费者注册
            this.consumerRegistered = true;
        } finally {
            // 确保锁被释放
            lock.unlock();
        }
    }
}
