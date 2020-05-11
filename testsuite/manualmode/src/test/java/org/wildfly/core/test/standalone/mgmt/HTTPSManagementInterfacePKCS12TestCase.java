/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2020 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.core.test.standalone.mgmt;

import org.jboss.as.test.categories.CommonCriteria;
import org.junit.BeforeClass;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.WildflyTestRunner;

/**
 * Same test than HTTPSManagementInterfaceTestCase but using PKCS12 certificates
 * instead of JKS.
 *
 * @author rmartinc
 */
@RunWith(WildflyTestRunner.class)
@ServerControl(manual = true)
@Category(CommonCriteria.class)
public class HTTPSManagementInterfacePKCS12TestCase extends HTTPSManagementInterfaceTestCase {

    @BeforeClass
    public static void startAndSetupContainer() throws Exception {
        HTTPSManagementInterfaceTestCase.startAndSetupContainer("PKCS12");
    }
}
