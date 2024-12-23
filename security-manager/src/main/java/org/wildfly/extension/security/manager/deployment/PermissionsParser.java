/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.security.manager.deployment;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.jboss.modules.ModuleIdentifier;
import org.jboss.modules.ModuleLoader;
import org.jboss.modules.security.PermissionFactory;
import org.wildfly.extension.security.manager.DeferredPermissionFactory;
import org.wildfly.extension.security.manager.logging.SecurityManagerLogger;

/**
 * This class implements a parser for the {@code permissions.xml} and {@code jboss-permissions.xml} descriptors. The
 * parsed permissions are returned as a collection of {@code PermissionFactory} objects.
 *
 * @author <a href="mailto:sguilhen@redhat.com">Stefan Guilhen</a>
 */
public class PermissionsParser {

    // TODO remove this as soon as the full WF testsuite use of it is updated to use the string variant
    /**
     * @deprecated use {@link #parse(XMLStreamReader, ModuleLoader, String)}
     */
    @Deprecated(forRemoval = true)
    public static List<PermissionFactory> parse(final XMLStreamReader reader, final ModuleLoader loader, final ModuleIdentifier identifier)
            throws XMLStreamException {
        return parse(reader,loader, identifier.toString());
    }

    /**
     * Parse the contents exposed by a reader into a list of {@link PermissionFactory} instances.
     * @param reader reader of a {@code permissions.xml} or {@code jboss-permissions.xml} descriptor. Cannot be {@code null}.
     * @param loader loader to use for loading permission classes. Cannot be {@code null}.
     * @param moduleName canonical name of the module to use for loading permission classes. Cannot be {@code null}.
     * @return a list of {@link PermissionFactory} instances
     * @throws XMLStreamException if a parsing error occurs
     */
    public static List<PermissionFactory> parse(final XMLStreamReader reader, final ModuleLoader loader, final String moduleName)
            throws XMLStreamException {

        reader.require(XMLStreamConstants.START_DOCUMENT, null, null);

        while (reader.hasNext()) {
            switch (reader.nextTag()) {
                case XMLStreamConstants.START_ELEMENT: {
                    Element element = Element.forName(reader.getLocalName());
                    switch (element) {
                        case PERMISSIONS: {
                            return parsePermissions(reader, loader, moduleName);
                        }
                        default: {
                            throw unexpectedElement(reader);
                        }
                    }
                }
                default: {
                    throw unexpectedContent(reader);
                }
            }
        }
        throw unexpectedEndOfDocument(reader);
    }

    private static List<PermissionFactory> parsePermissions(final XMLStreamReader reader, final ModuleLoader loader, final String moduleName)
            throws XMLStreamException {

        List<PermissionFactory> factories = new ArrayList<PermissionFactory>();

        // parse the permissions attributes.
        EnumSet<Attribute> requiredAttributes = EnumSet.of(Attribute.VERSION);
        for (int i = 0; i < reader.getAttributeCount(); i++) {
            final String attributeNamespace = reader.getAttributeNamespace(i);
            if (attributeNamespace != null && !attributeNamespace.isEmpty()) {
                continue;
            }
            Attribute attribute = Attribute.forName(reader.getAttributeLocalName(i));
            switch (attribute) {
                case VERSION: {
                    String version = reader.getAttributeValue(i);
                    if (!"7".equals(version) && !"9".equals(version) && !"10".equals(version))
                        throw SecurityManagerLogger.ROOT_LOGGER.invalidPermissionsXMLVersion(version, "7");
                    break;
                }
                default: {
                    throw unexpectedAttribute(reader, i);
                }
            }
            requiredAttributes.remove(attribute);
        }

        // check if all required attributes were parsed.
        if (!requiredAttributes.isEmpty())
            throw missingRequiredAttributes(reader, requiredAttributes);

        // parse the permissions sub-elements.
        while (reader.hasNext()) {
            switch (reader.nextTag()) {
                case XMLStreamConstants.END_ELEMENT: {
                    return factories;
                }
                case XMLStreamConstants.START_ELEMENT: {
                    Element element = Element.forName(reader.getLocalName());
                    switch (element) {
                        case PERMISSION: {
                            PermissionFactory factory = parsePermission(reader, loader, moduleName);
                            factories.add(factory);
                            break;
                        }
                        default: {
                            throw unexpectedElement(reader);
                        }
                    }
                    break;
                }
                default: {
                    throw unexpectedContent(reader);
                }
            }
        }
        throw unexpectedEndOfDocument(reader);
    }

