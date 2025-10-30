/*
 *
 *  Copyright 2019 Mahmoud Romeh, Robert Winkler
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

import io.github.resilience4j.core.registry.EntryAddedEvent;
import io.github.resilience4j.core.registry.EntryRemovedEvent;
import io.github.resilience4j.core.registry.EntryReplacedEvent;
import io.github.resilience4j.core.registry.RegistryEvent;

import java.util.Map;
import java.util.Optional;

/**
 * 注册表接口 - Resilience4j 的核心管理容器
 *
 * 作用说明：
 * 这是 Resilience4j 所有组件注册表的根接口，提供统一的注册、查找、管理功能。
 * 每种容错组件（CircuitBreaker、RateLimiter、Retry 等）都有对应的 Registry 实现。
 *
 * 设计理念：
 * - 注册表模式（Registry Pattern）：集中管理同类型的对象实例
 * - 配置管理：支持多个命名配置，可以为不同实例应用不同配置
 * - 生命周期管理：管理实例的创建、查找、替换和删除
 * - 事件驱动：注册表的变化会发布事件，方便监控和追踪
 *
 * 为什么需要 Registry？
 * 1. 避免重复创建：通过名称复用已创建的实例（如同一个服务的断路器）
 * 2. 集中配置：管理多个配置模板，按需应用
 * 3. 统一监控：在注册表层面监控所有实例的创建和销毁
 * 4. 动态管理：支持运行时添加、删除、替换实例
 *
 * 使用场景：
 * 1. 微服务场景：为每个下游服务创建一个断路器，统一管理
 * 2. 多租户场景：为不同租户使用不同的限流器配置
 * 3. 动态配置：通过配置中心动态调整容错策略
 * 4. 监控告警：订阅注册表事件，监控实例的创建和销毁
 *
 * 使用示例：
 * <pre>
 * // 创建断路器注册表
 * CircuitBreakerRegistry registry = CircuitBreakerRegistry.ofDefaults();
 *
 * // 添加自定义配置
 * CircuitBreakerConfig config = CircuitBreakerConfig.custom()
 *     .failureRateThreshold(50)
 *     .build();
 * registry.addConfiguration("customConfig", config);
 *
 * // 使用默认配置创建断路器
 * CircuitBreaker cb1 = registry.circuitBreaker("service1");
 *
 * // 使用自定义配置创建断路器
 * CircuitBreaker cb2 = registry.circuitBreaker("service2", "customConfig");
 *
 * // 查找已存在的断路器
 * Optional&lt;CircuitBreaker&gt; found = registry.find("service1");
 *
 * // 订阅注册表事件
 * registry.getEventPublisher()
 *     .onEntryAdded(event -&gt; System.out.println("新增: " + event.getAddedEntry()));
 * </pre>
 *
 * 线程安全：
 * 所有 Registry 的实现类都必须是线程安全的，支持并发访问。
 *
 * @param <E> 实体类型，表示注册表管理的对象类型（如 CircuitBreaker、RateLimiter）
 * @param <C> 配置类型，表示该实体使用的配置类型（如 CircuitBreakerConfig、RateLimiterConfig）
 *
 * @author Mahmoud Romeh
 * @author Robert Winkler
 * @since 1.0.0
 */
public interface Registry<E, C> {

    /**
     * 添加配置到注册表
     *
     * 功能说明：
     * 将一个命名配置添加到注册表中，后续可以通过配置名称来创建使用该配置的实例。
     * 这样可以定义多个配置模板，为不同的实例应用不同的容错策略。
     *
     * 使用场景：
     * - 为重要服务使用更严格的限流配置
     * - 为不同环境（开发/测试/生产）使用不同的容错参数
     * - 动态添加新的配置模板
     *
     * 示例：
     * <pre>
     * // 添加一个严格的限流配置
     * RateLimiterConfig strictConfig = RateLimiterConfig.custom()
     *     .limitForPeriod(10)  // 每个周期只允许10次请求
     *     .build();
     * registry.addConfiguration("strict", strictConfig);
     *
     * // 创建使用该配置的限流器
     * RateLimiter limiter = registry.rateLimiter("importantService", "strict");
     * </pre>
     *
     * @param configName    配置名称，用于后续引用该配置，不能为 null
     * @param configuration 配置对象，包含具体的容错参数，不能为 null
     */
    void addConfiguration(String configName, C configuration);

