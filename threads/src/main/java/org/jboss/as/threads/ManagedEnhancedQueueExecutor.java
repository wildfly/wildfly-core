package org.jboss.as.threads;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.jboss.msc.service.StopContext;
import org.jboss.threads.EnhancedQueueExecutor;

class ManagedEnhancedQueueExecutor extends ManagedExecutorServiceImpl
        implements ManagedQueueExecutorService,
                // implement the to-be-removed interfaces as well until uses are gone
                ManagedQueuelessExecutorService, ManagedJBossThreadPoolExecutorService {
    private final EnhancedQueueExecutor executor;

    private volatile int maxTasks;
    private volatile ReducableSemaphore semaphore;

    ManagedEnhancedQueueExecutor(EnhancedQueueExecutor executor, boolean blocking) {
        super(executor);
        this.executor = executor;
        if (blocking) {
            this.maxTasks = getMaxTasks(executor);
            this.semaphore = new ReducableSemaphore(maxTasks);
        } else {
            this.maxTasks = -1;
            this.semaphore = null;
        }
    }

    /**
     * Gets whether this executor is configured to block calls to
     * {@link #execute(Runnable)} until {@link #getMaxThreads() thread capacity}
     * or {@link #getQueueSize() queue capacity} is available to handle the
     * provided task.
     *
     * @return {@code true} if this executor support blocking semantics; {@code false} otherwise
     */
    public boolean isBlocking() {
        return maxTasks > 0;
    }

    /**
     * {@inheritDoc}
     * @see java.util.concurrent.Executor#execute(java.lang.Runnable)
     */
    @Override
    public void execute(Runnable command) {
        Semaphore gate = semaphore;

        if (gate != null) {
            try {
                gate.acquire();
                command = new WrappedRunnable(command, gate);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }

        this.executor.execute(command);
    }

    @Override
    void internalShutdown(StopContext stopContext) {
        executor.shutdown();
        if (semaphore != null) {
            Semaphore gate = semaphore;
            // Don't block subsequent calls to execute
            semaphore = null;
            boolean released = false;
            do {
                try {
                    // Let any blocked threads through, where they will be rejected
                    gate.release(Integer.MAX_VALUE - gate.availablePermits());
                    released = true;
                } catch (Throwable ignored) {
                    // release can throw an Error if a release call will result in > Integer.MAX_VALUE permits
                }
            } while (!released);
        }
        stopContext.complete();
    }

    @Override
    public int getCoreThreads() {
        return executor.getCorePoolSize();
    }

    // Package protected for subsys write-attribute handlers
    void setCoreThreads(int coreThreads) {
        executor.setCorePoolSize(coreThreads);
    }

    @Override
    public boolean isAllowCoreTimeout() {
        return executor.allowsCoreThreadTimeOut();
    }

    void setAllowCoreTimeout(boolean allowCoreTimeout) {
        executor.allowCoreThreadTimeOut(allowCoreTimeout);
    }

    @Override
    public int getMaxThreads() {
        return executor.getMaximumPoolSize();
    }

    void setMaxThreads(int maxThreads) {
        executor.setMaximumPoolSize(maxThreads);

        if (semaphore != null) {
            // Resize the semaphore if the max # of tasks has changed
            int oldMaxTasks = this.maxTasks;
            maxTasks = getMaxTasks(executor);

            if (maxTasks < oldMaxTasks) {
                semaphore.reducePermits(oldMaxTasks - maxTasks);
            } else if (maxTasks != oldMaxTasks) {
                // releasing permits we didn't acquire permanently increases the semaphore size
                semaphore.release(maxTasks - oldMaxTasks);
            } // else nothing changed so nothing to do
        }
    }

    @Override
    public long getKeepAlive() {
        return executor.getKeepAliveTime(TimeUnit.MILLISECONDS);
    }

    void setKeepAlive(TimeSpec keepAlive) {
        executor.setKeepAliveTime(keepAlive.getDuration(), keepAlive.getUnit());
    }

    @Override
    public int getRejectedCount() {
        return (int) executor.getRejectedTaskCount();
    }

    @Override
    public long getTaskCount() {
        return executor.getSubmittedTaskCount();
    }

    @Override
    public int getLargestThreadCount() {
        return executor.getLargestPoolSize();
    }

    @Override
    public int getLargestPoolSize() {
        return executor.getLargestPoolSize();
    }

    @Override
    public int getCurrentThreadCount() {
        return executor.getPoolSize();
    }

    @Override
    public long getCompletedTaskCount() {
        return executor.getCompletedTaskCount();
    }

    @Override
    public int getActiveCount() {
        return executor.getActiveCount();
    }

    @Override
    public int getQueueSize() {
        return executor.getQueueSize();
    }

    private static int getMaxTasks(EnhancedQueueExecutor executor) {
        return getMaxTasks(executor.getMaximumPoolSize(), executor.getMaximumQueueSize());
    }

    private static int getMaxTasks(int maxThreads, int queueLength) {
        return Integer.MAX_VALUE - queueLength >= maxThreads ? maxThreads + queueLength : Integer.MAX_VALUE;
    }

    private static class ReducableSemaphore extends Semaphore {

        ReducableSemaphore(int permits) {
            super(permits);
        }

        @Override
        public void reducePermits(int reduction) {
            super.reducePermits(reduction);
        }
    }

    private static class WrappedRunnable implements Runnable {
        private final Runnable wrapped;
        private final Semaphore gate;

        private WrappedRunnable(Runnable wrapped, Semaphore gate) {
            this.wrapped = wrapped;
            this.gate = gate;
        }

        @Override
        public void run() {
            try {
                wrapped.run();
            } finally {
                gate.release();
            }
        }
    }
}
