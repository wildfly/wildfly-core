/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.metadata;

import static org.jboss.as.controller.parsing.ParseUtils.readStringAttributeElement;
import static org.jboss.as.controller.parsing.ParseUtils.requireNoContent;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedAttribute;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedElement;
import static org.jboss.as.patching.HashUtils.bytesToHexString;
import static org.jboss.as.patching.HashUtils.hexStringToByteArray;
import static org.jboss.as.patching.IoUtils.NO_CONTENT;
import static org.jboss.as.patching.metadata.ModuleItem.MAIN_SLOT;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.patching.PatchingException;
import org.jboss.as.patching.installation.InstalledIdentity;
import org.jboss.as.patching.metadata.impl.PatchElementImpl;
import org.jboss.as.patching.metadata.impl.PatchElementProviderImpl;
import org.jboss.as.patching.metadata.impl.RequiresCallback;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;

/**
 * @author Emanuel Muckenhuber
 */
class PatchXmlUtils implements XMLStreamConstants {

    private static final String PATH_DELIMITER = "/";

    enum Element {

        ADDED("added"),
        BUNDLES("bundles"),
        DESCRIPTION("description"),
        IDENTITY("identity"),
        REQUIRES("requires"),
        LINK("link"),
        MISC_FILES("misc-files"),
        MODULES("modules"),
        NO_UPGRADE("no-upgrade"),
        ONE_OFF("one-off"),
        PATCH("patch"),
        PATCH_ELEMENT("element"),
        REMOVED("removed"),
        UPDATED("updated"),
        UPGRADE("upgrade"),

        // default unknown element
        UNKNOWN(null),
        ;

        public final String name;
        Element(String name) {
            this.name = name;
        }

        static Map<String, Element> elements = new HashMap<String, Element>();
        static {
            for(Element element : Element.values()) {
                if(element != UNKNOWN) {
                    elements.put(element.name, element);
                }
            }
        }

        static Element forName(String name) {
            final Element element = elements.get(name);
            return element == null ? UNKNOWN : element;
        }

    }

    enum Attribute {

        ADD_ON("add-on"),
        DIRECTORY("directory"),
        EXISTING_PATH("existing-path"),
        HASH("hash"),
        ID("id"),
        IN_RUNTIME_USE("in-runtime-use"),
        NAME("name"),
        NEW_HASH("new-hash"),
        PATH("path"),
        CONDITION("condition"),
        SLOT("slot"),
        TO_VERSION("to-version"),
        URL("url"),
        VERSION("version"),


        // default unknown attribute
        UNKNOWN(null);

        private final String name;
        Attribute(String name) {
            this.name = name;
        }

        static Map<String, Attribute> attributes = new HashMap<String, Attribute>();
        static {
            for(Attribute attribute : Attribute.values()) {
                if(attribute != UNKNOWN) {
                    attributes.put(attribute.name, attribute);
                }
            }
        }

        static Attribute forName(String name) {
            final Attribute attribute = attributes.get(name);
            return attribute == null ? UNKNOWN : attribute;
        }
    }

