/*
 *
 *  Copyright 2020: Robert Winkler
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

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.function.*;

/**
 * CompletionStage 工具类 - 提供异步编程的异常恢复和函数组合功能
 *
 * 作用说明：
 * 这是一个工具类，提供 CompletionStage (CompletableFuture) 的增强功能，
 * 主要用于异步编程中的异常恢复和结果转换。
 *
 * 为什么需要这个工具类？
 * CompletableFuture 是 Java 8 引入的异步编程利器，但在实际使用中有一些痛点：
 * 1. 异常处理复杂：exceptionally() 恢复所有异常，无法选择性恢复
 * 2. 包装异常：CompletionException 和 ExecutionException 包装了真实异常
 * 3. 结果恢复：无法根据结果值进行恢复（如 HTTP 状态码 500）
 *
 * CompletionStageUtils 解决方案：
 * - 选择性异常恢复：只恢复特定类型的异常
 * - 自动解包装：处理 CompletionException/ExecutionException
 * - 结果恢复：根据 Predicate 判断结果，选择性恢复
 * - Supplier 装饰器：延迟执行，支持多次调用
 *
 * 核心概念：
 * <pre>
 * CompletionStage - Java 8 异步编程接口
 *   ├─ 正常结果流：thenApply(), thenCompose(), thenAccept()
 *   └─ 异常恢复流：exceptionally(), handle(), whenComplete()
 *
 * 本工具类增强：
 *   ├─ recover(异常恢复)：从异常中恢复，返回备用值
 *   ├─ recover(结果恢复)：从不满意的结果中恢复
 *   └─ andThen(函数组合)：组合多个 CompletionStage
 * </pre>
 *
 * 使用场景：
 * - Resilience4j 的异步模式：断路器、重试、限流的异步支持
 * - 微服务调用：HTTP 调用失败后提供降级响应
 * - 异步任务：后台任务失败后的恢复逻辑
 * - Reactive 编程：与 Reactor、RxJava 集成
 *
 * 典型使用示例：
 * <pre>
 * // 场景1：从特定异常恢复
 * CompletionStage&lt;String&gt; future = callRemoteService();
 *
 * // 只从 TimeoutException 恢复，其他异常继续传播
 * CompletionStage&lt;String&gt; recovered = CompletionStageUtils.recover(
 *     future,
 *     TimeoutException.class,
 *     ex -&gt; "Fallback value" // 超时时返回备用值
 * );
 *
 * // 场景2：从不满意的结果恢复
 * CompletionStage&lt;HttpResponse&gt; httpFuture = httpClient.get(url);
 *
 * // HTTP 5xx 错误时重新请求或返回缓存
 * CompletionStage&lt;HttpResponse&gt; handled = CompletionStageUtils.recover(
 *     httpFuture,
 *     response -&gt; response.statusCode() &gt;= 500, // 判断条件
 *     response -&gt; getCachedResponse() // 恢复逻辑
 * );
 *
 * // 场景3：装饰 Supplier，延迟执行
 * Supplier&lt;CompletionStage&lt;Data&gt;&gt; dataSupplier = () -&gt; fetchDataAsync();
 *
 * // 装饰后可以多次调用，每次都会应用恢复逻辑
 * Supplier&lt;CompletionStage&lt;Data&gt;&gt; decorated = CompletionStageUtils.recover(
 *     dataSupplier,
 *     IOException.class,
 *     ex -&gt; getDefaultData()
 * );
 *
 * // 第一次调用
 * CompletionStage&lt;Data&gt; result1 = decorated.get();
 * // 第二次调用
 * CompletionStage&lt;Data&gt; result2 = decorated.get();
 * </pre>
 *
 * 异常处理原理：
 * <pre>
 * [异步任务]
 *     ↓
 * [抛出异常] → CompletionException/ExecutionException (包装异常)
 *     ↓
 * [解包装] → 提取真实异常 (throwable.getCause())
 *     ↓
 * [类型匹配] → 检查异常类型是否匹配
 *     ↓
 * [应用恢复] → 调用 exceptionHandler 生成恢复值
 *     ↓
 * [完成 Promise] → 返回恢复后的 CompletionStage
 * </pre>
 *
 * 与标准 API 的对比：
 * <pre>
 * // 标准 exceptionally() - 恢复所有异常
 * future.exceptionally(ex -&gt; "fallback");
 * // 问题：无法区分异常类型，所有异常都会被恢复
 *
 * // CompletionStageUtils.recover() - 选择性恢复
 * CompletionStageUtils.recover(future, TimeoutException.class, ex -&gt; "fallback");
 * // 优势：只恢复 TimeoutException，其他异常继续传播
 * </pre>
 *
 * 线程安全性：
 * - 所有方法都是无状态的静态方法，线程安全
 * - CompletableFuture 本身是线程安全的
 * - 恢复函数需要由调用者保证线程安全
 *
 * 性能考虑：
 * - Promise 模式：创建新的 CompletableFuture 作为代理
 * - 异常匹配：使用反射检查异常类型
 * - 适合异步场景，同步场景使用 Try 或 Either 更高效
 *
 * 注意事项：
 * - exceptionHandler 不应抛出异常，否则会导致 Promise 异常完成
 * - resultHandler 也不应抛出异常
 * - CompletionException/ExecutionException 会自动解包装
 * - 异常类型匹配使用 isAssignableFrom，支持子类
 *
 * @author Robert Winkler
 * @since 1.0.0
 * @see CompletionStage
 * @see CompletableFuture
 * @see CompletionException
 */
