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

import java.util.concurrent.TimeUnit;

/**
 * Metrics - 度量指标接口
 *
 * <h2>功能说明</h2>
 * Metrics 是 Resilience4j 的核心度量接口，用于记录调用结果并生成统计快照。
 * 所有需要统计调用成功率、失败率、执行时长等指标的组件都实现此接口。
 *
 * <h2>核心功能</h2>
 * <ul>
 *   <li><b>记录调用</b>：记录每次调用的时长和结果（成功/失败/慢调用）</li>
 *   <li><b>生成快照</b>：生成当前时间点的统计快照，包含成功率、失败率等</li>
 *   <li><b>滑动窗口</b>：支持基于次数或时间的滑动窗口统计</li>
 * </ul>
 *
 * <h2>调用结果分类</h2>
 * 调用结果分为4种类型（{@link Outcome}）：
 * <ul>
 *   <li><b>SUCCESS</b>：成功且快速的调用</li>
 *   <li><b>ERROR</b>：失败且快速的调用</li>
 *   <li><b>SLOW_SUCCESS</b>：成功但慢的调用</li>
 *   <li><b>SLOW_ERROR</b>：失败且慢的调用</li>
 * </ul>
 *
 * <h2>实现类</h2>
 * <ul>
 *   <li>{@link FixedSizeSlidingWindowMetrics} - 固定大小滑动窗口（基于次数）</li>
 *   <li>{@link LockFreeFixedSizeSlidingWindowMetrics} - 无锁固定大小滑动窗口</li>
 *   <li>{@link SlidingTimeWindowMetrics} - 时间滑动窗口（基于时间）</li>
 *   <li>{@link LockFreeSlidingTimeWindowMetrics} - 无锁时间滑动窗口</li>
 * </ul>
 *
 * <h2>使用场景</h2>
 * <ul>
 *   <li>断路器：统计调用失败率，决定是否打开断路器</li>
 *   <li>限流器：统计请求速率，控制请求流量</li>
 *   <li>性能监控：统计平均响应时间、慢调用比例</li>
 *   <li>健康检查：判断服务健康状态</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>
 * // 创建固定大小滑动窗口（统计最近100次调用）
 * Metrics metrics = new FixedSizeSlidingWindowMetrics(100);
 *
 * // 记录成功调用（耗时50ms）
 * metrics.record(50, TimeUnit.MILLISECONDS, Metrics.Outcome.SUCCESS);
 *
 * // 记录失败调用（耗时200ms）
 * metrics.record(200, TimeUnit.MILLISECONDS, Metrics.Outcome.ERROR);
 *
 * // 记录慢调用（成功但耗时2秒）
 * metrics.record(2, TimeUnit.SECONDS, Metrics.Outcome.SLOW_SUCCESS);
 *
 * // 获取统计快照
 * Snapshot snapshot = metrics.getSnapshot();
 * float failureRate = snapshot.getFailureRate();  // 失败率
 * float slowCallRate = snapshot.getSlowCallRate(); // 慢调用率
 * int totalCalls = snapshot.getTotalNumberOfCalls(); // 总调用次数
 *
 * logger.info("Failure rate: {}%, Slow call rate: {}%", failureRate, slowCallRate);
 * </pre>
 *
 * <h2>线程安全性</h2>
 * <ul>
 *   <li>接口本身不保证线程安全</li>
 *   <li>具体实现类负责保证线程安全（通过锁或无锁算法）</li>
 *   <li>FixedSizeSlidingWindowMetrics 使用 ReentrantLock</li>
 *   <li>LockFree* 实现使用原子操作和 CAS</li>
 * </ul>
 *
 * @author Robert Winkler, Bohdan Storozhuk
 * @since 1.0.0
 * @see Snapshot
 * @see FixedSizeSlidingWindowMetrics
 * @see SlidingTimeWindowMetrics
 */
public interface Metrics {

    /**
     * 记录调用 - 记录调用时长和结果
     *
     * 功能说明：
     * 记录一次调用的执行时长和结果，更新统计数据，并返回最新的快照。
     * 这是度量系统的核心方法，每次调用完成后都应该调用此方法。
     *
     * 执行流程：
     * 1. 将调用数据记录到滑动窗口中
     * 2. 更新聚合统计（总调用次数、失败次数、慢调用次数等）
     * 3. 如果使用固定大小窗口，可能驱逐最旧的记录
     * 4. 返回更新后的统计快照
     *
     * 调用结果分类：
     * - SUCCESS：快速成功（时长 < 慢调用阈值，无异常）
     * - ERROR：快速失败（时长 < 慢调用阈值，有异常）
     * - SLOW_SUCCESS：慢速成功（时长 >= 慢调用阈值，无异常）
     * - SLOW_ERROR：慢速失败（时长 >= 慢调用阈值，有异常）
     *
     * 使用示例：
     * <pre>
     * Metrics metrics = new FixedSizeSlidingWindowMetrics(100);
     *
     * // 记录快速成功调用（50ms）
     * Snapshot s1 = metrics.record(50, TimeUnit.MILLISECONDS, Outcome.SUCCESS);
     *
     * // 记录慢速成功调用（2秒）
     * Snapshot s2 = metrics.record(2, TimeUnit.SECONDS, Outcome.SLOW_SUCCESS);
     *
     * // 记录失败调用
     * Snapshot s3 = metrics.record(100, TimeUnit.MILLISECONDS, Outcome.ERROR);
     *
     * // 典型的断路器使用
     * StopWatch stopWatch = StopWatch.start();
     * try {
     *     String result = backendService.call();
     *     Duration duration = stopWatch.stop();
     *     Outcome outcome = duration.toMillis() > slowCallThreshold ?
     *         Outcome.SLOW_SUCCESS : Outcome.SUCCESS;
     *     metrics.record(duration.toMillis(), TimeUnit.MILLISECONDS, outcome);
     * } catch (Exception e) {
     *     Duration duration = stopWatch.stop();
     *     Outcome outcome = duration.toMillis() > slowCallThreshold ?
     *         Outcome.SLOW_ERROR : Outcome.ERROR;
     *     metrics.record(duration.toMillis(), TimeUnit.MILLISECONDS, outcome);
     *     throw e;
     * }
     * </pre>
     *
     * @param duration     调用时长
     * @param durationUnit 时长单位（毫秒、秒等）
     * @param outcome      调用结果（SUCCESS/ERROR/SLOW_SUCCESS/SLOW_ERROR）
     * @return 更新后的统计快照
     */
    Snapshot record(long duration, TimeUnit durationUnit, Outcome outcome);

