/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
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

package org.jboss.as.controller;

import javax.security.auth.Subject;

import org.jboss.as.controller.security.ControllerPermission;
import org.jboss.as.core.security.AccessMechanism;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * The context used to store state related to access control and auditing for the current invocation.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class AccessAuditContext {

    private static ThreadLocal<AccessAuditContext> contextThreadLocal = new ThreadLocal<AccessAuditContext>();

    private String domainUuid;
    private AccessMechanism accessMechanism;
    private boolean domainRollout;

    private AccessAuditContext() {
        // This can only be instantiated as part of the doAs call.
    }

    /**
     * Gets the unique identifier for a multi-domain-process operation.
     *
     * @return the identifier, or {@code null} if this context does not relate to a multi-domain-process operation
     */
    public String getDomainUuid() {
        return domainUuid;
    }

    public void setDomainUuid(String domainUuid) {
        this.domainUuid = domainUuid;
    }

    /**
     * Gets the mechanism via which the user initiated the access.
     *
     * @return the mechanism, or {@code null} if the access was initiated internally
     */
    public AccessMechanism getAccessMechanism() {
        return accessMechanism;
    }

    public void setAccessMechanism(AccessMechanism accessMechanism) {
        this.accessMechanism = accessMechanism;
    }

    /**
     * Gets whether this context relates to a secondary request initiated by a remote Host Controller
     * process as part of its rollout of an operation initiated on that process.
     *
     * @return {@code true} if this context relates to a remotely coordinated multi-process domain operation
     */
    public boolean isDomainRollout() {
        return domainRollout;
    }

    public void setDomainRollout(boolean domainRollout) {
        this.domainRollout = domainRollout;
    }

    /**
     * Obtain the current {@link AccessAuditContext} or {@code null} if none currently set.
     *
     * @return The current {@link AccessAuditContext}
     * @deprecated Internal use, will be changed without warning at any time.
     */
    @Deprecated
    public static AccessAuditContext currentAccessAuditContext() {
        if (WildFlySecurityManager.isChecking()) {
            System.getSecurityManager().checkPermission(ControllerPermission.GET_CURRENT_ACCESS_AUDIT_CONTEXT);
        }
        return contextThreadLocal.get();
    }

    /**
     * Perform work with a new {@code AccessAuditContext} as a particular {@code Subject}
     * @param subject the {@code Subject} that the specified {@code action} will run as. May be {@code null}
     * @param action the work to perform. Cannot be {@code null}
     * @param <T> the type of teh return value
     * @return the value returned by the PrivilegedAction's <code>run</code> method
     *
     * @exception NullPointerException if the specified
     *                  <code>PrivilegedExceptionAction</code> is
     *                  <code>null</code>.
     *
     * @exception SecurityException if the caller does not have permission
     *                  to invoke this method.
     */
    public static <T> T doAs(final Subject subject, final java.security.PrivilegedAction<T> action) {
        final AccessAuditContext previous = contextThreadLocal.get();
        try {
            contextThreadLocal.set(new AccessAuditContext());
            return Subject.doAs(subject, action);
        } finally {
            contextThreadLocal.set(previous);
        }
    }

    /**
     * Perform work with a new {@code AccessAuditContext} as a particular {@code Subject}
     * @param subject the {@code Subject} that the specified {@code action} will run as. May be {@code null}
     * @param action the work to perform. Cannot be {@code null}
     * @param <T> the type of teh return value
     * @return the value returned by the PrivilegedAction's <code>run</code> method
     *
     * @exception java.security.PrivilegedActionException if the
     *                  <code>PrivilegedExceptionAction.run</code>
     *                  method throws a checked exception.
     *
     * @exception NullPointerException if the specified
     *                  <code>PrivilegedExceptionAction</code> is
     *                  <code>null</code>.
     *
     * @exception SecurityException if the caller does not have permission
     *                  to invoke this method.
     */
    public static <T> T doAs(Subject subject, java.security.PrivilegedExceptionAction<T> action)
            throws java.security.PrivilegedActionException {
        final AccessAuditContext previous = contextThreadLocal.get();
        try {
            contextThreadLocal.set(new AccessAuditContext());
            return Subject.doAs(subject, action);
        } finally {
            contextThreadLocal.set(previous);
        }
    }

}
