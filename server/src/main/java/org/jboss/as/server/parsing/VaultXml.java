/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
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

package org.jboss.as.server.parsing;

import static javax.xml.stream.XMLStreamConstants.END_ELEMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VAULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.VAULT_OPTIONS;
import static org.jboss.as.controller.parsing.ParseUtils.missingRequired;
import static org.jboss.as.controller.parsing.ParseUtils.requireNamespace;
import static org.jboss.as.controller.parsing.ParseUtils.requireNoContent;
import static org.jboss.as.controller.parsing.ParseUtils.requireNoNamespaceAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedElement;

import java.util.EnumSet;
import java.util.List;

import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.parsing.Attribute;
import org.jboss.as.controller.parsing.Element;
import org.jboss.as.controller.parsing.Namespace;
import org.jboss.as.server.controller.resources.VaultResourceDefinition;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;

/**
 * Parsing and marshalling logic specific to vault definitions.
 *
 * The contents of this file have been pulled from {@see CommonXml}, see the commit history of that file for true author
 * attribution.
 *
 * Note: This class is only indented to support versions 1, 2, and 3 of the schema, if later major versions of the schema
 * include updates to the types represented by this class then this class should be forked.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class VaultXml {

    void parseVault(final XMLExtendedStreamReader reader, final ModelNode address, final Namespace expectedNs, final List<ModelNode> list) throws XMLStreamException {
        switch (expectedNs) {
            case DOMAIN_1_0:
                throw unexpectedElement(reader);
            case DOMAIN_1_1:
            case DOMAIN_1_2:
            case DOMAIN_1_3:
            case DOMAIN_1_4:
            case DOMAIN_1_5:
                parseVault_1_1(reader, address, expectedNs, list);
                break;
            default:
                if (expectedNs.getMajorVersion() == 2) {
                    parseVault_1_1(reader, address, expectedNs, list);
                } else {
                    parseVault_1_6_and_3_0(reader, address, expectedNs, list);
                }
        }
    }

    private void parseVault_1_1(final XMLExtendedStreamReader reader, final ModelNode address, final Namespace expectedNs, final List<ModelNode> list) throws XMLStreamException {
        final int vaultAttribCount = reader.getAttributeCount();

        ModelNode vault = new ModelNode();
        String code = null;

        for (int i = 0; i < vaultAttribCount; i++) {
            requireNoNamespaceAttribute(reader, i);
            final String value = reader.getAttributeValue(i);
            final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
            switch (attribute) {
                case CODE: {
                    VaultResourceDefinition.CODE.parseAndSetParameter(value, vault, reader);
                    break;
                }
                default:
                    throw unexpectedAttribute(reader, i);
            }
        }

        ModelNode vaultAddress = address.clone();
        vaultAddress.add(CORE_SERVICE, VAULT);
        if (code != null) {
            vault.get(Attribute.CODE.getLocalName()).set(code);
        }
        vault.get(OP_ADDR).set(vaultAddress);
        vault.get(OP).set(ADD);

        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, expectedNs);
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case VAULT_OPTION: {
                    parseModuleOption(reader, vault.get(VAULT_OPTIONS));
                    break;
                }
            }
        }
        list.add(vault);
    }

    private void parseVault_1_6_and_3_0(final XMLExtendedStreamReader reader, final ModelNode address, final Namespace expectedNs, final List<ModelNode> list) throws XMLStreamException {
        final int vaultAttribCount = reader.getAttributeCount();

        ModelNode vault = new ModelNode();
        String code = null;

        for (int i = 0; i < vaultAttribCount; i++) {
            requireNoNamespaceAttribute(reader, i);
            final String value = reader.getAttributeValue(i);
            final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
            switch (attribute) {
                case CODE: {
                    VaultResourceDefinition.CODE.parseAndSetParameter(value, vault, reader);
                    break;
                }
                case MODULE: {
                    VaultResourceDefinition.MODULE.parseAndSetParameter(value, vault, reader);
                    break;
                }
                default:
                    throw unexpectedAttribute(reader, i);
            }
        }

        ModelNode vaultAddress = address.clone();
        vaultAddress.add(CORE_SERVICE, VAULT);
        if (code != null) {
            vault.get(Attribute.CODE.getLocalName()).set(code);
        }
        vault.get(OP_ADDR).set(vaultAddress);
        vault.get(OP).set(ADD);

        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            requireNamespace(reader, expectedNs);
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case VAULT_OPTION: {
                    parseModuleOption(reader, vault.get(VAULT_OPTIONS));
                    break;
                }
            }
        }
        list.add(vault);
    }

    private void parseVaultOption(XMLExtendedStreamReader reader, ModelNode vaultOptions) throws XMLStreamException {
        String name = null;
        String val = null;
        EnumSet<Attribute> required = EnumSet.of(Attribute.NAME, Attribute.VALUE);
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            requireNoNamespaceAttribute(reader, i);
            final String value = reader.getAttributeValue(i);
            final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
            required.remove(attribute);
            switch (attribute) {
                case NAME: {
                    name = value;
                    break;
                }
                case VALUE: {
                    val = value;
                    break;
                }
                default:
                    throw unexpectedAttribute(reader, i);
            }
        }

        if (required.size() > 0) {
            throw missingRequired(reader, required);
        }

        vaultOptions.get(name).set(val);
        requireNoContent(reader);
    }

    private void parseModuleOption(XMLExtendedStreamReader reader, ModelNode moduleOptions) throws XMLStreamException {
        String name = null;
        String val = null;
        EnumSet<Attribute> required = EnumSet.of(Attribute.NAME, Attribute.VALUE);
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            requireNoNamespaceAttribute(reader, i);
            final String value = reader.getAttributeValue(i);
            final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
            required.remove(attribute);
            switch (attribute) {
                case NAME: {
                    name = value;
                    break;
                }
                case VALUE: {
                    val = value;
                    break;
                }
                default:
                    throw unexpectedAttribute(reader, i);
            }
        }

        if (required.size() > 0) {
            throw missingRequired(reader, required);
        }

        moduleOptions.add(name, val);
        requireNoContent(reader);
    }

    void writeVault(XMLExtendedStreamWriter writer, ModelNode vault) throws XMLStreamException {
        writer.writeStartElement(Element.VAULT.getLocalName());
        VaultResourceDefinition.CODE.marshallAsAttribute(vault, writer);
        VaultResourceDefinition.MODULE.marshallAsAttribute(vault, writer);

        if (vault.hasDefined(VAULT_OPTIONS)) {
            ModelNode properties = vault.get(VAULT_OPTIONS);
            for (Property prop : properties.asPropertyList()) {
                writer.writeEmptyElement(Element.VAULT_OPTION.getLocalName());
                writer.writeAttribute(Attribute.NAME.getLocalName(), prop.getName());
                writer.writeAttribute(Attribute.VALUE.getLocalName(), prop.getValue().asString());
            }
        }
        writer.writeEndElement();
    }

}
