/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat, Inc., and individual contributors
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

package org.jboss.as.remoting;


import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.Extension;
import org.jboss.as.controller.ExtensionContext;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.SubsystemRegistration;
import org.jboss.as.controller.access.constraint.SensitivityClassification;
import org.jboss.as.controller.access.management.SensitiveTargetAccessConstraintDefinition;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.descriptions.StandardResourceDescriptionResolver;
import org.jboss.as.controller.operations.common.GenericSubsystemDescribeHandler;
import org.jboss.as.controller.parsing.ExtensionParsingContext;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.transform.OperationTransformer;
import org.jboss.as.controller.transform.ResourceTransformer;
import org.jboss.as.controller.transform.description.ChainedTransformationDescriptionBuilder;
import org.jboss.as.controller.transform.description.DiscardAttributeChecker;
import org.jboss.as.controller.transform.description.RejectAttributeChecker;
import org.jboss.as.controller.transform.description.ResourceTransformationDescriptionBuilder;
import org.jboss.as.controller.transform.description.TransformationDescription;
import org.jboss.as.controller.transform.description.TransformationDescriptionBuilder;
import org.jboss.dmr.ModelNode;

/**
 * The implementation of the Remoting extension.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author Emanuel Muckenhuber
 */
public class RemotingExtension implements Extension {

    public static final String SUBSYSTEM_NAME = "remoting";

    private static final String RESOURCE_NAME = RemotingExtension.class.getPackage().getName() + ".LocalDescriptions";

    static final String NODE_NAME_PROPERTY = "jboss.node.name";

    static ResourceDescriptionResolver getResourceDescriptionResolver(final String keyPrefix) {
        return new StandardResourceDescriptionResolver(keyPrefix, RESOURCE_NAME, RemotingExtension.class.getClassLoader(), true, false);
    }

    static final SensitivityClassification REMOTING_SECURITY =
            new SensitivityClassification(SUBSYSTEM_NAME, "remoting-security", false, true, true);

    static final SensitiveTargetAccessConstraintDefinition REMOTING_SECURITY_DEF = new SensitiveTargetAccessConstraintDefinition(REMOTING_SECURITY);


    private static final int MANAGEMENT_API_MAJOR_VERSION = 2;
    private static final int MANAGEMENT_API_MINOR_VERSION = 1;
    private static final int MANAGEMENT_API_MICRO_VERSION = 0;

    private static final ModelVersion VERSION_1_1 = ModelVersion.create(1, 1);
    private static final ModelVersion VERSION_1_2 = ModelVersion.create(1, 2);
    private static final ModelVersion VERSION_1_3 = ModelVersion.create(1, 3);

    @Override
    public void initialize(ExtensionContext context) {

        // Register the remoting subsystem
        final SubsystemRegistration registration = context.registerSubsystem(SUBSYSTEM_NAME, MANAGEMENT_API_MAJOR_VERSION,
                MANAGEMENT_API_MINOR_VERSION, MANAGEMENT_API_MICRO_VERSION);
        registration.registerXMLElementWriter(RemotingSubsystemXMLPersister.INSTANCE);

        final ManagementResourceRegistration subsystem = registration.registerSubsystemModel(new RemotingSubsystemRootResource(context.getProcessType()));
        subsystem.registerOperationHandler(GenericSubsystemDescribeHandler.DEFINITION, GenericSubsystemDescribeHandler.INSTANCE);

        subsystem.registerSubModel(RemotingEndpointResource.INSTANCE);

        final ManagementResourceRegistration connector = subsystem.registerSubModel(ConnectorResource.INSTANCE);
        connector.registerSubModel(PropertyResource.INSTANCE_CONNECTOR);
        final ManagementResourceRegistration sasl = connector.registerSubModel(SaslResource.INSTANCE);
        sasl.registerSubModel(SaslPolicyResource.INSTANCE);
        sasl.registerSubModel(PropertyResource.INSTANCE_CONNECTOR);

        final ManagementResourceRegistration httpConnector = subsystem.registerSubModel(HttpConnectorResource.INSTANCE);
        httpConnector.registerSubModel(PropertyResource.INSTANCE_HTTP_CONNECTOR);
        final ManagementResourceRegistration httpSasl = httpConnector.registerSubModel(SaslResource.INSTANCE);
        httpSasl.registerSubModel(SaslPolicyResource.INSTANCE);
        httpSasl.registerSubModel(PropertyResource.INSTANCE_HTTP_CONNECTOR);

        // remote outbound connection
        subsystem.registerSubModel(RemoteOutboundConnectionResourceDefinition.INSTANCE);
        // local outbound connection
        subsystem.registerSubModel(LocalOutboundConnectionResourceDefinition.INSTANCE);
        // (generic) outbound connection
        subsystem.registerSubModel(GenericOutboundConnectionResourceDefinition.INSTANCE);

        if (context.isRegisterTransformers()) {
            registerTransformers(registration);
        }
    }

