/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.threads;

import java.io.Serializable;
import java.util.concurrent.TimeUnit;

import org.wildfly.common.Assert;

/**
 * A specification of a simple duration.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public final class TimeSpec implements Serializable {

    private static final long serialVersionUID = 5145007669106852119L;

    public static final TimeSpec DEFAULT_KEEPALIVE = new TimeSpec(TimeUnit.SECONDS, 30L);

    private final TimeUnit unit;
    private final long duration;

    /**
     * Construct a new instance.
     *
     * @param unit the unit of time
     * @param duration the quantity of units
     */
    public TimeSpec(final TimeUnit unit, final long duration) {
        Assert.checkNotNullParam("unit", unit);
        this.unit = unit;
        this.duration = duration;
    }

    /**
     * Get the time unit.
     *
     * @return the time unit
     */
    public TimeUnit getUnit() {
        return unit;
    }

    /**
     * Get the duration.
     *
     * @return the duration
     */
    public long getDuration() {
        return duration;
    }

    public long elementHash() {
        return Long.rotateLeft(duration, 3) ^ unit.hashCode() & 0xFFFFFFFFL;
    }

    public boolean equals(Object obj) {
        return obj instanceof TimeSpec && equals((TimeSpec) obj);
    }

    public boolean equals(TimeSpec obj) {
        return obj != null && unit == obj.unit && duration == obj.duration;
    }

    public int hashCode() {
        return (int) elementHash();
    }
}
