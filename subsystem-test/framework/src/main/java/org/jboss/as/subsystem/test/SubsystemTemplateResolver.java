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
package org.jboss.as.subsystem.test;

import static javax.xml.stream.XMLStreamConstants.CDATA;
import static javax.xml.stream.XMLStreamConstants.CHARACTERS;
import static javax.xml.stream.XMLStreamConstants.COMMENT;
import static javax.xml.stream.XMLStreamConstants.END_ELEMENT;
import static javax.xml.stream.XMLStreamConstants.PROCESSING_INSTRUCTION;
import static javax.xml.stream.XMLStreamConstants.START_DOCUMENT;
import static javax.xml.stream.XMLStreamConstants.START_ELEMENT;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.namespace.NamespaceContext;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;

import org.xnio.IoUtils;

/**
 * Tool to generate subsystem files from a subsystem template and a list of supplements.
 *
 * @author Kabir Khan
 */
public class SubsystemTemplateResolver {
    private final String subsystemName;
    private int count;

    private SubsystemTemplateResolver(String subsystemName) {
        this.subsystemName = subsystemName;
    }

    public static SubsystemTemplateResolver create(String subsystemName) throws Exception {
        //URL resource = SubsystemTemplateResolver.class.getResource(path);
        //System.err.println(resource);
        return new SubsystemTemplateResolver(subsystemName);
    }

