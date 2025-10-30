package io.github.resilience4j.core;


import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * Function 工具类 - 函数式编程辅助工具
 *
 * 作用说明：
 * 提供 Function 的组合、异常恢复等实用方法。
 * 简化函数式编程中的常见操作，增强代码的可读性和健壮性。
 *
 * 主要功能：
 * 1. andThen：函数组合，将多个函数串联执行
 * 2. recover：异常恢复，捕获异常并提供默认值或替代处理
 *
 * 使用场景：
 * - 链式处理：将多个转换步骤组合成一个函数
 * - 容错处理：为函数调用添加异常恢复逻辑
 * - 降级处理：当函数失败时提供降级结果
 *
 * 示例：
 * <pre>
 * // 函数组合
 * Function&lt;String, Integer&gt; parser = Integer::parseInt;
 * Function&lt;Integer, String&gt; formatter = n -&gt; "值是: " + n;
 * Function&lt;String, String&gt; combined = FunctionUtils.andThen(parser, formatter);
 *
 * // 异常恢复
 * Function&lt;String, Integer&gt; safeParser = FunctionUtils.recover(
 *     Integer::parseInt,
 *     ex -&gt; 0  // 解析失败返回 0
 * );
 * </pre>
 *
 * @author Resilience4j团队
 * @since 0.1.0
 */
public class FunctionUtils {

    /**
     * 私有构造函数，防止实例化
     * 这是一个工具类，只包含静态方法
     */
    private FunctionUtils() {
    }

    /**
     * 组合函数：先执行 Function，再对结果应用转换函数
     *
     * 功能说明：
     * 这是函数组合的基础方法，将两个 Function 串联起来。
     * 第一个 Function 的输出作为第二个 Function 的输入。
     *
     * 执行流程：
     * 1. 调用 function.apply(t) 获取中间结果
     * 2. 将中间结果传递给 resultHandler.apply() 进行转换
     * 3. 返回最终结果
     *
     * 使用场景：
     * - 管道处理：将多个转换步骤串联成一个函数
     * - 数据转换链：User -&gt; UserDTO -&gt; UserVO
     * - 业务流程：订单 -&gt; 验证结果 -&gt; 处理结果
     *
     * 示例：
     * <pre>
     * // 字符串转换流程：去空格 -&gt; 转大写
     * Function&lt;String, String&gt; trim = String::trim;
     * Function&lt;String, String&gt; toUpper = String::toUpperCase;
     *
     * Function&lt;String, String&gt; process = FunctionUtils.andThen(trim, toUpper);
     * String result = process.apply("  hello  "); // 返回: "HELLO"
     * </pre>
     *
     * 与 Function.andThen() 的关系：
     * - 这是 Function.andThen() 的静态工具方法版本
     * - 更适合函数式编程风格和方法引用
     *
     * @param <T>           输入参数类型
     * @param <U>           中间结果类型
     * @param <R>           最终返回类型
     * @param function      第一个函数
     * @param resultHandler 第二个函数，处理第一个函数的结果
     * @return 组合后的函数
     */
    public static <T, U, R> Function<T, R> andThen(Function<T, U> function, Function<U, R> resultHandler) {
        return t -> resultHandler.apply(function.apply(t));
    }

    /**
     * 组合函数：执行 Function，并用统一的 BiFunction 处理成功和失败两种情况
     *
     * 功能说明：
     * 使用 BiFunction 同时处理成功结果和异常情况。
     * handler 接收两个参数：结果值和异常，其中一个为 null。
     *
     * 执行流程：
     * 1. 尝试调用 function.apply(t)
     * 2. 如果成功：调用 handler.apply(result, null)
     * 3. 如果失败：调用 handler.apply(null, exception)
     * 4. 返回 handler 的处理结果
     *
     * 使用场景：
     * - 统一结果处理：用一个函数处理成功和失败
     * - Either 模式：将结果封装为统一类型
     * - 日志记录：统一记录成功和失败信息
     *
     * 示例：
     * <pre>
     * // 解析字符串为整数，统一处理成功和失败
     * Function&lt;String, Integer&gt; parse = Integer::parseInt;
     *
     * BiFunction&lt;Integer, Throwable, String&gt; handler = (result, error) -&gt; {
     *     if (error != null) {
     *         return "解析失败: " + error.getMessage();
     *     } else {
     *         return "解析成功: " + result;
     *     }
     * };
     *
     * Function&lt;String, String&gt; safeParse = FunctionUtils.andThen(parse, handler);
     * safeParse.apply("123");  // 返回: "解析成功: 123"
     * safeParse.apply("abc");  // 返回: "解析失败: ..."
     * </pre>
     *
     * @param <T>      输入参数类型
     * @param <U>      中间结果类型
     * @param <R>      最终返回类型
     * @param function 要执行的函数
     * @param handler  处理结果和异常的双参数函数
     * @return 组合后的函数，不会抛出异常
     */
    public static <T, U, R> Function<T, R> andThen(Function<T, U> function,
        BiFunction<U, Throwable, R> handler) {
        return t -> {
            try {
                // 尝试执行 function
                U result = function.apply(t);
                // 成功：将结果传递给 handler，异常参数为 null
                return handler.apply(result, null);
            } catch (Exception exception) {
                // 失败：将异常传递给 handler，结果参数为 null
                return handler.apply(null, exception);
            }
        };
    }

