/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.model.test;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Trivial tests for {@link ModelTestControllerVersion} to prevent accidental breaking of the method contract.
 *
 * @author Radoslav Husar
 */
public class ModelTestControllerVersionTest {

    @Test
    public void testCreateGAV() {
        // Test a released EAP version
        String eapGav = ModelTestControllerVersion.EAP_8_0_0.createGAV("wildfly-artifact");
        assertEquals("org.jboss.eap:wildfly-artifact:8.0.0.GA-redhat-00011", eapGav);

        // Test a WildFly version
        String wildflyGav = ModelTestControllerVersion.WILDFLY_31_0_0.createGAV("wildfly-artifact");
        assertEquals("org.wildfly:wildfly-artifact:31.0.0.Final", wildflyGav);
    }

    @Test
    public void testCreateCoreGAV() {
        // Test a released EAP version
        String eapCoreGav = ModelTestControllerVersion.EAP_8_0_0.createCoreGAV("wildfly-artifact");
        assertEquals("org.wildfly.core:wildfly-artifact:21.0.5.Final-redhat-00001", eapCoreGav);

        // Test a WildFly version
        String wildflyCoreGav = ModelTestControllerVersion.WILDFLY_31_0_0.createCoreGAV("wildfly-artifact");
        assertEquals("org.wildfly.core:wildfly-artifact:23.0.1.Final", wildflyCoreGav);
    }

}
