/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller.extension;

import java.util.function.Supplier;

import org.jboss.as.controller.access.management.JmxAuthorizer;
import org.jboss.as.controller.audit.AuditLogger;
import org.wildfly.security.auth.server.SecurityIdentity;

/**
 * Supplemental methods an extension context might implement. For internal use only.
 *
 * @author Brian Stansberry (c) 2014 Red Hat Inc.
 *
 * @deprecated for internal use only; may be removed at any time
 */
@Deprecated
public interface ExtensionContextSupplement {
    /** @throws java.lang.UnsupportedOperationException if called from an invalid caller */
    AuditLogger getAuditLogger(boolean inheritConfiguration, boolean manualCommit);
    /** @throws java.lang.UnsupportedOperationException if called from an invalid caller */
    JmxAuthorizer getAuthorizer();
    /** @throws java.lang.UnsupportedOperationException if called from an invalid caller */
    Supplier<SecurityIdentity> getSecurityIdentitySupplier();
    /** @throws java.lang.UnsupportedOperationException if called from an invalid caller */
    RuntimeHostControllerInfoAccessor getHostControllerInfoAccessor();
}
