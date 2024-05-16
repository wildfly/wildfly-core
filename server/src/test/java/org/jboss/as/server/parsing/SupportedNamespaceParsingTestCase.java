/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.parsing;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.jboss.as.controller.parsing.ManagementXmlSchema;
import org.jboss.as.version.Stability;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLMapper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.projectodd.vdx.core.XMLStreamValidationException;

/**
 * Test case testing the handling on unsupported namespaces when parsing the configuration.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
@RunWith(Parameterized.class)
public class SupportedNamespaceParsingTestCase {

    private static final String TEMPLATE = "<?xml version='1.0' encoding='UTF-8'?>" +
        "<server name=\"example\" xmlns=\"%s\">" +
        "</server>";

    private static final Set<String> UNSUPPORTED_NS = Set.of("urn:jboss:domain:1.0",
                                                                    "urn:jboss:domain:1.1",
                                                                    "urn:jboss:domain:1.2",
                                                                    "urn:jboss:domain:1.3",
                                                                    "urn:jboss:domain:1.4",
                                                                    "urn:jboss:domain:1.5",
                                                                    "urn:jboss:domain:1.6");

    private final Stability testStability;

    public SupportedNamespaceParsingTestCase(Stability testStability) {
        this.testStability = testStability;
    }

    @Parameterized.Parameters
    public static Iterable<Stability> stabilityLevels() {
        return Arrays.asList(Stability.DEFAULT, Stability.COMMUNITY, Stability.PREVIEW, Stability.EXPERIMENTAL);
    }

    @Test
    public void testNamespaceHandling() throws Exception {
        final StandaloneXmlSchemas standaloneXmlSchemas = new StandaloneXmlSchemas(testStability, null, null, null);
        Set<ManagementXmlSchema> schemas = new HashSet<>();
        schemas.add(standaloneXmlSchemas.getCurrent());
        schemas.addAll(standaloneXmlSchemas.getAdditional());


        for (ManagementXmlSchema current : schemas) {
            String currentNamespace = current.getNamespace().getUri();
            String xml = String.format(TEMPLATE, currentNamespace);
            final XMLStreamReader reader = XMLInputFactory.newInstance().createXMLStreamReader(new StringReader(xml));

            final List<ModelNode> operationList = new ArrayList<ModelNode>();
            final XMLMapper mapper = XMLMapper.Factory.create();

            mapper.registerRootElement(current.getQualifiedName(), current);

            if (UNSUPPORTED_NS.contains(currentNamespace)) {
                try {
                    mapper.parseDocument(operationList, reader);
                    fail(String.format("Parsing expected to fail due to unsupported NS %s", currentNamespace));
                } catch (XMLStreamValidationException e) {
                    assertTrue("Expected error should be WFLYCTL0513" , e.getMessage().contains("WFLYCTL0513"));
                }
            } else if (testStability.enables(current.getStability())) {
                // We expect no error if supported
                mapper.parseDocument(operationList, reader);
            } else {
                try {
                    mapper.parseDocument(operationList, reader);
                    fail(String.format("Parsing expected to fail due to unsupported stability level %s", currentNamespace));
                } catch (XMLStreamException e) {
                    assertTrue("Expected error should be WFLYCTL0514" , e.getMessage().contains("WFLYCTL0514"));
                }
            }
        }
    }

}
