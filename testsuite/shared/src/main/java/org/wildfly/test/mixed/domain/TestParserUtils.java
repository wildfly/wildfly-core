/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.test.mixed.domain;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.jboss.as.controller.Extension;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.extension.ExtensionRegistry;
import org.jboss.as.subsystem.test.TestParser;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLMapper;

/**
 * Builder to create a test xml mapper for a subsystem's xml configuration in full WildFly's testsuite without
 * depending on implementation details from core.
 *
 * It will parse the given subsystem xml and wrap it in a root element with a namespace.
 *
 * @author Kabir Khan
 */
public class TestParserUtils {

    private final XMLMapper xmlMapper;
    private final String wrappedXml;

    private TestParserUtils(XMLMapper xmlMapper, String wrappedXml) {
        this.xmlMapper = xmlMapper;
        this.wrappedXml = wrappedXml;
    }

    /**
     * Parses the operations in the subsystem xml
     *
     * @return The list of operations
     * @throws XMLStreamException if an error occurred parsing the xml
     */
    public List<ModelNode> parseOperations() throws XMLStreamException {
        final XMLStreamReader reader = XMLInputFactory.newInstance().createXMLStreamReader(new StringReader(wrappedXml));
        final List<ModelNode> operations = new ArrayList<>();
        xmlMapper.parseDocument(operations, reader);
        return operations;
    }

    public static class Builder {
        private final Extension extension;
        private final String subsystemName;
        private final String subsystemXml;
        private String rootWrapperName = "test";
        private String namespace = "urn.org.jboss.test:1.0";

        //These are needed to initialise the extension registry used to initialise the parser, but should not have
        //any effect on the parsers themselves. If that assumption turns out to be wrong, we can add setters for these
        //later, making sure that we don't expose core implementation details (such as
        // RuntimeHostControllerInfoAccessor)
        private final ProcessType processType = ProcessType.HOST_CONTROLLER;
        private final RunningMode runningMode = RunningMode.NORMAL;

        public Builder(Extension extension, String subsystemName, String subsystemXml) {
            this.extension = extension;
            this.subsystemName = subsystemName;
            this.subsystemXml = subsystemXml;
        }

        /**
         * Sets the name of the root element, which will wrap the subsystem xml.
         * Defaults to {@code test} if not set.
         *
         * @param rootWrapperName the name of the root wrapper xml element
         * @return this builder
         */
        public Builder setRootWrapperName(String rootWrapperName) {
            this.rootWrapperName = rootWrapperName;
            return this;
        }

        /**
         * Sets the namespace of the root element, used to wrap the subsystem xml.
         * Defaults to {@code urn.org.jboss.test:1.0} if not set.
         *
         * @param namespace the name of the root wrapper xml element namespace
         *
         */
        public Builder setNamespace(String namespace) {
            this.namespace = namespace;
            return this;
        }

        /**
         * Builds the utils needed to parse the document
         *
         * @return the test parser utils
         */
        public TestParserUtils build() {
            XMLMapper xmlMapper = XMLMapper.Factory.create();
            ExtensionRegistry extensionParsingRegistry = ExtensionRegistry.builder(this.processType).withRunningMode(this.runningMode).build();
            TestParser testParser = new TestParser(subsystemName, extensionParsingRegistry);
            xmlMapper.registerRootElement(new QName(namespace, "test"), testParser);
            extension.initializeParsers(extensionParsingRegistry.getExtensionParsingContext("Test", xmlMapper));

            String wrappedXml = "<" + rootWrapperName + " xmlns=\"" + namespace + "\">" +
                    subsystemXml +
                    "</test>";

            return new TestParserUtils(xmlMapper, wrappedXml);
        }
    }


}
