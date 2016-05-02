/*
Copyright 2016 Red Hat, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */

package org.jboss.as.host.controller.parsing;

import static javax.xml.stream.XMLStreamConstants.END_ELEMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ACCESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.AUTHORIZATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CONTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT_OVERLAY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DOMAIN_ORGANIZATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EXTENSION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HASH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST_EXCLUDE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INCLUDES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INTERFACE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.JVM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT_CLIENT_CONTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PATH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ROLLOUT_PLANS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOCKET_BINDING_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOCKET_BINDING_PORT_OFFSET;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SYSTEM_PROPERTY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VALUE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.WRITE_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.parsing.Namespace.CURRENT;
import static org.jboss.as.controller.parsing.ParseUtils.isNoNamespaceAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.missingRequired;
import static org.jboss.as.controller.parsing.ParseUtils.missingRequiredElement;
import static org.jboss.as.controller.parsing.ParseUtils.nextElement;
import static org.jboss.as.controller.parsing.ParseUtils.readStringAttributeElement;
import static org.jboss.as.controller.parsing.ParseUtils.requireNamespace;
import static org.jboss.as.controller.parsing.ParseUtils.requireNoAttributes;
import static org.jboss.as.controller.parsing.ParseUtils.requireNoContent;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedElement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.HashUtil;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.parsing.Attribute;
import org.jboss.as.controller.parsing.Element;
import org.jboss.as.controller.parsing.ExtensionXml;
import org.jboss.as.controller.parsing.Namespace;
import org.jboss.as.controller.parsing.ParseUtils;
import org.jboss.as.controller.parsing.ProfileParsingCompletionHandler;
import org.jboss.as.controller.parsing.WriteUtils;
import org.jboss.as.controller.persistence.ModelMarshallingContext;
import org.jboss.as.controller.persistence.SubsystemMarshallingContext;
import org.jboss.as.controller.resource.AbstractSocketBindingGroupResourceDefinition;
import org.jboss.as.domain.controller.logging.DomainControllerLogger;
import org.jboss.as.domain.controller.operations.SocketBindingGroupResourceDefinition;
import org.jboss.as.domain.controller.resources.DomainRootDefinition;
import org.jboss.as.domain.controller.resources.HostExcludeResourceDefinition;
import org.jboss.as.domain.controller.resources.ProfileResourceDefinition;
import org.jboss.as.domain.controller.resources.ServerGroupResourceDefinition;
import org.jboss.as.domain.management.access.AccessAuthorizationResourceDefinition;
import org.jboss.as.domain.management.parsing.AccessControlXml;
import org.jboss.as.domain.management.parsing.ManagementXml;
import org.jboss.as.domain.management.parsing.ManagementXmlDelegate;
import org.jboss.as.server.controller.resources.DeploymentAttributes;
import org.jboss.as.server.parsing.CommonXml;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.Property;
import org.jboss.staxmapper.XMLElementWriter;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;

/**
 * A mapper between an AS server's configuration model and XML representations, particularly {@code domain.xml}.
 *
 * @author Emanuel Muckenhuber
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 * @author Brian Stansberry
 */
public class DomainXml_5 extends CommonXml implements ManagementXmlDelegate {

    private final AccessControlXml accessControlXml;

    private final Namespace namespace;
    private final ExtensionXml extensionXml;
    private final ExtensionRegistry extensionRegistry;

    DomainXml_5(final ExtensionXml extensionXml, final ExtensionRegistry extensionRegistry, final Namespace namespace) {
        super(new DomainSocketBindingsXml());
        accessControlXml = AccessControlXml.newInstance(namespace);
        this.extensionXml = extensionXml;
        this.extensionRegistry = extensionRegistry;
        this.namespace = namespace;
    }

    @Override
    public void readElement(final XMLExtendedStreamReader reader, final List<ModelNode> nodes) throws XMLStreamException {
        if (Element.forName(reader.getLocalName()) != Element.DOMAIN) {
            throw unexpectedElement(reader);
        }
        // Instead of having to list the remaining versions we just check it is actually a valid version.
        for (Namespace current : Namespace.domainValues()) {
            if (namespace.equals(current)) {
                readDomainElement(reader, new ModelNode(), nodes);
                return;
            }
        }
        throw unexpectedElement(reader);
    }

