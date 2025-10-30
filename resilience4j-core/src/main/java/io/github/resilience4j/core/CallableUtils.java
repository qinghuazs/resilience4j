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
import java.util.concurrent.Callable;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * Callable 工具类 - 可抛出检查异常的函数式编程辅助工具
 *
 * 作用说明：
 * 提供 Callable 的组合、异常恢复等实用方法。
 * Callable 是 Java 中可以抛出检查异常的函数式接口，常用于线程池和并发场景。
 *
 * 与 SupplierUtils 的区别：
 * - Callable.call() 可以抛出检查异常（checked exception）
 * - Supplier.get() 只能抛出运行时异常（runtime exception）
 * - Callable 更适合需要异常处理的场景
 *
 * 主要功能：
 * 1. andThen：将 Callable 与后续处理函数组合
 * 2. recover：为 Callable 添加异常恢复逻辑
 *
 * 使用场景：
 * - ExecutorService.submit(callable)：线程池任务提交
 * - 需要抛出检查异常的异步任务
 * - 文件 I/O、网络请求等可能抛出异常的操作
 *
 * @author Robert Winkler
 * @since 1.0.0
 */
public class CallableUtils {

    /** 私有构造函数，防止实例化 */
    private CallableUtils() {
    }

    /**
     * 组合函数：先执行 Callable，再对结果应用转换函数
     *
     * 功能说明：
     * 这是一个函数组合方法，将 Callable 的执行结果传递给 resultHandler 进行后续处理。
     * 类似于函数式编程中的 map 或 andThen 操作。
     *
     * 执行流程：
     * 1. 调用 callable.call() 获取结果
     * 2. 将结果传递给 resultHandler.apply() 进行转换
     * 3. 返回转换后的结果
     *
     * 使用场景：
     * - 结果转换：将 Callable 的返回值转换为另一种类型
     * - 链式处理：将多个处理步骤组合成一个 Callable
     * - 数据映射：将原始数据映射为业务对象
     *
     * 示例：
     * <pre>
     * // 从数据库查询用户 ID，然后转换为用户名
     * Callable&lt;Integer&gt; fetchUserId = () -&gt; database.getUserId();
     * Function&lt;Integer, String&gt; toUserName = id -&gt; "用户_" + id;
     *
     * Callable&lt;String&gt; getUserName = CallableUtils.andThen(fetchUserId, toUserName);
     * String userName = getUserName.call(); // 返回: "用户_123"
     * </pre>
     *
     * 注意事项：
     * - 如果 callable.call() 抛出异常，异常会直接传播，不会被处理
     * - resultHandler 不应该为 null，否则会抛出 NullPointerException
     * - 如果需要异常处理，请使用带 exceptionHandler 参数的重载方法
     *
     * @param <T>           callable 的返回类型
     * @param <R>           resultHandler 的返回类型（最终返回类型）
     * @param callable      要执行的 Callable
     * @param resultHandler 用于处理 callable 结果的转换函数
     * @return 组合后的 Callable，返回类型为 R
     */
    public static <T, R> Callable<R> andThen(Callable<T> callable, Function<T, R> resultHandler) {
        return () -> resultHandler.apply(callable.call());
    }

