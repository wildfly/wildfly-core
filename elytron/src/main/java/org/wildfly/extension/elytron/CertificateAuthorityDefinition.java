/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.elytron;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
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
import org.wildfly.security.x500.cert.acme.CertificateAuthority;
import org.jboss.msc.service.ServiceController.Mode;

import java.net.MalformedURLException;
import java.net.URL;

import static org.wildfly.extension.elytron.Capabilities.CERTIFICATE_AUTHORITY_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.ElytronDefinition.commonRequirements;
import static org.wildfly.extension.elytron.ElytronExtension.getRequiredService;
import static org.wildfly.extension.elytron._private.ElytronSubsystemMessages.ROOT_LOGGER;

/**
 * A {@link ResourceDefinition} for a single certificate authority.
 * This resource represents certificate authority that implements <a href="https://tools.ietf.org/html/rfc8555">Automatic Certificate
 * Management Environment (ACME)</a> specification.
 *
 * @author <a href="mailto:dvilkola@redhat.com">Diana Vilkolakova</a>
 */
class CertificateAuthorityDefinition extends SimpleResourceDefinition {

    static final SimpleAttributeDefinition URL = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.URL, ModelType.STRING, false)
            .setValidator(new URLValidator(false))
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition STAGING_URL = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.STAGING_URL, ModelType.STRING, true)
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
                throw ROOT_LOGGER.invalidURL(url, e);
            }
        }
    }

    private static final AttributeDefinition[] ATTRIBUTES = new AttributeDefinition[]{URL, STAGING_URL};
    private static final AbstractAddStepHandler ADD = new CertificateAuthorityAddHandler();

    private static final OperationStepHandler REMOVE = new TrivialCapabilityServiceRemoveHandler(ADD, CERTIFICATE_AUTHORITY_RUNTIME_CAPABILITY);

    CertificateAuthorityDefinition() {
        super(new Parameters(PathElement.pathElement(ElytronDescriptionConstants.CERTIFICATE_AUTHORITY), ElytronExtension.getResourceDescriptionResolver(ElytronDescriptionConstants.CERTIFICATE_AUTHORITY))
                .setAddHandler(ADD)
                .setRemoveHandler(REMOVE)
                .setAddRestartLevel(OperationEntry.Flag.RESTART_RESOURCE_SERVICES)
                .setRemoveRestartLevel(OperationEntry.Flag.RESTART_RESOURCE_SERVICES)
                .setCapabilities(CERTIFICATE_AUTHORITY_RUNTIME_CAPABILITY));
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        for (AttributeDefinition current : ATTRIBUTES) {
            resourceRegistration.registerReadWriteAttribute(current, null, ElytronReloadRequiredWriteAttributeHandler.INSTANCE);
        }
    }

    private static class CertificateAuthorityAddHandler extends BaseAddHandler {
        private CertificateAuthorityAddHandler() {
            super(CERTIFICATE_AUTHORITY_RUNTIME_CAPABILITY, ATTRIBUTES);
        }

        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
            final String certificateAuthorityName = context.getCurrentAddressValue();

            // Creation of certificate authority with name "LetsEncrypt" is not allowed, because it is used internally as a fallback.
            if (certificateAuthorityName.equalsIgnoreCase(CertificateAuthority.LETS_ENCRYPT.getName())) {
                throw ROOT_LOGGER.letsEncryptNameNotAllowed();
            }
            commonRequirements(installService(context, model)).setInitialMode(Mode.ACTIVE).install();
        }

        ServiceBuilder<CertificateAuthority> installService(OperationContext context, ModelNode model) {
            ServiceTarget serviceTarget = context.getServiceTarget();
            TrivialService<CertificateAuthority> certificateAuthorityTrivialService = new TrivialService<>(getValueSupplier(context, model));
            return serviceTarget.addService(CERTIFICATE_AUTHORITY_RUNTIME_CAPABILITY.getCapabilityServiceName(context.getCurrentAddressValue()), certificateAuthorityTrivialService);
        }

        protected TrivialService.ValueSupplier<CertificateAuthority> getValueSupplier(OperationContext context, ModelNode model) {
            return () -> {
                String certificateAuthorityResourceName = context.getCurrentAddress().getLastElement().getValue();
                return new CertificateAuthority(certificateAuthorityResourceName,
                        model.get(ElytronDescriptionConstants.URL).asString(),
                        model.get(ElytronDescriptionConstants.STAGING_URL).asStringOrNull());
            };
        }
    }

    static Service<CertificateAuthority> getCertificateAuthorityService(ServiceRegistry serviceRegistry, String certificateAuthorityName) {
        RuntimeCapability<Void> runtimeCapability = CERTIFICATE_AUTHORITY_RUNTIME_CAPABILITY.fromBaseCapability(certificateAuthorityName);
        ServiceName serviceName = runtimeCapability.getCapabilityServiceName();
        ServiceController<CertificateAuthority> serviceContainer = getRequiredService(serviceRegistry, serviceName, CertificateAuthority.class);
        return serviceContainer.getService();
    }
}
