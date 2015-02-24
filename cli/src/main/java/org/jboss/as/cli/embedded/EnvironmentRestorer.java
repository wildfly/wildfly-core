/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
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

package org.jboss.as.cli.embedded;

import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.LogContextSelector;
import org.jboss.stdio.SimpleStdioContextSelector;
import org.jboss.stdio.StdioContext;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * Restores the environment follow a shutdown or failed launch of an embedded server.
 *
 * @author Brian Stansberry (c) 2015 Red Hat Inc.
 */
class EnvironmentRestorer {

    private final String jbossHome = WildFlySecurityManager.getPropertyPrivileged("jboss.home.dir", null);
    private final String bootLog = WildFlySecurityManager.getPropertyPrivileged("org.jboss.boot.log.file", null);
    private final Contexts defaultContexts = new Contexts(LogContext.getLogContext(), StdioContext.getStdioContext());
    private boolean logContextSelectorRestored;

    Contexts getDefaultContexts() {
        return defaultContexts;
    }

    synchronized void restoreLogContextSelector() {
        if (!logContextSelectorRestored) {
            final LogContext logContext = defaultContexts.getLogContext();
            if (logContext == LogContext.getSystemLogContext()) {
                LogContext.setLogContextSelector(LogContext.DEFAULT_LOG_CONTEXT_SELECTOR);
            } else {
                LogContext.setLogContextSelector(new LogContextSelector() {
                    @Override
                    public LogContext getLogContext() {
                        return logContext;
                    }
                });
            }
            logContextSelectorRestored = true;
        }
    }

    void restoreEnvironment() {
        if (jbossHome == null) {
            WildFlySecurityManager.clearPropertyPrivileged("jboss.home.dir");
        } else {
            WildFlySecurityManager.setPropertyPrivileged("jboss.home.dir", jbossHome);
        }
        if (bootLog == null) {
            WildFlySecurityManager.clearPropertyPrivileged("org.jboss.boot.log.file");
        } else {
            WildFlySecurityManager.setPropertyPrivileged("org.jboss.boot.log.file", bootLog);
        }
        StdioContext.setStdioContextSelector(new SimpleStdioContextSelector(defaultContexts.getStdioContext()));
        restoreLogContextSelector();
    }
}
