/*  __    __  __  __    __  ___
 * \  \  /  /    \  \  /  /  __/
 *  \  \/  /  /\  \  \/  /  /
 *   \____/__/  \__\____/__/
 *
 * Copyright 2014-2019 Vavr, http://vavr.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.resilience4j.core.functions;

/**
 * CheckedRunnable - 支持抛出检查异常的 Runnable
 *
 * <h2>功能说明</h2>
 * CheckedRunnable 类似于 {@link Runnable}，但允许抛出检查异常（Checked Exception）。
 * 这是 Java 标准 Runnable 的增强版本，用于需要抛出检查异常的场景。
 *
 * <h2>为什么需要 CheckedRunnable？</h2>
 * Java 标准的 Runnable 只能抛出运行时异常，不能抛出检查异常（如 IOException）：
 * <pre>
 * // 编译错误：Runnable 不允许抛出 IOException
 * Runnable runnable = () -> {
 *     Files.delete(Path.of("/temp/file.txt")); // delete() throws IOException
 * };
 *
 * // CheckedRunnable 可以抛出检查异常
 * CheckedRunnable checkedRunnable = () -> {
 *     Files.delete(Path.of("/temp/file.txt")); // OK!
 * };
 * </pre>
 *
 * <h2>使用场景</h2>
 * <ul>
 *   <li>后台任务：执行可能失败的后台操作</li>
 *   <li>异步执行：提交到线程池的任务</li>
 *   <li>清理操作：删除临时文件、关闭资源</li>
 *   <li>定时任务：定期执行的维护操作</li>
 *   <li>批处理：批量处理数据</li>
 *   <li>Resilience4j 装饰器：与断路器、重试等组合使用</li>
 * </ul>
 *
 * <h2>核心方法</h2>
 * <ul>
 *   <li><b>run()</b>：执行操作，可能抛出检查异常</li>
 *   <li><b>unchecked()</b>：转换为标准 Runnable，使用 sneaky throw</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>
 * // 创建 CheckedRunnable
 * CheckedRunnable cleanup = () -> {
 *     Files.delete(Path.of("/temp/cache.dat")); // 可能抛出 IOException
 *     logger.info("Cleanup completed");
 * };
 *
 * // 与 Resilience4j 组合
 * CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("backend");
 * CheckedRunnable decorated = CircuitBreaker.decorateCheckedRunnable(
 *     circuitBreaker,
 *     cleanup
 * );
 *
 * // 执行并处理异常
 * try {
 *     decorated.run();
 * } catch (Throwable e) {
 *     logger.error("Cleanup failed", e);
 * }
 *
 * // 转换为标准 Runnable 提交到线程池
 * ExecutorService executor = Executors.newFixedThreadPool(10);
 * CheckedRunnable task = () -> processFile("/data/input.csv");
 * executor.submit(task.unchecked());
 *
 * // 用于定时任务
 * ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
 * CheckedRunnable maintenance = () -> cleanupOldData();
 * scheduler.scheduleAtFixedRate(
 *     maintenance.unchecked(),
 *     0,
 *     1,
 *     TimeUnit.HOURS
 * );
 * </pre>
 *
 * <h2>与其他 Checked 接口的对比</h2>
 * <pre>
 * CheckedRunnable         - 无参数，无返回值，可能抛出异常
 * CheckedSupplier&lt;R&gt;      - 无参数，返回 R，可能抛出异常
 * CheckedFunction&lt;T, R&gt;   - 接受 T，返回 R，可能抛出异常
 * CheckedConsumer&lt;T&gt;      - 接受 T，无返回值，可能抛出异常
 * </pre>
 *
 * <h2>线程安全性</h2>
 * CheckedRunnable 本身是函数式接口，线程安全性取决于实现：
 * - 如果不访问共享状态，则线程安全
 * - 如果访问共享状态，需要适当的同步机制
 *
 * @author Vavr 团队
 * @see Runnable
 * @see CheckedSupplier
 * @see CheckedFunction
 */
@FunctionalInterface
public interface CheckedRunnable {

