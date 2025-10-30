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

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 类工具 - 提供通过反射创建对象实例的工具方法
 *
 * 作用说明：
 * 封装 Java 反射 API，提供类型安全的对象实例化方法。
 * 主要用于通过配置文件或注解中的 Class 对象创建实际的实例。
 *
 * 设计理念：
 * - 类型安全：使用泛型确保返回正确的类型
 * - 统一异常：将反射异常统一包装为 InstantiationException
 * - 无参构造：所有方法都要求目标类有无参构造函数
 * - 失败快速：如果无法实例化，立即抛出异常
 *
 * 使用场景：
 * - 从配置类中实例化 IntervalBiFunction
 * - 从配置类中实例化 Predicate
 * - 从配置类中实例化 Function
 * - 动态加载用户自定义的策略类
 *
 * 为什么需要这个工具类？
 * 1. 配置灵活性：用户可以在配置中指定自定义的策略类
 * 2. 插件机制：支持用户扩展 Resilience4j 的行为
 * 3. 解耦：配置和实现分离
 *
 * 示例场景：
 * <pre>
 * // 配置文件中指定自定义的 Predicate
 * {@literal @}Configuration
 * public class MyConfig {
 *     public Class&lt;? extends Predicate&lt;Response&gt;&gt; getRetryPredicate() {
 *         return CustomRetryPredicate.class; // 用户自定义类
 *     }
 * }
 *
 * // Resilience4j 内部使用 ClassUtils 创建实例
 * Class&lt;? extends Predicate&lt;Response&gt;&gt; clazz = config.getRetryPredicate();
 * Predicate&lt;Response&gt; predicate = ClassUtils.instantiatePredicateClass(clazz);
 * </pre>
 *
 * 注意事项：
 * - 所有目标类必须有公共的无参构造函数
 * - 如果类是内部类，必须是静态内部类
 * - 如果实例化失败，会抛出 InstantiationException（运行时异常）
 * - 不支持有参构造函数，如需传参请使用其他方式
 *
 * @author Robert Winkler
 * @since 1.0.0
 */
public final class ClassUtils {

    /** 实例化失败时的错误消息前缀 */
    private static final String INSTANTIATION_ERROR_PREFIX = "Unable to create instance of class: ";

    /** 私有构造函数，防止实例化 */
    private ClassUtils() {
        // 工具类，不允许实例化
    }

    /**
     * 实例化 IntervalBiFunction 类
     *
     * 通过反射创建 IntervalBiFunction 的实例。
     * 要求目标类有公共无参构造函数。
     *
     * @param <T>   IntervalBiFunction 的类型参数
     * @param clazz 要实例化的类对象
     * @return 创建的 IntervalBiFunction 实例
     * @throws InstantiationException 如果无法创建实例
     */
    public static <T> IntervalBiFunction<T> instantiateIntervalBiFunctionClass(
        Class<? extends IntervalBiFunction<T>> clazz) {
        try {
            Constructor<? extends IntervalBiFunction<T>> c = clazz.getConstructor();
            if (c != null) {
                return c.newInstance();
            } else {
                throw new InstantiationException(INSTANTIATION_ERROR_PREFIX + clazz.getName());
            }
        } catch (Exception e) {
            throw new InstantiationException(INSTANTIATION_ERROR_PREFIX + clazz.getName(), e);
        }
    }

    /**
     * 实例化 Predicate 类
     *
     * 通过反射创建 Predicate 的实例。
     * 常用于从配置中加载自定义的重试条件、断路器条件等。
     *
     * @param <T>   Predicate 的输入类型
     * @param clazz 要实例化的类对象
     * @return 创建的 Predicate 实例
     * @throws InstantiationException 如果无法创建实例
     */
    public static <T> Predicate<T> instantiatePredicateClass(Class<? extends Predicate<T>> clazz) {
        try {
            Constructor<? extends Predicate<T>> c = clazz.getConstructor();
            if (c != null) {
                return c.newInstance();
            } else {
                throw new InstantiationException(INSTANTIATION_ERROR_PREFIX + clazz.getName());
            }
        } catch (Exception e) {
            throw new InstantiationException(INSTANTIATION_ERROR_PREFIX + clazz.getName(), e);
        }
    }

