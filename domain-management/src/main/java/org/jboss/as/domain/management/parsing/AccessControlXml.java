/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.domain.management.parsing;

import static javax.xml.stream.XMLStreamConstants.END_ELEMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.APPLICATION_CLASSIFICATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CLASSIFICATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CONSTRAINT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.EXCLUDE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST_SCOPED_ROLE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.HOST_SCOPED_ROLES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INCLUDE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REALM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ROLE_MAPPING;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SENSITIVITY_CLASSIFICATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUP_SCOPED_ROLE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.TYPE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.USER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VAULT_EXPRESSION;
import static org.jboss.as.controller.parsing.ParseUtils.isNoNamespaceAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.missingRequired;
import static org.jboss.as.controller.parsing.ParseUtils.requireNamespace;
import static org.jboss.as.controller.parsing.ParseUtils.requireNoAttributes;
import static org.jboss.as.controller.parsing.ParseUtils.requireNoContent;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedElement;
import static org.jboss.as.controller.parsing.WriteUtils.writeAttribute;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ListAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.parsing.Attribute;
import org.jboss.as.controller.parsing.Element;
import org.jboss.as.controller.parsing.Namespace;
import org.jboss.as.controller.parsing.ParseUtils;
import org.jboss.as.domain.management.access.AccessAuthorizationResourceDefinition;
import org.jboss.as.domain.management.access.ApplicationClassificationConfigResourceDefinition;
import org.jboss.as.domain.management.access.ApplicationClassificationTypeResourceDefinition;
import org.jboss.as.domain.management.access.HostScopedRolesResourceDefinition;
import org.jboss.as.domain.management.access.PrincipalResourceDefinition;
import org.jboss.as.domain.management.access.RoleMappingResourceDefinition;
import org.jboss.as.domain.management.access.SensitivityClassificationTypeResourceDefinition;
import org.jboss.as.domain.management.access.SensitivityResourceDefinition;
import org.jboss.as.domain.management.access.ServerGroupScopedRoleResourceDefinition;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;

