/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.audit;

import org.wildfly.common.function.ExceptionSupplier;
import org.wildfly.security.credential.source.CredentialSource;

/**
 *
 * @author Emmanuel Hugonnet (c) 2017 Red Hat, inc.
 */
public interface SyslogCredentialReferenceSupplier {

    ExceptionSupplier<CredentialSource, Exception> getTlsClientCertStoreSupplier();

    ExceptionSupplier<CredentialSource, Exception> getTlsClientCertStoreKeySupplier();

    ExceptionSupplier<CredentialSource, Exception> getTlsTrustStoreSupplier();
}