    protected static void writePatch(final XMLExtendedStreamWriter writer, final Patch patch) throws XMLStreamException {

        // id
        writer.writeAttribute(Attribute.ID.name, patch.getPatchId());

        // Description
        String description = patch.getDescription();
        if (description != null) {
            writer.writeStartElement(Element.DESCRIPTION.name);
            writer.writeCharacters(description);
            writer.writeEndElement(); // description
        }

        String link = patch.getLink();
        if (link != null) {
            writer.writeEmptyElement(Element.LINK.name);
            writer.writeAttribute(Attribute.URL.name, link);
        }

        // identity
        final Identity identity = patch.getIdentity();
        final Patch.PatchType type = identity.getPatchType();
        if (type == Patch.PatchType.CUMULATIVE) {
            writer.writeStartElement(Element.UPGRADE.name);
        } else {
            writer.writeStartElement(Element.NO_UPGRADE.name);
        }

        writer.writeAttribute(Attribute.NAME.name, identity.getName());
        writer.writeAttribute(Attribute.VERSION.name, identity.getVersion());

        // upgrade / no-upgrade
        if(type == Patch.PatchType.CUMULATIVE) {
            final Identity.IdentityUpgrade upgrade = identity.forType(Patch.PatchType.CUMULATIVE, Identity.IdentityUpgrade.class);
            writer.writeAttribute(Attribute.TO_VERSION.name, upgrade.getResultingVersion());
        }

        if(!identity.getRequires().isEmpty()) {
            writer.writeStartElement(Element.REQUIRES.name);
            for(String patchId : identity.getRequires()) {
                writer.writeStartElement(Element.PATCH.name);
                writer.writeAttribute(Attribute.ID.name, patchId);
                writer.writeEndElement(); // patch
            }
            writer.writeEndElement(); // includes
        }
        writer.writeEndElement(); // identity

        // elements
        final List<PatchElement> elements = patch.getElements();
        for(PatchElement element : elements) {

            writer.writeStartElement(Element.PATCH_ELEMENT.name);
            writer.writeAttribute(Attribute.ID.name, element.getId());

            if(element.getDescription() != null) {
                writer.writeStartElement(Element.DESCRIPTION.name);
                writer.writeCharacters(element.getDescription());
                writer.writeEndElement(); // description
            }

            // layer / add-on
            final PatchElementProvider provider = element.getProvider();
            assert provider != null;

            // identity
            final Patch.PatchType elementPatchType =  provider.getPatchType();
            if (elementPatchType == Patch.PatchType.CUMULATIVE) {
                writer.writeStartElement(Element.UPGRADE.name);
            } else {
                writer.writeStartElement(Element.NO_UPGRADE.name);
            }

            writer.writeAttribute(Attribute.NAME.name, provider.getName());
            if (provider.isAddOn()) {
                writer.writeAttribute(Attribute.ADD_ON.name, "true");
            }

            if(!provider.getRequires().isEmpty()) {
                writer.writeStartElement(Element.REQUIRES.name);
                for(String elementId : provider.getRequires()) {
                    writer.writeStartElement(Element.PATCH.name);
                    writer.writeAttribute(Attribute.ID.name, elementId);
                    writer.writeEndElement(); // element
                }
                writer.writeEndElement(); // includes
            }
            writer.writeEndElement(); // add-on / layer

            // Write the content modifications
            writeContentModifications(writer, element.getModifications());

            writer.writeEndElement(); // element
        }

        // Write the identity modifications directory for some tests
        writeContentModifications(writer, patch.getModifications());

    }

    protected static void writeContentModifications(final XMLExtendedStreamWriter writer, final Collection<ContentModification> modifications) throws XMLStreamException {
        // Sort by content and modification type
        final List<ContentModification> bundlesAdd  = new ArrayList<ContentModification>();
        final List<ContentModification> bundlesUpdate  = new ArrayList<ContentModification>();
        final List<ContentModification> bundlesRemove  = new ArrayList<ContentModification>();

        final List<ContentModification> miscAdd = new ArrayList<ContentModification>();
        final List<ContentModification> miscUpdate = new ArrayList<ContentModification>();
        final List<ContentModification> miscRemove = new ArrayList<ContentModification>();

        final List<ContentModification> modulesAdd = new ArrayList<ContentModification>();
        final List<ContentModification> modulesUpdate = new ArrayList<ContentModification>();
        final List<ContentModification> modulesRemove = new ArrayList<ContentModification>();

        for(final ContentModification mod : modifications) {
            final ModificationType modificationType = mod.getType();
            final ContentType contentType = mod.getItem().getContentType();
            switch (contentType) {
                case BUNDLE:
                    switch (modificationType) {
                        case ADD:
                            bundlesAdd.add(mod);
                            break;
                        case MODIFY:
                            bundlesUpdate.add(mod);
                            break;
                        case REMOVE:
                            bundlesRemove.add(mod);
                            break;
                    }
                    break;
                case MODULE:
                    switch (modificationType) {
                        case ADD:
                            modulesAdd.add(mod);
                            break;
                        case MODIFY:
                            modulesUpdate.add(mod);
                            break;
                        case REMOVE:
                            modulesRemove.add(mod);
                            break;
                    }
                    break;
                case MISC:
                    switch (modificationType) {
                        case ADD:
                            miscAdd.add(mod);
                            break;
                        case MODIFY:
                            miscUpdate.add(mod);
                            break;
                        case REMOVE:
                            miscRemove.add(mod);
                            break;
                    }
                    break;
            }
        }

        // Modules
        if (!modulesAdd.isEmpty() ||
                !modulesUpdate.isEmpty() ||
                !modulesRemove.isEmpty()) {
            writer.writeStartElement(Element.MODULES.name);
            writeSlottedItems(writer, Element.ADDED, modulesAdd);
            writeSlottedItems(writer, Element.UPDATED, modulesUpdate);
            writeSlottedItems(writer, Element.REMOVED, modulesRemove);
            writer.writeEndElement(); // modules
        }

        // Bundles
        if (!bundlesAdd.isEmpty() ||
                !bundlesUpdate.isEmpty() ||
                !bundlesRemove.isEmpty()) {
            writer.writeStartElement(Element.BUNDLES.name);
            writeSlottedItems(writer, Element.ADDED, bundlesAdd);
            writeSlottedItems(writer, Element.UPDATED, bundlesUpdate);
            writeSlottedItems(writer, Element.REMOVED, bundlesRemove);
            writer.writeEndElement(); // bundles
        }

        // Misc
        if (!miscAdd.isEmpty() ||
                !miscUpdate.isEmpty() ||
                !miscRemove.isEmpty()) {
            writer.writeStartElement(Element.MISC_FILES.name);
            writeMiscItems(writer, Element.ADDED, miscAdd);
            writeMiscItems(writer, Element.UPDATED, miscUpdate);
            writeMiscItems(writer, Element.REMOVED, miscRemove);
            writer.writeEndElement(); // misc-files
        }
    }

