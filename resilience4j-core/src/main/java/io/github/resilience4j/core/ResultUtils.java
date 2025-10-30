/*
 *
 *  Copyright 2016 Robert Winkler and Bohdan Storozhuk
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

import io.github.resilience4j.core.functions.Either;

import java.util.function.Function;

/**
 * 结果判断工具类 - 检查 Either 类型的调用结果
 *
 * 作用说明：
 * 提供判断方法来检查异步调用或函数执行的结果，支持成功和失败两种情况。
 * 使用 Either 类型表示"二选一"的结果：要么成功返回值，要么失败抛出异常。
 *
 * Either 类型简介：
 * Either 是函数式编程中的常用类型，表示两种可能性中的一种：
 * - Left（左）：通常表示失败，包含异常
 * - Right（右）：通常表示成功，包含结果值
 *
 * <pre>
 * Either&lt;Throwable, String&gt;
 *   ├─ Left(IOException)       - 失败情况，包含异常
 *   └─ Right("success")        - 成功情况，包含结果
 * </pre>
 *
 * 为什么需要这个工具类？
 * 在 Resilience4j 中，很多操作的结果需要精确判断：
 * 1. 断路器：判断调用是否成功，决定是否打开断路器
 * 2. 重试：判断失败类型，决定是否重试
 * 3. Bulkhead：判断结果类型，进行统计
 * 4. 测试：验证特定的成功或失败情况
 *
 * 核心功能：
 * - isSuccessfulAndReturned：判断是否成功返回了特定类型的值
 * - isFailedAndThrown：判断是否失败并抛出了特定类型的异常
 *
 * 使用场景：
 * - 断路器配置：recordResultPredicate 判断调用结果是否应该记录为失败
 * - 测试断言：验证 Either 结果是否符合预期
 * - 统计分析：根据结果类型进行分类统计
 * - 条件执行：根据结果类型执行不同逻辑
 *
 * 典型使用示例：
 * <pre>
 * // 场景1：判断是否返回了特定值
 * Either&lt;Throwable, HttpResponse&gt; result = callApi();
 *
 * // 判断是否成功返回了 HttpResponse，且状态码为 200
 * boolean is200 = ResultUtils.isSuccessfulAndReturned(
 *     result,
 *     HttpResponse.class,
 *     response -&gt; response.statusCode() == 200
 * );
 *
 * // 场景2：判断是否抛出了特定异常
 * // 判断是否抛出了 TimeoutException
 * boolean isTimeout = ResultUtils.isFailedAndThrown(
 *     result,
 *     TimeoutException.class
 * );
 *
 * // 场景3：判断异常并检查异常信息
 * boolean isSpecificError = ResultUtils.isFailedAndThrown(
 *     result,
 *     IOException.class,
 *     ex -&gt; ex.getMessage().contains("Connection refused")
 * );
 *
 * // 场景4：在断路器配置中使用
 * CircuitBreakerConfig config = CircuitBreakerConfig.custom()
 *     .recordResultPredicate(result -&gt;
 *         // 5xx 错误视为失败
 *         ResultUtils.isSuccessfulAndReturned(
 *             result,
 *             HttpResponse.class,
 *             response -&gt; response.statusCode() &gt;= 500
 *         )
 *     )
 *     .build();
 * </pre>
 *
 * 判断逻辑：
 * <pre>
 * isSuccessfulAndReturned:
 * 1. 检查是否为 Right（成功）
 * 2. 检查结果是否非 null
 * 3. 检查结果类型是否匹配
 * 4. 应用自定义检查器
 *
 * isFailedAndThrown:
 * 1. 检查是否为 Left（失败）
 * 2. 检查异常类型是否匹配
 * 3. 应用自定义检查器
 * </pre>
 *
 * 与其他判断方式的对比：
 * <pre>
 * // 传统方式：繁琐且容易出错
 * if (result.isRight()) {
 *     Object value = result.get();
 *     if (value instanceof HttpResponse) {
 *         HttpResponse response = (HttpResponse) value;
 *         if (response.statusCode() == 200) {
 *             // ...
 *         }
 *     }
 * }
 *
 * // ResultUtils：简洁清晰
 * if (ResultUtils.isSuccessfulAndReturned(
 *     result,
 *     HttpResponse.class,
 *     r -&gt; r.statusCode() == 200
 * )) {
 *     // ...
 * }
 * </pre>
 *
 * 线程安全性：
 * - 所有方法都是无状态的静态方法，线程安全
 * - Either 本身是不可变的，线程安全
 *
 * 注意事项：
 * - 类型检查使用 isAssignableFrom，支持子类
 * - null 结果视为失败（isSuccessfulAndReturned 返回 false）
 * - 类型不匹配视为不满足条件，不会抛出异常
 *
 * @author Robert Winkler, Bohdan Storozhuk
 * @since 1.0.0
 * @see Either
 */
