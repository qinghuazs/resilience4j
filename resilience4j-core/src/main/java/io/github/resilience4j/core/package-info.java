/*
 *
 *  Copyright 2018: Clint Checketts
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

/**
 * Resilience4j 核心包 - 提供核心抽象、接口和工具类
 *
 * <h2>包概述</h2>
 * 这是 Resilience4j 的核心包，包含所有弹性模式的基础组件：
 * <ul>
 *   <li>核心接口：Registry、EventPublisher、EventConsumer 等</li>
 *   <li>工具类：Clock、StopWatch、IntervalFunction 等</li>
 *   <li>函数式工具：FunctionUtils、CallableUtils、SupplierUtils 等</li>
 *   <li>异步工具：CompletionStageUtils、ContextPropagator 等</li>
 *   <li>结果处理：ResultUtils、Either 类型支持</li>
 *   <li>异常类：InstantiationException、ConfigurationNotFoundException 等</li>
 * </ul>
 *
 * <h2>设计理念</h2>
 * <ul>
 *   <li><b>轻量级</b>：最小化外部依赖，只依赖 Java 标准库和 SLF4J</li>
 *   <li><b>函数式</b>：使用函数式编程风格，支持 lambda 表达式</li>
 *   <li><b>可组合</b>：装饰器模式，多种弹性模式可以组合使用</li>
 *   <li><b>事件驱动</b>：所有组件都会发布事件，支持监控和统计</li>
 *   <li><b>类型安全</b>：使用泛型确保编译时类型检查</li>
 *   <li><b>非空安全</b>：通过包级注解声明非空语义</li>
 * </ul>
 *
 * <h2>核心组件</h2>
 * <h3>注册表模式 (Registry Pattern)</h3>
 * <ul>
 *   <li>{@link Registry} - 组件注册表接口，管理组件生命周期</li>
 *   <li>{@link RegistryStore} - 存储抽象，支持可选的持久化</li>
 * </ul>
 *
 * <h3>事件处理 (Event Handling)</h3>
 * <ul>
 *   <li>{@link EventPublisher} - 事件发布器接口，使用观察者模式</li>
 *   <li>{@link EventConsumer} - 事件消费者接口，处理事件回调</li>
 *   <li>{@link EventProcessor} - 事件处理器实现，管理事件订阅</li>
 * </ul>
 *
 * <h3>时间管理 (Time Management)</h3>
 * <ul>
 *   <li>{@link Clock} - 时钟抽象，提供墙上时钟和单调时钟</li>
 *   <li>{@link StopWatch} - 秒表，测量操作执行时长</li>
 *   <li>{@link IntervalFunction} - 间隔函数，计算重试等待时间</li>
 *   <li>{@link IntervalBiFunction} - 双参数间隔函数，支持动态调整</li>
 * </ul>
 *
 * <h3>函数式工具 (Functional Utilities)</h3>
 * <ul>
 *   <li>{@link FunctionUtils} - Function 工具类，提供组合和恢复方法</li>
 *   <li>{@link CallableUtils} - Callable 工具类，支持检查异常</li>
 *   <li>{@link SupplierUtils} - Supplier 工具类，无参数的数据提供者</li>
 *   <li>{@link CheckedFunctionUtils} - 检查异常函数工具类</li>
 * </ul>
 *
 * <h3>异步工具 (Asynchronous Utilities)</h3>
 * <ul>
 *   <li>{@link CompletionStageUtils} - CompletionStage 工具类，异常恢复和函数组合</li>
 *   <li>{@link ContextPropagator} - 上下文传播器，跨线程传播 ThreadLocal 值</li>
 *   <li>{@link ContextAwareScheduledThreadPoolExecutor} - 上下文感知的定时线程池</li>
 * </ul>
 *
 * <h3>辅助工具 (Helper Utilities)</h3>
 * <ul>
 *   <li>{@link ResultUtils} - 结果判断工具，检查 Either 类型的调用结果</li>
 *   <li>{@link ClassUtils} - 类工具，通过反射创建对象实例</li>
 *   <li>{@link StringUtils} - 字符串工具，提供判空等方法</li>
 *   <li>{@link NamingThreadFactory} - 命名线程工厂，为线程池线程提供有意义的名称</li>
 * </ul>
 *
 * <h3>异常类 (Exceptions)</h3>
 * <ul>
 *   <li>{@link InstantiationException} - 实例化异常，当无法创建对象实例时抛出</li>
 *   <li>{@link ConfigurationNotFoundException} - 配置未找到异常</li>
 * </ul>
 *
 * <h2>非空语义 (Non-Null Semantics)</h2>
 * <p>
 * 本包使用 {@link NonNullApi} 和 {@link NonNullFields} 注解声明非空语义：
 * </p>
 * <ul>
 *   <li><b>NonNullApi</b>：包中所有 API 方法的参数和返回值默认不为 null</li>
 *   <li><b>NonNullFields</b>：包中所有类的字段默认不为 null</li>
 *   <li>如果允许 null，需要显式使用 {@code @Nullable} 注解标记</li>
 *   <li>这些注解帮助 IDE 和静态分析工具检测潜在的空指针问题</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>
 * // 事件处理
 * EventPublisher&lt;MyEvent&gt; publisher = ...;
 * publisher.onEvent(event -&gt; {
 *     logger.info("Event: {}", event);
 * });
 *
 * // 函数组合
 * Supplier&lt;String&gt; supplier = () -&gt; "hello";
 * Supplier&lt;String&gt; decorated = SupplierUtils.recover(
 *     supplier,
 *     ex -&gt; "fallback"
 * );
 *
 * // 时间测量
 * StopWatch stopWatch = StopWatch.start();
 * // 执行操作...
 * Duration duration = stopWatch.stop();
 * </pre>
 *
 * <h2>子包 (Subpackages)</h2>
 * <ul>
 *   <li>{@code io.github.resilience4j.core.functions} - 函数式接口和 Either 类型</li>
 *   <li>{@code io.github.resilience4j.core.metrics} - 度量指标接口</li>
 *   <li>{@code io.github.resilience4j.core.registry} - 注册表实现</li>
 *   <li>{@code io.github.resilience4j.core.lang} - 语言扩展（如非空注解）</li>
 * </ul>
 *
 * <h2>线程安全性</h2>
 * <p>
 * 核心组件的线程安全性：
 * </p>
 * <ul>
 *   <li>工具类方法：无状态的静态方法，线程安全</li>
 *   <li>EventProcessor：使用 CopyOnWriteArraySet，线程安全</li>
 *   <li>Registry 实现：具体实现类负责保证线程安全</li>
 *   <li>函数装饰器：返回的函数是否线程安全取决于被装饰的函数</li>
 * </ul>
 *
 * @author Clint Checketts
 * @since 1.0.0
 * @see io.github.resilience4j.core.functions
 * @see io.github.resilience4j.core.metrics
 * @see io.github.resilience4j.core.registry
 */
@NonNullApi
@NonNullFields
package io.github.resilience4j.core;

import io.github.resilience4j.core.lang.NonNullApi;
import io.github.resilience4j.core.lang.NonNullFields;