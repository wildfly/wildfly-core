/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server;

/**
 * Tracks elapsed time since either JVM start (as indicated by {@link System#nanoTime()}) or a moment of initialization.
 */
public final class ElapsedTime {

    /**
     * Creates a tracker that tracks elapsed time since JVM start.
     * @return the tracker. Will not return {@code null}.
     */
    public static ElapsedTime startingFromJvmStart() {
        return new ElapsedTime(null);
    }

    /**
     * Creates a tracker that tracks elapsed time since the invocation of this method.
     * @return the tracker. Will not return {@code null}.
     */
    public static ElapsedTime startingFromNow() {
        return new ElapsedTime(System.nanoTime());
    }

    /**
     * Creates a tracker that tracks elapsed time since whatever point the given {@code toCopy} tracker does.
     * This is a cloning operation; the created tracker shares no state with the {@code toCopy} tracker.
     *
     * @param toCopy tracker to copy. Cannot be {@code null}
     * @return the tracker. Will not return {@code null}.
     */
    public static ElapsedTime from(ElapsedTime toCopy) {
        return new ElapsedTime(toCopy.startTimeNano);
    }

    private volatile Long startTimeNano;
    private volatile long approximateStartTimeMs;

    private ElapsedTime(Long startTime) {
        this.startTimeNano = startTime;
        setApproximateStartTime();
    }

    /**
     * Gets an approximation of this tracker's start time, expressed in milliseconds since the epoch.
     * <p>
     * <strong>Note:</strong> The value returned here should not be used for any precision work. It is meant to be
     * an approximate reference value, used, for example, in a display value mean for human consumption.
     * It is calculated when this tracker is constructed or reset by taking the {@link System#currentTimeMillis()
     * and subtracting the value returned by {@link #getElapsedTimeMs()}.
     * </p>
     * @return the approximate start time for this tracker, in milliseconds since the epoch.
     */
    public long getApproximateStartTimeMs() {
        return approximateStartTimeMs;
    }

    /**
     * Get the elapsed time in milliseconds since this tracker's start point.
     * <p>
     * <strong>Note:</strong> This is calculated by calling {@link #getElapsedTimeNano()} and
     * dividing the result by {@code 1000000L}. The calculation does not involve the value
     * returned by {@link #getApproximateStartTimeMs()}.
     *
     * @return the elapsed time
     */
    public long getElapsedTimeMs() {
        return getElapsedTimeNano() / 1000000L;
    }

    /**
     * Get the elapsed time in nanoseconds since this tracker's start point.
     * @return the elapsed time
     */
    public long getElapsedTimeNano() {
        long now = System.nanoTime();
        return startTimeNano == null ? now : now - startTimeNano;
    }

    /**
     * Reset this tracker to begin tracking from the {@link System#nanoTime() 'origin' moment}
     * for this JVM. Meant for cases where the 'origin' moment may have changed and this tracker
     * should be updated accordingly -- for example, in a 'restored' JVM that
     * supports some form of checkpoint and restore behavior.
     */
    public void resetStartToJvmStart() {
        startTimeNano = null;
        setApproximateStartTime();
    }

    /**
     * Reset this tracker to begin tracking from the moment this method is invoked.
     * Meant for cases where some sort of 'restart' is being tracked.
     */
    public void resetStartToNow() {
        startTimeNano = System.nanoTime();
        setApproximateStartTime();
    }

    private void setApproximateStartTime() {
        this.approximateStartTimeMs = System.currentTimeMillis() - getElapsedTimeMs();
    }


}
