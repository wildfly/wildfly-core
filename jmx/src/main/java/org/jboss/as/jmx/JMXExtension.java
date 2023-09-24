/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.jmx;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HANDLER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.jmx.CommonAttributes.JMX;
import static org.jboss.as.jmx.CommonAttributes.REMOTING_CONNECTOR;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.Extension;
import org.jboss.as.controller.ExtensionContext;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.SubsystemRegistration;
import org.jboss.as.controller.access.constraint.SensitivityClassification;
import org.jboss.as.controller.access.management.JmxAuthorizer;
import org.jboss.as.controller.access.management.SensitiveTargetAccessConstraintDefinition;
import org.jboss.as.controller.audit.ManagedAuditLogger;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.descriptions.StandardResourceDescriptionResolver;
import org.jboss.as.controller.extension.ExtensionContextSupplement;
import org.jboss.as.controller.extension.RuntimeHostControllerInfoAccessor;
import org.jboss.as.controller.parsing.ExtensionParsingContext;
import org.jboss.as.controller.parsing.ParseUtils;
import org.jboss.as.controller.persistence.SubsystemMarshallingContext;
import org.jboss.as.jmx.logging.JmxLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLElementReader;
import org.jboss.staxmapper.XMLElementWriter;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;
import org.wildfly.security.auth.server.SecurityIdentity;

/**
 * Domain extension used to initialize the JMX subsystem.
 *
 * @author Emanuel Muckenhuber
 * @author Kabir Khan
 */
public class JMXExtension implements Extension {

    public static final String SUBSYSTEM_NAME = "jmx";
    private static final String RESOURCE_NAME = JMXExtension.class.getPackage().getName() + ".LocalDescriptions";


    static ResourceDescriptionResolver getResourceDescriptionResolver(final String keyPrefix) {
        return new StandardResourceDescriptionResolver(keyPrefix, RESOURCE_NAME, JMXExtension.class.getClassLoader(), true, false);
    }

    static final SensitivityClassification JMX_SENSITIVITY =
            new SensitivityClassification(SUBSYSTEM_NAME, "jmx", false, false, true);

    static final SensitiveTargetAccessConstraintDefinition JMX_SENSITIVITY_DEF = new SensitiveTargetAccessConstraintDefinition(JMX_SENSITIVITY);

    private static final int MANAGEMENT_API_MAJOR_VERSION = 1;
    private static final int MANAGEMENT_API_MINOR_VERSION = 2;
    private static final int MANAGEMENT_API_MICRO_VERSION = 0;

    private static final ModelVersion CURRENT_VERSION = ModelVersion.create(MANAGEMENT_API_MAJOR_VERSION, MANAGEMENT_API_MINOR_VERSION, MANAGEMENT_API_MICRO_VERSION);