    @Override
    public void writeContent(final XMLExtendedStreamWriter writer, final ModelMarshallingContext context) throws XMLStreamException {
        ModelNode modelNode = context.getModelNode();

        writer.writeStartDocument();
        writer.writeStartElement(Element.DOMAIN.getLocalName());

        writer.writeDefaultNamespace(CURRENT.getUriString());
        writeNamespaces(writer, modelNode);
        writeSchemaLocation(writer, modelNode);

        DomainRootDefinition.NAME.marshallAsAttribute(modelNode, false, writer);
        if (modelNode.hasDefined(DOMAIN_ORGANIZATION)) {
            DomainRootDefinition.ORGANIZATION_IDENTIFIER.marshallAsAttribute(modelNode, writer);
        }

        WriteUtils.writeNewLine(writer);
        if (modelNode.hasDefined(EXTENSION)) {
            extensionXml.writeExtensions(writer, modelNode.get(EXTENSION));
            WriteUtils.writeNewLine(writer);
        }
        if (modelNode.hasDefined(SYSTEM_PROPERTY)) {
            writeProperties(writer, modelNode.get(SYSTEM_PROPERTY), Element.SYSTEM_PROPERTIES, false);
            WriteUtils.writeNewLine(writer);
        }
        if (modelNode.hasDefined(PATH)) {
            writePaths(writer, modelNode.get(PATH), true);
            WriteUtils.writeNewLine(writer);
        }

        if (modelNode.hasDefined(CORE_SERVICE) && modelNode.get(CORE_SERVICE).hasDefined(MANAGEMENT)) {
            // We use CURRENT here as we only support writing the most recent.
            ManagementXml managementXml = ManagementXml.newInstance(CURRENT, this);
            managementXml.writeManagement(writer, modelNode.get(CORE_SERVICE, MANAGEMENT), true);
            WriteUtils.writeNewLine(writer);
        }

        if (modelNode.hasDefined(PROFILE)) {
            writer.writeStartElement(Element.PROFILES.getLocalName());
            ModelNode profiles = modelNode.get(PROFILE);
            for (final String profile : profiles.keys()) {
                writeProfile(writer, profile, profiles.get(profile), context);
            }
            writer.writeEndElement();
            WriteUtils.writeNewLine(writer);
        }
        if (modelNode.hasDefined(INTERFACE)) {
            writeInterfaces(writer, modelNode.get(INTERFACE));
            WriteUtils.writeNewLine(writer);
        }
        if (modelNode.hasDefined(SOCKET_BINDING_GROUP)) {
            writer.writeStartElement(Element.SOCKET_BINDING_GROUPS.getLocalName());
            ModelNode sbgs = modelNode.get(SOCKET_BINDING_GROUP);
            for (final String sbg : sbgs.keys()) {
                writeSocketBindingGroup(writer, sbgs.get(sbg), false);
            }
            writer.writeEndElement();
            WriteUtils.writeNewLine(writer);
        }
        if (modelNode.hasDefined(DEPLOYMENT)) {
            writeDomainDeployments(writer, modelNode.get(DEPLOYMENT));
            WriteUtils.writeNewLine(writer);
        }
        if (modelNode.hasDefined(DEPLOYMENT_OVERLAY)) {
            writeDeploymentOverlays(writer, modelNode.get(DEPLOYMENT_OVERLAY));
            WriteUtils.writeNewLine(writer);
        }
        if (modelNode.hasDefined(SERVER_GROUP)) {
            writer.writeStartElement(Element.SERVER_GROUPS.getLocalName());
            ModelNode sgs = modelNode.get(SERVER_GROUP);
            for (final String sg : sgs.keys()) {
                writeServerGroup(writer, sg, sgs.get(sg));
            }
            writer.writeEndElement();
            WriteUtils.writeNewLine(writer);
        }
        if (modelNode.hasDefined(HOST_EXCLUDE)) {
            writeHostExcludes(writer, modelNode.get(HOST_EXCLUDE));
            WriteUtils.writeNewLine(writer);
        }
        if (modelNode.hasDefined(MANAGEMENT_CLIENT_CONTENT)) {
            writeManagementClientContent(writer, modelNode.get(MANAGEMENT_CLIENT_CONTENT));
            WriteUtils.writeNewLine(writer);
        }

        writer.writeEndElement();
        WriteUtils.writeNewLine(writer);
        writer.writeEndDocument();
    }

    void readDomainElement(final XMLExtendedStreamReader reader, final ModelNode address, final List<ModelNode> list) throws XMLStreamException {

        parseNamespaces(reader, address, list);

        // attributes
        readDomainElementAttributes(reader, address, list);

        // Content
        // Handle elements: sequence
        Element element = nextElement(reader, namespace);
        if (element == Element.EXTENSIONS) {
            extensionXml.parseExtensions(reader, address, namespace, list);
            element = nextElement(reader, namespace);
        }
        if (element == Element.SYSTEM_PROPERTIES) {
            parseSystemProperties(reader, address, namespace, list, false);
            element = nextElement(reader, namespace);
        }
        if (element == Element.PATHS) {
            parsePaths(reader, address, namespace, list, false);
            element = nextElement(reader, namespace);
        }
        if (element == Element.MANAGEMENT) {
            ManagementXml managementXml = ManagementXml.newInstance(namespace, this);
            managementXml.parseManagement(reader, address, list, false);
            element = nextElement(reader, namespace);
        }
        if (element == Element.PROFILES) {
            parseProfiles(reader, address, list);
            element = nextElement(reader, namespace);
        }
        final Set<String> interfaceNames = new HashSet<String>();
        if (element == Element.INTERFACES) {
            parseInterfaces(reader, interfaceNames, address, namespace, list, false);
            element = nextElement(reader, namespace);
        }
        if (element == Element.SOCKET_BINDING_GROUPS) {
            parseDomainSocketBindingGroups(reader, address, list, interfaceNames);
            element = nextElement(reader, namespace);
        }
        if (element == Element.DEPLOYMENTS) {
            parseDeployments(reader, address, namespace, list, EnumSet.of(Attribute.NAME, Attribute.RUNTIME_NAME),
                    EnumSet.of(Element.CONTENT, Element.FS_ARCHIVE, Element.FS_EXPLODED), false);
            element = nextElement(reader, namespace);
        }
        if (element == Element.DEPLOYMENT_OVERLAYS) {
            parseDeploymentOverlays(reader, namespace, new ModelNode(), list, true, false);
            element = nextElement(reader, namespace);
        }
        if (element == Element.SERVER_GROUPS) {
            parseServerGroups(reader, address, list);
            element = nextElement(reader, namespace);
        }
        if (element == Element.HOST_EXCLUDES) {
            parseHostExcludes(reader, list);
            element = nextElement(reader, namespace);
        }
        if (element == Element.MANAGEMENT_CLIENT_CONTENT) {
            parseManagementClientContent(reader, address, namespace, list);
            element = nextElement(reader, namespace);
        } else if (element == null) {
            // Always add op(s) to set up management-client-content resources
            initializeRolloutPlans(address, list);
        } else {
            throw unexpectedElement(reader);
        }
    }

