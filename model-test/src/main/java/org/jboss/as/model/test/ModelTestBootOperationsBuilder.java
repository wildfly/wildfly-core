/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.model.test;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

import javax.xml.stream.XMLStreamException;

import org.jboss.dmr.ModelNode;

/**
 * Internal class.
 * Used to create the boot operations.
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class ModelTestBootOperationsBuilder {
    private final Class<?> testClass;
    private final BootOperationParser xmlParser;
    private List<ModelNode> bootOperations = Collections.emptyList();
    private String subsystemXml;
    private String subsystemXmlResource;
    private boolean built;

    public ModelTestBootOperationsBuilder(Class<?> testClass, BootOperationParser xmlParser) {
        this.testClass = testClass;
        this.xmlParser = xmlParser;
    }

    public ModelTestBootOperationsBuilder setXmlResource(String resource) throws IOException, XMLStreamException {
        validateNotAlreadyBuilt();
        validateSubsystemConfig();
        this.subsystemXmlResource = resource;
        internalSetSubsystemXml(ModelTestUtils.readResource(testClass, resource));
        return this;
    }

    public ModelTestBootOperationsBuilder setXml(String subsystemXml) throws XMLStreamException {
        validateNotAlreadyBuilt();
        validateSubsystemConfig();
        this.subsystemXml = subsystemXml;
        bootOperations = xmlParser.parse(subsystemXml);
        return this;
    }

    public ModelTestBootOperationsBuilder setBootOperations(List<ModelNode> bootOperations) {
        validateNotAlreadyBuilt();
        validateSubsystemConfig();
        this.bootOperations = bootOperations;
        return this;
    }

    private void internalSetSubsystemXml(String subsystemXml) throws XMLStreamException {
        this.subsystemXml = subsystemXml;
        bootOperations = xmlParser.parse(subsystemXml);
    }

    private void validateSubsystemConfig() {
        if (subsystemXmlResource != null) {
            throw new IllegalArgumentException("Xml resource is already set");
        }
        if (subsystemXml != null) {
            throw new IllegalArgumentException("Xml string is already set");
        }
        if (bootOperations != Collections.EMPTY_LIST) {
            throw new IllegalArgumentException("Boot operations are already set");
        }
    }

    public void validateNotAlreadyBuilt() {
        if (built) {
            throw new IllegalStateException("Already built");
        }
    }

    public List<ModelNode> build() {
        built = true;
        return bootOperations;
    }

    public interface BootOperationParser {
        List<ModelNode> parse(String subsystemXml) throws XMLStreamException;
    }
}
