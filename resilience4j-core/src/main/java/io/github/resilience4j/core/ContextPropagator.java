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

import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static java.util.stream.Collectors.toMap;

/**
 * 上下文传播器接口 - 跨线程边界传播上下文值
 *
 * 作用说明：
 * 这是一个抽象接口，用于在不同线程之间传播上下文信息。
 * 主要用于解决 ThreadLocal 变量在异步执行、线程池等场景下无法自动传递的问题。
 *
 * 核心问题：
 * Java 的 ThreadLocal 将数据绑定到特定线程，当代码在新线程中执行时，
 * ThreadLocal 的值不会自动传递。这在以下场景会导致问题：
 * 1. 使用线程池执行异步任务
 * 2. Resilience4j 的装饰器切换到其他线程执行
 * 3. 响应式编程（Reactor/RxJava）的操作符切换线程
 * 4. 异步 CompletableFuture
 *
 * 解决方案：
 * ContextPropagator 提供三步机制：
 * 1. retrieve()：在父线程中获取需要传递的值
 * 2. copy()：在子线程开始时，将值复制到新线程
 * 3. clear()：在子线程结束时，清理设置的值
 *
 * 设计理念：
 * - 模板方法模式：定义传播的标准流程
 * - 策略模式：不同的上下文有不同的实现
 * - 装饰器模式：通过装饰 Supplier/Callable/Runnable 实现透明传播
 *
 * 典型使用场景：
 * 1. MDC（日志上下文）传播：跨线程保持 traceId、userId 等信息
 * 2. 安全上下文传播：传递用户认证信息（如 Spring Security）
 * 3. 事务上下文传播：在某些情况下传递事务信息
 * 4. 自定义业务上下文：传递租户 ID、语言设置等
 *
 * 使用示例：
 * <pre>
 * // 示例1：传播 MDC（日志上下文）
 * class MDCContextPropagator implements ContextPropagator&lt;Map&lt;String, String&gt;&gt; {
 *     {@literal @}Override
 *     public Supplier&lt;Optional&lt;Map&lt;String, String&gt;&gt;&gt; retrieve() {
 *         // 在父线程中获取 MDC 的所有值
 *         return () -&gt; Optional.ofNullable(MDC.getCopyOfContextMap());
 *     }
 *
 *     {@literal @}Override
 *     public Consumer&lt;Optional&lt;Map&lt;String, String&gt;&gt;&gt; copy() {
 *         // 在子线程中设置 MDC
 *         return contextMap -&gt; {
 *             if (contextMap.isPresent()) {
 *                 MDC.setContextMap(contextMap.get());
 *             }
 *         };
 *     }
 *
 *     {@literal @}Override
 *     public Consumer&lt;Optional&lt;Map&lt;String, String&gt;&gt;&gt; clear() {
 *         // 清理 MDC
 *         return contextMap -&gt; MDC.clear();
 *     }
 * }
 *
 * // 使用方式1：装饰 Supplier
 * ContextPropagator&lt;Map&lt;String, String&gt;&gt; propagator = new MDCContextPropagator();
 * Supplier&lt;String&gt; decorated = ContextPropagator.decorateSupplier(
 *     propagator,
 *     () -&gt; {
 *         // 这里可以访问父线程的 MDC 值
 *         System.out.println("TraceId: " + MDC.get("traceId"));
 *         return "result";
 *     }
 * );
 * CompletableFuture.supplyAsync(decorated); // 在新线程执行，MDC 自动传播
 *
 * // 使用方式2：配合 Resilience4j
 * CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("myCircuitBreaker");
 * Supplier&lt;String&gt; supplier = circuitBreaker.decorateSupplier(() -&gt; callService());
 * // 添加上下文传播
 * Supplier&lt;String&gt; withContext = ContextPropagator.decorateSupplier(
 *     propagator,
 *     supplier
 * );
 * </pre>
 *
 * 执行流程：
 * <pre>
 * 父线程                          子线程
 *   |                              |
 *   |--retrieve()--> 获取上下文值   |
 *   |                              |
 *   +------------------------------|--copy()--> 设置上下文值
 *                                  |
 *                                  |--执行业务逻辑
 *                                  |
 *                                  |--clear()--> 清理上下文值
 *                                  |
 * </pre>
 *
 * 注意事项：
 * 1. retrieve() 在父线程调用，copy() 和 clear() 在子线程调用
 * 2. clear() 必须在 finally 块中执行，确保资源清理
 * 3. 对于线程池场景，clear() 非常重要，防止线程复用导致的上下文污染
 * 4. 可以组合多个 ContextPropagator 同时传播多种上下文
 *
 * 性能考虑：
 * - retrieve() 应该尽量轻量，因为每次调用都会执行
 * - 避免传播大对象，只传播必要的上下文信息
 * - 考虑使用不可变对象，避免并发问题
 *
 * @param <T> 跨线程传播的值的类型
 *
 * @author krnsaurabh
 * @since 1.5.0
 */
