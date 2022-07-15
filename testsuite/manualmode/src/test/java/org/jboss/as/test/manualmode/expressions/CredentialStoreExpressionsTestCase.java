/*
 * JBoss, Home of Professional Open Source
 * Copyright 2014, JBoss Inc., and individual contributors as indicated
 * by the @authors tag.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.test.manualmode.expressions;

import org.jboss.as.test.shared.SecureExpressionUtil;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ManagementClient;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.WildFlyRunner;


/**
 * @author <a href="mailto:kabir.khan@jboss.com">Kabir Khan</a>
 * @author Brian Stansberry
 */
@RunWith(WildFlyRunner.class)
@ServerControl(manual = true)
public class CredentialStoreExpressionsTestCase extends AbstractSecureExpressionsTestCase {

    private static final SecureExpressionUtil.SecureExpressionData EXPRESSION_DATA =
            new SecureExpressionUtil.SecureExpressionData(CLEAR_TEXT);
    static final String UNIQUE_NAME = "CredentialStoreExpressionsTestCase";
    static final String STORE_LOCATION = CredentialStoreExpressionsTestCase.class.getResource("/").getPath() + "security/" + UNIQUE_NAME + ".cs";

    private static final String STORE_NAME = CredentialStoreExpressionsTestCase.class.getSimpleName();

    @BeforeClass
    public static void setupServer() throws Exception {
        setupServerWithSecureExpressions(EXPRESSION_DATA, CredentialStoreExpressionsTestCase::setupFunction);
    }

    @AfterClass
    public static void tearDownServer() throws Exception {
        tearDownServer(CredentialStoreExpressionsTestCase::tearDownFunction);
    }

    static Void setupFunction(ManagementClient managementClient) throws Exception {
        SecureExpressionUtil.setupCredentialStoreExpressions(STORE_NAME, EXPRESSION_DATA);
        SecureExpressionUtil.setupCredentialStore(managementClient, STORE_NAME, STORE_LOCATION);
        return null;
    }

    static Void tearDownFunction(ManagementClient managementClient) throws Exception {
        SecureExpressionUtil.teardownCredentialStore(managementClient, STORE_NAME, STORE_LOCATION);
        return null;
    }

    @Override
    String getInvalidExpression() {
        return EXPRESSION_DATA.getExpression().replace(STORE_NAME, "notavalidresolver");
    }
}