    /**
     * 组合函数：执行 Callable，并用统一的 BiFunction 处理成功和失败两种情况
     *
     * 功能说明：
     * 这是一个更强大的组合方法，使用 BiFunction 同时处理成功和失败两种情况。
     * handler 会接收两个参数：结果值和异常，其中一个为 null。
     *
     * 执行流程：
     * 1. 尝试调用 callable.call()
     * 2. 如果成功：调用 handler.apply(result, null)
     * 3. 如果失败：调用 handler.apply(null, exception)
     * 4. 返回 handler 的处理结果
     *
     * 使用场景：
     * - 统一结果处理：用一个函数同时处理成功和失败的情况
     * - 降级策略：根据结果或异常决定返回值
     * - 日志记录：统一记录成功和失败的信息
     * - Either 模式：将结果和错误封装为统一的返回类型
     *
     * 示例：
     * <pre>
     * // 调用远程服务，统一处理成功和失败
     * Callable&lt;String&gt; remoteCall = () -&gt; httpClient.get("/api/data");
     *
     * BiFunction&lt;String, Throwable, Response&gt; handler = (result, error) -&gt; {
     *     if (error != null) {
     *         // 失败情况：返回错误响应
     *         return Response.error("服务不可用: " + error.getMessage());
     *     } else {
     *         // 成功情况：返回正常响应
     *         return Response.success(result);
     *     }
     * };
     *
     * Callable&lt;Response&gt; safeCall = CallableUtils.andThen(remoteCall, handler);
     * Response response = safeCall.call(); // 永远不会抛出异常
     * </pre>
     *
     * 注意事项：
     * - handler 必须能够处理 null 值（result 或 exception 其中一个为 null）
     * - 这个方法会捕获所有 Exception，不会向外传播异常
     * - 如果需要让某些异常继续传播，请使用其他重载方法
     * - handler 本身抛出的异常不会被捕获
     *
     * 与其他重载的区别：
     * - 相比第一个 andThen：增加了异常处理能力
     * - 相比第三个 andThen：使用单一函数处理，而不是两个独立函数
     *
     * @param <T>      callable 的返回类型
     * @param <R>      handler 的返回类型（最终返回类型）
     * @param callable 要执行的 Callable
     * @param handler  处理结果和异常的双参数函数
     * @return 组合后的 Callable，返回类型为 R
     */
    public static <T, R> Callable<R> andThen(Callable<T> callable,
        BiFunction<T, Throwable, R> handler) {
        return () -> {
            try {
                // 尝试执行 callable
                T result = callable.call();
                // 成功：将结果传递给 handler，异常参数为 null
                return handler.apply(result, null);
            } catch (Exception exception) {
                // 失败：将异常传递给 handler，结果参数为 null
                return handler.apply(null, exception);
            }
        };
    }

    /**
     * 组合函数：执行 Callable，根据成功或失败分别应用不同的处理函数
     *
     * 功能说明：
     * 这是最灵活的 andThen 重载，为成功和失败情况提供两个独立的处理函数。
     * 根据 callable 的执行结果，选择性地调用 resultHandler 或 exceptionHandler。
     *
     * 执行流程：
     * 1. 尝试调用 callable.call()
     * 2. 如果成功：调用 resultHandler.apply(result)
     * 3. 如果失败：调用 exceptionHandler.apply(exception)
     * 4. 返回相应处理函数的结果
     *
     * 使用场景：
     * - 分支处理：成功和失败需要完全不同的处理逻辑
     * - 错误恢复：失败时提供降级值或备用方案
     * - 类型转换：将结果和异常都转换为统一的返回类型
     * - 业务流程：成功走正常流程，失败走补偿流程
     *
     * 示例：
     * <pre>
     * // 调用支付接口，成功返回订单号，失败返回错误码
     * Callable&lt;PaymentResult&gt; payment = () -&gt; paymentService.pay(order);
     *
     * Function&lt;PaymentResult, String&gt; onSuccess = result -&gt;
     *     "支付成功，订单号: " + result.getOrderId();
     *
     * Function&lt;Throwable, String&gt; onFailure = error -&gt;
     *     "支付失败，错误: " + error.getMessage();
     *
     * Callable&lt;String&gt; safePayment = CallableUtils.andThen(
     *     payment,
     *     onSuccess,
     *     onFailure
     * );
     *
     * String message = safePayment.call(); // 总是返回消息，不抛异常
     * </pre>
     *
     * 注意事项：
     * - resultHandler 和 exceptionHandler 必须返回相同类型 R
     * - 这个方法会捕获所有 Exception，不会向外传播异常
     * - 处理函数本身抛出的异常不会被捕获
     * - 两个处理函数都不应该为 null
     *
     * 与其他重载的区别：
     * - 相比第一个 andThen：增加了异常处理能力
     * - 相比第二个 andThen：使用两个独立函数，逻辑更清晰
     *
     * 设计模式：
     * 这是 Either 模式的一种实现，将成功和失败两条路径统一为一个返回类型。
     *
     * @param <T>              callable 的返回类型
     * @param <R>              两个处理函数的返回类型（最终返回类型）
     * @param callable         要执行的 Callable
     * @param resultHandler    成功时的处理函数
     * @param exceptionHandler 失败时的处理函数
     * @return 组合后的 Callable，返回类型为 R
     */
    public static <T, R> Callable<R> andThen(Callable<T> callable, Function<T, R> resultHandler,
        Function<Throwable, R> exceptionHandler) {
        return () -> {
            try {
                // 尝试执行 callable
                T result = callable.call();
                // 成功：使用 resultHandler 处理结果
                return resultHandler.apply(result);
            } catch (Exception exception) {
                // 失败：使用 exceptionHandler 处理异常
                return exceptionHandler.apply(exception);
            }
        };
    }

