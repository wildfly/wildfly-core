package org.jboss.as.controller;

import static javax.xml.stream.XMLStreamConstants.END_ELEMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADDRESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
 */
public class PersistentResourceXMLDescription {

    protected final PersistentResourceDefinition resourceDefinition;
    protected final String xmlElementName;
    protected final String xmlWrapperElement;
    protected final LinkedHashMap<String, AttributeDefinition> attributes;
    protected final LinkedHashMap<String, LinkedHashMap<String, AttributeDefinition>> attributesByGroup;
    protected final List<PersistentResourceXMLDescription> children;
    protected final boolean useValueAsElementName;
    protected final boolean noAddOperation;
    protected final AdditionalOperationsGenerator additionalOperationsGenerator;
    private boolean flushRequired = true;
    private final Map<String,AttributeParser> attributeParsers;
    private final boolean useElementsForGroups;
    private final String namespaceURI;

    /** @deprecated use a {@link org.jboss.as.controller.PersistentResourceXMLDescription.PersistentResourceXMLBuilder builder} */
    @Deprecated
    protected PersistentResourceXMLDescription(final PersistentResourceDefinition resourceDefinition, final String xmlElementName, final String xmlWrapperElement, final LinkedHashMap<String, AttributeDefinition> attributes, final List<PersistentResourceXMLDescription> children, final boolean useValueAsElementName, final boolean noAddOperation, final AdditionalOperationsGenerator additionalOperationsGenerator, Map<String, AttributeParser> attributeParsers) {
        this.resourceDefinition = resourceDefinition;
        this.xmlElementName = xmlElementName;
        this.xmlWrapperElement = xmlWrapperElement;
        this.useElementsForGroups = true;
        this.attributes = attributes;
        this.attributesByGroup = new LinkedHashMap<>();
        this.attributesByGroup.put(null, attributes);
        this.children = children;
        this.useValueAsElementName = useValueAsElementName;
        this.noAddOperation = noAddOperation;
        this.additionalOperationsGenerator = additionalOperationsGenerator;
        this.attributeParsers = attributeParsers;
        this.namespaceURI = null;
    }

    private PersistentResourceXMLDescription(PersistentResourceXMLBuilder builder) {
        this.resourceDefinition = builder.resourceDefinition;
        this.xmlElementName = builder.xmlElementName;
        this.xmlWrapperElement = builder.xmlWrapperElement;
        this.attributes = builder.attributes;
        this.useElementsForGroups = builder.useElementsForGroups;
        this.attributesByGroup = new LinkedHashMap<>();
        this.namespaceURI = builder.namespaceURI;
        if (useElementsForGroups) {
            // Ensure we have a map for the default group even if there are no attributes so we don't NPE later
            this.attributesByGroup.put(null, new LinkedHashMap<String, AttributeDefinition>());

            // Segregate attributes by group
            for (Map.Entry<String, AttributeDefinition> entry : builder.attributes.entrySet()) {
                AttributeDefinition ad = entry.getValue();
                LinkedHashMap<String, AttributeDefinition> forGroup = this.attributesByGroup.get(ad.getAttributeGroup());
                if (forGroup == null) {
                    forGroup = new LinkedHashMap<>();
                    this.attributesByGroup.put(ad.getAttributeGroup(), forGroup);
                }
                forGroup.put(entry.getKey(), ad);
            }
        } else {
            // Ignore attribute-group, treat all as if they are in the default group
            this.attributesByGroup.put(null, builder.attributes);
        }
        this.children = new ArrayList<>();
        for (PersistentResourceXMLBuilder b : builder.children) {
            this.children.add(b.build());
        }
        this.useValueAsElementName = builder.useValueAsElementName;
        this.noAddOperation = builder.noAddOperation;
        this.additionalOperationsGenerator = builder.additionalOperationsGenerator;
        this.attributeParsers = builder.attributeParsers;
    }

    public PathElement getPathElement(){
        return resourceDefinition.getPathElement();
    }

    public void parse(final XMLExtendedStreamReader reader, PathAddress parentAddress, List<ModelNode> list) throws XMLStreamException {
        if (xmlWrapperElement != null) {
            if (reader.getLocalName().equals(xmlWrapperElement)) {
                if (reader.hasNext()) {
                    if (reader.nextTag() == END_ELEMENT) {
                        return;
                    }
                }
            } else {
                throw ParseUtils.unexpectedElement(reader);
            }

        }
        ModelNode op = Util.createAddOperation();

        boolean wildcard = resourceDefinition.getPathElement().isWildcard();
        String name = parseAttributes(reader, op, attributesByGroup.get(null), wildcard);
        if (wildcard && name == null) {
            throw ControllerLogger.ROOT_LOGGER.missingRequiredAttributes(new StringBuilder(NAME), reader.getLocation());
        }

        PathElement path = wildcard ? PathElement.pathElement(resourceDefinition.getPathElement().getKey(), name) : resourceDefinition.getPathElement();
        PathAddress address = parentAddress.append(path);
        if(!noAddOperation) {
            op.get(ADDRESS).set(address.toModelNode());
            list.add(op);
        }
        if(additionalOperationsGenerator != null) {
            additionalOperationsGenerator.additionalOperations(address, op, list);
        }
        parseChildren(reader, address, list, op);
        if (xmlWrapperElement != null) {
            ParseUtils.requireNoContent(reader);
        }
    }