    /**
     * 组合函数：执行 Function，根据成功或失败分别应用不同的处理函数
     *
     * 功能说明：
     * 为成功和失败情况提供两个独立的处理函数。
     * 根据 function 的执行结果，选择性地调用相应的处理函数。
     *
     * 执行流程：
     * 1. 尝试调用 function.apply(t)
     * 2. 如果成功：调用 resultHandler.apply(result)
     * 3. 如果失败：调用 exceptionHandler.apply(exception)
     * 4. 返回相应处理函数的结果
     *
     * 使用场景：
     * - 分支处理：成功和失败需要不同的处理逻辑
     * - 错误恢复：失败时提供降级值
     * - 类型转换：将结果和异常转换为统一类型
     *
     * 示例：
     * <pre>
     * // 计算折扣，成功返回价格，失败返回原价
     * Function&lt;Product, Double&gt; calculateDiscount = product -&gt;
     *     product.getPrice() * discountService.getRate(product.getId());
     *
     * Function&lt;Double, String&gt; onSuccess = price -&gt;
     *     String.format("折后价: %.2f", price);
     *
     * Function&lt;Throwable, String&gt; onFailure = error -&gt;
     *     "折扣计算失败，按原价销售";
     *
     * Function&lt;Product, String&gt; safeCalculate = FunctionUtils.andThen(
     *     calculateDiscount,
     *     onSuccess,
     *     onFailure
     * );
     * </pre>
     *
     * @param <T>              输入参数类型
     * @param <U>              中间结果类型
     * @param <R>              最终返回类型
     * @param function         要执行的函数
     * @param resultHandler    成功时的处理函数
     * @param exceptionHandler 失败时的处理函数
     * @return 组合后的函数，不会抛出异常
     */
    public static <T, U, R> Function<T, R> andThen(Function<T, U> function, Function<U, R> resultHandler,
        Function<Throwable, R> exceptionHandler) {
        return t -> {
            try {
                // 尝试执行 function
                U result = function.apply(t);
                // 成功：使用 resultHandler 处理结果
                return resultHandler.apply(result);
            } catch (Exception exception) {
                // 失败：使用 exceptionHandler 处理异常
                return exceptionHandler.apply(exception);
            }
        };
    }

    /**
     * 异常恢复：执行 Function，如果抛出异常则使用 exceptionHandler 恢复
     *
     * 功能说明：
     * 这是最基本的异常恢复方法，捕获所有异常并通过 exceptionHandler 提供降级值。
     * 保持返回类型不变（都是 R），使调用方无感知地处理失败情况。
     *
     * 执行流程：
     * 1. 尝试调用 function.apply(t)
     * 2. 如果成功：直接返回结果
     * 3. 如果失败：调用 exceptionHandler.apply(exception) 获取降级值
     * 4. 返回结果或降级值
     *
     * 使用场景：
     * - 降级处理：当主逻辑失败时返回默认值
     * - 容错处理：避免异常导致程序中断
     * - 数据转换：将不安全的转换变为安全的
     *
     * 示例：
     * <pre>
     * // 解析字符串为整数，失败时返回 0
     * Function&lt;String, Integer&gt; parse = Integer::parseInt;
     *
     * Function&lt;String, Integer&gt; safeParse = FunctionUtils.recover(
     *     parse,
     *     error -&gt; {
     *         logger.warn("解析失败", error);
     *         return 0; // 返回默认值
     *     }
     * );
     *
     * safeParse.apply("123");  // 返回: 123
     * safeParse.apply("abc");  // 返回: 0，不抛异常
     * </pre>
     *
     * @param <T>              输入参数类型
     * @param <R>              返回类型
     * @param function         要执行的函数
     * @param exceptionHandler 异常处理函数
     * @return 带有异常恢复能力的函数
     */
    public static <T, R> Function<T, R> recover(Function<T, R> function,
        Function<Throwable, R> exceptionHandler) {
        return t -> {
            try {
                // 尝试执行原始 function
                return function.apply(t);
            } catch (Exception exception) {
                // 捕获异常，使用 exceptionHandler 提供降级值
                return exceptionHandler.apply(exception);
            }
        };
    }

