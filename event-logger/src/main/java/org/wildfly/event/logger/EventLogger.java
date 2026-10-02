/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.event.logger;

import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * A logger for various events such as access or audit logging.
 * <p>
 * Note that a {@linkplain #getEventSource() event source} is an arbitrary string used to differentiate logging events.
 * For example a web access event may have an even source of {@code web-access}.
 * </p>
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@SuppressWarnings({"StaticMethodOnlyUsedInOneClass", "unused", "UnusedReturnValue"})
public interface EventLogger {

    /**
     * Creates a new logger which defaults to writing {@linkplain JsonEventFormatter JSON} to
     * {@link StdoutEventWriter stdout}.
     *
     * @param eventSource the identifier for the source of the event this logger is used for
     *
     * @return a new event logger
     */
    static EventLogger createLogger(final String eventSource) {
        return new StandardEventLogger(eventSource, StdoutEventWriter.of(JsonEventFormatter.builder().build()));
    }

    /**
     * Creates a new event logger.
     *
     * @param eventSource the identifier for the source of the event this logger is used for
     * @param writer      the writer this logger will write to
     *
     * @return a new event logger
     */
    static EventLogger createLogger(final String eventSource, final EventWriter writer) {
        return new StandardEventLogger(eventSource, writer);
    }

    /**
     * Creates a new asynchronous logger  which defaults to writing {@linkplain JsonEventFormatter JSON} to
     * {@link StdoutEventWriter stdout}.
     *
     * @param eventSource the identifier for the source of the event this logger is used for
     * @param executor    the executor to execute the threads in
     *
     * @return the new event logger
     */
    static EventLogger createAsyncLogger(final String eventSource, final Executor executor) {
        return new AsyncEventLogger(eventSource, StdoutEventWriter.of(JsonEventFormatter.builder().build()), executor);
    }

    /**
     * Creates a new asynchronous event logger.
     *
     * @param eventSource the identifier for the source of the event this logger is used for
     * @param writer      the writer this logger will write to
     * @param executor    the executor to execute the threads in
     *
     * @return a new event logger
     */
    static EventLogger createAsyncLogger(final String eventSource, final EventWriter writer, final Executor executor) {
        return new AsyncEventLogger(eventSource, writer, executor);
    }

    /**
     * Creates a new asynchronous event logger with a specified queue capacity.
     * <p>
     * Note that the background drain loop processes up to 1000 events per pass.
     * A queue capacity below 1000 leaves less than one cycle of headroom before
     * arriving events begin to be dropped if the writer stalls.
     * </p>
     *
     * @param eventSource   the identifier for the source of the event this logger is used for
     * @param writer        the writer this logger will write to
     * @param executor      the executor to execute the threads in
     * @param queueCapacity the maximum number of pending events before new arrivals are dropped; must be at least 1
     *
     * @return a new event logger
     * @throws IllegalArgumentException if {@code queueCapacity} is less than 1
     */
    static EventLogger createAsyncLogger(final String eventSource, final EventWriter writer, final Executor executor, final int queueCapacity) {
        return new AsyncEventLogger(eventSource, writer, executor, queueCapacity);
    }

    /**
     * Logs the event.
     *
     * @param event the event to log
     *
     * @return this logger
     */
    EventLogger log(Map<String, Object> event);

    /**
     * Logs the event.
     * <p>
     * The supplier can lazily load the data. Note that in the cases of an
     * {@linkplain #createAsyncLogger(String, Executor) asynchronous logger} the {@linkplain Supplier#get() data} will
     * be retrieved in a different thread.
     * </p>
     *
     * @param event the event to log
     *
     * @return this logger
     */
    EventLogger log(Supplier<Map<String, Object>> event);

    /**
     * Returns the source of event this logger is logging.
     *
     * @return the event source
     */
    String getEventSource();

    /**
     * Returns the number of events that were dropped by this logger.
     *
     * <p>For synchronous loggers this always returns {@code 0}. For asynchronous
     * loggers ({@link #createAsyncLogger}) this returns the total number of events
     * that could not be processed because the internal queue was full or because
     * the logger had already been closed.
     *
     * @return the number of dropped events
     */
    default long getDroppedCount() {
        return 0L;
    }

    /**
     * Stops this logger, draining any buffered events before returning.
     *
     * <p>For synchronous loggers this is a no-op.  For asynchronous loggers
     * ({@link #createAsyncLogger}) it drains the pending queue on the calling
     * thread so that no events are lost during an orderly shutdown.
     *
     * <p>After {@code close()} returns, calls to {@link #log} are silently
     * discarded.
     */
    default void close() {
        // no-op for synchronous loggers
    }
}
