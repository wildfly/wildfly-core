/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.parsing;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAMESPACES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SCHEMA_LOCATIONS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.controller.parsing.ParseUtils.invalidAttributeValue;
import static org.jboss.as.controller.parsing.XmlConstants.XML_SCHEMA_NAMESPACE;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.xml.XMLConstants;
import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.operations.common.NamespaceAddHandler;
import org.jboss.as.controller.operations.common.SchemaLocationAddHandler;
import org.jboss.as.controller.parsing.Attribute;
import org.jboss.as.controller.parsing.Element;
import org.jboss.as.controller.parsing.Namespace;
import org.jboss.as.controller.persistence.ModelMarshallingContext;
import org.jboss.as.controller.persistence.SubsystemMarshallingContext;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.jboss.staxmapper.IntVersion;
import org.jboss.staxmapper.XMLElementReader;
import org.jboss.staxmapper.XMLElementWriter;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;

/**
 * Bits of parsing and marshalling logic that are common across more than one of standalone.xml, domain.xml and host.xml.
 *
 * Note: On adding version specific parse methods to this class these MUST be private and the existing non-versioned method
 * handle the version switch.  This class is used by WildFly so we need to ensure the methods made accessible are those
 * that can be used and will not be renamed.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public abstract class CommonXml implements XMLElementReader<List<ModelNode>> {

    /*
     * When subsequent major versions are added these will be forked and loaded on-demand, may be better to make it
     * on-demand immediately as if the config does not contain the item the parsing code will not need to be loaded.
     */

    private final DeploymentOverlaysXml deploymentOverlaysXml = new DeploymentOverlaysXml();
    private final DeploymentsXml deploymentsXml = new DeploymentsXml();
    private final InterfacesXml interfacesXml = new InterfacesXml();
    private final PathsXml pathsXml = new PathsXml();
    private final SocketBindingsXml socketBindingsXml;
    private final SystemPropertiesXml systemPropertiesXml = new SystemPropertiesXml();
    private final VaultXml vaultXml = new VaultXml();

    protected CommonXml(SocketBindingsXml socketBindingsXml) {
        this.socketBindingsXml = socketBindingsXml;
    }

    protected void parseNamespaces(final XMLExtendedStreamReader reader, final ModelNode address, final List<ModelNode> nodes) {
        final int namespaceCount = reader.getNamespaceCount();
        for (int i = 0; i < namespaceCount; i++) {
            String prefix = reader.getNamespacePrefix(i);
            // FIXME - remove once STXM-8 is released
            if (prefix != null && prefix.length() > 0) {
                nodes.add(NamespaceAddHandler.getAddNamespaceOperation(address, prefix, reader.getNamespaceURI(i)));
            }
        }
    }

    protected void parseSchemaLocations(final XMLExtendedStreamReader reader, final ModelNode address,
                                        final List<ModelNode> updateList, final int idx) throws XMLStreamException {
        final List<String> elements = reader.getListAttributeValue(idx);
        final List<String> values = new ArrayList<String>();
        for (String element : elements) {
            if (!element.trim().isEmpty()) {
                values.add(element);
            }
        }
        if ((values.size() & 1) != 0) {
            throw invalidAttributeValue(reader, idx);
        }
        final Iterator<String> it = values.iterator();
        while (it.hasNext()) {
            String key = it.next();
            String val = it.next();
            if (key.length() > 0 && val.length() > 0) {
                updateList.add(SchemaLocationAddHandler.getAddSchemaLocationOperation(address, key, val));
            }
        }
    }

    protected void writeSchemaLocation(final XMLExtendedStreamWriter writer, final ModelNode modelNode)
            throws XMLStreamException {
        if (!modelNode.hasDefined(SCHEMA_LOCATIONS)) {
            return;
        }
        final StringBuilder b = new StringBuilder();
        final Iterator<ModelNode> iterator = modelNode.get(SCHEMA_LOCATIONS).asList().iterator();
        while (iterator.hasNext()) {
            final ModelNode location = iterator.next();
            final Property property = location.asProperty();
            b.append(property.getName()).append(' ').append(property.getValue().asString());
            if (iterator.hasNext()) {
                b.append(' ');
            }
        }
        if (b.length() > 0) {
            writer.writeAttribute(XML_SCHEMA_NAMESPACE, Attribute.SCHEMA_LOCATION.getLocalName(),
                    b.toString());
        }
    }

    protected void writeNamespaces(final XMLExtendedStreamWriter writer, final ModelNode modelNode) throws XMLStreamException {
        final boolean needXsd = modelNode.hasDefined(SCHEMA_LOCATIONS) && modelNode.get(SCHEMA_LOCATIONS).asInt() > 0;
        final boolean hasNamespaces = modelNode.hasDefined(NAMESPACES);
        if (!needXsd && !hasNamespaces) {
            return;
        }

        boolean wroteXsd = false;
        if (hasNamespaces) {
            for (final Property property : modelNode.get(NAMESPACES).asPropertyList()) {
                final String uri = property.getValue().asString();
                writer.writeNamespace(property.getName(), uri);
                if (!wroteXsd && XML_SCHEMA_NAMESPACE.equals(uri)) {
                    wroteXsd = true;
                }
            }
        }
        if (needXsd && !wroteXsd) {
            writer.writeNamespace("xsd", XML_SCHEMA_NAMESPACE);
        }
    }

    protected void writePaths(final XMLExtendedStreamWriter writer, final ModelNode node, final boolean namedPath) throws XMLStreamException {
        pathsXml.writePaths(writer, node, namedPath);
    }

    @Deprecated
    protected void parsePaths(final XMLExtendedStreamReader reader, final ModelNode address, final Namespace namespace, final List<ModelNode> list,
            final boolean requirePath) throws XMLStreamException {
        parsePaths(reader, address, namespace.getUriString(), list, requirePath);
    }

    protected void parsePaths(final XMLExtendedStreamReader reader, final ModelNode address, final String expectedNs, final List<ModelNode> list,
            final boolean requirePath) throws XMLStreamException {
        pathsXml.parsePaths(reader, address, expectedNs, list, requirePath);
    }

    @Deprecated
    protected void parseSystemProperties(final XMLExtendedStreamReader reader, final ModelNode address, final Namespace namespace,
            final List<ModelNode> updates, boolean standalone) throws XMLStreamException {
        parseSystemProperties(reader, address, namespace.getUriString(), updates, standalone);
    }
    protected void parseSystemProperties(final XMLExtendedStreamReader reader, final ModelNode address, final String expectedNs,
            final List<ModelNode> updates, boolean standalone) throws XMLStreamException {
        systemPropertiesXml.parseSystemProperties(reader, address, expectedNs, updates, standalone);
    }

    @Deprecated
    protected void parseInterfaces(final XMLExtendedStreamReader reader, final Set<String> names, final ModelNode address,
            final Namespace namespace, final List<ModelNode> list, final boolean checkSpecified) throws XMLStreamException {
        String namespaceUri = namespace.getUriString();
        String versionString = namespaceUri.substring(namespaceUri.lastIndexOf(':') + 1);
        String[] versionSegments = versionString.split(".");
        int[] versionInts = new int[versionSegments.length];
        for (int i = 0; i < versionSegments.length; i++) {
            versionInts[i] = Integer.parseInt(versionSegments[i]);
        }
        IntVersion version = new IntVersion(versionInts);

        parseInterfaces(reader, names, address, version, namespace.getUriString(), list, checkSpecified);
    }

    protected void parseInterfaces(final XMLExtendedStreamReader reader, final Set<String> names, final ModelNode address,
            final IntVersion version, final String expectedNs, final List<ModelNode> list, final boolean checkSpecified) throws XMLStreamException {
        interfacesXml.parseInterfaces(reader, names, address, version, expectedNs, list, checkSpecified);
    }

    protected void parseSocketBindingGroupRef(final XMLExtendedStreamReader reader, final ModelNode addOperation,
            final SimpleAttributeDefinition socketBindingGroup,
            final SimpleAttributeDefinition portOffset,
            final SimpleAttributeDefinition defaultInterface) throws XMLStreamException {
        socketBindingsXml.parseSocketBindingGroupRef(reader, addOperation, socketBindingGroup, portOffset, defaultInterface);
    }

    protected String parseSocketBinding(final XMLExtendedStreamReader reader, final Set<String> interfaces,
            final ModelNode address, final List<ModelNode> updates) throws XMLStreamException {
        return socketBindingsXml.parseSocketBinding(reader, interfaces, address, updates);
    }

    protected String parseOutboundSocketBinding(final XMLExtendedStreamReader reader, final Set<String> interfaces,
            final ModelNode address, final List<ModelNode> updates) throws XMLStreamException {
        return socketBindingsXml.parseOutboundSocketBinding(reader, interfaces, address, updates);
    }

    public void parseDeployments(final XMLExtendedStreamReader reader, final ModelNode address, final String expectedNs,
            final List<ModelNode> list, final Set<Attribute> allowedAttributes, final Set<Element> allowedElements,
            boolean validateUniqueRuntimeNames) throws XMLStreamException {
        deploymentsXml.parseDeployments(reader, address, expectedNs, list, allowedAttributes, allowedElements, validateUniqueRuntimeNames);
    }

    protected void parseDeploymentOverlays(final XMLExtendedStreamReader reader, final String namespace, final ModelNode baseAddress, final List<ModelNode> list, final boolean allowContent, final boolean allowDeployment) throws XMLStreamException {
        deploymentOverlaysXml.parseDeploymentOverlays(reader, namespace, baseAddress, list, allowContent, allowDeployment);
    }

    @Deprecated
    protected void parseVault(final XMLExtendedStreamReader reader, final ModelNode address, final Namespace namespace, final List<ModelNode> list) throws XMLStreamException {
        parseVault(reader, address, namespace.getUriString(), list);
    }

    protected void parseVault(final XMLExtendedStreamReader reader, final ModelNode address, final String expectedNs, final List<ModelNode> list) throws XMLStreamException {
        vaultXml.parseVault(reader, address, expectedNs, list);
    }

    /**
     * Write the interfaces including the criteria elements.
     *
     * @param writer    the xml stream writer
     * @param modelNode the model
     * @throws XMLStreamException
     */
    protected void writeInterfaces(final XMLExtendedStreamWriter writer, final ModelNode modelNode) throws XMLStreamException {
        interfacesXml.writeInterfaces(writer, modelNode);
    }

    protected void writeSocketBindingGroup(XMLExtendedStreamWriter writer, ModelNode bindingGroup, String name)
            throws XMLStreamException {
        socketBindingsXml.writeSocketBindingGroup(writer, bindingGroup, name);
    }

    protected void writeProperties(final XMLExtendedStreamWriter writer, final ModelNode modelNode, Element element,
            boolean standalone) throws XMLStreamException {
        systemPropertiesXml.writeProperties(writer, modelNode, element, standalone);
    }

    protected static void writeSubsystems(final ModelNode profileNode, final XMLExtendedStreamWriter writer,
                                          final ModelMarshallingContext context) throws XMLStreamException {

        if (profileNode.hasDefined(SUBSYSTEM)) {
            Set<String> subsystemNames = profileNode.get(SUBSYSTEM).keys();
            if (!subsystemNames.isEmpty()) {
                // establish traditional 'logging then alphabetical' ordering
                // note that logging first is just tradition; it's not necessary technically
                Set<String> alphabetical = new TreeSet<>(subsystemNames);
                if (alphabetical.contains("logging")) {
                    subsystemNames = new LinkedHashSet<>();
                    subsystemNames.add("logging");
                    subsystemNames.addAll(alphabetical);
                } else {
                    subsystemNames = alphabetical;
                }

                String defaultNamespace = writer.getNamespaceContext().getNamespaceURI(XMLConstants.DEFAULT_NS_PREFIX);
                for (String subsystemName : subsystemNames) {
                    try {
                        ModelNode subsystem = profileNode.get(SUBSYSTEM, subsystemName);
                        XMLElementWriter<SubsystemMarshallingContext> subsystemWriter = context.getSubsystemWriter(subsystemName);
                        if (subsystemWriter != null) { // FIXME -- remove when extensions are doing the registration
                            subsystemWriter.writeContent(writer, new SubsystemMarshallingContext(subsystem, writer));
                        }
                    } finally {
                        writer.setDefaultNamespace(defaultNamespace);
                    }
                }
            }
        }
    }

    protected static void writeContentItem(final XMLExtendedStreamWriter writer, final ModelNode contentItem)
            throws XMLStreamException {
        DeploymentsXml.writeContentItem(writer, contentItem);
    }

    protected void writeDeploymentOverlays(final XMLExtendedStreamWriter writer, final ModelNode modelNode) throws XMLStreamException {
        deploymentOverlaysXml.writeDeploymentOverlays(writer, modelNode);
    }

    protected static ModelNode parseAttributeValue(AttributeDefinition ad, String value, XMLExtendedStreamReader reader) throws XMLStreamException {
        return ad.getParser().parse(ad,value,reader);
    }

}