    /**
     * 查找注册表中的实例
     *
     * 功能说明：
     * 根据名称查找已经创建的实例。如果实例不存在，返回 Optional.empty()。
     * 不会创建新实例，只是查找已存在的。
     *
     * 使用场景：
     * - 检查某个实例是否已经创建
     * - 在不同的代码位置获取同一个实例
     * - 避免重复创建实例
     *
     * 示例：
     * <pre>
     * // 查找断路器
     * Optional&lt;CircuitBreaker&gt; cb = registry.find("userService");
     * if (cb.isPresent()) {
     *     // 实例已存在，直接使用
     *     cb.get().executeSupplier(() -&gt; callUserService());
     * } else {
     *     // 实例不存在，可能需要先创建
     *     CircuitBreaker newCb = registry.circuitBreaker("userService");
     * }
     * </pre>
     *
     * @param name 实例名称，不能为 null
     * @return Optional 包装的实例，如果不存在则为 Optional.empty()
     */
    Optional<E> find(String name);

    /**
     * 从注册表中移除实例
     *
     * 功能说明：
     * 从注册表中删除指定名称的实例。删除后，该实例将不再由注册表管理。
     * 如果实例不存在，返回 Optional.empty()。
     *
     * 注意事项：
     * - 移除实例不会停止正在进行的操作，只是从注册表中删除引用
     * - 移除后会发布 EntryRemovedEvent 事件
     * - 已经在使用中的实例引用不受影响，但无法通过注册表再次获取
     *
     * 使用场景：
     * - 服务下线时移除对应的断路器
     * - 清理不再使用的实例，释放资源
     * - 动态调整服务列表
     *
     * 示例：
     * <pre>
     * // 移除断路器
     * Optional&lt;CircuitBreaker&gt; removed = registry.remove("oldService");
     * if (removed.isPresent()) {
     *     System.out.println("已移除断路器: " + removed.get().getName());
     * }
     * </pre>
     *
     * @param name 要移除的实例名称，不能为 null
     * @return Optional 包装的被移除的实例，如果不存在则为 Optional.empty()
     */
    Optional<E> remove(String name);

    /**
     * 替换注册表中的实例
     *
     * 功能说明：
     * 用新实例替换注册表中已存在的实例。如果原实例不存在，返回 Optional.empty()。
     * 替换操作是原子的，确保并发安全。
     *
     * 注意事项：
     * - 替换后会发布 EntryReplacedEvent 事件
     * - 新旧实例的名称必须相同
     * - 已经持有旧实例引用的代码不受影响，继续使用旧实例
     * - 后续通过注册表获取的是新实例
     *
     * 使用场景：
     * - 动态更新配置，用新配置创建的实例替换旧实例
     * - 故障恢复后重置断路器状态
     * - 在线升级容错策略
     *
     * 示例：
     * <pre>
     * // 创建新的断路器配置
     * CircuitBreakerConfig newConfig = CircuitBreakerConfig.custom()
     *     .failureRateThreshold(60)  // 调整失败率阈值
     *     .build();
     * CircuitBreaker newCb = CircuitBreaker.of("service1", newConfig);
     *
     * // 替换旧的断路器
     * Optional&lt;CircuitBreaker&gt; oldCb = registry.replace("service1", newCb);
     * if (oldCb.isPresent()) {
     *     System.out.println("已替换断路器");
     * }
     * </pre>
     *
     * @param name     要替换的实例名称，不能为 null
     * @param newEntry 新的实例对象，不能为 null
     * @return Optional 包装的被替换的旧实例，如果不存在则为 Optional.empty()
     */
    Optional<E> replace(String name, E newEntry);

