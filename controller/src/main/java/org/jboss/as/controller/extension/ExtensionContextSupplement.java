/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
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
