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

import java.util.function.BiFunction;

/**
 * CheckedBiFunction - 支持抛出检查异常的 BiFunction
 *
 * <h2>功能说明</h2>
 * CheckedBiFunction 类似于 {@link BiFunction}，但允许抛出检查异常（Checked Exception）。
 * 这是 Java 标准 BiFunction 的增强版本，用于需要抛出检查异常的场景。
 *
 * <h2>为什么需要 CheckedBiFunction？</h2>
 * Java 标准的 BiFunction 只能抛出运行时异常，不能抛出检查异常（如 IOException）：
 * <pre>
 * // 编译错误：BiFunction 不允许抛出 IOException
 * BiFunction&lt;String, String, String&gt; merge = (file1, file2) -> {
 *     String content1 = Files.readString(Path.of(file1)); // readString() throws IOException
 *     String content2 = Files.readString(Path.of(file2));
 *     return content1 + content2;
 * };
 *
 * // CheckedBiFunction 可以抛出检查异常
 * CheckedBiFunction&lt;String, String, String&gt; checkedMerge = (file1, file2) -> {
 *     String content1 = Files.readString(Path.of(file1)); // OK!
 *     String content2 = Files.readString(Path.of(file2));
 *     return content1 + content2;
 * };
 * </pre>
 *
 * <h2>使用场景</h2>
 * <ul>
 *   <li>数据合并：将两个输入合并为一个输出，可能抛出异常</li>
 *   <li>文件操作：基于两个路径执行文件操作</li>
 *   <li>数据库查询：根据两个条件查询数据</li>
 *   <li>计算操作：执行可能失败的双参数计算</li>
 *   <li>Map reduce 操作：reducer 函数可能抛出异常</li>
 * </ul>
 *
 * <h2>核心方法</h2>
 * <ul>
 *   <li><b>apply(T, U)</b>：应用双参数转换函数，可能抛出检查异常</li>
 *   <li><b>unchecked()</b>：转换为标准 BiFunction，使用 sneaky throw</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>
 * // 创建 CheckedBiFunction
 * CheckedBiFunction&lt;String, String, String&gt; mergeFiles = (path1, path2) -> {
 *     String content1 = Files.readString(Path.of(path1)); // 可能抛出 IOException
 *     String content2 = Files.readString(Path.of(path2));
 *     return content1 + "\n" + content2;
 * };
 *
 * // 执行并处理异常
 * try {
 *     String merged = mergeFiles.apply("/file1.txt", "/file2.txt");
 *     logger.info("Merged: {}", merged);
 * } catch (Throwable e) {
 *     logger.error("Failed to merge files", e);
 * }
 *
 * // 转换为标准 BiFunction 用于 Stream API
 * BiFunction&lt;String, String, String&gt; unchecked = mergeFiles.unchecked();
 * Map&lt;String, String&gt; results = pairs.stream()
 *     .collect(Collectors.toMap(
 *         Pair::getKey,
 *         p -> unchecked.apply(p.getFirst(), p.getSecond())
 *     ));
 *
 * // 用于 Map.merge()
 * CheckedBiFunction&lt;Integer, Integer, Integer&gt; sum = (a, b) -> {
 *     if (a + b > MAX_VALUE) throw new ArithmeticException("Overflow");
 *     return a + b;
 * };
 * map.merge(key, value, sum.unchecked());
 * </pre>
 *
 * <h2>与其他 Checked 接口的对比</h2>
 * <pre>
 * CheckedSupplier&lt;R&gt;         - 无参数，返回 R，可能抛出异常
 * CheckedFunction&lt;T, R&gt;      - 接受 T，返回 R，可能抛出异常
 * CheckedBiFunction&lt;T, U, R&gt; - 接受 T 和 U，返回 R，可能抛出异常
 * CheckedConsumer&lt;T&gt;         - 接受 T，无返回值，可能抛出异常
 * </pre>
 *
 * @param <T> 第一个输入参数类型
 * @param <U> 第二个输入参数类型
 * @param <R> 返回值类型
 * @author krnSaurabh
 * @see BiFunction
 * @see CheckedFunction
 */