public interface  ContextPropagator<T> {

    /**
     * 错误消息常量：ContextPropagator 列表不能为 null
     */
    public static final String CONTEXT_PROPAGATOR_LIST_SHOULD_BE_NON_NULL = "ContextPropagator list should be non null";

    /**
     * 获取当前线程的上下文值
     *
     * 功能说明：
     * 从当前执行的线程（父线程）中获取需要传播的值。
     * 返回一个 Supplier，延迟获取值，确保在正确的时机读取。
     *
     * 执行时机：
     * - 在父线程中调用（装饰器创建时）
     * - 在子线程实际执行之前
     *
     * 实现要点：
     * 1. 使用 Optional 包装值，处理值不存在的情况
     * 2. 应该快速返回，避免阻塞
     * 3. 获取的是值的副本，而不是引用（如果需要）
     *
     * 典型实现：
     * <pre>
     * // MDC 上下文获取
     * {@literal @}Override
     * public Supplier&lt;Optional&lt;Map&lt;String, String&gt;&gt;&gt; retrieve() {
     *     return () -&gt; Optional.ofNullable(MDC.getCopyOfContextMap());
     * }
     *
     * // 安全上下文获取
     * {@literal @}Override
     * public Supplier&lt;Optional&lt;SecurityContext&gt;&gt; retrieve() {
     *     return () -&gt; Optional.ofNullable(
     *         SecurityContextHolder.getContext()
     *     );
     * }
     *
     * // 自定义上下文
     * {@literal @}Override
     * public Supplier&lt;Optional&lt;String&gt;&gt; retrieve() {
     *     return () -&gt; Optional.ofNullable(UserContext.getCurrentUserId());
     * }
     * </pre>
     *
     * @return Supplier，生产当前线程的上下文值（包装在 Optional 中）
     */
    Supplier<Optional<T>> retrieve();

    /**
     * 将上下文值复制到新线程
     *
     * 功能说明：
     * 在子线程开始执行时，将从父线程获取的值设置到当前线程。
     * 接收 retrieve() 方法在父线程中获取的值。
     *
     * 执行时机：
     * - 在子线程中调用
     * - 在实际业务逻辑执行之前
     * - 在 try 块的开始处
     *
     * 实现要点：
     * 1. 检查 Optional 是否有值
     * 2. 将值设置到线程本地存储（如 ThreadLocal）
     * 3. 应该快速完成，避免阻塞业务逻辑
     *
     * 典型实现：
     * <pre>
     * // MDC 上下文复制
     * {@literal @}Override
     * public Consumer&lt;Optional&lt;Map&lt;String, String&gt;&gt;&gt; copy() {
     *     return contextMap -&gt; {
     *         if (contextMap != null &amp;&amp; contextMap.isPresent()) {
     *             MDC.setContextMap(contextMap.get());
     *         }
     *     };
     * }
     *
     * // 安全上下文复制
     * {@literal @}Override
     * public Consumer&lt;Optional&lt;SecurityContext&gt;&gt; copy() {
     *     return context -&gt; {
     *         if (context != null &amp;&amp; context.isPresent()) {
     *             SecurityContextHolder.setContext(context.get());
     *         }
     *     };
     * }
     *
     * // 自定义上下文复制
     * {@literal @}Override
     * public Consumer&lt;Optional&lt;String&gt;&gt; copy() {
     *     return userId -&gt; {
     *         if (userId != null &amp;&amp; userId.isPresent()) {
     *             UserContext.setCurrentUserId(userId.get());
     *         }
     *     };
     * }
     * </pre>
     *
     * @return Consumer，接收上下文值并设置到当前线程
     */
    Consumer<Optional<T>> copy();

