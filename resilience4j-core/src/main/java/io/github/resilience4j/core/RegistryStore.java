/*
 * Copyright 2020 KrnSaurabh
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

package io.github.resilience4j.core;

import java.util.Collection;
import java.util.Optional;
import java.util.function.Function;

/**
 * 注册表存储接口 - Registry 的底层存储抽象
 *
 * 作用说明：
 * 这是 Registry 使用的底层存储接口，定义了注册表实例的存储和访问操作。
 * 将存储逻辑抽象出来，使得 Registry 可以使用不同的存储实现。
 *
 * 设计理念：
 * - 存储抽象：将存储细节与注册表逻辑分离
 * - 类似 Map 接口：提供类似 ConcurrentMap 的操作语义
 * - 支持原子操作：computeIfAbsent、putIfAbsent 等原子操作
 * - 线程安全：所有实现都必须保证线程安全
 *
 * 典型实现：
 * - InMemoryRegistryStore：基于 ConcurrentHashMap 的内存存储
 * - CacheStore：带缓存的存储实现
 *
 * 为什么需要这个抽象？
 * 1. 解耦：将存储实现与注册表逻辑解耦
 * 2. 可扩展：可以实现不同的存储策略（内存、持久化、分布式等）
 * 3. 可测试：方便进行单元测试，可以提供 Mock 实现
 *
 * 使用示例：
 * <pre>
 * // 创建存储
 * RegistryStore&lt;CircuitBreaker&gt; store = new InMemoryRegistryStore&lt;&gt;();
 *
 * // 添加实例（如果不存在）
 * CircuitBreaker cb = store.computeIfAbsent("service1", name -&gt; {
 *     return CircuitBreaker.ofDefaults(name);
 * });
 *
 * // 查找实例
 * Optional&lt;CircuitBreaker&gt; found = store.find("service1");
 *
 * // 获取所有实例
 * Collection&lt;CircuitBreaker&gt; all = store.values();
 * </pre>
 *
 * 线程安全：
 * 所有方法都必须是线程安全的，支持并发访问。
 *
 * @param <E> 存储的实体类型，通常是容错组件类型（如 CircuitBreaker、RateLimiter）
 *
 * @author KrnSaurabh
 * @since 1.5.0
 */
public interface RegistryStore<E> {

    /**
     * 如果不存在则计算并添加
     *
     * 功能说明：
     * 原子操作：如果指定 key 的实例不存在，则使用 mappingFunction 创建实例并添加。
     * 如果实例已存在，直接返回现有实例。
     * 这是一个"获取或创建"的原子操作。
     *
     * 执行流程：
     * 1. 检查 key 对应的实例是否存在
     * 2. 如果不存在，调用 mappingFunction 创建新实例
     * 3. 将新实例添加到存储中
     * 4. 返回实例（新创建或已存在的）
     *
     * 线程安全：
     * 整个操作是原子的，即使多个线程同时调用，mappingFunction 也只会被执行一次。
     *
     * 使用场景：
     * - Registry 的懒加载创建：只在首次使用时创建实例
     * - 确保同一个名称只有一个实例
     *
     * 示例：
     * <pre>
     * // 获取或创建断路器，保证线程安全
     * CircuitBreaker cb = store.computeIfAbsent("service1", name -&gt; {
     *     System.out.println("创建新的断路器: " + name);
     *     return CircuitBreaker.ofDefaults(name);
     * });
     * </pre>
     *
     * 注意事项：
     * - mappingFunction 应该尽量简单，避免耗时操作
     * - mappingFunction 不应该抛出未检查异常
     * - 如果 mappingFunction 返回 null，行为取决于具体实现
     *
     * @param key             实例的唯一标识，不能为 null
     * @param mappingFunction 创建实例的函数，接收 key 作为参数，返回新创建的实例
     * @return 实例对象，可能是新创建的，也可能是已存在的
     */
    E computeIfAbsent(String key,
        Function<? super String, ? extends E> mappingFunction);