    /**
     * {@inheritDoc}
     */
    @Override
    public void initialize(ExtensionContext context) {
        final SubsystemRegistration registration = context.registerSubsystem(SUBSYSTEM_NAME, CURRENT_VERSION);

        //This subsystem should be runnable on a host
        registration.setHostCapable();

        //This is ugly but for now we don't want to make the audit logger easily available to all extensions
        @SuppressWarnings("deprecation")
        ManagedAuditLogger auditLogger = (ManagedAuditLogger)((ExtensionContextSupplement)context).getAuditLogger(false, true);
        //This is ugly but for now we don't want to make the authorizer easily available to all extensions
        @SuppressWarnings("deprecation")
        JmxAuthorizer authorizer = ((ExtensionContextSupplement)context).getAuthorizer();
      //This is ugly but for now we don't want to make the securityIdentitySupplier easily available to all extensions
        @SuppressWarnings("deprecation")
        Supplier<SecurityIdentity> securityIdentitySupplier = ((ExtensionContextSupplement)context).getSecurityIdentitySupplier();
        //This is ugly but for now we don't want to make the hostInfoAccessor easily available to all extensions
        @SuppressWarnings("deprecation")
        RuntimeHostControllerInfoAccessor hostInfoAccessor = ((ExtensionContextSupplement)context).getHostControllerInfoAccessor();

        registration.registerSubsystemModel(JMXSubsystemRootResource.create(auditLogger, authorizer, securityIdentitySupplier, hostInfoAccessor));
        registration.registerXMLElementWriter(JMXSubsystemWriter::new);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void initializeParsers(ExtensionParsingContext context) {
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, Namespace.JMX_1_0.getUriString(), JMXSubsystemParser_1_0::new);
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, Namespace.JMX_1_1.getUriString(), JMXSubsystemParser_1_1::new);
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, Namespace.JMX_1_2.getUriString(), JMXSubsystemParser_1_2::new);
        context.setSubsystemXmlMapping(SUBSYSTEM_NAME, Namespace.JMX_1_3.getUriString(), JMXSubsystemParser_1_3::new);
    }

    private static ModelNode createAddOperation() {
        return createOperation(ADD);
    }

    private static ModelNode createOperation(String name, String... addressElements) {
        final ModelNode op = new ModelNode();
        op.get(OP).set(name);
        op.get(OP_ADDR).add(SUBSYSTEM, SUBSYSTEM_NAME);
        for (int i = 0; i < addressElements.length; i++) {
            op.get(OP_ADDR).add(addressElements[i], addressElements[++i]);
        }
        return op;
    }

    private static class JMXSubsystemParser_1_0 implements XMLStreamConstants, XMLElementReader<List<ModelNode>> {

        /**
         * {@inheritDoc}
         */
        @Override
        public void readElement(XMLExtendedStreamReader reader, List<ModelNode> list) throws XMLStreamException {
            ParseUtils.requireNoAttributes(reader);
            list.add(createAddOperation());

            boolean gotConnector = false;

            while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
                final String element = reader.getLocalName();
                switch (element) {
                    case CommonAttributes.JMX_CONNECTOR: {
                        if (gotConnector) {
                            throw ParseUtils.duplicateNamedElement(reader, CommonAttributes.JMX_CONNECTOR);
                        }
                        parseConnector(reader);
                        gotConnector = true;
                        break;
                    }
                    default: {
                        throw ParseUtils.unexpectedElement(reader);
                    }
                }
            }
        }

        void parseConnector(XMLExtendedStreamReader reader) throws XMLStreamException {
            JmxLogger.ROOT_LOGGER.jmxConnectorNotSupported();
            String serverBinding = null;
            String registryBinding = null;
            int count = reader.getAttributeCount();
            for (int i = 0; i < count; i++) {
                final String value = reader.getAttributeValue(i);
                final String attribute = reader.getAttributeLocalName(i);
                switch (attribute) {
                    case CommonAttributes.SERVER_BINDING: {
                        serverBinding = value;
                        break;
                    }
                    case CommonAttributes.REGISTRY_BINDING: {
                        registryBinding = value;
                        break;
                    }
                    default: {
                        throw ParseUtils.unexpectedAttribute(reader, i);
                    }
                }
            }
            // Require no content
            ParseUtils.requireNoContent(reader);
            if (serverBinding == null) {
                throw ParseUtils.missingRequired(reader, Collections.singleton(CommonAttributes.SERVER_BINDING));
            }
            if (registryBinding == null) {
                throw ParseUtils.missingRequired(reader, Collections.singleton(CommonAttributes.REGISTRY_BINDING));
            }
        }
    }

    private static class JMXSubsystemParser_1_1 implements XMLStreamConstants, XMLElementReader<List<ModelNode>> {

        /**
         * {@inheritDoc}
         */
        @Override
        public void readElement(XMLExtendedStreamReader reader, List<ModelNode> list) throws XMLStreamException {
            boolean showModel = false;

            ParseUtils.requireNoAttributes(reader);

            ModelNode connectorAdd = null;
            list.add(createAddOperation());
            while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
                final String element = reader.getLocalName();
                switch (element) {
                    case CommonAttributes.SHOW_MODEL:
                        if (showModel) {
                            throw ParseUtils.duplicateNamedElement(reader, CommonAttributes.SHOW_MODEL);
                        }
                        if (parseShowModelElement(reader)) {
                            //Add the show-model=>resolved part with the default domain name
                            ModelNode op = createOperation(ADD, CommonAttributes.EXPOSE_MODEL, CommonAttributes.RESOLVED);
                            //Use false here to keep total backwards compatibility
                            op.get(CommonAttributes.PROPER_PROPERTY_FORMAT).set(false);
                            list.add(op);
                        }
                        showModel = true;
                        break;
                    case CommonAttributes.REMOTING_CONNECTOR: {
                        if (connectorAdd != null) {
                            throw ParseUtils.duplicateNamedElement(reader, CommonAttributes.REMOTING_CONNECTOR);
                        }
                        list.add(parseRemoteConnector(reader));
                        break;
                    }
                    default: {
                        throw ParseUtils.unexpectedElement(reader);
                    }
                }
            }
        }

        protected ModelNode parseRemoteConnector(final XMLExtendedStreamReader reader) throws XMLStreamException {

            final ModelNode connector = new ModelNode();
            connector.get(OP).set(ADD);
            connector.get(OP_ADDR).add(SUBSYSTEM, JMX).add(REMOTING_CONNECTOR, CommonAttributes.JMX);

            int count = reader.getAttributeCount();
            for (int i = 0; i < count; i++) {
                final String value = reader.getAttributeValue(i);
                final String attribute = reader.getAttributeLocalName(i);
                switch (attribute) {
                    case CommonAttributes.USE_MANAGEMENT_ENDPOINT: {
                        RemotingConnectorResource.USE_MANAGEMENT_ENDPOINT.parseAndSetParameter(value, connector, reader);
                        break;
                    }
                    default: {
                        throw ParseUtils.unexpectedAttribute(reader, i);
                    }
                }
            }

            ParseUtils.requireNoContent(reader);
            return connector;
        }


        private boolean parseShowModelElement(XMLExtendedStreamReader reader) throws XMLStreamException {
            ParseUtils.requireSingleAttribute(reader, CommonAttributes.VALUE);
            return ParseUtils.readBooleanAttributeElement(reader, CommonAttributes.VALUE);
        }
    }

    private static class JMXSubsystemParser_1_2 extends JMXSubsystemParser_1_1 {

        @Override
        public void readElement(XMLExtendedStreamReader reader, List<ModelNode> list) throws XMLStreamException {
            boolean showResolvedModel = false;
            boolean showExpressionModel = false;
            boolean connectorAdd = false;

            ParseUtils.requireNoAttributes(reader);

            list.add(createAddOperation());

            while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
                final String element = reader.getLocalName();
                switch (element) {
                    case CommonAttributes.EXPOSE_RESOLVED_MODEL:
                        if (showResolvedModel) {
                            throw ParseUtils.duplicateNamedElement(reader, CommonAttributes.EXPOSE_RESOLVED_MODEL);
                        }
                        showResolvedModel = true;
                        list.add(parseShowModelElement(reader, CommonAttributes.RESOLVED));
                        break;
                    case CommonAttributes.EXPOSE_EXPRESSION_MODEL:
                        if (showExpressionModel) {
                            throw ParseUtils.duplicateNamedElement(reader, CommonAttributes.EXPOSE_EXPRESSION_MODEL);
                        }
                        showExpressionModel = true;
                        list.add(parseShowModelElement(reader, CommonAttributes.EXPRESSION));
                        break;
                    case REMOTING_CONNECTOR:
                        if (connectorAdd) {
                            throw ParseUtils.duplicateNamedElement(reader, CommonAttributes.REMOTING_CONNECTOR);
                        }
                        connectorAdd = true;
                        list.add(parseRemoteConnector(reader));
                        break;
                    default: {
                        throw ParseUtils.unexpectedElement(reader);
                    }
                }
            }
        }

        protected ModelNode parseShowModelElement(XMLExtendedStreamReader reader, String showModelChild) throws XMLStreamException {

            ModelNode op = createOperation(ADD, CommonAttributes.EXPOSE_MODEL, showModelChild);

            String domainName = null;
            Boolean properPropertyFormat = null;

            for (int i = 0; i < reader.getAttributeCount(); i++) {
                final String value = reader.getAttributeValue(i);
                final String attribute = reader.getAttributeLocalName(i);
                switch (attribute) {
                    case CommonAttributes.DOMAIN_NAME:
                        ExposeModelResource.getDomainNameAttribute(showModelChild).parseAndSetParameter(value, op, reader);
                        break;
                    case CommonAttributes.PROPER_PROPERTY_FORMAT:
                        if (showModelChild.equals(CommonAttributes.RESOLVED)) {
                            ExposeModelResourceResolved.PROPER_PROPERTY_FORMAT.parseAndSetParameter(value, op, reader);
                        } else {
                            throw ParseUtils.unexpectedAttribute(reader, i);
                        }
                        break;
                    default:
                        throw ParseUtils.unexpectedAttribute(reader, i);
                }
            }

            if (domainName == null && properPropertyFormat == null) {
                ParseUtils.requireNoContent(reader);
            }
            return op;
        }
    }

    private static class JMXSubsystemParser_1_3 extends JMXSubsystemParser_1_2 {
        @Override
        public void readElement(XMLExtendedStreamReader reader, List<ModelNode> list) throws XMLStreamException {
            boolean showResolvedModel = false;
            boolean showExpressionModel = false;
            boolean connectorAdd = false;
            boolean auditLog = false;
            boolean sensitivity = false;

            ParseUtils.requireNoAttributes(reader);

            ModelNode add = createAddOperation();
            list.add(add);

            while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
                final String element = reader.getLocalName();
                switch (element) {
                    case CommonAttributes.EXPOSE_RESOLVED_MODEL:
                        if (showResolvedModel) {
                            throw ParseUtils.duplicateNamedElement(reader, CommonAttributes.EXPOSE_RESOLVED_MODEL);
                        }
                        showResolvedModel = true;
                        list.add(parseShowModelElement(reader, CommonAttributes.RESOLVED));
                        break;
                    case CommonAttributes.EXPOSE_EXPRESSION_MODEL:
                        if (showExpressionModel) {
                            throw ParseUtils.duplicateNamedElement(reader, CommonAttributes.EXPOSE_EXPRESSION_MODEL);
                        }
                        showExpressionModel = true;
                        list.add(parseShowModelElement(reader, CommonAttributes.EXPRESSION));
                        break;
                    case REMOTING_CONNECTOR:
                        if (connectorAdd) {
                            throw ParseUtils.duplicateNamedElement(reader, CommonAttributes.REMOTING_CONNECTOR);
                        }
                        connectorAdd = true;
                        list.add(parseRemoteConnector(reader));
                        break;
                    case CommonAttributes.AUDIT_LOG:
                        if (auditLog) {
                            throw ParseUtils.duplicateNamedElement(reader, CommonAttributes.AUDIT_LOG);
                        }
                        auditLog = true;
                        parseAuditLogElement(reader, list);
                        break;
                    case CommonAttributes.SENSITIVITY:
                        if (sensitivity) {
                            throw ParseUtils.duplicateNamedElement(reader, CommonAttributes.SENSITIVITY);
                        }
                        sensitivity = true;
                        parseSensitivity(add, reader);
                        break;
                    default: {
                        throw ParseUtils.unexpectedElement(reader);
                    }
                }
            }
        }

        private void parseSensitivity(ModelNode add, XMLExtendedStreamReader reader) throws XMLStreamException {
            final int count = reader.getAttributeCount();
            for (int i = 0; i < count; i++) {
                final String value = reader.getAttributeValue(i);
                final String attribute = reader.getAttributeLocalName(i);
                switch (attribute) {
                    case CommonAttributes.NON_CORE_MBEANS:
                        JMXSubsystemRootResource.NON_CORE_MBEAN_SENSITIVITY.parseAndSetParameter(value, add, reader);
                        break;
                    default:
                        throw ParseUtils.unexpectedAttribute(reader, i);
                }
            }

            ParseUtils.requireNoContent(reader);
        }

        private void parseAuditLogElement(XMLExtendedStreamReader reader, List<ModelNode> list) throws XMLStreamException {

            ModelNode op = createOperation(ADD, JmxAuditLoggerResourceDefinition.PATH_ELEMENT.getKey(), JmxAuditLoggerResourceDefinition.PATH_ELEMENT.getValue());

            final int count = reader.getAttributeCount();
            for (int i = 0; i < count; i++) {
                final String value = reader.getAttributeValue(i);
                final String attribute = reader.getAttributeLocalName(i);
                switch (attribute) {
                    case CommonAttributes.LOG_BOOT: {
                        JmxAuditLoggerResourceDefinition.LOG_BOOT.parseAndSetParameter(value, op, reader);
                        break;
                    }
                    case CommonAttributes.LOG_READ_ONLY: {
                        JmxAuditLoggerResourceDefinition.LOG_READ_ONLY.parseAndSetParameter(value, op, reader);
                        break;
                    }
                    case CommonAttributes.ENABLED: {
                        JmxAuditLoggerResourceDefinition.ENABLED.parseAndSetParameter(value, op, reader);
                        break;
                    }
                    default: {
                        throw ParseUtils.unexpectedAttribute(reader, i);
                    }
                }
            }
            list.add(op);

            while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
                final String element = reader.getLocalName();
                switch (element) {
                    case CommonAttributes.HANDLERS:
                        parseAuditLogHandlers(reader, list);
                        break;
                    default: {
                        throw ParseUtils.unexpectedElement(reader);
                    }
                }
            }
        }

        private void parseAuditLogHandlers(XMLExtendedStreamReader reader, List<ModelNode> list) throws XMLStreamException {
            ParseUtils.requireNoAttributes(reader);

            while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
                final String element = reader.getLocalName();
                switch (element) {
                    case HANDLER:
                        parseAuditLogHandler(reader, list);
                        break;
                    default: {
                        throw ParseUtils.unexpectedElement(reader);
                    }
                }
            }
        }

        private void parseAuditLogHandler(XMLExtendedStreamReader reader, List<ModelNode> list) throws XMLStreamException {

            String name = null;
            final int count = reader.getAttributeCount();
            for (int i = 0; i < count; i++) {
                final String value = reader.getAttributeValue(i);
                final String attribute = reader.getAttributeLocalName(i);
                switch (attribute) {
                    case NAME:
                        name = value;
                        break;
                    default:
                        throw ParseUtils.unexpectedAttribute(reader, i);
                }
            }

            if (name == null) {
                throw ParseUtils.missingRequired(reader, Collections.singleton(NAME));
            }
            ModelNode op = createOperation(ADD,
                    JmxAuditLoggerResourceDefinition.PATH_ELEMENT.getKey(),
                    JmxAuditLoggerResourceDefinition.PATH_ELEMENT.getValue(),
                    JmxAuditLogHandlerReferenceResourceDefinition.PATH_ELEMENT.getKey(), name);
            list.add(op);

            ParseUtils.requireNoContent(reader);
        }
    }


    private static class JMXSubsystemWriter implements XMLStreamConstants, XMLElementWriter<SubsystemMarshallingContext> {
        /**
         * {@inheritDoc}
         */
        @Override
        public void writeContent(XMLExtendedStreamWriter writer, SubsystemMarshallingContext context) throws XMLStreamException {
            Namespace schemaVer = Namespace.CURRENT;
            final ModelNode node = context.getModelNode();

            context.startSubsystemElement(schemaVer.getUriString(), false);
            if (node.hasDefined(CommonAttributes.EXPOSE_MODEL)) {
                ModelNode showModel = node.get(CommonAttributes.EXPOSE_MODEL);
                if (showModel.hasDefined(CommonAttributes.RESOLVED)) {
                    writer.writeEmptyElement(CommonAttributes.EXPOSE_RESOLVED_MODEL);
                    ExposeModelResourceResolved.DOMAIN_NAME.marshallAsAttribute(showModel.get(CommonAttributes.RESOLVED), false, writer);
                    ExposeModelResourceResolved.PROPER_PROPERTY_FORMAT.marshallAsAttribute(showModel.get(CommonAttributes.RESOLVED), false, writer);
                }
                if (showModel.hasDefined(CommonAttributes.EXPRESSION)) {
                    writer.writeEmptyElement(CommonAttributes.EXPOSE_EXPRESSION_MODEL);
                    ExposeModelResourceExpression.DOMAIN_NAME.marshallAsAttribute(showModel.get(CommonAttributes.EXPRESSION), false, writer);
                }
            }
            if (node.hasDefined(CommonAttributes.REMOTING_CONNECTOR)) {
                writer.writeStartElement(CommonAttributes.REMOTING_CONNECTOR);
                final ModelNode resourceModel = node.get(CommonAttributes.REMOTING_CONNECTOR).get(CommonAttributes.JMX);
                RemotingConnectorResource.USE_MANAGEMENT_ENDPOINT.marshallAsAttribute(resourceModel, writer);
                writer.writeEndElement();
            }

            if (node.hasDefined(JmxAuditLoggerResourceDefinition.PATH_ELEMENT.getKey()) &&
                    node.get(JmxAuditLoggerResourceDefinition.PATH_ELEMENT.getKey()).hasDefined(JmxAuditLoggerResourceDefinition.PATH_ELEMENT.getValue())) {
                ModelNode auditLog = node.get(JmxAuditLoggerResourceDefinition.PATH_ELEMENT.getKey(), JmxAuditLoggerResourceDefinition.PATH_ELEMENT.getValue());
                writer.writeStartElement(CommonAttributes.AUDIT_LOG);
                JmxAuditLoggerResourceDefinition.LOG_BOOT.marshallAsAttribute(auditLog, writer);
                JmxAuditLoggerResourceDefinition.LOG_READ_ONLY.marshallAsAttribute(auditLog, writer);
                JmxAuditLoggerResourceDefinition.ENABLED.marshallAsAttribute(auditLog, writer);

                if (auditLog.hasDefined(HANDLER) && !auditLog.get(HANDLER).keys().isEmpty()) {
                    writer.writeStartElement(CommonAttributes.HANDLERS);
                    for (String key : auditLog.get(HANDLER).keys()) {
                        writer.writeEmptyElement(CommonAttributes.HANDLER);
                        writer.writeAttribute(CommonAttributes.NAME, key);
                    }
                    writer.writeEndElement();
                }

                writer.writeEndElement();
            }
            if (node.hasDefined(JMXSubsystemRootResource.NON_CORE_MBEAN_SENSITIVITY.getName())) {
                writer.writeStartElement(CommonAttributes.SENSITIVITY);
                JMXSubsystemRootResource.NON_CORE_MBEAN_SENSITIVITY.marshallAsAttribute(node, writer);
                writer.writeEndElement();
            }
            writer.writeEndElement();
        }
    }

}
