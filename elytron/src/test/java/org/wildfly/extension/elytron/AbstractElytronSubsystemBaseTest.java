/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.elytron;

import static jakarta.security.auth.message.config.AuthConfigFactory.DEFAULT_FACTORY_SECURITY_PROPERTY;

import java.security.Security;

import org.jboss.as.controller.Extension;
import org.jboss.as.subsystem.test.AbstractSubsystemBaseTest;
import org.junit.BeforeClass;

/**
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
abstract class AbstractElytronSubsystemBaseTest extends AbstractSubsystemBaseTest {

    public AbstractElytronSubsystemBaseTest(String mainSubsystemName, Extension mainExtension) {
        super(mainSubsystemName, mainExtension);
    }

    @BeforeClass
    public static void transferSystemProperty() {
        String value = System.getProperty(DEFAULT_FACTORY_SECURITY_PROPERTY);
        if (value != null) {
            String securityValue = Security.getProperty(DEFAULT_FACTORY_SECURITY_PROPERTY);
            if (securityValue == null) {
                Security.setProperty(DEFAULT_FACTORY_SECURITY_PROPERTY, value);
            }
        }

    }

 }