public class CompletionStageUtils {

    /** 私有构造函数，防止实例化 */
    private CompletionStageUtils() {
        // 工具类，不允许实例化
    }

    /**
     * 从任何异常中恢复 - 无条件恢复所有异常
     *
     * 功能说明：
     * 当 CompletionStage 异常完成时，应用恢复函数生成备用值。
     * 这是最简单的恢复方式，不区分异常类型，恢复所有异常。
     *
     * 执行流程：
     * 1. 异步任务执行
     * 2. 如果抛出异常，调用 exceptionHandler
     * 3. exceptionHandler 返回恢复值
     * 4. 返回包含恢复值的 CompletionStage
     *
     * 使用场景：
     * - 提供默认值：无论什么异常，都返回默认值
     * - 简单降级：不关心异常类型，统一处理
     * - 日志记录：记录异常并返回备用值
     *
     * 使用示例：
     * <pre>
     * CompletionStage&lt;String&gt; future = fetchDataAsync();
     *
     * // 任何异常都返回 "default"
     * CompletionStage&lt;String&gt; recovered = CompletionStageUtils.recover(
     *     future,
     *     ex -&gt; {
     *         logger.error("Failed to fetch data", ex);
     *         return "default";
     *     }
     * );
     * </pre>
     *
     * 与其他 recover 方法的对比：
     * - recover(all)：恢复所有异常（本方法）
     * - recover(type)：只恢复特定类型的异常
     * - recover(types)：只恢复列表中的异常类型
     *
     * 注意事项：
     * - exceptionHandler 不应抛出异常，否则会导致新的异常
     * - 这是对 CompletionStage.exceptionally() 的简单封装
     * - 无法区分异常类型，谨慎使用
     *
     * @param <T>              CompletionStage 的结果类型
     * @param completionStage  要恢复的 CompletionStage
     * @param exceptionHandler 异常恢复函数，接收异常并返回恢复值
     * @return 恢复后的 CompletionStage，异常时返回恢复值
     */
    public static <T> CompletionStage<T> recover(CompletionStage<T> completionStage, Function<Throwable, T> exceptionHandler){
        // 直接委托给 CompletionStage.exceptionally()
        return completionStage.exceptionally(exceptionHandler);
    }

