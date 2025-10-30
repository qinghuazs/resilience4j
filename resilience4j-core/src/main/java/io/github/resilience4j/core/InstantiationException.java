package io.github.resilience4j.core;

/**
 * 实例化异常 - 当无法创建对象实例时抛出
 *
 * 作用说明：
 * 这是一个运行时异常，当通过反射或其他方式创建对象失败时抛出。
 * 通常用于工厂方法、依赖注入、插件加载等需要动态创建对象的场景。
 *
 * 使用场景：
 * - 通过反射创建实例失败
 * - 类加载失败
 * - 构造函数调用失败
 * - SPI（Service Provider Interface）加载失败
 * - 插件实例化失败
 *
 * 示例：
 * <pre>
 * // 通过反射创建实例
 * try {
 *     Class&lt;?&gt; clazz = Class.forName(className);
 *     Object instance = clazz.getDeclaredConstructor().newInstance();
 * } catch (Exception e) {
 *     throw new InstantiationException(
 *         "Failed to instantiate class: " + className, e);
 * }
 * </pre>
 *
 * 与 java.lang.InstantiationException 的区别：
 * - java.lang.InstantiationException：检查异常，Java 内置
 * - resilience4j.InstantiationException：运行时异常，Resilience4j 自定义
 *
 * 为什么使用运行时异常？
 * - 实例化失败通常是配置错误或编程错误，无法在运行时恢复
 * - 避免强制调用方处理异常，简化 API
 * - 符合 Resilience4j 的异常处理风格
 *
 * 注意事项：
 * - 异常消息应该包含具体的失败原因
 * - 建议包含原始异常（cause），便于诊断问题
 * - 这是运行时异常，无需强制捕获
 *
 * @author Resilience4j 团队
 * @since 1.0.0
 */
public class InstantiationException extends RuntimeException {

    /**
     * 构造实例化异常
     *
     * @param message 异常消息，描述实例化失败的原因
     */
    public InstantiationException(String message) {
        super(message);
    }

    /**
     * 构造实例化异常，包含原始异常
     *
     * @param message 异常消息，描述实例化失败的原因
     * @param cause   原始异常，导致实例化失败的根本原因
     */
    public InstantiationException(String message, Throwable cause) {
        super(message, cause);
    }

}