    public List<String> resolveTemplates(List<String> templates) throws Exception {
        Path dir = Paths.get(".", "target", "test-classes", "generated-subsystems").toAbsolutePath().normalize();
        if (Files.exists(dir)) {
            Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        Files.createDirectories(dir);

        List<String> resolved = new ArrayList<>();
        for (String template : templates) {
            resolved.addAll(resolveTemplate(dir, template));
        }
        return resolved;
    }

    private List<String> resolveTemplate(Path dir, String path) throws Exception {
        final SubsystemParser parser;
        try (InputStream in = new BufferedInputStream(this.getClass().getResourceAsStream(path))) {
            parser = new SubsystemParser(null, in);
            parser.parse();
        }
        Set<String> supplementNames = new HashSet<>(parser.supplementReplacements.keySet());


        if (supplementNames.size() == 0) {
            return Collections.singletonList("/generated-subsystems/" + outputSubsystemFile(parser, dir));
        } else {
            List<String> generatedSubsystems = new ArrayList<>();
            for (String supplementName : supplementNames) {
                try (InputStream in = new BufferedInputStream(this.getClass().getResourceAsStream(path))) {
                    SubsystemParser supplementSubsystemParser = new SubsystemParser(supplementName, in);
                    supplementSubsystemParser.parse();
                    generatedSubsystems.add("/generated-subsystems/" + outputSubsystemFile(supplementSubsystemParser, dir));
                }
            }
            return generatedSubsystems;
        }
    }

    private String outputSubsystemFile(SubsystemParser parser, Path dir) throws Exception {
        Node node = parser.getSubsystem();


        final String xmlName = subsystemName + "-" + ++count + "-" + parser.supplementName + ".xml";
        final OutputStream out = new BufferedOutputStream(new FileOutputStream(dir.resolve(xmlName).toFile()));
        final XMLOutputFactory factory = XMLOutputFactory.newInstance();
        final XMLStreamWriter writer = factory.createXMLStreamWriter(out);
        try {
            node.marshall(writer);
        } finally {
            writer.flush();
            writer.close();
            IoUtils.safeClose(out);

        }
        return xmlName;
    }

    private static class NodeParser {

        private final String namespaceURI;

        public NodeParser() {
            this(null);
        }

        public NodeParser(String namespaceURI){
            this.namespaceURI = namespaceURI;
        }

        public ElementNode parseNode(XMLStreamReader reader, String nodeName) throws XMLStreamException {
            if (reader.getEventType() != START_ELEMENT) {
                throw new XMLStreamException("Expected START_ELEMENT", reader.getLocation());
            }
            if (!reader.getLocalName().equals(nodeName)) {
                throw new XMLStreamException("Expected <" + nodeName + ">", reader.getLocation());
            }

            ElementNode rootNode = createNodeWithAttributesAndNs(reader, null);
            ElementNode currentNode = rootNode;
            while (reader.hasNext()) {
                int type = reader.next();
                switch (type) {
                    case END_ELEMENT:
                        currentNode = currentNode.getParent();
                        String name = reader.getLocalName();
                        //TODO this looks wrong
                        if (name.equals(nodeName)) {
                            return rootNode;
                        }
                        break;
                    case START_ELEMENT:
                        ElementNode childNode = createNodeWithAttributesAndNs(reader, currentNode);
                        currentNode.addChild(childNode);
                        currentNode = childNode;
                        break;
                    case COMMENT:
                        String comment = reader.getText();
                        currentNode.addChild(new CommentNode(comment));
                        break;
                    case CDATA:
                        currentNode.addChild(new CDataNode(reader.getText()));
                        break;
                    case CHARACTERS:
                        if (!reader.isWhiteSpace()) {
                            currentNode.addChild(new TextNode(reader.getText()));
                        }
                        break;
                    case PROCESSING_INSTRUCTION:
                        ProcessingInstructionNode node = parseProcessingInstruction(reader, currentNode);
                        if (node != null) {
                            currentNode.addChild(node);
                        }

                        break;
                    default:
                        break;
                }
            }

            throw new XMLStreamException("Element was not terminated", reader.getLocation());
        }

        private ElementNode createNodeWithAttributesAndNs(XMLStreamReader reader, ElementNode parent) {
            String namespace = reader.getNamespaceURI() != null && reader.getNamespaceURI().length() > 0 ? reader.getNamespaceURI() : namespaceURI;

            ElementNode childNode = new ElementNode(parent, reader.getLocalName(), namespace);
            int count = reader.getAttributeCount();
            for (int i = 0 ; i < count ; i++) {
                String name = reader.getAttributeLocalName(i);
                String value = reader.getAttributeValue(i);
                childNode.addAttribute(name, createAttributeValue(value));
            }
            return childNode;
        }

        protected ProcessingInstructionNode parseProcessingInstruction(XMLStreamReader reader, ElementNode parent) throws XMLStreamException {
            return null;
        }

        protected Map<String, String> parseProcessingInstructionData(String data) {
            if (data == null) {
                return Collections.emptyMap();
            }

            Map<String, String> attributes = new HashMap<String, String>();
            StringBuilder builder = new StringBuilder();
            String name = null;

            final byte READ_NAME = 0x1;
            final byte ATTRIBUTE_START = 0x3;
            final byte ATTRIBUTE = 0x4;

            byte state = READ_NAME;
            char[] chars = data.toCharArray();
            for (int i = 0 ; i < chars.length ; i++) {
                char c = chars[i];
                if (state == READ_NAME) {
                    if (c == '=') {
                        state = ATTRIBUTE_START;
                        name = builder.toString();
                        builder = new StringBuilder();
                    } else if (c == ' ') {
                    } else {
                        builder.append(c);
                    }
                } else if (state == ATTRIBUTE_START) {
                    //open quote
                    state = ATTRIBUTE;
                } else if (state == ATTRIBUTE) {
                    if (c == '\"') {
                        attributes.put(name.toString(), builder.toString());
                        builder = new StringBuilder();
                        state = READ_NAME;
                    } else {
                        builder.append(c);
                    }
                }
            }
            return attributes;
        }

        protected AttributeValue createAttributeValue(String attributeValue) {
            return new AttributeValue(attributeValue);
        }
    }

    public class SubsystemParser extends NodeParser {

        private final InputStream inputStream;
        private final String supplementName;
        private String extensionModule;
        private Node subsystem;
        private final Map<String, Set<ProcessingInstructionNode>> supplementPlaceholders = new HashMap<String, Set<ProcessingInstructionNode>>();
        private final Map<String, Supplement> supplementReplacements = new HashMap<String, Supplement>();
        private final Map<String, List<AttributeValue>> attributesForReplacement = new HashMap<String, List<AttributeValue>>();

        SubsystemParser(String supplementName, InputStream inputStream){
            this.supplementName = supplementName;
            this.inputStream = inputStream;
        }



        private ElementNode createNodeWithAttributesAndNs(XMLStreamReader reader, ElementNode parent) {
            String namespace = reader.getNamespaceURI() != null && reader.getNamespaceURI().length() > 0 ? reader.getNamespaceURI() : null;

            ElementNode childNode = new ElementNode(parent, reader.getLocalName(), namespace);
            int count = reader.getAttributeCount();
            for (int i = 0 ; i < count ; i++) {
                String name = reader.getAttributeLocalName(i);
                String value = reader.getAttributeValue(i);
                childNode.addAttribute(name, createAttributeValue(value));
            }
            return childNode;
        }

        protected Map<String, String> parseProcessingInstructionData(String data) {
            if (data == null) {
                return Collections.emptyMap();
            }

            Map<String, String> attributes = new HashMap<String, String>();
            StringBuilder builder = new StringBuilder();
            String name = null;

            final byte READ_NAME = 0x1;
            final byte ATTRIBUTE_START = 0x3;
            final byte ATTRIBUTE = 0x4;

            byte state = READ_NAME;
            char[] chars = data.toCharArray();
            for (int i = 0 ; i < chars.length ; i++) {
                char c = chars[i];
                if (state == READ_NAME) {
                    if (c == '=') {
                        state = ATTRIBUTE_START;
                        name = builder.toString();
                        builder = new StringBuilder();
                    } else if (c == ' ') {
                    } else {
                        builder.append(c);
                    }
                } else if (state == ATTRIBUTE_START) {
                    //open quote
                    state = ATTRIBUTE;
                } else if (state == ATTRIBUTE) {
                    if (c == '\"') {
                        attributes.put(name.toString(), builder.toString());
                        builder = new StringBuilder();
                        state = READ_NAME;
                    } else {
                        builder.append(c);
                    }
                }
            }
            return attributes;
        }

        String getExtensionModule() {
            return extensionModule;
        }

        Node getSubsystem() {
            return subsystem;
        }

        void parse() throws IOException, XMLStreamException {
            try (InputStream in = inputStream) {
                XMLInputFactory factory = XMLInputFactory.newInstance();
                factory.setProperty(XMLInputFactory.IS_COALESCING, Boolean.FALSE);
                XMLStreamReader reader = factory.createXMLStreamReader(in);

                reader.require(START_DOCUMENT, null, null);
                Map<String, String> configAttributes = new HashMap<String, String>();
                configAttributes.put("default-supplement", null);
                ParsingUtils.getNextElement(reader, "config", configAttributes, false);
                extensionModule = ParsingUtils.getNextElement(reader, "extension-module", null, true);
                ParsingUtils.getNextElement(reader, "subsystem", null, false);
                subsystem = parseNode(reader, "subsystem");

                while (reader.hasNext()) {
                    if (reader.next() == START_ELEMENT) {
                        if (reader.getLocalName().equals("subsystem")) {
                            throw new XMLStreamException("Only one subsystem element is allowed", reader.getLocation());
                        } else if (reader.getLocalName().equals("supplement")) {
                            parseSupplement(reader, ((ElementNode)subsystem).getNamespace());
                        } else if (reader.getLocalName().equals("socket-binding")) {
                            //Skip the socket bindings
                        } else if (reader.getLocalName().equals("outbound-socket-binding")) {
                            //Skip the socket bindings
                        } else if (reader.getLocalName().equals("interface")) {
                            //Skip the interfaces
                        }
                    }
                }

                //Check for the default supplement name if no supplement is set
                String supplementName = this.supplementName;
                if (supplementName == null) {
                    supplementName = configAttributes.get("default-supplement");
                }

                if (supplementName != null) {
                    Supplement supplement = supplementReplacements.get(supplementName);
                    if (supplement == null) {
                        throw new IllegalStateException("No supplement called '" + supplementName + "' could be found to augment the subsystem configuration");
                    }
                    Map<String, ElementNode> nodeReplacements = supplement.getAllNodeReplacements();
                    for (Map.Entry<String, Set<ProcessingInstructionNode>> entry : supplementPlaceholders.entrySet()) {
                        ElementNode replacement = nodeReplacements.get(entry.getKey());
                        if (replacement != null) {
                            for (Iterator<Node> it = replacement.iterateChildren(); it.hasNext() ; ) {
                                Node node = it.next();
                                for (ProcessingInstructionNode processingInstructionNode : entry.getValue()) {
                                    processingInstructionNode.addDelegate(node);
                                }
                            }
                        }
                    }

                    Map<String, String> attributeReplacements = supplement.getAllAttributeReplacements();
                    for (Map.Entry<String, List<AttributeValue>> entry : attributesForReplacement.entrySet()) {
                        String replacement = attributeReplacements.get(entry.getKey());
                        if (replacement == null) {
                            throw new IllegalStateException("No replacement found for " + entry.getKey() + " in supplement " + supplementName);
                        }
                        for (AttributeValue attrValue : entry.getValue()) {
                            attrValue.setValue(replacement);
                        }
                    }
                }

            }
        }

        protected void parseSupplement(XMLStreamReader reader, String subsystemNs) throws XMLStreamException {
            String name = null;
            String[] includes = null;
            for (int i = 0 ; i < reader.getAttributeCount() ; i++) {
                String attr = reader.getAttributeLocalName(i);
                if (attr.equals("name")) {
                    name = reader.getAttributeValue(i);
                } else if (attr.equals("includes")){
                    String tmp = reader.getAttributeValue(i);
                    includes = tmp.split(" ");
                } else {
                    throw new XMLStreamException("Invalid attribute " + attr, reader.getLocation());
                }
            }
            if (name == null) {
                throw new XMLStreamException("Missing required attribute 'name'", reader.getLocation());
            }
            if (name.length() == 0) {
                throw new XMLStreamException("Empty name attribute for <supplement>", reader.getLocation());
            }

            Supplement supplement = new Supplement(includes);
            if (supplementReplacements.put(name, supplement) != null) {
                throw new XMLStreamException("Already have a supplement called " + name, reader.getLocation());
            }

            while (reader.hasNext()) {
                reader.next();
                int type = reader.getEventType();
                switch (type) {
                    case START_ELEMENT:
                        if (reader.getLocalName().equals("replacement")) {
                            parseSupplementReplacement(reader, subsystemNs, supplement);
                        } else {
                            throw new XMLStreamException("Unknown element " + reader.getLocalName(), reader.getLocation());
                        }
                        break;
                    case END_ELEMENT:
                        if (reader.getLocalName().equals("supplement")){
                            return;
                        } else {
                            throw new XMLStreamException("Unknown element " + reader.getLocalName(), reader.getLocation());
                        }
                }
            }
        }

        protected void parseSupplementReplacement(XMLStreamReader reader, String subsystemNs, Supplement supplement) throws XMLStreamException {
            String placeholder = null;
            String attributeValue = null;
            for (int i = 0 ; i < reader.getAttributeCount() ; i++) {
                String attr = reader.getAttributeLocalName(i);
                if (attr.equals("placeholder")) {
                    placeholder = reader.getAttributeValue(i);
                } else if (attr.equals("attributeValue")) {
                    attributeValue = reader.getAttributeValue(i);
                }else {
                    throw new XMLStreamException("Invalid attribute " + attr, reader.getLocation());
                }
            }
            if (placeholder == null) {
                throw new XMLStreamException("Missing required attribute 'placeholder'", reader.getLocation());
            }
            if (placeholder.length() == 0) {
                throw new XMLStreamException("Empty placeholder attribute for <replacement>", reader.getLocation());
            }

            if (attributeValue != null) {
                supplement.addAttributeReplacement(placeholder, attributeValue);
            }

            while (reader.hasNext()) {
                int type = reader.getEventType();
                switch (type) {
                    case START_ELEMENT:
                        ElementNode node = new NodeParser(subsystemNs).parseNode(reader, reader.getLocalName());
                        if (attributeValue != null && node.iterateChildren().hasNext()) {
                            throw new XMLStreamException("Can not have nested content when attributeValue is used", reader.getLocation());
                        }
                        if (supplement.addNodeReplacement(placeholder, node) != null) {
                            throw new XMLStreamException("Already have a replacement called " + placeholder + " in supplement", reader.getLocation());
                        }
                        break;
                    case END_ELEMENT:
                        if (reader.getLocalName().equals("replacement")){
                            return;
                        } else {
                            throw new XMLStreamException("Unknown element " + reader.getLocalName(), reader.getLocation());
                        }
                }
            }
        }

        protected ProcessingInstructionNode parseProcessingInstruction(XMLStreamReader reader, ElementNode parent) throws XMLStreamException {
            String name = reader.getPITarget();
            ProcessingInstructionNode placeholder = new ProcessingInstructionNode(name, parseProcessingInstructionData(reader.getPIData()));
            Set<ProcessingInstructionNode> processingInstructionNodes;
            if (supplementPlaceholders.containsKey(name)) {
                processingInstructionNodes = supplementPlaceholders.get(name);
            } else {
                processingInstructionNodes = new HashSet<ProcessingInstructionNode>();
            }
            processingInstructionNodes.add(placeholder);
            supplementPlaceholders.put(name, processingInstructionNodes);
            return placeholder;
        }


        protected AttributeValue createAttributeValue(String attributeValue) {

            AttributeValue value = new AttributeValue(attributeValue);
            if (attributeValue.startsWith("@@")) {
                List<AttributeValue> attributeValues = attributesForReplacement.get(attributeValue);
                if (attributeValues == null) {
                    attributeValues = new ArrayList<AttributeValue>();
                    attributesForReplacement.put(attributeValue, attributeValues);
                }
                attributeValues.add(value);
            }
            return value;
        }

        private class Supplement {
            final String[] includes;
            final Map<String, ElementNode> nodeReplacements = new HashMap<String, ElementNode>();
            final Map<String, String> attributeReplacements = new HashMap<String, String>();

            Supplement(String[] includes){
                this.includes = includes;
            }

            ElementNode addNodeReplacement(String placeholder, ElementNode replacement) {
                return nodeReplacements.put(placeholder, replacement);
            }

            String addAttributeReplacement(String placeholder, String replacement) {
                return attributeReplacements.put(placeholder, replacement);
            }

            Map<String, ElementNode> getAllNodeReplacements() {
                Map<String, ElementNode> result = new HashMap<String, ElementNode>();
                getAllNodeReplacements(result);
                return result;
            }

            void getAllNodeReplacements(Map<String, ElementNode> result) {
                if (includes != null && includes.length > 0) {
                    for (String include : includes) {
                        Supplement parent = supplementReplacements.get(include);
                        if (parent == null) {
                            throw new IllegalStateException("Can't find included supplement '" + include + "'");
                        }
                        parent.getAllNodeReplacements(result);
                    }
                }
                result.putAll(nodeReplacements);
            }

            Map<String, String> getAllAttributeReplacements() {
                Map<String, String> result = new HashMap<String, String>();
                getAllAttributeReplacements(result);
                return result;
            }

            void getAllAttributeReplacements(Map<String, String> result) {
                if (includes != null && includes.length > 0) {
                    for (String include : includes) {
                        Supplement parent = supplementReplacements.get(include);
                        if (parent == null) {
                            throw new IllegalStateException("Can't find included supplement '" + include + "'");
                        }
                        parent.getAllAttributeReplacements(result);
                    }
                }
                result.putAll(attributeReplacements);
            }
        }
    }

    private abstract static class Node {

        public abstract void marshall(XMLStreamWriter writer) throws XMLStreamException;

        public boolean hasContent() {
            return true;
        }
    }

    private static class AttributeValue {

        private String value;

        public AttributeValue(String value){
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

    private static class CDataNode extends Node {
        private final String cdata;

        public CDataNode(final String cdata) {
            this.cdata = cdata;
        }

        @Override
        public void marshall(XMLStreamWriter writer) throws XMLStreamException {
            writer.writeCData(cdata);
        }
    }

    private static class CommentNode extends Node {
        private final String comment;

        public CommentNode(final String comment) {
            this.comment = comment;
        }

        @Override
        public void marshall(XMLStreamWriter writer) throws XMLStreamException {
            writer.writeComment(comment);
        }
    }

    private static class ElementNode extends Node {

        private final ElementNode parent;
        private final String name;
        private final String namespace;
        private final Map<String, AttributeValue> attributes = new LinkedHashMap<String, AttributeValue>();
        private List<Node> children = new ArrayList<Node>();

        public ElementNode(final ElementNode parent, final String name) {
            this(parent, name, parent.getNamespace());
        }

        public ElementNode(final ElementNode parent, final String name, final String namespace) {
            this.parent = parent;
            this.name = name;
            this.namespace = namespace == null ? namespace : namespace.isEmpty() ? null : namespace;
        }

        public String getNamespace() {
            return namespace;
        }

        public String getName() {
            return name;
        }

        public void addAttribute(String name, AttributeValue value) {
            attributes.put(name, value);
        }

        public void addChild(Node child) {
            children.add(child);
        }

        public Iterator<Node> getChildren() {
            return children.iterator();
        }

        public ElementNode getParent() {
            return parent;
        }

        public Iterator<Node> iterateChildren(){
            return children.iterator();
        }

        public String getAttributeValue(String name) {
            AttributeValue av = attributes.get(name);
            if (av == null) {
                return null;
            }
            return av.getValue();
        }

        public String getAttributeValue(String name, String defaultValue) {
            String s = getAttributeValue(name);
            if (s == null) {
                return defaultValue;
            }
            return s;
        }

        @Override
        public void marshall(XMLStreamWriter writer) throws XMLStreamException {
//        boolean empty = false;//children.isEmpty()
            boolean empty = isEmpty();
            NamespaceContext context = writer.getNamespaceContext();
            String prefix = writer.getNamespaceContext().getPrefix(namespace);
            if (prefix == null) {
                // Unknown namespace; it becomes default
                writer.setDefaultNamespace(namespace);
                if (empty) {
                    writer.writeEmptyElement(name);
                }
                else {
                    writer.writeStartElement(name);
                }
                writer.writeNamespace(null, namespace);
            }
            else {
                if (empty) {
                    writer.writeEmptyElement(namespace, name);
                }
                else {
                    writer.writeStartElement(namespace, name);
                }
            }

            for (Map.Entry<String, AttributeValue> attr : attributes.entrySet()) {
                writer.writeAttribute(attr.getKey(), attr.getValue().getValue());
            }

            for (Node child : children) {
                child.marshall(writer);
            }

            if (!empty) {
                try {
                    writer.writeEndElement();
                } catch(XMLStreamException e) {
                    //TODO REMOVE THIS
                    throw e;
                }
            }
        }

        private boolean isEmpty() {
            if (parent == null) {
                return false;
            }
            if (children.isEmpty()) {
                return true;
            }
            for (Node child : children) {
                if (child.hasContent()) {
                    return false;
                }
            }
            return true;
        }

        public String toString() {
            return "Element(name=" + name + ",ns=" + namespace + ")";
        }
    }

    private static class TextNode extends Node {

        private final String text;

        public TextNode(final String text){
            this.text = text;
        }

        @Override
        public void marshall(XMLStreamWriter writer) throws XMLStreamException {
            writer.writeCharacters(text);
        }
    }

    private static class ProcessingInstructionNode extends Node {
        private final String name;
        private final Map<String, String> data;
        private List<Node> delegates = new ArrayList<Node>();

        public ProcessingInstructionNode(final String name, final Map<String, String> data) {
            this.name = name;
            this.data = data;
        }

        public void addDelegate(Node delegate) {
            if (delegate != null) {
                delegates.add(delegate);
            }
        }

        @Override
        public void marshall(XMLStreamWriter writer) throws XMLStreamException {
            for (Node delegate : delegates) {
                delegate.marshall(writer);
            }
        }

        public boolean hasContent() {
            for (Node delegate : delegates) {
                if (delegate.hasContent()) {
                    return true;
                }
            }
            return false;
        }

    }

    private static class ParsingUtils {

        public static String getNextElement(XMLStreamReader reader, String name, Map<String, String> attributes, boolean getElementText) throws XMLStreamException {
            if (!reader.hasNext()) {
                throw new XMLStreamException("Expected more elements", reader.getLocation());
            }
            int type = reader.next();
            while (reader.hasNext() && type != START_ELEMENT) {
                type = reader.next();
            }
            if (reader.getEventType() != START_ELEMENT) {
                throw new XMLStreamException("No <" + name + "> found");
            }
            if (!reader.getLocalName().equals("" + name + "")) {
                throw new XMLStreamException("<" + name + "> expected", reader.getLocation());
            }

            if (attributes != null) {
                for (int i = 0 ; i < reader.getAttributeCount() ; i++) {
                    String attr = reader.getAttributeLocalName(i);
                    if (!attributes.containsKey(attr)) {
                        throw new XMLStreamException("Unexpected attribute " + attr, reader.getLocation());
                    }
                    attributes.put(attr, reader.getAttributeValue(i));
                }
            }

            return getElementText ? reader.getElementText() : null;
        }
    }
}
