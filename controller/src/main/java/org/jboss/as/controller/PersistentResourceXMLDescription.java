package org.jboss.as.controller;

import static javax.xml.stream.XMLStreamConstants.END_ELEMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADDRESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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
import org.jboss.dmr.Property;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;

/**
 * A representation of a resource as needed by the XML parser.
 *
 * @author Tomaz Cerar
 * @author Stuart Douglas
 * @author <a href=mailto:tadamski@redhat.com>Tomasz Adamski</a>
 */
public class PersistentResourceXMLDescription {

    protected final PersistentResourceDefinition resourceDefinition;
    protected final String xmlElementName;
    protected final String xmlWrapperElement;
    @Deprecated
    protected final LinkedHashMap<String, AttributeDefinition> attributes;
    protected final AttributesXMLDescription attributesDescription;
    protected final List<PersistentResourceXMLDescription> children;
    protected final boolean useValueAsElementName;
    protected final boolean noAddOperation;
    protected final AdditionalOperationsGenerator additionalOperationsGenerator;
    private boolean flushRequired = true;
    private final Map<String,AttributeParser> attributeParsers;
    private final Map<String,AttributeMarshaller> attributeMarshallers;
    private final String namespaceURI;

    /** @deprecated use a {@link org.jboss.as.controller.PersistentResourceXMLDescription.PersistentResourceXMLBuilder builder} */
    @Deprecated
    protected PersistentResourceXMLDescription(final PersistentResourceDefinition resourceDefinition, final String xmlElementName, final String xmlWrapperElement, final LinkedHashMap<String, AttributeDefinition> attributes, final List<PersistentResourceXMLDescription> children, final boolean useValueAsElementName, final boolean noAddOperation, final AdditionalOperationsGenerator additionalOperationsGenerator, Map<String, AttributeParser> attributeParsers) {
        this.resourceDefinition = resourceDefinition;
        this.xmlElementName = xmlElementName;
        this.xmlWrapperElement = xmlWrapperElement;
        this.attributes = attributes;
        this.attributesDescription = new NestGroupedAttributesXMLDescription();
        for (final AttributeDefinition attribute : attributes.values()) {
            attributesDescription.registerAttribute(attribute);
        }
        this.children = children;
        this.useValueAsElementName = useValueAsElementName;
        this.noAddOperation = noAddOperation;
        this.additionalOperationsGenerator = additionalOperationsGenerator;
        this.attributeParsers = attributeParsers;
        this.attributeMarshallers = null;
        this.namespaceURI = null;
    }

    private PersistentResourceXMLDescription(PersistentResourceXMLBuilder builder) {
        this.resourceDefinition = builder.resourceDefinition;
        this.xmlElementName = builder.xmlElementName;
        this.xmlWrapperElement = builder.xmlWrapperElement;
        this.attributes = builder.attributes;
        this.namespaceURI = builder.namespaceURI;
        this.attributesDescription = builder.getAttributesXMLDescription();
        for (final AttributeDefinition attribute : builder.attributeList) {
            attributesDescription.registerAttribute(attribute);
        }
        this.children = new ArrayList<>();
        for (PersistentResourceXMLBuilder b : builder.children) {
            this.children.add(b.build());
        }
        this.useValueAsElementName = builder.useValueAsElementName;
        this.noAddOperation = builder.noAddOperation;
        this.additionalOperationsGenerator = builder.additionalOperationsGenerator;

        this.attributeParsers = builder.attributeParsers;
        this.attributeMarshallers = builder.attributeMarshallers;
    }

    public PathElement getPathElement() {
        return resourceDefinition.getPathElement();
    }

    public void parse(final XMLExtendedStreamReader reader, PathAddress parentAddress, List<ModelNode> list) throws XMLStreamException {
        ModelNode op = Util.createAddOperation();

        boolean wildcard = resourceDefinition.getPathElement().isWildcard();
        String name = parseRootAttributes(reader, op, wildcard);
        if (wildcard && name == null) {
            throw ControllerLogger.ROOT_LOGGER.missingRequiredAttributes(new StringBuilder(NAME), reader.getLocation());
        }
        PathElement path = wildcard ? PathElement.pathElement(resourceDefinition.getPathElement().getKey(), name) : resourceDefinition.getPathElement();
        PathAddress address = parentAddress.append(path);
        if (!noAddOperation) {
            op.get(ADDRESS).set(address.toModelNode());
            list.add(op);
        }
        if (additionalOperationsGenerator != null) {
            additionalOperationsGenerator.additionalOperations(address, op, list);
        }
        if (!reader.isEndElement()) { // only parse children if we are not on end of tag already
            parseChildren(reader, address, list, op);
        }
    }