    private String parseAttributes(final XMLExtendedStreamReader reader, ModelNode op, Map<String, AttributeDefinition> attributes, boolean wildcard) throws XMLStreamException {
        String name = null;
        for (int i = 0; i < reader.getAttributeCount(); i++) {
            String attributeName = reader.getAttributeLocalName(i);
            String value = reader.getAttributeValue(i);
            if (wildcard && NAME.equals(attributeName)) {
                name = value;
            } else if (attributes.containsKey(attributeName)) {
                AttributeDefinition def = attributes.get(attributeName);
                AttributeParser parser = attributeParsers.containsKey(attributeName)? attributeParsers.get(attributeName) : def.getParser();
                assert parser != null;
                parser.parseAndSetParameter(def,value,op,reader);
            } else {
                throw ParseUtils.unexpectedAttribute(reader, i, attributes.keySet());
            }
        }
        for (AttributeDefinition attributeDefinition: attributes.values()){
            if (attributeDefinition instanceof PropertiesAttributeDefinition){
                PropertiesAttributeDefinition attribute = (PropertiesAttributeDefinition) attributeDefinition;
                // TODO what if this attribute isn't required and isn't in the xml?
                attribute.parse(reader,op);
                flushRequired = false;
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
        if (children.size() == 0 && (!useElementsForGroups || attributesByGroup.size() == 1)) {
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
                PersistentResourceXMLDescription child = children.get(reader.getLocalName());
                if (child != null) {
                    child.parse(reader, parentAddress, list);
                } else {
                    Map<String, AttributeDefinition> groupAttributes = useElementsForGroups ? attributesByGroup.get(reader.getLocalName()) : null;
                    if (groupAttributes != null) {
                        parseAttributes(reader, parentAddOp, groupAttributes, false);
                    } else {
                        throw ParseUtils.unexpectedElement(reader, children.keySet());
                    }
                }
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
            writeStartElement(writer, namespaceURI, xmlWrapperElement);
        }

        if (wildcard) {
            for (Property p : model.asPropertyList()) {
                if (useValueAsElementName) {
                    writeStartElement(writer, namespaceURI, p.getName());
                } else {
                    writeStartElement(writer, namespaceURI, xmlElementName);
                    writer.writeAttribute(NAME, p.getName());
                }

                persistAttributes(writer, p.getValue(), false);

                persistChildren(writer, p.getValue());
                writer.writeEndElement();
            }
        } else {
            if (useValueAsElementName) {
                writeStartElement(writer, namespaceURI, resourceDefinition.getPathElement().getValue());
            } else if (isSubsystem) {
                startSubsystemElement(writer, namespaceURI, children.isEmpty());
            } else {
                writeStartElement(writer, namespaceURI, xmlElementName);
            }

            persistAttributes(writer, model, true);
            persistChildren(writer, model);

            // Do not attempt to write end element if the <subsystem/> has no elements!
            if (!isSubsystem || !children.isEmpty()) {
                writer.writeEndElement();
            }
        }

        if (writeWrapper) {
            writer.writeEndElement();
        }
    }

    private void persistAttributes(XMLExtendedStreamWriter writer, ModelNode model, boolean marshalDefault) throws XMLStreamException {

        // Persist all attributes in the 'null' group
        for (Map.Entry<String, AttributeDefinition> def : attributesByGroup.get(null).entrySet()) {
            def.getValue().getAttributeMarshaller().marshallAsAttribute(def.getValue(), model, marshalDefault, writer);
        }

        if (useElementsForGroups) {
            for (Map.Entry<String, LinkedHashMap<String, AttributeDefinition>> entry : attributesByGroup.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                boolean started = false;
                for (Map.Entry<String, AttributeDefinition> def : entry.getValue().entrySet()) {
                    AttributeDefinition ad = def.getValue();
                    AttributeMarshaller marshaller = ad.getAttributeMarshaller();
                    if (marshaller.isMarshallable(ad, model, marshalDefault)) {
                        if (!started) {
                            writer.writeEmptyElement(entry.getKey());
                            started = true;
                        }
                        marshaller.marshallAsAttribute(ad, model, marshalDefault, writer);
                    }
                }
            }
        } // else we will only have attributes under the 'null' group
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
        protected AdditionalOperationsGenerator additionalOperationsGenerator;
        protected final LinkedHashMap<String, AttributeDefinition> attributes = new LinkedHashMap<>();
        protected final List<PersistentResourceXMLBuilder> children = new ArrayList<>();
        protected final LinkedHashMap<String, AttributeParser> attributeParsers = new LinkedHashMap<>();
        protected boolean useElementsForGroups = true;

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
            this.attributes.put(attribute.getXmlName(), attribute);
            return this;
        }
        public PersistentResourceXMLBuilder addAttribute(AttributeDefinition attribute, AttributeParser attributeParser) {
            this.attributes.put(attribute.getXmlName(), attribute);
            this.attributeParsers.put(attribute.getXmlName(),attributeParser);
            return this;
        }

        public PersistentResourceXMLBuilder addAttributes(AttributeDefinition... attributes) {
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

        public PersistentResourceXMLBuilder setAdditionalOperationsGenerator(final AdditionalOperationsGenerator additionalOperationsGenerator) {
            this.additionalOperationsGenerator = additionalOperationsGenerator;
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