    /**
     * 从特定异常列表中恢复 - 选择性恢复多种异常类型
     *
     * 功能说明：
     * 只有当抛出的异常类型在指定列表中时，才应用恢复函数。
     * 其他类型的异常会继续传播，不会被恢复。
     * 自动处理 CompletionException 和 ExecutionException 的解包装。
     *
     * 执行流程：
     * 1. 异步任务执行
     * 2. 如果抛出异常：
     *    a. 如果是 CompletionException/ExecutionException，解包装获取真实异常
     *    b. 检查真实异常类型是否在 exceptionTypes 列表中
     *    c. 如果匹配，调用 exceptionHandler 生成恢复值
     *    d. 如果不匹配，异常继续传播
     * 3. 如果正常完成，直接返回结果
     *
     * 为什么需要解包装？
     * CompletableFuture 在异步执行时会将异常包装：
     * - CompletionException：在 thenApply/thenCompose 等链中抛出
     * - ExecutionException：在 get() 等阻塞方法中抛出
     * 解包装后才能获取真实的异常类型（如 IOException、TimeoutException）
     *
     * 使用场景：
     * - 网络调用：只恢复 IOException 和 TimeoutException
     * - 数据库操作：只恢复 SQLException
     * - 多种失败模式：需要区分不同的异常类型
     *
     * 使用示例：
     * <pre>
     * CompletionStage&lt;Data&gt; future = remoteCall();
     *
     * // 只恢复 TimeoutException 和 IOException
     * CompletionStage&lt;Data&gt; recovered = CompletionStageUtils.recover(
     *     future,
     *     Arrays.asList(TimeoutException.class, IOException.class),
     *     ex -&gt; {
     *         logger.warn("Network error: {}", ex.getMessage());
     *         return getCachedData();
     *     }
     * );
     *
     * // IllegalArgumentException 等其他异常不会被恢复，会继续传播
     * </pre>
     *
     * Promise 模式说明：
     * 本方法使用 Promise 模式实现：
     * 1. 创建新的 CompletableFuture 作为代理 (promise)
     * 2. 监听原始 CompletionStage 的完成事件 (whenComplete)
     * 3. 根据结果或异常，完成或异常完成 promise
     * 4. 返回 promise 作为新的 CompletionStage
     *
     * 异常类型匹配：
     * - 使用 isAssignableFrom() 检查类型，支持子类
     * - 例如：exceptionTypes 包含 IOException，可以匹配 SocketException
     *
     * 注意事项：
     * - exceptionHandler 如果抛出异常，会导致 promise 异常完成
     * - exceptionTypes 不应为空，否则不会恢复任何异常
     * - 异常类型匹配顺序与列表顺序无关，只要匹配任一类型即恢复
     *
     * @param <T>              CompletionStage 的结果类型
     * @param completionStage  要恢复的 CompletionStage
     * @param exceptionTypes   需要恢复的异常类型列表
     * @param exceptionHandler 异常恢复函数，接收匹配的异常并返回恢复值
     * @return 恢复后的 CompletionStage，匹配的异常会被恢复，其他异常继续传播
     */
    public static <T> CompletionStage<T> recover(CompletionStage<T> completionStage, List<Class<? extends Throwable>> exceptionTypes, Function<Throwable, T> exceptionHandler){
        // 创建 Promise (代理 CompletableFuture)
        CompletableFuture<T> promise = new CompletableFuture<>();

        // 监听原始 CompletionStage 的完成事件
        completionStage.whenComplete((result, throwable) -> {
            if (throwable != null){
                // 异常完成：尝试恢复

                // 检查是否为包装异常，如果是则解包装获取真实异常
                if (throwable instanceof CompletionException || throwable instanceof ExecutionException) {
                    // 解包装：getCause() 获取真实异常
                    tryRecover(exceptionTypes, exceptionHandler, promise, throwable.getCause());
                }else{
                    // 不是包装异常，直接尝试恢复
                    tryRecover(exceptionTypes, exceptionHandler, promise, throwable);
                }

            } else {
                // 正常完成：直接完成 Promise
                promise.complete(result);
            }
        });

        // 返回 Promise
        return promise;
    }