    /**
     * 如果不存在则添加
     *
     * 功能说明：
     * 原子操作：如果指定 key 的实例不存在，则添加给定的实例。
     * 如果实例已存在，不进行任何操作。
     *
     * 与 computeIfAbsent 的区别：
     * - computeIfAbsent：使用函数创建实例
     * - putIfAbsent：直接提供实例对象
     *
     * 使用场景：
     * - 当实例已经创建好，只需要添加到存储中
     * - 避免重复添加同名实例
     *
     * 示例：
     * <pre>
     * // 尝试添加实例
     * CircuitBreaker cb = CircuitBreaker.ofDefaults("service1");
     * CircuitBreaker existing = store.putIfAbsent("service1", cb);
     * if (existing == null) {
     *     System.out.println("实例添加成功");
     * } else {
     *     System.out.println("实例已存在，使用现有实例");
     * }
     * </pre>
     *
     * @param key   实例的唯一标识，不能为 null
     * @param value 要添加的实例，不能为 null
     * @return 如果 key 已存在，返回已存在的实例；如果 key 不存在，返回 null
     */
    E putIfAbsent(String key, E value);

    /**
     * 查找实例
     *
     * 功能说明：
     * 根据 key 查找实例。如果实例存在，返回包装在 Optional 中的实例；否则返回 Optional.empty()。
     *
     * 示例：
     * <pre>
     * Optional&lt;CircuitBreaker&gt; cb = store.find("service1");
     * cb.ifPresent(breaker -&gt; {
     *     System.out.println("找到断路器: " + breaker.getName());
     * });
     * </pre>
     *
     * @param key 实例的唯一标识，不能为 null
     * @return Optional 包装的实例，如果不存在则为 Optional.empty()
     */
    Optional<E> find(String key);

    /**
     * 移除实例
     *
     * 功能说明：
     * 从存储中移除指定 key 的实例。如果实例存在，移除并返回它；否则返回 Optional.empty()。
     *
     * 注意事项：
     * - 移除操作是原子的
     * - 移除后的实例不再由存储管理，但已有的引用仍然有效
     *
     * 示例：
     * <pre>
     * Optional&lt;CircuitBreaker&gt; removed = store.remove("service1");
     * if (removed.isPresent()) {
     *     System.out.println("已移除: " + removed.get().getName());
     * }
     * </pre>
     *
     * @param name 要移除的实例的唯一标识，不能为 null
     * @return Optional 包装的被移除的实例，如果不存在则为 Optional.empty()
     */
    Optional<E> remove(String name);

    /**
     * 替换实例
     *
     * 功能说明：
     * 用新实例替换指定 key 的现有实例。如果 key 存在，替换并返回旧实例；否则返回 Optional.empty()。
     *
     * 注意事项：
     * - 替换操作是原子的
     * - 只有当 key 存在时才会替换
     * - 如果 key 不存在，不会添加新实例
     *
     * 示例：
     * <pre>
     * CircuitBreaker newCb = CircuitBreaker.of("service1", newConfig);
     * Optional&lt;CircuitBreaker&gt; oldCb = store.replace("service1", newCb);
     * if (oldCb.isPresent()) {
     *     System.out.println("已替换实例");
     * } else {
     *     System.out.println("实例不存在，无法替换");
     * }
     * </pre>
     *
     * @param name     要替换的实例的唯一标识，不能为 null
     * @param newEntry 新的实例对象，不能为 null
     * @return Optional 包装的旧实例，如果不存在则为 Optional.empty()
     */
    Optional<E> replace(String name, E newEntry);

    /**
     * 获取所有实例
     *
     * 功能说明：
     * 返回存储中所有实例的集合。
     *
     * 注意事项：
     * - 返回的是实例的快照，不会反映后续的修改
     * - 集合的遍历不会抛出 ConcurrentModificationException
     * - 返回的集合可能是不可修改的
     *
     * 使用场景：
     * - 批量操作所有实例
     * - 监控和统计
     * - 导出所有实例信息
     *
     * 示例：
     * <pre>
     * Collection&lt;CircuitBreaker&gt; allBreakers = store.values();
     * System.out.println("共有 " + allBreakers.size() + " 个断路器");
     * allBreakers.forEach(cb -&gt; {
     *     System.out.println("断路器: " + cb.getName());
     * });
     * </pre>
     *
     * @return 所有实例的集合，永远不为 null（可能为空集合）
     */
    Collection<E> values();

}
