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

import java.time.ZonedDateTime;
import java.util.Locale;

import jakarta.json.JsonObject;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A single tracked metric: a named counter stored in the repository, along with descriptive metadata. Handles are
 * obtained from the {@link MetricsManager} service: one asked for by name reads the repository on every access, one
 * from a {@link MetricsManager#getMetrics listing} reports the values it was listed with, and increments are
 * cluster-safe from either. Beside the ever-growing {@link #getCurrentValue current
 * value}, a metric tracks reporting periods closed by {@link #rollOver rolling over} - usually on the metric's own
 * {@link #getRolloverSchedule schedule} - remembering the counter value at the last roll-over
 * ({@link #getPreviousValue}), when it happened ({@link #getLastRollover}), and the amount accumulated in the period
 * that was closed ({@link #getLastDelta}), so reports can show both "so far this period" and "in the last full
 * period" numbers.
 *
 * @version $Id$
 * @since 0.9.42
 */
public interface Metric
{
    /**
     * Who is allowed to see a metric.
     *
     * @since 0.9.42
     */
    enum AccessLevel
    {
        /** Visible to anyone. */
        PUBLIC,
        /** Visible only to administrators. */
        ADMIN;

        /**
         * The serialization of this access level, as stored in the repository.
         *
         * @return the enum name in lowercase
         */
        @NotNull
        public String asPropertyValue()
        {
            return name().toLowerCase(Locale.ROOT);
        }

        /**
         * Parse a stored property value back into an access level.
         *
         * @param value the stored value, may be {@code null}
         * @return {@link #ADMIN} if the value is {@code admin} (in any case), {@link #PUBLIC} otherwise
         */
        @NotNull
        public static AccessLevel fromPropertyValue(@Nullable final String value)
        {
            return ADMIN.asPropertyValue().equalsIgnoreCase(value) ? ADMIN : PUBLIC;
        }
    }

    /**
     * The identifier of this metric, unique among all metrics.
     *
     * @return a simple string, valid as a node name
     */
    @NotNull
    String getName();

    /**
     * The human-readable name of this metric.
     *
     * @return a short display string, the {@link #getName name} itself if no label was explicitly set
     */
    @NotNull
    String getLabel();

    /**
     * A longer explanation of what this metric counts.
     *
     * @return a description, or {@code null} if none was set
     */
    @Nullable
    String getDescription();

    /**
     * The category used to group related metrics in reports.
     *
     * @return a category name, or {@code null} if this metric is not categorized
     */
    @Nullable
    String getCategory();

    /**
     * Where this metric is placed among the metrics of its own {@link #getCategory category}, lower values first.
     *
     * @return the requested order, {@code 0} if none was set
     */
    long getDefaultOrder();

    /**
     * Who is allowed to see this metric.
     *
     * @return one of the {@link AccessLevel} values, {@link AccessLevel#PUBLIC} by default
     */
    @NotNull
    AccessLevel getAccessLevel();

    /**
     * The current value of the counter, accumulated over the whole lifetime of the metric.
     *
     * @return the current count, {@code 0} for a metric that was never incremented
     */
    long getCurrentValue();

    /**
     * The value the counter had at the {@link #rollOver last roll-over}, the baseline for
     * {@link #getCurrentDelta delta} computations.
     *
     * @return the previous count, {@code 0} if the metric was never rolled over
     */
    long getPreviousValue();

    /**
     * The amount accumulated so far in the current period: the difference between the {@link #getCurrentValue
     * current} and the {@link #getPreviousValue previous} value.
     *
     * @return the running count of the current period, may be negative if decrements were recorded
     */
    long getCurrentDelta();

    /**
     * The amount accumulated during the last closed period, frozen by the last {@link #rollOver}. Unlike
     * {@link #getCurrentDelta}, this is stable between roll-overs, so a report generated right after a roll-over
     * can still show the count of the period that just ended.
     *
     * @return the count of the last closed period, {@code 0} if the metric was never rolled over
     */
    long getLastDelta();

    /**
     * When this metric was last incremented.
     *
     * @return a date, or {@code null} if the metric was never incremented
     */
    @Nullable
    ZonedDateTime getLastUpdated();

    /**
     * When this metric was last {@link #rollOver rolled over}.
     *
     * @return a date, or {@code null} if the metric was never rolled over
     */
    @Nullable
    ZonedDateTime getLastRollover();

    /**
     * The schedule for automatic periodic {@link #rollOver roll-overs}, as a Quartz cron expression, e.g.
     * {@code 0 0 0 * * ?} for "nightly at midnight".
     *
     * @return a Quartz cron expression, or {@code null} if this metric is only rolled over manually
     */
    @Nullable
    String getRolloverSchedule();

    /**
     * Increment the counter by one. A shorthand for {@code increment(1)}.
     */
    void increment();

    /**
     * Add the given amount to the counter, atomically and cluster-safe: concurrent increments are all applied, none
     * gets lost or causes a conflict. Failures are logged but intentionally not propagated, so that recording a
     * metric can never disrupt the operation being counted.
     *
     * @param amount the amount to add, may be negative to correct over-counting; {@code 0} does nothing
     */
    void increment(long amount);

    /**
     * Close the current period and start a new one: freeze the amount accumulated so far as the
     * {@link #getLastDelta last delta}, remember the current value as the new {@link #getPreviousValue baseline},
     * and record the {@link #getLastRollover roll-over time}. The {@link #getCurrentValue current value} is not
     * affected, the counter keeps growing across roll-overs. Reading a metric never rolls it over - this only
     * happens when explicitly requested, normally by the scheduler honoring the metric's
     * {@link #getRolloverSchedule schedule}.
     *
     * @throws MetricsException if the roll-over cannot be persisted
     */
    void rollOver();

    /**
     * Serialize this metric as a JSON object.
     *
     * @return a JSON object with the {@code name}, {@code label}, {@code description}, {@code category},
     *         {@code defaultOrder}, {@code accessLevel}, {@code currentValue}, {@code previousValue},
     *         {@code currentDelta} and
     *         {@code lastDelta} keys, plus {@code lastUpdated} and {@code lastRollover} dates and the
     *         {@code rolloverSchedule} when present
     */
    @NotNull
    JsonObject toJson();
}
