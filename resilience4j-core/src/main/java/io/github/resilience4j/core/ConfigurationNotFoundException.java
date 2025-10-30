/*
 *
 *  Copyright 2019 Mahmoud Romeh
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

/**
 * 配置未找到异常 - 当请求的配置不存在时抛出
 *
 * 作用说明：
 * 这是一个运行时异常，当尝试获取不存在的配置时抛出。
 * 通常发生在通过名称查找配置，但该名称未在注册表中注册时。
 *
 * 使用场景：
 * - 通过名称获取 CircuitBreaker 配置时，配置不存在
 * - 通过名称获取 Retry 配置时，配置不存在
 * - 通过名称获取 RateLimiter 配置时，配置不存在
 * - 通过名称获取 Bulkhead 配置时，配置不存在
 *
 * 示例：
 * <pre>
 * // 尝试获取不存在的配置
 * CircuitBreakerRegistry registry = CircuitBreakerRegistry.ofDefaults();
 * try {
 *     CircuitBreakerConfig config = registry.getConfiguration("myConfig");
 * } catch (ConfigurationNotFoundException e) {
 *     // 处理配置不存在的情况
 *     System.err.println(e.getMessage());
 *     // 输出: Configuration with name 'myConfig' does not exist
 * }
 * </pre>
 *
 * 注意事项：
 * - 这是运行时异常，无需强制捕获
 * - 异常消息包含了具体的配置名称，便于诊断问题
 * - 建议在使用前通过 Registry.getConfiguration() 检查配置是否存在
 *
 * @author Mahmoud Romeh
 * @since 1.0.0
 */
public class ConfigurationNotFoundException extends RuntimeException {

    /**
     * 构造配置未找到异常
     *
     * @param configName 未找到的配置名称
     */
    public ConfigurationNotFoundException(String configName) {
        super(String.format("Configuration with name '%s' does not exist", configName));
    }
}
