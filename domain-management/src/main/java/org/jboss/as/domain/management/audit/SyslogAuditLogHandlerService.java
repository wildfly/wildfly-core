/*
 * Copyright 2017 JBoss by Red Hat.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.as.domain.management.audit;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.AUTHENTICATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CLIENT_CERT_STORE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROTOCOL;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.TRUSTSTORE;

import java.util.Locale;
import java.util.Set;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.audit.SyslogAuditLogHandler;
import org.jboss.as.controller.audit.SyslogCredentialReferenceSupplier;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.registry.Resource.ResourceEntry;
import org.jboss.as.controller.security.CredentialReference;
import org.jboss.as.domain.management.audit.SyslogAuditLogProtocolResourceDefinition.TlsKeyStore;
import org.jboss.as.domain.management.logging.DomainManagementLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.msc.value.InjectedValue;
import org.wildfly.common.function.ExceptionSupplier;
import org.wildfly.security.credential.source.CredentialSource;

/**
 * Dummy service to access credential  suppliers
 * @author Emmanuel Hugonnet (c) 2017 Red Hat, inc.
 */
public class SyslogAuditLogHandlerService implements Service<SyslogAuditLogHandlerService>, SyslogCredentialReferenceSupplier {

    public static final ServiceName SYSLOG_AUDIT_HANDLER = ServiceName.of("org", "wildfly", "management", "audit", "syslog-handler");
    private final InjectedValue<ExceptionSupplier<CredentialSource, Exception>> tlsClientCertStoreCredentialSourceSupplier = new InjectedValue<>();
    private final InjectedValue<ExceptionSupplier<CredentialSource, Exception>> tlsClientCertStoreKeyCredentialSourceSupplier = new InjectedValue<>();
    private final InjectedValue<ExceptionSupplier<CredentialSource, Exception>> tlsTrustStoreSupplier = new InjectedValue<>();

    public SyslogAuditLogHandlerService() {
    }

    @Override
    public void start(StartContext context) throws StartException {
    }

    @Override
    public void stop(StopContext context) {
    }

    @Override
    public SyslogAuditLogHandlerService getValue() throws IllegalStateException, IllegalArgumentException {
        return this;
    }

    @Override
     public ExceptionSupplier<CredentialSource, Exception> getTlsClientCertStoreSupplier() {
        return tlsClientCertStoreCredentialSourceSupplier.getOptionalValue();
    }

    @Override
    public ExceptionSupplier<CredentialSource, Exception> getTlsClientCertStoreKeySupplier() {
        return tlsClientCertStoreKeyCredentialSourceSupplier.getOptionalValue();
    }

    @Override
    public ExceptionSupplier<CredentialSource, Exception> getTlsTrustStoreSupplier() {
        return tlsTrustStoreSupplier.getOptionalValue();
    }

    public static final SyslogAuditLogHandlerService installService(OperationContext context, ServiceName serviceName, final Resource handlerResource) throws OperationFailedException {
        SyslogAuditLogHandlerService service = new SyslogAuditLogHandlerService();
        ServiceBuilder<SyslogAuditLogHandlerService> serviceBuilder = context.getServiceTarget().addService(serviceName, service);
        final Set<ResourceEntry> protocols = handlerResource.getChildren(PROTOCOL);
        if (protocols.isEmpty()) {
            //We already check in SyslogAuditLogProtocolResourceDefinition that there is only one protocol
            throw DomainManagementLogger.ROOT_LOGGER.noSyslogProtocol();
        }
        final ResourceEntry protocol = protocols.iterator().next();
        final SyslogAuditLogHandler.Transport transport = SyslogAuditLogHandler.Transport.valueOf(protocol.getPathElement().getValue().toUpperCase(Locale.ENGLISH));
        if (transport == SyslogAuditLogHandler.Transport.TLS) {
            final Set<ResourceEntry> tlsStores = protocol.getChildren(AUTHENTICATION);
            for (ResourceEntry storeEntry : tlsStores) {
                final ModelNode storeModel = storeEntry.getModel();
                String type = storeEntry.getPathElement().getValue();
                if (type.equals(CLIENT_CERT_STORE)) {
                    if (storeModel.hasDefined(TlsKeyStore.KEY_PASSWORD_CREDENTIAL_REFERENCE.getName())) {
                        service.tlsClientCertStoreKeyCredentialSourceSupplier
                                .inject(CredentialReference.getCredentialSourceSupplier(context, TlsKeyStore.KEY_PASSWORD_CREDENTIAL_REFERENCE, storeModel, serviceBuilder));
                    }
                    if (storeModel.hasDefined(TlsKeyStore.KEYSTORE_PASSWORD_CREDENTIAL_REFERENCE.getName())) {
                        service.tlsClientCertStoreCredentialSourceSupplier
                                .inject(CredentialReference.getCredentialSourceSupplier(context, TlsKeyStore.KEYSTORE_PASSWORD_CREDENTIAL_REFERENCE, storeModel, serviceBuilder));
                    }
                } else if (type.equals(TRUSTSTORE)) {
                    if (storeModel.hasDefined(TlsKeyStore.KEYSTORE_PASSWORD_CREDENTIAL_REFERENCE.getName())) {
                        service.tlsTrustStoreSupplier
                                .inject(CredentialReference.getCredentialSourceSupplier(context, TlsKeyStore.KEYSTORE_PASSWORD_CREDENTIAL_REFERENCE, storeModel, serviceBuilder));
                    }
                }
            }
        }
        serviceBuilder.install();
        return service;
    }
}