public class ResultUtils {

    /** 私有构造函数，防止实例化 */
    private ResultUtils() {
        // 工具类，不允许实例化
    }

    /**
     * 判断是否成功返回了特定类型的值
     *
     * 功能说明：
     * 检查 Either 结果是否满足以下所有条件：
     * 1. 是成功结果（isRight）
     * 2. 结果值非 null
     * 3. 结果值的类型与期望类型匹配或为其子类
     * 4. 结果值通过自定义检查器的验证
     *
     * 执行流程：
     * 1. 检查 Either 是否为 Left（失败），如果是则返回 false
     * 2. 获取结果值，如果为 null 则返回 false
     * 3. 检查结果值类型是否匹配 expectedClass，不匹配返回 false
     * 4. 应用 returnedChecker 对结果值进行自定义检查，返回检查结果
     *
     * 类型检查：
     * - 使用 isAssignableFrom() 检查类型，支持子类
     * - 例如：expectedClass = Number，可以匹配 Integer、Double 等
     *
     * 使用场景：
     * - HTTP 响应：检查是否返回了成功的响应（状态码 2xx）
     * - 数据库查询：检查是否返回了非空结果
     * - 断路器：判断调用结果是否应该记录为失败
     * - 业务逻辑：验证返回值是否满足业务条件
     *
     * 使用示例：
     * <pre>
     * Either&lt;Throwable, HttpResponse&gt; result = callApi();
     *
     * // 示例1：检查是否返回了 200 状态码
     * boolean is200 = ResultUtils.isSuccessfulAndReturned(
     *     result,
     *     HttpResponse.class,
     *     response -&gt; response.statusCode() == 200
     * );
     *
     * // 示例2：检查是否返回了非空列表
     * Either&lt;Throwable, List&lt;String&gt;&gt; listResult = queryDatabase();
     * boolean hasData = ResultUtils.isSuccessfulAndReturned(
     *     listResult,
     *     List.class,
     *     list -&gt; !list.isEmpty()
     * );
     *
     * // 示例3：检查返回值是否在有效范围内
     * Either&lt;Throwable, Integer&gt; numberResult = calculate();
     * boolean isValid = ResultUtils.isSuccessfulAndReturned(
     *     numberResult,
     *     Integer.class,
     *     num -&gt; num &gt; 0 && num &lt; 100
     * );
     * </pre>
     *
     * @param <T>             期望的结果类型
     * @param callsResult     Either 类型的调用结果
     * @param expectedClass   期望的结果类型 Class 对象
     * @param returnedChecker 自定义检查器，对结果值进行额外验证
     * @return 如果满足所有条件返回 true，否则返回 false
     */
    @SuppressWarnings("unchecked")
    public static <T> boolean isSuccessfulAndReturned(
        Either<? extends Throwable, ?> callsResult,
        Class<T> expectedClass,
        Function<T, Boolean> returnedChecker) {
        // 1. 检查是否为失败结果（Left）
        if (callsResult.isLeft()) {
            return false;
        }

        // 2. 获取结果值
        Object result = callsResult.get();

        // 3. 检查结果是否为 null
        if (result == null) {
            return false;
        }

        // 4. 检查结果类型是否匹配期望类型
        if (!expectedClass.isAssignableFrom(result.getClass())) {
            return false;
        }

        // 5. 应用自定义检查器，验证结果值
        return returnedChecker.apply((T) result);
    }