@FunctionalInterface
public interface CheckedBiFunction<T, U, R> {

    /**
     * 应用双参数转换函数 - 可能抛出检查异常
     *
     * 功能说明：
     * 接受两个输入参数 T 和 U，执行转换操作，返回结果 R。
     * 允许抛出任何异常（包括检查异常）。
     *
     * 使用示例：
     * <pre>
     * // 合并两个文件内容
     * CheckedBiFunction&lt;String, String, String&gt; mergeFiles = (file1, file2) ->
     *     Files.readString(Path.of(file1)) + Files.readString(Path.of(file2));
     *
     * // 两数相加，检查溢出
     * CheckedBiFunction&lt;Integer, Integer, Integer&gt; safeAdd = (a, b) -> {
     *     long result = (long) a + b;
     *     if (result > Integer.MAX_VALUE || result < Integer.MIN_VALUE) {
     *         throw new ArithmeticException("Integer overflow");
     *     }
     *     return (int) result;
     * };
     *
     * // 数据库查询，根据两个条件
     * CheckedBiFunction&lt;String, String, User&gt; findUser = (username, email) ->
     *     userRepository.findByUsernameAndEmail(username, email)
     *         .orElseThrow(() -> new EntityNotFoundException("User not found"));
     * </pre>
     *
     * @param t 第一个输入参数
     * @param u 第二个输入参数
     * @return 转换后的结果
     * @throws Throwable 任何异常（包括检查异常）
     */
    R apply(T t, U u) throws Throwable;

    /**
     * 转换为标准 BiFunction - 使用 sneaky throw 绕过检查异常
     *
     * 功能说明：
     * 将 CheckedBiFunction 转换为标准的 BiFunction，但保持抛出检查异常的能力。
     * 使用 "sneaky throw" 技术绕过 Java 的检查异常限制。
     *
     * Sneaky Throw 技术：
     * 利用 Java 泛型擦除的特性，在运行时抛出检查异常，但编译器不检查。
     * 这是一个高级技巧，使用时需谨慎。
     *
     * 使用场景：
     * - 需要传递给只接受 BiFunction 的 API
     * - Map.merge() 或 Map.compute() 等方法
     * - Stream.reduce() 或 Collectors.reducing()
     * - 函数式编程中的组合操作
     *
     * 使用示例：
     * <pre>
     * CheckedBiFunction&lt;String, String, String&gt; checked = (s1, s2) ->
     *     Files.readString(Path.of(s1)) + Files.readString(Path.of(s2));
     *
     * // 转换为标准 BiFunction
     * BiFunction&lt;String, String, String&gt; unchecked = checked.unchecked();
     *
     * // 用于 Map.merge()
     * Map&lt;String, String&gt; map = new HashMap&lt;&gt;();
     * map.merge("/file1.txt", "/file2.txt", unchecked);
     *
     * // 用于 Stream.reduce()
     * CheckedBiFunction&lt;Integer, Integer, Integer&gt; sum = (a, b) -> {
     *     if (a + b > MAX_VALUE) throw new ArithmeticException("Overflow");
     *     return a + b;
     * };
     * Optional&lt;Integer&gt; total = numbers.stream()
     *     .reduce(sum.unchecked());
     *
     * // 用于 Collectors
     * String result = pairs.stream()
     *     .collect(Collectors.reducing("", unchecked));
     * </pre>
     *
     * 注意事项：
     * - 虽然返回 BiFunction，但仍可能抛出检查异常
     * - 调用方需要捕获 Throwable 或使用 try-catch
     * - 在 Stream 中使用时，异常会终止流处理
     *
     * @return 标准 BiFunction，内部使用 sneaky throw
     */
    default BiFunction<T, U, R> unchecked() {
        return (t, u) -> {
            try {
                return apply(t, u);
            } catch (Throwable x) {
                return sneakyThrow(x);
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
