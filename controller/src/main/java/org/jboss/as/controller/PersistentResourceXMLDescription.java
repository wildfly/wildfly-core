/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import static javax.xml.stream.XMLStreamConstants.END_ELEMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADDRESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
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
import java.util.stream.Stream;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.parsing.Element;
import org.jboss.as.controller.parsing.ParseUtils;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.Namespace;
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
    private final LinkedHashMap<String, LinkedHashMap<String, AttributeDefinition>> attributesByGroup;
    private final List<PersistentResourceXMLDescription> children;
    private final List<ResourceMarshaller> marshallers;
    private final Map<String, AttributeDefinition> attributeElements = new HashMap<>();
    private final boolean useValueAsElementName;
    private final boolean noAddOperation;
    private final AdditionalOperationsGenerator additionalOperationsGenerator;
    private final LinkedHashMap<String, ResourceParser> customChildParsers;
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
                LinkedHashMap<String, AttributeDefinition> forGroup = this.attributesByGroup.get(adGroup);
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
                AttributeParser ap = builder.attributeParsers.getOrDefault(ad.getXmlName(), ad.getParser());
                if (ap != null && ap.isParseAsElement()) {
                    attributeElements.put(ap.getXmlName(ad), ad);
                }
            }
            // Ignore attribute-group, treat all as if they are in the default group
            this.attributesByGroup.put(null, attrs);
        }
        this.children = new ArrayList<>();
        this.marshallers = builder.marshallers;
        for (PersistentResourceXMLBuilder b : builder.childrenBuilders) {
            PersistentResourceXMLDescription child = b.build();
            this.children.add(child);
            this.marshallers.add(child);
        }
        this.children.addAll(builder.children);
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

    /**
     * Parse xml from provided <code>reader</code> and add resulting operations to passed list
     * @param reader xml reader to parse from
     * @param parentAddress address of the parent, used as base for all child elements
     * @param list list of operations where result will be put to.
     * @throws XMLStreamException if any error occurs while parsing
     */
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
            parseChildren(reader, parentAddress, list, new ModelNode());
        }
    }

    private void parseInternal(final XMLExtendedStreamReader reader, PathAddress parentAddress, List<ModelNode> list) throws XMLStreamException {
        ModelNode op = Util.createAddOperation();
        boolean wildcard = pathElement.isWildcard();
        String name = parseAttributeGroups(reader, op, wildcard);
        if (wildcard && name == null) {
            if (forcedName != null) {
                name = forcedName;
            } else {
                throw ControllerLogger.ROOT_LOGGER.missingRequiredAttributes(new StringBuilder(NAME), reader.getLocation());
            }
        }
        PathElement path = wildcard ? PathElement.pathElement(pathElement.getKey(), name) : pathElement;
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
                final String localName = reader.getLocalName();
                boolean element = attributeElements.containsKey(localName);
                //it can be a group or element attribute
                if (element || attributeGroups.contains(localName)) {
                    if (element) {
                        AttributeDefinition ad = attributeElements.get(localName);
                        getAttributeParser(ad).parseElement(ad, reader, op);
                        final String newLocalName = reader.getLocalName();
                        if (attributeGroups.contains(newLocalName)) {
                            parseGroup(reader, op, wildcard);
                        } else if (reader.isEndElement() && !attributeGroups.contains(newLocalName) && !attributeElements.containsKey(newLocalName)) {
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
            AttributeDefinition ad = groupAttrs.get(reader.getLocalName());
            if (ad != null) {
                getAttributeParser(ad).parseElement(ad, reader, op);
            } else {
                throw ParseUtils.unexpectedElement(reader);
            }
        }
    }

    private String parseAttributes(final XMLExtendedStreamReader reader, ModelNode op, Map<String, AttributeDefinition> attributes, boolean wildcard) throws XMLStreamException {
        String name = null;
        int attrCount = reader.getAttributeCount();
        for (int i = 0; i < attrCount; i++) {
            String attributeName = reader.getAttributeLocalName(i);
            String value = reader.getAttributeValue(i);
            if (wildcard && nameAttributeName.equals(attributeName)) {
                name = value;
            } else if (attributes.containsKey(attributeName)) {
                AttributeDefinition def = attributes.get(attributeName);
                AttributeParser parser = getAttributeParser(def);
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
                        getAttributeParser(ad).parseElement(ad, reader, op);
                    } else {
                        childAlreadyRead = true;
                        return name;  //this possibly means we only have children left, return so child handling logic can take over
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
        if (children.isEmpty()) {
            if (flushRequired && attributeGroups.isEmpty() && attributeElements.isEmpty()) {
                ParseUtils.requireNoContent(reader);
            }
            if (childAlreadyRead) {
                throw ParseUtils.unexpectedElement(reader);
            }
        } else {
            Map<String, PersistentResourceXMLDescription> children = getChildrenMap();
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
                        getAttributeParser(elementAd).parseElement(elementAd, reader, op);
                    } else if ((child = customChildParsers.get(localName)) != null) {
                        child.parse(reader, parentAddress, list);
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

    /**
     * persist decorator and than continue to children without touching the model
     */
    private void persistDecorator(XMLExtendedStreamWriter writer, ModelNode model) throws XMLStreamException {
       if (shouldWriteDecoratorAndElements(model)) {
           writer.writeStartElement(decoratorElement);
           persistChildren(writer, model);
           writer.writeEndElement();
       }
    }

    /**
     * @return true if any of children are defined in the model
     */
    private boolean shouldWriteDecoratorAndElements(ModelNode model) {
        for (PersistentResourceXMLDescription child : children) {
            //if we have child decorator, than we check its children, we only handle one level of nesting
            if (child.decoratorElement != null) {
                for (PersistentResourceXMLDescription decoratedChild : child.children) {
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

    private AttributeParser getAttributeParser(AttributeDefinition ad) {
        return attributeParsers.getOrDefault(ad.getXmlName(), ad.getParser());
    }

    private void marshallAttributes(XMLExtendedStreamWriter writer, ModelNode model, Collection<AttributeDefinition> attributes, String group) throws XMLStreamException {
        boolean started = false;

        //we sort attributes to make sure that attributes that marshall to elements are last
        List<AttributeDefinition> sortedAds = new ArrayList<>(attributes.size());
        List<AttributeDefinition> elementAds = null;
        for (AttributeDefinition ad : attributes) {
            if (getAttributeParser(ad).isParseAsElement()) {
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
            AttributeMarshaller marshaller = attributeMarshallers.getOrDefault(ad.getXmlName(), ad.getMarshaller());
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
        for (ResourceMarshaller child : marshallers) {
            child.persist(writer, model);
        }
    }

    /**
     * Creates builder for passed path element
     * @param pathElement for which we are creating builder
     * @return PersistentResourceXMLBuilder
     * @deprecated Use {@link Factory#builder(PathElement)} from {@link #factory(PersistentSubsystemSchema)} instead.
     */
    @Deprecated
    public static PersistentResourceXMLBuilder builder(final PathElement pathElement) {
        return new PersistentResourceXMLBuilder(pathElement);
    }

    /**
     * Creates builder for passed path element
     *
     * @param pathElement for which we are creating builder
     * @param namespaceURI xml namespace to use for this resource, usually used for top level elements such as subsystems
     * @return PersistentResourceXMLBuilder
     * @deprecated Use {@link Factory#builder(PathElement)} from {@link #factory(PersistentSubsystemSchema)} instead.
     */
    @Deprecated
    public static PersistentResourceXMLBuilder builder(final PathElement pathElement, final String namespaceURI) {
        return new PersistentResourceXMLBuilder(pathElement, namespaceURI);
    }

    /**
     * Creates builder for the given subsystem path and namespace.
     *
     * @param path a subsystem path element
     * @param namespace the subsystem namespace
     * @return a builder for creating a {@link PersistentResourceXMLDescription}.
     * @deprecated Use {@link Factory#builder(PathElement)} from {@link #factory(PersistentSubsystemSchema)} instead.
     */
    @Deprecated
    public static PersistentResourceXMLBuilder builder(PathElement path, Namespace namespace) {
        return new PersistentResourceXMLBuilder(path, namespace.getUri());
    }

    /**
     * Creates builder for passed path element
     *
     * @param elementName name of xml element that is used as decorator
     * @return PersistentResourceXMLBuilder
     * @since 4.0
     */
    public static PersistentResourceXMLBuilder decorator(final String elementName) {
        return new PersistentResourceXMLBuilder(PathElement.pathElement(elementName), null).setDecoratorGroup(elementName);
    }

    /**
     * Creates a factory for creating a {@link PersistentResourceXMLDescription} builders for the specified subsystem schema.
     * @param <S> the schema type
     * @param schema a subsystem schema
     * @return a factory for creating a {@link PersistentResourceXMLDescription} builders
     */
    public static <S extends PersistentSubsystemSchema<S>> Factory factory(PersistentSubsystemSchema<S> schema) {
        return new Factory() {
            @Override
            public Builder builder(ResourceRegistration registration) {
                if (!schema.enables(registration)) {
                    // If resource is not enabled for this schema, return a builder stub that returns a null description
                    return new Builder() {
                        @Override
                        public Builder addChild(PersistentResourceXMLDescription description) {
                            return this;
                        }

                        @Override
                        public Builder addAttribute(AttributeDefinition attribute) {
                            return this;
                        }

                        @Override
                        public Builder addAttribute(AttributeDefinition attribute, AttributeParser attributeParser, AttributeMarshaller attributeMarshaller) {
                            return this;
                        }

                        @Override
                        public Builder addAttributes(AttributeDefinition... attributes) {
                            return this;
                        }

                        @Override
                        public Builder addAttributes(Stream<? extends AttributeDefinition> attributes) {
                            return this;
                        }

                        @Override
                        public Builder addAttributes(Stream<? extends AttributeDefinition> attributes, AttributeParser attributeParser, AttributeMarshaller attributeMarshaller) {
                            return this;
                        }

                        @Override
                        public Builder setXmlWrapperElement(String xmlWrapperElement) {
                            return this;
                        }

                        @Override
                        public Builder setXmlElementName(String xmlElementName) {
                            return this;
                        }

                        @Override
                        public Builder setNoAddOperation(boolean noAddOperation) {
                            return this;
                        }

                        @Override
                        public Builder setAdditionalOperationsGenerator(AdditionalOperationsGenerator additionalOperationsGenerator) {
                            return this;
                        }

                        @Override
                        public Builder setUseElementsForGroups(boolean useElementsForGroups) {
                            return this;
                        }

                        @Override
                        public Builder setNameAttributeName(String nameAttributeName) {
                            return this;
                        }

                        @Override
                        public PersistentResourceXMLDescription build() {
                            return null;
                        }
                    };
                }
                PathElement path = registration.getPathElement();
                Builder builder = path.getKey().equals(ModelDescriptionConstants.SUBSYSTEM) ? PersistentResourceXMLDescription.builder(path, schema.getNamespace()) : PersistentResourceXMLDescription.builder(path);
                // Return decorated builder that filters its attributes
                return new Builder() {
                    @Override
                    public Builder addChild(PersistentResourceXMLDescription description) {
                        // Description might be null if this resource is not enabled by this schema
                        if (description != null) {
                            builder.addChild(description);
                        }
                        return this;
                    }

                    @Override
                    public Builder addAttribute(AttributeDefinition attribute) {
                        if (schema.enables(attribute)) {
                            builder.addAttribute(attribute);
                        }
                        return this;
                    }

                    @Override
                    public Builder addAttribute(AttributeDefinition attribute, AttributeParser attributeParser, AttributeMarshaller attributeMarshaller) {
                        if (schema.enables(attribute)) {
                            builder.addAttribute(attribute, attributeParser, attributeMarshaller);
                        }
                        return this;
                    }

                    @Override
                    public Builder addAttributes(AttributeDefinition... attributes) {
                        for (AttributeDefinition attribute : attributes) {
                            if (schema.enables(attribute)) {
                                builder.addAttribute(attribute);
                            }
                        }
                        return this;
                    }

                    @Override
                    public Builder addAttributes(Stream<? extends AttributeDefinition> attributes) {
                        builder.addAttributes(attributes.filter(schema::enables));
                        return this;
                    }

                    @Override
                    public Builder addAttributes(Stream<? extends AttributeDefinition> attributes, AttributeParser attributeParser, AttributeMarshaller attributeMarshaller) {
                        builder.addAttributes(attributes.filter(schema::enables), attributeParser, attributeMarshaller);
                        return this;
                    }

                    @Override
                    public Builder setXmlWrapperElement(String xmlWrapperElement) {
                        builder.setXmlElementName(xmlWrapperElement);
                        return this;
                    }

                    @Override
                    public Builder setXmlElementName(String xmlElementName) {
                        builder.setXmlElementName(xmlElementName);
                        return this;
                    }

                    @Override
                    public Builder setNoAddOperation(boolean noAddOperation) {
                        builder.setNoAddOperation(noAddOperation);
                        return this;
                    }

                    @Override
                    public Builder setAdditionalOperationsGenerator(AdditionalOperationsGenerator additionalOperationsGenerator) {
                        builder.setAdditionalOperationsGenerator(additionalOperationsGenerator);
                        return this;
                    }

                    @Override
                    public Builder setUseElementsForGroups(boolean useElementsForGroups) {
                        builder.setUseElementsForGroups(useElementsForGroups);
                        return this;
                    }

                    @Override
                    public Builder setNameAttributeName(String nameAttributeName) {
                        builder.setNameAttributeName(nameAttributeName);
                        return this;
                    }

                    @Override
                    public PersistentResourceXMLDescription build() {
                        return builder.build();
                    }
                };
            }
        };
    }

    /**
     * Factory for creating a {@link PersistentResourceXMLDescription} builder.
     */
    public static interface Factory {
        /**
         * Creates a builder for the resource registered at the specified path.
         * @param path a path element
         * @return a builder of a {@link PersistentResourceXMLDescription}
         */
        default Builder builder(PathElement path) {
            return builder(ResourceRegistration.of(path));
        }

        /**
         * Creates a builder for the specified resource registration.
         * @param path a resource registration
         * @return a builder of a {@link PersistentResourceXMLDescription}
         */
        Builder builder(ResourceRegistration registration);
    }

    /**
     * Builds a {@link PersistentResourceXMLDescription}.
     */
    public static interface Builder {
        Builder addChild(PersistentResourceXMLDescription description);

        Builder addAttribute(AttributeDefinition attribute);

        Builder addAttribute(AttributeDefinition attribute, AttributeParser attributeParser, AttributeMarshaller attributeMarshaller);

        Builder addAttributes(AttributeDefinition... attributes);

        Builder addAttributes(Stream<? extends AttributeDefinition> attributes);

        Builder addAttributes(Stream<? extends AttributeDefinition> attributes, AttributeParser attributeParser, AttributeMarshaller attributeMarshaller);

        Builder setXmlWrapperElement(String xmlWrapperElement);

        Builder setXmlElementName(String xmlElementName);

        Builder setNoAddOperation(boolean noAddOperation);

        Builder setAdditionalOperationsGenerator(AdditionalOperationsGenerator additionalOperationsGenerator);

        Builder setUseElementsForGroups(boolean useElementsForGroups);

        Builder setNameAttributeName(String nameAttributeName);

        PersistentResourceXMLDescription build();
    }

    public static final class PersistentResourceXMLBuilder implements Builder {
        private final PathElement pathElement;
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
        private final LinkedHashMap<String, ResourceParser> customChildParsers = new LinkedHashMap<>();
        private final LinkedList<ResourceMarshaller> marshallers = new LinkedList<>();
        private boolean useElementsForGroups = true;
        private String forcedName;
        private boolean marshallDefaultValues = true;
        private String nameAttributeName = NAME;
        private String decoratorElement = null;

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

        @Override
        public PersistentResourceXMLBuilder addChild(PersistentResourceXMLDescription description) {
            if (description != null) {
                this.children.add(description);
                this.marshallers.add(description);
            }
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

        @Override
        public PersistentResourceXMLBuilder addAttribute(AttributeDefinition attribute) {
            this.attributeList.add(attribute);
            return this;
        }

        public PersistentResourceXMLBuilder addAttribute(AttributeDefinition attribute, AttributeParser attributeParser) {
            this.attributeList.add(attribute);
            this.attributeParsers.put(attribute.getXmlName(), attributeParser);
            return this;
        }

        @Override
        public PersistentResourceXMLBuilder addAttribute(AttributeDefinition attribute, AttributeParser attributeParser, AttributeMarshaller attributeMarshaller) {
            this.attributeList.add(attribute);
            this.attributeParsers.put(attribute.getXmlName(), attributeParser);
            this.attributeMarshallers.put(attribute.getXmlName(), attributeMarshaller);
            return this;
        }

        @Override
        public PersistentResourceXMLBuilder addAttributes(AttributeDefinition... attributes) {
            Collections.addAll(this.attributeList, attributes);
            return this;
        }

        @Override
        public PersistentResourceXMLBuilder addAttributes(Stream<? extends AttributeDefinition> attributes) {
            attributes.forEach(this::addAttribute);
            return this;
        }

        @Override
        public PersistentResourceXMLBuilder addAttributes(Stream<? extends AttributeDefinition> attributes, AttributeParser parser, AttributeMarshaller attributeMarshaller) {
            attributes.forEach(attribute -> this.addAttribute(attribute, parser, attributeMarshaller));
            return this;
        }

        @Override
        public PersistentResourceXMLBuilder setXmlWrapperElement(final String xmlWrapperElement) {
            this.xmlWrapperElement = xmlWrapperElement;
            return this;
        }

        @Override
        public PersistentResourceXMLBuilder setXmlElementName(final String xmlElementName) {
            this.xmlElementName = xmlElementName;
            return this;
        }

        /**
         * @deprecated Use {@link #setNameAttributeName(String)}
         */
        @Deprecated(forRemoval = true)
        public PersistentResourceXMLBuilder setUseValueAsElementName(final boolean useValueAsElementName) {
            this.useValueAsElementName = useValueAsElementName;
            return this;
        }

        @Override
        @SuppressWarnings("unused")
        public PersistentResourceXMLBuilder setNoAddOperation(final boolean noAddOperation) {
            this.noAddOperation = noAddOperation;
            return this;
        }

        @Override
        @SuppressWarnings("unused")
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
         *
         * @deprecated Use an xml attribute to provide the name of the resource.
         */
        @Deprecated(forRemoval = true)
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
        @Override
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
        @Override
        public PersistentResourceXMLBuilder setNameAttributeName(String nameAttributeName) {
            this.nameAttributeName = nameAttributeName;
            return this;
        }

        private PersistentResourceXMLBuilder setDecoratorGroup(String elementName){
            this.decoratorElement = elementName;
            return this;
        }

        @Override
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
