/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.extension.elytron;

import static javax.xml.stream.XMLStreamConstants.END_ELEMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ROLE;
import static org.jboss.as.controller.parsing.ParseUtils.isNoNamespaceAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.missingRequired;
import static org.jboss.as.controller.parsing.ParseUtils.missingRequiredElement;
import static org.jboss.as.controller.parsing.ParseUtils.requireNoAttributes;
import static org.jboss.as.controller.parsing.ParseUtils.requireNoContent;
import static org.jboss.as.controller.parsing.ParseUtils.requireSingleAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedElement;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.ACTION;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.ADD_PREFIX_ROLE_MAPPER;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.ADD_SUFFIX_ROLE_MAPPER;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.AGGREGATE_PRINCIPAL_DECODER;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.AGGREGATE_PRINCIPAL_TRANSFORMER;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.AGGREGATE_ROLE_MAPPER;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.ATTRIBUTE;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.ATTRIBUTE_NAME;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.CHAINED_PRINCIPAL_TRANSFORMER;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.CLASS_NAME;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.CONCATENATING_PRINCIPAL_DECODER;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.CONSTANT;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.CONSTANT_PERMISSION_MAPPER;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.CONSTANT_PRINCIPAL_DECODER;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.CONSTANT_PRINCIPAL_TRANSFORMER;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.CONSTANT_REALM_MAPPER;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.CONSTANT_ROLE_MAPPER;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.CUSTOM_PERMISSION_MAPPER;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.CUSTOM_PRINCIPAL_DECODER;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.CUSTOM_PRINCIPAL_TRANSFORMER;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.CUSTOM_REALM_MAPPER;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.CUSTOM_ROLE_DECODER;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.CUSTOM_ROLE_MAPPER;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.DELEGATE_REALM_MAPPER;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.FROM;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.JOINER;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.LEFT;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.LOGICAL_OPERATION;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.LOGICAL_PERMISSION_MAPPER;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.LOGICAL_ROLE_MAPPER;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.MAPPED_REGEX_REALM_MAPPER;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.MAPPERS;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.MAPPING_MODE;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.MATCH;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.MAXIMUM_SEGMENTS;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.MODULE;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.NAME;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.OID;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.PATTERN;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.PERMISSION;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.PERMISSIONS;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.PERMISSION_MAPPING;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.PERMISSION_MAPPINGS;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.PREFIX;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.PRINCIPALS;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.PRINCIPAL_DECODER;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.PRINCIPAL_DECODERS;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.PRINCIPAL_TRANSFORMER;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.PRINCIPAL_TRANSFORMERS;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.REALM_MAP;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.REALM_MAPPING;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.REALM_NAME;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.REGEX_PRINCIPAL_TRANSFORMER;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.REGEX_VALIDATING_PRINCIPAL_TRANSFORMER;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.REPLACEMENT;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.REPLACE_ALL;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.REQUIRED_ATTRIBUTES;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.REQUIRED_OIDS;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.REVERSE;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.RIGHT;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.ROLES;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.ROLE_MAPPER;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.ROLE_MAPPERS;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.SIMPLE_PERMISSION_MAPPER;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.SIMPLE_REGEX_REALM_MAPPER;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.SIMPLE_ROLE_DECODER;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.START_SEGMENT;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.SUFFIX;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.TARGET_NAME;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.TO;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.X500_ATTRIBUTE_PRINCIPAL_DECODER;
import static org.wildfly.extension.elytron.ElytronSubsystemParser.readCustomComponent;
import static org.wildfly.extension.elytron.ElytronSubsystemParser.verifyNamespace;
import static org.wildfly.extension.elytron.ElytronSubsystemParser.writeCustomComponent;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.ListAttributeDefinition;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;

