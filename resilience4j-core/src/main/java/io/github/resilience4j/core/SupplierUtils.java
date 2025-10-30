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
import java.util.function.*;

/**
 * Supplier 工具类 - 无参数的函数式编程辅助工具
 *
 * 作用说明：
 * 提供 Supplier 的组合、异常恢复等实用方法。
 * Supplier 是 Java 中无参数的函数式接口，表示数据提供者或延迟计算。
 *
 * 与其他工具类的区别：
 * - Supplier.get()：无参数，返回结果，只能抛出运行时异常
 * - Function.apply(T)：有参数，返回结果，只能抛出运行时异常
 * - Callable.call()：无参数，返回结果，可以抛出检查异常
 *
 * 主要功能：
 * 1. andThen：将 Supplier 与后续处理函数组合
 * 2. recover：为 Supplier 添加异常恢复逻辑
 *
 * 使用场景：
 * - 延迟计算：只有在需要时才执行计算
 * - 数据获取：从缓存、数据库等获取数据
 * - 默认值提供：为 Optional 提供默认值
 * - 工厂方法：作为对象创建的工厂
 *
 * 示例：
 * <pre>
 * // 延迟计算复杂值
 * Supplier&lt;BigDecimal&gt; expensiveCalculation = () -&gt; calculateTax();
 *
 * // 从缓存获取数据
 * Supplier&lt;User&gt; getUser = () -&gt; cache.get("user:123");
 *
 * // 提供默认值
 * String value = Optional.ofNullable(config)
 *     .orElseGet(() -&gt; "default-config");
 * </pre>
 *
 * @author Robert Winkler
 * @since 1.0.0
 */
public class SupplierUtils {

    /** 私有构造函数，防止实例化 */
    private SupplierUtils() {
    }

    /**
     * 组合函数：先执行 Supplier，再对结果应用转换函数
     *
     * 功能说明：
     * 将 Supplier 的执行结果传递给 resultHandler 进行后续处理。
     * 这是 Supplier 的管道操作，用于链式转换数据。
     *
     * 执行流程：
     * 1. 调用 supplier.get() 获取结果
     * 2. 将结果传递给 resultHandler.apply() 进行转换
     * 3. 返回转换后的结果
     *
     * 使用场景：
     * - 数据转换：将获取的数据转换为另一种形式
     * - 延迟计算链：组合多个延迟计算步骤
     * - 工厂模式：创建对象后进行初始化
     *
     * 示例：
     * <pre>
     * // 从配置获取端口号，转换为URL
     * Supplier&lt;Integer&gt; getPort = () -&gt; config.getPort();
     * Function&lt;Integer, String&gt; toUrl = port -&gt; "http://localhost:" + port;
     *
     * Supplier&lt;String&gt; getUrl = SupplierUtils.andThen(getPort, toUrl);
     * String url = getUrl.get(); // 返回: "http://localhost:8080"
     * </pre>
     *
     * @param <T>           Supplier 的返回类型
     * @param <R>           resultHandler 的返回类型（最终返回类型）
     * @param supplier      要执行的 Supplier
     * @param resultHandler 用于处理 supplier 结果的转换函数
     * @return 组合后的 Supplier，返回类型为 R
     */
    public static <T, R> Supplier<R> andThen(Supplier<T> supplier, Function<T, R> resultHandler) {
        return () -> resultHandler.apply(supplier.get());
    }

