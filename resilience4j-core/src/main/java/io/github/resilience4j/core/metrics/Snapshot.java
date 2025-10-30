/*
 *
 *  Copyright 2019 Robert Winkler and Bohdan Storozhuk
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
package io.github.resilience4j.core.metrics;

import java.time.Duration;

/**
 * Snapshot - 度量指标快照接口
 *
 * <h2>功能说明</h2>
 * Snapshot 表示某一时刻的度量统计快照，提供完整的调用统计信息。
 * 快照是不可变的，一旦创建就不会改变，代表创建时刻的统计状态。
 *
 * <h2>核心指标</h2>
 * Snapshot 提供以下统计指标：
 * <ul>
 *   <li><b>调用次数统计</b>：总调用、成功、失败、慢调用</li>
 *   <li><b>时长统计</b>：总时长、平均时长</li>
 *   <li><b>比率统计</b>：失败率、慢调用率（百分比）</li>
 * </ul>
 *
 * <h2>统计公式</h2>
 * <pre>
 * 失败率 = (失败调用数 / 总调用数) × 100%
 * 慢调用率 = (慢调用数 / 总调用数) × 100%
 * 平均时长 = 总时长 / 总调用数
 * 成功调用数 = 总调用数 - 失败调用数
 * 慢成功调用数 = 慢调用数 - 慢失败调用数
 * </pre>
 *
 * <h2>使用场景</h2>
 * <ul>
 *   <li>断路器决策：根据失败率或慢调用率决定是否打开断路器</li>
 *   <li>监控报警：失败率或慢调用率超过阈值时触发告警</li>
 *   <li>健康检查：判断服务是否健康</li>
 *   <li>性能分析：分析平均响应时间和慢调用比例</li>
 *   <li>仪表盘展示：显示实时统计数据</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>
 * // 获取快照
 * Metrics metrics = circuitBreaker.getMetrics();
 * Snapshot snapshot = metrics.getSnapshot();
 *
 * // 检查失败率
 * float failureRate = snapshot.getFailureRate();
 * if (failureRate > 50.0f) {
 *     logger.warn("High failure rate: {}%", failureRate);
 * }
 *
 * // 检查慢调用率
 * float slowCallRate = snapshot.getSlowCallRate();
 * if (slowCallRate > 30.0f) {
 *     logger.warn("High slow call rate: {}%", slowCallRate);
 * }
 *
 * // 打印完整统计
 * logger.info("Total calls: {}", snapshot.getTotalNumberOfCalls());
 * logger.info("Successful calls: {}", snapshot.getNumberOfSuccessfulCalls());
 * logger.info("Failed calls: {}", snapshot.getNumberOfFailedCalls());
 * logger.info("Slow calls: {}", snapshot.getTotalNumberOfSlowCalls());
 * logger.info("Average duration: {}", snapshot.getAverageDuration());
 * logger.info("Total duration: {}", snapshot.getTotalDuration());
 * </pre>
 *
 * <h2>实现类</h2>
 * {@link SnapshotImpl} 是唯一实现类，提供所有统计指标的计算。
 *
 * <h2>线程安全性</h2>
 * Snapshot 实例是不可变的，因此天然线程安全。
 *
 * @author Robert Winkler, Bohdan Storozhuk
 * @since 1.0.0
 * @see Metrics
 * @see SnapshotImpl
 */
public interface Snapshot {

    /**
     * 获取总时长 - 所有调用的累计执行时间
     *
     * 功能说明：
     * 返回滑动窗口内所有调用的总执行时长。
     * 包括成功和失败的调用，快速和慢速的调用。
     *
     * 计算公式：
     * 总时长 = 调用1时长 + 调用2时长 + ... + 调用N时长
     *
     * 使用示例：
     * <pre>
     * Snapshot snapshot = metrics.getSnapshot();
     * Duration totalDuration = snapshot.getTotalDuration();
     *
     * logger.info("Total execution time: {} seconds",
     *     totalDuration.getSeconds());
     *
     * // 与调用次数结合使用
     * int totalCalls = snapshot.getTotalNumberOfCalls();
     * logger.info("Average time per call: {} ms",
     *     totalDuration.toMillis() / totalCalls);
     * </pre>
     *
     * @return 总时长
     */
    Duration getTotalDuration();

