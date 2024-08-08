/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.suspend;

import static org.junit.Assert.assertTrue;

import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests of {@link ServerSuspendController}.
 */
public class SuspendControllerTestCase {

    /**
     * Tests that ServerActivities in different execution groups are executed in the correct order
     * regardless of the order in which they are registered.
     */
    @Test
    public void testServerActivityCallbackOrder() {
        serverActivityCallbackOrderTest(CounterActivity.ONE, CounterActivity.TWO, CounterActivity.THREE, CounterActivity.FOUR, CounterActivity.FIVE, CounterActivity.SIX);
        serverActivityCallbackOrderTest(CounterActivity.SIX, CounterActivity.FIVE, CounterActivity.FOUR, CounterActivity.THREE, CounterActivity.TWO, CounterActivity.ONE);
        serverActivityCallbackOrderTest(CounterActivity.SIX, CounterActivity.ONE, CounterActivity.FIVE, CounterActivity.TWO, CounterActivity.THREE, CounterActivity.FOUR);
        serverActivityCallbackOrderTest(CounterActivity.ONE, CounterActivity.THREE, CounterActivity.TWO, CounterActivity.FOUR, CounterActivity.FIVE, CounterActivity.SIX);
    }

    private void serverActivityCallbackOrderTest(CounterActivity... activities) {
        SuspendController testee = new SuspendController();
        Assert.assertSame(ServerSuspendController.State.SUSPENDED, testee.getState());
        testee.resume();
        Assert.assertSame(ServerSuspendController.State.RUNNING, testee.getState());

        NavigableSet<CounterActivity> activitySet = new TreeSet<>();
        for (CounterActivity activity : activities) {
            testee.registerActivity(activity);
            activitySet.add(activity);
        }

        Assert.assertSame(ServerSuspendController.State.RUNNING, testee.getState());
        testee.suspend(-1);
        Assert.assertSame(ServerSuspendController.State.SUSPENDED, testee.getState());
        testee.resume();
        Assert.assertSame(ServerSuspendController.State.RUNNING, testee.getState());

        orderCheck(activitySet);

        // Randomly unregister some activities
        for (CounterActivity activity : activities) {
            if (Math.random() < 0.5) {
                testee.unRegisterActivity(activity);
                assertTrue(activitySet.remove(activity));
            }
        }

        Assert.assertSame(ServerSuspendController.State.RUNNING, testee.getState());
        testee.suspend(-1);
        Assert.assertSame(ServerSuspendController.State.SUSPENDED, testee.getState());
        testee.resume();
        Assert.assertSame(ServerSuspendController.State.RUNNING, testee.getState());

        orderCheck(activitySet);
    }

    private void orderCheck(NavigableSet<CounterActivity> activities) {
        CounterActivity lastPresuspend = null;
        CounterActivity lastGroup = null;
        for (CounterActivity activity : activities) {
            if (lastPresuspend == null) {
                // First item. Just confirm preSuspend was called
                assertTrue(activity.getId(), activity.preSuspend > -1);
            } else if (activity.executionGroup != lastPresuspend.executionGroup) {
                assertTrue(activity.getId(), activity.preSuspend > lastPresuspend.preSuspend);
                lastGroup = lastPresuspend;
            } else if (lastGroup != null) {
                assertTrue(activity.getId(), activity.preSuspend > lastGroup.preSuspend);
            } else {
                // Just confirm preSuspend was called
                assertTrue(activity.getId(), activity.preSuspend > -1);
            }
            lastPresuspend = activity;
        }
        CounterActivity lastSuspended = null;
        lastGroup = null;
        for (CounterActivity activity : activities) {
            if (lastSuspended == null) {
                // First suspended is after all preSuspends
                assertTrue(activity.getId(), activity.suspended > lastPresuspend.preSuspend);
            } else if (activity.executionGroup != lastSuspended.executionGroup) {
                assertTrue(activity.getId(), activity.suspended > lastSuspended.suspended);
                lastGroup = lastSuspended;
            } else if (lastGroup != null) {
                assertTrue(activity.getId(), activity.suspended > lastGroup.suspended);
            } else {
                // Just confirm suspended was called
                assertTrue(activity.getId(), activity.suspended > -1);
            }
            lastSuspended = activity;
        }

        Set<CounterActivity> reversed = activities.descendingSet();
        CounterActivity lastResume = null;
        lastGroup = null;
        for (CounterActivity activity : reversed) {
            if (lastResume == null) {
                assertTrue(activity.getId(), activity.resume > lastSuspended.suspended);
            } else if (activity.executionGroup != lastResume.executionGroup) {
                assertTrue(activity.getId(), activity.resume > lastResume.resume);
                lastGroup = lastResume;
            } else if (lastGroup != null) {
                assertTrue(activity.getId(), activity.resume > lastGroup.resume);
            } else {
                // Just confirm resume was called
                assertTrue(activity.getId(), activity.resume > -1);
            }
            lastResume = activity;
        }

        // Reset for the next check
        for (CounterActivity activity : activities) {
            activity.resetCounter();
        }
    }

    private static class CounterActivity implements ServerActivity, Comparable<CounterActivity> {
        private static final AtomicInteger invocationCounter = new AtomicInteger();

        private static final CounterActivity ONE = new CounterActivity(1,1);
        private static final CounterActivity TWO = new CounterActivity(2,1);
        private static final CounterActivity THREE = new CounterActivity(3,2);
        private static final CounterActivity FOUR = new CounterActivity(4,2);
        private static final CounterActivity FIVE = new CounterActivity(5,3);
        private static final CounterActivity SIX = new CounterActivity(6,3);

        private final Integer id;
        private final int executionGroup;
        private volatile int preSuspend = -1;
        private volatile int suspended = -1;
        private volatile int resume = -1;

        private CounterActivity(int id, int executionGroup) {
            this.id = id;
            this.executionGroup = executionGroup;
        }

        @Override
        public int getExecutionGroup() {
            return executionGroup;
        }

        @Override
        public void preSuspend(ServerActivityCallback listener) {
            preSuspend = invocationCounter.getAndIncrement();
            listener.done();
        }

        @Override
        public void suspended(ServerActivityCallback listener) {
            suspended = invocationCounter.getAndIncrement();
            listener.done();
        }

        @Override
        public void resume() {
            resume = invocationCounter.getAndIncrement();
        }

        @Override
        public int compareTo(CounterActivity o) {
            int diff = executionGroup - o.executionGroup;
            return diff != 0 ? diff : ((this.equals(o)) ? 0 : id - o.id);
        }

        String getId() {
            return id.toString();
        }

        void resetCounter() {
            preSuspend = suspended = resume = -1;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CounterActivity activity = (CounterActivity) o;
            return Objects.equals(id, activity.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }
    }
}
