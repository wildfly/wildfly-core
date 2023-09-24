/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deployment.scanner;

import java.util.Collections;
import java.util.List;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.parsing.ParseUtils;
import org.jboss.as.controller.persistence.SubsystemMarshallingContext;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.jboss.staxmapper.XMLElementReader;
import org.jboss.staxmapper.XMLElementWriter;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.parsing.ParseUtils.requireNoAttributes;
import static org.jboss.as.controller.parsing.ParseUtils.requireNoContent;
import static org.jboss.as.controller.parsing.ParseUtils.requireNoNamespaceAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedElement;
import static org.jboss.as.server.deployment.scanner.CommonAttributes.AUTO_DEPLOY_EXPLODED;
import static org.jboss.as.server.deployment.scanner.CommonAttributes.AUTO_DEPLOY_ZIPPED;
import static org.jboss.as.server.deployment.scanner.CommonAttributes.DEPLOYMENT_TIMEOUT;
import static org.jboss.as.server.deployment.scanner.CommonAttributes.NAME;
import static org.jboss.as.server.deployment.scanner.CommonAttributes.PATH;
import static org.jboss.as.server.deployment.scanner.CommonAttributes.RELATIVE_TO;
import static org.jboss.as.server.deployment.scanner.CommonAttributes.SCAN_ENABLED;
import static org.jboss.as.server.deployment.scanner.CommonAttributes.SCAN_INTERVAL;

/**
*/
class DeploymentScannerParser_1_0 implements XMLStreamConstants, XMLElementReader<List<ModelNode>>, XMLElementWriter<SubsystemMarshallingContext> {

    /** {@inheritDoc} */
    @Override
    public void writeContent(XMLExtendedStreamWriter writer, SubsystemMarshallingContext context) throws XMLStreamException {
        context.startSubsystemElement(Namespace.CURRENT.getUriString(), false);
        ModelNode scanners = context.getModelNode();
        for (final Property list : scanners.asPropertyList()) {
            final ModelNode node = list.getValue();

            for (final Property scanner : node.asPropertyList()) {

                writer.writeEmptyElement(CommonAttributes.DEPLOYMENT_SCANNER);
                writer.writeAttribute(CommonAttributes.NAME, scanner.getName());
                ModelNode configuration = scanner.getValue();
                if (configuration.hasDefined(PATH)) {
                    writer.writeAttribute(PATH, configuration.get(PATH)
                            .asString());
                }
                if (configuration.hasDefined(CommonAttributes.SCAN_ENABLED)) {
                    writer.writeAttribute(CommonAttributes.SCAN_ENABLED,
                            configuration.get(CommonAttributes.SCAN_ENABLED).asString());
                }
                if (configuration.hasDefined(CommonAttributes.SCAN_INTERVAL)) {
                    writer.writeAttribute(CommonAttributes.SCAN_INTERVAL,
                            configuration.get(CommonAttributes.SCAN_INTERVAL).asString());
                }
                if (configuration.hasDefined(CommonAttributes.RELATIVE_TO)) {
                    writer.writeAttribute(CommonAttributes.RELATIVE_TO,
                            configuration.get(CommonAttributes.RELATIVE_TO).asString());
                }
                if (configuration.hasDefined(CommonAttributes.AUTO_DEPLOY_ZIPPED)) {
                    if (!configuration.get(CommonAttributes.AUTO_DEPLOY_ZIPPED).asBoolean()) {
                        writer.writeAttribute(CommonAttributes.AUTO_DEPLOY_ZIPPED, Boolean.FALSE.toString());
                    }
                }
                if (configuration.hasDefined(CommonAttributes.AUTO_DEPLOY_EXPLODED)) {
                    if (configuration.get(CommonAttributes.AUTO_DEPLOY_EXPLODED).asBoolean()) {
                        writer.writeAttribute(CommonAttributes.AUTO_DEPLOY_EXPLODED, Boolean.TRUE.toString());
                    }
                }
                if (configuration.hasDefined(CommonAttributes.DEPLOYMENT_TIMEOUT)) {
                    writer.writeAttribute(CommonAttributes.DEPLOYMENT_TIMEOUT, configuration.get(CommonAttributes.DEPLOYMENT_TIMEOUT).asString());
                }
            }
            writer.writeEndElement();
        }
    }

