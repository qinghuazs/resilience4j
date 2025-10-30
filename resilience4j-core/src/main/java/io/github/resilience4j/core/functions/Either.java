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

import java.io.Serializable;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Function;

/**
 * Either 类型 - 表示"二选一"的值，要么是 Left，要么是 Right
 *
 * <h2>Either 类型简介</h2>
 * Either 是函数式编程中的经典类型，表示两种可能性中的一种：
 * <ul>
 *   <li><b>Left</b>（左）：通常表示失败、异常或"次要"的值</li>
 *   <li><b>Right</b>（右）：通常表示成功、正常值或"主要"的值</li>
 * </ul>
 *
 * <h2>为什么叫 Left 和 Right？</h2>
 * <p>
 * 在英语中，"right" 既有"右边"的意思，也有"正确"的意思。函数式编程社区
 * 利用这个双关语，约定 Right 表示"正确的"结果，Left 表示"错误的"结果。
 * </p>
 *
 * <h2>与 Optional 的对比</h2>
 * <pre>
 * Optional&lt;T&gt;:
 *   ├─ Some(value)  - 有值
 *   └─ None         - 无值（不知道为什么无值）
 *
 * Either&lt;L, R&gt;:
 *   ├─ Right(value) - 成功，有值
 *   └─ Left(error)  - 失败，有错误信息
 * </pre>
 *
 * <p>
 * Either 比 Optional 更强大，因为它可以在失败时携带错误信息。
 * </p>
 *
 * <h2>在 Resilience4j 中的使用</h2>
 * <ul>
 *   <li>表示操作结果：Right 表示成功，Left 表示异常</li>
 *   <li>断路器统计：记录成功或失败的调用结果</li>
 *   <li>ResultUtils：判断 Either 类型的结果</li>
 *   <li>测试验证：验证操作返回的是成功还是失败</li>
 * </ul>
 *
 * <h2>典型使用示例</h2>
 * <pre>
 * // 创建 Right（成功）
 * Either&lt;Throwable, String&gt; success = Either.right("Hello");
 *
 * // 创建 Left（失败）
 * Either&lt;Throwable, String&gt; failure = Either.left(new IOException("Error"));
 *
 * // 判断类型
 * if (result.isRight()) {
 *     String value = result.get(); // 获取成功的值
 * } else {
 *     Throwable error = result.getLeft(); // 获取错误
 * }
 *
 * // 使用 fold 统一处理
 * String message = result.fold(
 *     error -&gt; "Error: " + error.getMessage(),  // 处理 Left
 *     value -&gt; "Success: " + value              // 处理 Right
 * );
 *
 * // 转换 Right 值
 * Either&lt;Throwable, Integer&gt; length = result.map(String::length);
 *
 * // 转换 Left 值
 * Either&lt;String, String&gt; errorMsg = result.mapLeft(Throwable::getMessage);
 * </pre>
 *
 * <h2>方法说明</h2>
 * <ul>
 *   <li><b>right(R)</b> / <b>left(L)</b>：创建 Either 实例</li>
 *   <li><b>isRight()</b> / <b>isLeft()</b>：判断类型</li>
 *   <li><b>get()</b> / <b>getLeft()</b>：获取值（类型不匹配会抛异常）</li>
 *   <li><b>map()</b>：转换 Right 值</li>
 *   <li><b>mapLeft()</b>：转换 Left 值</li>
 *   <li><b>fold()</b>：统一处理 Left 和 Right</li>
 *   <li><b>swap()</b>：交换 Left 和 Right</li>
 * </ul>
 *
 * <h2>不可变性</h2>
 * <p>
 * Either 是不可变的（immutable），所有转换方法都返回新的 Either 实例。
 * </p>
 *
 * <h2>序列化</h2>
 * <p>
 * Left 和 Right 都实现了 Serializable，可以序列化。
 * </p>
 *
 * @param <L> Left 类型，通常是 Throwable 或错误信息
 * @param <R> Right 类型，通常是成功的结果值
 * @author Vavr 团队
 * @since 1.0.0
 * @see io.vavr.control.Either
 */
