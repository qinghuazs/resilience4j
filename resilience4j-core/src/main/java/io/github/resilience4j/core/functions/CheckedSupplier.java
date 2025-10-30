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

import java.util.Objects;
import java.util.function.Supplier;

/**
 * CheckedSupplier - 支持抛出检查异常的 Supplier
 *
 * <h2>功能说明</h2>
 * CheckedSupplier 类似于 {@link Supplier}，但允许抛出检查异常（Checked Exception）。
 * 这是 Java 标准 Supplier 的增强版本，用于需要抛出检查异常的场景。
 *
 * <h2>为什么需要 CheckedSupplier？</h2>
 * Java 标准的 Supplier 只能抛出运行时异常，不能抛出检查异常（如 IOException）：
 * <pre>
 * // 编译错误：Supplier 不允许抛出 IOException
 * Supplier&lt;String&gt; supplier = () -> {
 *     return readFile(); // readFile() throws IOException
 * };
 *
 * // CheckedSupplier 可以抛出检查异常
 * CheckedSupplier&lt;String&gt; checkedSupplier = () -> {
 *     return readFile(); // OK!
 * };
 * </pre>
 *
 * <h2>使用场景</h2>
 * <ul>
 *   <li>文件操作：读取文件可能抛出 IOException</li>
 *   <li>网络请求：HTTP 调用可能抛出网络异常</li>
 *   <li>数据库查询：JDBC 操作可能抛出 SQLException</li>
 *   <li>Resilience4j 装饰器：与断路器、重试等组合使用</li>
 * </ul>
 *
 * <h2>核心方法</h2>
 * <ul>
 *   <li><b>get()</b>：获取值，可能抛出检查异常</li>
 *   <li><b>andThen()</b>：函数组合，链式转换</li>
 *   <li><b>unchecked()</b>：转换为标准 Supplier，使用 sneaky throw</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>
 * // 创建 CheckedSupplier
 * CheckedSupplier&lt;String&gt; supplier = () -> {
 *     return Files.readString(Path.of("config.txt")); // 可能抛出 IOException
 * };
 *
 * // 与 Resilience4j 组合
 * CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("backend");
 * CheckedSupplier&lt;String&gt; decorated = CircuitBreaker.decorateCheckedSupplier(
 *     circuitBreaker,
 *     supplier
 * );
 *
 * // 执行并处理异常
 * try {
 *     String result = decorated.get();
 * } catch (Throwable e) {
 *     logger.error("Failed", e);
 * }
 *
 * // 使用 andThen 组合
 * CheckedSupplier&lt;Integer&gt; length = supplier.andThen(String::length);
 * </pre>
 *
 * @param <T> 返回值类型
 * @author Vavr 团队
 * @see Supplier
 * @see CheckedFunction
 */
@FunctionalInterface
public interface CheckedSupplier<T> {

    /**
     * 获取值 - 可能抛出检查异常
     *
     * 功能说明：
     * 执行操作并返回结果，允许抛出任何异常（包括检查异常）。
     *
     * @return 结果值
     * @throws Throwable 任何异常（包括检查异常）
     */
    T get() throws Throwable;

    /**
     * 函数组合 - 在当前 Supplier 后应用转换
     *
     * 功能说明：
     * 先执行当前 Supplier 获取值，然后应用 after 函数转换该值。
     * 这是函数式编程中的组合模式（Function Composition）。
     *
     * 执行流程：
     * 1. 调用当前 Supplier 的 get() 获取值 T
     * 2. 将值 T 传给 after 函数，得到新值 V
     * 3. 返回新值 V
     *
     * 使用示例：
     * <pre>
     * CheckedSupplier&lt;String&gt; readFile = () -> Files.readString(path);
     *
     * // 读取文件后转换为大写
     * CheckedSupplier&lt;String&gt; upperCase = readFile.andThen(String::toUpperCase);
     *
     * // 读取文件后获取长度
     * CheckedSupplier&lt;Integer&gt; length = readFile.andThen(String::length);
     *
     * // 链式组合
     * CheckedSupplier&lt;String&gt; result = readFile
     *     .andThen(String::trim)
     *     .andThen(String::toUpperCase);
     * </pre>
     *
     * @param <V>   转换后的返回值类型
     * @param after 转换函数
     * @return 组合后的新 CheckedSupplier
     * @throws NullPointerException 如果 after 为 null
     */
    default <V> CheckedSupplier<V> andThen(CheckedFunction<? super T, ? extends V> after) {
        Objects.requireNonNull(after, "after is null");
        return () -> after.apply(get());
    }

    /**
     * 转换为标准 Supplier - 使用 sneaky throw 绕过检查异常
     *
     * 功能说明：
     * 将 CheckedSupplier 转换为标准的 Supplier，但保持抛出检查异常的能力。
     * 使用 "sneaky throw" 技术绕过 Java 的检查异常限制。
     *
     * Sneaky Throw 技术：
     * 利用 Java 泛型擦除的特性，在运行时抛出检查异常，但编译器不检查。
     * 这是一个高级技巧，使用时需谨慎。
     *
     * 使用场景：
     * - 需要传递给只接受 Supplier 的 API
     * - 与 Stream API 等标准库集成
     *
     * 使用示例：
     * <pre>
     * CheckedSupplier&lt;String&gt; checked = () -> Files.readString(path);
     *
     * // 转换为标准 Supplier
     * Supplier&lt;String&gt; unchecked = checked.unchecked();
     *
     * // 可以用于 Stream API
     * Stream.generate(unchecked).limit(5).forEach(System.out::println);
     * </pre>
     *
     * 注意事项：
     * - 虽然返回 Supplier，但仍可能抛出检查异常
     * - 调用方需要捕获 Throwable 或使用 try-catch
     *
     * @return 标准 Supplier，内部使用 sneaky throw
     */
    default Supplier<T> unchecked() {
        return () -> {
            try {
                return get();
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