    /**
     * 执行操作 - 可能抛出检查异常
     *
     * 功能说明：
     * 执行操作，不接受参数，也不返回值。
     * 允许抛出任何异常（包括检查异常）。
     *
     * 使用示例：
     * <pre>
     * // 文件删除操作
     * CheckedRunnable deleteFile = () ->
     *     Files.delete(Path.of("/temp/cache.dat"));
     *
     * // 数据同步操作
     * CheckedRunnable syncData = () -> {
     *     Connection conn = dataSource.getConnection();
     *     try {
     *         Statement stmt = conn.createStatement();
     *         stmt.execute("REFRESH MATERIALIZED VIEW user_stats");
     *     } finally {
     *         conn.close();
     *     }
     * };
     *
     * // 批量处理操作
     * CheckedRunnable batchProcess = () -> {
     *     List&lt;Record&gt; records = loadRecords();
     *     for (Record record : records) {
     *         processRecord(record); // 可能抛出异常
     *     }
     * };
     * </pre>
     *
     * @throws Throwable 任何异常（包括检查异常）
     */
    void run() throws Throwable;

    /**
     * 转换为标准 Runnable - 使用 sneaky throw 绕过检查异常
     *
     * 功能说明：
     * 将 CheckedRunnable 转换为标准的 Runnable，但保持抛出检查异常的能力。
     * 使用 "sneaky throw" 技术绕过 Java 的检查异常限制。
     *
     * Sneaky Throw 技术：
     * 利用 Java 泛型擦除的特性，在运行时抛出检查异常，但编译器不检查。
     * 这是一个高级技巧，使用时需谨慎。
     *
     * 使用场景：
     * - 提交到线程池：ExecutorService.submit() 需要 Runnable
     * - 定时任务：ScheduledExecutorService 需要 Runnable
     * - 异步执行：CompletableFuture.runAsync() 需要 Runnable
     * - 事件处理：某些事件框架需要 Runnable
     *
     * 使用示例：
     * <pre>
     * CheckedRunnable checked = () -> Files.delete(Path.of("/temp/file.txt"));
     *
     * // 转换为标准 Runnable
     * Runnable unchecked = checked.unchecked();
     *
     * // 提交到线程池
     * ExecutorService executor = Executors.newCachedThreadPool();
     * executor.submit(unchecked);
     *
     * // 定时执行
     * ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
     * scheduler.scheduleWithFixedDelay(unchecked, 0, 5, TimeUnit.MINUTES);
     *
     * // 异步执行
     * CompletableFuture&lt;Void&gt; future = CompletableFuture.runAsync(unchecked);
     *
     * // 在新线程中运行
     * Thread thread = new Thread(unchecked);
     * thread.start();
     * </pre>
     *
     * 注意事项：
     * - 虽然返回 Runnable，但仍可能抛出检查异常
     * - 在线程池中使用时，异常会被捕获并记录
     * - 调用方需要适当处理异常情况
     *
     * @return 标准 Runnable，内部使用 sneaky throw
     */
    default Runnable unchecked() {
        return () -> {
            try {
                run();
            } catch(Throwable x) {
                sneakyThrow(x);
            }
        };
    }

    /**
     * Sneaky Throw - 绕过 Java 检查异常机制
     *
     * 功能说明：
     * 利用 Java 泛型擦除，在运行时抛出检查异常而不需要声明。
     * 这是一个编译器技巧，让检查异常表现得像运行时异常。
     *
     * 工作原理：
     * 1. 方法声明为泛型 {@code <T extends Throwable>}
     * 2. 编译时 T 被推断为 RuntimeException（不需要声明）
     * 3. 运行时泛型被擦除，实际抛出的是原始异常
     *
     * 特殊性：
     * - 声明返回类型为 R，但实际永远不会返回
     * - 这样设计是为了让编译器允许在任何需要返回值的地方使用
     * - 对于 CheckedRunnable，返回值不会被使用
     *
     * @param <T> 异常类型
     * @param <R> 返回值类型（实际永远不会返回）
     * @param t   要抛出的异常
     * @return 永远不会返回，总是抛出异常
     * @throws T 抛出的异常
     */
    @SuppressWarnings("unchecked")
    static <T extends Throwable, R> R sneakyThrow(Throwable t) throws T {
        throw (T) t;
    }
}