    /**
     * 组合函数：执行 Supplier，并用统一的 BiFunction 处理成功和失败两种情况
     *
     * 功能说明：
     * 使用 BiFunction 同时处理成功结果和异常情况。
     * handler 接收两个参数：结果值和异常，其中一个为 null。
     *
     * 执行流程：
     * 1. 尝试调用 supplier.get()
     * 2. 如果成功：调用 handler.apply(result, null)
     * 3. 如果失败：调用 handler.apply(null, exception)
     * 4. 返回 handler 的处理结果
     *
     * 使用场景：
     * - 统一结果处理：用一个函数同时处理成功和失败
     * - Either 模式：将结果和错误封装为统一类型
     * - 降级处理：根据成功或失败返回不同值
     *
     * 示例：
     * <pre>
     * // 获取配置，统一处理成功和失败
     * Supplier&lt;Config&gt; loadConfig = () -&gt; configLoader.load();
     *
     * BiFunction&lt;Config, Throwable, String&gt; handler = (config, error) -&gt; {
     *     if (error != null) {
     *         return "配置加载失败: " + error.getMessage();
     *     } else {
     *         return "配置加载成功: " + config.getName();
     *     }
     * };
     *
     * Supplier&lt;String&gt; safeLoad = SupplierUtils.andThen(loadConfig, handler);
     * String result = safeLoad.get(); // 永远不会抛出异常
     * </pre>
     *
     * @param <T>      Supplier 的返回类型
     * @param <R>      handler 的返回类型（最终返回类型）
     * @param supplier 要执行的 Supplier
     * @param handler  处理结果和异常的双参数函数
     * @return 组合后的 Supplier，返回类型为 R
     */
    public static <T, R> Supplier<R> andThen(Supplier<T> supplier,
        BiFunction<T, Throwable, R> handler) {
        return () -> {
            try {
                // 尝试执行 supplier
                T result = supplier.get();
                // 成功：将结果传递给 handler，异常参数为 null
                return handler.apply(result, null);
            } catch (Exception exception) {
                // 失败：将异常传递给 handler，结果参数为 null
                return handler.apply(null, exception);
            }
        };
    }

    /**
     * 结果恢复：执行 Supplier，如果结果满足特定条件则进行恢复处理
     *
     * 功能说明：
     * 基于结果的恢复方法，检查返回值是否满足条件。
     * 如果结果不理想，则通过 resultHandler 进行修正。
     *
     * 示例：
     * <pre>
     * // 获取配置，如果为 null 则返回默认配置
     * Supplier&lt;Config&gt; getConfig = () -&gt; cache.get("config");
     *
     * Supplier&lt;Config&gt; safeGet = SupplierUtils.recover(
     *     getConfig,
     *     config -&gt; config == null,  // 判断条件
     *     config -&gt; Config.defaultConfig() // 恢复操作
     * );
     * </pre>
     *
     * @param <T>             Supplier 的返回类型
     * @param supplier        要执行的 Supplier
     * @param resultPredicate 结果检查条件
     * @param resultHandler   结果恢复函数
     * @return 带有结果恢复能力的 Supplier
     */
    public static <T> Supplier<T> recover(Supplier<T> supplier,
        Predicate<T> resultPredicate, UnaryOperator<T> resultHandler) {
        return () -> {
            // 执行 supplier 获取结果
            T result = supplier.get();
            // 检查结果是否需要恢复
            if(resultPredicate.test(result)){
                // 需要恢复：使用 resultHandler 修正结果
                return resultHandler.apply(result);
            }
            // 不需要恢复：直接返回原始结果
            return result;
        };
    }

    /**
     * 异常恢复：执行 Supplier，如果抛出异常则使用 exceptionHandler 恢复
     *
     * 功能说明：
     * 最基本的异常恢复方法，捕获所有异常并通过 exceptionHandler 提供降级值。
     *
     * 示例：
     * <pre>
     * // 从缓存获取数据，失败时返回空对象
     * Supplier&lt;Data&gt; getData = () -&gt; cache.get("key");
     *
     * Supplier&lt;Data&gt; safeGet = SupplierUtils.recover(
     *     getData,
     *     error -&gt; Data.empty() // 返回空对象
     * );
     * </pre>
     *
     * @param <T>              Supplier 的返回类型
     * @param supplier         要执行的 Supplier
     * @param exceptionHandler 异常处理函数
     * @return 带有异常恢复能力的 Supplier
     */
    public static <T> Supplier<T> recover(Supplier<T> supplier,
        Function<Throwable, T> exceptionHandler) {
        return () -> {
            try {
                // 尝试执行原始 supplier
                return supplier.get();
            } catch (Exception exception) {
                // 捕获异常，使用 exceptionHandler 提供降级值
                return exceptionHandler.apply(exception);
            }
        };
    }