public interface Either<L, R> {

    /**
     * 创建 Right 实例 - 表示成功的结果
     *
     * 功能说明：
     * 创建一个包含成功值的 Either。在 Resilience4j 中，Right 表示操作成功。
     *
     * 使用示例：
     * <pre>
     * // 表示成功的 HTTP 响应
     * Either&lt;Throwable, HttpResponse&gt; result = Either.right(response);
     *
     * // 表示成功的计算结果
     * Either&lt;String, Integer&gt; number = Either.right(42);
     * </pre>
     *
     * @param <L>   Left 类型
     * @param <R>   Right 类型
     * @param right 成功的值
     * @return Right 实例
     */
    static <L, R> Either<L, R> right(R right) {
        return new Right<>(right);
    }

    /**
     * 创建 Left 实例 - 表示失败的结果
     *
     * 功能说明：
     * 创建一个包含失败信息的 Either。在 Resilience4j 中，Left 通常表示异常或错误。
     *
     * 使用示例：
     * <pre>
     * // 表示操作失败，携带异常
     * Either&lt;Throwable, String&gt; result = Either.left(new IOException("File not found"));
     *
     * // 表示验证失败，携带错误消息
     * Either&lt;String, User&gt; validation = Either.left("Invalid email");
     * </pre>
     *
     * @param <L>  Left 类型
     * @param <R>  Right 类型
     * @param left 失败的值（通常是异常或错误消息）
     * @return Left 实例
     */
    static <L, R> Either<L, R> left(L left) {
        return new Left<>(left);
    }

    /**
     * 获取 Left 值
     *
     * 功能说明：
     * 如果是 Left，返回其包含的值；如果是 Right，抛出 NoSuchElementException。
     *
     * 使用建议：
     * 调用前先使用 isLeft() 检查类型，或使用 fold() 统一处理。
     *
     * 使用示例：
     * <pre>
     * Either&lt;Throwable, String&gt; result = callService();
     * if (result.isLeft()) {
     *     Throwable error = result.getLeft();
     *     logger.error("Operation failed", error);
     * }
     * </pre>
     *
     * @return Left 包含的值
     * @throws NoSuchElementException 如果是 Right
     */
    L getLeft();

    /**
     * 判断是否为 Left
     *
     * 功能说明：
     * 检查 Either 是否为 Left（失败）。
     *
     * 使用示例：
     * <pre>
     * if (result.isLeft()) {
     *     // 处理失败情况
     * }
     * </pre>
     *
     * @return 如果是 Left 返回 true，否则返回 false
     */
    boolean isLeft();

    /**
     * 判断是否为 Right
     *
     * 功能说明：
     * 检查 Either 是否为 Right（成功）。
     *
     * 使用示例：
     * <pre>
     * if (result.isRight()) {
     *     // 处理成功情况
     * }
     * </pre>
     *
     * @return 如果是 Right 返回 true，否则返回 false
     */
    boolean isRight();

    /**
     * 获取 Right 值
     *
     * 功能说明：
     * 如果是 Right，返回其包含的值；如果是 Left，抛出 NoSuchElementException。
     *
     * 使用建议：
     * 调用前先使用 isRight() 检查类型，或使用 fold() 统一处理。
     *
     * 使用示例：
     * <pre>
     * Either&lt;Throwable, String&gt; result = callService();
     * if (result.isRight()) {
     *     String value = result.get();
     *     System.out.println("Success: " + value);
     * }
     * </pre>
     *
     * @return Right 包含的值
     * @throws NoSuchElementException 如果是 Left
     */
    R get();