    /**
     * 实例化 BiConsumer 类
     *
     * 通过反射创建 BiConsumer 的实例。
     * 常用于从配置中加载自定义的事件消费者。
     *
     * @param <T>   BiConsumer 的第二个参数类型
     * @param clazz 要实例化的类对象
     * @return 创建的 BiConsumer 实例
     * @throws InstantiationException 如果无法创建实例
     */
    public static <T> BiConsumer<Integer, T> instantiateBiConsumer(Class<? extends BiConsumer<Integer, T>> clazz) {
        try {
            Constructor<? extends BiConsumer<Integer, T>> c = clazz.getConstructor();
            if (c != null) {
                return c.newInstance();
            } else {
                throw new InstantiationException(INSTANTIATION_ERROR_PREFIX + clazz.getName());
            }
        } catch (Exception e) {
            throw new InstantiationException(INSTANTIATION_ERROR_PREFIX + clazz.getName(), e);
        }
    }

    /**
     * 实例化 Function 类
     *
     * 通过反射创建 Function 的实例。
     * 常用于从配置中加载自定义的转换函数、映射函数等。
     *
     * @param <T>   Function 的输入类型
     * @param <R>   Function 的返回类型
     * @param clazz 要实例化的类对象
     * @return 创建的 Function 实例
     * @throws InstantiationException 如果无法创建实例
     */
    public static <T, R> Function<T, R> instantiateFunction(Class<? extends Function<T, R>> clazz) {
        try {
            Constructor<? extends Function<T, R>> c = clazz.getConstructor();
            if (c != null) {
                return c.newInstance();
            } else {
                throw new InstantiationException(INSTANTIATION_ERROR_PREFIX + clazz.getName());
            }
        } catch (Exception e) {
            throw new InstantiationException(INSTANTIATION_ERROR_PREFIX + clazz.getName(), e);
        }
    }

    /**
     * 通过默认构造函数实例化类
     *
     * 功能说明：
     * 这是一个通用的实例化方法，可以创建任何类型的实例。
     * 在创建实例前会检查类是否有无参构造函数。
     *
     * 检查逻辑：
     * 1. 参数不能为 null
     * 2. 如果类定义了构造函数，必须包含无参构造函数
     * 3. 如果类没有定义任何构造函数，Java 会自动提供默认构造函数
     *
     * 使用场景：
     * - 动态加载配置类
     * - 实例化用户自定义的扩展类
     * - SPI（Service Provider Interface）实现类的加载
     *
     * 示例：
     * <pre>
     * // 实例化配置类
     * Class&lt;MyConfig&gt; configClass = MyConfig.class;
     * MyConfig config = ClassUtils.instantiateClassDefConstructor(configClass);
     * </pre>
     *
     * @param <T>   要实例化的类型
     * @param clazz 要实例化的类对象，不能为 null
     * @return 创建的实例
     * @throws InstantiationException 如果类为 null、没有无参构造函数或实例化失败
     */
    public static <T> T instantiateClassDefConstructor(Class<T> clazz) {
        // 如果定义了构造函数，则必须有无参构造函数
        // 如果没有定义构造函数，则默认构造函数已存在
        Objects.requireNonNull(clazz, "class to instantiate should not be null");

        // 检查：如果有构造函数，必须包含无参构造函数
        if (clazz.getConstructors().length > 0
            && Arrays.stream(clazz.getConstructors()).noneMatch(c -> c.getParameterCount() == 0)) {
            throw new InstantiationException(
                "Default constructor is required to create instance of public class: " + clazz
                    .getName());
        }

        try {
            return clazz.getConstructor().newInstance();
        } catch (Exception e) {
            throw new InstantiationException(INSTANTIATION_ERROR_PREFIX + clazz.getName(), e);
        }
    }
}