    /**
     * 从单个特定异常中恢复 - 选择性恢复一种异常类型
     *
     * 功能说明：
     * 只有当抛出的异常类型匹配指定类型时，才应用恢复函数。
     * 与多异常版本类似，但只针对单个异常类型，使用更方便。
     *
     * 执行流程：
     * 1. 异步任务执行
     * 2. 如果抛出异常，解包装（如果需要）获取真实异常
     * 3. 检查真实异常类型是否匹配 exceptionType
     * 4. 如果匹配，应用 exceptionHandler；否则异常继续传播
     * 5. 如果正常完成，直接返回结果
     *
     * 使用场景：
     * - 超时恢复：只恢复 TimeoutException
     * - 网络错误：只恢复 IOException
     * - 并发问题：只恢复 ConcurrentModificationException
     *
     * 使用示例：
     * <pre>
     * CompletionStage&lt;String&gt; future = callWithTimeout();
     *
     * // 只恢复 TimeoutException，其他异常继续传播
     * CompletionStage&lt;String&gt; recovered = CompletionStageUtils.recover(
     *     future,
     *     TimeoutException.class,
     *     ex -&gt; "Request timed out, using cached data"
     * );
     * </pre>
     *
     * @param <X>              异常类型
     * @param <T>              CompletionStage 的结果类型
     * @param completionStage  要恢复的 CompletionStage
     * @param exceptionType    需要恢复的异常类型
     * @param exceptionHandler 异常恢复函数，接收匹配的异常并返回恢复值
     * @return 恢复后的 CompletionStage，匹配的异常会被恢复，其他异常继续传播
     */
    public static <X extends Throwable, T> CompletionStage<T> recover(CompletionStage<T> completionStage, Class<X> exceptionType, Function<Throwable, T> exceptionHandler){
        // 创建 Promise
        CompletableFuture<T> promise = new CompletableFuture<>();

        // 监听完成事件
        completionStage.whenComplete((result, throwable) -> {
            if (throwable != null){
                // 异常完成：解包装并尝试恢复
                if (throwable instanceof CompletionException || throwable instanceof ExecutionException) {
                    tryRecover(exceptionType, exceptionHandler, promise, throwable.getCause());
                }else{
                    tryRecover(exceptionType, exceptionHandler, promise, throwable);
                }

            } else {
                // 正常完成
                promise.complete(result);
            }
        });
        return promise;
    }

    /**
     * 尝试从多个异常类型中恢复 - 内部辅助方法
     *
     * 功能说明：
     * 检查异常类型是否匹配列表中的任一类型，如果匹配则应用恢复函数。
     *
     * 执行逻辑：
     * 1. 遍历 exceptionTypes，检查 throwable 是否匹配任一类型
     * 2. 如果匹配：
     *    a. 调用 exceptionHandler 生成恢复值
     *    b. 如果 exceptionHandler 抛出异常，promise 异常完成
     *    c. 否则 promise 正常完成
     * 3. 如果不匹配：promise 异常完成（异常继续传播）
     *
     * 异常安全：
     * - 使用 try-catch 捕获 exceptionHandler 的异常
     * - 确保 promise 一定会完成（正常或异常）
     *
     * @param <T>              结果类型
     * @param exceptionTypes   异常类型列表
     * @param exceptionHandler 恢复函数
     * @param promise          Promise，用于传递恢复结果
     * @param throwable        需要恢复的异常
     */
    private static <T> void tryRecover(List<Class<? extends Throwable>> exceptionTypes,
        Function<Throwable, T> exceptionHandler, CompletableFuture<T> promise,
        Throwable throwable) {
        // 检查异常类型是否匹配列表中的任一类型
        if(exceptionTypes.stream().anyMatch(exceptionType -> exceptionType.isAssignableFrom(throwable.getClass()))) {
            try {
                // 匹配：应用恢复函数并完成 Promise
                promise.complete(exceptionHandler.apply(throwable));
            } catch (Exception fallbackException) {
                // 恢复函数抛出异常：Promise 异常完成
                promise.completeExceptionally(fallbackException);
            }
        }else{
            // 不匹配：异常继续传播
            promise.completeExceptionally(throwable);
        }
    }

    /**
     * 尝试从单个异常类型中恢复 - 内部辅助方法
     *
     * 功能说明：
     * 检查异常类型是否匹配指定类型，如果匹配则应用恢复函数。
     * 与多异常版本类似，但只检查单个类型。
     *
     * 类型匹配：
     * - 使用 isAssignableFrom() 检查，支持子类
     * - 例如：exceptionType = IOException，可以匹配 SocketException
     *
     * @param <X>              异常类型
     * @param <T>              结果类型
     * @param exceptionType    异常类型
     * @param exceptionHandler 恢复函数
     * @param promise          Promise，用于传递恢复结果
     * @param throwable        需要恢复的异常
     */
    private static <X extends Throwable, T> void tryRecover(Class<X> exceptionType,
        Function<Throwable, T> exceptionHandler, CompletableFuture<T> promise,
        Throwable throwable) {
        // 检查异常类型是否匹配
        if(exceptionType.isAssignableFrom(throwable.getClass())) {
            try {
                // 匹配：应用恢复函数
                promise.complete(exceptionHandler.apply(throwable));
            } catch (Exception fallbackException) {
                // 恢复函数抛出异常
                promise.completeExceptionally(fallbackException);
            }
        }else{
            // 不匹配：异常继续传播
            promise.completeExceptionally(throwable);
        }
    }

