package org.wildfly.extension.requestcontroller;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

public class WFCORE4967_TestCase {
    private static final int TASKS_QTY = 10;
    private static final int THREADS_QTY = 20;

    @Test
    public void noQueuedTasksLossWhenRunningRequestCompleteOnSuspendedRC() throws InterruptedException {
        final AtomicInteger executedTaskCount = new AtomicInteger();

        RequestController rc = suspendedRCWithQueuedTasks(TASKS_QTY, () -> {
            executedTaskCount.incrementAndGet();
        });

        CountDownLatch latch = new CountDownLatch(THREADS_QTY);
        createSynchronisedThreads(latch, () -> {
            rc.requestComplete();
        }).forEach(Thread::start);
        latch.await();

        rc.resume();
        assertEquals(TASKS_QTY, executedTaskCount.intValue());
    }

    private RequestController suspendedRCWithQueuedTasks(int i, Runnable whenExecuted) {
        RequestController _ret = new RequestController(false);
        _ret.suspended(() -> {
        });

        for (int taskNo = 0; taskNo < TASKS_QTY; taskNo++) {
            _ret.queueTask(null, null, task -> whenExecuted.run(), 0, null, false, false);
        }
        return _ret;
    }

    private List<Thread> createSynchronisedThreads(CountDownLatch latch, Runnable action) {
        int threadsQty = (int) latch.getCount();
        List<Thread> _ret = new ArrayList<>(threadsQty);
        for (int threadNo = 0; threadNo < threadsQty; threadNo++) {
            _ret.add(new Thread(() -> {
                // wait for all threads to initialise
                try {
                    latch.countDown();
                    latch.await();
                } catch (Exception e) {
                    /* yummy */}

                // run all of them simultaneously
                action.run();
            }));
        }
        return _ret;
    }
}
