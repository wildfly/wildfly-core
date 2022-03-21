/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2021 Red Hat, Inc., and individual contributors
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

package org.jboss.as.server.operations;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.jboss.as.controller.ExpressionResolver;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ValueExpression;
import org.junit.Test;

/**
 * Tests of the {@link SystemPropertyAddHandler} class.
 */
public class SystemPropertyAddHandlerUnitTestCase {

    private static final String text = "text";

    @Test
    public void testDeferredProcessor() {


        // Mock resolver that reads a map to decide what to do; we manipulate the map content
        // to drive test scenarios
        final Map<String, Object> map = new HashMap<>();
        final ExpressionResolver resolver = new ExpressionResolver() {
            @Override
            public ModelNode resolveExpressions(ModelNode node) throws OperationFailedException {
                String key = node.asExpression().getExpressionString();
                Object val = map.get(key);
                if (val instanceof String) {
                    return new ModelNode((String) val);
                } else if (val instanceof OperationFailedException) {
                    throw (OperationFailedException) val;
                } else if (val != null) {
                    throw (RuntimeException) val;
                }
                throw new ExpressionResolutionServerException(key);
            }
        };

        SystemPropertyAddHandler.DeferredProcessor testee = new SystemPropertyAddHandler.DeferredProcessor(null);

        // Add 5 properties to the DeferredProcessor under test
        // 1 resolves in processDeferredProperty
        // 1 resolves in first call to processDeferredProperties
        // 1 resolves in second call to processDeferredProperties
        // 2 don't resolve

        String keyA = "${A}";
        String keyB = "${B}";
        String keyC = "${C}";
        String keyD = "${D}";
        String keyE = "${E}";
        ModelNode modelA = new ModelNode();
        modelA.get("value").set(new ValueExpression(keyA));
        testee.addDeferredProcessee("A", modelA, null);
        ModelNode modelB = new ModelNode();
        modelB.get("value").set(new ValueExpression(keyB));
        testee.addDeferredProcessee("B", modelB, new OperationFailedException(keyB));
        ModelNode modelC = new ModelNode();
        modelC.get("value").set(new ValueExpression(keyC));
        testee.addDeferredProcessee("C", modelC, new ExpressionResolver.ExpressionResolutionUserException(keyC));
        ModelNode modelD = new ModelNode();
        modelD.get("value").set(new ValueExpression(keyD));
        testee.addDeferredProcessee("D", modelD, new ExpressionResolver.ExpressionResolutionServerException(keyD));
        ModelNode modelE = new ModelNode();
        modelE.get("value").set(new ValueExpression(keyE));
        testee.addDeferredProcessee("E", modelE, new OperationFailedException(keyE));

        // Validate setup
        validateDeferredProcessee(testee.getUnresolved("A"), null, false);
        validateDeferredProcessee(testee.getUnresolved("B"), null, true);
        validateDeferredProcessee(testee.getUnresolved("C"), ExpressionResolver.ExpressionResolutionUserException.class, false);
        validateDeferredProcessee(testee.getUnresolved("D"), ExpressionResolver.ExpressionResolutionServerException.class, false);
        validateDeferredProcessee(testee.getUnresolved("E"), null, true);

        // Round of Stage.RUNTIME resolution
        map.put("${A}", "Avalue");
        map.put("${B}", new ExpressionResolver.ExpressionResolutionServerException("B"));
        map.put("${C}", ":C");
        map.put("${D}", new OperationFailedException("D"));
        map.put("${E}", new ExpressionResolver.ExpressionResolutionUserException("E"));

        testee.processDeferredPropertyAtRuntime("A", resolver);
        testee.processDeferredPropertyAtRuntime("B", resolver);
        // Don't invoke for C
        //testee.processDeferredPropertyAtRuntime("C", resolver);
        testee.processDeferredPropertyAtRuntime("D", resolver);
        testee.processDeferredPropertyAtRuntime("E", resolver);

        assertEquals("Avalue", System.getProperty("A"));
        assertNull(testee.getUnresolved("A"));
        validateDeferredProcessee(testee.getUnresolved("B"), ExpressionResolver.ExpressionResolutionServerException.class, true);
        validateDeferredProcessee(testee.getUnresolved("C"), ExpressionResolver.ExpressionResolutionUserException.class, false);
        validateDeferredProcessee(testee.getUnresolved("D"), null, true);
        assertSame(map.get("${D}"), testee.getUnresolved("D").getOperationFailedException());
        validateDeferredProcessee(testee.getUnresolved("E"), ExpressionResolver.ExpressionResolutionUserException.class, false);

        // First call to processDeferredProperties
        map.remove("${A}");
        map.put("${B}", new OperationFailedException("B"));
        map.put("${C}", "Cvalue");
        map.put("${D}", new ExpressionResolver.ExpressionResolutionServerException("D"));
        map.put("${E}", new ExpressionResolver.ExpressionResolutionServerException("E"));
        testee.processDeferredProperties(resolver);

        assertEquals("Avalue", System.getProperty("A"));
        assertNull(testee.getUnresolved("A"));
        validateDeferredProcessee(testee.getUnresolved("B"), null, true);
        assertSame(map.get("${B}"), testee.getUnresolved("B").getOperationFailedException());
        assertEquals("Cvalue", System.getProperty("C"));
        assertNull(testee.getUnresolved("C"));
        validateDeferredProcessee(testee.getUnresolved("D"), ExpressionResolver.ExpressionResolutionServerException.class, true);
        validateDeferredProcessee(testee.getUnresolved("E"), ExpressionResolver.ExpressionResolutionServerException.class, false);



    }

    private static void validateDeferredProcessee(SystemPropertyAddHandler.DeferredProcesee dp,
                                                  Class<? extends RuntimeException> reClazz, boolean expectOFE) {
        assertNotNull(dp);
        if (reClazz != null) {
            assertTrue(dp.getRuntimeException().getClass().isAssignableFrom(reClazz));
        } else {
            assertNull(dp.getRuntimeException());
        }
        if (expectOFE) {
            assertNotNull(dp.getOperationFailedException());
        } else {
            assertNull(dp.getOperationFailedException());
        }
    }
}