    /**
     * 结果恢复：执行 Function，如果结果满足特定条件则进行恢复处理
     *
     * 功能说明：
     * 基于结果的恢复方法，检查返回值是否满足条件。
     * 如果结果不理想，则通过 resultHandler 进行修正。
     *
     * 执行流程：
     * 1. 调用 function.apply(t) 获取结果
     * 2. 使用 resultPredicate.test(result) 检查结果
     * 3. 如果需要恢复：调用 resultHandler.apply(result)
     * 4. 否则直接返回原始结果
     *
     * 使用场景：
     * - null 值处理：当返回 null 时替换为默认值
     * - 无效值修正：当返回值不符合规则时修正
     * - 边界值处理：处理超出范围的值
     *
     * 示例：
     * <pre>
     * // 除法运算，结果为 null 时返回 0
     * Function&lt;Integer, Integer&gt; divide = x -&gt; x / 2;
     *
     * Function&lt;Integer, Integer&gt; safeDiv = FunctionUtils.recover(
     *     divide,
     *     result -&gt; result == null,  // 判断条件
     *     result -&gt; 0                 // 恢复操作
     * );
     * </pre>
     *
     * @param <T>             输入参数类型
     * @param <R>             返回类型
     * @param function        要执行的函数
     * @param resultPredicate 结果检查条件
     * @param resultHandler   结果恢复函数
     * @return 带有结果恢复能力的函数
     */
    public static <T, R> Function<T, R> recover(Function<T, R> function,
        Predicate<R> resultPredicate, UnaryOperator<R> resultHandler) {
        return t -> {
            // 执行 function 获取结果
            R result = function.apply(t);
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
     * 选择性的异常恢复方法，只处理特定类型的异常。
     * 如果异常类型在列表中，则进行恢复；否则重新抛出。
     *
     * 执行流程：
     * 1. 尝试调用 function.apply(t)
     * 2. 如果成功：直接返回结果
     * 3. 如果抛出异常：检查异常类型是否在列表中
     * 4. 如果在列表中：调用 exceptionHandler 恢复
     * 5. 如果不在列表中：重新抛出异常
     *
     * 使用场景：
     * - 业务异常恢复：只恢复业务异常
     * - 可预期异常处理：只处理已知异常
     * - 分层异常处理：不同层级不同策略
     *
     * 示例：
     * <pre>
     * // 数据转换，只恢复特定异常
     * Function&lt;String, Data&gt; parse = jsonParser::parse;
     *
     * List&lt;Class&lt;? extends Throwable&gt;&gt; recoverableExceptions = Arrays.asList(
     *     JsonParseException.class,
     *     IOException.class
     * );
     *
     * Function&lt;String, Data&gt; safeParse = FunctionUtils.recover(
     *     parse,
     *     recoverableExceptions,
     *     error -&gt; Data.empty() // 返回空对象
     * );
     * </pre>
     *
     * @param <T>              输入参数类型
     * @param <R>              返回类型
     * @param function         要执行的函数
     * @param exceptionTypes   需要恢复的异常类型列表
     * @param exceptionHandler 异常处理函数
     * @return 带有选择性异常恢复能力的函数
     */
    public static <T, R> Function<T, R> recover(Function<T, R> function,
        List<Class<? extends Throwable>> exceptionTypes,
        Function<Throwable, R> exceptionHandler) {
        return t -> {
            try {
                // 尝试执行原始 function
                return function.apply(t);
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
     * 如果异常是指定类型（或其子类），则进行恢复；否则重新抛出。
     *
     * 执行流程：
     * 1. 尝试调用 function.apply(t)
     * 2. 如果成功：直接返回结果
     * 3. 如果抛出异常：检查异常类型是否匹配
     * 4. 如果匹配：调用 exceptionHandler 恢复
     * 5. 如果不匹配：重新抛出异常
     *
     * 使用场景：
     * - 精确异常处理：只处理特定的单一异常
     * - 性能优化：相比列表匹配更高效
     * - 明确的异常契约：声明要处理的异常类型
     *
     * 示例：
     * <pre>
     * // 字符串转整数，只恢复 NumberFormatException
     * Function&lt;String, Integer&gt; parse = Integer::parseInt;
     *
     * Function&lt;String, Integer&gt; safeParse = FunctionUtils.recover(
     *     parse,
     *     NumberFormatException.class,
     *     error -&gt; 0 // 解析失败返回 0
     * );
     *
     * safeParse.apply("123");  // 返回: 123
     * safeParse.apply("abc");  // 返回: 0，不抛异常
     * </pre>
     *
     * 性能对比：
     * - 单一类型：O(1) 类型检查，最快
     * - 多种类型：O(n) 遍历列表，较慢
     *
     * @param <X>              要恢复的异常类型
     * @param <T>              输入参数类型
     * @param <R>              返回类型
     * @param function         要执行的函数
     * @param exceptionType    需要恢复的异常类型
     * @param exceptionHandler 异常处理函数
     * @return 带有单一类型异常恢复能力的函数
     */
    public static <X extends Throwable, T, R> Function<T, R> recover(Function<T, R> function,
        Class<X> exceptionType,
        Function<Throwable, R> exceptionHandler) {
        return t -> {
            try {
                // 尝试执行原始 function
                return function.apply(t);
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
