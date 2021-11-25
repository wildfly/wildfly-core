/*
* JBoss, Home of Professional Open Source.
* Copyright 2011, Red Hat Middleware LLC, and individual contributors
* as indicated by the @author tags. See the copyright.txt file in the
* distribution for a full listing of individual contributors.
*
* This is free software; you can redistribute it and/or modify it
* under the terms of the GNU Lesser General Public License as
* published by the Free Software Foundation; either version 2.1 of
* the License, or (at your option) any later version.
*
* This software is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
* Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public
* License along with this software; if not, write to the Free
* Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
* 02110-1301 USA, or see the FSF site: http://www.fsf.org.
*/
package org.jboss.as.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.File;

import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.ValueExpression;
import org.junit.Test;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class ExpressionResolverUnitTestCase {

    @Test(expected = ExpressionResolver.ExpressionResolutionUserException.class)
    public void testDefaultExpressionResolverWithNoResolutions() throws OperationFailedException {
        ModelNode unresolved = createModelNode();
        ExpressionResolver.TEST_RESOLVER.resolveExpressions(unresolved);
        fail("Did not fail with ERUE: " + unresolved);
    }

    @Test
    public void testDefaultExpressionResolverWithRecursiveSystemPropertyResolutions() throws OperationFailedException {
        System.setProperty("test.prop.expr", "EXPR");
        System.setProperty("test.prop.b", "B");
        System.setProperty("test.prop.c", "C");
        System.setProperty("test.prop.two", "TWO");
        System.setProperty("test.prop.three", "THREE");

        // recursive example
        System.setProperty("test.prop.prop", "${test.prop.prop.intermediate}");
        System.setProperty("test.prop.prop.intermediate", "PROP");

        // recursive example with a property expression as the default
        System.setProperty("test.prop.expr", "${NOTHERE:${ISHERE}}");
        System.setProperty("ISHERE", "EXPR");

        //PROP
        try {
            ModelNode node = ExpressionResolver.TEST_RESOLVER.resolveExpressions(createModelNode());
            checkResolved(node);
        } finally {
            System.clearProperty("test.prop.expr");
            System.clearProperty("test.prop.b");
            System.clearProperty("test.prop.c");
            System.clearProperty("test.prop.two");
            System.clearProperty("test.prop.three");
            System.clearProperty("test.prop.prop");
        }
    }

    @Test
    public void testDefaultExpressionResolverWithSystemPropertyResolutions() throws OperationFailedException {
        System.setProperty("test.prop.expr", "EXPR");
        System.setProperty("test.prop.b", "B");
        System.setProperty("test.prop.c", "C");
        System.setProperty("test.prop.two", "TWO");
        System.setProperty("test.prop.three", "THREE");
        System.setProperty("test.prop.prop", "PROP");
        try {
            ModelNode node = ExpressionResolver.TEST_RESOLVER.resolveExpressions(createModelNode());
            checkResolved(node);
        } finally {
            System.clearProperty("test.prop.expr");
            System.clearProperty("test.prop.b");
            System.clearProperty("test.prop.c");
            System.clearProperty("test.prop.two");
            System.clearProperty("test.prop.three");
            System.clearProperty("test.prop.prop");
        }
    }

    @Test
    public void testPluggableExpressionResolverRecursive() throws OperationFailedException {
        ModelNode node = new ExpressionResolverImpl() {
            @Override
            protected void resolvePluggableExpression(ModelNode node, OperationContext context) {
                String s = node.asString();
                if (s.equals("${test.prop.expr}")) {
                    node.set("${test.prop.expr.inner}");
                } else if (s.equals("${test.prop.expr.inner}")) {
                    node.set("EXPR");
                } else if (s.equals("${test.prop.b}")) {
                    node.set("B");
                } else if (s.equals("${test.prop.c}")) {
                    node.set("C");
                } else if (s.equals("${test.prop.two}")) {
                    node.set("TWO");
                } else if (s.equals("${test.prop.three}")) {
                    node.set("THREE");
                } else if (s.equals("${test.prop.prop}")) {
                    node.set("PROP");
                }
            }

        }.resolveExpressions(createModelNode());

        checkResolved(node);
    }

    @Test
    public void testPluggableExpressionResolver() throws OperationFailedException {
        ModelNode node = new ExpressionResolverImpl() {
            @Override
            protected void resolvePluggableExpression(ModelNode node, OperationContext context) {
                String s = node.asString();
                if (s.equals("${test.prop.expr}")) {
                    node.set("EXPR");
                } else if (s.equals("${test.prop.b}")) {
                    node.set("B");
                } else if (s.equals("${test.prop.c}")) {
                    node.set("C");
                } else if (s.equals("${test.prop.two}")) {
                    node.set("TWO");
                } else if (s.equals("${test.prop.three}")) {
                    node.set("THREE");
                } else if (s.equals("${test.prop.prop}")) {
                    node.set("PROP");
                }
            }

        }.resolveExpressions(createModelNode());

        checkResolved(node);
    }

    @Test(expected = ExpressionResolver.ExpressionResolutionUserException.class)
    public void testPluggableExpressionResolverNotResolved() throws OperationFailedException {
        ModelNode unresolved = createModelNode();
        new ExpressionResolverImpl() {
            @Override
            protected void resolvePluggableExpression(ModelNode node, OperationContext context) {
            }

        }.resolveExpressions(unresolved);

        fail("Did not fail with ERUE: " + unresolved);
    }

    @Test
    public void testPluggableExpressionResolverSomeResolvedAndSomeByDefault() throws OperationFailedException {
        System.setProperty("test.prop.c", "C");
        System.setProperty("test.prop.three", "THREE");
        System.setProperty("test.prop.prop", "PROP");
        try {
            ModelNode node = new ExpressionResolverImpl() {
                @Override
                protected void resolvePluggableExpression(ModelNode node, OperationContext context) {
                    String s = node.asString();
                    if (s.equals("${test.prop.expr}")) {
                        node.set("EXPR");
                    } else if (s.equals("${test.prop.b}")) {
                        node.set("B");
                    } else if (s.equals("${test.prop.two}")) {
                        node.set("TWO");
                    }
                }

            }.resolveExpressions(createModelNode());

            checkResolved(node);
        } finally {
            System.clearProperty("test.prop.c");
            System.clearProperty("test.prop.three");
            System.clearProperty("test.prop.prop");
        }
    }

    @Test
    public void testSimpleLenientResolver() {

        ModelNode input = createModelNode();
        input.get("defaulted").set(new ValueExpression("${test.default:default}"));
        ModelNode node = new ModelNode();
        try {
            node = ExpressionResolver.SIMPLE_LENIENT.resolveExpressions(input);
        } catch (OperationFailedException ofe) {
            fail("Should not have thrown OFE: " + ofe.toString());
        }

        assertEquals(7, node.keys().size());
        assertEquals(1, node.get("int").asInt());
        assertEquals(new ValueExpression("${test.prop.expr}"), node.get("expr").asExpression());
        assertEquals(3, node.get("map").keys().size());

        assertEquals("a", node.get("map", "plain").asString());
        assertEquals(new ValueExpression("${test.prop.b}"), node.get("map", "prop.b").asExpression());
        assertEquals(new ValueExpression("${test.prop.c}"), node.get("map", "prop.c").asExpression());

        assertEquals(3, node.get("list").asList().size());
        assertEquals("one", node.get("list").asList().get(0).asString());
        assertEquals(new ValueExpression("${test.prop.two}"), node.get("list").asList().get(1).asExpression());
        assertEquals(new ValueExpression("${test.prop.three}"), node.get("list").asList().get(2).asExpression());

        assertEquals("plain", node.get("plainprop").asProperty().getValue().asString());
        assertEquals(new ValueExpression("${test.prop.prop}"), node.get("prop").asProperty().getValue().asExpression());

        assertEquals("default", node.get("defaulted").asString());
    }

    @Test
    public void testPluggableExpressionResolverNestedExpression() throws OperationFailedException {
        System.setProperty("test.prop.nested", "expr");
        try {
            ModelNode node = new ExpressionResolverImpl() {
                @Override
                protected void resolvePluggableExpression(ModelNode node, OperationContext context) {
                    String s = node.asString();
                    if (s.equals("${test.prop.expr}")) {
                        node.set("EXPR");
                    }
                }

            }.resolveExpressions(new ModelNode(new ValueExpression("${test.prop.${test.prop.nested}}")));

            assertEquals("EXPR", node.asString());
        } finally {
            System.clearProperty("test.prop.nested");
        }
    }

    @Test
    public void testNestedExpressions() throws OperationFailedException {
        System.setProperty("foo", "FOO");
        System.setProperty("bar", "BAR");
        System.setProperty("baz", "BAZ");
        System.setProperty("FOO", "oof");
        System.setProperty("BAR", "rab");
        System.setProperty("BAZ", "zab");
        System.setProperty("foo.BAZ.BAR", "FOO.baz.bar");
        System.setProperty("foo.BAZBAR", "FOO.bazbar");
        System.setProperty("bazBAR", "BAZbar");
        System.setProperty("fooBAZbar", "FOObazBAR");
        try {
            ModelNode node = ExpressionResolver.TEST_RESOLVER.resolveExpressions(expression("${foo:${bar}}"));
            assertEquals("FOO", node.asString());

            node = ExpressionResolver.TEST_RESOLVER.resolveExpressions(expression("${${bar}}"));
            assertEquals("rab", node.asString());

            node = ExpressionResolver.TEST_RESOLVER.resolveExpressions(expression("${foo.${baz}.${bar}}"));
            assertEquals("FOO.baz.bar", node.asString());

            node = ExpressionResolver.TEST_RESOLVER.resolveExpressions(expression("${foo.${baz}${bar}}"));
            assertEquals("FOO.bazbar", node.asString());

            node = ExpressionResolver.TEST_RESOLVER.resolveExpressions(expression("a${foo${baz${bar}}}b"));
            assertEquals("aFOObazBARb", node.asString());

            node = ExpressionResolver.TEST_RESOLVER.resolveExpressions(expression("a${foo${baz${bar}}}"));
            assertEquals("aFOObazBAR", node.asString());

            node = ExpressionResolver.TEST_RESOLVER.resolveExpressions(expression("${foo${baz${bar}}}b"));
            assertEquals("FOObazBARb", node.asString());

            node = ExpressionResolver.TEST_RESOLVER.resolveExpressions(expression("a${foo}.b.${bar}c"));
            assertEquals("aFOO.b.BARc", node.asString());

            System.clearProperty("foo");

            node = ExpressionResolver.TEST_RESOLVER.resolveExpressions(expression("${foo:${bar}}"));
            assertEquals("BAR", node.asString());

            node = ExpressionResolver.TEST_RESOLVER.resolveExpressions(expression("${foo:${bar}.{}.$$}"));
            assertEquals("BAR.{}.$$", node.asString());

            node = ExpressionResolver.TEST_RESOLVER.resolveExpressions(expression("$$${bar}"));
            assertEquals("$BAR", node.asString());

        } finally {
            System.clearProperty("foo");
            System.clearProperty("bar");
            System.clearProperty("baz");
            System.clearProperty("FOO");
            System.clearProperty("BAR");
            System.clearProperty("BAZ");
            System.clearProperty("foo.BAZ.BAR");
            System.clearProperty("foo.BAZBAR");
            System.clearProperty("bazBAR");
            System.clearProperty("fooBAZbar");
        }
    }

    @Test
    public void testDollarEscaping() throws OperationFailedException {
        System.setProperty("$$", "FOO");
        try {
            ModelNode node = ExpressionResolver.TEST_RESOLVER.resolveExpressions(expression("$$"));
            assertEquals("$", node.asString());

            node = ExpressionResolver.TEST_RESOLVER.resolveExpressions(expression("$$$"));
            assertEquals("$$", node.asString());

            node = ExpressionResolver.TEST_RESOLVER.resolveExpressions(expression("$$$$"));
            assertEquals("$$", node.asString());

            node = ExpressionResolver.TEST_RESOLVER.resolveExpressions(expression("${$$$$:$$}"));
            assertEquals("$$", node.asString());

            node = ExpressionResolver.TEST_RESOLVER.resolveExpressions(expression("${$$:$$}"));
            assertEquals("FOO", node.asString());

            node = ExpressionResolver.TEST_RESOLVER.resolveExpressions(expression("${foo:$${bar}}"));
            assertEquals("${bar}", node.asString());

            node = ExpressionResolver.TEST_RESOLVER.resolveExpressions(expression("$${bar}"));
            assertEquals("${bar}", node.asString());
        } finally {
            System.clearProperty("$$");
        }
    }

    @Test
    public void testFileSeparator() throws OperationFailedException {
        assertEquals(File.separator, ExpressionResolver.TEST_RESOLVER.resolveExpressions(expression("${/}")).asString());
        assertEquals(File.separator + "a", ExpressionResolver.TEST_RESOLVER.resolveExpressions(expression("${/}a")).asString());
        assertEquals("a" + File.separator, ExpressionResolver.TEST_RESOLVER.resolveExpressions(expression("a${/}")).asString());
    }

    @Test
    public void testPathSeparator() {
        assertEquals(File.pathSeparator, new ValueExpression("${:}").resolveString());
        assertEquals(File.pathSeparator + "a", new ValueExpression("${:}a").resolveString());
        assertEquals("a" + File.pathSeparator, new ValueExpression("a${:}").resolveString());
    }

    @Test
    public void testNonExpression() throws OperationFailedException {
        ModelNode node =  ExpressionResolver.TEST_RESOLVER.resolveExpressions(expression("abc"));
        assertEquals("abc", node.asString());
        assertEquals(ModelType.STRING, node.getType());
    }

    @Test
    public void testBlankExpression() throws OperationFailedException {
        ModelNode node =  ExpressionResolver.TEST_RESOLVER.resolveExpressions(expression(""));
        assertEquals("", node.asString());
        assertEquals(ModelType.STRING, node.getType());
    }

    /**
     * Test that a incomplete expression to a system property reference throws an ISE
     */
    @Test(expected = ExpressionResolver.ExpressionResolutionUserException.class)
    public void testIncompleteReference() throws OperationFailedException {
        System.setProperty("test.property1", "test.property1.value");
        try {
            ModelNode resolved = ExpressionResolver.TEST_RESOLVER.resolveExpressions(expression("${test.property1"));
            fail("Did not fail with ERUE: " + resolved);
        } finally {
            System.clearProperty("test.property1");
        }
    }

    /**
     * Test that an incomplete expression is ignored if escaped
     */
    @Test
    public void testEscapedIncompleteReference() throws OperationFailedException {
        assertEquals("${test.property1", ExpressionResolver.TEST_RESOLVER.resolveExpressions(expression("$${test.property1")).asString());
    }

    /**
     * Test that a incomplete expression to a system property reference throws an ISE
     */
    @Test(expected = ExpressionResolver.ExpressionResolutionUserException.class)
    public void testIncompleteReferenceFollowingSuccessfulResolve() throws OperationFailedException {
        System.setProperty("test.property1", "test.property1.value");
        try {
            ModelNode resolved = ExpressionResolver.TEST_RESOLVER.resolveExpressions(expression("${test.property1} ${test.property1"));
            fail("Did not fail with ERUE: "+ resolved);
        } finally {
            System.clearProperty("test.property1");
        }
    }

    /**
     * Test an expression that contains more than one system property name to
     * see that the second property value is used when the first property
     * is not defined.
     */
    @Test
    public void testSystemPropertyRefs() throws OperationFailedException {
        System.setProperty("test.property2", "test.property2.value");
        try {
            assertEquals("test.property2.value", ExpressionResolver.TEST_RESOLVER.resolveExpressions(expression("${test.property1,test.property2}")).asString());
        } finally {
            System.clearProperty("test.property2");
        }
        assertEquals("default", ExpressionResolver.TEST_RESOLVER.resolveExpressions(expression("${test.property1,test.property2:default}")).asString());
    }

    @Test
    public void testExpressionWithDollarEndingDefaultValue() throws OperationFailedException {
        try {
            ModelNode node = new ModelNode();
            node.get("expr").set(new ValueExpression("${test.property.dollar.default:default$}-test"));
            node = ExpressionResolver.TEST_RESOLVER.resolveExpressions(node);
            assertEquals("default$-test", node.get("expr").asString());
            node = new ModelNode();
            node.get("expr").set(new ValueExpression("${test.property.dollar.default:default$test}-test"));
            node = ExpressionResolver.TEST_RESOLVER.resolveExpressions(node);
            assertEquals(1, node.keys().size());
            assertEquals("default$test-test", node.get("expr").asString());

            System.setProperty("test.property.dollar.default", "system-prop-value");
            node = new ModelNode();
            node.get("expr").set(new ValueExpression("${test.property.dollar.default:default$}-test"));
            node = ExpressionResolver.TEST_RESOLVER.resolveExpressions(node);
            assertEquals(1, node.keys().size());
            assertEquals("system-prop-value-test", node.get("expr").asString());
            node = new ModelNode();
            node.get("expr").set(new ValueExpression("${test.property.dollar.default:default$test}-test"));
            node = ExpressionResolver.TEST_RESOLVER.resolveExpressions(node);
            assertEquals(1, node.keys().size());
            assertEquals("system-prop-value-test", node.get("expr").asString());
        } finally {
            System.clearProperty("test.property.dollar.default");
        }
    }

    private ModelNode expression(String str) {
        return new ModelNode(new ValueExpression(str));
    }

    private void checkResolved(ModelNode node) {
        assertEquals(6, node.keys().size());
        assertEquals(1, node.get("int").asInt());
        assertEquals("EXPR", node.get("expr").asString());
        assertEquals(3, node.get("map").keys().size());

        assertEquals("a", node.get("map", "plain").asString());
        assertEquals("B", node.get("map", "prop.b").asString());
        assertEquals("C", node.get("map", "prop.c").asString());

        assertEquals(3, node.get("list").asList().size());
        assertEquals("one", node.get("list").asList().get(0).asString());
        assertEquals("TWO", node.get("list").asList().get(1).asString());
        assertEquals("THREE", node.get("list").asList().get(2).asString());

        assertEquals("plain", node.get("plainprop").asProperty().getValue().asString());
        assertEquals("PROP", node.get("prop").asProperty().getValue().asString());
    }

    private ModelNode createModelNode() {
        ModelNode node = new ModelNode();
        node.get("int").set(1);
        node.get("expr").set(new ValueExpression("${test.prop.expr}"));
        node.get("map", "plain").set("a");
        node.get("map", "prop.b").set(new ValueExpression("${test.prop.b}"));
        node.get("map", "prop.c").set(new ValueExpression("${test.prop.c}"));
        node.get("list").add("one");
        node.get("list").add(new ValueExpression("${test.prop.two}"));
        node.get("list").add(new ValueExpression("${test.prop.three}"));
        node.get("plainprop").set("plain", "plain");
        node.get("prop").set("test", new ValueExpression("${test.prop.prop}"));
        return node;
    }
}