    /**
     * 交换 Left 和 Right
     *
     * 功能说明：
     * 将 Left 转换为 Right，将 Right 转换为 Left。
     * 返回一个新的 Either 实例，类型参数位置互换。
     *
     * 使用场景：
     * - 反转语义：将"成功/失败"的语义反转
     * - 类型适配：适配需要相反类型的 API
     *
     * 使用示例：
     * <pre>
     * Either&lt;String, Integer&gt; result = Either.right(42);
     * Either&lt;Integer, String&gt; swapped = result.swap();
     * // swapped 是 Left(42)
     *
     * Either&lt;String, Integer&gt; error = Either.left("Error");
     * Either&lt;Integer, String&gt; swappedError = error.swap();
     * // swappedError 是 Right("Error")
     * </pre>
     *
     * @return 交换后的新 Either 实例
     */
    default Either<R, L> swap() {
        if (isRight()) {
            // Right 变成 Left
            return new Left<>(get());
        } else {
            // Left 变成 Right
            return new Right<>(getLeft());
        }
    }

    /**
     * 判断是否为空（失败）
     *
     * 功能说明：
     * 如果是 Left 返回 true，如果是 Right 返回 false。
     * 这个方法将 Left 视为"空"或"无值"。
     *
     * @return 如果是 Left 返回 true，否则返回 false
     */
    default boolean isEmpty() {
        return isLeft();
    }

    /**
     * 获取 Right 值或返回 null
     *
     * 功能说明：
     * 安全地获取 Right 值，如果是 Left 返回 null 而不是抛出异常。
     *
     * 使用场景：
     * - 不想处理异常，接受 null 值
     * - 与传统 Java API 集成
     *
     * 使用示例：
     * <pre>
     * Either&lt;Throwable, String&gt; result = callService();
     * String value = result.getOrNull(); // Left 时返回 null
     * </pre>
     *
     * @return Right 的值，或 null（如果是 Left）
     */
    default R getOrNull() {
        return isEmpty() ? null : get();
    }

    /**
     * 转换 Left 值
     *
     * 功能说明：
     * 如果是 Left，应用转换函数将 Left 值从类型 L 转换为类型 U。
     * 如果是 Right，返回原 Either（类型安全地转换）。
     *
     * 函子（Functor）模式：
     * mapLeft 遵循函子法则，保持 Either 的结构，只转换值。
     *
     * 使用场景：
     * - 转换异常类型：Throwable -> String
     * - 包装错误信息：String -> ErrorResponse
     * - 错误信息国际化
     *
     * 使用示例：
     * <pre>
     * Either&lt;Throwable, String&gt; result = callService();
     *
     * // 将异常转换为错误消息
     * Either&lt;String, String&gt; errorMsg = result.mapLeft(Throwable::getMessage);
     *
     * // 将异常包装为响应对象
     * Either&lt;ErrorResponse, String&gt; response = result.mapLeft(ex ->
     *     new ErrorResponse(500, ex.getMessage())
     * );
     * </pre>
     *
     * @param <U>        转换后的 Left 类型
     * @param leftMapper Left 值转换函数
     * @return 转换后的新 Either 实例
     * @throws NullPointerException 如果 leftMapper 为 null
     */
    @SuppressWarnings("unchecked")
    default <U> Either<U, R> mapLeft(Function<? super L, ? extends U> leftMapper) {
        // 参数校验
        Objects.requireNonNull(leftMapper, "leftMapper is null");

        if (isLeft()) {
            // Left：应用转换函数，创建新的 Left
            return Either.left(leftMapper.apply(getLeft()));
        } else {
            // Right：不转换，直接返回（类型安全地转换）
            return (Either<U, R>) this;
        }
    }

