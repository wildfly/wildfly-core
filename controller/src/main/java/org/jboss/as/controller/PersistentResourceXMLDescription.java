package org.jboss.as.controller;

import static javax.xml.stream.XMLStreamConstants.END_ELEMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADDRESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.parsing.Element;
import org.jboss.as.controller.parsing.ParseUtils;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;

/**
 * A representation of a resource as needed by the XML parser.
 *
 * @author Tomaz Cerar
 * @author Stuart Douglas
 */
public final class PersistentResourceXMLDescription {

    protected final PathElement pathElement;
    private final String xmlElementName;
    private final String xmlWrapperElement;
    private final LinkedHashMap<String, LinkedHashMap<String, AttributeDefinition>> attributesByGroup;
    protected final List<PersistentResourceXMLDescription> children;
    private final Map<String, AttributeDefinition> attributeElements = new HashMap<>();
    private final boolean useValueAsElementName;
    private final boolean noAddOperation;
    private final AdditionalOperationsGenerator additionalOperationsGenerator;
    private boolean flushRequired = true;
    private boolean childAlreadyRead = false;
    private final Map<String, AttributeParser> attributeParsers;
    private final Map<String, AttributeMarshaller> attributeMarshallers;
    private final boolean useElementsForGroups;
    private final String namespaceURI;
    private final Set<String> attributeGroups;
    private final String forcedName;
    private final boolean marshallDefaultValues;
    //name of the attribute that is used for wildcard elements
    private final String nameAttributeName;


    private PersistentResourceXMLDescription(PersistentResourceXMLBuilder builder) {
        this.pathElement = builder.pathElement;
        this.xmlElementName = builder.xmlElementName;
        this.xmlWrapperElement = builder.xmlWrapperElement;
        this.useElementsForGroups = builder.useElementsForGroups;
        this.attributesByGroup = new LinkedHashMap<>();
        this.namespaceURI = builder.namespaceURI;
        this.attributeGroups = new HashSet<>();
        if (useElementsForGroups) {
            // Ensure we have a map for the default group even if there are no attributes so we don't NPE later
            this.attributesByGroup.put(null, new LinkedHashMap<>());
            // Segregate attributes by group
            for (AttributeDefinition ad : builder.attributeList) {

                LinkedHashMap<String, AttributeDefinition> forGroup = this.attributesByGroup.get(ad.getAttributeGroup());
                if (forGroup == null) {
                    forGroup = new LinkedHashMap<>();
                    this.attributesByGroup.put(ad.getAttributeGroup(), forGroup);
                    this.attributeGroups.add(ad.getAttributeGroup());
                }
                forGroup.put(ad.getXmlName(), ad);
                AttributeParser ap = builder.attributeParsers.getOrDefault(ad.getXmlName(), ad.getParser());
                if (ap != null && ap.isParseAsElement()) {
                    attributeElements.put(ap.getXmlName(ad), ad);
                }

            }
        } else {
            LinkedHashMap<String, AttributeDefinition> attrs = new LinkedHashMap<>();
            for (AttributeDefinition ad : builder.attributeList) {
                attrs.put(ad.getXmlName(), ad);

                if (ad.getParser() != null && ad.getParser().isParseAsElement()) {
                    attributeElements.put(ad.getParser().getXmlName(ad), ad);
                }
            }
            // Ignore attribute-group, treat all as if they are in the default group
            this.attributesByGroup.put(null, attrs);
        }
        this.children = new ArrayList<>();
        for (PersistentResourceXMLBuilder b : builder.childrenBuilders) {
            this.children.add(b.build());
        }
        builder.children.forEach(this.children::add);
        this.useValueAsElementName = builder.useValueAsElementName;
        this.noAddOperation = builder.noAddOperation;
        this.additionalOperationsGenerator = builder.additionalOperationsGenerator;
        this.attributeParsers = builder.attributeParsers;
        this.attributeMarshallers = builder.attributeMarshallers;
        this.forcedName = builder.forcedName;
        this.marshallDefaultValues = builder.marshallDefaultValues;
        this.nameAttributeName = builder.nameAttributeName;
    }

    public PathElement getPathElement() {
        return this.pathElement;
    }

