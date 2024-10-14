/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.subsystem.test.elementorder;

import org.jboss.as.subsystem.test.AbstractSubsystemBaseTest;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;

import static org.junit.Assert.fail;

/**
 *  Test for checking an XML configuration will be correctly parsed when the elements are written in non-default order if the schema allows it
 *  Default order = attribute elements before children elements
 */
public class ElementOrderSubsystemTestCase extends AbstractSubsystemBaseTest {

    public ElementOrderSubsystemTestCase() {
        super(ElementOrderSubsystemResourceDefinition.SUBSYSTEM_NAME, new ElementOrderSubsystemExtension());
    }

    @Override
    protected String getSubsystemXml() throws IOException {
        return readResource("element-order-1.0.xml");
    }

    @Override
    protected String getSubsystemXsdPath() {
        return "schema/wildfly-element-order_1_0.xsd";
    }

    @Override
    protected void compareXml(String configId, String original, String marshalled) {
        // do nothing, compareXml imposes the default order
    }

    @Override
    public void testSubsystem() throws Exception {
        try {
            super.testSubsystem();
        } catch (UnsupportedOperationException | XMLStreamException e) {
            fail("Parser failed: " + e.getMessage());
        }
    }
}
