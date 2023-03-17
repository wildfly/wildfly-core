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
        List<Thread> threads = createSynchronisedThreads(latch, () -> {
            rc.requestComplete();
        });
        threads.forEach(Thread::start);
        // wait until all above threads ready to fire rc.requestComplete() together with bellow rc.resume()
        latch.await();
        rc.resume();

        for (Thread t : threads) {
            t.join();
        }

        // simulate just enough requests after server is resumed to drain potential outstanding tasks from taskQueue
        for (int requestNo = 0; requestNo < TASKS_QTY; requestNo++) {
            rc.requestComplete();
        }

        assertEquals(TASKS_QTY, executedTaskCount.intValue());
    }

    private RequestController suspendedRCWithQueuedTasks(int i, Runnable whenExecuted) {
        RequestController requestController = new RequestController(false, () -> null);
        requestController.suspended(() -> {
        });

        for (int taskNo = 0; taskNo < TASKS_QTY; taskNo++) {
            requestController.queueTask(null, null, task -> whenExecuted.run(), 0, null, false, false);
        }
        return requestController;
    }

    private List<Thread> createSynchronisedThreads(CountDownLatch latch, Runnable action) {
        int threadsQty = (int) latch.getCount();
        List<Thread> threads = new ArrayList<>(threadsQty);
        for (int threadNo = 0; threadNo < threadsQty; threadNo++) {
            threads.add(new Thread(() -> {
                // wait for all threads to initialise
                try {
                    latch.countDown();
                    latch.await();
                } catch (InterruptedException e) {
                    return;
                }

                // run all of them simultaneously
                action.run();
            }));
        }
        return threads;
    }
}