    protected void readDomainElementAttributes(XMLExtendedStreamReader reader, ModelNode address, List<ModelNode> list) throws XMLStreamException {
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            Namespace ns = Namespace.forUri(reader.getAttributeNamespace(i));
            switch (ns) {
                case XML_SCHEMA_INSTANCE: {
                    switch (Attribute.forName(reader.getAttributeLocalName(i))) {
                        case SCHEMA_LOCATION: {
                            parseSchemaLocations(reader, address, list, i);
                            break;
                        }
                        case NO_NAMESPACE_SCHEMA_LOCATION: {
                            // todo, jeez
                            break;
                        }
                        default: {
                            throw unexpectedAttribute(reader, i);
                        }
                    }
                    break;
                }
                default:
                    switch (Attribute.forName(reader.getAttributeLocalName(i))) {
                        case NAME: {
                            ModelNode op = new ModelNode();
                            op.get(OP).set(WRITE_ATTRIBUTE_OPERATION);
                            op.get(NAME).set(NAME);
                            op.get(VALUE).set(ParseUtils.parsePossibleExpression(reader.getAttributeValue(i)));
                            list.add(op);
                            break;
                        }
                        case  ORGANIZATION: {
                            ModelNode op = new ModelNode();
                            op.get(OP).set(WRITE_ATTRIBUTE_OPERATION);
                            op.get(NAME).set(DOMAIN_ORGANIZATION);
                            op.get(VALUE).set(ParseUtils.parsePossibleExpression(reader.getAttributeValue(i)));
                            list.add(op);
                            break;
                        }
                        default:
                            throw unexpectedAttribute(reader, i);
                    }
            }
        }
    }

    void parseDomainSocketBindingGroups(final XMLExtendedStreamReader reader, final ModelNode address,
                                        final List<ModelNode> list, final Set<String> interfaces) throws XMLStreamException {
        HashSet<String> uniqueGroupNames = new HashSet<>();
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, namespace);
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case SOCKET_BINDING_GROUP: {
                    parseSocketBindingGroup(reader, interfaces, address, list, uniqueGroupNames);
                    break;
                }
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }
    }


    void parseSocketBindingGroup(final XMLExtendedStreamReader reader, final Set<String> interfaces,
                                 final ModelNode address, final List<ModelNode> updates, HashSet<String> uniqueGroupNames) throws XMLStreamException {
        // both outbound-socket-bindings and socket-binding names
        final Set<String> uniqueBindingNames = new HashSet<String>();

        String socketBindingGroupName = null;
        String defaultInterface = null;
        final int count = reader.getAttributeCount();
        final ModelNode add = Util.createAddOperation();
        for (int i = 0 ; i < count ; i++) {
            if (!isNoNamespaceAttribute(reader, i)) {
                throw ParseUtils.unexpectedAttribute(reader, i);
            }
            final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
            switch (attribute) {
                case NAME: {
                    socketBindingGroupName = reader.getAttributeValue(i);
                    if (!uniqueGroupNames.add(socketBindingGroupName)) {
                        throw ControllerLogger.ROOT_LOGGER.duplicateDeclaration(SOCKET_BINDING_GROUP, socketBindingGroupName, reader.getLocation());
                    }
                    add.get(OP_ADDR).set(address.clone().add(SOCKET_BINDING_GROUP, socketBindingGroupName));
                    break;
                }
                case DEFAULT_INTERFACE: {
                    defaultInterface = reader.getAttributeValue(i);
                    SocketBindingGroupResourceDefinition.DEFAULT_INTERFACE.parseAndSetParameter(defaultInterface, add, reader);
                    break;
                }
                case INCLUDES: {
                    for (String val : reader.getListAttributeValue(i)) {
                        SocketBindingGroupResourceDefinition.INCLUDES.parseAndAddParameterElement(val, add, reader);
                    }
                    HashSet<String> includes = new HashSet<>();
                    for (ModelNode include : add.get(INCLUDES).asList()) {
                        if (!includes.add(include.asString())) {
                            throw DomainControllerLogger.ROOT_LOGGER.duplicateSocketBindingGroupInclude(include.asString());
                        }
                    }
                    break;
                }
                default:
                    throw unexpectedAttribute(reader, i);
            }
        }
        if (socketBindingGroupName == null || defaultInterface == null) {
            HashSet<String> missing = new HashSet<>();
            if (socketBindingGroupName == null) {
                missing.add(socketBindingGroupName);
            }
            if (defaultInterface == null) {
                missing.add(defaultInterface);
            }
            throw missingRequired(reader, missing);
        }

        if (add.get(AbstractSocketBindingGroupResourceDefinition.DEFAULT_INTERFACE.getName()).getType() != ModelType.EXPRESSION
                && !interfaces.contains(defaultInterface)) {
            throw ControllerLogger.ROOT_LOGGER.unknownInterface(defaultInterface,
                    Attribute.DEFAULT_INTERFACE.getLocalName(), Element.INTERFACES.getLocalName(), reader.getLocation());
        }

        updates.add(add);
        final ModelNode groupAddress = add.get(OP_ADDR);

        // Handle elements
        while (reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, namespace);
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case SOCKET_BINDING: {
                    final String bindingName = parseSocketBinding(reader, interfaces, groupAddress, updates);
                    if (!uniqueBindingNames.add(bindingName)) {
                        throw ControllerLogger.ROOT_LOGGER.alreadyDeclared(Element.SOCKET_BINDING.getLocalName(), Element.OUTBOUND_SOCKET_BINDING.getLocalName(), bindingName, Element.SOCKET_BINDING_GROUP.getLocalName(), socketBindingGroupName, reader.getLocation());
                    }
                    break;
                }
                case OUTBOUND_SOCKET_BINDING: {
                    final String bindingName = parseOutboundSocketBinding(reader, interfaces, groupAddress, updates);
                    if (!uniqueBindingNames.add(bindingName)) {
                        throw ControllerLogger.ROOT_LOGGER.alreadyDeclared(Element.SOCKET_BINDING.getLocalName(), Element.OUTBOUND_SOCKET_BINDING.getLocalName(), bindingName, Element.SOCKET_BINDING_GROUP.getLocalName(), socketBindingGroupName, reader.getLocation());
                    }
                    break;
                }
                default:
                    throw unexpectedElement(reader);
            }
        }
    }

    void parseServerGroups(final XMLExtendedStreamReader reader, final ModelNode address, final List<ModelNode> list) throws XMLStreamException {
        requireNoAttributes(reader);

        final Set<String> names = new HashSet<String>();

        while (reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, namespace);
            Element serverGroup = Element.forName(reader.getLocalName());
            if (Element.SERVER_GROUP != serverGroup) {
                throw unexpectedElement(reader);
            }

            final ModelNode groupAddOp = new ModelNode();
            groupAddOp.get(OP).set(ADD);
            groupAddOp.get(OP_ADDR);

            String name = null;

            // Handle attributes
            Set<Attribute> required = EnumSet.of(Attribute.NAME, Attribute.PROFILE);
            final int count = reader.getAttributeCount();
            for (int i = 0; i < count; i++) {

                final String value = reader.getAttributeValue(i);
                if (!isNoNamespaceAttribute(reader, i)) {
                    throw ParseUtils.unexpectedAttribute(reader, i);
                } else {
                    final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                    required.remove(attribute);
                    switch (attribute) {
                        case NAME: {
                            if (!names.add(value)) {
                                throw ParseUtils.duplicateNamedElement(reader, value);
                            }
                            name = value;
                            break;
                        }
                        case PROFILE: {
                            ServerGroupResourceDefinition.PROFILE.parseAndSetParameter(value, groupAddOp, reader);
                            break;
                        }
                        case MANAGEMENT_SUBSYSTEM_ENDPOINT: {
                            ServerGroupResourceDefinition.MANAGEMENT_SUBSYSTEM_ENDPOINT.parseAndSetParameter(value, groupAddOp, reader);
                            break;
                        }
                        default:
                            throw ParseUtils.unexpectedAttribute(reader, i);
                    }
                }
            }
            if (!required.isEmpty()) {
                throw missingRequired(reader, required);
            }

            final ModelNode groupAddress = new ModelNode().set(address);
            groupAddress.add(ModelDescriptionConstants.SERVER_GROUP, name);
            groupAddOp.get(OP_ADDR).set(groupAddress);

            list.add(groupAddOp);

            // Handle elements
            boolean sawDeployments = false;

            while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
                requireNamespace(reader, namespace);
                final Element element = Element.forName(reader.getLocalName());
                switch (element) {
                    case JVM: {
                        JvmXml.parseJvm(reader, groupAddress, namespace, list, new HashSet<String>(), false);
                        break;
                    }
                    case SOCKET_BINDING_GROUP: {
                        parseSocketBindingGroupRef(reader, groupAddOp, ServerGroupResourceDefinition.SOCKET_BINDING_GROUP,
                                ServerGroupResourceDefinition.SOCKET_BINDING_PORT_OFFSET,
                                ServerGroupResourceDefinition.SOCKET_BINDING_DEFAULT_INTERFACE);
                        break;
                    }
                    case DEPLOYMENTS: {
                        if (sawDeployments) {
                            throw ControllerLogger.ROOT_LOGGER.alreadyDefined(element.getLocalName(), reader.getLocation());
                        }
                        sawDeployments = true;
                        List<ModelNode> deployments = new ArrayList<ModelNode>();
                        parseDeployments(reader, groupAddress, namespace, deployments,
                                EnumSet.of(Attribute.NAME, Attribute.RUNTIME_NAME, Attribute.ENABLED),
                                Collections.<Element>emptySet(), true);
                        list.addAll(deployments);
                        break;
                    }
                    case DEPLOYMENT_OVERLAYS: {
                        parseDeploymentOverlays(reader, namespace, groupAddress, list, false, true);
                        break;
                    }
                    case SYSTEM_PROPERTIES: {
                        parseSystemProperties(reader, groupAddress, namespace, list, false);
                        break;
                    }
                    default:
                        throw ParseUtils.unexpectedElement(reader);
                }
            }

        }
    }

    void parseProfiles(final XMLExtendedStreamReader reader, final ModelNode address, final List<ModelNode> list) throws XMLStreamException {
        requireNoAttributes(reader);

        final Set<String> names = new HashSet<String>();

        while (reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, namespace);
            Element element = Element.forName(reader.getLocalName());
            if (Element.PROFILE != element) {
                throw unexpectedElement(reader);
            }

            //Attributes
            String name = null;
            final int count = reader.getAttributeCount();
            final ModelNode profile = Util.createAddOperation();
            for (int i = 0 ; i < count ; i++) {
                if (!isNoNamespaceAttribute(reader, i)) {
                    throw ParseUtils.unexpectedAttribute(reader, i);
                }
                final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                switch (attribute) {
                    case NAME: {
                        name = reader.getAttributeValue(i);
                        if (!names.add(name)) {
                            throw ControllerLogger.ROOT_LOGGER.duplicateDeclaration("profile", name, reader.getLocation());
                        }
                        profile.get(OP_ADDR).set(address.clone().add(PROFILE, name));
                        break;
                    }
                    case INCLUDES: {
                        for (String val : reader.getListAttributeValue(i)) {
                            ProfileResourceDefinition.INCLUDES.parseAndAddParameterElement(val, profile, reader);
                        }
                        HashSet<String> includes = new HashSet<>();
                        for (ModelNode include : profile.get(INCLUDES).asList()) {
                            if (!includes.add(include.asString())) {
                                throw DomainControllerLogger.ROOT_LOGGER.duplicateProfileInclude(include.asString());
                            }
                        }
                        break;
                    }
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }

            if (name == null) {
                throw ParseUtils.missingRequired(reader, Collections.singleton(NAME));
            }
            list.add(profile);


            final Map<String, List<ModelNode>> profileOps = new LinkedHashMap<String, List<ModelNode>>();
            while (reader.nextTag() != END_ELEMENT) {
                Namespace ns = Namespace.forUri(reader.getNamespaceURI());
                switch (ns) {
                    case UNKNOWN: {
                        if (Element.forName(reader.getLocalName()) != Element.SUBSYSTEM) {
                            throw unexpectedElement(reader);
                        }
                        String namespace = reader.getNamespaceURI();
                        if (profileOps.containsKey(namespace)) {
                            throw ControllerLogger.ROOT_LOGGER.duplicateDeclaration("subsystem", name, reader.getLocation());
                        }
                        // parse content
                        final List<ModelNode> subsystems = new ArrayList<ModelNode>();
                        reader.handleAny(subsystems);

                        profileOps.put(namespace, subsystems);

                        break;
                    }
                    default: {
                        throw unexpectedElement(reader);
                    }
                }
            }

            // Let extensions modify the profile
            Set<ProfileParsingCompletionHandler> completionHandlers = extensionRegistry.getProfileParsingCompletionHandlers();
            for (ProfileParsingCompletionHandler completionHandler : completionHandlers) {
                completionHandler.handleProfileParsingCompletion(profileOps, list);
            }

            // Process subsystems
            for (List<ModelNode> subsystems : profileOps.values()) {
                for (final ModelNode update : subsystems) {
                    // Process relative subsystem path address
                    final ModelNode subsystemAddress = address.clone().set(address).add(ModelDescriptionConstants.PROFILE, name);
                    for (final Property path : update.get(OP_ADDR).asPropertyList()) {
                        subsystemAddress.add(path.getName(), path.getValue().asString());
                    }
                    update.get(OP_ADDR).set(subsystemAddress);
                    list.add(update);
                }
            }
        }
    }

    private void parseManagementClientContent(XMLExtendedStreamReader reader, ModelNode address, Namespace expectedNs, List<ModelNode> list) throws XMLStreamException {
        requireNoAttributes(reader);

        boolean rolloutPlansAdded = false;
        while (reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, expectedNs);
            Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case ROLLOUT_PLANS: {
                    parseRolloutPlans(reader, address, list);
                    rolloutPlansAdded = true;
                    break;
                }
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }

        if (!rolloutPlansAdded) {
            initializeRolloutPlans(address, list);
        }
    }

    private void parseRolloutPlans(XMLExtendedStreamReader reader, ModelNode address, List<ModelNode> list) throws XMLStreamException {

        String hash = readStringAttributeElement(reader, Attribute.SHA1.getLocalName());

        ModelNode addAddress = address.clone().add(MANAGEMENT_CLIENT_CONTENT, ROLLOUT_PLANS);
        ModelNode addOp = Util.getEmptyOperation(ADD, addAddress);
        try {
            addOp.get(HASH).set(HashUtil.hexStringToByteArray(hash));
        } catch (final Exception e) {
            throw ControllerLogger.ROOT_LOGGER.invalidSha1Value(e, hash, Attribute.SHA1.getLocalName(), reader.getLocation());
        }

        list.add(addOp);
    }

    private void initializeRolloutPlans(ModelNode address, List<ModelNode> list) {

        ModelNode addAddress = address.clone().add(MANAGEMENT_CLIENT_CONTENT, ROLLOUT_PLANS);
        ModelNode addOp = Util.getEmptyOperation(ADD, addAddress);
        list.add(addOp);
    }

    private void writeProfile(final XMLExtendedStreamWriter writer, final String profileName, final ModelNode profileNode, final ModelMarshallingContext context) throws XMLStreamException {

        writer.writeStartElement(Element.PROFILE.getLocalName());
        writer.writeAttribute(Attribute.NAME.getLocalName(), profileName);
        ProfileResourceDefinition.INCLUDES.getAttributeMarshaller().marshallAsAttribute(ProfileResourceDefinition.INCLUDES, profileNode, false, writer);

        if (profileNode.hasDefined(SUBSYSTEM)) {
            final Set<String> subsystemNames = profileNode.get(SUBSYSTEM).keys();
            if (subsystemNames.size() > 0) {
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
        writer.writeEndElement();
    }

    private void writeDomainDeployments(final XMLExtendedStreamWriter writer, final ModelNode modelNode) throws XMLStreamException {

        final Set<String> deploymentNames = modelNode.keys();
        if (deploymentNames.size() > 0) {
            writer.writeStartElement(Element.DEPLOYMENTS.getLocalName());
            for (String uniqueName : deploymentNames) {
                final ModelNode deployment = modelNode.get(uniqueName);
                writer.writeStartElement(Element.DEPLOYMENT.getLocalName());
                WriteUtils.writeAttribute(writer, Attribute.NAME, uniqueName);
                DeploymentAttributes.RUNTIME_NAME.marshallAsAttribute(deployment, writer);
                final List<ModelNode> contentItems = deployment.require(CONTENT).asList();
                for (ModelNode contentItem : contentItems) {
                    writeContentItem(writer, contentItem);
                }
                writer.writeEndElement();
            }
            writer.writeEndElement();
        }
    }

    private void writeServerGroupDeployments(final XMLExtendedStreamWriter writer, final ModelNode modelNode) throws XMLStreamException {

        final Set<String> deploymentNames = modelNode.keys();
        if (deploymentNames.size() > 0) {
            writer.writeStartElement(Element.DEPLOYMENTS.getLocalName());
            for (String uniqueName : deploymentNames) {
                final ModelNode deployment = modelNode.get(uniqueName);
                writer.writeStartElement(Element.DEPLOYMENT.getLocalName());
                WriteUtils.writeAttribute(writer, Attribute.NAME, uniqueName);
                DeploymentAttributes.RUNTIME_NAME.marshallAsAttribute(deployment, writer);
                DeploymentAttributes.ENABLED.marshallAsAttribute(deployment, writer);
                writer.writeEndElement();
            }
            writer.writeEndElement();
        }
    }

    private void writeServerGroup(final XMLExtendedStreamWriter writer, final String groupName, final ModelNode group) throws XMLStreamException {
        writer.writeStartElement(Element.SERVER_GROUP.getLocalName());
        writer.writeAttribute(Attribute.NAME.getLocalName(), groupName);

        ServerGroupResourceDefinition.PROFILE.marshallAsAttribute(group, writer);
        ServerGroupResourceDefinition.MANAGEMENT_SUBSYSTEM_ENDPOINT.marshallAsAttribute(group, writer);

        // JVM
        if (group.hasDefined(JVM)) {
            for (final Property jvm : group.get(JVM).asPropertyList()) {
                JvmXml.writeJVMElement(writer, jvm.getName(), jvm.getValue());
                break; // TODO just write the first !?
            }
        }

        // Socket binding ref
        if (group.hasDefined(SOCKET_BINDING_GROUP) || group.hasDefined(SOCKET_BINDING_PORT_OFFSET)) {
            writer.writeStartElement(Element.SOCKET_BINDING_GROUP.getLocalName());
            ServerGroupResourceDefinition.SOCKET_BINDING_GROUP.marshallAsAttribute(group, writer);
            ServerGroupResourceDefinition.SOCKET_BINDING_PORT_OFFSET.marshallAsAttribute(group, writer);
            ServerGroupResourceDefinition.SOCKET_BINDING_DEFAULT_INTERFACE.marshallAsAttribute(group, writer);
            writer.writeEndElement();
        }

        if (group.hasDefined(DEPLOYMENT)) {
            writeServerGroupDeployments(writer, group.get(DEPLOYMENT));
        }
        if (group.hasDefined(DEPLOYMENT_OVERLAY)) {
            writeDeploymentOverlays(writer, group.get(DEPLOYMENT_OVERLAY));
            WriteUtils.writeNewLine(writer);
        }
        // System properties
        if (group.hasDefined(SYSTEM_PROPERTY)) {
            writeProperties(writer, group.get(SYSTEM_PROPERTY), Element.SYSTEM_PROPERTIES, false);
        }

        writer.writeEndElement();
    }

    private void writeManagementClientContent(XMLExtendedStreamWriter writer, ModelNode modelNode) throws XMLStreamException {
        boolean hasRolloutPlans = modelNode.hasDefined(ROLLOUT_PLANS) && modelNode.get(ROLLOUT_PLANS).hasDefined(HASH);
        boolean mustWrite = hasRolloutPlans; // || other elements we may add later
        if (mustWrite) {
            writer.writeStartElement(Element.MANAGEMENT_CLIENT_CONTENT.getLocalName());
            if (hasRolloutPlans) {
                writer.writeEmptyElement(Element.ROLLOUT_PLANS.getLocalName());
                writer.writeAttribute(Attribute.SHA1.getLocalName(), HashUtil.bytesToHexString(modelNode.get(ROLLOUT_PLANS).get(HASH).asBytes()));
            }
            writer.writeEndElement();
        }
    }

    /*
     * ManagamentXmlDelegate Methods
     */

    @Override
    public boolean parseSecurityRealms(XMLExtendedStreamReader reader, ModelNode address, List<ModelNode> operationsList)
            throws XMLStreamException {
        throw unexpectedElement(reader);
    }

    @Override
    public boolean parseOutboundConnections(XMLExtendedStreamReader reader, ModelNode address, List<ModelNode> operationsList)
            throws XMLStreamException {
        throw unexpectedElement(reader);
    }

    @Override
    public boolean parseAccessControl(XMLExtendedStreamReader reader, ModelNode address, List<ModelNode> operationsList)
            throws XMLStreamException {

        ModelNode accAuthzAddr = address.clone().add(ACCESS, AUTHORIZATION);

        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {

            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw ParseUtils.unexpectedAttribute(reader, i);
            }

            final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
            if (attribute == Attribute.PROVIDER) {
                ModelNode provider = AccessAuthorizationResourceDefinition.PROVIDER.parse(value, reader);
                ModelNode op = Util.getWriteAttributeOperation(accAuthzAddr,
                        AccessAuthorizationResourceDefinition.PROVIDER.getName(), provider);

                operationsList.add(op);
            } else if (attribute == Attribute.PERMISSION_COMBINATION_POLICY) {
                ModelNode provider = AccessAuthorizationResourceDefinition.PERMISSION_COMBINATION_POLICY.parse(value, reader);
                ModelNode op = Util.getWriteAttributeOperation(accAuthzAddr,
                        AccessAuthorizationResourceDefinition.PERMISSION_COMBINATION_POLICY.getName(), provider);

                operationsList.add(op);
            } else {
                throw unexpectedAttribute(reader, i);
            }
        }

        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, namespace);
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case ROLE_MAPPING:
                    accessControlXml.parseAccessControlRoleMapping(reader, accAuthzAddr, operationsList);
                    break;
                case SERVER_GROUP_SCOPED_ROLES:
                    accessControlXml.parseServerGroupScopedRoles(reader, accAuthzAddr, operationsList);
                    break;
                case HOST_SCOPED_ROLES:
                    accessControlXml.parseHostScopedRoles(reader, accAuthzAddr, operationsList);
                    break;
                case PATTERN_SCOPED_ROLES:
                    accessControlXml.parsePatternScopedRoles(reader, accAuthzAddr, operationsList);
                    break;
                case CONSTRAINTS: {
                    accessControlXml.parseAccessControlConstraints(reader, accAuthzAddr, operationsList);
                    break;
                }
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }

        return true;
    }

    @Override
    public boolean writeAccessControl(XMLExtendedStreamWriter writer, ModelNode accessAuthorization) throws XMLStreamException {
        accessControlXml.writeAccessControl(writer, accessAuthorization);

        return true;
    }

    private void parseHostExcludes(XMLExtendedStreamReader reader, List<ModelNode> list) throws XMLStreamException {
        requireNoAttributes(reader);
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, namespace);
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case HOST_EXCLUDE:
                    parseHostExclude(reader, list);
                    break;
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }
    }

    private void parseHostExclude(XMLExtendedStreamReader reader, List<ModelNode> list) throws XMLStreamException {

        ModelNode addOp = Util.createAddOperation(PathAddress.EMPTY_ADDRESS);

        String name = null;
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {

            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw ParseUtils.unexpectedAttribute(reader, i);
            }

            final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
            if (attribute == Attribute.NAME) {
                name = value;
                addOp.get(OP_ADDR).set(PathAddress.pathAddress(HOST_EXCLUDE, name).toModelNode());
            } else if (attribute == Attribute.ACTIVE_SERVER_GROUPS) {
                HostExcludeResourceDefinition.ACTIVE_SERVER_GROUPS.getParser()
                        .parseAndSetParameter(HostExcludeResourceDefinition.ACTIVE_SERVER_GROUPS, value, addOp, reader);
            } else if (attribute == Attribute.ACTIVE_SOCKET_BINDING_GROUPS) {
                HostExcludeResourceDefinition.ACTIVE_SOCKET_BINDING_GROUPS.getParser()
                        .parseAndSetParameter(HostExcludeResourceDefinition.ACTIVE_SOCKET_BINDING_GROUPS, value, addOp, reader);
            } else {
                throw unexpectedAttribute(reader, i);
            }
        }

        if (name == null) {
            throw missingRequired(reader, NAME);
        }

        boolean sawMapping = false;

        ModelNode extensions = new ModelNode();

        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, namespace);
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case HOST_RELEASE:
                    if (sawMapping) {
                        throw unexpectedElement(reader);
                    }
                    sawMapping = true;
                    parseHostRelease(reader, addOp);
                    break;
                case HOST_API_VERSION:
                    if (sawMapping) {
                        throw unexpectedElement(reader);
                    }
                    sawMapping = true;
                    parseHostApiVersion(reader, addOp);
                    break;
                case EXCLUDED_EXTENSIONS:
                    requireNoAttributes(reader);
                    while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
                        requireNamespace(reader, namespace);
                        final Element element1 = Element.forName(reader.getLocalName());
                        switch (element1) {
                            case EXTENSION:
                                final int attrcount = reader.getAttributeCount();
                                for (int i = 0; i < attrcount; i++) {
                                    final String value = reader.getAttributeValue(i);
                                    if (!isNoNamespaceAttribute(reader, i)) {
                                        throw ParseUtils.unexpectedAttribute(reader, i);
                                    }

                                    final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                                    if (attribute == Attribute.MODULE) {
                                        extensions.add(value);
                                    } else {
                                        throw unexpectedAttribute(reader, i);
                                    }
                                }
                                requireNoContent(reader);
                                break;
                            default: {
                                throw unexpectedElement(reader);
                            }
                        }
                    }
                    break;
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }
        if (extensions.isDefined()) {
            addOp.get(HostExcludeResourceDefinition.EXCLUDED_EXTENSIONS.getName()).set(extensions);
        }

        if (!sawMapping) {
            throw missingRequiredElement(reader, EnumSet.of(Element.HOST_RELEASE, Element.HOST_API_VERSION));
        }
        list.add(addOp);
    }

    private void parseHostRelease(XMLExtendedStreamReader reader, ModelNode addOp) throws XMLStreamException {

        boolean sawId = false;
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {

            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw ParseUtils.unexpectedAttribute(reader, i);
            }

            final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
            if (attribute == Attribute.ID) {
                sawId = true;
                HostExcludeResourceDefinition.HOST_RELEASE.parseAndSetParameter(value, addOp, reader);
            } else {
                throw unexpectedAttribute(reader, i);
            }
        }

        if (!sawId) {
            throw missingRequired(reader, Attribute.ID.getLocalName());
        }

        requireNoContent(reader);
    }

    private void parseHostApiVersion(XMLExtendedStreamReader reader, ModelNode addOp) throws XMLStreamException {

        Set<Attribute> required = EnumSet.of(Attribute.MAJOR_VERSION, Attribute.MINOR_VERSION);
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {

            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw ParseUtils.unexpectedAttribute(reader, i);
            }

            final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
            if (attribute == Attribute.MAJOR_VERSION) {
                required.remove(attribute);
                HostExcludeResourceDefinition.MANAGEMENT_MAJOR_VERSION.parseAndSetParameter(value, addOp, reader);
            } else if (attribute == Attribute.MINOR_VERSION) {
                required.remove(attribute);
                HostExcludeResourceDefinition.MANAGEMENT_MINOR_VERSION.parseAndSetParameter(value, addOp, reader);
            } else if (attribute == Attribute.MICRO_VERSION) {
                HostExcludeResourceDefinition.MANAGEMENT_MICRO_VERSION.parseAndSetParameter(value, addOp, reader);
            } else {
                throw unexpectedAttribute(reader, i);
            }
        }

        if (!required.isEmpty()) {
            throw missingRequired(reader, required);
        }

        requireNoContent(reader);
    }

    private void writeHostExcludes(XMLExtendedStreamWriter writer, ModelNode modelNode) throws XMLStreamException {
        writer.writeStartElement(Element.HOST_EXCLUDES.getLocalName());
        for (String exclude : modelNode.keys()) {
            writer.writeStartElement(Element.HOST_EXCLUDE.getLocalName());
            writer.writeAttribute(Attribute.NAME.getLocalName(), exclude);
            ModelNode excludeNode = modelNode.get(exclude);
            HostExcludeResourceDefinition.ACTIVE_SERVER_GROUPS.getAttributeMarshaller()
                    .marshall(HostExcludeResourceDefinition.ACTIVE_SERVER_GROUPS, excludeNode, false, writer);
            HostExcludeResourceDefinition.ACTIVE_SOCKET_BINDING_GROUPS.getAttributeMarshaller()
                    .marshall(HostExcludeResourceDefinition.ACTIVE_SOCKET_BINDING_GROUPS, excludeNode, false, writer);
            if (HostExcludeResourceDefinition.HOST_RELEASE.isMarshallable(excludeNode)) {
                writer.writeEmptyElement(Element.HOST_RELEASE.getLocalName());
                HostExcludeResourceDefinition.HOST_RELEASE.marshallAsAttribute(excludeNode, writer);
            } else {
                writer.writeStartElement(Element.HOST_API_VERSION.getLocalName());
                HostExcludeResourceDefinition.MANAGEMENT_MAJOR_VERSION.marshallAsAttribute(excludeNode, writer);
                HostExcludeResourceDefinition.MANAGEMENT_MINOR_VERSION.marshallAsAttribute(excludeNode, writer);
                HostExcludeResourceDefinition.MANAGEMENT_MICRO_VERSION.marshallAsAttribute(excludeNode, writer);
                writer.writeEndElement();
            }

            if (HostExcludeResourceDefinition.EXCLUDED_EXTENSIONS.isMarshallable(excludeNode)) {
                writer.writeStartElement(Element.EXCLUDED_EXTENSIONS.getLocalName());
                for (ModelNode ext : excludeNode.get(HostExcludeResourceDefinition.EXCLUDED_EXTENSIONS.getName()).asList()) {
                    if (ext.isDefined()) {
                        writer.writeEmptyElement(Element.EXTENSION.getLocalName());
                        writer.writeAttribute(Attribute.MODULE.getLocalName(), ext.asString());
                    }
                }
                writer.writeEndElement();
            }

            writer.writeEndElement();
        }
        writer.writeEndElement();
    }
}
