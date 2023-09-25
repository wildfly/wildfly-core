/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
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
