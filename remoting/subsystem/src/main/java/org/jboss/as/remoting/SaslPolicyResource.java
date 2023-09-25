/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.remoting;

import static org.jboss.as.remoting.CommonAttributes.CONNECTOR;
import static org.jboss.as.remoting.CommonAttributes.HTTP_CONNECTOR;
import static org.jboss.as.remoting.CommonAttributes.POLICY;
import static org.jboss.as.remoting.CommonAttributes.SASL_POLICY;

import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 * @author Tomaz Cerar
 */
class SaslPolicyResource extends ConnectorChildResource {
    static final PathElement SASL_POLICY_CONFIG_PATH = PathElement.pathElement(SASL_POLICY, POLICY);

    static final SimpleAttributeDefinition FORWARD_SECRECY = createBooleanAttributeDefinition(CommonAttributes.FORWARD_SECRECY);
    static final SimpleAttributeDefinition NO_ACTIVE = createBooleanAttributeDefinition(CommonAttributes.NO_ACTIVE);
    static final SimpleAttributeDefinition NO_ANONYMOUS = createBooleanAttributeDefinition(CommonAttributes.NO_ANONYMOUS);
    static final SimpleAttributeDefinition NO_DICTIONARY = createBooleanAttributeDefinition(CommonAttributes.NO_DICTIONARY);
    static final SimpleAttributeDefinition NO_PLAIN_TEXT = createBooleanAttributeDefinition(CommonAttributes.NO_PLAIN_TEXT);
    static final SimpleAttributeDefinition PASS_CREDENTIALS = createBooleanAttributeDefinition(CommonAttributes.PASS_CREDENTIALS);


    static final SimpleAttributeDefinition[] ATTRIBUTES = {
            FORWARD_SECRECY,
            NO_ACTIVE,
            NO_ANONYMOUS,
            NO_DICTIONARY,
            NO_PLAIN_TEXT,
            PASS_CREDENTIALS,

    };

    static final SaslPolicyResource INSTANCE_CONNECTOR = new SaslPolicyResource(CONNECTOR);
    static final SaslPolicyResource INSTANCE_HTTP_CONNECTOR = new SaslPolicyResource(HTTP_CONNECTOR);
    private final String parent;

    private SaslPolicyResource(String parent) {
        super(SASL_POLICY_CONFIG_PATH,
                RemotingExtension.getResourceDescriptionResolver(POLICY),
                new AddResourceConnectorRestartHandler(parent, ATTRIBUTES),
                new RemoveResourceConnectorRestartHandler(parent));
        this.parent = parent;
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
        final RestartConnectorWriteAttributeHandler writeHandler =
                new RestartConnectorWriteAttributeHandler(parent,FORWARD_SECRECY, NO_ACTIVE, NO_ANONYMOUS, NO_DICTIONARY,
                        NO_PLAIN_TEXT, PASS_CREDENTIALS);
        resourceRegistration.registerReadWriteAttribute(FORWARD_SECRECY, null, writeHandler);
        resourceRegistration.registerReadWriteAttribute(NO_ACTIVE, null, writeHandler);
        resourceRegistration.registerReadWriteAttribute(NO_ANONYMOUS, null, writeHandler);
        resourceRegistration.registerReadWriteAttribute(NO_DICTIONARY, null, writeHandler);
        resourceRegistration.registerReadWriteAttribute(NO_PLAIN_TEXT, null, writeHandler);
        resourceRegistration.registerReadWriteAttribute(PASS_CREDENTIALS, null, writeHandler);
    }

    private static SimpleAttributeDefinition createBooleanAttributeDefinition(String name) {
        return SimpleAttributeDefinitionBuilder.create(name, ModelType.BOOLEAN)
                .setDefaultValue(ModelNode.TRUE)
                .setRequired(false)
                .setAllowExpression(true)
                .setAttributeMarshaller(new WrappedAttributeMarshaller(Attribute.VALUE))
                .build();
    }
}
