/*
 *
 *  Copyright 2020 krnsaurabh
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
import org.slf4j.MDC;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static java.util.stream.Collectors.toList;

/**
 * 上下文感知的定时线程池执行器 - 跨线程传播上下文信息
 *
 * 作用说明：
 * 这是 ScheduledThreadPoolExecutor 的增强版本，能够在异步任务执行时
 * 自动传播线程上下文（ThreadLocal 值），解决跨线程数据丢失的问题。
 *
 * 为什么需要这个类？
 * 在异步编程中，常见的上下文信息包括：
 * - MDC（Mapped Diagnostic Context）：SLF4J 的日志上下文，用于追踪请求
 * - Security Context：安全上下文，包含当前用户信息
 * - Transaction Context：事务上下文
 * - Custom ThreadLocal：自定义的线程本地变量
 *
 * 这些上下文通常存储在 ThreadLocal 中，当任务切换到线程池的工作线程时，
 * 这些上下文会丢失，导致日志追踪断开、权限检查失败等问题。
 *
 * 核心解决方案：
 * 1. 在主线程（任务提交线程）捕获上下文快照
 * 2. 在工作线程（任务执行线程）恢复上下文
 * 3. 任务执行完毕后清理上下文，避免污染线程池
 *
 * 设计理念：
 * - 透明传播：用户无需修改业务代码，上下文自动传播
 * - 可扩展性：支持自定义 ContextPropagator，传播任意类型的上下文
 * - 自动清理：使用 try-finally 确保上下文不会泄漏到其他任务
 * - 命名线程：使用 NamingThreadFactory 便于问题排查
 *
 * 上下文传播机制：
 * <pre>
 * [主线程]                    [工作线程]
 *    |                           |
 *    +--> 1. 捕获 MDC            |
 *    |    Map mdcMap = MDC.getCopyOfContextMap()
 *    |                           |
 *    +--> 2. 捕获自定义上下文    |
 *    |    ContextPropagator.decorateRunnable()
 *    |                           |
 *    +--> 3. 提交任务            |
 *    |    schedule(task)         |
 *    |                           |
 *    |                    4. 恢复 MDC <--+
 *    |                       MDC.setContextMap(mdcMap)
 *    |                           |
 *    |                    5. 恢复自定义上下文
 *    |                       contextPropagator.copy()
 *    |                           |
 *    |                    6. 执行任务
 *    |                       task.run()
 *    |                           |
 *    |                    7. 清理上下文 <--+
 *    |                       MDC.clear()
 *    |                       contextPropagator.clear()
 * </pre>
 *
 * 使用场景：
 * - Resilience4j 的断路器状态转换：需要保持日志追踪上下文
 * - 定时任务：需要保持安全上下文或业务上下文
 * - 异步回调：需要在回调中访问原始请求的上下文
 *
 * 使用示例：
 * <pre>
 * // 创建上下文感知的线程池
 * ContextAwareScheduledThreadPoolExecutor executor =
 *     ContextAwareScheduledThreadPoolExecutor.newScheduledThreadPool()
 *         .corePoolSize(5)
 *         .contextPropagators(new SecurityContextPropagator())
 *         .build();
 *
 * // 设置 MDC 上下文
 * MDC.put("requestId", "12345");
 * MDC.put("userId", "user-001");
 *
 * // 提交定时任务 - MDC 会自动传播到工作线程
 * executor.schedule(() -> {
 *     // 工作线程中可以访问 MDC
 *     String requestId = MDC.get("requestId"); // "12345"
 *     logger.info("执行定时任务"); // 日志会包含 requestId
 * }, 1, TimeUnit.SECONDS);
 *
 * // 无需手动清理，executor 会自动处理
 * </pre>
 *
 * 与标准 ScheduledThreadPoolExecutor 的对比：
 * <pre>
 * // 标准线程池 - 上下文丢失
 * ScheduledThreadPoolExecutor standardExecutor = new ScheduledThreadPoolExecutor(5);
 * MDC.put("requestId", "12345");
 * standardExecutor.schedule(() -> {
 *     String requestId = MDC.get("requestId"); // null - 上下文丢失！
 * }, 1, TimeUnit.SECONDS);
 *
 * // 上下文感知线程池 - 上下文保留
 * ContextAwareScheduledThreadPoolExecutor contextExecutor = ...;
 * MDC.put("requestId", "12345");
 * contextExecutor.schedule(() -> {
 *     String requestId = MDC.get("requestId"); // "12345" - 上下文保留！
 * }, 1, TimeUnit.SECONDS);
 * </pre>
 *
 * 线程安全性：
 * - 继承 ScheduledThreadPoolExecutor 的线程安全性
 * - contextPropagators 在构造后不可变
 * - MDC 操作在任务执行时是线程隔离的
 *
 * 性能考虑：
 * - 每次任务提交时复制 MDC：O(n) 其中 n 是 MDC 键值对数量
 * - 使用 Collections.emptyMap() 避免空 MDC 时的开销
 * - ContextPropagator 操作的性能取决于具体实现
 *
 * 注意事项：
 * - 任务执行后会自动清理 MDC，确保线程池线程干净
 * - 如果任务本身修改了 MDC，这些修改不会影响原始线程
 * - ContextPropagator 需要正确实现 retrieve/copy/clear 方法
 * - corePoolSize 必须 >= 1，否则抛出 IllegalArgumentException
 *
 * @author krnsaurabh
 * @since 1.5.0
 * @see ContextPropagator
 * @see NamingThreadFactory
 * @see MDC
 */
