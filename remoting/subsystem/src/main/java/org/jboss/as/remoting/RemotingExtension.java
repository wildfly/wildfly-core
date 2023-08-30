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

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EXTENSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MODULE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;

import java.util.List;
import java.util.Map;

import org.jboss.as.controller.Extension;
import org.jboss.as.controller.ExtensionContext;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SubsystemRegistration;
import org.jboss.as.controller.access.constraint.SensitivityClassification;
import org.jboss.as.controller.access.management.SensitiveTargetAccessConstraintDefinition;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.descriptions.StandardResourceDescriptionResolver;
import org.jboss.as.controller.operations.common.GenericSubsystemDescribeHandler;
import org.jboss.as.controller.operations.common.OrderedChildTypesAttachment;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.parsing.ExtensionParsingContext;
import org.jboss.as.controller.parsing.ProfileParsingCompletionHandler;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.remoting.logging.RemotingLogger;
import org.jboss.dmr.ModelNode;

/**
 * The implementation of the Remoting extension.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author Emanuel Muckenhuber
 * @author Tomaz Cerar
 */
public class RemotingExtension implements Extension {

    public static final String SUBSYSTEM_NAME = "remoting";

    private static final String RESOURCE_NAME = RemotingExtension.class.getPackage().getName() + ".LocalDescriptions";

    static final String NODE_NAME_PROPERTY = "jboss.node.name";

    static ResourceDescriptionResolver getResourceDescriptionResolver(final String keyPrefix) {
        return new StandardResourceDescriptionResolver(keyPrefix, RESOURCE_NAME, RemotingExtension.class.getClassLoader(), true, false);
    }

    private static final SensitivityClassification REMOTING_SECURITY =
            new SensitivityClassification(SUBSYSTEM_NAME, "remoting-security", false, true, true);

    static final SensitiveTargetAccessConstraintDefinition REMOTING_SECURITY_DEF = new SensitiveTargetAccessConstraintDefinition(REMOTING_SECURITY);

    private static final int MANAGEMENT_API_MAJOR_VERSION = 6;
    private static final int MANAGEMENT_API_MINOR_VERSION = 0;
    private static final int MANAGEMENT_API_MICRO_VERSION = 0;

    private static final ModelVersion CURRENT_VERSION = ModelVersion.create(MANAGEMENT_API_MAJOR_VERSION, MANAGEMENT_API_MINOR_VERSION, MANAGEMENT_API_MICRO_VERSION);


    private static final String IO_EXTENSION_MODULE = "org.wildfly.extension.io";

