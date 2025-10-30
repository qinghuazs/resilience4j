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

import io.github.resilience4j.core.functions.CheckedBiFunction;
import io.github.resilience4j.core.functions.CheckedFunction;
import io.github.resilience4j.core.functions.CheckedSupplier;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * CheckedFunction 工具类 - 支持检查异常的函数式编程辅助工具
 *
 * 作用说明：
 * 提供 CheckedFunction、CheckedSupplier 等的组合和异常恢复方法。
 * 与标准 Java 函数式接口的关键区别：可以声明抛出检查异常（checked exception）。
 *
 * CheckedFunction 系列接口说明：
 * - CheckedSupplier：类似 Supplier，但可以抛出检查异常
 * - CheckedFunction：类似 Function，但可以抛出检查异常
 * - CheckedBiFunction：类似 BiFunction，但可以抛出检查异常
 *
 * 为什么需要 CheckedFunction？
 * Java 标准函数式接口（Function、Supplier 等）不能抛出检查异常，
 * 如果要处理 IOException、SQLException 等检查异常，需要在 lambda 内部捕获，
 * 这会使代码变得冗余。CheckedFunction 解决了这个问题。
 *
 * 对比说明：
 * <pre>
 * // 标准 Supplier - ���须在内部捕获异常
 * Supplier&lt;String&gt; standardSupplier = () -&gt; {
 *     try {
 *         return Files.readString(path); // 抛出 IOException
 *     } catch (IOException e) {
 *         throw new RuntimeException(e); // 必须包装为运行时异常
 *     }
 * };
 *
 * // CheckedSupplier - 可以直接声明抛出异常
 * CheckedSupplier&lt;String&gt; checkedSupplier = () -&gt;
 *     Files.readString(path); // 直接抛出 IOException，无需try-catch
 * </pre>
 *
 * 主要功能：
 * 1. andThen：将 CheckedFunction 与后续处理函数组合
 * 2. recover：为 CheckedFunction 添加异常恢复逻辑
 *
 * 使用场景：
 * - 文件 I/O：读写文件时抛出 IOException
 * - 数据库操作：JDBC 操作抛出 SQLException
 * - 网络请求：HTTP 请求抛出各种网络异常
 * - 序列化：JSON/XML 解析抛出解析异常
 *
 * 与其他工具类的对比：
 * | 工具类                 | 函数式接口类型        | 能否抛出检查异常 | 使用场景              |
 * |-----------------------|---------------------|-----------------|---------------------|
 * | SupplierUtils         | Supplier            | 否              | 无参数，只能抛运行时异常 |
 * | FunctionUtils         | Function            | 否              | 有参数，只能抛运行时异常 |
 * | CallableUtils         | Callable            | 是              | 无参数，常用于线程池   |
 * | CheckedFunctionUtils  | CheckedFunction等   | 是              | 任意场景，支持检查异常 |
 *
 * 示例：
 * <pre>
 * // 读取文件，失败时返回默认内容
 * CheckedSupplier&lt;String&gt; readFile = () -&gt; Files.readString(path);
 *
 * CheckedSupplier&lt;String&gt; safeRead = CheckedFunctionUtils.recover(
 *     readFile,
 *     IOException.class,
 *     error -&gt; "default content" // 发生 IOException 时返回默认内容
 * );
 *
 * String content = safeRead.get(); // 可能抛出其他类型异常，但 IOException 已被处理
 * </pre>
 *
 * 注意事项：
 * - CheckedFunction 可以抛出 Throwable（包括 Exception 和 Error）
 * - recover 方法可以将 CheckedFunction 转换为永不抛出异常的函数
 * - 如果不需要处理检查异常，建议使用 FunctionUtils 等标准工具类
 *
 * @author Robert Winkler
 * @since 1.0.0
 */
public class CheckedFunctionUtils {

    /** 私有构造函数，防止实例化 */
    private CheckedFunctionUtils() {
    }


