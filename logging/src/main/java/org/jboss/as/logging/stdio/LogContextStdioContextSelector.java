/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.logging.stdio;

import java.security.AccessController;
import java.security.PrivilegedAction;

import org.jboss.as.logging.CommonAttributes;
import org.jboss.logmanager.Level;
import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.Logger;
import org.jboss.logmanager.Logger.AttachmentKey;
import org.jboss.stdio.LoggingOutputStream;
import org.jboss.stdio.NullInputStream;
import org.jboss.stdio.StdioContext;
import org.jboss.stdio.StdioContextSelector;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class LogContextStdioContextSelector implements StdioContextSelector {

    private static final AttachmentKey<StdioContext> STDIO_CONTEXT_ATTACHMENT_KEY = new AttachmentKey<>();

    public LogContextStdioContextSelector(final StdioContext defaultContext) {
        // Register the STDIO context on the default log context
        LogContext.getLogContext().getLogger(CommonAttributes.ROOT_LOGGER_NAME).attachIfAbsent(STDIO_CONTEXT_ATTACHMENT_KEY, defaultContext);
    }

    @Override
    public StdioContext getStdioContext() {
        final LogContext logContext = LogContext.getLogContext();
        final Logger root = logContext.getLogger(CommonAttributes.ROOT_LOGGER_NAME);
        StdioContext stdioContext = root.getAttachment(STDIO_CONTEXT_ATTACHMENT_KEY);
        if (stdioContext == null) {
            // Create the StdioContext
            stdioContext = createContext(logContext);
            final StdioContext appearing = attachIfAbsent(root, stdioContext);
            if (appearing != null) {
                stdioContext = appearing;
            }
        }
        return stdioContext;
    }

    private static StdioContext createContext(final LogContext logContext) {
        if (System.getSecurityManager() == null) {
            return StdioContext.create(
                    new NullInputStream(),
                    new LoggingOutputStream(logContext.getLogger("stdout"), Level.INFO),
                    new LoggingOutputStream(logContext.getLogger("stderr"), Level.ERROR)
            );
        }

        // Create the StdioContext using a privileged action. StdioContext.create() requires the "createStdioContext"
        // RuntimePermission. If a deployment or a dependency of the deployment attempts to write to System.out or
        // System.err with the security manager enabled then this could throw a security exception without the
        // privileged action. We should not block writing to those streams as they could be "logging" an important
        // error.
        return AccessController.doPrivileged((PrivilegedAction<StdioContext>) () -> StdioContext.create(
                new NullInputStream(),
                new LoggingOutputStream(logContext.getLogger("stdout"), Level.INFO),
                new LoggingOutputStream(logContext.getLogger("stderr"), Level.ERROR)
        ));
    }

    private static StdioContext attachIfAbsent(final Logger logger, final StdioContext context) {
        if (System.getSecurityManager() == null) {
            return logger.attachIfAbsent(STDIO_CONTEXT_ATTACHMENT_KEY, context);
        }
        return AccessController.doPrivileged((PrivilegedAction<StdioContext>) () ->
                logger.attachIfAbsent(STDIO_CONTEXT_ATTACHMENT_KEY, context));
    }
}
