/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
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