    /**
     * 异常恢复：执行 CheckedSupplier，捕获所有 Throwable 并恢复
     *
     * 功能说明：
     * 最基本的异常恢复方法，捕获所有 Throwable（包括 Exception 和 Error）。
     * 注意：这里捕获的是 Throwable，而不仅仅是 Exception。
     *
     * 执行流程：
     * 1. 尝试调用 supplier.get()
     * 2. 如果成功：直接返回结果
     * 3. 如果抛出任何 Throwable：调用 exceptionHandler 进行恢复
     *
     * 使用场景：
     * - 文件 I/O：读取文件可能抛出 IOException
     * - 数据库操作：JDBC 可能抛出 SQLException
     * - 网络请求：HTTP 请求可能抛出各种异常
     *
     * 示例：
     * <pre>
     * // 读取文件，失败时返回空字符串
     * CheckedSupplier&lt;String&gt; readFile = () -&gt;
     *     Files.readString(Path.of("config.txt"));
     *
     * CheckedSupplier&lt;String&gt; safeRead = CheckedFunctionUtils.recover(
     *     readFile,
     *     error -&gt; {
     *         logger.warn("文件读取失败", error);
     *         return ""; // 返回空字符串
     *     }
     * );
     *
     * String content = safeRead.get(); // 永远不会抛出异常
     * </pre>
     *
     * 注意事项：
     * - 捕获 Throwable 意味着连 Error 也会被捕获（如 OutOfMemoryError）
     * - exceptionHandler 本身也可能抛出检查异常
     * - 如果只想恢复特定类型异常，使用带异常类型参数的重载方法
     *
     * @param <T>              CheckedSupplier 的返回类型
     * @param supplier         要执行的 CheckedSupplier
     * @param exceptionHandler 异常处理函数，可以抛出检查异常
     * @return 带有异常恢复能力的 CheckedSupplier
     */
    public static <T> CheckedSupplier<T> recover(CheckedSupplier<T> supplier,
                                                 CheckedFunction<Throwable, T> exceptionHandler) {
        return () -> {
            try {
                // 尝试执行原始 supplier
                return supplier.get();
            } catch (Throwable throwable) {
                // 捕获所有 Throwable，使用 exceptionHandler 恢复
                return exceptionHandler.apply(throwable);
            }
        };
    }

    /**
     * 组合函数：执行 CheckedSupplier，用 CheckedBiFunction 统一处理成功和失败
     * （与 SupplierUtils.andThen 类似，但支持检查异常）
     */
    public static <T, R> CheckedSupplier<R> andThen(CheckedSupplier<T> supplier,
        CheckedBiFunction<T, Throwable, R> handler) {
        return () -> {
            try {
                return handler.apply(supplier.get(), null);
            } catch (Throwable throwable) {
                return handler.apply(null, throwable);
            }
        };
    }

    /**
     * 结果恢复：执行 CheckedSupplier，如果结果满足条件则进行恢复处理
     * （与 SupplierUtils.recover 类似，但 resultHandler 可以抛出检查异常）
     */
    public static <T> CheckedSupplier<T> recover(CheckedSupplier<T> supplier,
        Predicate<T> resultPredicate, CheckedFunction<T, T> resultHandler) {
        return () -> {
            T result = supplier.get();
            if(resultPredicate.test(result)){
                return resultHandler.apply(result);
            }
            return result;
        };
    }

    /**
     * 选择性异常恢复：仅恢复指定类型列表中的异常
     * （与 SupplierUtils.recover 类似，但 exceptionHandler 可以抛出检查异常）
     */
    public static <T> CheckedSupplier<T> recover(CheckedSupplier<T> supplier,
        List<Class<? extends Throwable>> exceptionTypes,
        CheckedFunction<Throwable, T> exceptionHandler) {
        return () -> {
            try {
                return supplier.get();
            } catch (Exception exception) {
                if(exceptionTypes.stream().anyMatch(exceptionType -> exceptionType.isAssignableFrom(exception.getClass()))){
                    return exceptionHandler.apply(exception);
                }else{
                    throw exception;
                }
            }
        };
    }

    /**
     * 单一类型异常恢复：仅恢复指定单一类型的异常
     * （与 SupplierUtils.recover 类似，但捕获 Throwable 且 exceptionHandler 可以抛出检查异常）
     */
    public static <X extends Throwable, T> CheckedSupplier<T> recover(CheckedSupplier<T> supplier,
        Class<X> exceptionType,
        CheckedFunction<Throwable, T> exceptionHandler) {
        return () -> {
            try {
                return supplier.get();
            } catch (Throwable throwable) {
                if(exceptionType.isAssignableFrom(throwable.getClass())) {
                    return exceptionHandler.apply(throwable);
                }else{
                    throw throwable;
                }
            }
        };
    }