public class ContextAwareScheduledThreadPoolExecutor extends ScheduledThreadPoolExecutor {

    /** 上下文传播器列表，用于传播自定义的线程本地变量 */
    private final List<ContextPropagator> contextPropagators;

    /** 线程名称前缀，生成的线程名如 "ContextAwareScheduledThreadPool-1" */
    private static final String THREAD_PREFIX = "ContextAwareScheduledThreadPool";

    /**
     * 私有构造函数，通过 Builder 创建实例
     *
     * 功能说明：
     * 创建上下文感知的线程池，配置核心线程数和上下文传播器。
     * 使用 NamingThreadFactory 为线程命名，便于问题排查。
     *
     * 初始化步骤：
     * 1. 调用父类构造函数，设置核心线程数
     * 2. 使用 NamingThreadFactory 创建命名线程
     * 3. 保存上下文传播器列表（如果为 null 则使用空列表）
     *
     * @param corePoolSize        核心线程池大小，必须 >= 1
     * @param contextPropagators  上下文传播器列表，可以为 null（表示不传播自定义上下文）
     */
    private ContextAwareScheduledThreadPoolExecutor(int corePoolSize,
                                                   @Nullable List<ContextPropagator> contextPropagators) {
        // 调用父类构造函数，传入核心线程数和命名线程工厂
        super(corePoolSize, new NamingThreadFactory(THREAD_PREFIX));

        // 保存上下文传播器，如果为 null 则使用空列表
        this.contextPropagators = contextPropagators != null ? contextPropagators : new ArrayList<>();
    }

    /**
     * 获取上下文传播器列表
     *
     * 功能说明：
     * 返回不可修改的上下文传播器列表，防止外部修改。
     *
     * 使用场景：
     * - 检查当前配置了哪些上下文传播器
     * - 调试上下文传播问题
     *
     * @return 不可修改的上下文传播器列表
     */
    public List<ContextPropagator> getContextPropagators() {
        return Collections.unmodifiableList(this.contextPropagators);
    }

    /**
     * 调度一次性任务 - 支持上下文传播
     *
     * 功能说明：
     * 延迟指定时间后执行任务，同时传播 MDC 和自定义上下文到工作线程。
     *
     * 执行流程：
     * 1. 在当前线程（提交线程）捕获 MDC 上下文快照
     * 2. 使用 ContextPropagator.decorateRunnable() 包装任务，捕获自定义上下文
     * 3. 提交包装后的任务到父类线程池
     * 4. 延迟 delay 时间后，工作线程执行任务：
     *    a. 恢复 MDC 上下文到工作线程
     *    b. 恢复自定义上下文（由 ContextPropagator 处理）
     *    c. 执行原始任务
     *    d. 清理 MDC，确保线程池干净（finally 块保证清理）
     *
     * 上下文传播示例：
     * <pre>
     * // 主线程设置 MDC
     * MDC.put("requestId", "12345");
     *
     * // 提交延迟任务
     * executor.schedule(() -> {
     *     // 工作线程可以访问 MDC
     *     String requestId = MDC.get("requestId"); // "12345"
     * }, 5, TimeUnit.SECONDS);
     * </pre>
     *
     * @param command 要执行的任务
     * @param delay   延迟时间
     * @param unit    时间单位
     * @return ScheduledFuture 表示待完成的任务，可用于取消或等待完成
     */
    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        // 1. 捕获当前线程的 MDC 上下文
        Map<String, String> mdcContextMap = getMdcContextMap();