    /**
     * 转换 Right 值
     *
     * 功能说明：
     * 如果是 Right，应用转换函数将 Right 值从类型 R 转换为类型 U。
     * 如果是 Left，返回原 Either（类型安全地转换）。
     *
     * 这是最常用的转换方法，用于转换成功的结果值。
     *
     * 函子（Functor）模式：
     * map 遵循函子法则，保持 Either 的结构，只转换值。
     *
     * 使用场景：
     * - 转换数据类型：String -> Integer
     * - 提取属性：User -> String (name)
     * - 链式转换：多次 map 组合
     *
     * 使用示例：
     * <pre>
     * Either&lt;Throwable, String&gt; result = Either.right("hello");
     *
     * // 转换为大写
     * Either&lt;Throwable, String&gt; upper = result.map(String::toUpperCase);
     * // Right("HELLO")
     *
     * // 获取长度
     * Either&lt;Throwable, Integer&gt; length = result.map(String::length);
     * // Right(5)
     *
     * // 链式转换
     * Either&lt;Throwable, String&gt; transformed = result
     *     .map(String::trim)
     *     .map(String::toUpperCase)
     *     .map(s -> s + "!");
     * // Right("HELLO!")
     * </pre>
     *
     * @param <U>    转换后的 Right 类型
     * @param mapper Right 值转换函数
     * @return 转换后的新 Either 实例
     * @throws NullPointerException 如果 mapper 为 null
     */
    @SuppressWarnings("unchecked")
    default <U> Either<L, U> map(Function<? super R, ? extends U> mapper) {
        // 参数校验
        Objects.requireNonNull(mapper, "mapper is null");

        if (isRight()) {
            // Right：应用转换函数，创建新的 Right
            return Either.right(mapper.apply(get()));
        } else {
            // Left：不转换，直接返回（类型安全地转换）
            return (Either<L, U>) this;
        }
    }

    /**
     * 折叠 Either - 统一处理 Left 和 Right
     *
     * 功能说明：
     * 无论是 Left 还是 Right，都转换为相同类型的值。
     * 这是处理 Either 的最通用方式，避免了类型检查和分支。
     *
     * 折叠（Fold）模式：
     * fold 是函数式编程中的经典模式，将两种可能性统一为一个结果。
     *
     * 执行逻辑：
     * - 如果是 Left，应用 leftMapper，返回转换结果
     * - 如果是 Right，应用 rightMapper，返回转换结果
     * - 两个 mapper 返回相同类型 U
     *
     * 与其他方法的对比：
     * <pre>
     * // map：只转换 Right，Left 保持不变
     * Either&lt;L, U&gt; mapped = either.map(r -> ...)
     *
     * // mapLeft：只转换 Left，Right 保持不变
     * Either&lt;U, R&gt; mappedLeft = either.mapLeft(l -> ...)
     *
     * // fold：统一处理，返回单一类型
     * U result = either.fold(l -> ..., r -> ...)
     * </pre>
     *
     * 使用场景：
     * - 转换为响应：成功和失败都转换为 HTTP 响应
     * - 生成消息：成功和失败都生成用户消息
     * - 统计分析：成功和失败都转换为统计数据
     * - 日志记录：统一格式记录结果
     *
     * 使用示例：
     * <pre>
     * Either&lt;Throwable, String&gt; result = callService();
     *
     * // 示例1：转换为消息
     * String message = result.fold(
     *     error -> "Error: " + error.getMessage(),
     *     value -> "Success: " + value
     * );
     *
     * // 示例2：转换为 HTTP 响应
     * HttpResponse response = result.fold(
     *     error -> HttpResponse.serverError(error.getMessage()),
     *     value -> HttpResponse.ok(value)
     * );
     *
     * // 示例3：转换为 Optional
     * Optional&lt;String&gt; optional = result.fold(
     *     error -> Optional.empty(),
     *     value -> Optional.of(value)
     * );
     *
     * // 示例4：计数统计
     * int count = result.fold(
     *     error -> 0,  // 失败计为 0
     *     value -> 1   // 成功计为 1
     * );
     * </pre>
     *
     * @param <U>         折叠后的结果类型
     * @param leftMapper  Left 转换函数
     * @param rightMapper Right 转换函数
     * @return 折叠后的值，类型为 U
     * @throws NullPointerException 如果任一 mapper 为 null
     */
    default <U> U fold(Function<? super L, ? extends U> leftMapper, Function<? super R, ? extends U> rightMapper) {
        // 参数校验
        Objects.requireNonNull(leftMapper, "leftMapper is null");
        Objects.requireNonNull(rightMapper, "rightMapper is null");

        if (isRight()) {
            // Right：应用 rightMapper
            return rightMapper.apply(get());
        } else {
            // Left：应用 leftMapper
            return leftMapper.apply(getLeft());
        }
    }