    /**
     * 装饰 Supplier 以从所有异常中恢复 - Supplier 版本
     *
     * 功能说明：
     * 返回一个装饰后的 Supplier，每次调用 get() 时都会应用异常恢复逻辑。
     * 这是装饰器模式的应用，延迟执行并支持多次调用。
     *
     * 装饰器模式优势：
     * - 延迟执行：只在调用 get() 时才创建 CompletionStage
     * - 可重用：可以多次调用 get()，每次都会应用恢复逻辑
     * - 组合性：可以与其他装饰器链式组合
     *
     * 使用场景：
     * - Resilience4j 装饰器：与断路器、重试等组合
     * - 多次调用：需要重复执行相同的异步操作
     *
     * @param <T>                   结果类型
     * @param completionStageSupplier CompletionStage 提供者
     * @param exceptionHandler      异常恢复函数
     * @return 装饰后的 Supplier，每次调用都会应用恢复逻辑
     */
    public static <T> Supplier<CompletionStage<T>> recover(
        Supplier<CompletionStage<T>> completionStageSupplier,
        Function<Throwable, T> exceptionHandler) {
        // 返回新的 Supplier：每次调用 get() 时应用 recover
        return () -> recover(completionStageSupplier.get(), exceptionHandler);
    }

    /**
     * 装饰 Supplier 以从特定异常中恢复 - 单异常类型 Supplier 版本
     *
     * 功能说明：
     * 返回装饰后的 Supplier，每次调用时应用单异常类型恢复逻辑。
     *
     * @param <T>                   结果类型
     * @param <X>                   异常类型
     * @param completionStageSupplier CompletionStage 提供者
     * @param exceptionType         需要恢复的异常类型
     * @param exceptionHandler      异常恢复函数
     * @return 装饰后的 Supplier
     */
    public static <T, X extends Throwable> Supplier<CompletionStage<T>> recover(
        Supplier<CompletionStage<T>> completionStageSupplier, Class<X> exceptionType,
        Function<Throwable, T> exceptionHandler) {
        // 返回新的 Supplier：应用单异常类型恢复
        return () -> recover(completionStageSupplier.get(), exceptionType, exceptionHandler);
    }

    /**
     * 装饰 Supplier 以从多个异常中恢复 - 多异常类型 Supplier 版本
     *
     * 功能说明：
     * 返回装饰后的 Supplier，每次调用时应用多异常类型恢复逻辑。
     *
     * @param <T>                   结果类型
     * @param completionStageSupplier CompletionStage 提供者
     * @param exceptionTypes        需要恢复的异常类型列表
     * @param exceptionHandler      异常恢复函数
     * @return 装饰后的 Supplier
     */
    public static <T> Supplier<CompletionStage<T>> recover(
        Supplier<CompletionStage<T>> completionStageSupplier, List<Class<? extends Throwable>> exceptionTypes,
        Function<Throwable, T> exceptionHandler) {
        // 返回新的 Supplier：应用多异常类型恢复
        return () -> recover(completionStageSupplier.get(), exceptionTypes, exceptionHandler);
    }

