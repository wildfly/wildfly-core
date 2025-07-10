/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.suspend;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;

import org.jboss.as.server.suspend.ServerSuspendController.Context;
import org.jboss.as.server.suspend.SuspendableActivityRegistry.SuspendPriority;
import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests of {@link ServerSuspendController}.
 */
public class SuspendControllerTestCase {

    /**
     * Verify prepare/suspend stages of {@link ServerSuspendController#suspend(ServerSuspendContext)}.
     */
    @Test
    public void suspend() {
        SuspendController controller = new SuspendController();
        // For the sake of test simplification, bypass auto-suspend on activity registration by resuming server
        controller.resume(Context.STARTUP);

        SuspendableActivity activity = mock(SuspendableActivity.class);
        ServerSuspendContext suspendContext = mock(ServerSuspendContext.class);

        CompletableFuture<Void> prepare = new CompletableFuture<>();
        CompletableFuture<Void> suspend = new CompletableFuture<>();

        doReturn(prepare).when(activity).prepare(suspendContext);
        doReturn(suspend).when(activity).suspend(suspendContext);

        controller.registerActivity(activity);

        CompletableFuture<Void> result = controller.suspend(suspendContext).toCompletableFuture();

        verify(activity).prepare(suspendContext);
        verifyNoMoreInteractions(activity);

        Assert.assertFalse(result.isDone());
        Assert.assertFalse(result.isCancelled());
        Assert.assertFalse(result.isCompletedExceptionally());

        prepare.complete(null);

        verify(activity).suspend(suspendContext);
        verifyNoMoreInteractions(activity);

        Assert.assertFalse(result.isDone());
        Assert.assertFalse(result.isCancelled());
        Assert.assertFalse(result.isCompletedExceptionally());

        suspend.complete(null);

        result.getNow(null);

        verifyNoMoreInteractions(activity);

        Assert.assertTrue(result.isDone());
        Assert.assertFalse(result.isCancelled());
        Assert.assertFalse(result.isCompletedExceptionally());
    }

    /**
     * Verify that cancelling the future returned via {@link SuspendController#suspend(ServerSuspendContext)} also cancels the future returned by {@link SuspendableActivity#prepare(ServerSuspendContext)}.
     */
    @Test
    public void cancelPrepare() {
        SuspendController controller = new SuspendController();
        // For the sake of test simplification, bypass auto-suspend on activity registration by resuming server
        controller.resume(Context.STARTUP);

        SuspendableActivity activity = mock(SuspendableActivity.class);
        ServerSuspendContext suspendContext = mock(ServerSuspendContext.class);

        CompletableFuture<Void> prepare = new CompletableFuture<>();
        CompletableFuture<Void> suspend = new CompletableFuture<>();

        doReturn(prepare).when(activity).prepare(suspendContext);
        doReturn(suspend).when(activity).suspend(suspendContext);

        controller.registerActivity(activity);

        CompletableFuture<Void> result = controller.suspend(suspendContext).toCompletableFuture();

        verify(activity).prepare(suspendContext);
        verifyNoMoreInteractions(activity);

        Assert.assertFalse(result.isDone());
        Assert.assertFalse(result.isCancelled());
        Assert.assertFalse(result.isCompletedExceptionally());

        Assert.assertTrue(result.cancel(false));

        Assert.assertTrue(result.isDone());
        Assert.assertTrue(result.isCancelled());
        Assert.assertTrue(result.isCompletedExceptionally());
        Assert.assertThrows(CancellationException.class, result::join);

        // Verify that cancellation propagates to future returned by ServerActivity
        Assert.assertTrue(prepare.isDone());
        Assert.assertTrue(prepare.isCancelled());
        Assert.assertTrue(prepare.isCompletedExceptionally());
        Assert.assertThrows(CancellationException.class, prepare::join);
    }

