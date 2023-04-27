/*
 * Copyright 2022 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.extension.elytron;

import static jakarta.security.auth.message.config.AuthConfigFactory.DEFAULT_FACTORY_SECURITY_PROPERTY;

import java.security.Security;

import org.jboss.as.controller.Extension;
import org.jboss.as.subsystem.test.AbstractSubsystemBaseTest;
import org.junit.BeforeClass;

/**
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 * @author <a href="mailto:carodrig@redhat.com">Cameron Rodriguez</a>
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