    /**
     * 清理线程的上下文值
     *
     * 功能说明：
     * 在子线程执行完毕时，清理设置的上下文值。
     * 这一步非常重要，特别是在使用线程池的场景。
     *
     * 为什么必须清理？
     * 1. 线程池复用：线程会被复用执行其他任务，残留的上下文会污染后续任务
     * 2. 内存泄漏：ThreadLocal 如果不清理，会导致内存泄漏
     * 3. 安全问题：残留的用户信息可能被下一个任务错误使用
     *
     * 执行时机：
     * - 在子线程中调用
     * - 在业务逻辑执行之后
     * - 必须在 finally 块中执行，确保无论成功还是异常都会清理
     *
     * 实现要点：
     * 1. 应该幂等，多次调用不会出错
     * 2. 不应该抛出异常
     * 3. 应该快速完成
     *
     * 典型实现：
     * <pre>
     * // MDC 上下文清理
     * {@literal @}Override
     * public Consumer&lt;Optional&lt;Map&lt;String, String&gt;&gt;&gt; clear() {
     *     return contextMap -&gt; {
     *         MDC.clear(); // 清理所有 MDC 值
     *     };
     * }
     *
     * // 安全上下文清理
     * {@literal @}Override
     * public Consumer&lt;Optional&lt;SecurityContext&gt;&gt; clear() {
     *     return context -&gt; {
     *         SecurityContextHolder.clearContext();
     *     };
     * }
     *
     * // 自定义上下文清理
     * {@literal @}Override
     * public Consumer&lt;Optional&lt;String&gt;&gt; clear() {
     *     return userId -&gt; {
     *         UserContext.clearCurrentUserId();
     *     };
     * }
     *
     * // 可选：恢复原来的值（而不是清空）
     * {@literal @}Override
     * public Consumer&lt;Optional&lt;String&gt;&gt; clear() {
     *     String originalValue = UserContext.getCurrentUserId(); // 在 copy() 之前保存
     *     return userId -&gt; {
     *         if (originalValue != null) {
     *             UserContext.setCurrentUserId(originalValue);
     *         } else {
     *             UserContext.clearCurrentUserId();
     *         }
     *     };
     * }
     * </pre>
     *
     * 注意事项：
     * - 参数中传递的是原始的上下文值（从 retrieve() 获取的）
     * - 可以根据需要决定是完全清空，还是恢复到原始状态
     * - 对于线程池场景，通常建议完全清空
     *
     * @return Consumer，接收上下文值并清理线程的上下文
     */
    Consumer<Optional<T>> clear();

    /**
     * 装饰 Supplier，使其支持上下文传播（单个传播器）
     *
     * 功能说明：
     * 将普通的 Supplier 包装成支持上下文传播的 Supplier。
     * 当装饰后的 Supplier 在新线程中执行时，会自动传播上下文。
     *
     * 执行流程：
     * 1. 装饰时（在父线程）：调用 propagator.retrieve() 获取上下文值
     * 2. 执行时（在子线程）：
     *    a. 调用 propagator.copy() 设置上下文
     *    b. 执行原始的 supplier.get()
     *    c. 在 finally 块中调用 propagator.clear() 清理上下文
     *
     * 使用场景：
     * - 异步执行：CompletableFuture.supplyAsync(decorated)
     * - 线程池：executor.submit(decorated::get)
     * - Resilience4j：与断路器、重试等装饰器配合使用
     *
     * 示例：
     * <pre>
     * // 传播 MDC 日志上下文
     * MDC.put("traceId", "12345");
     * ContextPropagator&lt;Map&lt;String, String&gt;&gt; mdcPropagator = new MDCContextPropagator();
     *
     * Supplier&lt;String&gt; original = () -&gt; {
     *     // 在新线程中可以访问 traceId
     *     System.out.println("TraceId: " + MDC.get("traceId"));
     *     return callRemoteService();
     * };
     *
     * // 装饰 Supplier
     * Supplier&lt;String&gt; decorated = ContextPropagator.decorateSupplier(
     *     mdcPropagator,
     *     original
     * );
     *
     * // 在新线程中执行，traceId 会自动传播
     * CompletableFuture&lt;String&gt; future = CompletableFuture.supplyAsync(decorated);
     * </pre>
     *
     * @param propagator 上下文传播器实例
     * @param supplier   要装饰的原始 Supplier
     * @param <T>        Supplier 的返回类型
     * @return 装饰后的 Supplier，支持上下文传播
     */
    static <T> Supplier<T> decorateSupplier(ContextPropagator propagator,
                                            Supplier<T> supplier) {
        // 在装饰时（父线程）立即获取上下文值
        final Optional value = (Optional) propagator.retrieve().get();
        return () -> {
            try {
                // 在执行时（子线程）先设置上下文
                propagator.copy().accept(value);
                // 执行原始的业务逻辑
                return supplier.get();
            } finally {
                // 无论成功还是异常，都要清理上下文
                propagator.clear().accept(value);
            }
        };
    }