    @Override
    public void initialize(ExtensionContext context) {

        // Register the remoting subsystem
        final SubsystemRegistration registration = context.registerSubsystem(SUBSYSTEM_NAME, CURRENT_VERSION);
        registration.registerXMLElementWriter(RemotingSubsystemXMLPersister::new);

        final boolean forDomain = context.getType() == ExtensionContext.ContextType.DOMAIN;
        final ResourceDefinition root = RemotingSubsystemRootResource.create(context.getProcessType(), forDomain);
        final ManagementResourceRegistration subsystem = registration.registerSubsystemModel(root);
        subsystem.registerOperationHandler(GenericSubsystemDescribeHandler.DEFINITION, new DescribeHandler());

        subsystem.registerSubModel(RemotingEndpointResource.INSTANCE);

        final ManagementResourceRegistration connector = subsystem.registerSubModel(ConnectorResource.INSTANCE);
        connector.registerSubModel(PropertyResource.INSTANCE_CONNECTOR);
        final ManagementResourceRegistration sasl = connector.registerSubModel(SaslResource.INSTANCE_CONNECTOR);
        sasl.registerSubModel(SaslPolicyResource.INSTANCE_CONNECTOR);
        sasl.registerSubModel(PropertyResource.INSTANCE_CONNECTOR);

        final ManagementResourceRegistration httpConnector = subsystem.registerSubModel(HttpConnectorResource.INSTANCE);
        httpConnector.registerSubModel(PropertyResource.INSTANCE_HTTP_CONNECTOR);
        final ManagementResourceRegistration httpSasl = httpConnector.registerSubModel(SaslResource.INSTANCE_HTTP_CONNECTOR);
        httpSasl.registerSubModel(SaslPolicyResource.INSTANCE_HTTP_CONNECTOR);
        httpSasl.registerSubModel(PropertyResource.INSTANCE_HTTP_CONNECTOR);

        // remote outbound connection
        subsystem.registerSubModel(RemoteOutboundConnectionResourceDefinition.INSTANCE);
        // local outbound connection
        subsystem.registerSubModel(LocalOutboundConnectionResourceDefinition.INSTANCE);
        // (generic) outbound connection
        subsystem.registerSubModel(new GenericOutboundConnectionResourceDefinition());
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void initializeParsers(ExtensionParsingContext context) {
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, Namespace.REMOTING_1_0.getUriString(), RemotingSubsystem10Parser::new);
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, Namespace.REMOTING_1_1.getUriString(), RemotingSubsystem11Parser::new);
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, Namespace.REMOTING_1_2.getUriString(), RemotingSubsystem12Parser::new);
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, Namespace.REMOTING_2_0.getUriString(), RemotingSubsystem20Parser::new);
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, Namespace.REMOTING_3_0.getUriString(), RemotingSubsystem30Parser::new);
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, Namespace.REMOTING_4_0.getUriString(), RemotingSubsystem40Parser::new);
        // For the current version we don't use a Supplier as we want its description initialized
        // TODO if any new xsd versions are added, use a Supplier for the old version

        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, Namespace.REMOTING_5_0.getUriString(), new RemotingSubsystem50Parser());
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, Namespace.REMOTING_6_0.getUriString(), new RemotingSubsystem60Parser());

        // For servers only as a migration aid we'll install io if it is missing.
        // It is invalid to do this on an HC as the HC needs to support profiles running legacy
        // slaves that will not understand the io extension
        // See also WFCORE-778 and the description of the pull request for it
        if (context.getProcessType().isServer()) {
            context.setProfileParsingCompletionHandler(new IOCompletionHandler());
        }
    }

    private static class IOCompletionHandler implements ProfileParsingCompletionHandler {

        @Override
        public void handleProfileParsingCompletion(Map<String, List<ModelNode>> profileBootOperations, List<ModelNode> otherBootOperations) {

            // If the namespace used for our subsystem predates the introduction of the IO subsystem,
            // check if the profile includes io and if not add it

            String legacyNS = null;
            List<ModelNode> legacyRemotingOps = null;
            for (Namespace ns : Namespace.values()) {
                String nsString = ns.getUriString();
                if (nsString != null && nsString.startsWith("urn:jboss:domain:remoting:1.")) {
                    legacyRemotingOps = profileBootOperations.get(nsString);
                    if (legacyRemotingOps != null) {
                        legacyNS = nsString;
                        break;
                    }
                }
            }

            if (legacyRemotingOps != null) {
                boolean foundIO = false;
                for (String ns : profileBootOperations.keySet()) {
                    if (ns.startsWith("urn:jboss:domain:io:")) {
                        foundIO = true;
                        break;
                    }
                }

                if (!foundIO) {
                    // legacy Remoting subsystem and no io subsystem, add it

                    // See if we need to add the extension as well
                    boolean hasIoExtension = false;
                    for (ModelNode op : otherBootOperations) {
                        PathAddress pa = PathAddress.pathAddress(op.get(OP_ADDR));
                        if (pa.size() == 1 && EXTENSION.equals(pa.getElement(0).getKey())
                                && IO_EXTENSION_MODULE.equals(pa.getElement(0).getValue())) {
                            hasIoExtension = true;
                            break;
                        }
                    }

                    if (!hasIoExtension) {
                        final ModelNode addIoExtensionOp = Util.createAddOperation(PathAddress.pathAddress(EXTENSION, IO_EXTENSION_MODULE));
                        addIoExtensionOp.get(MODULE).set(IO_EXTENSION_MODULE);
                        otherBootOperations.add(addIoExtensionOp);
                    }

                    PathAddress subsystemAddress = PathAddress.pathAddress(SUBSYSTEM, "io");
                    legacyRemotingOps.add(Util.createAddOperation(subsystemAddress));
                    legacyRemotingOps.add(Util.createAddOperation(subsystemAddress.append("worker", "default")));
                    legacyRemotingOps.add(Util.createAddOperation(subsystemAddress.append("buffer-pool", "default")));

                    RemotingLogger.ROOT_LOGGER.addingIOSubsystem(legacyNS);
                }
            }
        }
    }

    private static class DescribeHandler extends GenericSubsystemDescribeHandler {

        @Override
        protected void describe(OrderedChildTypesAttachment orderedChildTypesAttachment, Resource resource,
                                ModelNode address, ModelNode result, ImmutableManagementResourceRegistration registration) {
            // Don't describe the configuration=endpoint resource. It's just an alias for
            // a set of attributes on its parent and the parent description covers those.

            PathElement pe = registration.getPathAddress().getLastElement();
            if (!pe.equals(RemotingEndpointResource.ENDPOINT_PATH)) {
                super.describe(orderedChildTypesAttachment, resource, address, result, registration);
            }
        }
    }
}