        // 2. 使用 ContextPropagator 装饰任务，传播自定义上下文
        // 3. 提交到父类线程池
        return super.schedule(ContextPropagator.decorateRunnable(contextPropagators, () -> {
                try {
                    // 4a. 在工作线程恢复 MDC 上下文
                    setMDCContext(mdcContextMap);

                    // 4c. 执行原始任务
                    command.run();
                } finally {
                    // 4d. 清理 MDC，避免污染线程池
                    MDC.clear();
                }
            }), delay, unit);
    }

    /**
     * 调度一次性 Callable 任务 - 支持上下文传播和返回值
     *
     * 功能说明：
     * 延迟指定时间后执行 Callable 任务，传播上下文并返回结果。
     * 与 schedule(Runnable) 的区别是支持返回值。
     *
     * 执行流程：
     * 1. 捕获 MDC 上下文
     * 2. 使用 ContextPropagator.decorateCallable() 包装任务
     * 3. 提交到线程池
     * 4. 工作线程执行时：恢复上下文 -> 执行任务 -> 清理上下文
     * 5. 返回 ScheduledFuture，可通过 get() 获取结果
     *
     * 使用示例：
     * <pre>
     * MDC.put("requestId", "12345");
     *
     * ScheduledFuture&lt;String&gt; future = executor.schedule(() -> {
     *     String requestId = MDC.get("requestId"); // "12345"
     *     return "Result-" + requestId;
     * }, 3, TimeUnit.SECONDS);
     *
     * String result = future.get(); // "Result-12345"
     * </pre>
     *
     * @param <V>      返回值类型
     * @param callable 要执行的 Callable 任务
     * @param delay    延迟时间
     * @param unit     时间单位
     * @return ScheduledFuture 表示待完成的任务，可获取返回值
     */
    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        // 1. 捕获当前线程的 MDC 上下文
        Map<String, String> mdcContextMap = getMdcContextMap();

        // 2. 使用 ContextPropagator 装饰 Callable，传播自定义上下文
        return super.schedule(ContextPropagator.decorateCallable(contextPropagators, () -> {
            try {
                // 3. 在工作线程恢复 MDC 上下文
                setMDCContext(mdcContextMap);

                // 4. 执行原始 Callable 并返回结果
                return callable.call();
            } finally {
                // 5. 清理 MDC
                MDC.clear();
            }
        }), delay, unit);
    }

    /**
     * 以固定频率调度周期任务 - 支持上下文传播
     *
     * 功能说明：
     * 初始延迟后，以固定的频率（时间间隔）重复执行任务。
     * 每次执行时都会传播 MDC 和自定义上下文到工作线程。
     *
     * 固定频率（Fixed Rate）的含义：
     * - 每隔固定时间启动一次任务，不管上一次任务是否完成
     * - 如果任务执行时间 > period，下一次会立即执行（不会并发，会等待上次完成）
     * - 适用于执行时间稳定、需要严格时间间隔的场景
     *
     * 执行时序图：
     * <pre>
     * 时间轴：  0s    5s    10s   15s   20s
     *          |     |     |     |     |
     * 任务1：   [===执行===]
     * 任务2：        |     [===执行===]
     * 任务3：              |     [===执行===]
     * 任务4：                    |     [===执行===]
     *
     * period = 5s，无论任务执行多久，每 5s 启动一次
     * </pre>
     *
     * 与 scheduleWithFixedDelay 的对比：
     * <pre>
     * scheduleAtFixedRate:
     * - 任务开始时间间隔固定
     * - 适合：心跳检测、定时采集、定时同步
     *
     * scheduleWithFixedDelay:
     * - 任务结束到下次开始的间隔固定
     * - 适合：批处理、数据清理、依赖前次完成的任务
     * </pre>
     *
     * 上下文传播机制：
     * - 每次任务执行前都会恢复提交时的 MDC 和自定义上下文
     * - 每次任务执行后都会清理上下文
     * - 如果任务本身修改了 MDC，不会影响下次执行
     *
     * 使用示例：
     * <pre>
     * MDC.put("service", "health-check");
     *
     * // 每 10 秒执行一次健康检查
     * executor.scheduleAtFixedRate(() -> {
     *     String service = MDC.get("service"); // "health-check"
     *     logger.info("执行健康检查"); // 日志包含 service 信息
     *     checkServiceHealth();
     * }, 5, 10, TimeUnit.SECONDS);
     * </pre>
     *
     * @param command      要执行的周期任务
     * @param initialDelay 初始延迟时间
     * @param period       执行周期（固定频率）
     * @param unit         时间单位
     * @return ScheduledFuture 表示周期任务，可用于取消
     */
    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        // 捕获当前线程的 MDC 上下文（只捕获一次，所有执行共享）
        Map<String, String> mdcContextMap = getMdcContextMap();

        // 装饰任务并提交
        return super.scheduleAtFixedRate(ContextPropagator.decorateRunnable(contextPropagators, () -> {
            try {
                // 每次执行前恢复 MDC 上下文
                setMDCContext(mdcContextMap);

                // 执行原始任务
                command.run();
            } finally {
                // 每次执行后清理 MDC
                MDC.clear();
            }
        }), initialDelay, period, unit);
    }

    /**
     * 以固定延迟调度周期任务 - 支持上下文传播
     *
     * 功能说明：
     * 初始延迟后执行任务，每次任务完成后，等待固定延迟时间再执行下次任务。
     * 每次执行时都会传播 MDC 和自定义上下文到工作线程。
     *
     * 固定延迟（Fixed Delay）的含义：
     * - 前一次任务完成后，等待固定时间再开始下一次任务
     * - 保证任务之间有固定的间隔时间
     * - 适用于依赖前次任务完成、需要冷却时间的场景
     *
     * 执行时序图：
     * <pre>
     * 时间轴：  0s    5s    10s   15s   20s   25s
     *          |     |     |     |     |     |
     * 任务1：   [===执行3s===]
     * 延迟：                 |--5s--|
     * 任务2：                       [===执行2s===]
     * 延迟：                                |--5s--|
     * 任务3：                                      [===]
     *
     * delay = 5s，每次任务完成后等待 5s 再开始下次
     * </pre>
     *
     * 与 scheduleAtFixedRate 的对比：
     * <pre>
     * scheduleAtFixedRate:
     * 0s    5s    10s   15s
     * [任务1] [任务2] [任务3]
     * - 固定频率，可能重叠（实际会等待）
     *
     * scheduleWithFixedDelay:
     * 0s    5s    10s   15s   20s
     * [任务1]----[任务2]----[任务3]
     * - 固定间隔，保证冷却时间
     * </pre>
     *
     * 适用场景：
     * - 数据批处理：每次处理完一批数据后，等待一段时间再处理下一批
     * - 定时清理：垃圾回收、临时文件清理等需要完成后再执行的任务
     * - 依赖前次结果：后续任务需要前次任务完成
     *
     * 使用示例：
     * <pre>
     * MDC.put("batchId", "batch-001");
     *
     * // 每次处理完成后等待 30 秒再处理下一批
     * executor.scheduleWithFixedDelay(() -> {
     *     String batchId = MDC.get("batchId"); // "batch-001"
     *     logger.info("处理数据批次"); // 日志包含 batchId
     *     processBatch();
     * }, 10, 30, TimeUnit.SECONDS);
     * </pre>
     *
     * @param command      要执行的周期任务
     * @param initialDelay 初始延迟时间
     * @param delay        任务完成后的延迟时间（固定间隔）
     * @param unit         时间单位
     * @return ScheduledFuture 表示周期任务，可用于取消
     */
    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        // 捕获当前线程的 MDC 上下文
        Map<String, String> mdcContextMap = getMdcContextMap();

        // 装饰任务并提交
        return super.scheduleWithFixedDelay(ContextPropagator.decorateRunnable(contextPropagators, () -> {
            try {
                // 每次执行前恢复 MDC 上下文
                setMDCContext(mdcContextMap);

                // 执行原始任务
                command.run();
            } finally {
                // 每次执行后清理 MDC
                MDC.clear();
            }
        }), initialDelay, delay, unit);
    }

    /**
     * 获取当前线程的 MDC 上下文快照
     *
     * 功能说明：
     * 复制当前线程的 MDC 上下文映射，用于后续传播到工作线程。
     *
     * 为什么需要复制？
     * - MDC ��储在 ThreadLocal 中，线程切换后会丢失
     * - 复制快照可以在工作线程恢复原始上下文
     *
     * 空值处理：
     * - 如果 MDC 为空（null），返回空 Map 而非 null
     * - 避免空指针异常，简化后续处理
     *
     * @return MDC 上下文快照，如果 MDC 为空则返回空 Map
     */
    private Map<String, String> getMdcContextMap() {
        // getCopyOfContextMap() 返回 MDC 的副本，如果为 null 则返回空 Map
        return Optional.ofNullable(MDC.getCopyOfContextMap()).orElse(Collections.emptyMap());
    }

    /**
     * 设置 MDC 上下文到当前线程
     *
     * 功能说明：
     * 将捕获的 MDC 上下文恢复到工作线程的 MDC 中。
     * 在设置前先清理，确保没有残留的上下文。
     *
     * 执行步骤：
     * 1. 清理当前线程的 MDC（避免污染）
     * 2. 如果提供的上下文不为空，设置到 MDC
     *
     * 为什么先清理？
     * - 线程池的线程会被重用
     * - 前一个任务可能留下了 MDC 数据
     * - 清理确保每个任务都是干净的环境
     *
     * @param contextMap 要设置的 MDC 上下文映射，可以为 null
     */
    private void setMDCContext(Map<String, String> contextMap) {
        // 1. 清理当前线程的 MDC，避免前一个任务的残留数据
        MDC.clear();

        // 2. 如果提供了上下文，设置到 MDC
        if (contextMap != null) {
            MDC.setContextMap(contextMap);
        }
    }

    /**
     * 创建 Builder ��例
     *
     * 功能说明：
     * 使用 Builder 模式创建 ContextAwareScheduledThreadPoolExecutor。
     * 提供流畅的 API 来配置线程池参数和上下文传播器。
     *
     * Builder 模式的优势：
     * - 参数配置清晰易读
     * - 支持链式调用
     * - 可选参数灵活配置
     * - 参数校验集中在 build() 方法
     *
     * 使用示例：
     * <pre>
     * ContextAwareScheduledThreadPoolExecutor executor =
     *     ContextAwareScheduledThreadPoolExecutor.newScheduledThreadPool()
     *         .corePoolSize(5)
     *         .contextPropagators(new SecurityContextPropagator())
     *         .build();
     * </pre>
     *
     * @return Builder 实例
     */
    public static Builder newScheduledThreadPool() {
        return new Builder();
    }

    /**
     * Builder 类 - 构建 ContextAwareScheduledThreadPoolExecutor
     *
     * 作用说明：
     * 使用 Builder 模式构建线程池，提供流畅的 API 来配置参数。
     *
     * 设计理念：
     * - 链式调用：每个方法返回 this，支持方法链
     * - 参数校验：在 build() 前验证参数合法性
     * - 默认值：contextPropagators 默认为空列表
     * - 类型安全：使用泛型和可变参数
     *
     * 配置步骤：
     * 1. 设置核心线程池大小（必需）
     * 2. 设置上下文传播器（可选）
     * 3. 调用 build() 创建实例
     *
     * 使用示例：
     * <pre>
     * ContextAwareScheduledThreadPoolExecutor executor =
     *     ContextAwareScheduledThreadPoolExecutor.newScheduledThreadPool()
     *         .corePoolSize(10)
     *         .contextPropagators(
     *             new SecurityContextPropagator(),
     *             new TransactionContextPropagator()
     *         )
     *         .build();
     * </pre>
     */
    public static class Builder {
        /** 上下文传播器列表，默认为空列表 */
        private List<ContextPropagator> contextPropagators = new ArrayList<>();

        /** 核心线程池大小，默认为 0（需要在 build() 前设置） */
        private int corePoolSize;

        /**
         * 设置核心线程池大小
         *
         * 功能说明：
         * 配置线程池的核心线程数量。这些线程会一直存活，即使空闲。
         *
         * 核心线程数的选择：
         * - CPU 密集型任务：核心数 = CPU 核心数 + 1
         * - IO 密集型任务：核心数 = CPU 核心数 * 2
         * - 混合型任务：根据实际测试调整
         *
         * 参数校验：
         * - 必须 >= 1，否则抛出 IllegalArgumentException
         * - ScheduledThreadPoolExecutor 至少需要 1 个线程来执行定时任务
         *
         * 使用示例：
         * <pre>
         * builder.corePoolSize(5); // 设置 5 个核心线程
         * </pre>
         *
         * @param corePoolSize 核心线程数，必须 >= 1
         * @return this，支持链式调用
         * @throws IllegalArgumentException 如果 corePoolSize < 1
         */
        public Builder corePoolSize(int corePoolSize) {
            // 参数校验：核心线程数必须至少为 1
            if (corePoolSize < 1) {
                throw new IllegalArgumentException(
                    "corePoolSize must be a positive integer value >= 1");
            }

            // 保存核心线程数
            this.corePoolSize = corePoolSize;

            // 返回 this 支持链式调用
            return this;
        }

        /**
         * 设置上下文传播器列表
         *
         * 功能说明：
         * 配置用于传播自定义上下文的 ContextPropagator 列表。
         * 除了 MDC（自动传播），还可以传播自定义的 ThreadLocal 变量。
         *
         * 可变参数：
         * - 支持传入多个 ContextPropagator
         * - 如果传入 null 或不传，则使用空列表（只传播 MDC）
         *
         * 上下文传播顺序：
         * - 按照传入的顺序执行传播器
         * - retrieve() 按顺序调用
         * - copy() 按顺序调用
         * - clear() 按顺序调用
         *
         * 常见的上下文传播器：
         * - SecurityContextPropagator：传播 Spring Security 上下文
         * - TransactionContextPropagator：传播事务上下文
         * - RequestContextPropagator：传播 HTTP 请求上下文
         * - CustomContextPropagator：自定义业务上下文
         *
         * 使用示例：
         * <pre>
         * // 传播多个上下文
         * builder.contextPropagators(
         *     new SecurityContextPropagator(),
         *     new RequestContextPropagator()
         * );
         *
         * // 不传播自定义上下文（只传播 MDC）
         * builder.contextPropagators();
         * </pre>
         *
         * @param contextPropagators 上下文传播器数组，可以为 null 或空
         * @return this，支持链式调用
         */
        public Builder contextPropagators(ContextPropagator... contextPropagators) {
            // 如果传入了传播器，转换为列表；否则使用空列表
            this.contextPropagators = contextPropagators != null ?
                Arrays.stream(contextPropagators).collect(toList()) :
                new ArrayList<>();

            // 返回 this 支持链式调用
            return this;
        }

        /**
         * 构建 ContextAwareScheduledThreadPoolExecutor 实例
         *
         * 功能说明：
         * 根据配置的参数创建线程池实例。
         * 这是 Builder 的最后一步，完成构建过程。
         *
         * 前置条件：
         * - 必须调用过 corePoolSize() 设置线程数
         * - contextPropagators 可选，默认为空列表
         *
         * 创建后的线程池：
         * - 使用 NamingThreadFactory 创建命名线程
         * - 线程名格式：ContextAwareScheduledThreadPool-{序号}
         * - 自动传播 MDC 上下文
         * - 根据配置传播自定义上下文
         *
         * 使用示例：
         * <pre>
         * ContextAwareScheduledThreadPoolExecutor executor =
         *     ContextAwareScheduledThreadPoolExecutor.newScheduledThreadPool()
         *         .corePoolSize(5)
         *         .contextPropagators(new SecurityContextPropagator())
         *         .build();
         * </pre>
         *
         * @return ContextAwareScheduledThreadPoolExecutor 实例
         */
        public ContextAwareScheduledThreadPoolExecutor build() {
            // 创建并返回线程池实例
            return new ContextAwareScheduledThreadPoolExecutor(corePoolSize, contextPropagators);
        }
    }
}
