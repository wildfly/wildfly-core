/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.client;

import static org.jboss.as.controller.client.logging.ControllerClientLogger.ROOT_LOGGER;

/**
 * An operation message handler for handling progress reports.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface OperationMessageHandler {
    /**
     * Handle an operation progress report.
     *
     * @param severity the severity of the message
     * @param message the message
     */
    void handleReport(MessageSeverity severity, String message);

    /**
     * An operation message handler which logs to the current system log.
     */
    OperationMessageHandler logging = new OperationMessageHandler() {

        public void handleReport(final MessageSeverity severity, final String message) {
            switch (severity) {
                case ERROR:
                    ROOT_LOGGER.error(message);
                    break;
                case WARN:
                    ROOT_LOGGER.warn(message);
                    break;
                case INFO:
                default:
                    ROOT_LOGGER.trace(message);
                    break;
            }
        }
    };


    /**
     * A noop operation message handler, which discards all received messages.
     */
    OperationMessageHandler DISCARD = new OperationMessageHandler() {

        @Override
        public void handleReport(MessageSeverity severity, String message) {
            //
        }
    };

}