    /**
     * 异常恢复：执行 Callable，如果抛出异常则使用 exceptionHandler 恢复
     *
     * 功能说明：
     * 这是最基本的异常恢复方法，捕获所有异常并通过 exceptionHandler 提供降级值。
     * 保持返回类型不变（都是 T），使调用方无感知地处理失败情况。
     *
     * 执行流程：
     * 1. 尝试调用 callable.call()
     * 2. 如果成功：直接返回结果
     * 3. 如果失败：调用 exceptionHandler.apply(exception) 获取降级值
     * 4. 返回结果或降级值
     *
     * 使用场景：
     * - 降级处理：当主逻辑失败时返回默认值或备用值
     * - 容错处理：避免异常导致程序中断
     * - 静默失败：将异常转换为正常返回值
     * - 重试后的兜底：重试失败后的最终降级方案
     *
     * 示例：
     * <pre>
     * // 从缓存读取数据，失败时返回空对象
     * Callable&lt;UserData&gt; fetchFromCache = () -&gt; cache.get("user:123");
     *
     * Callable&lt;UserData&gt; safeGet = CallableUtils.recover(
     *     fetchFromCache,
     *     error -&gt; {
     *         logger.warn("缓存读取失败", error);
     *         return UserData.empty(); // 返回空对象
     *     }
     * );
     *
     * UserData data = safeGet.call(); // 永远不会抛出异常
     * </pre>
     *
     * 与 andThen 的区别：
     * - recover：保持相同返回类型，专注于异常恢复
     * - andThen：可以改变返回类型，专注于结果转换
     *
     * 注意事项：
     * - 会捕获所有 Exception（包括 RuntimeException 和 checked exception）
     * - exceptionHandler 返回的值类型必须与 callable 相同
     * - exceptionHandler 本身抛出的异常不会被捕获
     * - 如果只想恢复特定异常，请使用带异常类型参数的重载方法
     *
     * 典型的降级策略：
     * - 返回默认值：() -&gt; DEFAULT_VALUE
     * - 返回空对象：() -&gt; Collections.emptyList()
     * - 返回缓存值：() -&gt; cachedValue
     * - 记录日志后返回：error -&gt; { log(error); return fallback; }
     *
     * @param <T>              callable 的返回类型（也是 exceptionHandler 的返回类型）
     * @param callable         要执行的 Callable
     * @param exceptionHandler 异常处理函数，将异常转换为降级值
     * @return 带有异常恢复能力的 Callable
     */
    public static <T> Callable<T> recover(Callable<T> callable,
        Function<Throwable, T> exceptionHandler) {
        return () -> {
            try {
                // 尝试执行原始 callable
                return callable.call();
            } catch (Exception exception) {
                // 捕获异常，使用 exceptionHandler 提供降级值
                return exceptionHandler.apply(exception);
            }
        };
    }

