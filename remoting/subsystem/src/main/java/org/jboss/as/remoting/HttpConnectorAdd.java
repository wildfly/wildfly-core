/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.remoting;

import static org.jboss.as.remoting.Capabilities.SASL_AUTHENTICATION_FACTORY_CAPABILITY;
import static org.jboss.as.remoting.logging.RemotingLogger.ROOT_LOGGER;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceName;
import org.wildfly.security.auth.server.SaslAuthenticationFactory;
import org.xnio.OptionMap;

/**
 * Add a connector to a remoting container.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author Emanuel Muckenhuber
 * @author Kabir Khan
 */
public class HttpConnectorAdd extends AbstractAddStepHandler {

    static final HttpConnectorAdd INSTANCE = new HttpConnectorAdd();

    private HttpConnectorAdd() {
        super(HttpConnectorResource.CONNECTOR_REF, HttpConnectorResource.AUTHENTICATION_PROVIDER, HttpConnectorResource.SECURITY_REALM,
                HttpConnectorResource.SASL_AUTHENTICATION_FACTORY, ConnectorCommon.SASL_PROTOCOL, ConnectorCommon.SERVER_NAME);
    }

    @Override
    protected void populateModel(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {
        super.populateModel(context, operation, resource);

        context.addStep(operation, HttpConnectorValidationStep.INSTANCE, OperationContext.Stage.MODEL);
    }

    @Override
    protected void performRuntime(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {
        final String connectorName = context.getCurrentAddressValue();
        final ModelNode fullModel = Resource.Tools.readModel(resource);
        launchServices(context, connectorName, fullModel);
    }

    void launchServices(OperationContext context, String connectorName, ModelNode model) throws OperationFailedException {
        OptionMap optionMap = ConnectorUtils.getFullOptions(context, model);

        final String connectorRef = HttpConnectorResource.CONNECTOR_REF.resolveModelAttribute(context, model).asString();

        ModelNode securityRealmModel = HttpConnectorResource.SECURITY_REALM.resolveModelAttribute(context, model);
        if (securityRealmModel.isDefined()) {
            throw ROOT_LOGGER.runtimeSecurityRealmUnsupported();
        }
        ModelNode saslAuthenticationFactoryModel = HttpConnectorResource.SASL_AUTHENTICATION_FACTORY.resolveModelAttribute(context, model);
        final String saslAuthenticationFactory = saslAuthenticationFactoryModel.asStringOrNull();
        ServiceName saslAuthenticationFactorySvc = saslAuthenticationFactory != null ? context.getCapabilityServiceName(
                SASL_AUTHENTICATION_FACTORY_CAPABILITY, saslAuthenticationFactory, SaslAuthenticationFactory.class) : null;

        RemotingHttpUpgradeService.installServices(context, connectorName, connectorRef, RemotingServices.SUBSYSTEM_ENDPOINT,
                optionMap, saslAuthenticationFactorySvc);
    }


    /**
     * Validates that there is no other listener with the same connector-ref
     */
    private static class HttpConnectorValidationStep implements OperationStepHandler {

        private static HttpConnectorValidationStep INSTANCE = new HttpConnectorValidationStep();

        @Override
        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
            final PathAddress address = context.getCurrentAddress();
            final String connectorName = address.getLastElement().getValue();

            PathAddress parentAddress = address.getParent();
            Resource parent = context.readResourceFromRoot(parentAddress, false);
            Resource resource = context.readResourceFromRoot(address, false);
            ModelNode resourceRef = resource.getModel().get(CommonAttributes.CONNECTOR_REF);
            boolean listenerAlreadyExists = false;

            for(Resource.ResourceEntry child: parent.getChildren(CommonAttributes.HTTP_CONNECTOR)) {
                if(!connectorName.equals(child.getName())) {
                    Resource childResource = context.readResourceFromRoot(PathAddress.pathAddress(parentAddress, child.getPathElement()), false);
                    if(childResource.getModel().get(CommonAttributes.CONNECTOR_REF).equals(resourceRef)) {
                        listenerAlreadyExists = true;
                        break;
                    }
                }
            }

            if(listenerAlreadyExists) {
                throw ControllerLogger.ROOT_LOGGER.alreadyDefinedAttribute(CommonAttributes.HTTP_CONNECTOR, resourceRef.asString(), CommonAttributes.CONNECTOR_REF);
            }
        }

    }
}