    private static PermissionFactory parsePermission(final XMLStreamReader reader, final ModuleLoader loader, final String moduleName)
            throws XMLStreamException {

        // permission element has no attributes.
        requireNoAttributes(reader);

        String permissionClass = null;
        String permissionName = null;
        String permissionActions = null;

        EnumSet<Element> requiredElements = EnumSet.of(Element.CLASS_NAME);
        while (reader.hasNext()) {
            switch (reader.nextTag()) {
                case XMLStreamConstants.END_ELEMENT: {
                    // check if all required permission elements have been processed.
                    if (!requiredElements.isEmpty())
                        throw missingRequiredElement(reader, requiredElements);

                    // build a permission and add it to the list.
                    PermissionFactory factory = new DeferredPermissionFactory(DeferredPermissionFactory.Type.DEPLOYMENT,
                            loader, moduleName, permissionClass, permissionName, permissionActions);
                    return factory;
                }
                case XMLStreamConstants.START_ELEMENT: {
                    Element element = Element.forName(reader.getLocalName());
                    requiredElements.remove(element);
                    switch (element) {
                        case CLASS_NAME: {
                            requireNoAttributes(reader);
                            permissionClass = reader.getElementText();
                            break;
                        }
                        case NAME: {
                            requireNoAttributes(reader);
                            permissionName = reader.getElementText();
                            break;
                        }
                        case ACTIONS: {
                            requireNoAttributes(reader);
                            permissionActions = reader.getElementText();
                            break;
                        }
                        default: {
                            throw unexpectedElement(reader);
                        }
                    }
                    break;
                }
                default: {
                    throw unexpectedContent(reader);
                }
            }
        }
        throw unexpectedEndOfDocument(reader);
    }

    private static XMLStreamException unexpectedContent(final XMLStreamReader reader) {
        final String kind;
        switch (reader.getEventType()) {
            case XMLStreamConstants.ATTRIBUTE:
                kind = "attribute";
                break;
            case XMLStreamConstants.CDATA:
                kind = "cdata";
                break;
            case XMLStreamConstants.CHARACTERS:
                kind = "characters";
                break;
            case XMLStreamConstants.COMMENT:
                kind = "comment";
                break;
            case XMLStreamConstants.DTD:
                kind = "dtd";
                break;
            case XMLStreamConstants.END_DOCUMENT:
                kind = "document end";
                break;
            case XMLStreamConstants.END_ELEMENT:
                kind = "element end";
                break;
            case XMLStreamConstants.ENTITY_DECLARATION:
                kind = "entity declaration";
                break;
            case XMLStreamConstants.ENTITY_REFERENCE:
                kind = "entity ref";
                break;
            case XMLStreamConstants.NAMESPACE:
                kind = "namespace";
                break;
            case XMLStreamConstants.NOTATION_DECLARATION:
                kind = "notation declaration";
                break;
            case XMLStreamConstants.PROCESSING_INSTRUCTION:
                kind = "processing instruction";
                break;
            case XMLStreamConstants.SPACE:
                kind = "whitespace";
                break;
            case XMLStreamConstants.START_DOCUMENT:
                kind = "document start";
                break;
            case XMLStreamConstants.START_ELEMENT:
                kind = "element start";
                break;
            default:
                kind = "unknown";
                break;
        }
        return SecurityManagerLogger.ROOT_LOGGER.unexpectedContentType(kind, reader.getLocation());
    }

    /**
     * Gets an exception reporting an unexpected end of XML document.
     *
     * @param reader a reference to the stream reader.
     * @return the constructed {@link javax.xml.stream.XMLStreamException}.
     */
    private static XMLStreamException unexpectedEndOfDocument(final XMLStreamReader reader) {
        return SecurityManagerLogger.ROOT_LOGGER.unexpectedEndOfDocument(reader.getLocation());
    }

    /**
     * Gets an exception reporting an unexpected XML element.
     *
     * @param reader a reference to the stream reader.
     * @return the constructed {@link javax.xml.stream.XMLStreamException}.
     */
    private static XMLStreamException unexpectedElement(final XMLStreamReader reader) {
        return SecurityManagerLogger.ROOT_LOGGER.unexpectedElement(reader.getName(), reader.getLocation());
    }

    /**
     * Gets an exception reporting an unexpected XML attribute.
     *
     * @param reader a reference to the stream reader.
     * @param index the attribute index.
     * @return the constructed {@link javax.xml.stream.XMLStreamException}.
     */
    private static XMLStreamException unexpectedAttribute(final XMLStreamReader reader, final int index) {
        return SecurityManagerLogger.ROOT_LOGGER.unexpectedAttribute(reader.getAttributeName(index), reader.getLocation());
    }

