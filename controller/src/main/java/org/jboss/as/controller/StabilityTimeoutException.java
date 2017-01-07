/*
Copyright 2017 Red Hat, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */

package org.jboss.as.controller;

import java.util.concurrent.TimeUnit;

/**
 * A {@link java.util.concurrent.TimeoutException} variant that provides the period
 * exceeded. Useful for reporting on timeouts in situations where the reporting code
 * does not have access to the exact figure that triggered the timeout.
 *
 * @author Brian Stansberry
 */
class StabilityTimeoutException extends java.util.concurrent.TimeoutException {

    private static final long serialVersionUID = 1L;

    private final long timeoutPeriod;

    /**
     * Creates a new StabilityTimeoutException.
     * @param timeoutPeriod the period of time, in ms, the exceeding of which triggered this exception
     */
    StabilityTimeoutException(long timeoutPeriod) {
        this.timeoutPeriod = timeoutPeriod;
    }

    /**
     * Creates a new StabilityTimeoutException.
     * @param timeoutPeriod the period of time the exceeding of which triggered this exception
     * @param timeUnit unit represented by {@code timePeriod}
     */
    StabilityTimeoutException(long timeoutPeriod, TimeUnit timeUnit) {
        this(timeUnit.toMillis(timeoutPeriod));
    }

    /**
     * Gets the period of time the exceeding of which was considered to be an exception.
     * This is not the period that actually elapsed; it's the limit that was exceeded.
     *
     * @return the period, in ms
     */
    long getTimeoutPeriod() {
        return timeoutPeriod;
    }
}