    /**
     * Verify that cancelling the future returned via {@link SuspendController#suspend(ServerSuspendContext)} also cancels the future returned by {@link SuspendableActivity#suspend(ServerSuspendContext)}.
     */
    @Test
    public void cancelSuspend() {
        SuspendController controller = new SuspendController();
        // For the sake of test simplification, bypass auto-suspend on activity registration by resuming server
        controller.resume(Context.STARTUP);

        SuspendableActivity activity = mock(SuspendableActivity.class);
        ServerSuspendContext suspendContext = mock(ServerSuspendContext.class);

        CompletableFuture<Void> prepare = new CompletableFuture<>();
        CompletableFuture<Void> suspend = new CompletableFuture<>();

        doReturn(prepare).when(activity).prepare(suspendContext);
        doReturn(suspend).when(activity).suspend(suspendContext);

        controller.registerActivity(activity);

        CompletableFuture<Void> result = controller.suspend(suspendContext).toCompletableFuture();

        verify(activity).prepare(suspendContext);
        verifyNoMoreInteractions(activity);

        Assert.assertFalse(result.isDone());
        Assert.assertFalse(result.isCancelled());
        Assert.assertFalse(result.isCompletedExceptionally());

        prepare.complete(null);

        verify(activity).suspend(suspendContext);
        verifyNoMoreInteractions(activity);

        Assert.assertFalse(result.isDone());
        Assert.assertFalse(result.isCancelled());
        Assert.assertFalse(result.isCompletedExceptionally());

        Assert.assertTrue(result.cancel(false));

        Assert.assertTrue(result.isDone());
        Assert.assertTrue(result.isCancelled());
        Assert.assertTrue(result.isCompletedExceptionally());
        Assert.assertThrows(CancellationException.class, result::join);

        // Verify that cancellation propagates to future returned by ServerActivity
        Assert.assertTrue(suspend.isDone());
        Assert.assertTrue(suspend.isCancelled());
        Assert.assertTrue(suspend.isCompletedExceptionally());
        Assert.assertThrows(CancellationException.class, suspend::join);
    }

    /**
     * Verify that {@link ServerSuspendController#resume(ServerResumeContext)} cancels the future returned by a previous {@link SuspendController#suspend(ServerResumeContext)}.
     */
    @Test
    public void resume() {
        SuspendController controller = new SuspendController();
        // For the sake of test simplification, bypass auto-suspend on activity registration by resuming server
        controller.resume(Context.STARTUP);

        SuspendableActivity activity = mock(SuspendableActivity.class);
        ServerSuspendContext suspendContext = mock(ServerSuspendContext.class);
        ServerResumeContext resumeContext = mock(ServerResumeContext.class);

        CompletableFuture<Void> prepare = new CompletableFuture<>();
        CompletableFuture<Void> suspend = new CompletableFuture<>();
        CompletableFuture<Void> resume = new CompletableFuture<>();

        doReturn(prepare).when(activity).prepare(suspendContext);
        doReturn(suspend).when(activity).suspend(suspendContext);
        doReturn(resume).when(activity).resume(resumeContext);

        controller.registerActivity(activity);

        CompletableFuture<Void> activeSuspend = controller.suspend(suspendContext).toCompletableFuture();

        verify(activity).prepare(suspendContext);
        verifyNoMoreInteractions(activity);

        Assert.assertFalse(activeSuspend.isDone());
        Assert.assertFalse(activeSuspend.isCancelled());
        Assert.assertFalse(activeSuspend.isCompletedExceptionally());

        // Complete prepare, but leave suspend incomplete
        prepare.complete(null);

        verify(activity).suspend(suspendContext);
        verifyNoMoreInteractions(activity);

        CompletableFuture<Void> result = controller.resume(resumeContext).toCompletableFuture();

        verify(activity).resume(resumeContext);
        verifyNoMoreInteractions(activity);

        Assert.assertFalse(resume.isDone());
        Assert.assertFalse(resume.isCancelled());
        Assert.assertFalse(resume.isCompletedExceptionally());

        // Verify cancellation of active suspend
        Assert.assertTrue(activeSuspend.isDone());
        Assert.assertTrue(activeSuspend.isCancelled());
        Assert.assertTrue(activeSuspend.isCompletedExceptionally());
        Assert.assertThrows(CancellationException.class, activeSuspend::join);

        Assert.assertTrue(suspend.isDone());
        Assert.assertTrue(suspend.isCancelled());
        Assert.assertTrue(suspend.isCompletedExceptionally());
        Assert.assertThrows(CancellationException.class, suspend::join);

        resume.complete(null);

        Assert.assertTrue(result.isDone());
        Assert.assertFalse(result.isCancelled());
        Assert.assertFalse(result.isCompletedExceptionally());
    }