    /**
     * 组合函数：先执行 CheckedFunction，再对结果应用转换函数
     * （与 FunctionUtils.andThen 类似，但支持检查异常）
     */
    public static <T, U, R> CheckedFunction<T, R> andThen(CheckedFunction<T, U> function, CheckedFunction<U, R> resultHandler) {
        return t -> resultHandler.apply(function.apply(t));
    }

    /**
     * 组合函数：执行 CheckedFunction，用 BiFunction 统一处理成功和失败
     * （与 FunctionUtils.andThen 类似，但输入是 CheckedFunction）
     *
     * 注意：handler 是标准 BiFunction，不能抛出检查异常
     */
    public static <T, U, R> CheckedFunction<T, R> andThen(CheckedFunction<T, U> function,
                                                   BiFunction<U, Throwable, R> handler) {
        return t -> {
            try {
                U result = function.apply(t);
                return handler.apply(result, null);
            } catch (Exception exception) {
                return handler.apply(null, exception);
            }
        };
    }

    /**
     * 组合函数：执行 CheckedFunction，根据成功或失败分别应用不同的处理函数
     * （与 FunctionUtils.andThen 类似，但支持检查异常）
     */
    public static <T, U, R> CheckedFunction<T, R> andThen(CheckedFunction<T, U> function, CheckedFunction<U, R> resultHandler,
                                                          CheckedFunction<Throwable, R> exceptionHandler) {
        return t -> {
            try {
                U result = function.apply(t);
                return resultHandler.apply(result);
            } catch (Exception exception) {
                return exceptionHandler.apply(exception);
            }
        };
    }

    /**
     * 异常恢复：执行 CheckedFunction，如果抛出异常则使用 exceptionHandler 恢复
     * （与 FunctionUtils.recover 类似，但支持检查异常）
     */
    public static <T, R> CheckedFunction<T, R> recover(CheckedFunction<T, R> function,
                                                CheckedFunction<Throwable, R> exceptionHandler) {
        return t -> {
            try {
                return function.apply(t);
            } catch (Exception exception) {
                return exceptionHandler.apply(exception);
            }
        };
    }

    /**
     * 结果恢复：执行 CheckedFunction，如果结果满足条件则进行恢复处理
     * （与 FunctionUtils.recover 类似，但输入是 CheckedFunction）
     *
     * 注意：resultHandler 是标准 UnaryOperator，不能抛出检查异常
     */
    public static <T, R> CheckedFunction<T, R> recover(CheckedFunction<T, R> function,
                                                Predicate<R> resultPredicate, UnaryOperator<R> resultHandler) {
        return t -> {
            R result = function.apply(t);
            if(resultPredicate.test(result)){
                return resultHandler.apply(result);
            }
            return result;
        };
    }

    /**
     * 选择性异常恢复：仅恢复指定类型列表中的异常
     * （与 FunctionUtils.recover 类似，但支持检查异常）
     */
    public static <T, R> CheckedFunction<T, R> recover(CheckedFunction<T, R> function,
                                                List<Class<? extends Throwable>> exceptionTypes,
                                                       CheckedFunction<Throwable, R> exceptionHandler) {
        return t -> {
            try {
                return function.apply(t);
            } catch (Exception exception) {
                if(exceptionTypes.stream().anyMatch(exceptionType -> exceptionType.isAssignableFrom(exception.getClass()))){
                    return exceptionHandler.apply(exception);
                }else{
                    throw exception;
                }
            }
        };
    }

    /**
     * 单一类型异常恢复：仅恢复指定单一类型的异常
     * （与 FunctionUtils.recover 类似，但支持检查异常）
     */
    public static <X extends Throwable, T, R> CheckedFunction<T, R> recover(CheckedFunction<T, R> function,
                                                                     Class<X> exceptionType,
                                                                     CheckedFunction<Throwable, R> exceptionHandler) {
        return t -> {
            try {
                return function.apply(t);
            } catch (Exception exception) {
                if(exceptionType.isAssignableFrom(exception.getClass())) {
                    return exceptionHandler.apply(exception);
                }else{
                    throw exception;
                }
            }
        };
    }
}
