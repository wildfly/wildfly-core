/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2019 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.extension.elytron.common;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.descriptions.StandardResourceDescriptionResolver;
import org.jboss.as.controller.operations.validation.StringLengthValidator;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.service.ServiceTarget;
import org.wildfly.extension.elytron.common.util.ElytronCommonMessages;
import org.wildfly.security.x500.cert.acme.CertificateAuthority;
import org.jboss.msc.service.ServiceController.Mode;

import java.net.MalformedURLException;
import java.net.URL;


/**
 * A {@link ResourceDefinition} for a single certificate authority.
 * This resource represents certificate authority that implements <a href="https://tools.ietf.org/html/rfc8555">Automatic Certificate
 * Management Environment (ACME)</a> specification.
 *
 * @author <a href="mailto:dvilkola@redhat.com">Diana Vilkolakova</a>
 */
public class CertificateAuthorityDefinition extends SimpleResourceDefinition {

    public static final SimpleAttributeDefinition URL = new SimpleAttributeDefinitionBuilder(ElytronCommonConstants.URL, ModelType.STRING, false)
            .setValidator(new URLValidator(false))
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();

    public static final SimpleAttributeDefinition STAGING_URL = new SimpleAttributeDefinitionBuilder(ElytronCommonConstants.STAGING_URL, ModelType.STRING, true)
            .setValidator(new URLValidator(false))
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();

    private static class URLValidator extends StringLengthValidator {

        private URLValidator(boolean nullable) {
            super(1, nullable, false);
        }

        @Override
        public void validateParameter(String parameterName, ModelNode value) throws OperationFailedException {
            super.validateParameter(parameterName, value);
            String url = value.asString();
            try {
                new URL(url);
            } catch (MalformedURLException e) {
                throw ElytronCommonMessages.ROOT_LOGGER.invalidURL(url, e);
            }
        }
    }

    private static final AttributeDefinition[] ATTRIBUTES = new AttributeDefinition[]{URL, STAGING_URL};
    private static final ElytronReloadRequiredWriteAttributeHandler WRITE = new ElytronReloadRequiredWriteAttributeHandler(ATTRIBUTES);

    public static CertificateAuthorityDefinition configure(final Class<?> extensionClass) {
        StandardResourceDescriptionResolver caResourceResolver = ElytronCommonDefinitions.getResourceDescriptionResolver(extensionClass, ElytronCommonConstants.CERTIFICATE_AUTHORITY);

        CertificateAuthorityAddHandler caAddHandler = new CertificateAuthorityAddHandler(extensionClass);
        TrivialCapabilityServiceRemoveHandler caRemoveHandler = new TrivialCapabilityServiceRemoveHandler(extensionClass, caAddHandler, ElytronCommonCapabilities.CERTIFICATE_AUTHORITY_RUNTIME_CAPABILITY);

        return new CertificateAuthorityDefinition(caResourceResolver, caAddHandler, caRemoveHandler);
    }

    protected CertificateAuthorityDefinition(StandardResourceDescriptionResolver caResourceResolver, CertificateAuthorityAddHandler caAddHandler,
                                           ElytronCommonTrivialCapabilityServiceRemoveHandler caRemoveHandler) {

        super(new Parameters(PathElement.pathElement(ElytronCommonConstants.CERTIFICATE_AUTHORITY), caResourceResolver)
                .setAddHandler(caAddHandler)
                .setRemoveHandler(caRemoveHandler)
                .setAddRestartLevel(OperationEntry.Flag.RESTART_RESOURCE_SERVICES)
                .setRemoveRestartLevel(OperationEntry.Flag.RESTART_RESOURCE_SERVICES)
                .setCapabilities(ElytronCommonCapabilities.CERTIFICATE_AUTHORITY_RUNTIME_CAPABILITY));
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        for (AttributeDefinition current : ATTRIBUTES) {
            resourceRegistration.registerReadWriteAttribute(current, null, WRITE);
        }
    }

    private static class CertificateAuthorityAddHandler extends ElytronCommonBaseAddHandler {
        private final Class<?> extensionClass;

        private CertificateAuthorityAddHandler(final Class<?> extensionClass) {
            super(ElytronCommonCapabilities.CERTIFICATE_AUTHORITY_RUNTIME_CAPABILITY, ATTRIBUTES);
            this.extensionClass = extensionClass;
        }

        @Override
        protected String getSubsystemCapability() {
            return ElytronCommonDefinitions.getSubsystemCapability(extensionClass);
        }

        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
            final String certificateAuthorityName = context.getCurrentAddressValue();

            // Creation of certificate authority with name "LetsEncrypt" is not allowed, because it is used internally as a fallback.
            if (certificateAuthorityName.equalsIgnoreCase(CertificateAuthority.LETS_ENCRYPT.getName())) {
                throw ElytronCommonMessages.ROOT_LOGGER.letsEncryptNameNotAllowed();
            }
            ElytronCommonDefinitions.commonRequirements(extensionClass, installService(context, model)).setInitialMode(Mode.ACTIVE).install();
        }

        protected ServiceBuilder<CertificateAuthority> installService(OperationContext context, ModelNode model) {
            ServiceTarget serviceTarget = context.getServiceTarget();
            TrivialService<CertificateAuthority> certificateAuthorityTrivialService = new TrivialService<>(getValueSupplier(context, model));
            return serviceTarget.addService(ElytronCommonCapabilities.CERTIFICATE_AUTHORITY_RUNTIME_CAPABILITY.getCapabilityServiceName(context.getCurrentAddressValue()), certificateAuthorityTrivialService);
        }

        protected TrivialService.ValueSupplier<CertificateAuthority> getValueSupplier(OperationContext context, ModelNode model) {
            return () -> {
                String certificateAuthorityResourceName = context.getCurrentAddress().getLastElement().getValue();
                return new CertificateAuthority(certificateAuthorityResourceName,
                        model.get(ElytronCommonConstants.URL).asString(),
                        model.get(ElytronCommonConstants.STAGING_URL).asStringOrNull());
            };
        }
    }

    public static Service<CertificateAuthority> getCertificateAuthorityService(ServiceRegistry serviceRegistry, String certificateAuthorityName) {
        RuntimeCapability<Void> runtimeCapability = ElytronCommonCapabilities.CERTIFICATE_AUTHORITY_RUNTIME_CAPABILITY.fromBaseCapability(certificateAuthorityName);
        ServiceName serviceName = runtimeCapability.getCapabilityServiceName();
        ServiceController<CertificateAuthority> serviceContainer = ElytronCommonDefinitions.getRequiredService(serviceRegistry, serviceName, CertificateAuthority.class);
        return serviceContainer.getService();
    }
}
