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

import io.github.resilience4j.core.lang.Nullable;

/**
 * 字符串工具类 - 提供字符串判空等常用方法
 *
 * 作用说明：
 * 提供简单的字符串操作工具方法，避免引入 Apache Commons Lang 等重量级依赖。
 * Resilience4j 作为轻量级库，尽量减少外部依赖。
 *
 * 设计理念：
 * - 最小依赖：不依赖第三方工具库
 * - 简单够用：只提供项目真正需要的方法
 * - 性能优先：直接判断，无额外开销
 *
 * @author Robert Winkler
 * @since 1.0.0
 */
public class StringUtils {

    /** 私有构造函数，防止实例化 */
    private StringUtils() {
    }

    /**
     * 判断字符串是否不为空
     *
     * 功能说明：
     * 检查字符串是否既不为 null 也不为空字符串（""）。
     *
     * 判断逻辑：
     * - null：返回 false
     * - ""：返回 false
     * - " "（空格）：返回 true（只检查是否为空，不检查是否为空白）
     * - "abc"：返回 true
     *
     * 与其他方法的对比：
     * <pre>
     * String s = ...;
     *
     * // StringUtils.isNotEmpty() - 本方法
     * isNotEmpty(null)   = false
     * isNotEmpty("")     = false
     * isNotEmpty(" ")    = true  （注意：空格被认为不为空）
     * isNotEmpty("abc")  = true
     *
     * // Apache Commons: StringUtils.isNotBlank()
     * isNotBlank(null)   = false
     * isNotBlank("")     = false
     * isNotBlank(" ")    = false （空白字符被认为为空）
     * isNotBlank("abc")  = true
     * </pre>
     *
     * 使用场景：
     * - 验证配置名称不为空
     * - 检查用户输入的字符串
     * - 参数校验
     *
     * 示例：
     * <pre>
     * String configName = registry.getConfigName();
     * if (StringUtils.isNotEmpty(configName)) {
     *     // 使用配置名称
     *     applyConfiguration(configName);
     * } else {
     *     // 使用默认配置
     *     applyDefaultConfiguration();
     * }
     * </pre>
     *
     * 注意事项：
     * - 本方法不会去除空白字符（trim）
     * - 如果需要判断字符串是否包含实际内容（排除空白），需要先 trim()
     * - 参数允许为 null，不会抛出 NullPointerException
     *
     * @param string 要检查的字符串，可以为 null
     * @return 如果字符串不为 null 且不为空，返回 true；否则返回 false
     */
    public static boolean isNotEmpty(@Nullable String string) {
        return string != null && !string.isEmpty();
    }

}