    /**
     * 从特定结果中恢复 - 基于结果值的恢复
     *
     * 功能说明：
     * 当 CompletionStage 正常完成但结果不满意时，应用恢复函数转换结果。
     * 这与异常恢复不同，是针对正常结果的条件转换。
     *
     * 执行流程：
     * 1. CompletionStage 正常完成，得到结果 result
     * 2. 使用 resultPredicate 判断结果是否需要恢复
     * 3. 如果需要恢复（predicate 返回 true），应用 resultHandler
     * 4. 如果不需要恢复，直接返回原始结果
     *
     * 使用场景：
     * - HTTP 响应：状态码 5xx 时返回缓存数据
     * - 数据库查询：空结果时返回默认值
     * - 业务逻辑：不满意的结果需要重新计算
     *
     * 使用示例：
     * <pre>
     * CompletionStage&lt;HttpResponse&gt; responseFuture = httpCall();
     *
     * // HTTP 5xx 错误时使用缓存
     * CompletionStage&lt;HttpResponse&gt; recovered = CompletionStageUtils.recover(
     *     responseFuture,
     *     response -&gt; response.statusCode() &gt;= 500,  // 判断条件
     *     response -&gt; getCachedResponse()            // 恢复逻辑
     * );
     * </pre>
     *
     * @param <T>              结果类型
     * @param completionStage  要检查的 CompletionStage
     * @param resultPredicate  结果判断谓词，返回 true 表示需要恢复
     * @param resultHandler    结果恢复函数，将不满意的结果转换为期望的结果
     * @return 恢复后的 CompletionStage
     */
    public static <T> CompletionStage<T> recover(
        CompletionStage<T> completionStage, Predicate<T> resultPredicate,
        UnaryOperator<T> resultHandler) {
        // 使用 thenApply 转换结果
        return completionStage.thenApply(result -> {
            // 判断结果是否需要恢复
            if(resultPredicate.test(result)){
                // 需要恢复：应用恢复函数
                return resultHandler.apply(result);
            }else{
                // 不需要恢复：返回原始结果
                return result;
            }
        });
    }

    /**
     * 装饰 Supplier 以从特定结果中恢复 - 结果恢复 Supplier 版本
     *
     * 功能说明：
     * 返回装饰后的 Supplier，每次调用时应用结果恢复逻辑。
     *
     * @param <T>                   结果类型
     * @param completionStageSupplier CompletionStage 提供者
     * @param resultPredicate       结果判断谓词
     * @param resultHandler         结果恢复函数
     * @return 装饰后的 Supplier
     */
    public static <T> Supplier<CompletionStage<T>> recover(
        Supplier<CompletionStage<T>> completionStageSupplier, Predicate<T> resultPredicate,
        UnaryOperator<T> resultHandler) {
        // 返回新的 Supplier：应用结果恢复逻辑
        return () -> recover(completionStageSupplier.get(), resultPredicate, resultHandler);
    }

    /**
     * 函数组合 - 处理结果和异常
     *
     * 功能说明：
     * 装饰 Supplier，使用 BiFunction 同时处理正常结果和异常。
     * handler 会接收两个参数：结果（成功时）和异常（失败时），其中一个为 null。
     *
     * 与 recover 的区别：
     * - recover：只处理异常，返回恢复值
     * - andThen：同时处理结果和异常，可以转换类型
     *
     * 使用场景：
     * - 统一处理：无论成功失败都需要处理
     * - 类型转换：将 CompletionStage&lt;T&gt; 转换为 CompletionStage&lt;R&gt;
     * - 日志记录：记录成功和失败的情况
     *
     * 使用示例：
     * <pre>
     * Supplier&lt;CompletionStage&lt;Data&gt;&gt; dataSupplier = () -&gt; fetchDataAsync();
     *
     * // 将 Data 转换为 String，同时处理异常
     * Supplier&lt;CompletionStage&lt;String&gt;&gt; stringSupplier = CompletionStageUtils.andThen(
     *     dataSupplier,
     *     (data, ex) -&gt; {
     *         if (ex != null) {
     *             return "Error: " + ex.getMessage();
     *         } else {
     *             return data.toString();
     *         }
     *     }
     * );
     * </pre>
     *
     * @param <T>                   原始结果类型
     * @param <R>                   转换后的结果类型
     * @param completionStageSupplier CompletionStage 提供者
     * @param handler               处理函数，接收结果和异常，返回新结果
     * @return 装饰后的 Supplier，返回转换后的 CompletionStage
     */
    public static <T, R> Supplier<CompletionStage<R>> andThen(Supplier<CompletionStage<T>> completionStageSupplier,
        BiFunction<T, Throwable, R> handler) {
        // 返回新的 Supplier：应用 handle 处理结果和异常
        return () -> completionStageSupplier.get().handle(handler);
    }
}