    /**
     * 装饰 Supplier，使其支持上下文传播（多个传播器）
     *
     * 功能说明：
     * 支持同时传播多种上下文（如 MDC + 安全上下文 + 自定义上下文）。
     * 所有传播器的上下文都会被传播到子线程。
     *
     * 去重机制：
     * 如果列表中有重复的传播器（相同类型），后面的会覆盖前面的。
     * 使用 Map 来保存每个传播器对应的值，key 是传播器实例本身。
     *
     * 执行顺序：
     * - copy() 按传播器在 Map 中的顺序执行（不保证顺序）
     * - clear() 也按相同的顺序执行
     * - 通常���需要关心顺序，除非传播器之间有依赖关系
     *
     * 使用场景：
     * - 同时传播多种上下文
     * - 框架集成，需要传播多种系统级上下文
     *
     * 示例：
     * <pre>
     * // 同时传播 MDC 和安全上下文
     * List&lt;ContextPropagator&gt; propagators = Arrays.asList(
     *     new MDCContextPropagator(),
     *     new SecurityContextPropagator(),
     *     new CustomContextPropagator()
     * );
     *
     * Supplier&lt;String&gt; original = () -&gt; {
     *     // 可以访问所有上下文
     *     System.out.println("TraceId: " + MDC.get("traceId"));
     *     System.out.println("User: " + SecurityContextHolder.getContext().getAuthentication().getName());
     *     return callService();
     * };
     *
     * Supplier&lt;String&gt; decorated = ContextPropagator.decorateSupplier(
     *     propagators,
     *     original
     * );
     *
     * CompletableFuture.supplyAsync(decorated);
     * </pre>
     *
     * @param propagators 上下文传播器列表，不能为 null
     * @param supplier    要装饰的原始 Supplier
     * @param <T>         Supplier 的返回类型
     * @return 装饰后的 Supplier，支持多上下文传播
     * @throws NullPointerException 如果 propagators 为 null
     */
    static <T> Supplier<T> decorateSupplier(List<? extends ContextPropagator> propagators,
                                            Supplier<T> supplier) {

        // 验证参数不能为 null
        Objects.requireNonNull(propagators, CONTEXT_PROPAGATOR_LIST_SHOULD_BE_NON_NULL);

        // 创建 Map 保存每个传播器对应的上下文值
        // key：传播器实例本身
        // value：从父线程获取的上下文值（Optional 包装）
        // 如果有重复的传播器，后面的会覆盖前面的（通过 merge function）
        final Map<? extends ContextPropagator, Object> values = propagators.stream()
            .collect(toMap(
                p -> p, // key 是传播器实例
                p -> p.retrieve().get(), // value 是获取的上下文值
                (first, second) -> second, // 冲突时保留后面的
                HashMap::new)); // 使用 HashMap 存储

        return () -> {
            try {
                // 遍历所有传播器，依次设置上下文
                values.forEach((p, v) -> p.copy().accept(v));
                // 执行原始的业务逻辑
                return supplier.get();
            } finally {
                // 遍历所有传播器，依次清理上下文
                values.forEach((p, v) -> p.clear().accept(v));
            }
        };
    }