    /**
     * Right 实现类 - Either 的"成功"版本
     *
     * 功能说明：
     * Right 表示操作成功，包含成功的结果值。
     * 在 Resilience4j 中，Right 通常表示正常的返回值。
     *
     * 设计特点：
     * - 不可变：final 类，value 字段是 final
     * - 序列化：实现 Serializable，可以序列化
     * - 类型安全：泛型保证类型安全
     *
     * @param <L> Left 类型（虽然 Right 不包含 Left 值，但需要类型参数）
     * @param <R> Right 类型，实际包含的值类型
     */
    final class Right<L, R> implements Either<L, R>, Serializable {

        /** 包含的成功值 */
        private final R value;

        /**
         * 构造 Right 实例
         *
         * @param value 成功的值
         */
        private Right(R value) {
            this.value = value;
        }

        /**
         * 获取 Right 值
         *
         * @return 成功的值
         */
        @Override
        public R get() {
            return value;
        }

        /**
         * 尝试获取 Left 值 - 会抛出异常
         *
         * 功能说明：
         * Right 不包含 Left 值，调用此方法会抛出 NoSuchElementException。
         *
         * @throws NoSuchElementException 始终抛出，因为 Right 没有 Left 值
         */
        @Override
        public L getLeft() {
            throw new NoSuchElementException("getLeft() on Right");
        }

        /**
         * 判断是否为 Left
         *
         * @return 始终返回 false，因为这是 Right
         */
        @Override
        public boolean isLeft() {
            return false;
        }

        /**
         * 判断是否为 Right
         *
         * @return 始终返回 true，因为这是 Right
         */
        @Override
        public boolean isRight() {
            return true;
        }
    }

    /**
     * Left 实现类 - Either 的"失败"版本
     *
     * 功能说明：
     * Left 表示操作失败，包含失败的信息（通常是异常或错误消息）。
     * 在 Resilience4j 中，Left 通常表示抛出的异常。
     *
     * 设计特点：
     * - 不可变：final 类，value 字段是 final
     * - 序列化：实现 Serializable，可以序列化
     * - 类型安全：泛型保证类型安全
     *
     * 使用示例：
     * <pre>
     * // 捕获异常并包装为 Left
     * Either&lt;Throwable, String&gt; result;
     * try {
     *     String data = riskyOperation();
     *     result = Either.right(data);
     * } catch (Exception e) {
     *     result = Either.left(e);
     * }
     * </pre>
     *
     * @param <L> Left 类型，实际包含的值类型（通常是 Throwable 或 String）
     * @param <R> Right 类型（虽然 Left 不包含 Right 值，但需要类型参数）
     */
    final class Left<L, R> implements Either<L, R>, Serializable {

        /** 包含的失败值 */
        private final L value;

        /**
         * 构造 Left 实例
         *
         * @param value 失败的值（通常是异常或错误消息）
         */
        private Left(L value) {
            this.value = value;
        }

        /**
         * 尝试获取 Right 值 - 会抛出异常
         *
         * 功能说明：
         * Left 不包含 Right 值，调用此方法会抛出 NoSuchElementException。
         *
         * @throws NoSuchElementException 始终抛出，因为 Left 没有 Right 值
         */
        @Override
        public R get() {
            throw new NoSuchElementException("get() on Left");
        }

        /**
         * 获取 Left 值
         *
         * @return 失败的值
         */
        @Override
        public L getLeft() {
            return value;
        }

        /**
         * 判断是否为 Left
         *
         * @return 始终返回 true，因为这是 Left
         */
        @Override
        public boolean isLeft() {
            return true;
        }

        /**
         * 判断是否为 Right
         *
         * @return 始终返回 false，因为这是 Left
         */
        @Override
        public boolean isRight() {
            return false;
        }
    }
}