    /**
     * Checks that the current element has no attributes, throwing an {@link javax.xml.stream.XMLStreamException} if one is found.
     *
     * @param reader a reference to the stream reader.
     * @throws {@link javax.xml.stream.XMLStreamException} if an error occurs.
     */
    private static void requireNoAttributes(final XMLStreamReader reader) throws XMLStreamException {
        if (reader.getAttributeCount() > 0) {
            throw unexpectedAttribute(reader, 0);
        }
    }

    /**
     * Gets an exception reporting missing required XML attribute(s).
     *
     * @param reader a reference to the stream reader
     * @param required a set of enums whose toString method returns the attribute name.
     * @return the constructed {@link javax.xml.stream.XMLStreamException}.
     */
    private static XMLStreamException missingRequiredAttributes(final XMLStreamReader reader, final Set<?> required) {
        final StringBuilder builder = new StringBuilder();
        Iterator<?> iterator = required.iterator();
        while (iterator.hasNext()) {
            final Object o = iterator.next();
            builder.append(o.toString());
            if (iterator.hasNext()) {
                builder.append(", ");
            }
        }
        return SecurityManagerLogger.ROOT_LOGGER.missingRequiredAttributes(builder, reader.getLocation());
    }

    /**
     * Get an exception reporting missing required XML element(s).
     *
     * @param reader a reference to the stream reader.
     * @param required a set of enums whose toString method returns the element name.
     * @return the constructed {@link javax.xml.stream.XMLStreamException}.
     */
    private static XMLStreamException missingRequiredElement(final XMLStreamReader reader, final Set<?> required) {
        final StringBuilder builder = new StringBuilder();
        Iterator<?> iterator = required.iterator();
        while (iterator.hasNext()) {
            final Object o = iterator.next();
            builder.append(o.toString());
            if (iterator.hasNext()) {
                builder.append(", ");
            }
        }
        return SecurityManagerLogger.ROOT_LOGGER.missingRequiredElements(builder, reader.getLocation());
    }


    /**
     * <p>
     * Enumeration of the persistence.xml configuration elements.
     * </p>
     *
     * @author <a href="mailto:sguilhen@redhat.com">Stefan Guilhen</a>
     */
    enum Element {

        UNKNOWN(null),

        PERMISSIONS("permissions"),
        PERMISSION("permission"),
        CLASS_NAME("class-name"),
        NAME("name"),
        ACTIONS("actions");


        // elements used to configure the ORB.

        private final String name;

        /**
         * <p>
         * {@code Element} constructor. Sets the element name.
         * </p>
         *
         * @param name a {@code String} representing the local name of the element.
         */
        Element(final String name) {
            this.name = name;
        }

        /**
         * <p>
         * Obtains the local name of this element.
         * </p>
         *
         * @return a {@code String} representing the element's local name.
         */
        public String getLocalName() {
            return name;
        }

        // a map that caches all available elements by name.
        private static final Map<String, Element> MAP;

        static {
            final Map<String, Element> map = new HashMap<String, Element>();
            for (Element element : values()) {
                final String name = element.getLocalName();
                if (name != null)
                    map.put(name, element);
            }
            MAP = map;
        }


        /**
         * <p>
         * Gets the {@code Element} identified by the specified name.
         * </p>
         *
         * @param localName a {@code String} representing the local name of the element.
         * @return the {@code Element} identified by the name. If no attribute can be found, the {@code Element.UNKNOWN}
         *         type is returned.
         */
        public static Element forName(String localName) {
            final Element element = MAP.get(localName);
            return element == null ? UNKNOWN : element;
        }

    }

    enum Attribute {
        UNKNOWN(null),
        VERSION("version");

        private final String name;

        /**
         * <p>
         * {@code Attribute} constructor. Sets the attribute name.
         * </p>
         *
         * @param name a {@code String} representing the local name of the attribute.
         */
        Attribute(final String name) {
            this.name = name;
        }

        /**
         * <p>
         * Obtains the local name of this attribute.
         * </p>
         *
         * @return a {@code String} representing the attribute local name.
         */
        public String getLocalName() {
            return this.name;
        }

        // a map that caches all available attributes by name.
        private static final Map<String, Attribute> MAP;

        static {
            final Map<String, Attribute> map = new HashMap<String, Attribute>();
            for (Attribute attribute : values()) {
                final String name = attribute.name;
                if (name != null)
                    map.put(name, attribute);
            }
            MAP = map;
        }

        /**
         * <p>
         * Gets the {@code Attribute} identified by the specified name.
         * </p>
         *
         * @param localName a {@code String} representing the local name of the attribute.
         * @return the {@code Attribute} identified by the name. If no attribute can be found, the {@code Attribute.UNKNOWN}
         *         type is returned.
         */
        public static Attribute forName(String localName) {
            final Attribute attribute = MAP.get(localName);
            return attribute == null ? UNKNOWN : attribute;
        }
    }
}