    /**
     * Verify that cancelling the future returned via {@link SuspendController#resume(ServerResumeContext)} also cancels the future returned by {@link SuspendableActivity#resume(ServerResumeContext)}.
     */
    @Test
    public void cancelResume() {
        SuspendController controller = new SuspendController();
        // For the sake of test simplification, bypass auto-suspend on activity registration by resuming server
        controller.resume(Context.STARTUP);

        SuspendableActivity activity = mock(SuspendableActivity.class);
        ServerSuspendContext suspendContext = mock(ServerSuspendContext.class);
        ServerResumeContext resumeContext = mock(ServerResumeContext.class);

        CompletableFuture<Void> prepare = new CompletableFuture<>();
        CompletableFuture<Void> suspend = new CompletableFuture<>();
        CompletableFuture<Void> resume = new CompletableFuture<>();

        doReturn(prepare).when(activity).prepare(suspendContext);
        doReturn(suspend).when(activity).suspend(suspendContext);
        doReturn(resume).when(activity).resume(resumeContext);

        controller.registerActivity(activity);

        // First suspend server so we can resume it
        CompletableFuture<Void> activeSuspend = controller.suspend(suspendContext).toCompletableFuture();

        verify(activity).prepare(suspendContext);
        verifyNoMoreInteractions(activity);

        Assert.assertFalse(activeSuspend.isDone());
        Assert.assertFalse(activeSuspend.isCancelled());
        Assert.assertFalse(activeSuspend.isCompletedExceptionally());

        prepare.complete(null);

        verify(activity).suspend(suspendContext);
        verifyNoMoreInteractions(activity);

        suspend.complete(null);

        Assert.assertTrue(activeSuspend.isDone());
        Assert.assertFalse(activeSuspend.isCancelled());
        Assert.assertFalse(activeSuspend.isCompletedExceptionally());

        CompletableFuture<Void> result = controller.resume(resumeContext).toCompletableFuture();

        verify(activity).resume(resumeContext);
        verifyNoMoreInteractions(activity);

        Assert.assertFalse(resume.isDone());
        Assert.assertFalse(resume.isCancelled());
        Assert.assertFalse(resume.isCompletedExceptionally());

        Assert.assertTrue(result.cancel(false));

        Assert.assertTrue(result.isDone());
        Assert.assertTrue(result.isCancelled());
        Assert.assertTrue(result.isCompletedExceptionally());
        Assert.assertThrows(CancellationException.class, result::join);

        // Verify that cancellation propagates to future returned by ServerActivity
        Assert.assertTrue(resume.isDone());
        Assert.assertTrue(result.isCancelled());
        Assert.assertTrue(result.isCompletedExceptionally());
        Assert.assertThrows(CancellationException.class, result::join);
    }

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
            testee.registerActivity(activity, SuspendPriority.of(activity.executionGroup));
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
                testee.unregisterActivity(activity);
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

    private static class CounterActivity implements SuspendableActivity, Comparable<CounterActivity> {
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
        public CompletionStage<Void> prepare(ServerSuspendContext context) {
            return CompletableFuture.runAsync(this::preSuspend);
        }

        @Override
        public CompletionStage<Void> suspend(ServerSuspendContext context) {
            return CompletableFuture.runAsync(this::suspended);
        }

        @Override
        public CompletionStage<Void> resume(ServerResumeContext context) {
            return CompletableFuture.runAsync(this::resume);
        }

        private void preSuspend() {
            this.preSuspend = invocationCounter.getAndIncrement();
        }

        private void suspended() {
            this.suspended = invocationCounter.getAndIncrement();
        }

        private void resume() {
            this.resume = invocationCounter.getAndIncrement();
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