/**
 * Bits of parsing and marshaling logic that are related to {@code <access-control>} elements.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class AccessControlXml {

    private final Namespace namespace;

    private AccessControlXml(final Namespace namespace) {
        this.namespace = namespace;
    }

    public static AccessControlXml newInstance(Namespace namespace) {
        return new AccessControlXml(namespace);
    }

    public void parseAccessControlConstraints(final XMLExtendedStreamReader reader, final ModelNode accAuthzAddr, final List<ModelNode> list) throws XMLStreamException {
        ParseUtils.requireNoAttributes(reader);

        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, namespace);
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case VAULT_EXPRESSION_SENSITIVITY: {
                    ModelNode vaultAddr = accAuthzAddr.clone().add(CONSTRAINT, VAULT_EXPRESSION);
                    parseClassificationType(reader, vaultAddr, list, true);
                    break;
                }
                case SENSITIVE_CLASSIFICATIONS: {
                    ModelNode sensAddr = accAuthzAddr.clone().add(CONSTRAINT, SENSITIVITY_CLASSIFICATION);
                    parseSensitiveClassifications(reader, sensAddr, list);
                    break;
                }
                case APPLICATION_CLASSIFICATIONS: {
                    ModelNode applAddr = accAuthzAddr.clone().add(CONSTRAINT, APPLICATION_CLASSIFICATION);
                    parseApplicationClassifications(reader, applAddr, list);
                    break;
                }
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }
    }

    public void parseAccessControlRoleMapping(final XMLExtendedStreamReader reader, final ModelNode accContAddr,
            final List<ModelNode> list) throws XMLStreamException {
        requireNoAttributes(reader);

        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, namespace);
            final Element element = Element.forName(reader.getLocalName());
            if (element == Element.ROLE) {
                parseRole(reader, accContAddr, list);
            } else {
                throw unexpectedElement(reader);
            }
        }
    }

    public void parseServerGroupScopedRoles(final XMLExtendedStreamReader reader, final ModelNode address, final List<ModelNode> list) throws XMLStreamException {

        ParseUtils.requireNoAttributes(reader);

        String scopedRoleType = ServerGroupScopedRoleResourceDefinition.PATH_ELEMENT.getKey();

        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, namespace);
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case ROLE: {
                    parseScopedRole(reader, address, list, scopedRoleType, Element.SERVER_GROUP,
                            ServerGroupScopedRoleResourceDefinition.BASE_ROLE, ServerGroupScopedRoleResourceDefinition.SERVER_GROUPS, true);
                    break;
                }
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }
    }

    public void parseHostScopedRoles(final XMLExtendedStreamReader reader, final ModelNode address, final List<ModelNode> list) throws XMLStreamException {

        ParseUtils.requireNoAttributes(reader);

        String scopedRoleType = HostScopedRolesResourceDefinition.PATH_ELEMENT.getKey();

        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, namespace);
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case ROLE: {
                    parseScopedRole(reader, address, list, scopedRoleType, Element.HOST,
                            HostScopedRolesResourceDefinition.BASE_ROLE, HostScopedRolesResourceDefinition.HOSTS, false);
                    break;
                }
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }

    }

    private void parseScopedRole(XMLExtendedStreamReader reader, ModelNode address,
                                 List<ModelNode> ops, String scopedRoleType, final Element listElement,
                                 SimpleAttributeDefinition baseRoleDefinition, ListAttributeDefinition listDefinition,
                                 boolean requireChildren) throws XMLStreamException {

        final ModelNode addOp = Util.createAddOperation();
        ops.add(addOp);
        final ModelNode ourAddress = addOp.get(OP_ADDR).set(address);
        final Set<Attribute> required = EnumSet.of(Attribute.NAME, Attribute.BASE_ROLE);
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            }
            final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
            required.remove(attribute);
            switch (attribute) {
                case NAME:
                    ourAddress.add(scopedRoleType, value);
                    break;
                case BASE_ROLE:
                    baseRoleDefinition.parseAndSetParameter(value, addOp, reader);
                    break;
                default: {
                    throw unexpectedAttribute(reader, i);
                }
            }
        }

        if (!required.isEmpty()) {
            throw missingRequired(reader, required);
        }

        boolean missingChildren = requireChildren;
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            boolean named = false;
            requireNamespace(reader, namespace);
            final Element element = Element.forName(reader.getLocalName());
            if (element == listElement) {
                missingChildren = false;
                final int groupCount = reader.getAttributeCount();
                for (int i = 0; i < groupCount; i++) {
                    final String value = reader.getAttributeValue(i);
                    if (!isNoNamespaceAttribute(reader, i)) {
                        throw unexpectedAttribute(reader, i);
                    }
                    final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                    required.remove(attribute);
                    if (attribute == Attribute.NAME) {
                        named = true;
                        listDefinition.parseAndAddParameterElement(value, addOp, reader);
                    } else {
                        throw unexpectedAttribute(reader, i);
                    }
                }
            } else {
                throw unexpectedElement(reader);
            }

            if (!named) {
                throw missingRequired(reader, EnumSet.of(Attribute.NAME));
            }

            requireNoContent(reader);
        }

        if (missingChildren) {
            throw missingRequired(reader, EnumSet.of(listElement));
        }
    }

    private void parseRole(final XMLExtendedStreamReader reader, final ModelNode address,
            final List<ModelNode> list) throws XMLStreamException {
        final ModelNode add = new ModelNode();
        list.add(add);
        String name = null;

        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                switch (attribute) {
                    case NAME:
                        name = value;
                        break;
                    case INCLUDE_ALL: {
                        RoleMappingResourceDefinition.INCLUDE_ALL.parseAndSetParameter(value, add, reader);
                        break;
                    }
                    default: {
                        throw unexpectedAttribute(reader, i);
                    }
                }
            }
        }

        if (name == null) {
            throw missingRequired(reader, Collections.singleton(Attribute.NAME));
        }

        ModelNode addr = address.clone().add(ROLE_MAPPING, name);
        add.get(OP_ADDR).set(addr);
        add.get(OP).set(ADD);

        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, namespace);
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case INCLUDE: {
                    parseIncludeExclude(reader, addr, INCLUDE, list);
                    break;
                }
                case EXCLUDE: {
                    parseIncludeExclude(reader, addr, EXCLUDE, list);
                    break;
                }
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }
    }

    private void parseIncludeExclude(final XMLExtendedStreamReader reader, final ModelNode parentAddress, final String incExcType,
            final List<ModelNode> list) throws XMLStreamException {
        ParseUtils.requireNoAttributes(reader);

        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, namespace);
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case GROUP: {
                    parsePrincipal(reader, parentAddress, incExcType, GROUP, list);
                    break;
                }
                case USER: {
                    parsePrincipal(reader, parentAddress, incExcType, USER, list);
                    break;
                }
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }
    }

    private void parsePrincipal(final XMLExtendedStreamReader reader, final ModelNode parentAddress, final String incExcType, final String principalType,
            final List<ModelNode> list) throws XMLStreamException {
        String alias = null;
        String realm = null;
        String name = null;

        ModelNode addOp = new ModelNode();
        addOp.get(OP).set(ADD);
        addOp.get(TYPE).set(principalType);

        int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                switch (attribute) {
                    case ALIAS: {
                        alias = value;
                        break;
                    }
                    case NAME: {
                        name = value;
                        PrincipalResourceDefinition.NAME.parseAndSetParameter(value, addOp, reader);
                        break;
                    }
                    case REALM: {
                        realm = value;
                        PrincipalResourceDefinition.REALM.parseAndSetParameter(value, addOp, reader);
                        break;
                    }
                    default: {
                        throw unexpectedAttribute(reader, i);
                    }
                }
            }
        }

        if (name == null) {
            throw ParseUtils.missingRequired(reader, Collections.singleton(Attribute.NAME));
        }

        String addrValue = alias == null ? generateAlias(principalType, name, realm) : alias;
        ModelNode addAddr = parentAddress.clone().add(incExcType, addrValue);
        addOp.get(OP_ADDR).set(addAddr);
        list.add(addOp);

        ParseUtils.requireNoContent(reader);
    }

    static String generateAlias(final String type, final String name, final String realm) {
        return type + "-" + name + (realm != null ? "@" + realm : "");
    }

    private void parseSensitiveClassifications(final XMLExtendedStreamReader reader, final ModelNode address,
            final List<ModelNode> list) throws XMLStreamException {

        ParseUtils.requireNoAttributes(reader);

        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, namespace);
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case SENSITIVE_CLASSIFICATION: {
                    parseSensitivityClassification(reader, address, list);
                    break;
                }
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }
    }

    private void parseClassificationType(final XMLExtendedStreamReader reader, final ModelNode address,
            final List<ModelNode> list, boolean vault) throws XMLStreamException {
        final int count = reader.getAttributeCount();
        String name = null;
        String type = null;
        Map<String, ModelNode> values = new HashMap<String, ModelNode>();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                switch (attribute) {
                    case NAME: {
                        name = value;
                        break;
                    }
                    case TYPE: {
                        type = value;
                        break;
                    }
                    case REQUIRES_READ: {
                        values.put(SensitivityResourceDefinition.CONFIGURED_REQUIRES_READ.getName(),
                                parse(SensitivityResourceDefinition.CONFIGURED_REQUIRES_READ, value, reader));
                        break;
                    }
                    case REQUIRES_WRITE: {
                        values.put(SensitivityResourceDefinition.CONFIGURED_REQUIRES_WRITE.getName(),
                                parse(SensitivityResourceDefinition.CONFIGURED_REQUIRES_WRITE, value, reader));
                        break;
                    }
                    case REQUIRES_ADDRESSABLE: {
                        if (!vault) {
                            values.put(SensitivityResourceDefinition.CONFIGURED_REQUIRES_ADDRESSABLE.getName(),
                                parse(SensitivityResourceDefinition.CONFIGURED_REQUIRES_ADDRESSABLE, value, reader));
                            break;
                        }
                    }
                    default: {
                        throw unexpectedAttribute(reader, i);
                    }
                }
            }
        }

        if (name == null && !vault) {
            throw ParseUtils.missingRequired(reader, Collections.singleton(Attribute.NAME));
        }

        if (type == null && !vault) {
            throw ParseUtils.missingRequired(reader, Collections.singleton(Attribute.TYPE));
        }

        final ModelNode newAddress = vault ? address :
            address.clone()
            .add(SensitivityClassificationTypeResourceDefinition.PATH_ELEMENT.getKey(), type)
            .add(SensitivityResourceDefinition.PATH_ELEMENT.getKey(), name);

        for (Map.Entry<String, ModelNode> entry : values.entrySet()) {
            list.add(Util.getWriteAttributeOperation(newAddress, entry.getKey(), entry.getValue()));
        }
        ParseUtils.requireNoContent(reader);
    }


    private void parseApplicationClassifications(final XMLExtendedStreamReader reader, final ModelNode address,
            final List<ModelNode> list) throws XMLStreamException {

        ParseUtils.requireNoAttributes(reader);

        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, namespace);
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case APPLICATION_CLASSIFICATION: {
                    parseApplicationClassification(reader, address, list);
                    break;
                }
                default: {
                    throw unexpectedElement(reader);
                }
            }
        }
    }

    private void parseApplicationClassification(final XMLExtendedStreamReader reader, final ModelNode address,
            final List<ModelNode> list) throws XMLStreamException {
        String name = null;
        String type = null;
        Boolean applicationValue = null;
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
                switch (attribute) {
                    case NAME:
                        name = value;
                        break;
                    case TYPE:
                        type = value;
                        break;
                    case APPLICATION:
                        applicationValue = Boolean.valueOf(value);
                        break;
                    default: {
                        throw unexpectedAttribute(reader, i);
                    }
                }
            }
        }

        if (name == null) {
            throw ParseUtils.missingRequired(reader, Collections.singleton(NAME));
        }
        if (type == null) {
            throw ParseUtils.missingRequired(reader, Collections.singleton(Attribute.TYPE));
        }
        if (applicationValue == null) {
            throw ParseUtils.missingRequired(reader, Collections.singleton(Attribute.APPLICATION));
        }

        ModelNode newAddress = address.clone()
                .add(ApplicationClassificationTypeResourceDefinition.PATH_ELEMENT.getKey(), type)
                .add(ApplicationClassificationConfigResourceDefinition.PATH_ELEMENT.getKey(), name);


        list.add(Util.getWriteAttributeOperation(newAddress, ApplicationClassificationConfigResourceDefinition.CONFIGURED_APPLICATION.getName(), applicationValue.toString()));
        ParseUtils.requireNoContent(reader);
    }

    private void parseSensitivityClassification(final XMLExtendedStreamReader reader, final ModelNode address,
            final List<ModelNode> list) throws XMLStreamException {
        parseClassificationType(reader, address, list, false);
    }

    public void writeAccessControl(final XMLExtendedStreamWriter writer, final ModelNode accessAuthorization) throws XMLStreamException {
        if (accessAuthorization == null || accessAuthorization.isDefined()==false) {
            return; // All subsequent checks are based on this being defined.
        }

        boolean hasServerGroupRoles =  accessAuthorization.hasDefined(SERVER_GROUP_SCOPED_ROLE);
        boolean hasHostRoles = accessAuthorization.hasDefined(HOST_SCOPED_ROLE) || accessAuthorization.hasDefined(HOST_SCOPED_ROLES);
        boolean hasRoleMapping = accessAuthorization.hasDefined(ROLE_MAPPING);
        Map<String, Map<String, Set<String>>> configuredAccessConstraints = getConfiguredAccessConstraints(accessAuthorization);
        boolean hasProvider = accessAuthorization.hasDefined(AccessAuthorizationResourceDefinition.PROVIDER.getName());
        boolean hasCombinationPolicy = accessAuthorization.hasDefined(AccessAuthorizationResourceDefinition.PERMISSION_COMBINATION_POLICY.getName());

        if (!hasProvider && !hasCombinationPolicy && !hasServerGroupRoles && !hasHostRoles
                && !hasRoleMapping && configuredAccessConstraints.size() == 0) {
            return;
        }

        writer.writeStartElement(Element.ACCESS_CONTROL.getLocalName());

        AccessAuthorizationResourceDefinition.PROVIDER.marshallAsAttribute(accessAuthorization, writer);
        AccessAuthorizationResourceDefinition.USE_IDENTITY_ROLES.marshallAsAttribute(accessAuthorization, writer);
        AccessAuthorizationResourceDefinition.PERMISSION_COMBINATION_POLICY.marshallAsAttribute(accessAuthorization, writer);

        if (hasServerGroupRoles) {
            ModelNode serverGroupRoles = accessAuthorization.get(SERVER_GROUP_SCOPED_ROLE);
            if (serverGroupRoles.asInt() > 0) {
                writeServerGroupScopedRoles(writer, serverGroupRoles);
            }
        }

        if (hasHostRoles) {
            ModelNode serverGroupRoles = accessAuthorization.get(HOST_SCOPED_ROLE);
            if (serverGroupRoles.asInt() > 0) {
                writeHostScopedRoles(writer, serverGroupRoles);
            }
        }

        if (hasRoleMapping) {
            writeRoleMapping(writer, accessAuthorization);
        }

        if (configuredAccessConstraints.size() > 0) {
            writeAccessConstraints(writer, accessAuthorization, configuredAccessConstraints);
        }

        writer.writeEndElement();
    }

    private void writeServerGroupScopedRoles(XMLExtendedStreamWriter writer, ModelNode scopedRoles) throws XMLStreamException {
        writer.writeStartElement(Element.SERVER_GROUP_SCOPED_ROLES.getLocalName());

        for (Property property : scopedRoles.asPropertyList()) {
            writer.writeStartElement(Element.ROLE.getLocalName());
            writer.writeAttribute(Attribute.NAME.getLocalName(), property.getName());
            ModelNode value = property.getValue();
            ServerGroupScopedRoleResourceDefinition.BASE_ROLE.marshallAsAttribute(value, writer);
            ServerGroupScopedRoleResourceDefinition.SERVER_GROUPS.marshallAsElement(value, writer);
            writer.writeEndElement();
        }
        writer.writeEndElement();
    }

    private void writeHostScopedRoles(XMLExtendedStreamWriter writer, ModelNode scopedRoles) throws XMLStreamException {
        writer.writeStartElement(Element.HOST_SCOPED_ROLES.getLocalName());

        for (Property property : scopedRoles.asPropertyList()) {
            writer.writeStartElement(Element.ROLE.getLocalName());
            writer.writeAttribute(Attribute.NAME.getLocalName(), property.getName());
            ModelNode value = property.getValue();
            HostScopedRolesResourceDefinition.BASE_ROLE.marshallAsAttribute(value, writer);
            HostScopedRolesResourceDefinition.HOSTS.marshallAsElement(value, writer);
            writer.writeEndElement();
        }
        writer.writeEndElement();
    }

    private void writeAccessConstraints(XMLExtendedStreamWriter writer, ModelNode accessAuthorization, Map<String, Map<String, Set<String>>> configuredConstraints) throws XMLStreamException {
        writer.writeStartElement(Element.CONSTRAINTS.getLocalName());

        if (configuredConstraints.containsKey(SensitivityResourceDefinition.VAULT_ELEMENT.getKey())){
            writer.writeEmptyElement(Element.VAULT_EXPRESSION_SENSITIVITY.getLocalName());
            ModelNode model = accessAuthorization.get(SensitivityResourceDefinition.VAULT_ELEMENT.getKey(),
                    SensitivityResourceDefinition.VAULT_ELEMENT.getValue());
            SensitivityResourceDefinition.CONFIGURED_REQUIRES_READ.marshallAsAttribute(model, writer);
            SensitivityResourceDefinition.CONFIGURED_REQUIRES_WRITE.marshallAsAttribute(model, writer);
        }

        if (configuredConstraints.containsKey(SENSITIVITY_CLASSIFICATION)) {
            writer.writeStartElement(Element.SENSITIVE_CLASSIFICATIONS.getLocalName());
            Map<String, Set<String>> constraints = configuredConstraints.get(SENSITIVITY_CLASSIFICATION);
            for (Map.Entry<String, Set<String>> entry : constraints.entrySet()) {
                for (String classification : entry.getValue()) {
                    writer.writeEmptyElement(Element.SENSITIVE_CLASSIFICATION.getLocalName());
                    ModelNode model = accessAuthorization.get(CONSTRAINT, SENSITIVITY_CLASSIFICATION, TYPE, entry.getKey(), CLASSIFICATION, classification);
                    writeAttribute(writer, Attribute.TYPE, entry.getKey());
                    writeAttribute(writer, Attribute.NAME, classification);
                    SensitivityResourceDefinition.CONFIGURED_REQUIRES_ADDRESSABLE.marshallAsAttribute(model, writer);
                    SensitivityResourceDefinition.CONFIGURED_REQUIRES_READ.marshallAsAttribute(model, writer);
                    SensitivityResourceDefinition.CONFIGURED_REQUIRES_WRITE.marshallAsAttribute(model, writer);
                }
            }
            writer.writeEndElement();
        }
        if (configuredConstraints.containsKey(APPLICATION_CLASSIFICATION)) {
            writer.writeStartElement(Element.APPLICATION_CLASSIFICATIONS.getLocalName());
            Map<String, Set<String>> constraints = configuredConstraints.get(APPLICATION_CLASSIFICATION);
            for (Map.Entry<String, Set<String>> entry : constraints.entrySet()) {

                for (String classification : entry.getValue()) {
                    writer.writeEmptyElement(Element.APPLICATION_CLASSIFICATION.getLocalName());
                    ModelNode model = accessAuthorization.get(CONSTRAINT, APPLICATION_CLASSIFICATION, TYPE, entry.getKey(), CLASSIFICATION, classification);
                    writeAttribute(writer, Attribute.TYPE, entry.getKey());
                    writeAttribute(writer, Attribute.NAME, classification);
                    ApplicationClassificationConfigResourceDefinition.CONFIGURED_APPLICATION.marshallAsAttribute(model, writer);
                }
            }
            writer.writeEndElement();
        }
        writer.writeEndElement();
    }

    private void writeRoleMapping(XMLExtendedStreamWriter writer, ModelNode accessAuthorization)
            throws XMLStreamException {
        writer.writeStartElement(Element.ROLE_MAPPING.getLocalName());


        if (accessAuthorization.hasDefined(ROLE_MAPPING)) {
            ModelNode roleMappings = accessAuthorization.get(ROLE_MAPPING);

            for (Property variable : roleMappings.asPropertyList()) {
                writer.writeStartElement(Element.ROLE.getLocalName());
                writeAttribute(writer, Attribute.NAME, variable.getName());
                ModelNode role = variable.getValue();
                RoleMappingResourceDefinition.INCLUDE_ALL.marshallAsAttribute(role, writer);
                if (role.hasDefined(INCLUDE)) {
                    writeIncludeExclude(writer, Element.INCLUDE.getLocalName(), role.get(INCLUDE));
                }

                if (role.hasDefined(EXCLUDE)) {
                    writeIncludeExclude(writer, Element.EXCLUDE.getLocalName(), role.get(EXCLUDE));
                }

                writer.writeEndElement();
            }
        }

        writer.writeEndElement();
    }

    private void writeIncludeExclude(XMLExtendedStreamWriter writer, String elementName, ModelNode includeExclude)
            throws XMLStreamException {
        List<Property> list = includeExclude.asPropertyList();
        if (list.isEmpty()) {
            return;
        }

        writer.writeStartElement(elementName);
        for (Property current : list) {
            // The names where only arbitrary to allow unique referencing.
            writePrincipal(writer, current.getName(), current.getValue());
        }

        writer.writeEndElement();
    }

    private void writePrincipal(XMLExtendedStreamWriter writer, String alias, ModelNode principal) throws XMLStreamException {
        String elementName = principal.require(TYPE).asString().equalsIgnoreCase(GROUP) ? Element.GROUP.getLocalName() : Element.USER.getLocalName();
        writer.writeStartElement(elementName);

        String realm = principal.get(REALM).isDefined() ? principal.require(REALM).asString() : null;
        String name = principal.require(NAME).asString();

        String expectedAlias = AccessControlXml.generateAlias(elementName, name, realm);
        if (alias.equals(expectedAlias)==false) {
            writeAttribute(writer, Attribute.ALIAS, alias);
        }

        PrincipalResourceDefinition.REALM.marshallAsAttribute(principal, writer);

        PrincipalResourceDefinition.NAME.marshallAsAttribute(principal, writer);

        writer.writeEndElement();
    }


    static Map<String, Map<String, Set<String>>> getConfiguredAccessConstraints(ModelNode accessAuthorization) {
        Map<String, Map<String, Set<String>>> configuredConstraints = new HashMap<String, Map<String, Set<String>>>();
        if (accessAuthorization != null && accessAuthorization.hasDefined(CONSTRAINT)) {
            ModelNode constraint = accessAuthorization.get(CONSTRAINT);

            configuredConstraints.putAll(getVaultConstraints(constraint));
            configuredConstraints.putAll(getSensitivityClassificationConstraints(constraint));
            configuredConstraints.putAll(getApplicationClassificationConstraints(constraint));
        }

        return configuredConstraints;
    }

    static Map<String, Map<String, Set<String>>> getVaultConstraints(final ModelNode constraint) {
        Map<String, Map<String, Set<String>>> configuredConstraints = new HashMap<String, Map<String, Set<String>>>();

        if (constraint.hasDefined(VAULT_EXPRESSION)) {
            ModelNode classification = constraint.require(VAULT_EXPRESSION);
            if (classification.hasDefined(SensitivityResourceDefinition.CONFIGURED_REQUIRES_WRITE.getName())
                    || classification.hasDefined(SensitivityResourceDefinition.CONFIGURED_REQUIRES_READ.getName())) {
                configuredConstraints.put(SensitivityResourceDefinition.VAULT_ELEMENT.getKey(),
                        Collections.<String, Set<String>> emptyMap());
            }
        }

        return configuredConstraints;
    }

    static Map<String, Map<String, Set<String>>> getSensitivityClassificationConstraints(final ModelNode constraint) {
        Map<String, Map<String, Set<String>>> configuredConstraints = new HashMap<String, Map<String, Set<String>>>();

        if (constraint.hasDefined(SENSITIVITY_CLASSIFICATION)) {
            ModelNode sensitivityParent = constraint.require(SENSITIVITY_CLASSIFICATION);

            if (sensitivityParent.hasDefined(TYPE)) {
                for (Property typeProperty : sensitivityParent.get(TYPE).asPropertyList()) {
                    if (typeProperty.getValue().hasDefined(CLASSIFICATION)) {
                        for (Property sensitivityProperty : typeProperty.getValue().get(CLASSIFICATION).asPropertyList()) {
                            ModelNode classification = sensitivityProperty.getValue();
                            if (classification.hasDefined(SensitivityResourceDefinition.CONFIGURED_REQUIRES_ADDRESSABLE.getName())
                                    || classification.hasDefined(SensitivityResourceDefinition.CONFIGURED_REQUIRES_WRITE
                                            .getName())
                                    || classification.hasDefined(SensitivityResourceDefinition.CONFIGURED_REQUIRES_READ
                                            .getName())) {
                                Map<String, Set<String>> constraintMap = configuredConstraints.get(SENSITIVITY_CLASSIFICATION);
                                if (constraintMap == null) {
                                    constraintMap = new TreeMap<String, Set<String>>();
                                    configuredConstraints.put(SENSITIVITY_CLASSIFICATION, constraintMap);
                                }
                                Set<String> types = constraintMap.get(typeProperty.getName());
                                if (types == null) {
                                    types = new TreeSet<String>();
                                    constraintMap.put(typeProperty.getName(), types);
                                }
                                types.add(sensitivityProperty.getName());
                            }
                        }
                    }
                }
            }
        }

        return configuredConstraints;
    }

    static Map<String, Map<String, Set<String>>> getApplicationClassificationConstraints(final ModelNode constraint) {
        Map<String, Map<String, Set<String>>> configuredConstraints = new HashMap<String, Map<String, Set<String>>>();

        if (constraint.hasDefined(APPLICATION_CLASSIFICATION)) {
            ModelNode appTypeParent = constraint.require(APPLICATION_CLASSIFICATION);

            if (appTypeParent.hasDefined(TYPE)) {
                for (Property typeProperty : appTypeParent.get(TYPE).asPropertyList()) {
                    if (typeProperty.getValue().hasDefined(CLASSIFICATION)) {
                        for (Property applicationProperty : typeProperty.getValue().get(CLASSIFICATION).asPropertyList()) {
                            ModelNode applicationType = applicationProperty.getValue();
                            if (applicationType.hasDefined(ApplicationClassificationConfigResourceDefinition.CONFIGURED_APPLICATION.getName())) {
                                Map<String, Set<String>> constraintMap = configuredConstraints.get(APPLICATION_CLASSIFICATION);
                                if (constraintMap == null) {
                                    constraintMap = new TreeMap<String, Set<String>>();
                                    configuredConstraints.put(APPLICATION_CLASSIFICATION, constraintMap);
                                }
                                Set<String> types = constraintMap.get(typeProperty.getName());
                                if (types == null) {
                                    types = new TreeSet<String>();
                                    constraintMap.put(typeProperty.getName(), types);
                                }
                                types.add(applicationProperty.getName());
                            }
                        }
                    }
                }
            }
        }

        return configuredConstraints;
    }

    private static ModelNode parse(AttributeDefinition ad, String value, XMLExtendedStreamReader reader) throws XMLStreamException {
        return ad.getParser().parse(ad,value,reader);
    }
}