/**
 * XML handling for the <mappers /> element.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class MapperParser {

    void readMappers(ModelNode parentAddress, XMLExtendedStreamReader reader, List<ModelNode> operations) throws XMLStreamException {
        requireNoAttributes(reader);
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            verifyNamespace(reader);
            String localName = reader.getLocalName();
            switch (localName) {
                // Permission Mapper
                case CUSTOM_PERMISSION_MAPPER:
                    readCustomComponent(CUSTOM_PERMISSION_MAPPER, parentAddress, reader, operations);
                    break;
                case LOGICAL_PERMISSION_MAPPER:
                    readLogicalPermissionMapper(parentAddress, reader, operations);
                    break;
                case SIMPLE_PERMISSION_MAPPER:
                    readSimplePermissionMapper(parentAddress, reader, operations);
                    break;
                case CONSTANT_PERMISSION_MAPPER:
                    readConstantPermissionMapper(parentAddress, reader, operations);
                    break;
                // Principal Decoders
                case AGGREGATE_PRINCIPAL_DECODER:
                    readAggregatePrincipalDecoderElement(parentAddress, reader, operations);
                    break;
                case CONCATENATING_PRINCIPAL_DECODER:
                    readConcatenatingPrincipalDecoderElement(parentAddress, reader, operations);
                    break;
                case CONSTANT_PRINCIPAL_DECODER:
                    readConstantPrincipalDecoderElement(parentAddress, reader, operations);
                    break;
                case CUSTOM_PRINCIPAL_DECODER:
                    readCustomComponent(CUSTOM_PRINCIPAL_DECODER, parentAddress, reader, operations);
                    break;
                case X500_ATTRIBUTE_PRINCIPAL_DECODER:
                    readX500AttributePrincipalDecoderElement(parentAddress, reader, operations);
                    break;
                // Principal Transformers
                case AGGREGATE_PRINCIPAL_TRANSFORMER:
                    readAggregatePrincipalTransformerElement(parentAddress, reader, operations);
                    break;
                case CHAINED_PRINCIPAL_TRANSFORMER:
                    readChainedPrincipalTransformersElement(parentAddress, reader, operations);
                    break;
                case CONSTANT_PRINCIPAL_TRANSFORMER:
                    readConstantPrincipalTransformerElement(parentAddress, reader, operations);
                    break;
                case CUSTOM_PRINCIPAL_TRANSFORMER:
                    readCustomComponent(CUSTOM_PRINCIPAL_TRANSFORMER, parentAddress, reader, operations);
                    break;
                case REGEX_PRINCIPAL_TRANSFORMER:
                    readRegexPrincipalTransformerElement(parentAddress, reader, operations);
                    break;
                case REGEX_VALIDATING_PRINCIPAL_TRANSFORMER:
                    readRegexValidatingPrincipalTransformerElement(parentAddress, reader, operations);
                    break;
                // Realm Mappers
                case CONSTANT_REALM_MAPPER:
                    readConstantRealmMapperElement(parentAddress, reader, operations);
                    break;
                case CUSTOM_REALM_MAPPER:
                    readCustomComponent(CUSTOM_REALM_MAPPER, parentAddress, reader, operations);
                    break;
                case SIMPLE_REGEX_REALM_MAPPER:
                    readSimpleRegexRealmMapperElement(parentAddress, reader, operations);
                    break;
                case MAPPED_REGEX_REALM_MAPPER:
                    readMappedRegexRealmMapperElement(parentAddress, reader, operations);
                    break;
                // Role Decoders
                case CUSTOM_ROLE_DECODER:
                    readCustomComponent(CUSTOM_ROLE_DECODER, parentAddress, reader, operations);
                    break;
                case SIMPLE_ROLE_DECODER:
                    readSimpleRoleDecoder(parentAddress, reader, operations);
                    break;
                // Role Mappers
                case ADD_PREFIX_ROLE_MAPPER:
                    readAddPrefixRoleMapper(parentAddress, reader, operations);
                    break;
                case ADD_SUFFIX_ROLE_MAPPER:
                    readAddSuffixRoleMapper(parentAddress, reader, operations);
                    break;
                case AGGREGATE_ROLE_MAPPER:
                    readAggregateRoleMapperElement(parentAddress, reader, operations);
                    break;
                case CONSTANT_ROLE_MAPPER:
                    readConstantRoleMapper(parentAddress, reader, operations);
                    break;
                case CUSTOM_ROLE_MAPPER:
                    readCustomComponent(CUSTOM_ROLE_MAPPER, parentAddress, reader, operations);
                    break;
                case LOGICAL_ROLE_MAPPER:
                    readLogicalRoleMapper(parentAddress, reader, operations);
                    break;
                default:
                    throw unexpectedElement(reader);
            }
        }
    }

    private void readLogicalPermissionMapper(ModelNode parentAddress, XMLExtendedStreamReader reader, List<ModelNode> operations) throws XMLStreamException {
        ModelNode addPermissionMapper = new ModelNode();
        addPermissionMapper.get(OP).set(ADD);

        Set<String> requiredAttributes = new HashSet<String>(Arrays.asList(new String[] { NAME, LOGICAL_OPERATION, LEFT, RIGHT }));

        String name = null;

        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                String attribute = reader.getAttributeLocalName(i);
                requiredAttributes.remove(attribute);
                switch (attribute) {
                    case NAME:
                        name = value;
                        break;
                    case LOGICAL_OPERATION:
                        PermissionMapperDefinitions.LOGICAL_OPERATION.parseAndSetParameter(value, addPermissionMapper, reader);
                        break;
                    case LEFT:
                        PermissionMapperDefinitions.LEFT.parseAndSetParameter(value, addPermissionMapper, reader);
                        break;
                    case RIGHT:
                        PermissionMapperDefinitions.RIGHT.parseAndSetParameter(value, addPermissionMapper, reader);
                        break;
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }

        if (requiredAttributes.isEmpty() == false) {
            throw missingRequired(reader, requiredAttributes);
        }

        requireNoContent(reader);

        addPermissionMapper.get(OP_ADDR).set(parentAddress).add(LOGICAL_PERMISSION_MAPPER, name);
        operations.add(addPermissionMapper);
    }

    private void readSimplePermissionMapper(ModelNode parentAddress, XMLExtendedStreamReader reader, List<ModelNode> operations) throws XMLStreamException {
        ModelNode addPermissionMapper = new ModelNode();
        addPermissionMapper.get(OP).set(ADD);

        String name = null;

        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                String attribute = reader.getAttributeLocalName(i);
                switch (attribute) {
                    case NAME:
                        name = value;
                        break;
                    case MAPPING_MODE:
                        PermissionMapperDefinitions.MAPPING_MODE.parseAndSetParameter(value, addPermissionMapper, reader);
                        break;
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }

        if (name == null) {
            throw missingRequired(reader, NAME);
        }

        ModelNode permissionMappings = new ModelNode();

        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            verifyNamespace(reader);
            String localName = reader.getLocalName();
            if (PERMISSION_MAPPING.equals(localName) == false) {
                throw unexpectedElement(reader);
            }
            permissionMappings.add(readPermissionMapping(reader));
        }

        addPermissionMapper.get(OP_ADDR).set(parentAddress).add(SIMPLE_PERMISSION_MAPPER, name);
        addPermissionMapper.get(PERMISSION_MAPPINGS).set(permissionMappings);
        operations.add(addPermissionMapper);
    }

    private void readConstantPermissionMapper(ModelNode parentAddress, XMLExtendedStreamReader reader, List<ModelNode> operations) throws XMLStreamException {
        ModelNode addPermissionMapper = new ModelNode();
        addPermissionMapper.get(OP).set(ADD);

        String name = null;

        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                String attribute = reader.getAttributeLocalName(i);
                switch (attribute) {
                    case NAME:
                        name = value;
                        break;
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }

        if (name == null) {
            throw missingRequired(reader, NAME);
        }

        ModelNode permissions = new ModelNode();

        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            verifyNamespace(reader);
            String localName = reader.getLocalName();
            if (PERMISSION.equals(localName) == false) {
                throw unexpectedElement(reader);
            }
            permissions.add(readPermission(reader));
        }

        addPermissionMapper.get(OP_ADDR).set(parentAddress).add(CONSTANT_PERMISSION_MAPPER, name);
        addPermissionMapper.get(PERMISSIONS).set(permissions);
        operations.add(addPermissionMapper);
    }

    private ModelNode readPermissionMapping(XMLExtendedStreamReader reader) throws XMLStreamException {
        ModelNode permissionMapping = new ModelNode();

        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                String attribute = reader.getAttributeLocalName(i);
                switch (attribute) {
                    case PRINCIPALS:
                        for (String principal : reader.getListAttributeValue(i)) {
                            PermissionMapperDefinitions.PRINCIPALS.parseAndAddParameterElement(principal, permissionMapping, reader);
                        }
                        break;
                    case ROLES:
                        for (String role : reader.getListAttributeValue(i)) {
                            PermissionMapperDefinitions.ROLES.parseAndAddParameterElement(role, permissionMapping, reader);
                        }
                        break;
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }

        ModelNode permissions = new ModelNode();

        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            verifyNamespace(reader);
            String localName = reader.getLocalName();
            if (PERMISSION.equals(localName) == false) {
                throw unexpectedElement(reader);
            }
            permissions.add(readPermission(reader));
        }
        permissionMapping.get(PERMISSIONS).set(permissions);

        return permissionMapping;
    }

    private ModelNode readPermission(XMLExtendedStreamReader reader) throws XMLStreamException {
        ModelNode permission = new ModelNode();

        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                String attribute = reader.getAttributeLocalName(i);
                switch (attribute) {
                    case CLASS_NAME:
                        ClassLoadingAttributeDefinitions.CLASS_NAME.parseAndSetParameter(value, permission, reader);
                        break;
                    case MODULE:
                        ClassLoadingAttributeDefinitions.MODULE.parseAndSetParameter(value, permission, reader);
                        break;
                    case TARGET_NAME:
                        PermissionMapperDefinitions.TARGET_NAME.parseAndSetParameter(value, permission, reader);
                        break;
                    case ACTION:
                        PermissionMapperDefinitions.ACTION.parseAndSetParameter(value, permission, reader);
                        break;
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }

        if (permission.get(CLASS_NAME).isDefined() == false) {
            throw missingRequired(reader, CLASS_NAME);
        }

        requireNoContent(reader);

        return permission;
    }

    private void readAggregatePrincipalDecoderElement(ModelNode parentAddress, XMLExtendedStreamReader reader, List<ModelNode> operations)
            throws XMLStreamException {
        ModelNode addPrincipalDecoder = new ModelNode();
        addPrincipalDecoder.get(OP).set(ADD);

        String name = null;

        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                String attribute = reader.getAttributeLocalName(i);
                switch (attribute) {
                    case NAME:
                        name = value;
                        break;
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }

        if (name == null) {
            throw missingRequired(reader, NAME);
        }

        addPrincipalDecoder.get(OP_ADDR).set(parentAddress).add(AGGREGATE_PRINCIPAL_DECODER, name);

        operations.add(addPrincipalDecoder);

        ListAttributeDefinition principalDecoders = PrincipalDecoderDefinitions.getAggregatePrincipalDecoderDefinition().getReferencesAttribute();
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            verifyNamespace(reader);
            String localName = reader.getLocalName();
            if (PRINCIPAL_DECODER.equals(localName) == false) {
                throw unexpectedElement(reader);
            }

            requireSingleAttribute(reader, NAME);
            String principalDecoderName = reader.getAttributeValue(0);


            principalDecoders.parseAndAddParameterElement(principalDecoderName, addPrincipalDecoder, reader);

            requireNoContent(reader);
        }
    }

    private void readConcatenatingPrincipalDecoderElement(ModelNode parentAddress, XMLExtendedStreamReader reader, List<ModelNode> operations)
            throws XMLStreamException {
        ModelNode addPrincipalDecoder = new ModelNode();
        addPrincipalDecoder.get(OP).set(ADD);

        Set<String> requiredAttributes = new HashSet<>(Arrays.asList(new String[] { NAME }));

        String name = null;

        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (! isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                String attribute = reader.getAttributeLocalName(i);
                requiredAttributes.remove(attribute);
                switch (attribute) {
                    case NAME:
                        name = value;
                        break;
                    case JOINER:
                        PrincipalDecoderDefinitions.JOINER.parseAndSetParameter(value, addPrincipalDecoder, reader);
                        break;
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }

        if (requiredAttributes.isEmpty() == false) {
            throw missingRequired(reader, requiredAttributes);
        }

        addPrincipalDecoder.get(OP_ADDR).set(parentAddress).add(CONCATENATING_PRINCIPAL_DECODER, name);

        operations.add(addPrincipalDecoder);

        ListAttributeDefinition principalDecoders = PrincipalDecoderDefinitions.PRINCIPAL_DECODERS;
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            verifyNamespace(reader);
            String localName = reader.getLocalName();
            if (PRINCIPAL_DECODER.equals(localName) == false) {
                throw unexpectedElement(reader);
            }

            requireSingleAttribute(reader, NAME);
            String principalDecoderName = reader.getAttributeValue(0);
            principalDecoders.parseAndAddParameterElement(principalDecoderName, addPrincipalDecoder, reader);
            requireNoContent(reader);
        }
    }

    private void readConstantPrincipalDecoderElement(ModelNode parentAddress, XMLExtendedStreamReader reader, List<ModelNode> operations)
            throws XMLStreamException {
        ModelNode addPrincipalDecoder = new ModelNode();
        addPrincipalDecoder.get(OP).set(ADD);

        Set<String> requiredAttributes = new HashSet<>(Arrays.asList(new String[] { NAME, CONSTANT }));

        String name = null;
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (! isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                String attribute = reader.getAttributeLocalName(i);
                requiredAttributes.remove(attribute);
                switch (attribute) {
                    case NAME:
                        name = value;
                        break;
                    case CONSTANT:
                        PrincipalDecoderDefinitions.CONSTANT.parseAndSetParameter(value, addPrincipalDecoder, reader);
                        break;
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }

        if (requiredAttributes.isEmpty() == false) {
            throw missingRequired(reader, requiredAttributes);
        }

        addPrincipalDecoder.get(OP_ADDR).set(parentAddress).add(CONSTANT_PRINCIPAL_DECODER, name);
        operations.add(addPrincipalDecoder);
        requireNoContent(reader);
    }

    private void readX500AttributePrincipalDecoderElement(ModelNode parentAddress, XMLExtendedStreamReader reader, List<ModelNode> operations)
            throws XMLStreamException {
        ModelNode addPrincipalDecoder = new ModelNode();
        addPrincipalDecoder.get(OP).set(ADD);

        Set<String> requiredAttributes = new HashSet<String>(Arrays.asList(new String[] { NAME }));

        String name = null;

        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                String attribute = reader.getAttributeLocalName(i);
                requiredAttributes.remove(attribute);
                switch (attribute) {
                    case NAME:
                        name = value;
                        break;
                    case OID:
                        PrincipalDecoderDefinitions.OID.parseAndSetParameter(value, addPrincipalDecoder, reader);
                        break;
                    case ATTRIBUTE_NAME:
                        PrincipalDecoderDefinitions.ATTRIBUTE_NAME.parseAndSetParameter(value, addPrincipalDecoder, reader);
                        break;
                    case JOINER:
                        PrincipalDecoderDefinitions.JOINER.parseAndSetParameter(value, addPrincipalDecoder, reader);
                        break;
                    case START_SEGMENT:
                        PrincipalDecoderDefinitions.START_SEGMENT.parseAndSetParameter(value, addPrincipalDecoder, reader);
                        break;
                    case MAXIMUM_SEGMENTS:
                        PrincipalDecoderDefinitions.MAXIMUM_SEGMENTS.parseAndSetParameter(value, addPrincipalDecoder, reader);
                        break;
                    case REVERSE:
                        PrincipalDecoderDefinitions.REVERSE.parseAndSetParameter(value, addPrincipalDecoder, reader);
                        break;
                    case REQUIRED_OIDS:
                        for (String requiredOid : reader.getListAttributeValue(i)) {
                            PrincipalDecoderDefinitions.REQUIRED_OIDS.parseAndAddParameterElement(requiredOid, addPrincipalDecoder, reader);
                        }
                        break;
                    case REQUIRED_ATTRIBUTES:
                        for (String requiredOid : reader.getListAttributeValue(i)) {
                            PrincipalDecoderDefinitions.REQUIRED_ATTRIBUTES.parseAndAddParameterElement(requiredOid, addPrincipalDecoder, reader);
                        }
                        break;
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }

        if (requiredAttributes.isEmpty() == false) {
            throw missingRequired(reader, requiredAttributes);
        }

        addPrincipalDecoder.get(OP_ADDR).set(parentAddress).add(X500_ATTRIBUTE_PRINCIPAL_DECODER, name);

        operations.add(addPrincipalDecoder);

        requireNoContent(reader);
    }

    private void readAggregatePrincipalTransformerElement(ModelNode parentAddress, XMLExtendedStreamReader reader, List<ModelNode> operations)
            throws XMLStreamException {
        ModelNode addPrincipalTransformer = new ModelNode();
        addPrincipalTransformer.get(OP).set(ADD);

        String name = null;

        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                String attribute = reader.getAttributeLocalName(i);
                switch (attribute) {
                    case NAME:
                        name = value;
                        break;
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }

        if (name == null) {
            throw missingRequired(reader, NAME);
        }

        addPrincipalTransformer.get(OP_ADDR).set(parentAddress).add(AGGREGATE_PRINCIPAL_TRANSFORMER, name);

        operations.add(addPrincipalTransformer);

        ListAttributeDefinition principalTranformers = PrincipalTransformerDefinitions.getAggregatePrincipalTransformerDefinition().getReferencesAttribute();
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            verifyNamespace(reader);
            String localName = reader.getLocalName();
            if (PRINCIPAL_TRANSFORMER.equals(localName) == false) {
                throw unexpectedElement(reader);
            }

            requireSingleAttribute(reader, NAME);
            String principalTransformerName = reader.getAttributeValue(0);


            principalTranformers.parseAndAddParameterElement(principalTransformerName, addPrincipalTransformer, reader);

            requireNoContent(reader);
        }
    }

    private void readChainedPrincipalTransformersElement(ModelNode parentAddress, XMLExtendedStreamReader reader, List<ModelNode> operations)
            throws XMLStreamException {
        ModelNode addPrincipalTransformer = new ModelNode();
        addPrincipalTransformer.get(OP).set(ADD);

        String name = null;

        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                String attribute = reader.getAttributeLocalName(i);
                switch (attribute) {
                    case NAME:
                        name = value;
                        break;
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }

        if (name == null) {
            throw missingRequired(reader, NAME);
        }

        addPrincipalTransformer.get(OP_ADDR).set(parentAddress).add(CHAINED_PRINCIPAL_TRANSFORMER, name);

        operations.add(addPrincipalTransformer);

        ListAttributeDefinition principalTransformers = PrincipalTransformerDefinitions.getChainedPrincipalTransformerDefinition().getReferencesAttribute();
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            verifyNamespace(reader);
            String localName = reader.getLocalName();
            if (PRINCIPAL_TRANSFORMER.equals(localName) == false) {
                throw unexpectedElement(reader);
            }

            requireSingleAttribute(reader, NAME);
            String principalTransformerName = reader.getAttributeValue(0);


            principalTransformers.parseAndAddParameterElement(principalTransformerName, addPrincipalTransformer, reader);

            requireNoContent(reader);
        }
    }

    private void readConstantPrincipalTransformerElement(ModelNode parentAddress, XMLExtendedStreamReader reader, List<ModelNode> operations)
            throws XMLStreamException {
        ModelNode addPrincipalTransformer = new ModelNode();
        addPrincipalTransformer.get(OP).set(ADD);

        Set<String> requiredAttributes = new HashSet<String>(Arrays.asList(new String[] { NAME, CONSTANT }));

        String name = null;

        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                String attribute = reader.getAttributeLocalName(i);
                requiredAttributes.remove(attribute);
                switch (attribute) {
                    case NAME:
                        name = value;
                        break;
                    case CONSTANT:
                        PrincipalTransformerDefinitions.CONSTANT.parseAndSetParameter(value, addPrincipalTransformer, reader);
                        break;
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }

        if (requiredAttributes.isEmpty() == false) {
            throw missingRequired(reader, requiredAttributes);
        }

        addPrincipalTransformer.get(OP_ADDR).set(parentAddress).add(CONSTANT_PRINCIPAL_TRANSFORMER, name);

        operations.add(addPrincipalTransformer);

        requireNoContent(reader);
    }

    private void readRegexPrincipalTransformerElement(ModelNode parentAddress, XMLExtendedStreamReader reader, List<ModelNode> operations)
            throws XMLStreamException {
        ModelNode addPrincipalTransformer = new ModelNode();
        addPrincipalTransformer.get(OP).set(ADD);

        Set<String> requiredAttributes = new HashSet<String>(Arrays.asList(new String[] { NAME, PATTERN, REPLACEMENT }));

        String name = null;

        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                String attribute = reader.getAttributeLocalName(i);
                requiredAttributes.remove(attribute);
                switch (attribute) {
                    case NAME:
                        name = value;
                        break;
                    case PATTERN:
                        RegexAttributeDefinitions.PATTERN.parseAndSetParameter(value, addPrincipalTransformer, reader);
                        break;
                    case REPLACEMENT:
                        PrincipalTransformerDefinitions.REPLACEMENT.parseAndSetParameter(value, addPrincipalTransformer, reader);
                        break;
                    case REPLACE_ALL:
                        PrincipalTransformerDefinitions.REPLACE_ALL.parseAndSetParameter(value, addPrincipalTransformer, reader);
                        break;
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }

        if (requiredAttributes.isEmpty() == false) {
            throw missingRequired(reader, requiredAttributes);
        }

        addPrincipalTransformer.get(OP_ADDR).set(parentAddress).add(REGEX_PRINCIPAL_TRANSFORMER, name);

        operations.add(addPrincipalTransformer);

        requireNoContent(reader);
    }

    private void readRegexValidatingPrincipalTransformerElement(ModelNode parentAddress, XMLExtendedStreamReader reader, List<ModelNode> operations)
            throws XMLStreamException {
        ModelNode addPrincipalTransformer = new ModelNode();
        addPrincipalTransformer.get(OP).set(ADD);

        Set<String> requiredAttributes = new HashSet<String>(Arrays.asList(new String[] { NAME, PATTERN }));

        String name = null;

        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                String attribute = reader.getAttributeLocalName(i);
                requiredAttributes.remove(attribute);
                switch (attribute) {
                    case NAME:
                        name = value;
                        break;
                    case PATTERN:
                        RegexAttributeDefinitions.PATTERN.parseAndSetParameter(value, addPrincipalTransformer, reader);
                        break;
                    case MATCH:
                        PrincipalTransformerDefinitions.MATCH.parseAndSetParameter(value, addPrincipalTransformer, reader);
                        break;
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }

        if (requiredAttributes.isEmpty() == false) {
            throw missingRequired(reader, requiredAttributes);
        }

        addPrincipalTransformer.get(OP_ADDR).set(parentAddress).add(REGEX_VALIDATING_PRINCIPAL_TRANSFORMER, name);

        operations.add(addPrincipalTransformer);

        requireNoContent(reader);
    }

    private void readConstantRealmMapperElement(ModelNode parentAddress, XMLExtendedStreamReader reader, List<ModelNode> operations)
            throws XMLStreamException {
        ModelNode addRealmMapper = new ModelNode();
        addRealmMapper.get(OP).set(ADD);

        Set<String> requiredAttributes = new HashSet<>(Arrays.asList(new String[] { NAME, REALM_NAME }));

        String name = null;
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (! isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                String attribute = reader.getAttributeLocalName(i);
                requiredAttributes.remove(attribute);
                switch (attribute) {
                    case NAME:
                        name = value;
                        break;
                    case REALM_NAME:
                        RealmMapperDefinitions.REALM_NAME.parseAndSetParameter(value, addRealmMapper, reader);
                        break;
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }

        if (requiredAttributes.isEmpty() == false) {
            throw missingRequired(reader, requiredAttributes);
        }

        addRealmMapper.get(OP_ADDR).set(parentAddress).add(CONSTANT_REALM_MAPPER, name);
        operations.add(addRealmMapper);
        requireNoContent(reader);
    }

    private void readSimpleRegexRealmMapperElement(ModelNode parentAddress, XMLExtendedStreamReader reader, List<ModelNode> operations)
            throws XMLStreamException {
        ModelNode addRealmMapper = new ModelNode();
        addRealmMapper.get(OP).set(ADD);

        Set<String> requiredAttributes = new HashSet<String>(Arrays.asList(new String[] { NAME, PATTERN }));

        String name = null;

        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                String attribute = reader.getAttributeLocalName(i);
                requiredAttributes.remove(attribute);
                switch (attribute) {
                    case NAME:
                        name = value;
                        break;
                    case PATTERN:
                        RegexAttributeDefinitions.PATTERN_CAPTURE_GROUP.parseAndSetParameter(value, addRealmMapper, reader);
                        break;
                    case DELEGATE_REALM_MAPPER:
                        RealmMapperDefinitions.DELEGATE_REALM_MAPPER.parseAndSetParameter(value, addRealmMapper, reader);
                        break;
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }

        if (requiredAttributes.isEmpty() == false) {
            throw missingRequired(reader, requiredAttributes);
        }

        addRealmMapper.get(OP_ADDR).set(parentAddress).add(SIMPLE_REGEX_REALM_MAPPER, name);

        operations.add(addRealmMapper);

        requireNoContent(reader);
    }

    private void readMappedRegexRealmMapperElement(ModelNode parentAddress, XMLExtendedStreamReader reader, List<ModelNode> operations)
            throws XMLStreamException {
        ModelNode addRealmMapper = new ModelNode();
        addRealmMapper.get(OP).set(ADD);

        Set<String> requiredAttributes = new HashSet<String>(Arrays.asList(new String[] { NAME, PATTERN }));

        String name = null;

        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                String attribute = reader.getAttributeLocalName(i);
                requiredAttributes.remove(attribute);
                switch (attribute) {
                    case NAME:
                        name = value;
                        break;
                    case PATTERN:
                        RegexAttributeDefinitions.PATTERN_CAPTURE_GROUP.parseAndSetParameter(value, addRealmMapper, reader);
                        break;
                    case DELEGATE_REALM_MAPPER:
                        RealmMapperDefinitions.DELEGATE_REALM_MAPPER.parseAndSetParameter(value, addRealmMapper, reader);
                        break;
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }

        if (requiredAttributes.isEmpty() == false) {
            throw missingRequired(reader, requiredAttributes);
        }

        addRealmMapper.get(OP_ADDR).set(parentAddress).add(MAPPED_REGEX_REALM_MAPPER, name);
        operations.add(addRealmMapper);

        ModelNode realmNameMap = addRealmMapper.get(REALM_MAP);

        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            verifyNamespace(reader);
            String localName = reader.getLocalName();
            if (REALM_MAPPING.equals(localName) == false) {
                throw unexpectedElement(reader);
            }

            readRealmMapping(realmNameMap, reader);
        }

        if (realmNameMap.isDefined() == false) {
            throw missingRequiredElement(reader, Collections.singleton(REALM_MAPPING));
        }

    }

    private void readRealmMapping(ModelNode realmNameMap, XMLExtendedStreamReader reader) throws XMLStreamException {
        Set<String> requiredAttributes = new HashSet<String>(Arrays.asList(new String[] { FROM, TO }));

        String from = null;
        String to = null;

        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                String attribute = reader.getAttributeLocalName(i);
                requiredAttributes.remove(attribute);
                switch (attribute) {
                    case FROM:
                        from = value;
                        break;
                    case TO:
                        to = value;
                        break;
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }

        if (requiredAttributes.isEmpty() == false) {
            throw missingRequired(reader, requiredAttributes);
        }

        requireNoContent(reader);
        realmNameMap.add(from, to);
    }

    private void readSimpleRoleDecoder(ModelNode parentAddress, XMLExtendedStreamReader reader, List<ModelNode> operations) throws XMLStreamException {
        ModelNode addRoleDecoder = new ModelNode();
        addRoleDecoder.get(OP).set(ADD);

        Set<String> requiredAttributes = new HashSet<String>(Arrays.asList(new String[] { NAME, ATTRIBUTE }));

        String name = null;

        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                String attribute = reader.getAttributeLocalName(i);
                requiredAttributes.remove(attribute);
                switch (attribute) {
                    case NAME:
                        name = value;
                        break;
                    case ATTRIBUTE:
                        RoleDecoderDefinitions.ATTRIBUTE.parseAndSetParameter(value, addRoleDecoder, reader);
                        break;
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }

        if (requiredAttributes.isEmpty() == false) {
            throw missingRequired(reader, requiredAttributes);
        }

        requireNoContent(reader);

        addRoleDecoder.get(OP_ADDR).set(parentAddress).add(SIMPLE_ROLE_DECODER, name);
        operations.add(addRoleDecoder);
    }

    private void readAddPrefixRoleMapper(ModelNode parentAddress, XMLExtendedStreamReader reader, List<ModelNode> operations) throws XMLStreamException {
        ModelNode addRoleMapper = new ModelNode();
        addRoleMapper.get(OP).set(ADD);

        Set<String> requiredAttributes = new HashSet<String>(Arrays.asList(new String[] { NAME, PREFIX }));

        String name = null;

        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                String attribute = reader.getAttributeLocalName(i);
                requiredAttributes.remove(attribute);
                switch (attribute) {
                    case NAME:
                        name = value;
                        break;
                    case PREFIX:
                        RoleMapperDefinitions.PREFIX.parseAndSetParameter(value, addRoleMapper, reader);
                        break;
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }

        if (requiredAttributes.isEmpty() == false) {
            throw missingRequired(reader, requiredAttributes);
        }

        requireNoContent(reader);

        addRoleMapper.get(OP_ADDR).set(parentAddress).add(ADD_PREFIX_ROLE_MAPPER, name);
        operations.add(addRoleMapper);
    }

    private void readAddSuffixRoleMapper(ModelNode parentAddress, XMLExtendedStreamReader reader, List<ModelNode> operations) throws XMLStreamException {
        ModelNode addRoleMapper = new ModelNode();
        addRoleMapper.get(OP).set(ADD);

        Set<String> requiredAttributes = new HashSet<String>(Arrays.asList(new String[] { NAME, SUFFIX }));

        String name = null;

        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                String attribute = reader.getAttributeLocalName(i);
                requiredAttributes.remove(attribute);
                switch (attribute) {
                    case NAME:
                        name = value;
                        break;
                    case SUFFIX:
                        RoleMapperDefinitions.SUFFIX.parseAndSetParameter(value, addRoleMapper, reader);
                        break;
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }

        if (requiredAttributes.isEmpty() == false) {
            throw missingRequired(reader, requiredAttributes);
        }

        requireNoContent(reader);

        addRoleMapper.get(OP_ADDR).set(parentAddress).add(ADD_SUFFIX_ROLE_MAPPER, name);
        operations.add(addRoleMapper);
    }

    private void readAggregateRoleMapperElement(ModelNode parentAddress, XMLExtendedStreamReader reader, List<ModelNode> operations)
            throws XMLStreamException {
        ModelNode addRoleMapper = new ModelNode();
        addRoleMapper.get(OP).set(ADD);

        String name = null;

        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                String attribute = reader.getAttributeLocalName(i);
                switch (attribute) {
                    case NAME:
                        name = value;
                        break;
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }

        if (name == null) {
            throw missingRequired(reader, NAME);
        }

        addRoleMapper.get(OP_ADDR).set(parentAddress).add(AGGREGATE_ROLE_MAPPER, name);

        operations.add(addRoleMapper);

        ListAttributeDefinition roleMappers = RoleMapperDefinitions.getAggregateRoleMapperDefinition().getReferencesAttribute();
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            verifyNamespace(reader);
            String localName = reader.getLocalName();
            if (ROLE_MAPPER.equals(localName) == false) {
                throw unexpectedElement(reader);
            }

            requireSingleAttribute(reader, NAME);
            String roleMapperName = reader.getAttributeValue(0);


            roleMappers.parseAndAddParameterElement(roleMapperName, addRoleMapper, reader);

            requireNoContent(reader);
        }
    }

    private void readConstantRoleMapper(ModelNode parentAddress, XMLExtendedStreamReader reader, List<ModelNode> operations) throws XMLStreamException {
        ModelNode addRoleMapper = new ModelNode();
        addRoleMapper.get(OP).set(ADD);

        Set<String> requiredAttributes = new HashSet<String>(Arrays.asList(new String[] { NAME }));
        Set<String> requiredElements = new HashSet<String>(Arrays.asList(new String[] { ROLE }));

        String name = null;

        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                String attribute = reader.getAttributeLocalName(i);
                requiredAttributes.remove(attribute);
                switch (attribute) {
                    case NAME:
                        name = value;
                        break;
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }

        if (requiredAttributes.isEmpty() == false) {
            throw missingRequired(reader, requiredAttributes);
        }

        addRoleMapper.get(OP_ADDR).set(parentAddress).add(CONSTANT_ROLE_MAPPER, name);

        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            verifyNamespace(reader);
            String localName = reader.getLocalName();
            requiredElements.remove(localName);
            switch (localName) {
                case ROLE:
                    requireSingleAttribute(reader, NAME);
                    String roleName = reader.getAttributeValue(0);
                    RoleMapperDefinitions.ROLES.parseAndAddParameterElement(roleName, addRoleMapper, reader);
                    break;
                default:
                    throw unexpectedElement(reader);
            }
            requireNoContent(reader);
        }

        if ( ! requiredElements.isEmpty()) throw missingRequiredElement(reader, requiredElements);
        operations.add(addRoleMapper);
    }

    private void readLogicalRoleMapper(ModelNode parentAddress, XMLExtendedStreamReader reader, List<ModelNode> operations) throws XMLStreamException {
        ModelNode addRoleMapper = new ModelNode();
        addRoleMapper.get(OP).set(ADD);

        Set<String> requiredAttributes = new HashSet<String>(Arrays.asList(new String[] { NAME, LOGICAL_OPERATION }));

        String name = null;

        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            if (!isNoNamespaceAttribute(reader, i)) {
                throw unexpectedAttribute(reader, i);
            } else {
                String attribute = reader.getAttributeLocalName(i);
                requiredAttributes.remove(attribute);
                switch (attribute) {
                    case NAME:
                        name = value;
                        break;
                    case LOGICAL_OPERATION:
                        RoleMapperDefinitions.LOGICAL_OPERATION.parseAndSetParameter(value, addRoleMapper, reader);
                        break;
                    case LEFT:
                        RoleMapperDefinitions.LEFT.parseAndSetParameter(value, addRoleMapper, reader);
                        break;
                    case RIGHT:
                        RoleMapperDefinitions.RIGHT.parseAndSetParameter(value, addRoleMapper, reader);
                        break;
                    default:
                        throw unexpectedAttribute(reader, i);
                }
            }
        }

        if (requiredAttributes.isEmpty() == false) {
            throw missingRequired(reader, requiredAttributes);
        }

        requireNoContent(reader);

        addRoleMapper.get(OP_ADDR).set(parentAddress).add(LOGICAL_ROLE_MAPPER, name);
        operations.add(addRoleMapper);
    }

    private void startMappers(boolean started, XMLExtendedStreamWriter writer) throws XMLStreamException {
        if (started == false) {
            writer.writeStartElement(MAPPERS);
        }
    }

    private boolean writeCustomPermissionMappers(boolean started, ModelNode subsystem, XMLExtendedStreamWriter writer) throws XMLStreamException {
        if (subsystem.hasDefined(CUSTOM_PERMISSION_MAPPER)) {
            startMappers(started, writer);
            ModelNode principalDecoders = subsystem.require(CUSTOM_PERMISSION_MAPPER);
            for (String name : principalDecoders.keys()) {
                ModelNode principalDecoder = principalDecoders.require(name);

                writeCustomComponent(CUSTOM_PERMISSION_MAPPER, name, principalDecoder, writer);
            }

            return true;
        }

        return false;
    }

    private boolean writeLogicalPermissionMappers(boolean started, ModelNode subsystem, XMLExtendedStreamWriter writer) throws XMLStreamException {
        if (subsystem.hasDefined(LOGICAL_PERMISSION_MAPPER)) {
            startMappers(started, writer);
            ModelNode permissionMappers = subsystem.require(LOGICAL_PERMISSION_MAPPER);
            for (String name : permissionMappers.keys()) {
                ModelNode permissionMapper = permissionMappers.require(name);
                writer.writeStartElement(LOGICAL_PERMISSION_MAPPER);
                writer.writeAttribute(NAME, name);
                PermissionMapperDefinitions.LOGICAL_OPERATION.marshallAsAttribute(permissionMapper, writer);
                PermissionMapperDefinitions.LEFT.marshallAsAttribute(permissionMapper, writer);
                PermissionMapperDefinitions.RIGHT.marshallAsAttribute(permissionMapper, writer);
                writer.writeEndElement();
            }

            return true;
        }

        return false;
    }

    private boolean writeSimplePermissionMappers(boolean started, ModelNode subsystem, XMLExtendedStreamWriter writer) throws XMLStreamException {
        if (subsystem.hasDefined(SIMPLE_PERMISSION_MAPPER)) {
            startMappers(started, writer);
            ModelNode permissionMappers = subsystem.require(SIMPLE_PERMISSION_MAPPER);
            for (String name : permissionMappers.keys()) {
                ModelNode permissionMapper = permissionMappers.require(name);
                writer.writeStartElement(SIMPLE_PERMISSION_MAPPER);
                writer.writeAttribute(NAME, name);
                PermissionMapperDefinitions.MAPPING_MODE.marshallAsAttribute(permissionMapper, false, writer);
                if (permissionMapper.hasDefined(PERMISSION_MAPPINGS)) {
                    for (ModelNode permissionMapping : permissionMapper.get(PERMISSION_MAPPINGS).asList()) {
                        writer.writeStartElement(PERMISSION_MAPPING);
                        PermissionMapperDefinitions.PRINCIPALS.getAttributeMarshaller().marshallAsAttribute(PermissionMapperDefinitions.PRINCIPALS, permissionMapping, false, writer);
                        PermissionMapperDefinitions.ROLES.getAttributeMarshaller().marshallAsAttribute(PermissionMapperDefinitions.ROLES, permissionMapping, false, writer);
                        if (permissionMapping.hasDefined(PERMISSIONS)) {
                            for (ModelNode permission : permissionMapping.get(PERMISSIONS).asList()) {
                                writer.writeStartElement(PERMISSION);
                                ClassLoadingAttributeDefinitions.CLASS_NAME.marshallAsAttribute(permission, writer);
                                ClassLoadingAttributeDefinitions.MODULE.marshallAsAttribute(permission, writer);
                                PermissionMapperDefinitions.TARGET_NAME.marshallAsAttribute(permission, writer);
                                PermissionMapperDefinitions.ACTION.marshallAsAttribute(permission, writer);
                                writer.writeEndElement();
                            }
                        }

                        writer.writeEndElement();
                    }
                }

                writer.writeEndElement();
            }

            return true;
        }

        return false;
    }

    private boolean writeConstantPermissionMappers(boolean started, ModelNode subsystem, XMLExtendedStreamWriter writer) throws XMLStreamException {
        if (subsystem.hasDefined(CONSTANT_PERMISSION_MAPPER)) {
            startMappers(started, writer);
            ModelNode permissionMappers = subsystem.require(CONSTANT_PERMISSION_MAPPER);

            for (String name : permissionMappers.keys()) {
                ModelNode permissionMapper = permissionMappers.require(name);
                writer.writeStartElement(CONSTANT_PERMISSION_MAPPER);
                writer.writeAttribute(NAME, name);

                if (permissionMapper.hasDefined(PERMISSIONS)) {
                    for (ModelNode permission : permissionMapper.get(PERMISSIONS).asList()) {
                        writer.writeStartElement(PERMISSION);
                        ClassLoadingAttributeDefinitions.CLASS_NAME.marshallAsAttribute(permission, writer);
                        ClassLoadingAttributeDefinitions.MODULE.marshallAsAttribute(permission, writer);
                        PermissionMapperDefinitions.TARGET_NAME.marshallAsAttribute(permission, writer);
                        PermissionMapperDefinitions.ACTION.marshallAsAttribute(permission, writer);
                        writer.writeEndElement();
                    }
                }

                writer.writeEndElement();
            }

            return true;
        }

        return false;
    }

    private boolean writeAggregatePrincipalDecoders(boolean started, ModelNode subsystem, XMLExtendedStreamWriter writer) throws XMLStreamException {
        if (subsystem.hasDefined(AGGREGATE_PRINCIPAL_DECODER)) {
            startMappers(started, writer);
            ModelNode principalDecoders = subsystem.require(AGGREGATE_PRINCIPAL_DECODER);
            for (String name : principalDecoders.keys()) {
                ModelNode principalDecoder = principalDecoders.require(name);
                writer.writeStartElement(AGGREGATE_PRINCIPAL_DECODER);
                writer.writeAttribute(NAME, name);

                List<ModelNode> principalDecoderReferences = principalDecoder.get(PRINCIPAL_DECODERS).asList();
                for (ModelNode currentReference : principalDecoderReferences) {
                    writer.writeStartElement(PRINCIPAL_DECODER);
                    writer.writeAttribute(NAME, currentReference.asString());
                    writer.writeEndElement();
                }

                writer.writeEndElement();
            }

            return true;
        }

        return false;
    }

    private boolean writeConcatenatingPrincipalDecoders(boolean started, ModelNode subsystem, XMLExtendedStreamWriter writer) throws XMLStreamException {
        if (subsystem.hasDefined(CONCATENATING_PRINCIPAL_DECODER)) {
            startMappers(started, writer);
            ModelNode principalDecoders = subsystem.require(CONCATENATING_PRINCIPAL_DECODER);
            for (String name : principalDecoders.keys()) {
                ModelNode principalDecoder = principalDecoders.require(name);
                writer.writeStartElement(CONCATENATING_PRINCIPAL_DECODER);
                writer.writeAttribute(NAME, name);
                PrincipalDecoderDefinitions.JOINER.marshallAsAttribute(principalDecoder, writer);

                List<ModelNode> principalDecoderReferences = principalDecoder.get(PRINCIPAL_DECODERS).asList();
                for (ModelNode currentReference : principalDecoderReferences) {
                    writer.writeStartElement(PRINCIPAL_DECODER);
                    writer.writeAttribute(NAME, currentReference.asString());
                    writer.writeEndElement();
                }

                writer.writeEndElement();
            }
            return true;
        }
        return false;
    }

    private boolean writeConstantPrincipalDecoders(boolean started, ModelNode subsystem, XMLExtendedStreamWriter writer) throws XMLStreamException {
        if (subsystem.hasDefined(CONSTANT_PRINCIPAL_DECODER)) {
            startMappers(started, writer);
            ModelNode principalDecoders = subsystem.require(CONSTANT_PRINCIPAL_DECODER);
            for (String name : principalDecoders.keys()) {
                ModelNode principalDecoder = principalDecoders.require(name);
                writer.writeStartElement(CONSTANT_PRINCIPAL_DECODER);
                writer.writeAttribute(NAME, name);
                PrincipalDecoderDefinitions.CONSTANT.marshallAsAttribute(principalDecoder, writer);
                writer.writeEndElement();
            }
            return true;
        }
        return false;
    }

    private boolean writeCustomPrincipalDecoders(boolean started, ModelNode subsystem, XMLExtendedStreamWriter writer) throws XMLStreamException {
        if (subsystem.hasDefined(CUSTOM_PRINCIPAL_DECODER)) {
            startMappers(started, writer);
            ModelNode principalDecoders = subsystem.require(CUSTOM_PRINCIPAL_DECODER);
            for (String name : principalDecoders.keys()) {
                ModelNode principalDecoder = principalDecoders.require(name);

                writeCustomComponent(CUSTOM_PRINCIPAL_DECODER, name, principalDecoder, writer);
            }

            return true;
        }

        return false;
    }

    private boolean writeX500AttributePrincipalDecoders(boolean started, ModelNode subsystem, XMLExtendedStreamWriter writer) throws XMLStreamException {
        if (subsystem.hasDefined(X500_ATTRIBUTE_PRINCIPAL_DECODER)) {
            startMappers(started, writer);
            ModelNode principalDecoders = subsystem.require(X500_ATTRIBUTE_PRINCIPAL_DECODER);
            for (String name : principalDecoders.keys()) {
                ModelNode principalDecoder = principalDecoders.require(name);
                writer.writeStartElement(X500_ATTRIBUTE_PRINCIPAL_DECODER);
                writer.writeAttribute(NAME, name);
                PrincipalDecoderDefinitions.OID.marshallAsAttribute(principalDecoder, writer);
                PrincipalDecoderDefinitions.ATTRIBUTE_NAME.marshallAsAttribute(principalDecoder, writer);
                PrincipalDecoderDefinitions.JOINER.marshallAsAttribute(principalDecoder, writer);
                PrincipalDecoderDefinitions.START_SEGMENT.marshallAsAttribute(principalDecoder, writer);
                PrincipalDecoderDefinitions.MAXIMUM_SEGMENTS.marshallAsAttribute(principalDecoder, writer);
                PrincipalDecoderDefinitions.REVERSE.marshallAsAttribute(principalDecoder, writer);
                PrincipalDecoderDefinitions.REQUIRED_OIDS.getAttributeMarshaller().marshallAsAttribute(PrincipalDecoderDefinitions.REQUIRED_OIDS, principalDecoder, false, writer);
                PrincipalDecoderDefinitions.REQUIRED_ATTRIBUTES.getAttributeMarshaller().marshallAsAttribute(PrincipalDecoderDefinitions.REQUIRED_ATTRIBUTES, principalDecoder, false, writer);
                writer.writeEndElement();
            }

            return true;
        }

        return false;
    }

    private boolean writeAggregatePrincipalTransformers(boolean started, ModelNode subsystem, XMLExtendedStreamWriter writer) throws XMLStreamException {
        if (subsystem.hasDefined(AGGREGATE_PRINCIPAL_TRANSFORMER)) {
            startMappers(started, writer);
            ModelNode principalTransformers = subsystem.require(AGGREGATE_PRINCIPAL_TRANSFORMER);
            for (String name : principalTransformers.keys()) {
                ModelNode principalTransformer = principalTransformers.require(name);
                writer.writeStartElement(AGGREGATE_PRINCIPAL_TRANSFORMER);
                writer.writeAttribute(NAME, name);

                List<ModelNode> principalTransformerReferences = principalTransformer.get(PRINCIPAL_TRANSFORMERS).asList();
                for (ModelNode currentReference : principalTransformerReferences) {
                    writer.writeStartElement(PRINCIPAL_TRANSFORMER);
                    writer.writeAttribute(NAME, currentReference.asString());
                    writer.writeEndElement();
                }

                writer.writeEndElement();
            }

            return true;
        }

        return false;
    }

    private boolean writeChainedPrincipalTransformers(boolean started, ModelNode subsystem, XMLExtendedStreamWriter writer) throws XMLStreamException {
        if (subsystem.hasDefined(CHAINED_PRINCIPAL_TRANSFORMER)) {
            startMappers(started, writer);
            ModelNode principalTransformers = subsystem.require(CHAINED_PRINCIPAL_TRANSFORMER);
            for (String name : principalTransformers.keys()) {
                ModelNode principalTransformer = principalTransformers.require(name);
                writer.writeStartElement(CHAINED_PRINCIPAL_TRANSFORMER);
                writer.writeAttribute(NAME, name);

                List<ModelNode> principalTransformerReferences = principalTransformer.get(PRINCIPAL_TRANSFORMERS).asList();
                for (ModelNode currentReference : principalTransformerReferences) {
                    writer.writeStartElement(PRINCIPAL_TRANSFORMER);
                    writer.writeAttribute(NAME, currentReference.asString());
                    writer.writeEndElement();
                }

                writer.writeEndElement();
            }

            return true;
        }

        return false;
    }

    private boolean writeCustomPrincipalTransformers(boolean started, ModelNode subsystem, XMLExtendedStreamWriter writer) throws XMLStreamException {
        if (subsystem.hasDefined(CUSTOM_PRINCIPAL_TRANSFORMER)) {
            startMappers(started, writer);
            ModelNode transformers = subsystem.require(CUSTOM_PRINCIPAL_TRANSFORMER);
            for (String name : transformers.keys()) {
                ModelNode realm = transformers.require(name);

                writeCustomComponent(CUSTOM_PRINCIPAL_TRANSFORMER, name, realm, writer);
            }

            return true;
        }

        return false;
    }

    private boolean writeConstantPrincipalTransformers(boolean started, ModelNode subsystem, XMLExtendedStreamWriter writer) throws XMLStreamException {
        if (subsystem.hasDefined(CONSTANT_PRINCIPAL_TRANSFORMER)) {
            startMappers(started, writer);
            ModelNode principalTransformers = subsystem.require(CONSTANT_PRINCIPAL_TRANSFORMER);
            for (String name : principalTransformers.keys()) {
                ModelNode principalTransformer = principalTransformers.require(name);
                writer.writeStartElement(CONSTANT_PRINCIPAL_TRANSFORMER);
                writer.writeAttribute(NAME, name);
                PrincipalTransformerDefinitions.CONSTANT.marshallAsAttribute(principalTransformer, writer);
                writer.writeEndElement();
            }

            return true;
        }

        return false;
    }

    private boolean writeRegexPrincipalTransformers(boolean started, ModelNode subsystem, XMLExtendedStreamWriter writer) throws XMLStreamException {
        if (subsystem.hasDefined(REGEX_PRINCIPAL_TRANSFORMER)) {
            startMappers(started, writer);
            ModelNode principalTransformers = subsystem.require(REGEX_PRINCIPAL_TRANSFORMER);
            for (String name : principalTransformers.keys()) {
                ModelNode principalTransformer = principalTransformers.require(name);
                writer.writeStartElement(REGEX_PRINCIPAL_TRANSFORMER);
                writer.writeAttribute(NAME, name);
                RegexAttributeDefinitions.PATTERN.marshallAsAttribute(principalTransformer, writer);
                PrincipalTransformerDefinitions.REPLACEMENT.marshallAsAttribute(principalTransformer, writer);
                PrincipalTransformerDefinitions.REPLACE_ALL.marshallAsAttribute(principalTransformer, writer);
                writer.writeEndElement();
            }

            return true;
        }

        return false;
    }

    private boolean writeRegexValidatingPrincipalTransformer(boolean started, ModelNode subsystem, XMLExtendedStreamWriter writer) throws XMLStreamException {
        if (subsystem.hasDefined(REGEX_VALIDATING_PRINCIPAL_TRANSFORMER)) {
            startMappers(started, writer);
            ModelNode principalTransformers = subsystem.require(REGEX_VALIDATING_PRINCIPAL_TRANSFORMER);
            for (String name : principalTransformers.keys()) {
                ModelNode principalTransformer = principalTransformers.require(name);
                writer.writeStartElement(REGEX_VALIDATING_PRINCIPAL_TRANSFORMER);
                writer.writeAttribute(NAME, name);
                RegexAttributeDefinitions.PATTERN.marshallAsAttribute(principalTransformer, writer);
                PrincipalTransformerDefinitions.MATCH.marshallAsAttribute(principalTransformer, writer);
                writer.writeEndElement();
            }

            return true;
        }

        return false;
    }

    private boolean writeCustomRealmMappers(boolean started, ModelNode subsystem, XMLExtendedStreamWriter writer) throws XMLStreamException {
        if (subsystem.hasDefined(CUSTOM_REALM_MAPPER)) {
            startMappers(started, writer);
            ModelNode realmMappers = subsystem.require(CUSTOM_REALM_MAPPER);
            for (String name : realmMappers.keys()) {
                ModelNode realmMapper = realmMappers.require(name);

                writeCustomComponent(CUSTOM_REALM_MAPPER, name, realmMapper, writer);
            }

            return true;
        }

        return false;
    }

    private boolean writeConstantRealmMappers(boolean started, ModelNode subsystem, XMLExtendedStreamWriter writer) throws XMLStreamException {
        if (subsystem.hasDefined(CONSTANT_REALM_MAPPER)) {
            startMappers(started, writer);
            ModelNode realmMappers = subsystem.require(CONSTANT_REALM_MAPPER);
            for (String name : realmMappers.keys()) {
                ModelNode realmMapper = realmMappers.require(name);
                writer.writeStartElement(CONSTANT_REALM_MAPPER);
                writer.writeAttribute(NAME, name);
                RealmMapperDefinitions.REALM_NAME.marshallAsAttribute(realmMapper, writer);
                writer.writeEndElement();
            }

            return true;
        }

        return false;
    }

    private boolean writeSimpleRegexRealmMappers(boolean started, ModelNode subsystem, XMLExtendedStreamWriter writer) throws XMLStreamException {
        if (subsystem.hasDefined(SIMPLE_REGEX_REALM_MAPPER)) {
            startMappers(started, writer);
            ModelNode realmMappers = subsystem.require(SIMPLE_REGEX_REALM_MAPPER);
            for (String name : realmMappers.keys()) {
                ModelNode realmMapper = realmMappers.require(name);
                writer.writeStartElement(SIMPLE_REGEX_REALM_MAPPER);
                writer.writeAttribute(NAME, name);
                RegexAttributeDefinitions.PATTERN_CAPTURE_GROUP.marshallAsAttribute(realmMapper, writer);
                RealmMapperDefinitions.DELEGATE_REALM_MAPPER.marshallAsAttribute(realmMapper, writer);
                writer.writeEndElement();
            }

            return true;
        }

        return false;
    }

    private boolean writeMapRegexRealmMappers(boolean started, ModelNode subsystem, XMLExtendedStreamWriter writer) throws XMLStreamException {
        if (subsystem.hasDefined(MAPPED_REGEX_REALM_MAPPER)) {
            startMappers(started, writer);
            ModelNode realmMappers = subsystem.require(MAPPED_REGEX_REALM_MAPPER);
            for (String name : realmMappers.keys()) {
                ModelNode realmMapper = realmMappers.require(name);
                writer.writeStartElement(MAPPED_REGEX_REALM_MAPPER);
                writer.writeAttribute(NAME, name);
                RegexAttributeDefinitions.PATTERN_CAPTURE_GROUP.marshallAsAttribute(realmMapper, writer);
                RealmMapperDefinitions.DELEGATE_REALM_MAPPER.marshallAsAttribute(realmMapper, writer);
                RealmMapperDefinitions.REALM_REALM_MAP.marshallAsElement(realmMapper, writer);
                writer.writeEndElement();
            }

            return true;
        }

        return false;
    }

    private boolean writeCustomRoleDecoders(boolean started, ModelNode subsystem, XMLExtendedStreamWriter writer) throws XMLStreamException {
        if (subsystem.hasDefined(CUSTOM_ROLE_DECODER)) {
            startMappers(started, writer);
            ModelNode roleDecoders = subsystem.require(CUSTOM_ROLE_DECODER);
            for (String name : roleDecoders.keys()) {
                ModelNode roleDecoder = roleDecoders.require(name);

                writeCustomComponent(CUSTOM_ROLE_DECODER, name, roleDecoder, writer);
            }

            return true;
        }

        return false;
    }

    private boolean writeSimpleRoleDecoders(boolean started, ModelNode subsystem, XMLExtendedStreamWriter writer) throws XMLStreamException {
        if (subsystem.hasDefined(SIMPLE_ROLE_DECODER)) {
            startMappers(started, writer);
            ModelNode roleDecoders = subsystem.require(SIMPLE_ROLE_DECODER);
            for (String name : roleDecoders.keys()) {
                ModelNode roleDecoder = roleDecoders.require(name);
                writer.writeStartElement(SIMPLE_ROLE_DECODER);
                writer.writeAttribute(NAME, name);
                RoleDecoderDefinitions.ATTRIBUTE.marshallAsAttribute(roleDecoder, writer);
                writer.writeEndElement();
            }

            return true;
        }

        return false;
    }

    private boolean writeAddPrefixRoleMappers(boolean started, ModelNode subsystem, XMLExtendedStreamWriter writer) throws XMLStreamException {
        if (subsystem.hasDefined(ADD_PREFIX_ROLE_MAPPER)) {
            startMappers(started, writer);
            ModelNode roleMappers = subsystem.require(ADD_PREFIX_ROLE_MAPPER);
            for (String name : roleMappers.keys()) {
                ModelNode roleMapper = roleMappers.require(name);
                writer.writeStartElement(ADD_PREFIX_ROLE_MAPPER);
                writer.writeAttribute(NAME, name);
                RoleMapperDefinitions.PREFIX.marshallAsAttribute(roleMapper, writer);
                writer.writeEndElement();
            }

            return true;
        }

        return false;
    }

    private boolean writeAddSuffixRoleMappers(boolean started, ModelNode subsystem, XMLExtendedStreamWriter writer) throws XMLStreamException {
        if (subsystem.hasDefined(ADD_SUFFIX_ROLE_MAPPER)) {
            startMappers(started, writer);
            ModelNode roleMappers = subsystem.require(ADD_SUFFIX_ROLE_MAPPER);
            for (String name : roleMappers.keys()) {
                ModelNode roleMapper = roleMappers.require(name);
                writer.writeStartElement(ADD_SUFFIX_ROLE_MAPPER);
                writer.writeAttribute(NAME, name);
                RoleMapperDefinitions.SUFFIX.marshallAsAttribute(roleMapper, writer);
                writer.writeEndElement();
            }

            return true;
        }

        return false;
    }

    private boolean writeAggregateRoleMappers(boolean started, ModelNode subsystem, XMLExtendedStreamWriter writer) throws XMLStreamException {
        if (subsystem.hasDefined(AGGREGATE_ROLE_MAPPER)) {
            startMappers(started, writer);
            ModelNode roleMappers = subsystem.require(AGGREGATE_ROLE_MAPPER);
            for (String name : roleMappers.keys()) {
                ModelNode roleMapper = roleMappers.require(name);
                writer.writeStartElement(AGGREGATE_ROLE_MAPPER);
                writer.writeAttribute(NAME, name);

                List<ModelNode> roleMapperReferences = roleMapper.get(ROLE_MAPPERS).asList();
                for (ModelNode currentReference : roleMapperReferences) {
                    writer.writeStartElement(ROLE_MAPPER);
                    writer.writeAttribute(NAME, currentReference.asString());
                    writer.writeEndElement();
                }

                writer.writeEndElement();
            }

            return true;
        }

        return false;
    }

    private boolean writeConstantRoleMappers(boolean started, ModelNode subsystem, XMLExtendedStreamWriter writer) throws XMLStreamException {
        if (subsystem.hasDefined(CONSTANT_ROLE_MAPPER)) {
            startMappers(started, writer);
            ModelNode roleMappers = subsystem.require(CONSTANT_ROLE_MAPPER);
            for (String name : roleMappers.keys()) {
                ModelNode roleMapper = roleMappers.require(name);
                writer.writeStartElement(CONSTANT_ROLE_MAPPER);
                writer.writeAttribute(NAME, name);
                for(ModelNode role : roleMapper.require(ROLES).asList()) {
                    writer.writeStartElement(ROLE);
                    writer.writeAttribute(NAME, role.asString());
                    writer.writeEndElement();
                }
                writer.writeEndElement();
            }

            return true;
        }

        return false;
    }

    private boolean writeCustomRoleMappers(boolean started, ModelNode subsystem, XMLExtendedStreamWriter writer) throws XMLStreamException {
        if (subsystem.hasDefined(CUSTOM_ROLE_MAPPER)) {
            startMappers(started, writer);
            ModelNode roleMappers = subsystem.require(CUSTOM_ROLE_MAPPER);
            for (String name : roleMappers.keys()) {
                ModelNode roleMapper = roleMappers.require(name);

                writeCustomComponent(CUSTOM_ROLE_MAPPER, name, roleMapper, writer);
            }

            return true;
        }

        return false;
    }

    private boolean writeLogicalRoleMappers(boolean started, ModelNode subsystem, XMLExtendedStreamWriter writer) throws XMLStreamException {
        if (subsystem.hasDefined(LOGICAL_ROLE_MAPPER)) {
            startMappers(started, writer);
            ModelNode roleMappers = subsystem.require(LOGICAL_ROLE_MAPPER);
            for (String name : roleMappers.keys()) {
                ModelNode roleMapper = roleMappers.require(name);
                writer.writeStartElement(LOGICAL_ROLE_MAPPER);
                writer.writeAttribute(NAME, name);
                RoleMapperDefinitions.LOGICAL_OPERATION.marshallAsAttribute(roleMapper, writer);
                RoleMapperDefinitions.LEFT.marshallAsAttribute(roleMapper, writer);
                RoleMapperDefinitions.RIGHT.marshallAsAttribute(roleMapper, writer);
                writer.writeEndElement();
            }

            return true;
        }

        return false;
    }

    void writeMappers(ModelNode subsystem, XMLExtendedStreamWriter writer) throws XMLStreamException {
        boolean mappersStarted = false;

        mappersStarted = mappersStarted | writeCustomPermissionMappers(mappersStarted, subsystem, writer);
        mappersStarted = mappersStarted | writeLogicalPermissionMappers(mappersStarted, subsystem, writer);
        mappersStarted = mappersStarted | writeSimplePermissionMappers(mappersStarted, subsystem, writer);
        mappersStarted = mappersStarted | writeConstantPermissionMappers(mappersStarted, subsystem, writer);

        mappersStarted = mappersStarted | writeAggregatePrincipalDecoders(mappersStarted, subsystem, writer);
        mappersStarted = mappersStarted | writeConcatenatingPrincipalDecoders(mappersStarted, subsystem, writer);
        mappersStarted = mappersStarted | writeConstantPrincipalDecoders(mappersStarted, subsystem, writer);
        mappersStarted = mappersStarted | writeCustomPrincipalDecoders(mappersStarted, subsystem, writer);
        mappersStarted = mappersStarted | writeX500AttributePrincipalDecoders(mappersStarted, subsystem, writer);

        mappersStarted = mappersStarted | writeAggregatePrincipalTransformers(mappersStarted, subsystem, writer);
        mappersStarted = mappersStarted | writeChainedPrincipalTransformers(mappersStarted, subsystem, writer);
        mappersStarted = mappersStarted | writeConstantPrincipalTransformers(mappersStarted, subsystem, writer);
        mappersStarted = mappersStarted | writeCustomPrincipalTransformers(mappersStarted, subsystem, writer);
        mappersStarted = mappersStarted | writeRegexPrincipalTransformers(mappersStarted, subsystem, writer);
        mappersStarted = mappersStarted | writeRegexValidatingPrincipalTransformer(mappersStarted, subsystem, writer);

        mappersStarted = mappersStarted | writeConstantRealmMappers(mappersStarted, subsystem, writer);
        mappersStarted = mappersStarted | writeCustomRealmMappers(mappersStarted, subsystem, writer);
        mappersStarted = mappersStarted | writeSimpleRegexRealmMappers(mappersStarted, subsystem, writer);
        mappersStarted = mappersStarted | writeMapRegexRealmMappers(mappersStarted, subsystem, writer);

        mappersStarted = mappersStarted | writeCustomRoleDecoders(mappersStarted, subsystem, writer);
        mappersStarted = mappersStarted | writeSimpleRoleDecoders(mappersStarted, subsystem, writer);

        mappersStarted = mappersStarted | writeAddPrefixRoleMappers(mappersStarted, subsystem, writer);
        mappersStarted = mappersStarted | writeAddSuffixRoleMappers(mappersStarted, subsystem, writer);
        mappersStarted = mappersStarted | writeAggregateRoleMappers(mappersStarted, subsystem, writer);
        mappersStarted = mappersStarted | writeConstantRoleMappers(mappersStarted, subsystem, writer);
        mappersStarted = mappersStarted | writeCustomRoleMappers(mappersStarted, subsystem, writer);
        mappersStarted = mappersStarted | writeLogicalRoleMappers(mappersStarted, subsystem, writer);


        if (mappersStarted) {
            writer.writeEndElement();
        }
    }
}