    private void registerTransformers(SubsystemRegistration registration) {
        ChainedTransformationDescriptionBuilder chainedBuilder = TransformationDescriptionBuilder.Factory.createChainedSubystemInstance(registration.getSubsystemVersion());



        //Current 2.1.0 to 1.3.0
        buildTransformers_1_3(chainedBuilder.createBuilder(registration.getSubsystemVersion(), VERSION_1_3));

        //1.3.0 to 1.2.0 (do nothing)
        chainedBuilder.createBuilder(VERSION_1_3, VERSION_1_2);

        //1.2.0 to 1.1.0
        buildTransformers_1_1(chainedBuilder.createBuilder(VERSION_1_2, VERSION_1_1));

        //Register the 1.1.0 transformer
        TransformationDescription.Tools.register(chainedBuilder.build(VERSION_1_1, VERSION_1_2, VERSION_1_3), registration, VERSION_1_1);
        //Register the 1.2.0 transformer
        TransformationDescription.Tools.register(chainedBuilder.build(VERSION_1_2, VERSION_1_3), registration, VERSION_1_2);
        //Register the 1.3.0 transformer
        TransformationDescription.Tools.register(chainedBuilder.build(VERSION_1_3), registration, VERSION_1_3);
    }

    private void buildTransformers_1_1(ResourceTransformationDescriptionBuilder builder) {

        builder.getAttributeBuilder()
                .addRejectCheck(RejectAttributeChecker.SIMPLE_EXPRESSIONS, RemotingSubsystemRootResource.ATTRIBUTES);

        builder.rejectChildResource(HttpConnectorResource.PATH);

        ResourceTransformationDescriptionBuilder connector = builder.addChildResource(ConnectorResource.PATH);
        connector.addChildResource(PropertyResource.PATH).getAttributeBuilder().addRejectCheck(RejectAttributeChecker.SIMPLE_EXPRESSIONS, PropertyResource.VALUE);

        ResourceTransformationDescriptionBuilder sasl = connector.addChildResource(SaslResource.SASL_CONFIG_PATH);
        sasl.getAttributeBuilder().addRejectCheck(RejectAttributeChecker.SIMPLE_EXPRESSIONS, SaslResource.ATTRIBUTES);
        sasl.addChildResource(SaslPolicyResource.INSTANCE).getAttributeBuilder().addRejectCheck(RejectAttributeChecker.SIMPLE_EXPRESSIONS, SaslPolicyResource.ATTRIBUTES);
        sasl.addChildResource(PropertyResource.PATH).getAttributeBuilder().addRejectCheck(RejectAttributeChecker.SIMPLE_EXPRESSIONS, PropertyResource.VALUE);

        builder.addChildResource(RemoteOutboundConnectionResourceDefinition.ADDRESS).getAttributeBuilder()
                .addRejectCheck(RejectAttributeChecker.SIMPLE_EXPRESSIONS, RemoteOutboundConnectionResourceDefinition.USERNAME).end()
                .addChildResource(PropertyResource.PATH).getAttributeBuilder().addRejectCheck(RejectAttributeChecker.SIMPLE_EXPRESSIONS, PropertyResource.VALUE).end();

        builder.addChildResource(LocalOutboundConnectionResourceDefinition.ADDRESS)
                .addChildResource(PropertyResource.PATH).getAttributeBuilder().addRejectCheck(RejectAttributeChecker.SIMPLE_EXPRESSIONS, PropertyResource.VALUE).end();

        builder.addChildResource(GenericOutboundConnectionResourceDefinition.ADDRESS)
                .addChildResource(PropertyResource.PATH).getAttributeBuilder().addRejectCheck(RejectAttributeChecker.SIMPLE_EXPRESSIONS, PropertyResource.VALUE).end();
    }

    private void buildTransformers_1_3(ResourceTransformationDescriptionBuilder builder) {
        builder.rejectChildResource(HttpConnectorResource.PATH);

        endpointTransform(builder);

        builder.addChildResource(RemoteOutboundConnectionResourceDefinition.ADDRESS).getAttributeBuilder()
                .setDiscard(new DiscardAttributeChecker.DiscardAttributeValueChecker(new ModelNode(Protocol.REMOTE.toString())), RemoteOutboundConnectionResourceDefinition.PROTOCOL)
                .addRejectCheck(RejectAttributeChecker.DEFINED, RemoteOutboundConnectionResourceDefinition.PROTOCOL);
    }

    private static ResourceTransformationDescriptionBuilder endpointTransform(ResourceTransformationDescriptionBuilder parent) {
        // For configuration=endpoint, reject if any attributes are defined, otherwise discard the add op and the resource
        parent.addChildResource(RemotingEndpointResource.ENDPOINT_PATH)
                .getAttributeBuilder()
                    .setDiscard(DiscardAttributeChecker.UNDEFINED, RemotingEndpointResource.ATTRIBUTES)
                    .addRejectCheck(RejectAttributeChecker.DEFINED, RemotingEndpointResource.ATTRIBUTES.toArray(new AttributeDefinition[RemotingEndpointResource.ATTRIBUTES.size()]))
                    .end()
                .addOperationTransformationOverride(ModelDescriptionConstants.ADD)
                    .inheritResourceAttributeDefinitions()
                    .setCustomOperationTransformer(OperationTransformer.DISCARD)
                    .end()
                .setCustomResourceTransformer(ResourceTransformer.DISCARD);
        return parent;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void initializeParsers(ExtensionParsingContext context) {
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, Namespace.REMOTING_1_0.getUriString(), RemotingSubsystem10Parser.INSTANCE);
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, Namespace.REMOTING_1_1.getUriString(), RemotingSubsystem11Parser.INSTANCE);
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, Namespace.REMOTING_2_0.getUriString(), RemotingSubsystem20Parser.INSTANCE);
    }

}