    /**
     * 获取统计快照 - 返回当前时刻的统计数据
     *
     * 功能说明：
     * 返回当前滑动窗口的统计快照，包含所有度量指标。
     * 快照是不可变的，代表调用时刻的统计状态。
     *
     * 快照包含的指标：
     * - 总调用次数
     * - 成功调用次数
     * - 失败调用次数
     * - 慢调用次数
     * - 失败率（百分比）
     * - 慢调用率（百分比）
     * - 总执行时长
     * - 平均执行时长
     *
     * 性能特性：
     * - 时间复杂度：O(1)，因为统计数据是预先聚合的
     * - 不会阻塞 record() 操作（在某些实现中）
     * - 返回的快照是不可变的，线程安全
     *
     * 使用示例：
     * <pre>
     * Metrics metrics = new FixedSizeSlidingWindowMetrics(100);
     *
     * // 记录一些调用...
     * for (int i = 0; i < 50; i++) {
     *     metrics.record(100, TimeUnit.MILLISECONDS, Outcome.SUCCESS);
     * }
     * for (int i = 0; i < 10; i++) {
     *     metrics.record(100, TimeUnit.MILLISECONDS, Outcome.ERROR);
     * }
     *
     * // 获取快照并打印统计信息
     * Snapshot snapshot = metrics.getSnapshot();
     * logger.info("Total calls: {}", snapshot.getTotalNumberOfCalls());
     * logger.info("Failed calls: {}", snapshot.getNumberOfFailedCalls());
     * logger.info("Failure rate: {}%", snapshot.getFailureRate());
     * logger.info("Average duration: {}", snapshot.getAverageDuration());
     *
     * // 在断路器中使用
     * Snapshot snapshot = circuitBreaker.getMetrics().getSnapshot();
     * if (snapshot.getFailureRate() > 50.0f) {
     *     logger.warn("Failure rate exceeds threshold, circuit breaker may open");
     * }
     * </pre>
     *
     * @return 当前统计快照
     */
    Snapshot getSnapshot();

    /**
     * 调用结果枚举 - 定义4种调用结果类型
     *
     * <h2>结果分类</h2>
     * 调用结果根据两个维度分类：
     * <ul>
     *   <li><b>成功/失败</b>：是否抛出异常或返回错误</li>
     *   <li><b>快速/慢速</b>：执行时长是否超过慢调用阈值</li>
     * </ul>
     *
     * <h2>四种结果类型</h2>
     * <pre>
     * ┌────────────────┬──────────────┬──────────────┐
     * │                │   快速调用   │   慢速调用   │
     * ├────────────────┼──────────────┼──────────────┤
     * │  成功调用      │   SUCCESS    │ SLOW_SUCCESS │
     * │  失败调用      │    ERROR     │  SLOW_ERROR  │
     * └────────────────┴──────────────┴──────────────┘
     * </pre>
     *
     * <h2>使用说明</h2>
     * <ul>
     *   <li><b>SUCCESS</b>：正常情况，调用成功且响应快速</li>
     *   <li><b>ERROR</b>：调用失败（抛出异常或返回错误），但失败很快</li>
     *   <li><b>SLOW_SUCCESS</b>：调用成功但响应缓慢（可能是性能问题）</li>
     *   <li><b>SLOW_ERROR</b>：调用失败且耗时长（最糟糕的情况）</li>
     * </ul>
     *
     * <h2>在断路器中的应用</h2>
     * <pre>
     * // 慢调用阈值：1秒
     * long slowCallThreshold = 1000;
     *
     * // 判断逻辑
     * Outcome outcome;
     * if (exception == null) {
     *     // 调用成功
     *     outcome = (duration > slowCallThreshold) ?
     *         Outcome.SLOW_SUCCESS : Outcome.SUCCESS;
     * } else {
     *     // 调用失败
     *     outcome = (duration > slowCallThreshold) ?
     *         Outcome.SLOW_ERROR : Outcome.ERROR;
     * }
     * metrics.record(duration, TimeUnit.MILLISECONDS, outcome);
     * </pre>
     *
     * <h2>统计影响</h2>
     * <ul>
     *   <li>SUCCESS：增加成功计数</li>
     *   <li>ERROR：增加失败计数，影响失败率</li>
     *   <li>SLOW_SUCCESS：增加成功计数和慢调用计数，影响慢调用率</li>
     *   <li>SLOW_ERROR：增加失败计数、慢调用计数和慢失败计数</li>
     * </ul>
     */
    enum Outcome {
        /** 成功且快速的调用 */
        SUCCESS,
        /** 失败但快速的调用 */
        ERROR,
        /** 成功但慢的调用 */
        SLOW_SUCCESS,
        /** 失败且慢的调用 */
        SLOW_ERROR
    }

}