    /** {@inheritDoc} */
    @Override
    public void readElement(XMLExtendedStreamReader reader, List<ModelNode> list) throws XMLStreamException {
        // no attributes
        requireNoAttributes(reader);

        final ModelNode address = new ModelNode();
        address.add(ModelDescriptionConstants.SUBSYSTEM, DeploymentScannerExtension.SUBSYSTEM_NAME);
        address.protect();

        final ModelNode subsystem = new ModelNode();
        subsystem.get(OP).set(ADD);
        subsystem.get(OP_ADDR).set(address);
        list.add(subsystem);

        // elements
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            switch (Namespace.forUri(reader.getNamespaceURI())) {
                case DEPLOYMENT_SCANNER_1_0: {
                    final String element = reader.getLocalName();
                    switch (element) {
                        case CommonAttributes.DEPLOYMENT_SCANNER: {
                            //noinspection unchecked
                            parseScanner(reader, address, list);
                            break;
                        }
                        default: throw unexpectedElement(reader);
                    }
                    break;
                }
                default: throw unexpectedElement(reader);
            }
        }
    }

    void parseScanner(XMLExtendedStreamReader reader, final ModelNode address, List<ModelNode> list) throws XMLStreamException {
        // Handle attributes
        Boolean enabled = null;
        Integer interval = null;
        String path = null;
        String name = DeploymentScannerExtension.DEFAULT_SCANNER_NAME;
        String relativeTo = null;
        Boolean autoDeployZipped = null;
        Boolean autoDeployExploded = null;
        Long deploymentTimeout = null;
        final int attrCount = reader.getAttributeCount();
        for (int i = 0; i < attrCount; i++) {
            requireNoNamespaceAttribute(reader, i);
            final String value = reader.getAttributeValue(i);
            final String attribute = reader.getAttributeLocalName(i);
            switch (attribute) {
                case PATH: {
                    path = value;
                    break;
                }
                case NAME: {
                    name = value;
                    break;
                }
                case RELATIVE_TO: {
                    relativeTo = value;
                    break;
                }
                case SCAN_INTERVAL: {
                    interval = Integer.parseInt(value);
                    break;
                }
                case SCAN_ENABLED: {
                    enabled = Boolean.parseBoolean(value);
                    break;
                }
                case AUTO_DEPLOY_ZIPPED: {
                    autoDeployZipped = Boolean.parseBoolean(value);
                    break;
                }
                case AUTO_DEPLOY_EXPLODED: {
                    autoDeployExploded = Boolean.parseBoolean(value);
                    break;
                }
                case DEPLOYMENT_TIMEOUT: {
                    deploymentTimeout = Long.parseLong(value);
                    break;
                }
                default:
                    throw ParseUtils.unexpectedAttribute(reader, i);
            }
        }
        if (name == null) {
            throw ParseUtils.missingRequired(reader, Collections.singleton(CommonAttributes.NAME));
        }
        if (path == null) {
            throw ParseUtils.missingRequired(reader, Collections.singleton(PATH));
        }
        requireNoContent(reader);

        final ModelNode operation = new ModelNode();
        operation.get(OP).set(ADD);
        operation.get(OP_ADDR).set(address).add(CommonAttributes.SCANNER, name);
        operation.get(PATH).set(path);
        if (interval != null) operation.get(CommonAttributes.SCAN_INTERVAL).set(interval.intValue());
        if (autoDeployZipped != null) operation.get(CommonAttributes.AUTO_DEPLOY_ZIPPED).set(autoDeployZipped.booleanValue());
        if (autoDeployExploded != null) operation.get(CommonAttributes.AUTO_DEPLOY_EXPLODED).set(autoDeployExploded.booleanValue());
        if (enabled != null) operation.get(CommonAttributes.SCAN_ENABLED).set(enabled.booleanValue());
        if(relativeTo != null) operation.get(CommonAttributes.RELATIVE_TO).set(relativeTo);
        if(deploymentTimeout != null) operation.get(CommonAttributes.DEPLOYMENT_TIMEOUT).set(deploymentTimeout);
        list.add(operation);
    }

}