    public void parse(final XMLExtendedStreamReader reader, PathAddress parentAddress, List<ModelNode> list) throws XMLStreamException {
        ModelNode op = Util.createAddOperation();
        boolean wildcard = getPathElement().isWildcard();
        String name = parseAttributeGroups(reader, op, wildcard);
        if (wildcard && name == null) {
            if (forcedName != null) {
                name = forcedName;
            } else {
                throw ControllerLogger.ROOT_LOGGER.missingRequiredAttributes(new StringBuilder(NAME), reader.getLocation());
            }
        }
        PathElement path = wildcard ? PathElement.pathElement(getPathElement().getKey(), name) : getPathElement();
        PathAddress address = parentAddress.append(path);
        if (!noAddOperation) {
            op.get(ADDRESS).set(address.toModelNode());
            list.add(op);
        }
        if (additionalOperationsGenerator != null) {
            additionalOperationsGenerator.additionalOperations(address, op, list);
        }
        if (!reader.isEndElement()) { //only parse children if we are not on end of tag already
            parseChildren(reader, address, list, op);
        }
    }

    private String parseAttributeGroups(final XMLExtendedStreamReader reader, ModelNode op, boolean wildcard) throws XMLStreamException {
        String name = parseAttributes(reader, op, attributesByGroup.get(null), wildcard); //parse attributes not belonging to a group
        if (!attributeGroups.isEmpty()) {
            while (reader.hasNext() && reader.nextTag() != XMLStreamConstants.END_ELEMENT) {
                boolean element = attributeElements.containsKey(reader.getLocalName());
                //it can be a group or element attribute
                if (attributeGroups.contains(reader.getLocalName()) || element) {
                    if (element) {
                        AttributeDefinition ad = attributeElements.get(reader.getLocalName());
                        ad.getParser().parseElement(ad, reader, op);
                        if (attributeGroups.contains(reader.getLocalName())) {
                            parseGroup(reader, op, wildcard);
                        } else if (reader.isEndElement() && !attributeGroups.contains(reader.getLocalName()) && !attributeElements.containsKey(reader.getLocalName())) {
                            childAlreadyRead = true;
                            break;
                        }
                    } else {
                        parseGroup(reader, op, wildcard);
                    }

                } else {
                    //don't break, as we read all attributes, we set that child was already read so readChildren wont do .nextTag()
                    childAlreadyRead = true;
                    return name;
                }
            }
            flushRequired = false;
        }
        return name;
    }

