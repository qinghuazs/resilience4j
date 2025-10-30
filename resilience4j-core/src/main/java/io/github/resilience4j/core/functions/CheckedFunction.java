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

import java.util.function.Function;

/**
 * CheckedFunction - 支持抛出检查异常的 Function
 *
 * <h2>功能说明</h2>
 * CheckedFunction 类似于 {@link Function}，但允许抛出检查异常（Checked Exception）。
 * 这是 Java 标准 Function 的增强版本，用于需要抛出检查异常的场景。
 *
 * <h2>为什么需要 CheckedFunction？</h2>
 * Java 标准的 Function 只能抛出运行时异常，不能抛出检查异常（如 IOException）：
 * <pre>
 * // 编译错误：Function 不允许抛出 IOException
 * Function&lt;String, String&gt; function = path -> {
 *     return Files.readString(Path.of(path)); // readString() throws IOException
 * };
 *
 * // CheckedFunction 可以抛出检查异常
 * CheckedFunction&lt;String, String&gt; checkedFunction = path -> {
 *     return Files.readString(Path.of(path)); // OK!
 * };
 * </pre>
 *
 * <h2>使用场景</h2>
 * <ul>
 *   <li>数据转换：将输入数据转换为输出，可能抛出异常</li>
 *   <li>文件处理：根据文件路径读取内容</li>
 *   <li>数据库操作：将 ID 转换为实体对象</li>
 *   <li>网络调用：将请求参数转换为响应结果</li>
 *   <li>Resilience4j 装饰器：与断路器、重试等组合使用</li>
 * </ul>
 *
 * <h2>核心方法</h2>
 * <ul>
 *   <li><b>apply(T)</b>：应用转换函数，可能抛出检查异常</li>
 *   <li><b>unchecked()</b>：转换为标准 Function，使用 sneaky throw</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>
 * // 创建 CheckedFunction
 * CheckedFunction&lt;String, String&gt; readFile = path -> {
 *     return Files.readString(Path.of(path)); // 可能抛出 IOException
 * };
 *
 * // 与 Resilience4j 组合
 * CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("backend");
 * CheckedFunction&lt;String, String&gt; decorated = CircuitBreaker.decorateCheckedFunction(
 *     circuitBreaker,
 *     readFile
 * );
 *
 * // 执行并处理异常
 * try {
 *     String content = decorated.apply("/path/to/file.txt");
 * } catch (Throwable e) {
 *     logger.error("Failed to read file", e);
 * }
 *
 * // 转换为标准 Function 用于 Stream API
 * Function&lt;String, String&gt; unchecked = readFile.unchecked();
 * List&lt;String&gt; contents = paths.stream()
 *     .map(unchecked)
 *     .collect(Collectors.toList());
 * </pre>
 *
 * <h2>与其他 Checked 接口的对比</h2>
 * <pre>
 * CheckedSupplier&lt;R&gt;      - 无参数，返回 R，可能抛出异常
 * CheckedFunction&lt;T, R&gt;   - 接受 T，返回 R，可能抛出异常
 * CheckedRunnable         - 无参数，无返回值，可能抛出异常
 * CheckedConsumer&lt;T&gt;      - 接受 T，无返回值，可能抛出异常
 * </pre>
 *
 * @param <T> 输入参数类型
 * @param <R> 返回值类型
 * @author Vavr 团队
 * @see Function
 * @see CheckedSupplier
 * @see CheckedRunnable
 */
@FunctionalInterface
public interface CheckedFunction<T, R> {

    /**
     * 应用转换函数 - 可能抛出检查异常
     *
     * 功能说明：
     * 接受一个输入参数 T，执行转换操作，返回结果 R。
     * 允许抛出任何异常（包括检查异常）。
     *
     * 使用示例：
     * <pre>
     * // 文件路径转换为内容
     * CheckedFunction&lt;String, String&gt; readFile = path ->
     *     Files.readString(Path.of(path));
     *
     * // 用户 ID 转换为用户对象
     * CheckedFunction&lt;Long, User&gt; findUser = id ->
     *     userRepository.findById(id).orElseThrow();
     *
     * // 字符串解析为数字
     * CheckedFunction&lt;String, Integer&gt; parse = str -> {
     *     if (str == null || str.isEmpty()) {
     *         throw new IllegalArgumentException("Empty input");
     *     }
     *     return Integer.parseInt(str);
     * };
     * </pre>
     *
     * @param t 输入参数
     * @return 转换后的结果
     * @throws Throwable 任何异常（包括检查异常）
     */
    R apply(T t) throws Throwable;

    /**
     * 转换为标准 Function - 使用 sneaky throw 绕过检查异常
     *
     * 功能说明：
     * 将 CheckedFunction 转换为标准的 Function，但保持抛出检查异常的能力。
     * 使用 "sneaky throw" 技术绕过 Java 的检查异常限制。
     *
     * Sneaky Throw 技术：
     * 利用 Java 泛型擦除的特性，在运行时抛出检查异常，但编译器不检查。
     * 这是一个高级技巧，使用时需谨慎。
     *
     * 使用场景：
     * - 需要传递给只接受 Function 的 API
     * - 与 Stream API 等标准库集成
     * - 函数式编程中的链式调用
     *
     * 使用示例：
     * <pre>
     * CheckedFunction&lt;String, String&gt; checked = path ->
     *     Files.readString(Path.of(path));
     *
     * // 转换为标准 Function
     * Function&lt;String, String&gt; unchecked = checked.unchecked();
     *
     * // 可以用于 Stream API
     * List&lt;String&gt; paths = Arrays.asList("/file1.txt", "/file2.txt");
     * List&lt;String&gt; contents = paths.stream()
     *     .map(unchecked)
     *     .collect(Collectors.toList());
     *
     * // 用于 Optional
     * Optional&lt;String&gt; path = Optional.of("/config.txt");
     * Optional&lt;String&gt; content = path.map(unchecked);
     * </pre>
     *
     * 注意事项：
     * - 虽然返回 Function，但仍可能抛出检查异常
     * - 调用方需要捕获 Throwable 或使用 try-catch
     * - 在 Stream 中使用时，异常会终止流处理
     *
     * @return 标准 Function，内部使用 sneaky throw
     */
    default Function<T, R> unchecked() {
        return t1 -> {
            try {
                return apply(t1);
            } catch(Throwable t) {
                return sneakyThrow(t);
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
     * 使用示例（内部使用）：
     * <pre>
     * // 这个方法看起来返回 R，实际上会抛出异常
     * return sneakyThrow(new IOException("Error"));
     * </pre>
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