    private String parseRootAttributes(final XMLExtendedStreamReader reader, ModelNode op, boolean wildcard) throws XMLStreamException {
        String name = null;
        final Map<String, AttributeDefinition> rootAttributes = attributesDescription.getRootAttributes();
        for (int i = 0; i < reader.getAttributeCount(); i++) {
            String attributeName = reader.getAttributeLocalName(i);
            String value = reader.getAttributeValue(i);
            if (wildcard && NAME.equals(attributeName)) {
                name = value;
            } else if (rootAttributes.containsKey(attributeName)) {
                AttributeDefinition def = rootAttributes.get(attributeName);
                AttributeParser parser = attributeParsers.containsKey(attributeName)? attributeParsers.get(attributeName) : def.getParser();
                assert parser != null;
                parser.parseAndSetParameter(def,value,op,reader);
            } else {
                throw ParseUtils.unexpectedAttribute(reader, i, rootAttributes.keySet());
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

    private void parseChildren(final XMLExtendedStreamReader reader, PathAddress parentAddress, List<ModelNode> list, ModelNode parentAddOp) throws XMLStreamException {
        if (children.isEmpty() && attributesDescription.getTags().isEmpty()) {
            if (flushRequired){
                ParseUtils.requireNoContent(reader);
            }
        } else {
            String parentName = reader.getLocalName();
            Map<String, PersistentResourceXMLDescription> children = getChildrenMap();
            while (reader.hasNext()) {
                if (reader.nextTag() == XMLStreamConstants.END_ELEMENT) {
                    // break the loop at the end of the parent element
                    if (parentName.equals(reader.getLocalName())) {
                        break;
                    }
                    // else continue to the next children
                    continue;
                }
                final String childName = reader.getLocalName();

                if (attributesDescription.getTags().contains(childName)) {
                    attributesDescription.parseTag(childName, reader, attributeParsers, parentAddOp);
                } else {
                    final PersistentResourceXMLDescription child = children.get(childName);
                    if (child != null) {
                        parseChild(child, reader, parentAddress, list);
                    } else {
                        final Set<String> allowedElements = new LinkedHashSet<>();
                        allowedElements.addAll(attributesDescription.getTags());
                        allowedElements.addAll(children.keySet());
                        throw ParseUtils.unexpectedElement(reader, allowedElements);
                    }
                }
            }
        }
    }

    private void parseChild(final PersistentResourceXMLDescription child, final XMLExtendedStreamReader reader,
            final PathAddress parentAddress, final List<ModelNode> list) throws XMLStreamException {
        if (child.xmlWrapperElement != null) {
            if (reader.getLocalName().equals(child.xmlWrapperElement)) {
                if (reader.hasNext() && reader.nextTag() == END_ELEMENT) {
                    return;
                }
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
    }


    public void persist(XMLExtendedStreamWriter writer, ModelNode model) throws XMLStreamException {
        persist(writer, model, namespaceURI);
    }

    private void writeStartElement(XMLExtendedStreamWriter writer, String namespaceURI, String localName, boolean empty)
            throws XMLStreamException {
        if (!empty) {
            if (namespaceURI != null) {
                writer.writeStartElement(namespaceURI, localName);
            } else {
                writer.writeStartElement(localName);
            }
        } else {
            if (namespaceURI != null) {
                writer.writeEmptyElement(namespaceURI, localName);
            } else {
                writer.writeEmptyElement(localName);
            }
        }
    }

    public void startSubsystemElement(XMLExtendedStreamWriter writer, String namespaceURI, boolean empty) throws XMLStreamException {

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
        boolean wildcard = resourceDefinition.getPathElement().isWildcard();
        model = wildcard ? model.get(resourceDefinition.getPathElement().getKey()) : model.get(resourceDefinition.getPathElement().getKeyValuePair());
        boolean isSubsystem = resourceDefinition.getPathElement().getKey().equals(ModelDescriptionConstants.SUBSYSTEM);
        if (!isSubsystem && !model.isDefined() && !useValueAsElementName) {
            return;
        }

        boolean writeWrapper = xmlWrapperElement != null;
        if (writeWrapper) {
            writeStartElement(writer, namespaceURI, xmlWrapperElement, false);
        }

        if (wildcard) {
            for (Property p : model.asPropertyList()) {
                boolean attributeTags = attributesDescription.willCreateTags(p.getValue(), false);
                boolean noNestedTags =!attributeTags && children.isEmpty();
                if (useValueAsElementName) {
                    writeStartElement(writer, namespaceURI, p.getName(), noNestedTags);
                } else {
                    writeStartElement(writer, namespaceURI, xmlElementName, noNestedTags);
                    writer.writeAttribute(NAME, p.getName());
                }

                attributesDescription.persist(writer, p.getValue(), attributeMarshallers, false);
                persistChildren(writer, p.getValue());

                if (!noNestedTags) {
                    writer.writeEndElement();
                }
            }
        } else {
            boolean attributeTags = attributesDescription.willCreateTags(model, true);
            boolean noNestedTags =!attributeTags && children.isEmpty();
            if (useValueAsElementName) {
                writeStartElement(writer, namespaceURI, resourceDefinition.getPathElement().getValue(), noNestedTags);
            } else if (isSubsystem) {
                startSubsystemElement(writer, namespaceURI, noNestedTags);
            } else {
                writeStartElement(writer, namespaceURI, xmlElementName, noNestedTags);
            }

            attributesDescription.persist(writer, model, attributeMarshallers, true);
            persistChildren(writer, model);

            if (!noNestedTags) {
                writer.writeEndElement();
            }
        }

        if (writeWrapper) {
            writer.writeEndElement();
        }
    }


    public void persistChildren(XMLExtendedStreamWriter writer, ModelNode model) throws XMLStreamException {
        for (PersistentResourceXMLDescription child : children) {
            child.persist(writer, model);
        }
    }

    public static PersistentResourceXMLBuilder builder(PersistentResourceDefinition resource) {
        return new PersistentResourceXMLBuilder(resource);
    }

    public static PersistentResourceXMLBuilder builder(PersistentResourceDefinition resource, String namespaceURI) {
        return new PersistentResourceXMLBuilder(resource, namespaceURI);
    }

    public static class PersistentResourceXMLBuilder {


        protected final PersistentResourceDefinition resourceDefinition;
        private final String namespaceURI;
        protected String xmlElementName;
        protected String xmlWrapperElement;
        protected boolean useValueAsElementName;
        protected boolean noAddOperation;
        protected boolean useElementsForGroups = true;
        protected AdditionalOperationsGenerator additionalOperationsGenerator;
        @Deprecated
        protected final LinkedHashMap<String, AttributeDefinition> attributes = new LinkedHashMap<>();
        protected final LinkedList<AttributeDefinition> attributeList = new LinkedList<>();
        protected final List<PersistentResourceXMLBuilder> children = new ArrayList<>();
        protected final LinkedHashMap<String, AttributeParser> attributeParsers = new LinkedHashMap<>();
        protected final LinkedHashMap<String, AttributeMarshaller> attributeMarshallers = new LinkedHashMap<>();

        protected PersistentResourceXMLBuilder(final PersistentResourceDefinition resourceDefinition) {
            this.resourceDefinition = resourceDefinition;
            this.namespaceURI = null;
            this.xmlElementName = resourceDefinition.getPathElement().isWildcard() ? resourceDefinition.getPathElement().getKey() : resourceDefinition.getPathElement().getValue();
        }

        protected PersistentResourceXMLBuilder(final PersistentResourceDefinition resourceDefinition, String namespaceURI) {
            this.resourceDefinition = resourceDefinition;
            this.namespaceURI = namespaceURI;
            this.xmlElementName = resourceDefinition.getPathElement().isWildcard() ? resourceDefinition.getPathElement().getKey() : resourceDefinition.getPathElement().getValue();
        }

        public PersistentResourceXMLBuilder addChild(PersistentResourceXMLBuilder builder) {
            this.children.add(builder);
            return this;
        }

        public PersistentResourceXMLBuilder addAttribute(AttributeDefinition attribute) {
            this.attributeList.add(attribute);
            this.attributes.put(attribute.getXmlName(), attribute);
            return this;
        }
        public PersistentResourceXMLBuilder addAttribute(AttributeDefinition attribute, AttributeParser attributeParser) {
            this.attributeList.add(attribute);
            this.attributes.put(attribute.getXmlName(), attribute);
            this.attributeParsers.put(attribute.getXmlName(),attributeParser);
            return this;
        }

        public PersistentResourceXMLBuilder addAttribute(AttributeDefinition attribute, AttributeParser attributeParser, AttributeMarshaller attributeMarshaller) {
            this.attributeList.add(attribute);
            this.attributes.put(attribute.getXmlName(), attribute);
            this.attributeParsers.put(attribute.getXmlName(), attributeParser);
            this.attributeMarshallers.put(attribute.getXmlName(), attributeMarshaller);
            return this;
        }

        public PersistentResourceXMLBuilder addAttributes(AttributeDefinition... attributes) {
            Collections.addAll(this.attributeList, attributes);
            for (final AttributeDefinition at : attributes) {
                this.attributes.put(at.getXmlName(), at);
            }
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

        public void setUseElementsForGroups(final boolean useElementsForGroups) {
            this.useElementsForGroups = useElementsForGroups;
        }

        public AttributesXMLDescription getAttributesXMLDescription() {
            if (useElementsForGroups) {
                return new NestGroupedAttributesXMLDescription();
            } else {
                return new IgnoreGroupsAttributesXMLDescription();
            }
        }

        public PersistentResourceXMLBuilder setAdditionalOperationsGenerator(final AdditionalOperationsGenerator additionalOperationsGenerator) {
            this.additionalOperationsGenerator = additionalOperationsGenerator;
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
         * @param address The address of the resource
         * @param addOperation The add operation for the resource
         * @param operations The operation list
         */
        void additionalOperations(final PathAddress address, final ModelNode addOperation, final List<ModelNode> operations);

    }
}