    /**
     * 获取平均时长 - 每次调用的平均执行时间
     *
     * 功能说明：
     * 返回滑动窗口内所有调用的平均执行时长。
     * 这是最重要的性能指标之一，反映服务的整体响应速度。
     *
     * 计算公式：
     * 平均时长 = 总时长 / 总调用数
     *
     * 特殊情况：
     * 如果总调用数为0，返回 Duration.ZERO
     *
     * 使用示例：
     * <pre>
     * Snapshot snapshot = metrics.getSnapshot();
     * Duration avgDuration = snapshot.getAverageDuration();
     *
     * logger.info("Average response time: {} ms", avgDuration.toMillis());
     *
     * // 性能告警
     * if (avgDuration.toMillis() > 1000) {
     *     logger.warn("Average response time exceeds 1 second");
     * }
     *
     * // 与 SLA 比较
     * Duration slaTarget = Duration.ofMillis(500);
     * if (avgDuration.compareTo(slaTarget) > 0) {
     *     logger.error("SLA violated: average response time {} > target {}",
     *         avgDuration, slaTarget);
     * }
     * </pre>
     *
     * @return 平均时长
     */
    Duration getAverageDuration();

    /**
     * 获取慢调用总数 - 超过慢调用阈值的调用次数
     *
     * 功能说明：
     * 返回执行时长超过慢调用阈值的调用总数。
     * 包括慢成功调用和慢失败调用。
     *
     * 计算公式：
     * 慢调用总数 = 慢成功调用数 + 慢失败调用数
     *
     * 使用示例：
     * <pre>
     * Snapshot snapshot = metrics.getSnapshot();
     * int slowCalls = snapshot.getTotalNumberOfSlowCalls();
     * int totalCalls = snapshot.getTotalNumberOfCalls();
     *
     * logger.info("Slow calls: {}/{} ({}%)",
     *     slowCalls, totalCalls, snapshot.getSlowCallRate());
     *
     * // 检查慢调用占比
     * if (slowCalls > totalCalls * 0.2) {
     *     logger.warn("More than 20% calls are slow");
     * }
     * </pre>
     *
     * @return 慢调用总数
     */
    int getTotalNumberOfSlowCalls();

    /**
     * 获取慢成功调用数 - 成功但超过阈值的调用次数
     *
     * 功能说明：
     * 返回执行成功但时长超过慢调用阈值的调用次数。
     * 这些调用虽然成功，但性能不佳，需要关注。
     *
     * 计算公式：
     * 慢成功调用数 = 慢调用总数 - 慢失败调用数
     *
     * 使用示例：
     * <pre>
     * Snapshot snapshot = metrics.getSnapshot();
     * int slowSuccessful = snapshot.getNumberOfSlowSuccessfulCalls();
     * int slowFailed = snapshot.getNumberOfSlowFailedCalls();
     *
     * logger.info("Slow calls breakdown:");
     * logger.info("  Slow successful: {}", slowSuccessful);
     * logger.info("  Slow failed: {}", slowFailed);
     *
     * // 性能问题分析
     * if (slowSuccessful > 0) {
     *     logger.warn("Service is responding slowly even on successful calls");
     * }
     * </pre>
     *
     * @return 慢成功调用数
     */
    int getNumberOfSlowSuccessfulCalls();

    /**
     * 获取慢失败调用数 - 失败且超过阈值的调用次数
     *
     * 功能说明：
     * 返回执行失败且时长超过慢调用阈值的调用次数。
     * 这是最糟糕的情况：既失败又慢。
     *
     * 使用示例：
     * <pre>
     * Snapshot snapshot = metrics.getSnapshot();
     * int slowFailed = snapshot.getNumberOfSlowFailedCalls();
     *
     * if (slowFailed > 0) {
     *     logger.error("Service is failing slowly: {} calls", slowFailed);
     *     // 可能是超时导致的失败
     * }
     *
     * // 与总失败数比较
     * int totalFailed = snapshot.getNumberOfFailedCalls();
     * if (slowFailed == totalFailed) {
     *     logger.error("All failures are slow - possible timeout issue");
     * }
     * </pre>
     *
     * @return 慢失败调用数
     */
    int getNumberOfSlowFailedCalls();

    /**
     * 获取慢调用率 - 慢调用在所有调用中的占比
     *
     * 功能说明：
     * 返回慢调用占总调用的百分比（0-100）。
     * 这是断路器判断是否打开的重要指标之一。
     *
     * 计算公式：
     * 慢调用率 = (慢调用总数 / 总调用数) × 100%
     *
     * 特殊情况：
     * 如果总调用数为0，返回 0.0f
     *
     * 使用示例：
     * <pre>
     * Snapshot snapshot = metrics.getSnapshot();
     * float slowRate = snapshot.getSlowCallRate();
     *
     * logger.info("Slow call rate: {:.2f}%", slowRate);
     *
     * // 断路器决策
     * if (slowRate > 50.0f) {
     *     logger.warn("Slow call rate exceeds 50%, circuit breaker may open");
     * }
     *
     * // 结合失败率判断
     * float failureRate = snapshot.getFailureRate();
     * if (slowRate > 30.0f && failureRate > 30.0f) {
     *     logger.error("Both slow rate and failure rate are high - service degraded");
     * }
     * </pre>
     *
     * @return 慢调用率（百分比，0-100）
     */
    float getSlowCallRate();

