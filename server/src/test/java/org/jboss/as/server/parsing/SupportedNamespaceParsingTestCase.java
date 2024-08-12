/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.parsing;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;

import org.jboss.as.controller.parsing.ManagementXmlSchema;
import org.jboss.as.controller.parsing.Namespace;
import org.jboss.as.version.Stability;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.XMLMapper;
import org.junit.Test;
import org.projectodd.vdx.core.XMLStreamValidationException;

/**
 * Test case testing the handling on unsupported namespaces when parsing the configuration.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class SupportedNamespaceParsingTestCase {

    private static final String TEMPLATE = "<?xml version='1.0' encoding='UTF-8'?>" +
        "<server name=\"example\" xmlns=\"%s\">" +
        "</server>";

    private static final Set<Namespace> UNSUPPORTED_NS = EnumSet.of(Namespace.DOMAIN_1_0,
                                                                    Namespace.DOMAIN_1_1,
                                                                    Namespace.DOMAIN_1_2,
                                                                    Namespace.DOMAIN_1_3,
                                                                    Namespace.DOMAIN_1_4,
                                                                    Namespace.DOMAIN_1_5,
                                                                    Namespace.DOMAIN_1_6);

    @Test
    public void testNamespaceHandling() throws Exception {
        for (Namespace current : Namespace.ALL_NAMESPACES) {
            String xml = String.format(TEMPLATE, current.getUriString());
            final XMLStreamReader reader = XMLInputFactory.newInstance().createXMLStreamReader(new StringReader(xml));
            final StandaloneXmlSchemas standaloneXmlSchemas = new StandaloneXmlSchemas(Stability.DEFAULT, null, null, null);
            final ManagementXmlSchema parser = standaloneXmlSchemas.getCurrent();
            final List<ModelNode> operationList = new ArrayList<ModelNode>();
            final XMLMapper mapper = XMLMapper.Factory.create();

            mapper.registerRootElement(new QName(current.getUriString(), "server"), parser);

            if (UNSUPPORTED_NS.contains(current)) {
                try {
                    mapper.parseDocument(operationList, reader);
                    fail(String.format("Parsing expected to fail due to unsupported NS %s", current.getUriString()));
                } catch (XMLStreamValidationException e) {
                    assertTrue("Expected error should be WFLYCTL0513" , e.getMessage().contains("WFLYCTL0513"));
                }
            } else {
                // We expect no error if supported.
                mapper.parseDocument(operationList, reader);
            }
        }
    }

}