    private void parseGroup(XMLExtendedStreamReader reader, ModelNode op, boolean wildcard) throws XMLStreamException {
        Map<String, AttributeDefinition> groupAttrs = attributesByGroup.get(reader.getLocalName());
        for (AttributeDefinition attrGroup : groupAttrs.values()) {
            if (op.hasDefined(attrGroup.getName())) {
                throw ParseUtils.unexpectedElement(reader);
            }
        }
        parseAttributes(reader, op, groupAttrs, wildcard);
        // Check if there are also element attributes inside a group
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            String attrName = reader.getLocalName();
            if (groupAttrs.containsKey(attrName) && groupAttrs.containsKey(attrName)) {
                AttributeDefinition ad = groupAttrs.get(reader.getLocalName());
                ad.getParser().parseElement(ad, reader, op);
            } else {
                throw ParseUtils.unexpectedElement(reader);
            }
        }
    }

    private String parseAttributes(final XMLExtendedStreamReader reader, ModelNode op, Map<String, AttributeDefinition> attributes, boolean wildcard) throws XMLStreamException {
        String name = null;
        for (int i = 0; i < reader.getAttributeCount(); i++) {
            String attributeName = reader.getAttributeLocalName(i);
            String value = reader.getAttributeValue(i);
            if (wildcard && nameAttributeName.equals(attributeName)) {
                name = value;
            } else if (attributes.containsKey(attributeName)) {
                AttributeDefinition def = attributes.get(attributeName);
                AttributeParser parser = attributeParsers.getOrDefault(attributeName, def.getParser());
                assert parser != null;
                parser.parseAndSetParameter(def, value, op, reader);
            } else {
                Set<String> possible = new LinkedHashSet<>(attributes.keySet());
                possible.add(nameAttributeName);
                throw ParseUtils.unexpectedAttribute(reader, i, possible);
            }
        }
        //only parse attribute elements here if there are no attribute groups defined
        if (attributeGroups.isEmpty() && !attributeElements.isEmpty() && reader.isStartElement()) {
            String originalStartElement = reader.getLocalName();
            if (reader.hasNext() && reader.nextTag() != XMLStreamConstants.END_ELEMENT) {
                do {
                    if (attributeElements.containsKey(reader.getLocalName())) {
                        AttributeDefinition ad = attributeElements.get(reader.getLocalName());
                        AttributeParser parser = attributeParsers.getOrDefault(ad.getXmlName(), ad.getParser());
                        parser.parseElement(ad, reader, op);
                    } else {
                        return name;  //this means we only have children left, return so child handling logic can take over
                    }
                    childAlreadyRead = true;
                } while (!reader.getLocalName().equals(originalStartElement) && reader.hasNext() && reader.nextTag() != XMLStreamConstants.END_ELEMENT);
            }
        }


        return name;
    }

    private Map<String, PersistentResourceXMLDescription> getChildrenMap() {
        Map<String, PersistentResourceXMLDescription> res = new HashMap<>();
        for (PersistentResourceXMLDescription child : children) {
            if (child.xmlWrapperElement != null) {
                res.put(child.xmlWrapperElement, child);
            } else {
                res.put(child.xmlElementName, child);
            }
        }
        return res;
    }

    private void parseChildren(final XMLExtendedStreamReader reader, PathAddress parentAddress, List<ModelNode> list, ModelNode op) throws XMLStreamException {
        if (children.size() == 0) {
            if (flushRequired && attributeGroups.isEmpty() && attributeElements.isEmpty()) {
                ParseUtils.requireNoContent(reader);
            }
            if (childAlreadyRead) {
                throw ParseUtils.unexpectedElement(reader);
            }
        } else {
            Map<String, PersistentResourceXMLDescription> children = getChildrenMap();
            if (childAlreadyRead || (reader.hasNext() && reader.nextTag() != XMLStreamConstants.END_ELEMENT)) {
                do {
                    PersistentResourceXMLDescription child = children.get(reader.getLocalName());
                    if (child != null) {
                        if (child.xmlWrapperElement != null) {
                            if (reader.getLocalName().equals(child.xmlWrapperElement)) {
                                if (reader.hasNext() && reader.nextTag() == END_ELEMENT) { return; }
                            } else {
                                throw ParseUtils.unexpectedElement(reader);
                            }
                            child.parse(reader, parentAddress, list);
                            while (reader.nextTag() != END_ELEMENT && !reader.getLocalName().equals(child.xmlWrapperElement)) {
                                child.parse(reader, parentAddress, list);
                            }
                        } else {
                            child.parse(reader, parentAddress, list);
                        }

                    } else if (attributeElements.containsKey(reader.getLocalName())) {
                        AttributeDefinition ad = attributeElements.get(reader.getLocalName());
                        ad.getParser().parseElement(ad, reader, op);
                    } else {
                        throw ParseUtils.unexpectedElement(reader, children.keySet());
                    }
                } while (reader.hasNext() && reader.nextTag() != XMLStreamConstants.END_ELEMENT);
            }
        }
    }


    public void persist(XMLExtendedStreamWriter writer, ModelNode model) throws XMLStreamException {
        persist(writer, model, namespaceURI);
    }

    private void writeStartElement(XMLExtendedStreamWriter writer, String namespaceURI, String localName) throws XMLStreamException {
        if (namespaceURI != null) {
            writer.writeStartElement(namespaceURI, localName);
        } else {
            writer.writeStartElement(localName);
        }
    }

    private void startSubsystemElement(XMLExtendedStreamWriter writer, String namespaceURI, boolean empty) throws XMLStreamException {
        if (writer.getNamespaceContext().getPrefix(namespaceURI) == null) {
            // Unknown namespace; it becomes default
            writer.setDefaultNamespace(namespaceURI);
            if (empty) {
                writer.writeEmptyElement(Element.SUBSYSTEM.getLocalName());
            } else {
                writer.writeStartElement(Element.SUBSYSTEM.getLocalName());
            }
            writer.writeNamespace(null, namespaceURI);
        } else {
            if (empty) {
                writer.writeEmptyElement(namespaceURI, Element.SUBSYSTEM.getLocalName());
            } else {
                writer.writeStartElement(namespaceURI, Element.SUBSYSTEM.getLocalName());
            }
        }

    }

    public void persist(XMLExtendedStreamWriter writer, ModelNode model, String namespaceURI) throws XMLStreamException {
        boolean wildcard = getPathElement().isWildcard();
        model = wildcard ? model.get(getPathElement().getKey()) : model.get(getPathElement().getKeyValuePair());
        boolean isSubsystem = getPathElement().getKey().equals(ModelDescriptionConstants.SUBSYSTEM);
        if (!isSubsystem && !model.isDefined() && !useValueAsElementName) {
            return;
        }

        boolean writeWrapper = xmlWrapperElement != null;
        if (writeWrapper) {
            writeStartElement(writer, namespaceURI, xmlWrapperElement);
        }

        if (wildcard) {
            for (String name : model.keys()) {
                ModelNode subModel = model.get(name);
                if (useValueAsElementName) {
                    writeStartElement(writer, namespaceURI, name);
                } else {
                    writeStartElement(writer, namespaceURI, xmlElementName);
                    writer.writeAttribute(nameAttributeName, name);
                }
                persistAttributes(writer, subModel);
                persistChildren(writer, subModel);
                writer.writeEndElement();
            }
        } else {
            final boolean empty = attributeGroups.isEmpty() && children.isEmpty();
            if (useValueAsElementName) {
                writeStartElement(writer, namespaceURI, getPathElement().getValue());
            } else if (isSubsystem) {
                startSubsystemElement(writer, namespaceURI, empty);
            } else {
                writeStartElement(writer, namespaceURI, xmlElementName);
            }

            persistAttributes(writer, model);
            persistChildren(writer, model);

            // Do not attempt to write end element if the <subsystem/> has no elements!
            if (!isSubsystem || !empty) {
                writer.writeEndElement();
            }
        }

        if (writeWrapper) {
            writer.writeEndElement();
        }
    }

    private void persistAttributes(XMLExtendedStreamWriter writer, ModelNode model) throws XMLStreamException {
        marshallAttributes(writer, model, attributesByGroup.get(null).values(), null);
        if (useElementsForGroups) {
            for (Map.Entry<String, LinkedHashMap<String, AttributeDefinition>> entry : attributesByGroup.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                marshallAttributes(writer, model, entry.getValue().values(), entry.getKey());
            }
        }
    }

    private void marshallAttributes(XMLExtendedStreamWriter writer, ModelNode model, Collection<AttributeDefinition> attributes, String group) throws XMLStreamException {
        boolean started = false;

        //we sort attributes to make sure that attributes that marshall to elements are last
        List<AttributeDefinition> sortedAds = new ArrayList<>(attributes.size());
        List<AttributeDefinition> elementAds = null;
        for (AttributeDefinition ad : attributes) {
            if (ad.getParser().isParseAsElement()) {
                if (elementAds == null) {
                    elementAds = new ArrayList<>();
                }
                elementAds.add(ad);
            } else {
                sortedAds.add(ad);
            }
        }
        if (elementAds != null) {
            sortedAds.addAll(elementAds);
        }

        for (AttributeDefinition ad : sortedAds) {
            AttributeMarshaller marshaller = attributeMarshallers.getOrDefault(ad.getXmlName(), ad.getAttributeMarshaller());
            if (marshaller.isMarshallable(ad, model, marshallDefaultValues)) {
                if (!started && group != null) {
                    if (elementAds != null) {
                        writer.writeStartElement(group);
                    } else {
                        writer.writeEmptyElement(group);
                    }
                    started = true;
                }
                marshaller.marshall(ad, model, marshallDefaultValues, writer);
            }

        }
        if (elementAds != null && started) {
            writer.writeEndElement();
        }
    }

    public void persistChildren(XMLExtendedStreamWriter writer, ModelNode model) throws XMLStreamException {
        for (PersistentResourceXMLDescription child : children) {
            child.persist(writer, model);
        }
    }

    /**
     * @param resource resource for which path we are creating builder
     * @return PersistentResourceXMLBuilder
     * @deprecated please use {@linkplain PersistentResourceXMLBuilder(PathElement, String)} variant
     */
    @SuppressWarnings("deprecation")
    @Deprecated
    public static PersistentResourceXMLBuilder builder(PersistentResourceDefinition resource) {
        return new PersistentResourceXMLBuilder(resource.getPathElement());
    }

    /**
     * @param resource resource for which path we are creating builder
     * @return PersistentResourceXMLBuilder
     * @deprecated please use {@linkplain PersistentResourceXMLBuilder(PathElement, String)} variant
     */
    @SuppressWarnings("deprecation")
    @Deprecated
    public static PersistentResourceXMLBuilder builder(ResourceDefinition resource) {
        return new PersistentResourceXMLBuilder(resource.getPathElement());
    }

    /**
     *
     * @param resource resource for which path we are creating builder
     * @param namespaceURI xml namespace to use for this resource, usually used for top level elements such as subsystems
     * @return PersistentResourceXMLBuilder
     * @deprecated please use {@linkplain PersistentResourceXMLBuilder(PathElement, String)} variant
     */
    @SuppressWarnings("deprecation")
    @Deprecated
    public static PersistentResourceXMLBuilder builder(PersistentResourceDefinition resource, String namespaceURI) {
        return new PersistentResourceXMLBuilder(resource.getPathElement(), namespaceURI);
    }

    /**
     * Creates builder for passed path element
     * @param pathElement for which we are creating builder
     * @return PersistentResourceXMLBuilder
     */
    public static PersistentResourceXMLBuilder builder(final PathElement pathElement) {
        return new PersistentResourceXMLBuilder(pathElement);
    }

    /**
     * Creates builder for passed path element
     *
     * @param pathElement for which we are creating builder
     * @param namespaceURI xml namespace to use for this resource, usually used for top level elements such as subsystems
     * @return PersistentResourceXMLBuilder
     */
    public static PersistentResourceXMLBuilder builder(final PathElement pathElement, final String namespaceURI) {
        return new PersistentResourceXMLBuilder(pathElement, namespaceURI);
    }

    public static final class PersistentResourceXMLBuilder {
        protected final PathElement pathElement;
        private final String namespaceURI;
        private String xmlElementName;
        private String xmlWrapperElement;
        private boolean useValueAsElementName;
        private boolean noAddOperation;
        private AdditionalOperationsGenerator additionalOperationsGenerator;
        private final LinkedList<AttributeDefinition> attributeList = new LinkedList<>();
        private final List<PersistentResourceXMLBuilder> childrenBuilders = new ArrayList<>();
        private final List<PersistentResourceXMLDescription> children = new ArrayList<>();
        private final LinkedHashMap<String, AttributeParser> attributeParsers = new LinkedHashMap<>();
        private final LinkedHashMap<String, AttributeMarshaller> attributeMarshallers = new LinkedHashMap<>();
        private boolean useElementsForGroups = true;
        private String forcedName;
        private boolean marshallDefaultValues = false;
        private String nameAttributeName = NAME;

        private PersistentResourceXMLBuilder(final PathElement pathElement) {
            this.pathElement = pathElement;
            this.namespaceURI = null;
            this.xmlElementName = pathElement.isWildcard() ? pathElement.getKey() : pathElement.getValue();
        }

        private PersistentResourceXMLBuilder(final PathElement pathElement, String namespaceURI) {
            this.pathElement = pathElement;
            this.namespaceURI = namespaceURI;
            this.xmlElementName = pathElement.isWildcard() ? pathElement.getKey() : pathElement.getValue();
        }

        public PersistentResourceXMLBuilder addChild(PersistentResourceXMLBuilder builder) {
            this.childrenBuilders.add(builder);
            return this;
        }

        public PersistentResourceXMLBuilder addChild(PersistentResourceXMLDescription builder) {
            this.children.add(builder);
            return this;
        }

        public PersistentResourceXMLBuilder addAttribute(AttributeDefinition attribute) {
            this.attributeList.add(attribute);
            return this;
        }

        public PersistentResourceXMLBuilder addAttribute(AttributeDefinition attribute, AttributeParser attributeParser) {
            this.attributeList.add(attribute);
            this.attributeParsers.put(attribute.getXmlName(), attributeParser);
            return this;
        }

        public PersistentResourceXMLBuilder addAttribute(AttributeDefinition attribute, AttributeParser attributeParser, AttributeMarshaller attributeMarshaller) {
            this.attributeList.add(attribute);
            this.attributeParsers.put(attribute.getXmlName(), attributeParser);
            this.attributeMarshallers.put(attribute.getXmlName(), attributeMarshaller);
            return this;
        }

        public PersistentResourceXMLBuilder addAttributes(AttributeDefinition... attributes) {
            Collections.addAll(this.attributeList, attributes);
            return this;
        }

        public PersistentResourceXMLBuilder setXmlWrapperElement(final String xmlWrapperElement) {
            this.xmlWrapperElement = xmlWrapperElement;
            return this;
        }

        public PersistentResourceXMLBuilder setXmlElementName(final String xmlElementName) {
            this.xmlElementName = xmlElementName;
            return this;
        }

        public PersistentResourceXMLBuilder setUseValueAsElementName(final boolean useValueAsElementName) {
            this.useValueAsElementName = useValueAsElementName;
            return this;
        }

        public PersistentResourceXMLBuilder setNoAddOperation(final boolean noAddOperation) {
            this.noAddOperation = noAddOperation;
            return this;
        }

        public PersistentResourceXMLBuilder setAdditionalOperationsGenerator(final AdditionalOperationsGenerator additionalOperationsGenerator) {
            this.additionalOperationsGenerator = additionalOperationsGenerator;
            return this;
        }

        /**
         * This method permit to set a forced name for resource created by parser.
         * This is useful when xml tag haven't an attribute defining the name for the resource,
         * but the tag name itself is sufficient to decide the name for the resource
         * For example when you have 2 different tag of the same xsd type representing same resource with different name
         *
         * @param forcedName the name to be forced as resourceName
         * @return the PersistentResourceXMLBuilder itself
         */
        public PersistentResourceXMLBuilder setForcedName(String forcedName) {
            this.forcedName = forcedName;
            return this;
        }

        /**
         * Sets whether attributes with an {@link org.jboss.as.controller.AttributeDefinition#getAttributeGroup attribute group}
         * defined should be persisted to a child element whose name is the name of the group. Child elements
         * will be ordered based on the order in which attributes are added to this builder. Child elements for
         * attribute groups will be ordered before elements for child resources.
         *
         * @param useElementsForGroups {@code true} if child elements should be used.
         * @return a builder that can be used for further configuration or to build the xml description
         */
        public PersistentResourceXMLBuilder setUseElementsForGroups(boolean useElementsForGroups) {
            this.useElementsForGroups = useElementsForGroups;
            return this;
        }

        /**
         * If set to false, default attribute values won't be persisted
         *
         * @param marshallDefault weather default values should be persisted or not.
         * @return builder
         */
        public PersistentResourceXMLBuilder setMarshallDefaultValues(boolean marshallDefault) {
            this.marshallDefaultValues = marshallDefault;
            return this;
        }

        /**
         * Sets name for "name" attribute that is used for wildcard resources.
         * It defines name of attribute one resource xml element to be used for such identifier
         * If not set it defaults to "name"
         *
         * @param nameAttributeName xml attribute name to be used for resource name
         * @return builder
         */
        public PersistentResourceXMLBuilder setNameAttributeName(String nameAttributeName) {
            this.nameAttributeName = nameAttributeName;
            return this;
        }

        public PersistentResourceXMLDescription build() {

            return new PersistentResourceXMLDescription(this);
        }
    }

    /**
     * Some resources require more operations that just a simple add. This interface provides a hook for these to be plugged in.
     */
    public interface AdditionalOperationsGenerator {

        /**
         * Generates any additional operations required by the resource
         *
         * @param address      The address of the resource
         * @param addOperation The add operation for the resource
         * @param operations   The operation list
         */
        void additionalOperations(final PathAddress address, final ModelNode addOperation, final List<ModelNode> operations);

    }
}