    /**
     * 获取成功调用数 - 执行成功的调用次数
     *
     * 功能说明：
     * 返回执行成功的调用总数。
     * 包括快速成功和慢速成功的调用。
     *
     * 计算公式：
     * 成功调用数 = 总调用数 - 失败调用数
     *
     * 使用示例：
     * <pre>
     * Snapshot snapshot = metrics.getSnapshot();
     * int successful = snapshot.getNumberOfSuccessfulCalls();
     * int total = snapshot.getTotalNumberOfCalls();
     *
     * logger.info("Successful calls: {}/{} ({:.2f}%)",
     *     successful, total, (successful * 100.0f / total));
     *
     * // 健康检查
     * if (successful < total * 0.9) {
     *     logger.warn("Success rate below 90%");
     * }
     * </pre>
     *
     * @return 成功调用数
     */
    int getNumberOfSuccessfulCalls();

    /**
     * 获取失败调用数 - 执行失败的调用次数
     *
     * 功能说明：
     * 返回执行失败的调用总数。
     * 包括快速失败和慢速失败的调用。
     *
     * 使用示例：
     * <pre>
     * Snapshot snapshot = metrics.getSnapshot();
     * int failed = snapshot.getNumberOfFailedCalls();
     * int total = snapshot.getTotalNumberOfCalls();
     *
     * logger.info("Failed calls: {}/{}", failed, total);
     *
     * // 告警阈值
     * if (failed > 10) {
     *     logger.error("Too many failures: {}", failed);
     * }
     *
     * // 计算成功率
     * float successRate = 100.0f - snapshot.getFailureRate();
     * logger.info("Success rate: {:.2f}%", successRate);
     * </pre>
     *
     * @return 失败调用数
     */
    int getNumberOfFailedCalls();

    /**
     * 获取总调用数 - 所有调用的总次数
     *
     * 功能说明：
     * 返回滑动窗口内的总调用次数。
     * 包括成功、失败、快速、慢速的所有调用。
     *
     * 计算公式：
     * 总调用数 = 成功调用数 + 失败调用数
     *
     * 使用示例：
     * <pre>
     * Snapshot snapshot = metrics.getSnapshot();
     * int totalCalls = snapshot.getTotalNumberOfCalls();
     *
     * logger.info("Total calls: {}", totalCalls);
     *
     * // 检查样本量
     * if (totalCalls < 10) {
     *     logger.warn("Sample size too small for reliable statistics");
     * }
     *
     * // 流量分析
     * Duration windowDuration = Duration.ofSeconds(60);
     * double qps = totalCalls / (double) windowDuration.getSeconds();
     * logger.info("QPS: {:.2f}", qps);
     * </pre>
     *
     * @return 总调用数
     */
    int getTotalNumberOfCalls();

    /**
     * 获取失败率 - 失败调用在所有调用中的占比
     *
     * 功能说明：
     * 返回失败调用占总调用的百分比（0-100）。
     * 这是断路器判断是否打开的核心指标。
     *
     * 计算公式：
     * 失败率 = (失败调用数 / 总调用数) × 100%
     *
     * 特殊情况：
     * 如果总调用数为0，返回 0.0f
     *
     * 使用示例：
     * <pre>
     * Snapshot snapshot = metrics.getSnapshot();
     * float failureRate = snapshot.getFailureRate();
     *
     * logger.info("Failure rate: {:.2f}%", failureRate);
     *
     * // 断路器决策（典型阈值：50%）
     * if (failureRate > 50.0f) {
     *     logger.error("Failure rate {}% exceeds threshold, opening circuit breaker",
     *         failureRate);
     * }
     *
     * // 健康状态判断
     * if (failureRate < 1.0f) {
     *     return HealthStatus.HEALTHY;
     * } else if (failureRate < 10.0f) {
     *     return HealthStatus.DEGRADED;
     * } else {
     *     return HealthStatus.UNHEALTHY;
     * }
     * </pre>
     *
     * @return 失败率（百分比，0-100）
     */
    float getFailureRate();
}
