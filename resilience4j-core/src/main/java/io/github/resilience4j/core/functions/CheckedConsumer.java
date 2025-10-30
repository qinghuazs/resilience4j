/*
 *
 *  Copyright 2020 krnSaurabh
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
package io.github.resilience4j.core.functions;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * CheckedConsumer - 支持抛出检查异常的 Consumer
 *
 * <h2>功能说明</h2>
 * CheckedConsumer 类似于 {@link Consumer}，但允许抛出检查异常（Checked Exception）。
 * 这是 Java 标准 Consumer 的增强版本，用于需要抛出检查异常的场景。
 *
 * <h2>为什么需要 CheckedConsumer？</h2>
 * Java 标准的 Consumer 只能抛出运行时异常，不能抛出检查异常（如 IOException）：
 * <pre>
 * // 编译错误：Consumer 不允许抛出 IOException
 * Consumer&lt;String&gt; writeFile = path -> {
 *     Files.writeString(Path.of(path), "content"); // writeString() throws IOException
 * };
 *
 * // CheckedConsumer 可以抛出检查异常
 * CheckedConsumer&lt;String&gt; checkedWrite = path -> {
 *     Files.writeString(Path.of(path), "content"); // OK!
 * };
 * </pre>
 *
 * <h2>使用场景</h2>
 * <ul>
 *   <li>数据处理：处理输入数据，可能抛出异常</li>
 *   <li>文件写入：将数据写入文件</li>
 *   <li>网络发送：发送数据到远程服务器</li>
 *   <li>数据库操作：保存或更新实体</li>
 *   <li>事件处理：处理事件，可能失败</li>
 *   <li>Stream 操作：forEach() 等终端操作</li>
 * </ul>
 *
 * <h2>核心方法</h2>
 * <ul>
 *   <li><b>accept(T)</b>：消费输入，可能抛出检查异常</li>
 *   <li><b>andThen(CheckedConsumer)</b>：链式组合多个消费者</li>
 *   <li><b>unchecked()</b>：转换为标准 Consumer，使用 sneaky throw</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>
 * // 创建 CheckedConsumer
 * CheckedConsumer&lt;String&gt; writeLog = message -> {
 *     Files.writeString(
 *         Path.of("/var/log/app.log"),
 *         message + "\n",
 *         StandardOpenOption.APPEND
 *     ); // 可能抛出 IOException
 * };
 *
 * // 执行并处理异常
 * try {
 *     writeLog.accept("Application started");
 * } catch (Throwable e) {
 *     logger.error("Failed to write log", e);
 * }
 *
 * // 链式组合
 * CheckedConsumer&lt;User&gt; saveUser = user -> userRepository.save(user);
 * CheckedConsumer&lt;User&gt; sendEmail = user -> emailService.sendWelcome(user);
 *
 * CheckedConsumer&lt;User&gt; registerUser = saveUser.andThen(sendEmail);
 * registerUser.accept(newUser); // 先保存，再发送邮件
 *
 * // 转换为标准 Consumer 用于 Stream
 * CheckedConsumer&lt;String&gt; processFile = path -> {
 *     String content = Files.readString(Path.of(path));
 *     // 处理内容...
 * };
 *
 * List&lt;String&gt; filePaths = Arrays.asList("/file1.txt", "/file2.txt");
 * filePaths.forEach(processFile.unchecked());
 * </pre>
 *
 * <h2>与其他 Checked 接口的对比</h2>
 * <pre>
 * CheckedRunnable         - 无参数，无返回值，可能抛出异常
 * CheckedSupplier&lt;R&gt;      - 无参数，返回 R，可能抛出异常
 * CheckedConsumer&lt;T&gt;      - 接受 T，无返回值，可能抛出异常
 * CheckedFunction&lt;T, R&gt;   - 接受 T，返回 R，可能抛出异常
 * </pre>
 *
 * @param <T> 输入参数类型
 * @author krnSaurabh
 * @see Consumer
 * @see CheckedFunction
 * @see CheckedRunnable
 */
@FunctionalInterface
public interface CheckedConsumer<T> {