    /**
     * 根据名称获取配置
     *
     * 功能说明：
     * 从注册表中查找已添加的命名配置。
     *
     * 示例：
     * <pre>
     * Optional&lt;CircuitBreakerConfig&gt; config = registry.getConfiguration("strict");
     * if (config.isPresent()) {
     *     // 使用该配置创建新实例
     *     CircuitBreaker cb = CircuitBreaker.of("newService", config.get());
     * }
     * </pre>
     *
     * @param configName 配置名称，不能为 null
     * @return Optional 包装的配置对象，如果不存在则为 Optional.empty()
     */
    Optional<C> getConfiguration(String configName);

    /**
     * 获取默认配置
     *
     * 功能说明：
     * 返回注册表的默认配置。当创建实例时没有指定配置名称，将使用此默认配置。
     *
     * 注意事项：
     * - 默认配置在创建注册表时指定，如果未指定则使用框架内置的默认值
     * - 所有通过 registry.circuitBreaker("name") 创建的实例都使用默认配置
     *
     * 示例：
     * <pre>
     * CircuitBreakerConfig defaultConfig = registry.getDefaultConfig();
     * System.out.println("默认失败率阈值: " + defaultConfig.getFailureRateThreshold());
     * </pre>
     *
     * @return 默认配置对象，永远不为 null
     */
    C getDefaultConfig();

    /**
     * 获取全局标签
     *
     * 功能说明：
     * 返回注册表级别的全局标签。这些标签会被应用到所有通过该注册表创建的实例上。
     * 标签通常用于监控系统中的维度信息（如环境、版本、集群等）。
     *
     * 使用场景：
     * - 在 Prometheus 中添加标签维度
     * - 在监控系统中区分不同环境的指标
     * - 添加元数据用于日志和追踪
     *
     * 示例：
     * <pre>
     * Map&lt;String, String&gt; tags = registry.getTags();
     * // tags 可能包含: {"env": "prod", "region": "us-east-1"}
     * </pre>
     *
     * @return 标签的 Map，键值对形式，永远不为 null（可能为空 Map）
     */
    Map<String, String> getTags();

    /**
     * 获取事件发布器
     *
     * 功能说明：
     * 返回注册表的事件发布器，可以用来订阅注册表级别的事件。
     * 通过事件发布器可以监控实例的创建、删除、替换等操作。
     *
     * 注意事项：
     * - 这是注册表级别的事件，不同于实例级别的事件
     * - 注册表事件包括：EntryAddedEvent、EntryRemovedEvent、EntryReplacedEvent
     * - 实例级别的事件需要通过实例自身的 getEventPublisher() 获取
     *
     * 使用场景：
     * - 监控服务实例的动态变化
     * - 统计注册表中实例的数量变化
     * - 在实例创建时自动进行初始化操作
     *
     * 示例：
     * <pre>
     * registry.getEventPublisher()
     *     .onEntryAdded(event -&gt; {
     *         System.out.println("新增实例: " + event.getAddedEntry().getName());
     *     })
     *     .onEntryRemoved(event -&gt; {
     *         System.out.println("移除实例: " + event.getRemovedEntry().getName());
     *     });
     * </pre>
     *
     * @return 事件发布器，永远不为 null
     */
    EventPublisher<E> getEventPublisher();

    /**
     * 从注册表中移除配置
     *
     * 功能说明：
     * 删除指定名称的配置。移除后，无法再使用该配置名称创建新实例。
     *
     * 注意事项：
     * - 移除配置不会影响已经创建的实例
     * - 只是删除了配置模板，已使用该配置创建的实例继续正常工作
     * - 无法移除默认配置
     *
     * 使用场景：
     * - 清理不再使用的配置模板
     * - 动态配置管理中删除过期配置
     *
     * 示例：
     * <pre>
     * // 移除配置
     * CircuitBreakerConfig removed = registry.removeConfiguration("oldConfig");
     * if (removed != null) {
     *     System.out.println("已移除配置");
     * }
     * </pre>
     *
     * @param configName 要移除的配置名称，不能为 null
     * @return 被移除的配置对象，如果配置不存在则返回 null
     */
    C removeConfiguration(String configName);

