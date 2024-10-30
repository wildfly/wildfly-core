/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.core.management;

import static org.junit.Assert.assertEquals;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

/**
 * Unit tests of {@link VirtualThreadPinningResourceDefinition.Metrics}.
 */
public class VirtualThreadPinningMetricsTestCase {

    @Test
    public void testUnrecorded() {
        VirtualThreadPinningResourceDefinition.Metrics metrics = new VirtualThreadPinningResourceDefinition.Metrics();
        VirtualThreadPinningResourceDefinition.ElapsedPinning elapsedPinning = metrics.getElapsedPinning();
        assertEquals(0, elapsedPinning.getCount());
        assertEquals(0, elapsedPinning.getTotalPinnedTime());
        assertEquals(0, elapsedPinning.getAveragePinnedTime()); // this is the point -- checks for divide by zero
    }

    @Test
    public void testOnePinning() {
        VirtualThreadPinningResourceDefinition.Metrics metrics = new VirtualThreadPinningResourceDefinition.Metrics();
        metrics.recordPinning(Duration.ofSeconds(1));

        VirtualThreadPinningResourceDefinition.ElapsedPinning elapsedPinning = metrics.getElapsedPinning();
        assertEquals(1, elapsedPinning.getCount());
        assertEquals(TimeUnit.SECONDS.toNanos(1), elapsedPinning.getTotalPinnedTime());
        assertEquals(TimeUnit.SECONDS.toNanos(1), elapsedPinning.getAveragePinnedTime());
    }

    @Test
    public void testTwoPinnings() {
        VirtualThreadPinningResourceDefinition.Metrics metrics = new VirtualThreadPinningResourceDefinition.Metrics();
        metrics.recordPinning(Duration.ofSeconds(1));
        metrics.recordPinning(Duration.ofNanos(100));


        VirtualThreadPinningResourceDefinition.ElapsedPinning elapsedPinning = metrics.getElapsedPinning();
        assertEquals(2, elapsedPinning.getCount());
        assertEquals(TimeUnit.SECONDS.toNanos(1)  + 100, elapsedPinning.getTotalPinnedTime());
        assertEquals(Math.toIntExact((TimeUnit.SECONDS.toNanos(1)  + 100)  / 2), elapsedPinning.getAveragePinnedTime());
    }
}
