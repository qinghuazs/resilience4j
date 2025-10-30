package io.github.resilience4j.core.functions;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * OnceConsumer - 确保消费操作只执行一次的包装器
 *
 * <h2>功能说明</h2>
 * OnceConsumer 是一个线程安全的包装器，确保对给定对象的消费操作只执行一次。
 * 无论调用多少次 applyOnce()，实际的消费操作只会在第一次调用时执行。
 *
 * <h2>为什么需要 OnceConsumer？</h2>
 * 在并发或异步编程中，某些操作只应执行一次，例如：
 * - 初始化资源（打开连接、加载配置）
 * - 发送通知（避免重复通知）
 * - 状态转换（只能发生一次的状态变化）
 * - 回调执行（确保回调只被调用一次）
 *
 * <h2>核心特性</h2>
 * <ul>
 *   <li><b>线程安全</b>：使用 AtomicBoolean 确保并发场景下的正确性</li>
 *   <li><b>幂等性</b>：多次调用 applyOnce() 等同于调用一次</li>
 *   <li><b>不可变性</b>：包装的对象 T 在创建时确定，不能更改</li>
 *   <li><b>无阻塞</b>：使用 CAS 操作，不需要加锁</li>
 * </ul>
 *
 * <h2>使用场景</h2>
 * <ul>
 *   <li>资源初始化：确保数据库连接只建立一次</li>
 *   <li>配置加载：避免重复加载配置文件</li>
 *   <li>通知发送：防止重复发送邮件或推送通知</li>
 *   <li>回调执行：确保 CompletableFuture 回调只执行一次</li>
 *   <li>状态初始化：确保组件只初始化一次</li>
 *   <li>清理操作：确保资源只释放一次</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>
 * // 示例1：确保列表元素只添加一次
 * List&lt;String&gt; messages = new ArrayList&lt;&gt;();
 * OnceConsumer&lt;List&lt;String&gt;&gt; once = OnceConsumer.of(messages);
 *
 * // 多次调用，但只会执行一次
 * once.applyOnce(list -> list.add("Hello World"));
 * once.applyOnce(list -> list.add("Hello World"));
 * once.applyOnce(list -> list.add("Hello World"));
 *
 * // 结果：messages 只包含一个元素 "Hello World"
 * assertThat(messages).hasSize(1).contains("Hello World");
 *
 * // 示例2：确保数据库连接只初始化一次
 * OnceConsumer&lt;DataSource&gt; initDb = OnceConsumer.of(dataSource);
 *
 * // 多个线程同时调用，但只会初始化一次
 * ExecutorService executor = Executors.newFixedThreadPool(10);
 * for (int i = 0; i < 100; i++) {
 *     executor.submit(() ->
 *         initDb.applyOnce(ds -> {
 *             logger.info("Initializing database...");
 *             ds.initialize(); // 只会执行一次
 *         })
 *     );
 * }
 *
 * // 示例3：确保清理操作只执行一次
 * OnceConsumer&lt;Connection&gt; closeOnce = OnceConsumer.of(connection);
 *
 * // 无论调用多少次，连接只会关闭一次
 * closeOnce.applyOnce(conn -> {
 *     logger.info("Closing connection");
 *     conn.close();
 * });
 * closeOnce.applyOnce(conn -> conn.close()); // 不会执行
 *
 * // 示例4：确保通知只发送一次
 * OnceConsumer&lt;User&gt; sendNotification = OnceConsumer.of(user);
 * sendNotification.applyOnce(u -> {
 *     emailService.sendWelcome(u.getEmail());
 *     logger.info("Welcome email sent to {}", u.getEmail());
 * });
 * sendNotification.applyOnce(u -> emailService.sendWelcome(u.getEmail())); // 不会执行
 * </pre>
 *
 * <h2>线程安全性</h2>
 * <p>
 * OnceConsumer 是线程安全的：
 * </p>
 * <ul>
 *   <li>使用 {@link AtomicBoolean#compareAndSet(boolean, boolean)} 确保原子性</li>
 *   <li>多个线程同时调用 applyOnce()，只有一个会成功执行消费操作</li>
 *   <li>其他线程的调用会直接返回，不会执行消费操作</li>
 *   <li>不需要外部同步机制</li>
 * </ul>
 *
 * <h2>实现原理</h2>
 * <pre>
 * 1. 使用 AtomicBoolean 标记是否已执行
 * 2. applyOnce() 使用 compareAndSet(false, true) 尝试更改状态
 * 3. 只有第一个成功更改状态的调用会执行消费操作
 * 4. 后续调用会因为 CAS 失败而直接返回
 * </pre>
 *
 * <h2>与其他模式的对比</h2>
 * <pre>
 * OnceConsumer     - 确保操作只执行一次，线程安全
 * Lazy&lt;T&gt;          - 延迟初始化，值只计算一次
 * AtomicReference  - 提供原子更新，但不保证只执行一次
 * synchronized     - 可以实现类似功能，但需要加锁，性能较差
 * </pre>
 *
 * <h2>注意事项</h2>
 * <ul>
 *   <li>一旦执行过，就无法重置状态（不可逆）</li>
 *   <li>包装的对象 T 不能更改</li>
 *   <li>如果消费操作抛出异常，状态仍会变为已执行</li>
 *   <li>不适合需要重复执行的场景</li>
 * </ul>
 *
 * @param <T> 被消费对象的类型
 * @author Resilience4j 团队
 * @see AtomicBoolean
 * @see Consumer
 */
public final class OnceConsumer<T> {

    /** 被消费的对象 */
    final T t;

    /** 标记是否已经执行过消费操作 */
    private final AtomicBoolean hasRun = new AtomicBoolean(false);

    /**
     * 私有构造函数 - 使用 of() 工厂方法创建实例
     *
     * @param t 被消费的对象
     */
    private OnceConsumer(T t) {
        this.t = t;
    }

    /**
     * 创建 OnceConsumer 实例 - 工厂方法
     *
     * 功能说明：
     * 创建一个新的 OnceConsumer 实例，包装给定的对象。
     * 使用工厂方法而不是公共构造函数，符合 API 设计最佳实践。
     *
     * 使用示例：
     * <pre>
     * // 包装列表
     * List&lt;String&gt; list = new ArrayList&lt;&gt;();
     * OnceConsumer&lt;List&lt;String&gt;&gt; once = OnceConsumer.of(list);
     *
     * // 包装数据库连接
     * Connection connection = dataSource.getConnection();
     * OnceConsumer&lt;Connection&gt; closeOnce = OnceConsumer.of(connection);
     *
     * // 包装用户对象
     * User user = new User("john@example.com");
     * OnceConsumer&lt;User&gt; notifyOnce = OnceConsumer.of(user);
     * </pre>
     *
     * @param t   被包装的对象
     * @param <T> 对象类型
     * @return OnceConsumer 实例
     */
    public static <T> OnceConsumer<T> of(T t) {
        return new OnceConsumer<>(t);
    }

    /**
     * 应用消费操作 - 只执行一次
     *
     * 功能说明：
     * 对包装的对象应用给定的消费操作。
     * 无论调用多少次，实际的消费操作只会在第一次调用时执行。
     *
     * 执行流程：
     * 1. 使用 compareAndSet(false, true) 尝试将 hasRun 从 false 改为 true
     * 2. 如果成功（返回 true），说明这是第一次调用，执行消费操作
     * 3. 如果失败（返回 false），说明已经执行过，直接返回
     *
     * 线程安全：
     * - 使用 AtomicBoolean 的 CAS 操作确保线程安全
     * - 多个线程同时调用时，只有一个会成功执行
     * - 其他线程的调用会立即返回，不会阻塞
     *
     * 使用示例：
     * <pre>
     * List&lt;String&gt; messages = new ArrayList&lt;&gt;();
     * OnceConsumer&lt;List&lt;String&gt;&gt; once = OnceConsumer.of(messages);
     *
     * // 第一次调用：会执行
     * once.applyOnce(list -> {
     *     logger.info("Adding message"); // 会打印
     *     list.add("Hello World");       // 会执行
     * });
     *
     * // 第二次调用：不会执行
     * once.applyOnce(list -> {
     *     logger.info("Adding message"); // 不会打印
     *     list.add("Hello World");       // 不会执行
     * });
     *
     * // 并发场景
     * OnceConsumer&lt;AtomicInteger&gt; counter = OnceConsumer.of(new AtomicInteger(0));
     * ExecutorService executor = Executors.newFixedThreadPool(10);
     *
     * // 提交100个任务，但只会执行一次
     * for (int i = 0; i < 100; i++) {
     *     executor.submit(() ->
     *         counter.applyOnce(count -> count.incrementAndGet())
     *     );
     * }
     * executor.shutdown();
     * executor.awaitTermination(1, TimeUnit.SECONDS);
     *
     * // 结果：counter 的值为 1（只增加了一次）
     * assertThat(counter.t.get()).isEqualTo(1);
     * </pre>
     *
     * 注意事项：
     * - 如果消费操作抛出异常，状态仍会变为已执行
     * - 异常不会被捕获，会向上传播
     * - 一旦执行过，无法重置状态
     *
     * @param consumer 消费操作，只会被执行一次
     */
    public void applyOnce(Consumer<T> consumer) {
        // 使用 CAS 操作尝试将 hasRun 从 false 改为 true
        // 只有第一个成功的调用会执行消费操作
        if (hasRun.compareAndSet(false, true)) {
            consumer.accept(t);
        }
        // 如果 CAS 失败（hasRun 已经是 true），直接返回，不执行任何操作
    }
}