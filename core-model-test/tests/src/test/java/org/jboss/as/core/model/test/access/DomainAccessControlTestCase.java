/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.core.model.test.access;

import org.jboss.as.controller.access.constraint.ApplicationTypeConfig;
import org.jboss.as.controller.access.constraint.SensitivityClassification;
import org.jboss.as.controller.access.management.ApplicationTypeAccessConstraintDefinition;
import org.jboss.as.controller.access.management.SensitiveTargetAccessConstraintDefinition;
import org.jboss.as.core.model.test.AbstractCoreModelTest;
import org.jboss.as.core.model.test.KernelServices;
import org.jboss.as.core.model.test.TestModelType;
import org.jboss.as.model.test.ModelTestUtils;
import org.junit.Assert;
import org.junit.Test;

/**
 * Test of access control handling in the domain-wide model.
 *
 * @author Brian Stansberry (c) 2013 Red Hat Inc.
 */
public class DomainAccessControlTestCase extends AbstractCoreModelTest {

    @Test
    public void testConfiguration() throws Exception {
        //Initialize some additional constraints
        new SensitiveTargetAccessConstraintDefinition(new SensitivityClassification("play", "security-realm", true, true, true));
        new ApplicationTypeAccessConstraintDefinition(new ApplicationTypeConfig("play", "deployment", false));

        KernelServices kernelServices = createKernelServicesBuilder(TestModelType.DOMAIN)
                .setXmlResource("domain-all.xml")
                .validateDescription()
                .build();
        Assert.assertTrue(kernelServices.isSuccessfulBoot());

//        System.out.println(kernelServices.readWholeModel());

        String marshalled = kernelServices.getPersistedSubsystemXml();
        ModelTestUtils.compareXml(ModelTestUtils.readResource(this.getClass(), "domain-all.xml"), marshalled);
    }
}