    /**
     * 结果恢复：执行 Callable，如果结果满足特定条件则进行恢复处理
     *
     * 功能说明：
     * 这是一个基于结果的恢复方法，不处理异常，而是检查返回值是否满足条件。
     * 如果结果不理想（满足 resultPredicate），则通过 resultHandler 进行修正。
     *
     * 执行流程：
     * 1. 调用 callable.call() 获取结果
     * 2. 使用 resultPredicate.test(result) 检查结果是否需要恢复
     * 3. 如果需要恢复：调用 resultHandler.apply(result) 获取修正后的值
     * 4. 如果不需要恢复：直接返回原始结果
     *
     * 使用场景：
     * - null 值处理：当返回 null 时替换为默认值
     * - 无效值修正：当返回值不符合业务规则时进行修正
     * - 空集合处理：当返回空列表时替换为带默认元素的列表
     * - 降级处理：当返回值表示失败时切换到降级逻辑
     *
     * 示例：
     * <pre>
     * // 查询用户，如果返回 null 则返回匿名用户
     * Callable&lt;User&gt; queryUser = () -&gt; database.findUser(userId);
     *
     * Callable&lt;User&gt; safeQuery = CallableUtils.recover(
     *     queryUser,
     *     user -&gt; user == null,           // 判断条件：是否为 null
     *     user -&gt; User.anonymous()         // 恢复操作：返回匿名用户
     * );
     *
     * User user = safeQuery.call(); // 永远不会返回 null
     * </pre>
     *
     * 更多示例：
     * <pre>
     * // 查询商品列表，如果为空则返回推荐商品
     * Callable&lt;List&lt;Product&gt;&gt; queryProducts = () -&gt; database.findProducts(category);
     *
     * Callable&lt;List&lt;Product&gt;&gt; withDefault = CallableUtils.recover(
     *     queryProducts,
     *     list -&gt; list.isEmpty(),          // 判断条件：列表为空
     *     list -&gt; getRecommendedProducts() // 恢复操作：返回推荐列表
     * );
     * </pre>
     *
     * 与异常恢复的区别：
     * - 异常恢复：处理抛出的异常（try-catch）
     * - 结果恢复：处理不理想的返回值（条件判断）
     *
     * 注意事项：
     * - 这个方法不会捕获异常，异常会正常传播
     * - resultPredicate 返回 true 表示需要恢复，false 表示结果正常
     * - resultHandler 接收原始结果，可以基于原始值进行修正
     * - resultPredicate 和 resultHandler 都不应该为 null
     *
     * 常见的恢复条件：
     * - null 检查：result -&gt; result == null
     * - 空集合检查：result -&gt; result.isEmpty()
     * - 错误码检查：result -&gt; result.getCode() != 0
     * - 业务规则：result -&gt; result.getAmount() &lt; 0
     *
     * 设计模式：
     * 这是责任链模式的一种应用，在结果不满足条件时进行后处理。
     *
     * @param <T>             callable 的返回类型
     * @param callable        要执行的 Callable
     * @param resultPredicate 结果检查条件，返回 true 表示需要恢复
     * @param resultHandler   结果恢复函数，将不理想的结果转换为理想的结果
     * @return 带有结果恢复能力的 Callable
     */
    public static <T> Callable<T> recover(Callable<T> callable,
        Predicate<T> resultPredicate, UnaryOperator<T> resultHandler) {
        return () -> {
            // 执行 callable 获取结果
            T result = callable.call();
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
     * 选择性异常恢复：仅恢复指定类型列表中的异常，其他异常继续传播
     *
     * 功能说明：
     * 这是一个选择性的异常恢复方法，只处理特定类型的异常。
     * 如果异常类型在 exceptionTypes 列表中，则进行恢复；否则重新抛出异常。
     *
     * 执行流程：
     * 1. 尝试调用 callable.call()
     * 2. 如果成功：直接返回结果
     * 3. 如果抛出异常：检查异常类型是否在 exceptionTypes 列表中
     * 4. 如果在列表中：调用 exceptionHandler 进行恢复
     * 5. 如果不在列表中：重新抛出异常
     *
     * 使用场景：
     * - 业务异常恢复：只恢复业务异常，系统异常继续抛出
     * - 可预期异常处理：只处理已知的、可恢复的异常
     * - 分层异常处理：不同层级的异常采用不同策略
     * - 外部服务调用：只恢复网络、超时等可预期异常
     *
     * 示例：
     * <pre>
     * // 调用外部 API，只恢复网络相关异常，其他异常继续抛出
     * Callable&lt;String&gt; apiCall = () -&gt; httpClient.get("/api/data");
     *
     * List&lt;Class&lt;? extends Throwable&gt;&gt; recoverableExceptions = Arrays.asList(
     *     IOException.class,           // 网络 IO 异常
     *     SocketTimeoutException.class,// 超时异常
     *     ConnectException.class       // 连接异常
     * );
     *
     * Callable&lt;String&gt; safeCall = CallableUtils.recover(
     *     apiCall,
     *     recoverableExceptions,
     *     error -&gt; {
     *         logger.warn("API 调用失败，使用缓存数据", error);
     *         return cachedData; // 降级到缓存
     *     }
     * );
     *
     * // 网络异常会被恢复，其他异常（如业务异常）会继续抛出
     * String data = safeCall.call();
     * </pre>
     *
     * 异常类型匹配规则：
     * - 使用 isAssignableFrom 进行类型检查
     * - 支持父类匹配：如果列表包含 Exception.class，则所有 Exception 子类都会被恢复
     * - 支持接口匹配：如果异常实现了列表中的接口，也会被匹配
     *
     * 常见的异常分组：
     * <pre>
     * // 网络相关异常
     * List.of(IOException.class, TimeoutException.class)
     *
     * // 资源相关异常
     * List.of(FileNotFoundException.class, AccessDeniedException.class)
     *
     * // 业务异常
     * List.of(BusinessException.class, ValidationException.class)
     * </pre>
     *
     * 注意事项：
     * - 只有匹配的异常类型才会被恢复
     * - 不匹配的异常会重新抛出，调用方需要处理
     * - exceptionTypes 列表为空会导致所有异常都重新抛出
     * - 列表中的异常类型顺序不影响匹配结果
     * - 使用 stream().anyMatch() 进行匹配，有一定性能开销
     *
     * 与其他 recover 重载的区别：
     * - recover(callable, handler)：恢复所有异常
     * - recover(callable, types, handler)：只恢复指定类型列表中的异常（本方法）
     * - recover(callable, type, handler)：只恢复单一类型的异常
     *
     * 设计模式：
     * 这是策略模式的应用，根据异常类型选择不同的处理策略。
     *
     * @param <T>              callable 的返回类型
     * @param callable         要执行的 Callable
     * @param exceptionTypes   需要恢复的异常类型列表
     * @param exceptionHandler 异常处理函数，将匹配的异常转换为降级值
     * @return 带有选择性异常恢复能力的 Callable
     */
    public static <T> Callable<T> recover(Callable<T> callable,
        List<Class<? extends Throwable>> exceptionTypes,
        Function<Throwable, T> exceptionHandler) {
        return () -> {
            try {
                // 尝试执行原始 callable
                return callable.call();
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
     * 这是最精确的异常恢复方法，只处理一种特定类型的异常。
     * 如果异常是指定类型（或其子类），则进行恢复；否则重新抛出。
     *
     * 执行流程：
     * 1. 尝试调用 callable.call()
     * 2. 如果成功：直接返回结果
     * 3. 如果抛出异常：检查异常类型是否匹配 exceptionType
     * 4. 如果匹配：调用 exceptionHandler 进行恢复
     * 5. 如果不匹配：重新抛出异常
     *
     * 使用场景：
     * - 精确异常处理：只处理特定的单一异常类型
     * - 性能优化：相比列表匹配，单一类型检查更高效
     * - 明确的异常契约：明确声明要处理的异常类型
     * - 典型异常恢复：如 IOException、TimeoutException
     *
     * 示例：
     * <pre>
     * // 读取文件，只恢复 FileNotFoundException，其他 IO 异常继续抛出
     * Callable&lt;String&gt; readFile = () -&gt; Files.readString(Path.of("/data/config.txt"));
     *
     * Callable&lt;String&gt; safeRead = CallableUtils.recover(
     *     readFile,
     *     FileNotFoundException.class,
     *     error -&gt; {
     *         logger.warn("配置文件不存在，使用默认配置");
     *         return "default-config"; // 返回默认配置
     *     }
     * );
     *
     * // FileNotFoundException 会被恢复
     * // IOException 等其他异常会继续抛出
     * String config = safeRead.call();
     * </pre>
     *
     * 更多示例：
     * <pre>
     * // 1. 处理超时异常
     * Callable&lt;Response&gt; apiCall = () -&gt; httpClient.get("/api/data");
     * Callable&lt;Response&gt; withTimeout = CallableUtils.recover(
     *     apiCall,
     *     TimeoutException.class,
     *     error -&gt; Response.timeout() // 返回超时响应
     * );
     *
     * // 2. 处理数据库连接异常
     * Callable&lt;Connection&gt; getConnection = () -&gt; dataSource.getConnection();
     * Callable&lt;Connection&gt; withFallback = CallableUtils.recover(
     *     getConnection,
     *     SQLException.class,
     *     error -&gt; backupDataSource.getConnection() // 使用备用数据源
     * );
     * </pre>
     *
     * 异常类型匹配规则：
     * - 使用 isAssignableFrom 进行类型检查
     * - 匹配指定类型及其所有子类
     * - 例如：指定 IOException.class 会匹配 FileNotFoundException、SocketException 等
     *
     * 泛型参数说明：
     * - &lt;X extends Throwable&gt;：编译时类型检查，确保 exceptionType 是 Throwable 的子类
     * - 这提供了类型安全，避免传入错误的类对象
     *
     * 注意事项：
     * - 这是性能最好的 recover 重载（相比列表版本）
     * - 只有匹配的异常类型才会被恢复
     * - 不匹配的异常会重新抛出，调用方需要处理
     * - exceptionType 不能为 null
     *
     * 与其他 recover 重载的区别：
     * - recover(callable, handler)：恢复所有异常
     * - recover(callable, types, handler)：恢复多种类型的异常
     * - recover(callable, type, handler)：只恢复单一类型的异常（本方法，最精确）
     *
     * 性能对比：
     * - 单一类型：O(1) 类型检查，最快
     * - 多种类型：O(n) 遍历列表，较慢
     * - 所有异常：无类型检查，但捕获范围最广
     *
     * 设计模式：
     * 这是策略模式和责任链模式的结合，针对特定异常类型应用恢复策略。
     *
     * @param <X>              要恢复的异常类型（必须是 Throwable 的子类）
     * @param <T>              callable 的返回类型
     * @param callable         要执行的 Callable
     * @param exceptionType    需要恢复的异常类型 Class 对象
     * @param exceptionHandler 异常处理函数，将匹配的异常转换为降级值
     * @return 带有单一类型异常恢复能力的 Callable
     */
    public static <X extends Throwable, T> Callable<T> recover(Callable<T> callable,
        Class<X> exceptionType,
        Function<Throwable, T> exceptionHandler) {
        return () -> {
            try {
                // 尝试执行原始 callable
                return callable.call();
            } catch (Exception exception) {
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
}
