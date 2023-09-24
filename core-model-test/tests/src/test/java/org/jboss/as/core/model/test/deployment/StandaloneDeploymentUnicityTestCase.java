/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.core.model.test.deployment;

import javax.xml.stream.Location;
import javax.xml.stream.XMLStreamException;
import static org.hamcrest.CoreMatchers.containsString;

import org.hamcrest.MatcherAssert;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.core.model.test.AbstractCoreModelTest;
import org.jboss.as.core.model.test.KernelServices;
import org.jboss.as.core.model.test.TestModelType;
import org.jboss.as.model.test.ModelTestUtils;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class StandaloneDeploymentUnicityTestCase extends AbstractCoreModelTest {

    @Test
    public void testDeployments() throws Exception {
        KernelServices kernelServices = createKernelServicesBuilder(TestModelType.STANDALONE)
            .setXmlResource("standalone.xml")
            .createContentRepositoryContent("12345678901234567890")
            .build();
        Assert.assertTrue(kernelServices.isSuccessfulBoot());

        String marshalled = kernelServices.getPersistedSubsystemXml();
        ModelTestUtils.compareXml(ModelTestUtils.readResource(this.getClass(), "standalone.xml"), marshalled);
    }

    @Test
    public void testIncorrectDeployments() throws Exception {
        try { createKernelServicesBuilder(TestModelType.STANDALONE)
            .setXmlResource("standalone_duplicate.xml")
            .createContentRepositoryContent("12345678901234567890")
            .build();
        } catch (XMLStreamException ex) {
            String expectedMessage = ControllerLogger.ROOT_LOGGER.duplicateNamedElement("abc.war", new Location() {
                public int getLineNumber() {
                    return 287;
                }

                public int getColumnNumber() {
                    return 1;
                }

                public int getCharacterOffset() {
                    return 1;
                }

                public String getPublicId() {
                    return "";
                }

                public String getSystemId() {
                    return "";
                }
            }).getMessage();
            expectedMessage = expectedMessage.substring(expectedMessage.indexOf("WFLYCTL0073:"));
            MatcherAssert.assertThat(ex.getMessage(), containsString(expectedMessage));
        }
    }
}
