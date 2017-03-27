/*
Copyright 2017 Red Hat, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */

package org.jboss.as.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.VaultReader;
import org.jboss.as.server.services.security.RuntimeVaultReader;
import org.jboss.as.server.services.security.RuntimeVaultReaderUnitTestCase;
import org.jboss.as.server.services.security.VaultHandler;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.ValueExpression;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

/**
 * Unit tests of {@link RuntimeExpressionResolver}.
 *
 * @author Brian Stansberry
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class RuntimeExpressionResolverUnitTestCase {

    private static final ValueExpression EXPRESSION = new ValueExpression("${" + RuntimeVaultReaderUnitTestCase.VAULTED_DATA + "}");
    private static final String NON_EXPRESSION = "abc";
    private static final ValueExpression NON_VAULT_EXPR = new ValueExpression("${abc:ok}");

    private static VaultHandler vaultHandler;

    @BeforeClass
    public static void beforeClass() {
        vaultHandler = new VaultHandler();
    }

    @AfterClass
    public static void afterClass() {
        vaultHandler.cleanUp();
    }

    @Test
    public void testANoVaultReader() {
        RuntimeExpressionResolver testee = new RuntimeExpressionResolver(null);
        ModelNode value = getVaultedData();
        try {
            testee.resolveExpressions(value);
            fail("Did not fail; got " + value);
        } catch (OperationFailedException good) {
            // good
        }
    }

    @Test
    public void testBNonExpression() throws OperationFailedException {
        RuntimeExpressionResolver testee = new RuntimeExpressionResolver(getVaultReader());
        ModelNode value = new ModelNode(NON_EXPRESSION);
        assertEquals(value, testee.resolveExpressions(value));
    }

    @Test
    public void testCNonVault() throws OperationFailedException {
        RuntimeExpressionResolver testee = new RuntimeExpressionResolver(getVaultReader());
        ModelNode value = new ModelNode(NON_VAULT_EXPR);
        ModelNode resolved = testee.resolveExpressions(value);
        assertEquals(resolved.toString(), ModelType.STRING, resolved.getType());
        assertEquals("ok", resolved.asString());
    }

    @Test
    public void testDNonExpressionNoVaultReader() throws OperationFailedException {
        RuntimeExpressionResolver testee = new RuntimeExpressionResolver(null);
        ModelNode value = new ModelNode(NON_EXPRESSION);
        assertEquals(value, testee.resolveExpressions(value));
    }

    @Test
    public void testENonVaultNoVaultReader() throws OperationFailedException {
        RuntimeExpressionResolver testee = new RuntimeExpressionResolver(null);
        ModelNode value = new ModelNode(NON_VAULT_EXPR);
        ModelNode resolved = testee.resolveExpressions(value);
        assertEquals(resolved.toString(), ModelType.STRING, resolved.getType());
        assertEquals("ok", resolved.asString());
    }

    @Test
    public void testFMissedLookup() {
        RuntimeExpressionResolver testee = new RuntimeExpressionResolver(getVaultReader());
        ModelNode value = getVaultedData();
        try {
            testee.resolveExpressions(value);
            fail("Did not fail; got " + value);
        } catch (OperationFailedException good) {
            // good
        }

    }

    @Test
    public void testGGoodLookup() throws Exception {
        RuntimeExpressionResolver testee = new RuntimeExpressionResolver(getVaultReader());
        RuntimeVaultReaderUnitTestCase.storeSecret(vaultHandler);
        ModelNode resolved = testee.resolveExpressions(getVaultedData());
        assertEquals(resolved.toString(), ModelType.STRING, resolved.getType());
        assertEquals(RuntimeVaultReaderUnitTestCase.SECRET, resolved.asString());
    }

    private static ModelNode getVaultedData() {
        return new ModelNode(EXPRESSION);
    }

    private static VaultReader getVaultReader() {
        RuntimeVaultReader result = new RuntimeVaultReader();
        RuntimeVaultReaderUnitTestCase.createVault(result, vaultHandler);
        return result;
    }
}
