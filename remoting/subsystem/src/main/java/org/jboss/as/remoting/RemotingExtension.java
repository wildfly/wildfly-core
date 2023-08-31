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
import static org.jboss.as.remoting.CommonAttributes.CONNECTOR;
import static org.jboss.as.remoting.CommonAttributes.HTTP_CONNECTOR;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import org.jboss.as.controller.Extension;
import org.jboss.as.controller.ExtensionContext;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PersistentResourceXMLDescription;
import org.jboss.as.controller.PersistentResourceXMLDescriptionReader;
import org.jboss.as.controller.PersistentResourceXMLDescriptionWriter;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SubsystemRegistration;
import org.jboss.as.controller.access.constraint.SensitivityClassification;
import org.jboss.as.controller.access.management.SensitiveTargetAccessConstraintDefinition;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.descriptions.StandardResourceDescriptionResolver;
import org.jboss.as.controller.operations.common.GenericSubsystemDescribeHandler;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.parsing.ExtensionParsingContext;
import org.jboss.as.controller.parsing.ProfileParsingCompletionHandler;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
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

    private static final String IO_EXTENSION_MODULE = "org.wildfly.extension.io";

    private final PersistentResourceXMLDescription currentXMLDescription = RemotingSubsystemSchema.CURRENT.getXMLDescription();

    @Override
    public void initialize(ExtensionContext context) {

        // Register the remoting subsystem
        final SubsystemRegistration registration = context.registerSubsystem(SUBSYSTEM_NAME, RemotingSubsystemModel.CURRENT.getVersion());
        registration.registerXMLElementWriter(new PersistentResourceXMLDescriptionWriter(this.currentXMLDescription));

        final ResourceDefinition root = new RemotingSubsystemRootResource();
        final ManagementResourceRegistration subsystem = registration.registerSubsystemModel(root);
        subsystem.registerOperationHandler(GenericSubsystemDescribeHandler.DEFINITION, GenericSubsystemDescribeHandler.INSTANCE);

        final ManagementResourceRegistration connector = subsystem.registerSubModel(new ConnectorResource());
        ResourceDefinition connectorPropertyResourceDefinition = new PropertyResource(CONNECTOR);
        connector.registerSubModel(connectorPropertyResourceDefinition);
        final ManagementResourceRegistration sasl = connector.registerSubModel(new SaslResource(CONNECTOR));
        sasl.registerSubModel(new SaslPolicyResource(CONNECTOR));
        sasl.registerSubModel(connectorPropertyResourceDefinition);

        final ManagementResourceRegistration httpConnector = subsystem.registerSubModel(new HttpConnectorResource());
        ResourceDefinition httpConnectorPropertyResourceDefinition = new PropertyResource(HTTP_CONNECTOR);
        httpConnector.registerSubModel(httpConnectorPropertyResourceDefinition);
        final ManagementResourceRegistration httpSasl = httpConnector.registerSubModel(new SaslResource(HTTP_CONNECTOR));
        httpSasl.registerSubModel(new SaslPolicyResource(HTTP_CONNECTOR));
        httpSasl.registerSubModel(httpConnectorPropertyResourceDefinition);

        // remote outbound connection
        subsystem.registerSubModel(new RemoteOutboundConnectionResourceDefinition());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void initializeParsers(ExtensionParsingContext context) {
        // Legacy parsers
        context.setSubsystemXmlMappings(SUBSYSTEM_NAME, EnumSet.complementOf(EnumSet.of(RemotingSubsystemSchema.CURRENT)));
        // Register parser for current version separately, using pre-built XML description
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, RemotingSubsystemSchema.CURRENT.getNamespace().getUri(), new PersistentResourceXMLDescriptionReader(this.currentXMLDescription));

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
            for (RemotingSubsystemSchema schema : EnumSet.allOf(RemotingSubsystemSchema.class)) {
                if (schema.getNamespace().getVersion().major() == 1) {
                    String uri = schema.getNamespace().getUri();
                    legacyRemotingOps = profileBootOperations.get(uri);
                    if (legacyRemotingOps != null) {
                        legacyNS = uri;
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
}
