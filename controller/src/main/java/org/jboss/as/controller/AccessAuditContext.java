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

import java.net.InetAddress;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;

import org.jboss.as.controller.security.ControllerPermission;
import org.jboss.as.core.security.AccessMechanism;
import org.wildfly.security.auth.server.SecurityDomain;
import org.wildfly.security.auth.server.SecurityIdentity;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * The context used to store state related to access control and auditing for the current invocation.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class AccessAuditContext {

    private static ThreadLocal<AccessAuditContext> contextThreadLocal = new ThreadLocal<AccessAuditContext>();

    private final boolean inflowed;
    private final SecurityIdentity securityIdentity;
    private final InetAddress remoteAddress;
    private String domainUuid;
    private AccessMechanism accessMechanism;
    private boolean domainRollout;

    private AccessAuditContext(final boolean inflowed, final SecurityIdentity securityIdentity, final InetAddress remoteAddress, final AccessAuditContext previous) {
        // This can only be instantiated as part of the doAs call.
        this.securityIdentity = securityIdentity;
        // The address would be set on the first context in the stack so use it.
        if (previous != null) {
            domainUuid = previous.domainUuid;
            accessMechanism = previous.accessMechanism;
            domainRollout = previous.domainRollout;
            this.remoteAddress = previous.remoteAddress;
            this.inflowed = previous.inflowed;
        } else {
            this.inflowed = inflowed;
            this.remoteAddress = remoteAddress;
        }

        // This is checked here so code can not obtain a reference to an AccessAuditContext with an inflowed identity and then
        // use it swap in any arbitrary identity.
        if (this.inflowed && WildFlySecurityManager.isChecking()) {
            System.getSecurityManager().checkPermission(ControllerPermission.INFLOW_SECURITY_IDENTITY);
        }
    }

    /**
     * Get the {@link SecurityIdentity} associated with this {@link AccessAuditContext}.
     *
     * This provides a way for the {@link SecurityIdentity} to be passed without the underlying {@link SecurityDomain} being known.
     *
     * @return the {@link SecurityIdentity} associated with this {@link AccessAuditContext}.
     */
    public SecurityIdentity getSecurityIdentity() {
        return securityIdentity;
    }

    /**
     * Get if the current {@link SecurityIdentity} was inflowed from another process.
     *
     * This is a special case where we want to use it without attempting to inflow into a configured security domain.
     *
     * @return {@code true} if the identity was inflowed, {@code false} otherwise.
     */
    public boolean isInflowed() {
        return inflowed;
    }

    /**
     * Get the remote address of the caller.
     *
     * @return the remote address of the caller.
     */
    public InetAddress getRemoteAddress() {
        return remoteAddress;
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
     * Perform work with a new {@code AccessAuditContext} as a particular {@code SecurityIdentity}
     * @param securityIdentity the {@code SecurityIdentity} that the specified {@code action} will run as. May be {@code null}
     * @param remoteAddress the remote address of the caller.
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
    public static <T> T doAs(final SecurityIdentity securityIdentity, final InetAddress remoteAddress, final PrivilegedAction<T> action) {
        return doAs(false, securityIdentity, remoteAddress, action);
    }

    /**
     * Perform work with a new {@code AccessAuditContext} as a particular {@code SecurityIdentity}
     * @param inflowed was the identity inflowed from a remote process?
     * @param securityIdentity the {@code SecurityIdentity} that the specified {@code action} will run as. May be {@code null}
     * @param remoteAddress the remote address of the caller.
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
    public static <T> T doAs(final boolean inflowed, final SecurityIdentity securityIdentity, final InetAddress remoteAddress, final PrivilegedAction<T> action) {
        final AccessAuditContext previous = contextThreadLocal.get();
        try {
            contextThreadLocal.set(new AccessAuditContext(inflowed, securityIdentity, remoteAddress, previous));
            return securityIdentity != null ? securityIdentity.runAs(action) : action.run();
        } finally {
            contextThreadLocal.set(previous);
        }
    }

    /**
     * Perform work with a new {@code AccessAuditContext} as a particular {@code SecurityIdentity}
     * @param securityIdentity the {@code SecurityIdentity} that the specified {@code action} will run as. May be {@code null}
     * @param remoteAddress the remote address of the caller.
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
    public static <T> T doAs(SecurityIdentity securityIdentity, InetAddress remoteAddress, PrivilegedExceptionAction<T> action)
            throws java.security.PrivilegedActionException {
        return doAs(false, securityIdentity, remoteAddress, action);
    }

    /**
     * Perform work with a new {@code AccessAuditContext} as a particular {@code SecurityIdentity}
     * @param inflowed was the identity inflowed from a remote process?
     * @param securityIdentity the {@code SecurityIdentity} that the specified {@code action} will run as. May be {@code null}
     * @param remoteAddress the remote address of the caller.
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
    public static <T> T doAs(boolean inflowed, SecurityIdentity securityIdentity, InetAddress remoteAddress, PrivilegedExceptionAction<T> action)
            throws java.security.PrivilegedActionException {
        final AccessAuditContext previous = contextThreadLocal.get();
        try {
            contextThreadLocal.set(new AccessAuditContext(inflowed, securityIdentity, remoteAddress, previous));
            if (securityIdentity != null) {
                return securityIdentity.runAs(action);
            } else try {
                return action.run();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new PrivilegedActionException(e);
            }
        } finally {
            contextThreadLocal.set(previous);
        }
    }

}