    /**
     * 判断是否失败并抛出了特定类型的异常 - 简化版本
     *
     * 功能说明：
     * 检查 Either 结果是否失败（isLeft）且异常类型匹配 expectedClass。
     * 这是 isFailedAndThrown(Either, Class, Function) 的简化版本，
     * 不需要自定义检查器，只检查异常类型。
     *
     * 使用场景：
     * - 检查是否抛出了特定异常：TimeoutException、IOException 等
     * - 重试逻辑：判断异常类型决定是否重试
     * - 统计分析：统计异常类型分布
     *
     * 使用示例：
     * <pre>
     * Either&lt;Throwable, String&gt; result = remoteCall();
     *
     * // 检查是否超时
     * if (ResultUtils.isFailedAndThrown(result, TimeoutException.class)) {
     *     // 处理超时情况
     *     retry();
     * }
     * </pre>
     *
     * @param <T>           异常类型
     * @param callsResult   Either 类型的调用结果
     * @param expectedClass 期望的异常类型 Class 对象
     * @return 如果失败且异常类型匹配返回 true，否则返回 false
     */
    public static <T extends Throwable>  boolean isFailedAndThrown(
        Either<? extends Throwable, ?> callsResult,
        Class<T> expectedClass) {
        // 委托给完整版本，检查器始终返回 true（不进行额外检查）
        return isFailedAndThrown(callsResult, expectedClass, thrown -> true);
    }

    /**
     * 判断是否失败并抛出了特定类型的异常 - 完整版本
     *
     * 功能说明：
     * 检查 Either 结果是否满足以下所有条件：
     * 1. 是失败结果（isLeft）
     * 2. 异常类型与期望类型匹配或为其子类
     * 3. 异常通过自定义检查器的验证
     *
     * 执行流程：
     * 1. 检查 Either 是否为 Right（成功），如果是则返回 false
     * 2. 获取异常对象
     * 3. 检查异常类型是否匹配 expectedClass，不匹配返回 false
     * 4. 应用 thrownChecker 对异常进行自定义检查，返回检查结果
     *
     * 类型检查：
     * - 使用 isAssignableFrom() 检查类型，支持子类
     * - 例如：expectedClass = IOException，可以匹配 SocketException
     *
     * 使用场景：
     * - 精确匹配异常：不仅检查类型，还检查异常消息或其他属性
     * - 重试条件：根据异常详情决定是否重试
     * - 错误分类：根据异常特征进行分类统计
     * - 测试验证：验证是否抛出了预期的异常
     *
     * 使用示例：
     * <pre>
     * Either&lt;Throwable, Data&gt; result = fetchData();
     *
     * // 示例1：检查是否抛出了包含特定消息的 IOException
     * boolean isConnectionRefused = ResultUtils.isFailedAndThrown(
     *     result,
     *     IOException.class,
     *     ex -&gt; ex.getMessage().contains("Connection refused")
     * );
     *
     * // 示例2：检查 HTTP 异常的状态码
     * boolean is404 = ResultUtils.isFailedAndThrown(
     *     result,
     *     HttpException.class,
     *     ex -&gt; ex.getStatusCode() == 404
     * );
     *
     * // 示例3：在重试配置中使用
     * RetryConfig config = RetryConfig.custom()
     *     .retryOnException(ex -&gt;
     *         ResultUtils.isFailedAndThrown(
     *             Either.left(ex),
     *             IOException.class,
     *             e -&gt; !e.getMessage().contains("Permanent failure")
     *         )
     *     )
     *     .build();
     * </pre>
     *
     * @param <T>            异常类型
     * @param callsResult    Either 类型的调用结果
     * @param expectedClass  期望的异常类型 Class 对象
     * @param thrownChecker  自定义检查器，对异常进行额外验证
     * @return 如果满足所有条件返回 true，否则返回 false
     */
    @SuppressWarnings("unchecked")
    public static <T extends Throwable>  boolean isFailedAndThrown(
        Either<? extends Throwable, ?> callsResult,
        Class<T> expectedClass,
        Function<T, Boolean> thrownChecker) {
        // 1. 检查是否为成功结果（Right）
        if (callsResult.isRight()) {
            return false;
        }

        // 2. 获取异常对象
        Throwable thrown = callsResult.getLeft();

        // 3. 检查异常类型是否匹配期望类型
        if (!expectedClass.isAssignableFrom(thrown.getClass())) {
            return false;
        }

        // 4. 应用自定义检查器，验证异常
        return thrownChecker.apply((T) thrown);
    }
}