    /**
     * 注册表事件发布器
     *
     * 作用说明：
     * 这是专门用于注册表的事件发布器，继承自通用的 EventPublisher 接口。
     * 提供了针对注册表操作的专用事件订阅方法。
     *
     * 事件类型：
     * - EntryAddedEvent：实例被添加到注册表
     * - EntryRemovedEvent：实例从注册表中移除
     * - EntryReplacedEvent：注册表中的实例被替换
     *
     * 设计理念：
     * - 流式API：支持链式调用，可以连续订阅多种事件
     * - 类型安全：每个事件类型都有专门的方法，避免类型错误
     *
     * 使用示例：
     * <pre>
     * // 链式订阅多个事件
     * registry.getEventPublisher()
     *     .onEntryAdded(event -&gt; {
     *         System.out.println("新增: " + event.getAddedEntry().getName());
     *     })
     *     .onEntryRemoved(event -&gt; {
     *         System.out.println("移除: " + event.getRemovedEntry().getName());
     *     })
     *     .onEntryReplaced(event -&gt; {
     *         System.out.println("替换: " + event.getOldEntry().getName());
     *     });
     * </pre>
     */
    interface EventPublisher<E> extends io.github.resilience4j.core.EventPublisher<RegistryEvent> {

        /**
         * 订阅实例添加事件
         *
         * 功能说明：
         * 当有新实例添加到注册表时，会触发此事件。
         * 无论是通过 Registry.of() 创建，还是通过其他方式添加，都会触发。
         *
         * 使用场景：
         * - 自动注册监控：当新实例创建时，自动添加到监控系统
         * - 日志记录：记录所有实例的创建时间和配置
         * - 初始化操作：为新创建的实例执行初始化逻辑
         *
         * 示例：
         * <pre>
         * registry.getEventPublisher().onEntryAdded(event -&gt; {
         *     E entry = event.getAddedEntry();
         *     System.out.println("新增实例: " + entry.getName());
         *     // 可以在这里执行额外的初始化操作
         * });
         * </pre>
         *
         * @param eventConsumer 事件消费者，接收 EntryAddedEvent 类型的事件
         * @return 返回 this，支持链式调用
         */
        EventPublisher<E> onEntryAdded(EventConsumer<EntryAddedEvent<E>> eventConsumer);

        /**
         * 订阅实例移除事件
         *
         * 功能说明：
         * 当实例从注册表中移除时，会触发此事件。
         *
         * 使用场景：
         * - 清理资源：当实例移除时，清理相关的监控、日志等资源
         * - 统计分析：统计实例的生命周期
         * - 告警通知：当重要实例被移除时发送告警
         *
         * 示例：
         * <pre>
         * registry.getEventPublisher().onEntryRemoved(event -&gt; {
         *     E entry = event.getRemovedEntry();
         *     System.out.println("移除实例: " + entry.getName());
         *     // 清理与该实例相关的资源
         * });
         * </pre>
         *
         * @param eventConsumer 事件消费者，接收 EntryRemovedEvent 类型的事件
         * @return 返回 this，支持链式调用
         */
        EventPublisher<E> onEntryRemoved(EventConsumer<EntryRemovedEvent<E>> eventConsumer);

        /**
         * 订阅实例替换事件
         *
         * 功能说明：
         * 当注册表中的实例被替换时，会触发此事件。
         * 事件中包含旧实例和新实例的信息。
         *
         * 使用场景：
         * - 配置变更追踪：记录配置的变更历史
         * - 平滑迁移：在实例替换时进行状态迁移
         * - 审计日志：记录谁在什么时候替换了实例
         *
         * 示例：
         * <pre>
         * registry.getEventPublisher().onEntryReplaced(event -&gt; {
         *     E oldEntry = event.getOldEntry();
         *     E newEntry = event.getNewEntry();
         *     System.out.println("替换实例: " + oldEntry.getName() +
         *                        " -&gt; " + newEntry.getName());
         * });
         * </pre>
         *
         * @param eventConsumer 事件消费者，接收 EntryReplacedEvent 类型的事件
         * @return 返回 this，支持链式调用
         */
        EventPublisher<E> onEntryReplaced(EventConsumer<EntryReplacedEvent<E>> eventConsumer);
    }
}
