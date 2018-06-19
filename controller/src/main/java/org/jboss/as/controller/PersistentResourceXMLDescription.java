package org.jboss.as.controller;

import static javax.xml.stream.XMLStreamConstants.END_ELEMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.parsing.ParseUtils.unexpectedElement;

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
public final class PersistentResourceXMLDescription implements ResourceParser, ResourceMarshaller {

    private final PathElement pathElement;
    private final String xmlElementName;
    private final String xmlWrapperElement;
    private final Map<String, Map<String, AttributeDefinition>> attributesByGroup;
    private final Map<String, List<PersistentResourceXMLDescription>> childrenByGroup = new HashMap<>();
    private final Map<String, List<ResourceMarshaller>> marshallersByGroup = new HashMap<>();
    private final Map<String, AttributeDefinition> attributeElements = new HashMap<>();
    private final boolean useValueAsElementName;
    private final boolean noAddOperation;
    private final AdditionalOperationsGenerator additionalOperationsGenerator;
    private final Map<String, ResourceParser> customChildParsers;
    private final String decoratorElement;
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


    private PersistentResourceXMLDescription(PersistentResourceXMLBuilder builder)  {
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
                String adGroup = ad.getAttributeGroup();
                Map<String, AttributeDefinition> forGroup = this.attributesByGroup.get(adGroup);
                if (forGroup == null) {
                    forGroup = new LinkedHashMap<>();
                    this.attributesByGroup.put(adGroup, forGroup);
                    this.attributeGroups.add(adGroup);
                }
                String adXmlName = ad.getXmlName();
                forGroup.put(adXmlName, ad);
                AttributeParser ap = builder.attributeParsers.getOrDefault(adXmlName, ad.getParser());
                if (ap != null && ap.isParseAsElement()) {
                    attributeElements.put(ap.getXmlName(ad), ad);
                }

            }
        } else {
            LinkedHashMap<String, AttributeDefinition> attrs = new LinkedHashMap<>();
            for (AttributeDefinition ad : builder.attributeList) {
                attrs.put(ad.getXmlName(), ad);
                AttributeParser ap = ad.getParser();
                if (ap != null && ap.isParseAsElement()) {
                    attributeElements.put(ap.getXmlName(ad), ad);
                }
            }
            // Ignore attribute-group, treat all as if they are in the default group
            this.attributesByGroup.put(null, attrs);
        }
        this.childrenByGroup.put(null, builder.children);
        this.marshallersByGroup.put(null, builder.marshallers);
        for (Map.Entry<String, List<PersistentResourceXMLBuilder>> entry : builder.childrenBuilders.entrySet()) {
            String group = entry.getKey();
            List<PersistentResourceXMLBuilder> childBuilders = entry.getValue();
            List<PersistentResourceXMLDescription> children = this.childrenByGroup.get(group);
            if (children == null) {
                children = new ArrayList<>(childBuilders.size());
                this.childrenByGroup.put(group, children);
            }
            List<ResourceMarshaller> marshallers = this.marshallersByGroup.get(group);
            if (marshallers == null) {
                marshallers = new ArrayList<>(childBuilders.size());
                this.marshallersByGroup.put(group, marshallers);
            }
            for (PersistentResourceXMLBuilder childBuilder : childBuilders) {
                PersistentResourceXMLDescription child = childBuilder.build();
                children.add(child);
                marshallers.add(child);
            }
        }
        this.useValueAsElementName = builder.useValueAsElementName;
        this.noAddOperation = builder.noAddOperation;
        this.additionalOperationsGenerator = builder.additionalOperationsGenerator;
        this.attributeParsers = builder.attributeParsers;
        this.attributeMarshallers = builder.attributeMarshallers;
        this.forcedName = builder.forcedName;
        this.marshallDefaultValues = builder.marshallDefaultValues;
        this.nameAttributeName = builder.nameAttributeName;
        this.customChildParsers = builder.customChildParsers;
        this.decoratorElement = builder.decoratorElement;
    }

    public PathElement getPathElement() {
        return this.pathElement;
    }

    @Override
    public void parse(final XMLExtendedStreamReader reader, PathAddress parentAddress, List<ModelNode> list) throws XMLStreamException {
        if (decoratorElement != null) {
            parseDecorator(reader, parentAddress, list);
            return;
        }
        if (xmlWrapperElement != null) {
            if (reader.getLocalName().equals(xmlWrapperElement)) {
                if (reader.hasNext() && reader.nextTag() == END_ELEMENT) { return; }
            } else {
                throw ParseUtils.unexpectedElement(reader);
            }
            parseInternal(reader, parentAddress, list);
            while (reader.nextTag() != END_ELEMENT && !reader.getLocalName().equals(xmlWrapperElement)) {
                parseInternal(reader, parentAddress, list);
            }
        } else {
            parseInternal(reader, parentAddress, list);
        }
    }

    private void parseDecorator(final XMLExtendedStreamReader reader, PathAddress parentAddress, List<ModelNode> list) throws XMLStreamException {
        if (!reader.getLocalName().equals(decoratorElement)) {
            throw unexpectedElement(reader, Collections.singleton(decoratorElement));
        }
        if (!reader.isEndElement()) { //only parse children if we are not on end of tag already
            parseChildren(reader, parentAddress, list, new ModelNode(), null);
        }
    }

    private void parseInternal(final XMLExtendedStreamReader reader, PathAddress parentAddress, List<ModelNode> list) throws XMLStreamException {
        ModelNode op = parseAttributeGroups(reader, parentAddress, list);
        if (additionalOperationsGenerator != null) {
            additionalOperationsGenerator.additionalOperations(PathAddress.pathAddress(op.get(OP_ADDR)), op, list);
        }
        if (!reader.isEndElement()) { //only parse children if we are not on end of tag already
            parseChildren(reader, list, op, null);
        }
    }


    private ModelNode parseAttributeGroups(final XMLExtendedStreamReader reader, PathAddress parentAddress, List<ModelNode> list) throws XMLStreamException {
        ModelNode op = parseAttributes(reader, parentAddress, list); //parse attributes not belonging to a group
        if (!attributeGroups.isEmpty()) {
            while (reader.hasNext() && reader.nextTag() != XMLStreamConstants.END_ELEMENT) {
                final String localName = reader.getLocalName();
                boolean element = attributeElements.containsKey(localName);
                //it can be a group or element attribute
                if (element || attributeGroups.contains(localName)) {
                    if (element) {
                        AttributeDefinition ad = attributeElements.get(localName);
                        ad.getParser().parseElement(ad, reader, op);
                        final String newLocalName = reader.getLocalName();
                        if (attributeGroups.contains(newLocalName)) {
                            parseGroup(reader, list, op);
                        } else if (reader.isEndElement() && !attributeElements.containsKey(newLocalName)) {
                            childAlreadyRead = true;
                            break;
                        }
                    } else {
                        parseGroup(reader, list, op);
                    }

                } else {
                    //don't break, as we read all attributes, we set that child was already read so readChildren wont do .nextTag()
                    childAlreadyRead = true;
                    return op;
                }
            }
            flushRequired = false;
        }
        return op;
    }

    private void parseGroup(XMLExtendedStreamReader reader, List<ModelNode> list, ModelNode op) throws XMLStreamException {
        String group = reader.getLocalName();
        Map<String, AttributeDefinition> groupAttrs = attributesByGroup.get(group);
        for (AttributeDefinition attrGroup : groupAttrs.values()) {
            if (op.hasDefined(attrGroup.getName())) {
                throw ParseUtils.unexpectedElement(reader);
            }
        }
        parseAttributes(reader, op, groupAttrs);
        // Check if there are also element attributes inside a group
        while (reader.hasNext() && reader.nextTag() != END_ELEMENT) {
            AttributeDefinition ad = groupAttrs.get(reader.getLocalName());
            if (ad != null) {
                ad.getParser().parseElement(ad, reader, op);
            } else {
                if (this.childrenByGroup.containsKey(group)) {
                    this.childAlreadyRead = true;
                    this.parseChildren(reader, list, op, group);
                    this.childAlreadyRead = false;
                    break;
                }
                throw ParseUtils.unexpectedElement(reader);
            }
        }
    }

    private ModelNode parseAttributes(XMLExtendedStreamReader reader, PathAddress parentAddress, List<ModelNode> list) throws XMLStreamException {
        PathAddress address = parentAddress.append(this.parsePathElement(reader));
        ModelNode op = Util.createAddOperation(address);
        if (!this.noAddOperation) {
            list.add(op);
        }
        parseAttributes(reader, op, attributesByGroup.get(null)); //parse attributes not belonging to a group
        return op;
    }

    private void parseAttributes(final XMLExtendedStreamReader reader, ModelNode op, Map<String, AttributeDefinition> attributes) throws XMLStreamException {
        int attrCount = reader.getAttributeCount();
        for (int i = 0; i < attrCount; i++) {
            String attributeName = reader.getAttributeLocalName(i);
            String value = reader.getAttributeValue(i);
            if (this.pathElement.isWildcard() && nameAttributeName.equals(attributeName)) {
                // Already parsed
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
                    AttributeDefinition ad = attributeElements.get(reader.getLocalName());
                    if (ad != null) {
                        AttributeParser parser = attributeParsers.getOrDefault(ad.getXmlName(), ad.getParser());
                        parser.parseElement(ad, reader, op);
                    } else {
                        break;  //this means we only have children left, return so child handling logic can take over
                    }
                } while (!reader.getLocalName().equals(originalStartElement) && reader.hasNext() && reader.nextTag() != XMLStreamConstants.END_ELEMENT);
                childAlreadyRead = true;
            }
        }
    }

    private PathElement parsePathElement(XMLExtendedStreamReader reader) throws XMLStreamException {
        if (this.pathElement.isWildcard()) {
            String name = reader.getAttributeValue(null, this.nameAttributeName);
            if (name == null) {
                name = this.forcedName;
            }
            if (name == null) {
                throw ControllerLogger.ROOT_LOGGER.missingRequiredAttributes(new StringBuilder(this.nameAttributeName), reader.getLocation());
            }
            return PathElement.pathElement(this.pathElement.getKey(), name);
        }
        return this.pathElement;
    }

    private Map<String, PersistentResourceXMLDescription> getChildrenMap(String group) {
        Map<String, PersistentResourceXMLDescription> res = new HashMap<>();
        for (PersistentResourceXMLDescription child : childrenByGroup.get(group)) {
            if (child.xmlWrapperElement != null) {
                res.put(child.xmlWrapperElement, child);
            } else {
                res.put(child.xmlElementName, child);
            }
        }
        return res;
    }

    private void parseChildren(final XMLExtendedStreamReader reader, List<ModelNode> list, ModelNode op, String group) throws XMLStreamException {
        this.parseChildren(reader, PathAddress.pathAddress(op.get(OP_ADDR)), list, op, group);
    }

    private void parseChildren(final XMLExtendedStreamReader reader, PathAddress parentAddress, List<ModelNode> list, ModelNode op, String group) throws XMLStreamException {
        if (childrenByGroup.get(group).isEmpty()) {
            if (flushRequired && attributeGroups.isEmpty() && attributeElements.isEmpty()) {
                ParseUtils.requireNoContent(reader);
            }
            if (childAlreadyRead) {
                throw ParseUtils.unexpectedElement(reader);
            }
        } else {
            Map<String, PersistentResourceXMLDescription> children = getChildrenMap(group);
            if (childAlreadyRead) {
                PersistentResourceXMLDescription decoratorChild = children.get(reader.getLocalName());
                if (decoratorChild != null && decoratorChild.decoratorElement != null) {
                    decoratorChild.parseDecorator(reader, parentAddress, list);
                }
            }
            if (childAlreadyRead || (reader.hasNext() && reader.nextTag() != XMLStreamConstants.END_ELEMENT)) {
                do {
                    final String localName = reader.getLocalName();
                    AttributeDefinition elementAd;
                    ResourceParser child = children.get(localName);
                    if (child != null) {
                        child.parse(reader, parentAddress, list);
                    } else if ((elementAd = attributeElements.get(localName)) != null) {
                        elementAd.getParser().parseElement(elementAd, reader, op);
                    } else if ((child = customChildParsers.get(localName)) != null) {
                        child.parse(reader, parentAddress, list);
                    } else {
                        throw ParseUtils.unexpectedElement(reader, children.keySet());
                    }
                } while (reader.hasNext() && reader.nextTag() != XMLStreamConstants.END_ELEMENT);
            }
        }
    }

    @Override
    public void persist(XMLExtendedStreamWriter writer, ModelNode model) throws XMLStreamException {
        persist(writer, model, namespaceURI);
    }

    private static void writeStartElement(XMLExtendedStreamWriter writer, String namespaceURI, String localName) throws XMLStreamException {
        if (namespaceURI != null) {
            writer.writeStartElement(namespaceURI, localName);
        } else {
            writer.writeStartElement(localName);
        }
    }

    private static void startSubsystemElement(XMLExtendedStreamWriter writer, String namespaceURI, boolean empty) throws XMLStreamException {
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

    /**
     * persist decorator and than continue to children without touching the model
     */
    private void persistDecorator(XMLExtendedStreamWriter writer, ModelNode model) throws XMLStreamException {
       if (shouldWriteDecoratorAndElements(model)) {
           writer.writeStartElement(decoratorElement);
           persistChildren(writer, model, null);
           writer.writeEndElement();
       }
    }

    /**
     * @return true if any of children are defined in the model
     */
    private boolean shouldWriteDecoratorAndElements(ModelNode model) {
        for (PersistentResourceXMLDescription child : childrenByGroup.get(null)) {
            //if we have child decorator, than we check its children, we only handle one level of nesting
            if (child.decoratorElement != null) {
                for (PersistentResourceXMLDescription decoratedChild : child.childrenByGroup.get(null)) {
                    if (definedInModel(model, decoratedChild)) {
                        return true;
                    }
                }
            } else if (definedInModel(model, child)) {
                return true;
            }

        }
        //we always write if there is custom writer defined
        return !customChildParsers.isEmpty();
    }

    private boolean definedInModel(ModelNode model, PersistentResourceXMLDescription child) {
        PathElement pe = child.getPathElement();
        boolean wildcard = getPathElement().isWildcard();
        if (wildcard ? model.hasDefined(pe.getKey()) : model.hasDefined(pe.getKeyValuePair())){
            return true;
        }
        return false;
    }

    public void persist(XMLExtendedStreamWriter writer, ModelNode model, String namespaceURI) throws XMLStreamException {
        if (decoratorElement!=null){
            persistDecorator(writer, model);
            return;
        }
        boolean wildcard = pathElement.isWildcard();
        model = wildcard ? model.get(pathElement.getKey()) : model.get(pathElement.getKeyValuePair());
        boolean isSubsystem = pathElement.getKey().equals(ModelDescriptionConstants.SUBSYSTEM);
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
                persistChildren(writer, subModel, null);
                writer.writeEndElement();
            }
        } else {
            final boolean empty = attributeGroups.isEmpty() && childrenByGroup.get(null).isEmpty();
            if (useValueAsElementName) {
                writeStartElement(writer, namespaceURI, getPathElement().getValue());
            } else if (isSubsystem) {
                startSubsystemElement(writer, namespaceURI, empty);
            } else {
                writeStartElement(writer, namespaceURI, xmlElementName);
            }

            persistAttributes(writer, model);
            persistChildren(writer, model, null);

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
            for (Map.Entry<String, Map<String, AttributeDefinition>> entry : attributesByGroup.entrySet()) {
                String group = entry.getKey();
                if (group != null) {
                    marshallAttributes(writer, model, entry.getValue().values(), group);
                }
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

        boolean hasChildren = (group != null) && this.marshallersByGroup.containsKey(group);
        boolean hasNestedElements = elementAds != null || hasChildren;

        for (AttributeDefinition ad : sortedAds) {
            AttributeMarshaller marshaller = attributeMarshallers.getOrDefault(ad.getXmlName(), ad.getMarshaller());
            boolean marshallable = marshaller.isMarshallable(ad, model, marshallDefaultValues);
            if (marshallable || hasChildren) {
                if (!started && group != null) {
                    if (hasNestedElements) {
                        writer.writeStartElement(group);
                    } else {
                        writer.writeEmptyElement(group);
                    }
                    started = true;
                }
                if (marshallable) {
                    marshaller.marshall(ad, model, marshallDefaultValues, writer);
                }
            }
        }
        if (hasChildren) {
            this.persistChildren(writer, model, group);
        }
        if (hasNestedElements && started) {
            writer.writeEndElement();
        }
    }

    public void persistChildren(XMLExtendedStreamWriter writer, ModelNode model, String group) throws XMLStreamException {
        for (ResourceMarshaller child : marshallersByGroup.get(group)) {
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

    /**
     * Creates builder for passed path element
     *
     * @param elementName name of xml element that is used as decorator
     * @return PersistentResourceXMLBuilder
     * @deprecated decorator element support is currently considered as preview
     * @since 4.0
     */
    @Deprecated
    public static PersistentResourceXMLBuilder decorator(final String elementName) {
        return new PersistentResourceXMLBuilder(PathElement.pathElement(elementName), null).setDecoratorGroup(elementName);
    }

    public static final class PersistentResourceXMLBuilder {
        protected final PathElement pathElement;
        private final String namespaceURI;
        private String xmlElementName;
        private String xmlWrapperElement;
        private boolean useValueAsElementName;
        private boolean noAddOperation;
        private AdditionalOperationsGenerator additionalOperationsGenerator;
        private final List<AttributeDefinition> attributeList = new LinkedList<>();
        private final Map<String, List<PersistentResourceXMLBuilder>> childrenBuilders = new HashMap<>();
        private final List<PersistentResourceXMLDescription> children = new LinkedList<>();
        private final Map<String, AttributeParser> attributeParsers = new LinkedHashMap<>();
        private final Map<String, AttributeMarshaller> attributeMarshallers = new LinkedHashMap<>();
        private final Map<String, ResourceParser> customChildParsers = new LinkedHashMap<>();
        private final LinkedList<ResourceMarshaller> marshallers = new LinkedList<>();
        private boolean useElementsForGroups = true;
        private String forcedName;
        private boolean marshallDefaultValues = true;
        private String nameAttributeName = NAME;
        private String decoratorElement = null;

        private PersistentResourceXMLBuilder(final PathElement pathElement) {
            this(pathElement, null);
        }

        private PersistentResourceXMLBuilder(final PathElement pathElement, String namespaceURI) {
            this.pathElement = pathElement;
            this.namespaceURI = namespaceURI;
            this.xmlElementName = pathElement.isWildcard() ? pathElement.getKey() : pathElement.getValue();
            this.childrenBuilders.put(null, new LinkedList<>());
        }

        public PersistentResourceXMLBuilder addChild(PersistentResourceXMLBuilder builder) {
            return this.addChild(null, builder);
        }

        public PersistentResourceXMLBuilder addChild(AttributeDefinition parentAttribute, PersistentResourceXMLBuilder builder) {
            String group = (parentAttribute != null) ? parentAttribute.getAttributeGroup() : null;
            List<PersistentResourceXMLBuilder> children = this.childrenBuilders.get(group);
            if (children == null) {
                children = new LinkedList<>();
                this.childrenBuilders.put(group, children);
            }
            children.add(builder);
            return this;
        }

        public PersistentResourceXMLBuilder addChild(PersistentResourceXMLDescription description) {
            this.children.add(description);
            this.marshallers.add(description);
            return this;
        }

        /*
         * Adds custom parser for child element
         * @param xmlElementName name of xml element that will be hand off to custom parser
         * @param parser custom parser
         * @return this builder
         * @since 4.0
         *//*
        public PersistentResourceXMLBuilder addChild(final String xmlElementName, ResourceParser parser, ResourceMarshaller marshaller) {
            this.customChildParsers.put(xmlElementName, parser);
            this.marshallers.add(marshaller);
            return this;
        }*/

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

        private PersistentResourceXMLBuilder setDecoratorGroup(String elementName){
            this.decoratorElement = elementName;
            return this;
        }

        public PersistentResourceXMLDescription build() {

            return new PersistentResourceXMLDescription(this);
        }
    }

    /**
     * Some resources require more operations that just a simple add. This interface provides a hook for these to be plugged in.
     */
    @FunctionalInterface
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