    /**
     * 消费输入 - 可能抛出检查异常
     *
     * 功能说明：
     * 接受一个输入参数 T，执行操作，不返回值。
     * 允许抛出任何异常（包括检查异常）。
     *
     * 使用示例：
     * <pre>
     * // 写入文件
     * CheckedConsumer&lt;String&gt; writeFile = data ->
     *     Files.writeString(Path.of("/output.txt"), data);
     *
     * // 发送邮件
     * CheckedConsumer&lt;User&gt; sendWelcomeEmail = user -> {
     *     EmailMessage message = new EmailMessage(
     *         user.getEmail(),
     *         "Welcome!",
     *         "Welcome to our service"
     *     );
     *     emailService.send(message); // 可能抛出 MessagingException
     * };
     *
     * // 数据库保存
     * CheckedConsumer&lt;Order&gt; saveOrder = order -> {
     *     Connection conn = dataSource.getConnection();
     *     try {
     *         orderDao.insert(conn, order);
     *     } finally {
     *         conn.close();
     *     }
     * };
     * </pre>
     *
     * @param t 输入参数
     * @throws Throwable 任何异常（包括检查异常）
     */
    void accept(T t) throws Throwable;

    /**
     * 链式组合 - 在当前消费者后执行另一个消费者
     *
     * 功能说明：
     * 先执行当前消费者，然后执行 after 消费者。
     * 这是函数式编程中的组合模式（Function Composition）。
     *
     * 执行流程：
     * 1. 调用当前消费者的 accept(t)
     * 2. 调用 after 消费者的 accept(t)
     * 3. 如果任一步骤抛出异常，后续步骤不会执行
     *
     * 使用示例：
     * <pre>
     * // 验证、保存、发送通知的流程
     * CheckedConsumer&lt;User&gt; validate = user -> {
     *     if (user.getEmail() == null) {
     *         throw new ValidationException("Email is required");
     *     }
     * };
     *
     * CheckedConsumer&lt;User&gt; save = user ->
     *     userRepository.save(user);
     *
     * CheckedConsumer&lt;User&gt; notify = user ->
     *     notificationService.sendRegistration(user);
     *
     * // 组合成完整流程
     * CheckedConsumer&lt;User&gt; registerUser = validate
     *     .andThen(save)
     *     .andThen(notify);
     *
     * // 执行
     * registerUser.accept(newUser);
     * </pre>
     *
     * @param after 后续消费者
     * @return 组合后的新 CheckedConsumer
     * @throws NullPointerException 如果 after 为 null
     */
    default CheckedConsumer<T> andThen(CheckedConsumer<? super T> after) {
        Objects.requireNonNull(after, "after is null");
        return (T t) -> { accept(t); after.accept(t); };
    }

    /**
     * 转换为标准 Consumer - 使用 sneaky throw 绕过检查异常
     *
     * 功能说明：
     * 将 CheckedConsumer 转换为标准的 Consumer，但保持抛出检查异常的能力。
     * 使用 "sneaky throw" 技术绕过 Java 的检查异常限制。
     *
     * Sneaky Throw 技术：
     * 利用 Java 泛型擦除的特性，在运行时抛出检查异常，但编译器不检查。
     * 这是一个高级技巧，使用时需谨慎。
     *
     * 使用场景：
     * - 需要传递给只接受 Consumer 的 API
     * - Stream.forEach() 等终端操作
     * - Iterable.forEach() 方法
     * - Optional.ifPresent() 方法
     *
     * 使用示例：
     * <pre>
     * CheckedConsumer&lt;String&gt; checked = path ->
     *     Files.delete(Path.of(path));
     *
     * // 转换为标准 Consumer
     * Consumer&lt;String&gt; unchecked = checked.unchecked();
     *
     * // 用于 Stream.forEach()
     * List&lt;String&gt; filesToDelete = Arrays.asList("/tmp/file1", "/tmp/file2");
     * filesToDelete.stream().forEach(unchecked);
     *
     * // 用于 Iterable.forEach()
     * filesToDelete.forEach(unchecked);
     *
     * // 用于 Optional.ifPresent()
     * Optional&lt;String&gt; tempFile = getTempFile();
     * tempFile.ifPresent(unchecked);
     *
     * // 用于 Map.forEach()
     * CheckedConsumer&lt;Map.Entry&lt;String, String&gt;&gt; processEntry = entry ->
     *     processKeyValue(entry.getKey(), entry.getValue());
     * map.forEach((k, v) -> processEntry.unchecked().accept(Map.entry(k, v)));
     * </pre>
     *
     * 注意事项：
     * - 虽然返回 Consumer，但仍可能抛出检查异常
     * - 调用方需要捕获 Throwable 或使用 try-catch
     * - 在 Stream 中使用时，异常会终止流处理
     *
     * @return 标准 Consumer，内部使用 sneaky throw
     */
    default Consumer<T> unchecked() {
        return t -> {
            try {
                accept(t);
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
     * - 对于 CheckedConsumer，返回值不会被使用
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