    protected void doReadElement(final XMLExtendedStreamReader reader, final PatchBuilder builder, InstalledIdentity originalIdentity) throws XMLStreamException {

        final PatchBuilder patch = builder;
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
            if(Attribute.ID == attribute) {
                patch.setPatchId(value);
            } else {
                throw unexpectedAttribute(reader, i);
            }
        }

        final Collection<ContentModification> modifications = patch.getModifications();
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            final String localName = reader.getLocalName();
            final Element element = Element.forName(localName);
            switch (element) {
                case DESCRIPTION:
                    patch.setDescription(reader.getElementText());
                    break;
                case LINK:
                    final String link = readStringAttributeElement(reader, Attribute.URL.name);
                    builder.setLink(link);
                    break;
                case UPGRADE:
                    parseIdentity(reader, patch, Patch.PatchType.CUMULATIVE);
                    break;
                case NO_UPGRADE:
                    parseIdentity(reader, patch, Patch.PatchType.ONE_OFF);
                    break;
                case PATCH_ELEMENT:
                    parseElement(reader, patch);
                    break;
                case MODULES:
                    parseModules(reader, modifications);
                    break;
                case BUNDLES:
                    parseBundles(reader, modifications);
                    break;
                case MISC_FILES:
                    parseMiscFiles(reader, modifications);
                    break;
                default:
                    handleRootElement(localName, reader, patch, originalIdentity);
            }
        }
    }

    protected void handleRootElement(final String localName, final XMLExtendedStreamReader reader, final PatchBuilder builder, InstalledIdentity originalIdentity) throws XMLStreamException {
        throw unexpectedElement(reader);
    }

    static void parseElement(final XMLExtendedStreamReader reader, final PatchBuilder builder) throws XMLStreamException {

        String id = null;
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
            if (Attribute.ID == attribute) {
                id = value;
            } else {
                throw unexpectedAttribute(reader, i);
            }
        }

        final PatchElementImpl patchElement = new PatchElementImpl(id);
        try {
            builder.addElement(patchElement);
        } catch(IllegalStateException e) {
            throw new XMLStreamException(e);
        }
        final List<ContentModification> modifications = patchElement.getModifications();
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case DESCRIPTION:
                    patchElement.setDescription(reader.getElementText());
                    break;
                case UPGRADE:
                    parseElementProvider(reader, patchElement, Patch.PatchType.CUMULATIVE);
                    break;
                case NO_UPGRADE:
                    parseElementProvider(reader, patchElement, Patch.PatchType.ONE_OFF);
                    break;
                case MODULES:
                    parseModules(reader, modifications);
                    break;
                case BUNDLES:
                    parseBundles(reader, modifications);
                    break;
                case MISC_FILES:
                    parseMiscFiles(reader, modifications);
                    break;
                default:
                    throw unexpectedElement(reader);
            }
        }
    }

    static void parseElementProvider(final XMLExtendedStreamReader reader, PatchElementImpl patchElement, Patch.PatchType patchType) throws XMLStreamException {

        String name = null;
        boolean isAddOn = false;
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
            if (Attribute.NAME == attribute) {
                name = reader.getAttributeValue(i);
            } else if (Attribute.ADD_ON == attribute) {
                isAddOn = Boolean.valueOf(reader.getAttributeValue(i));
            } else {
                throw unexpectedAttribute(reader, i);
            }
        }

        final PatchElementProviderImpl provider = new PatchElementProviderImpl(name, isAddOn);
        patchElement.setProvider(provider);
        switch (patchType) {
            case CUMULATIVE:
                provider.upgrade();
                break;
            case ONE_OFF:
                provider.oneOffPatch();
                break;
            default:
                throw new IllegalStateException();
        }

        int level = 0;
        while (reader.hasNext()) {
            if(reader.nextTag() == END_ELEMENT) {
                if(level == 0) {
                    break;
                } else {
                    --level;
                }
            }
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case REQUIRES:
                    break;
                case PATCH:
                    level = 1;
                    parseIncluded(reader, provider);
                    break;
                default:
                    throw unexpectedElement(reader);
            }
        }
    }

    static void parseIdentity(final XMLExtendedStreamReader reader, final PatchBuilder builder, final Patch.PatchType patchType) throws XMLStreamException {

        String name = null;
        String version = null;
        String resultingVersion = null;
        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
            if (Attribute.VERSION == attribute) {
                version = reader.getAttributeValue(i);
            } else if (Attribute.TO_VERSION == attribute) {
                resultingVersion = reader.getAttributeValue(i);
            } else if (Attribute.NAME == attribute) {
                name = reader.getAttributeValue(i);
            } else {
                throw unexpectedAttribute(reader, i);
            }
        }

        final PatchIdentityBuilder identityBuilder;
        switch (patchType) {
            case CUMULATIVE:
                identityBuilder = builder.upgradeIdentity(name, version, resultingVersion);
                break;
            case ONE_OFF:
                identityBuilder = builder.oneOffPatchIdentity(name, version);
                break;
            default:
                throw new IllegalStateException();
        }

        int level = 0;
        while (reader.hasNext()) {
            if(reader.nextTag() == END_ELEMENT) {
                if(level == 0) {
                    break;
                } else {
                    --level;
                }
            }

            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case REQUIRES:
                    break;
                case PATCH:
                    level = 1;
                    parseIncluded(reader, identityBuilder);
                    break;
                default:
                    throw unexpectedElement(reader);
            }
        }
    }

    static void parseIncluded(final XMLExtendedStreamReader reader, final RequiresCallback includes) throws XMLStreamException {

        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final String value = reader.getAttributeValue(i);
            final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
            if(Attribute.ID == attribute) {
                includes.require(value);
            } else {
                throw unexpectedAttribute(reader, i);
            }
        }
        requireNoContent(reader);
    }

    static void parseModules(final XMLExtendedStreamReader reader, final Collection<ContentModification> modifications) throws XMLStreamException {
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case ADDED:
                    modifications.add(parseModuleModification(reader, ModificationType.ADD));
                    break;
                case UPDATED:
                    modifications.add(parseModuleModification(reader, ModificationType.MODIFY));
                    break;
                case REMOVED:
                    modifications.add(parseModuleModification(reader, ModificationType.REMOVE));
                    break;
                default:
                    throw unexpectedElement(reader);
            }
        }
    }

    static void parseMiscFiles(final XMLExtendedStreamReader reader, final Collection<ContentModification> modifications) throws XMLStreamException {
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case ADDED:
                    modifications.add(parseMiscModification(reader, ModificationType.ADD));
                    break;
                case UPDATED:
                    modifications.add(parseMiscModification(reader, ModificationType.MODIFY));
                    break;
                case REMOVED:
                    modifications.add(parseMiscModification(reader, ModificationType.REMOVE));
                    break;
                default:
                    throw unexpectedElement(reader);
            }
        }
    }

    static void parseBundles(final XMLExtendedStreamReader reader, final Collection<ContentModification> modifications) throws XMLStreamException {
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            final Element element = Element.forName(reader.getLocalName());
            switch (element) {
                case ADDED:
                    modifications.add(parseBundleModification(reader, ModificationType.ADD));
                    break;
                case UPDATED:
                    modifications.add(parseBundleModification(reader, ModificationType.MODIFY));
                    break;
                case REMOVED:
                    modifications.add(parseBundleModification(reader, ModificationType.REMOVE));
                    break;
                default:
                    throw unexpectedElement(reader);
            }
        }
    }

    static ContentModification parseBundleModification(final XMLExtendedStreamReader reader, final ModificationType type) throws XMLStreamException {
        return parseSlottedItem(reader, type, ContentType.BUNDLE);
    }

    static ContentModification parseModuleModification(final XMLExtendedStreamReader reader, final ModificationType type) throws XMLStreamException {
        return parseSlottedItem(reader, type, ContentType.MODULE);
    }

    static ContentModification parseSlottedItem(final XMLExtendedStreamReader reader, ModificationType modificationType, ContentType contentType) throws XMLStreamException {

        String moduleName = null;
        String slot = "main";
        byte[] hash = NO_CONTENT;
        byte[] targetHash = NO_CONTENT;

        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
            switch (attribute) {
                case NAME:
                    moduleName = reader.getAttributeValue(i);
                    break;
                case SLOT:
                    slot = reader.getAttributeValue(i);
                    break;
                case HASH:
                    if (modificationType == ModificationType.REMOVE) {
                        targetHash = hexStringToByteArray(reader.getAttributeValue(i));
                    } else {
                        hash = hexStringToByteArray(reader.getAttributeValue(i));
                    }
                    break;
                case NEW_HASH:
                    if (modificationType == ModificationType.REMOVE) {
                        hash = hexStringToByteArray(reader.getAttributeValue(i));
                    } else {
                        targetHash = hexStringToByteArray(reader.getAttributeValue(i));
                    }
                    break;
                default:
                    throw unexpectedAttribute(reader, i);
            }
        }
        requireNoContent(reader);

        final ModuleItem item = contentType == ContentType.MODULE ? new ModuleItem(moduleName, slot, hash) : new BundleItem(moduleName, slot, hash);
        return new ContentModification(item, targetHash, modificationType);
    }

    static ContentModification parseMiscModification(final XMLExtendedStreamReader reader, ModificationType type) throws XMLStreamException {

        String path = null;
        byte[] hash = NO_CONTENT;
        boolean directory = false;
        boolean affectsRuntime = false;
        byte[] targetHash = NO_CONTENT;
        ModificationCondition condition = null;

        final int count = reader.getAttributeCount();
        for (int i = 0; i < count; i++) {
            final Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
            switch (attribute) {
                case DIRECTORY:
                    directory = Boolean.parseBoolean(reader.getAttributeValue(i));
                    break;
                case PATH:
                    path = reader.getAttributeValue(i);
                    break;
                case HASH:
                    if (type == ModificationType.REMOVE) {
                        targetHash = hexStringToByteArray(reader.getAttributeValue(i));
                    } else {
                        hash = hexStringToByteArray(reader.getAttributeValue(i));
                    }
                    break;
                case NEW_HASH:
                    if (type == ModificationType.REMOVE) {
                        hash = hexStringToByteArray(reader.getAttributeValue(i));
                    } else {
                        targetHash = hexStringToByteArray(reader.getAttributeValue(i));
                    }
                    break;
                case IN_RUNTIME_USE:
                    affectsRuntime = Boolean.parseBoolean(reader.getAttributeValue(i));
                    break;
                case CONDITION:
                    try {
                        condition = ModificationCondition.Factory.fromString(reader.getAttributeValue(i));
                    } catch (PatchingException e) {
                        throw ControllerLogger.ROOT_LOGGER.invalidAttributeValue(reader.getAttributeValue(i), new QName(
                                attribute.name), reader.getLocation());
                    }
                    break;
                default:
                    throw unexpectedAttribute(reader, i);
            }
        }
        requireNoContent(reader);

        final MiscContentItem item = createMiscItem(path, hash, directory, affectsRuntime);
        return new ContentModification(item, targetHash, type, condition);
    }

    private static MiscContentItem createMiscItem(String path, byte[] hash, boolean directory, boolean affectsRuntime) {
        final String[] s = path.split(PATH_DELIMITER);
        final int length = s.length;
        final String name = s[length - 1];
        final String[] itemPath = Arrays.copyOf(s, length - 1);
        return new MiscContentItem(name, itemPath, hash, directory, affectsRuntime);
    }

    protected static void writeAppliesToVersions(XMLExtendedStreamWriter writer, List<String> appliesTo) throws XMLStreamException {
        for (String version : appliesTo) {
//            writer.writeStartElement(Element.APPLIES_TO_VERSION.name);
            writer.writeCharacters(version);
            writer.writeEndElement();
        }
    }

    protected static void writeSlottedItems(final XMLExtendedStreamWriter writer, final Element element, final List<ContentModification> modifications) throws XMLStreamException {
        for(final ContentModification modification : modifications) {
            writeSlottedItem(writer, element, modification);
        }
    }

    protected static void writeSlottedItem(final XMLExtendedStreamWriter writer, Element element, ContentModification modification) throws XMLStreamException {

        writer.writeEmptyElement(element.name);

        final ModuleItem item = (ModuleItem) modification.getItem();
        final ModificationType type = modification.getType();

        writer.writeAttribute(Attribute.NAME.name, item.getName());
        if (!MAIN_SLOT.equals(item.getSlot())) {
            writer.writeAttribute(Attribute.SLOT.name, item.getSlot());
        }
        byte[] hash = item.getContentHash();
        if (hash.length > 0 && type != ModificationType.REMOVE) {
            writer.writeAttribute(Attribute.HASH.name,  bytesToHexString(hash));
        }
        if(type == ModificationType.REMOVE) {
            final byte[] existingHash = modification.getTargetHash();
            if (existingHash.length > 0) {
                writer.writeAttribute(Attribute.HASH.name, bytesToHexString(existingHash));
            }
        } else if(type == ModificationType.MODIFY) {
            final byte[] existingHash = modification.getTargetHash();
            if (existingHash.length > 0) {
                writer.writeAttribute(Attribute.NEW_HASH.name, bytesToHexString(existingHash));
            }
        }
    }

    protected static void writeMiscItems(final XMLExtendedStreamWriter writer, final Element element, final List<ContentModification> modifications) throws XMLStreamException {
        for(final ContentModification modification : modifications) {
            writeMiscItem(writer, element, modification);
        }
    }

    protected static void writeMiscItem(final XMLExtendedStreamWriter writer, final Element element, final ContentModification modification) throws XMLStreamException {

        writer.writeEmptyElement(element.name);

        final MiscContentItem item = (MiscContentItem) modification.getItem();
        final ModificationType type = modification.getType();

        final StringBuilder path = new StringBuilder();
        for(final String p : item.getPath()) {
            path.append(p).append(PATH_DELIMITER);
        }
        path.append(item.getName());

        writer.writeAttribute(Attribute.PATH.name, path.toString());
        if (item.isDirectory()) {
            writer.writeAttribute(Attribute.DIRECTORY.name, "true");
        }

        final ModificationCondition condition = modification.getCondition();
        if(condition != null) {
            writer.writeAttribute(Attribute.CONDITION.name, condition.toString());
        }

        if(type == ModificationType.REMOVE) {
            final byte[] existingHash = modification.getTargetHash();
            if (existingHash.length > 0) {
                writer.writeAttribute(Attribute.HASH.name, bytesToHexString(existingHash));
            }
            if(item.isAffectsRuntime()) {
                writer.writeAttribute(Attribute.IN_RUNTIME_USE.name, "true");
            }
        } else {
            byte[] hash = item.getContentHash();
            if(hash.length > 0) {
                writer.writeAttribute(Attribute.HASH.name, bytesToHexString(hash));
            }

            if(type == ModificationType.MODIFY) {
                writer.writeAttribute(Attribute.NEW_HASH.name, bytesToHexString(modification.getTargetHash()));
                if (item.isAffectsRuntime()) {
                    writer.writeAttribute(Attribute.IN_RUNTIME_USE.name, "true");
                }
            }
        }
    }



}