    /**
     * 选择性异常恢复：仅恢复指定类型列表中的异常，其他异常继续传播
     *
     * 功能说明：
     * 选择性的异常恢复方法，只处理特定类型的异常。
     *
     * 示例：
     * <pre>
     * // 加载配置，只恢复特定异常
     * Supplier&lt;Config&gt; loadConfig = () -&gt; configLoader.load();
     *
     * List&lt;Class&lt;? extends Throwable&gt;&gt; recoverableExceptions = Arrays.asList(
     *     IOException.class,
     *     TimeoutException.class
     * );
     *
     * Supplier&lt;Config&gt; safeLoad = SupplierUtils.recover(
     *     loadConfig,
     *     recoverableExceptions,
     *     error -&gt; Config.defaultConfig()
     * );
     * </pre>
     *
     * @param <T>              Supplier 的返回类型
     * @param supplier         要执行的 Supplier
     * @param exceptionTypes   需要恢复的异常类型列表
     * @param exceptionHandler 异常处理函数
     * @return 带有选择性异常恢复能力的 Supplier
     */
    public static <T> Supplier<T> recover(Supplier<T> supplier,
        List<Class<? extends Throwable>> exceptionTypes,
        Function<Throwable, T> exceptionHandler) {
        return () -> {
            try {
                // 尝试执行原始 supplier
                return supplier.get();
            } catch (Exception exception) {
                // 检查异常类型是否在恢复列表中
                if(exceptionTypes.stream().anyMatch(exceptionType -> exceptionType.isAssignableFrom(exception.getClass()))){
                    // 匹配成功：使用 exceptionHandler 恢复
                    return exceptionHandler.apply(exception);
                }else{
                    // 不匹配：重新抛出异常
                    throw exception;
                }
            }
        };
    }

    /**
     * 单一类型异常恢复：仅恢复指定单一类型的异常，其他异常继续传播
     *
     * 功能说明：
     * 最精确的异常恢复方法，只处理一种特定类型的异常。
     *
     * 注意：
     * 这个方法只捕获 RuntimeException，因为 Supplier.get() 不能抛出检查异常。
     *
     * 示例：
     * <pre>
     * // 获取系统属性，只恢复 NullPointerException
     * Supplier&lt;String&gt; getProperty = () -&gt; System.getProperty("key");
     *
     * Supplier&lt;String&gt; safeGet = SupplierUtils.recover(
     *     getProperty,
     *     NullPointerException.class,
     *     error -&gt; "default" // 返回默认值
     * );
     * </pre>
     *
     * @param <X>              要恢复的异常类型
     * @param <T>              Supplier 的返回类型
     * @param supplier         要执行的 Supplier
     * @param exceptionType    需要恢复的异常类型
     * @param exceptionHandler 异常处理函数
     * @return 带有单一类型异常恢复能力的 Supplier
     */
    public static <X extends Throwable, T> Supplier<T> recover(Supplier<T> supplier,
        Class<X> exceptionType,
        Function<Throwable, T> exceptionHandler) {
        return () -> {
            try {
                // 尝试执行原始 supplier
                return supplier.get();
            } catch (RuntimeException exception) {
                // 检查异常类型是否匹配
                if(exceptionType.isAssignableFrom(exception.getClass())) {
                    // 匹配成功：使用 exceptionHandler 恢复
                    return exceptionHandler.apply(exception);
                }else{
                    // 不匹配：重新抛出异常
                    throw exception;
                }
            }
        };
    }

    /**
     * 组合函数：执行 Supplier，根据成功或失败分别应用不同的处理函数
     *
     * 功能说明：
     * 为成功和失败情况提供两个独立的处理函数。
     *
     * 示例：
     * <pre>
     * // 加载配置，成功返回配置信息，失败返回错误信息
     * Supplier&lt;Config&gt; loadConfig = () -&gt; configLoader.load();
     *
     * Function&lt;Config, String&gt; onSuccess = config -&gt;
     *     "配置加载成功: " + config.getName();
     *
     * Function&lt;Throwable, String&gt; onFailure = error -&gt;
     *     "配置加载失败: " + error.getMessage();
     *
     * Supplier&lt;String&gt; safeLoad = SupplierUtils.andThen(
     *     loadConfig,
     *     onSuccess,
     *     onFailure
     * );
     * </pre>
     *
     * @param <T>              Supplier 的返回类型
     * @param <R>              处理函数的返回类型（最终返回类型）
     * @param supplier         要执行的 Supplier
     * @param resultHandler    成功时的处理函数
     * @param exceptionHandler 失败时的处理函数
     * @return 组合后的 Supplier，不会抛出异常
     */
    public static <T, R> Supplier<R> andThen(Supplier<T> supplier, Function<T, R> resultHandler,
        Function<Throwable, R> exceptionHandler) {
        return () -> {
            try {
                // 尝试执行 supplier
                T result = supplier.get();
                // 成功：使用 resultHandler 处理结果
                return resultHandler.apply(result);
            } catch (Exception exception) {
                // 失败：使用 exceptionHandler 处理异常
                return exceptionHandler.apply(exception);
            }
        };
    }
}
