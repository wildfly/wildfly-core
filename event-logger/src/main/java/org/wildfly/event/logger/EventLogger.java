/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2019 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
}
