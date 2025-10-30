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

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 命名线程工厂 - 创建具有自定义命名模式的线程
 *
 * 作用说明：
 * 实现 ThreadFactory 接口，为线程池创建的线程提供有意义的名称。
 * 线程名称格式："{name}-{序号}"，例如 "resilience4j-1", "resilience4j-2"。
 *
 * 为什么需要线程命名？
 * 1. 问题诊断：通过线程名快速定位问题所在的线程池
 * 2. 性能分析：在 JProfiler、VisualVM 等工具中更容易识别线程
 * 3. 日志追踪：日志中显示有意义的线程名，便于问题排查
 * 4. 监控告警：根据线程名进行监控和告警
 *
 * 设计理念：
 * - 基于 {@link Executors#defaultThreadFactory()} 的实现
 * - 保持线程的默认设置（非守护线程、正常优先级）
 * - 使用原子计数器保证线程编号的唯一性和线程安全性
 *
 * 使用场景：
 * - 为 Resilience4j 的内部线程池提供命名
 * - 为断路器的状态转换线程命名
 * - 为异步操作的执行器命名
 * - 任何需要自定义线程名称的场景
 *
 * 与默认 ThreadFactory 的对比：
 * <pre>
 * Executors.defaultThreadFactory():
 * - 线程名：pool-N-thread-M
 * - 难以区分不同用途的线程池
 *
 * NamingThreadFactory("circuit-breaker"):
 * - 线程名：circuit-breaker-1, circuit-breaker-2, ...
 * - 清晰标识线程的用途
 * </pre>
 *
 * 使用示例：
 * <pre>
 * // 创建带命名的线程池
 * ThreadFactory threadFactory = new NamingThreadFactory("my-service");
 * ExecutorService executor = Executors.newFixedThreadPool(5, threadFactory);
 *
 * // 创建的线程名称：my-service-1, my-service-2, my-service-3, ...
 * executor.submit(() -&gt; {
 *     String threadName = Thread.currentThread().getName();
 *     System.out.println("Running in: " + threadName);
 *     // 输出: Running in: my-service-1
 * });
 *
 * // Resilience4j 中的实际使用
 * ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(
 *     1,
 *     new NamingThreadFactory("circuit-breaker-state-transition")
 * );
 * </pre>
 *
 * 线程属性设置：
 * - 守护线程：false（非守护线程，不会随 JVM 退出而立即终止）
 * - 优先级：NORM_PRIORITY（5，正常优先级）
 * - 线程组：继承创建者的线程组或安全管理器指定的线程组
 *
 * 线程安全性：
 * - 使用 AtomicInteger 保证线程编号的原子性
 * - 可以在多线程环境中安全使用
 * - 不同 NamingThreadFactory 实例的计数器相互独立
 *
 * 注意事项：
 * - 线程编号从 1 开始，而不是 0
 * - 线程编号会持续递增，不会重置
 * - 线程名称不包含时间戳，如果需要可以自行扩展
 *
 * @author krnsaurabh
 * @since 1.5.0
 */
public class NamingThreadFactory implements ThreadFactory {

    /** 线程组，用于组织线程 */
    private final ThreadGroup group;

    /** 线程编号计数器，保证线程名称唯一性 */
    private final AtomicInteger threadNumber = new AtomicInteger(1);

    /** 线程名称前缀 */
    private final String prefix;

    /**
     * 构造命名线程工厂
     *
     * @param name 线程名称前缀，生成的线程名格式为 "{name}-{序号}"
     */
    public NamingThreadFactory(String name) {
        this.group = getThreadGroup();
        // 添加分隔符 "-"，生成格式如 "name-1", "name-2"
        this.prefix = String.join("-", name, "");
    }

    /**
     * 获取线程组
     *
     * 功能说明：
     * 优先使用安全管理器指定的线程组，否则使用当前线程的线程组。
     * 这是 Java 安全模型的一部分，确保线程在正确的安全上下文中运行。
     *
     * 为什么需要检查 SecurityManager？
     * - 在某些安全受限的环境（如 Applet、Web 容器）中，SecurityManager 会限制线程操作
     * - 遵循安全管理器的线程组策略，避免安全违规
     *
     * @return 线程组对象
     */
    private ThreadGroup getThreadGroup() {
        SecurityManager security = System.getSecurityManager();
        return security != null ? security.getThreadGroup()
            : Thread.currentThread().getThreadGroup();
    }

    /**
     * 创建新线程
     *
     * 功能说明：
     * 创建一个新线程，设置线程名称、线程组、守护状态和优先级。
     * 确保创建的线程具有一致的、可预测的属性。
     *
     * 线程属性：
     * - 线程组：使用构造函数中确定的线程组
     * - 线程名：{prefix}{编号}，如 "my-service-1"
     * - 守护状态：非守护线程（即使父线程是守护线程）
     * - 优先级：正常优先级（NORM_PRIORITY = 5）
     * - 栈大小：0（使用 JVM 默认值）
     *
     * 为什么重置守护状态和优先级？
     * - 新线程默认继承创建者线程的属性
     * - 重置确保线程池中的线程具有一致的行为
     * - 避免因父线程的特殊设置影响线程池的运行
     *
     * @param runnable 要执行的任务
     * @return 创建的线程对象
     */
    @Override
    public Thread newThread(Runnable runnable) {
        // 创建线程：线程组、任务、名称、栈大小
        Thread thread = new Thread(group, runnable, createName(), 0);

        // 确保是非守护线程（守护线程会随主线程退出而终止）
        if (thread.isDaemon()) {
            thread.setDaemon(false);
        }

        // 确保使用正常优先级
        if (thread.getPriority() != Thread.NORM_PRIORITY) {
            thread.setPriority(Thread.NORM_PRIORITY);
        }

        return thread;
    }

    /**
     * 创建线程名称
     *
     * 功能说明：
     * 生成格式为 "{prefix}{序号}" 的线程名称。
     * 使用原子计数器保证线程编号的唯一性和递增性。
     *
     * 线程名称示例：
     * - 如果 prefix = "worker-"，生成：worker-1, worker-2, worker-3, ...
     * - 如果 prefix = "circuit-breaker-"，生成：circuit-breaker-1, circuit-breaker-2, ...
     *
     * @return 唯一的线程名称
     */
    private String createName() {
        return prefix + threadNumber.getAndIncrement();
    }
}
