/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.parsing;

import static javax.xml.stream.XMLStreamConstants.END_ELEMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.BOOT_TIME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SYSTEM_PROPERTY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;
import static org.jboss.as.controller.parsing.ParseUtils.isNoNamespaceAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.requireNamespace;
import static org.jboss.as.controller.parsing.ParseUtils.requireNoContent;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedElement;

import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.ExpressionResolver;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.parsing.Element;
import org.jboss.as.controller.parsing.Namespace;
import org.jboss.as.controller.parsing.ParseUtils;
import org.jboss.as.server.controller.resources.SystemPropertyResourceDefinition;
import org.jboss.as.server.logging.ServerLogger;
import org.jboss.as.server.operations.SystemPropertyAddHandler;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * Parsing and marshalling logic specific to system properties.
 *
 * The contents of this file have been pulled from {@see CommonXml}, see the commit history of that file for true author
 * attribution.
 *
 * Note: This class is only indented to support versions 1, 2, and 3 of the schema, if later major versions of the schema
 * include updates to the types represented by this class then this class should be forked.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class SystemPropertiesXml {

    void parseSystemProperties(final XMLExtendedStreamReader reader, final ModelNode address,
            final Namespace expectedNs, final List<ModelNode> updates, boolean standalone) throws XMLStreamException {

        Properties properties = WildFlySecurityManager.getSystemPropertiesPrivileged();

        while (reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, expectedNs);
            final Element element = Element.forName(reader.getLocalName());
            if (element != Element.PROPERTY) {
                throw unexpectedElement(reader);
            }

            boolean setName = false;
            boolean setValue = false;
            boolean setBoottime = false;
            // Will set OP_ADDR after parsing the NAME attribute
            ModelNode op = Util.getEmptyOperation(SystemPropertyAddHandler.OPERATION_NAME, new ModelNode());
            final int count = reader.getAttributeCount();
            String name = null;
            for (int i = 0; i < count; i++) {
                final String val = reader.getAttributeValue(i);
                if (!isNoNamespaceAttribute(reader, i)) {
                    throw unexpectedAttribute(reader, i);
                } else {
                    final String attribute = reader.getAttributeLocalName(i);

                    switch (attribute) {
                        case NAME: {
                            if (setName) {
                                throw ParseUtils.duplicateAttribute(reader, NAME);
                            }
                            setName = true;
                            ModelNode addr = new ModelNode().set(address).add(SYSTEM_PROPERTY, val);
                            op.get(OP_ADDR).set(addr);
                            name = val;
                            break;
                        }
                        case VALUE: {
                            if (setValue) {
                                throw ParseUtils.duplicateAttribute(reader, VALUE);
                            }
                            setValue = true;
                            SystemPropertyResourceDefinition.VALUE.parseAndSetParameter(val, op, reader);
                            break;
                        }
                        case BOOT_TIME: {
                            if (standalone) {
                                throw unexpectedAttribute(reader, i);
                            }
                            if (setBoottime) {
                                throw ParseUtils.duplicateAttribute(reader, BOOT_TIME);
                            }
                            setBoottime = true;
                            SystemPropertyResourceDefinition.BOOT_TIME.parseAndSetParameter(val, op, reader);
                            break;
                        }
                        default: {
                            throw unexpectedAttribute(reader, i);
                        }
                    }
                }
            }
            requireNoContent(reader);
            if (!setName) {
                throw ParseUtils.missingRequired(reader, Collections.singleton(NAME));
            }

            AtomicReference<String> newPropertyValue = null;
            try {
                String resolved = SystemPropertyResourceDefinition.VALUE.resolveValue(ExpressionResolver.EXTENSION_REJECTING, op.get(VALUE)).asStringOrNull();
                newPropertyValue = new AtomicReference<>(resolved);
                String oldPropertyValue = properties.getProperty(name);
                if (oldPropertyValue != null && !oldPropertyValue.equals(resolved)) {
                    ControllerLogger.ROOT_LOGGER.systemPropertyAlreadyExist(name);
                }
            } catch (OperationFailedException | ExpressionResolver.ExpressionResolutionUserException | ExpressionResolver.ExpressionResolutionServerException e) {
                ServerLogger.AS_ROOT_LOGGER.tracef(e, "Failed to resolve value for system property %s at parse time.", name);
            }

            if(standalone) {
                //eagerly set the property so it can potentially be used by jboss modules
                //only do this for standalone servers
                if (newPropertyValue != null) {
                    String val = newPropertyValue.get();
                    if (val != null) {
                        System.setProperty(name, newPropertyValue.get());
                    } else {
                        System.clearProperty(name);
                    }
                } else {
                    ServerLogger.AS_ROOT_LOGGER.tracef("Failed to set property %s at parse time, it will be set later in the boot process", name);
                }
            }

            updates.add(op);
        }
    }

    void writeProperties(final XMLExtendedStreamWriter writer, final ModelNode modelNode, Element element,
            boolean standalone) throws XMLStreamException {
        final List<Property> properties = modelNode.asPropertyList();
        if (!properties.isEmpty()) {
            writer.writeStartElement(element.getLocalName());
            for (Property prop : properties) {
                writer.writeStartElement(Element.PROPERTY.getLocalName());
                writer.writeAttribute(NAME, prop.getName());
                ModelNode sysProp = prop.getValue();
                SystemPropertyResourceDefinition.VALUE.marshallAsAttribute(sysProp, writer);
                if (!standalone) {
                    SystemPropertyResourceDefinition.BOOT_TIME.marshallAsAttribute(sysProp, writer);
                }

                writer.writeEndElement();
            }
            writer.writeEndElement();
        }
    }

}