    /**
     * 装饰 Callable，使其支持上下文传播（单个传播器）
     *
     * 功能与 decorateSupplier 相同，但适用于 Callable 接口。
     * Callable 可以抛出检查异常，适合需要异常处理的场景。
     *
     * 使用场景：
     * - ExecutorService.submit(callable)
     * - ForkJoinPool.submit(callable)
     * - 需要抛出检查异常的异步任务
     *
     * @param propagator 上下文传播器实例
     * @param callable   要装饰的原始 Callable
     * @param <T>        Callable 的返回类型
     * @return 装饰后的 Callable，支持上下文传播
     */
    static <T> Callable<T> decorateCallable(ContextPropagator propagator, Callable<T> callable) {
        // 在装饰时（父线程）立即获取上下文值
        final Optional value = (Optional) propagator.retrieve().get();
        return () -> {
            try {
                // 在执行时（子线程）先设置上下文
                propagator.copy().accept(value);
                // 执行原始的业务逻辑
                return callable.call();
            } finally {
                // 清理上下文
                propagator.clear().accept(value);
            }
        };
    }

    /**
     * Method decorates callable to copy variables across thread boundary.
     *
     * @param propagators the instance of {@link ContextPropagator} should be non null.
     * @param callable    the callable to be decorated
     * @param <T>         the type of variable that cross thread boundary
     * @return decorated callable of type T
     */
    static <T> Callable<T> decorateCallable(List<? extends ContextPropagator> propagators,
                                            Callable<T> callable) {

        Objects.requireNonNull(propagators, CONTEXT_PROPAGATOR_LIST_SHOULD_BE_NON_NULL);

        //Create identity map of <ContextPropagator,Optional Supplier value>, if we have duplicate ContextPropagators then last one wins.
        final Map<? extends ContextPropagator, Object> values = propagators.stream()
            .collect(toMap(
                p -> p, //key as ContextPropagator instance itself
                p -> p.retrieve().get(), //Supplier Optional value
                (first, second) -> second, //Merge function, this simply choose later value in key collision
                HashMap::new)); //type of map

        return () -> {
            try {
                values.forEach((p, v) -> p.copy().accept(v));
                return callable.call();
            } finally {
                values.forEach((p, v) -> p.clear().accept(v));
            }
        };
    }

    /**
     * Method decorates runnable to copy variables across thread boundary.
     *
     * @param propagators the instance of {@link ContextPropagator}
     * @param runnable    the runnable to be decorated
     * @param <T>         the type of variable that cross thread boundary
     * @return decorated supplier of type T
     */
    static <T> Runnable decorateRunnable(List<? extends ContextPropagator> propagators,
                                         Runnable runnable) {
        Objects.requireNonNull(propagators, CONTEXT_PROPAGATOR_LIST_SHOULD_BE_NON_NULL);

        //Create identity map of <ContextPropagator,Optional Supplier value>, if we have duplicate ContextPropagators then last one wins.
        final Map<? extends ContextPropagator, Object> values = propagators.stream()
            .collect(toMap(
                p -> p, //key as ContextPropagator instance itself
                p -> p.retrieve().get(), //Supplier Optional value
                (first, second) -> second, //Merge function, this simply choose later value in key collision
                HashMap::new)); //type of map

        return () -> {
            try {
                values.forEach((p, v) -> p.copy().accept(v));
                runnable.run();
            } finally {
                values.forEach((p, v) -> p.clear().accept(v));
            }
        };
    }

    /**
     * Method decorates runnable to copy variables across thread boundary.
     *
     * @param propagator the instance of {@link ContextPropagator}
     * @param runnable   the runnable to be decorated
     * @param <T>        the type of variable that cross thread boundary
     * @return decorated supplier of type T
     */
    static <T> Runnable decorateRunnable(ContextPropagator propagator,
                                         Runnable runnable) {
        final Optional value = (Optional) propagator.retrieve().get();
        return () -> {
            try {
                propagator.copy().accept(value);
                runnable.run();
            } finally {
                propagator.clear().accept(value);
            }
        };
    }

    /**
     * An empty context propagator.
     *
     * @param <T> type.
     * @return an empty {@link ContextPropagator}
     */
    static <T> ContextPropagator<T> empty() {
        return new EmptyContextPropagator<>();
    }

    /**
     * A convenient implementation of empty {@link ContextPropagator}
     *
     * @param <T> type of class.
     */
    class EmptyContextPropagator<T> implements ContextPropagator<T> {

        @Override
        public Supplier<Optional<T>> retrieve() {
            return Optional::empty;
        }

        @Override
        public Consumer<Optional<T>> copy() {
            return t -> {
            };
        }

        @Override
        public Consumer<Optional<T>> clear() {
            return t -> {
            };
        }
    }
}
