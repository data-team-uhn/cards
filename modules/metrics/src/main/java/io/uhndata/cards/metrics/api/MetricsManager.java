/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.uhndata.cards.metrics.api;

import java.util.List;
import java.util.Optional;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Service for defining and looking up {@link Metric metrics}. This only handles the lifecycle: reading and updating
 * a counter is done through the returned {@link Metric} handles.
 *
 * @version $Id$
 * @since 0.9.42
 */
public interface MetricsManager
{
    /**
     * A {@link MetricBuilder#withRolloverSchedule roll-over schedule} closing a period at the end of each day. It
     * fires a minute before midnight rather than at midnight, so that a report scheduled at midnight, the usual
     * time, always sees the day that just ended instead of racing the roll-over.
     */
    String END_OF_DAY = "0 59 23 * * ?";

    /**
     * Configures the metadata of a metric being {@link MetricsManager#createMetric defined}. All the metadata is
     * optional: an unlabeled metric displays its name, and unless restricted, a metric is publicly visible.
     *
     * @since 0.9.42
     */
    interface MetricBuilder
    {
        /**
         * Set the human-readable name of the metric.
         *
         * @param label a short display string
         * @return this builder
         */
        @NotNull
        MetricBuilder withLabel(@Nullable String label);

        /**
         * Set the label, and the {@link #withDefaultOrder order} along with it, from the form metric names take in
         * configurations: {@code {007} Initial emails sent} is labeled {@code Initial emails sent} and ordered
         * {@code 7}. A value without a leading number is all label and leaves the order alone.
         *
         * @param numberedLabel a label, optionally prefixed with a number in braces
         * @return this builder
         */
        @NotNull
        MetricBuilder withNumberedLabel(@Nullable String numberedLabel);

        /**
         * Set a longer explanation of what the metric counts.
         *
         * @param description a description
         * @return this builder
         */
        @NotNull
        MetricBuilder withDescription(@Nullable String description);

        /**
         * Set the category used to group related metrics in reports.
         *
         * @param category a category name
         * @return this builder
         */
        @NotNull
        MetricBuilder withCategory(@Nullable String category);

        /**
         * Place the metric among the metrics of its own {@link #withCategory category}, lower values first. The
         * convention elsewhere in the platform is to leave gaps, e.g. multiples of ten, so that a metric can later
         * be slotted in between two existing ones without renumbering them.
         *
         * @param defaultOrder the requested order; metrics that do not set one are ordered as {@code 0}
         * @return this builder
         */
        @NotNull
        MetricBuilder withDefaultOrder(long defaultOrder);

        /**
         * Restrict who is allowed to see the metric.
         *
         * @param accessLevel one of the {@link Metric.AccessLevel} values
         * @return this builder
         */
        @NotNull
        MetricBuilder withAccessLevel(@NotNull Metric.AccessLevel accessLevel);

        /**
         * Set a schedule for automatic periodic {@link Metric#rollOver roll-overs}. Without a schedule, the metric
         * is only rolled over when {@link Metric#rollOver} is explicitly invoked. The expression is not validated
         * here: an invalid expression is reported in the logs by the scheduler and simply never fires.
         *
         * @param rolloverSchedule a Quartz cron expression, e.g. {@code 0 0 0 * * ?} for "nightly at midnight"
         * @return this builder
         */
        @NotNull
        MetricBuilder withRolloverSchedule(@Nullable String rolloverSchedule);

        /**
         * Persist the metric definition. This is idempotent and can safely be invoked on every component
         * activation: if the metric already exists, its counter is left untouched and its metadata is updated to
         * match this builder, including removing metadata not set on the builder.
         *
         * @return the created or updated metric
         * @throws MetricsException if the definition cannot be persisted
         */
        @NotNull
        Metric create();
    }

    /**
     * Start defining a new metric. Nothing is persisted until {@link MetricBuilder#create()} is invoked on the
     * returned builder.
     *
     * @param name the identifier of the metric, which must be usable as a single node name: not empty, not
     *            {@code .} or {@code ..}, and without {@code /}, {@code :}, {@code [}, {@code ]}, {@code |} or
     *            {@code *}
     * @return a builder for the optional metadata
     * @throws IllegalArgumentException if the name is missing or not usable as a node name
     */
    @NotNull
    MetricBuilder createMetric(@Nullable String name);

    /**
     * Add to a defined metric, by name. This is how code that is told which metric to count, typically by its
     * configuration, records something: unlike going through {@link #getMetric}, it never throws, and a name that
     * does not identify a metric counts nothing, so recording a metric can never disrupt the operation being
     * counted. Failures are logged.
     *
     * @param name the identifier of the metric
     * @param amount the amount to add, may be negative to correct over-counting; {@code 0} does nothing
     */
    void increment(@Nullable String name, long amount);

    /**
     * Look up an existing metric.
     *
     * @param name the identifier of the metric
     * @return the metric, or an empty optional if no metric with this name was defined
     */
    @NotNull
    Optional<Metric> getMetric(@Nullable String name);

    /**
     * List all the defined metrics, in the order they are meant to be displayed: grouped by
     * {@link Metric#getCategory category}, categories in alphabetical order with the uncategorized metrics last,
     * and ordered by {@link Metric#getDefaultOrder defaultOrder} within each category. Metrics that agree on all of
     * that are ordered by name, so the listing is stable.
     *
     * <p>
     * The returned metrics report the values read while listing them, which is what a report or a dashboard wants,
     * and costs one repository session for the whole list rather than one per value. They are still perfectly good
     * for {@link Metric#increment incrementing} and {@link Metric#rollOver rolling over}, since those always go to
     * the repository; but for a handle that keeps reporting current values, ask for it by name with
     * {@link #getMetric}.
     * </p>
     *
     * @return all metrics, in display order, may be empty
     */
    @NotNull
    List<Metric> getMetrics();
}
