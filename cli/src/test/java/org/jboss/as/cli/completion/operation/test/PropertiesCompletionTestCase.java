/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.completion.operation.test;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.completion.mock.MockCommandContext;
import org.jboss.as.cli.completion.mock.MockNode;
import org.jboss.as.cli.completion.mock.MockOperation;
import org.jboss.as.cli.completion.mock.MockOperationCandidatesProvider;
import org.jboss.as.cli.completion.mock.MockOperationProperty;
import org.jboss.as.cli.operation.OperationRequestCompleter;
import org.junit.Test;

/**
 *
 * @author Alexey Loubyansky
 */
public class PropertiesCompletionTestCase {

    private MockCommandContext ctx;
    private OperationRequestCompleter completer;
    private static String OPERATION_PROPERTY_VALUES = "operation-property-values";

    public PropertiesCompletionTestCase() {

        MockNode root = new MockNode("root");

        MockOperation op = new MockOperation("operation-no-properties");
        root.addOperation(op);

        op = new MockOperation("operation-properties-one-two-three");
        op.setProperties(Arrays.asList(
                new MockOperationProperty("one"),
                new MockOperationProperty("two"),
                new MockOperationProperty("three")));
        root.addOperation(op);

        op = new MockOperation(OPERATION_PROPERTY_VALUES);
        op.setProperties(Arrays.asList(
                new MockOperationProperty("one", new String[]{"1", "5"}),
                new MockOperationProperty("two", new String[]{"false", "true"}, false),
                new MockOperationProperty("three", new String[]{"false", "true"}, false),
                new MockOperationProperty("three-test", new String[]{"false", "true"}, false)));
        root.addOperation(op);

        ctx = new MockCommandContext();
        ctx.setOperationCandidatesProvider(new MockOperationCandidatesProvider(root));
        completer = new OperationRequestCompleter();
    }

    @Test
    public void testOperationsWithFalseValue() {
        String buffer = ":"+OPERATION_PROPERTY_VALUES+"(two=";
        List<String> candidates = fetchCandidates(buffer);
        int result = fetchResult(buffer);
        assertEquals(Arrays.asList("false"), candidates);
        assertEquals(buffer.length(), result);
    }

    @Test
    public void testOperationsWithValuesAllSpecified() {
        String buffer = ":"+OPERATION_PROPERTY_VALUES+"(one=1,two=false,three-test=false,!three";
        List<String> candidates = fetchCandidates(buffer);
        int result = fetchResult(buffer);
        assertEquals(Arrays.asList(")"), candidates);
        assertEquals(buffer.length(), result);
    }

    @Test
    public void testOperationsNotOperator() {
        String buffer = ":"+OPERATION_PROPERTY_VALUES+"(!three";
        List<String> candidates = fetchCandidates(buffer);
        assertTrue(candidates.contains(")"));
        assertTrue(candidates.contains(","));
        assertTrue(candidates.contains("three-test"));
    }

    @Test
    public void testOperationsWithValues() {
        String buffer = ":"+OPERATION_PROPERTY_VALUES+"(one=";
        List<String> candidates = fetchCandidates(buffer);
        int result = fetchResult(buffer);
        assertEquals(Arrays.asList("1","5"), candidates);
        assertEquals(buffer.length(), result);
    }

    @Test
    public void testOperationsWithNotOperator() {
        String buffer = ":"+OPERATION_PROPERTY_VALUES+"(!two";
        List<String> candidates = fetchCandidates(buffer);
        int result = fetchResult(buffer);
        assertTrue(candidates.contains(")"));
        assertTrue(candidates.contains(","));
        assertEquals(buffer.length(), result);
    }

    @Test
    public void testOperationsWithNotOperatorAndWrongProperty() {
        String buffer = ":"+OPERATION_PROPERTY_VALUES+"(!twoo";
        List<String> candidates = fetchCandidates(buffer);
        int result = fetchResult(buffer);
        assertEquals(0, candidates.size());
        assertEquals(buffer.length()-"twoo".length(), result);
    }

    @Test
    public void testOperationsWithNotOperatorAndWrongPropertyPart() {
        String buffer = ":"+OPERATION_PROPERTY_VALUES+"(!te";
        List<String> candidates = fetchCandidates(buffer);
        int result = fetchResult(buffer);
        assertEquals(0, candidates.size());
        assertEquals(buffer.length()-"te".length(), result);
    }

    @Test
    public void testNoProperties() {

        List<String> candidates = fetchCandidates(":operation-no-properties(");
        //assertEquals(Arrays.asList(), candidates);
        assertEquals(Arrays.asList(")"), candidates);
    }

    @Test
    public void testAllCandidates() {

        List<String> candidates = fetchCandidates(":operation-properties-one-two-three(");
        assertEquals(Arrays.asList("one", "three", "two"), candidates);
    }

    @Test
    public void testTCandidates() {

        List<String> candidates = fetchCandidates(":operation-properties-one-two-three(t");
        assertEquals(Arrays.asList("three", "two"), candidates);
    }

    @Test
    public void testTwCandidates() {

        List<String> candidates = fetchCandidates(":operation-properties-one-two-three(tw");
        assertEquals(Arrays.asList("two"), candidates);
    }

    @Test
    public void testTwoCandidates() {

        List<String> candidates = fetchCandidates(":operation-properties-one-two-three(two");
        assertEquals(Arrays.asList("two"), candidates);
    }

    @Test
    public void testNoMatch() {

        List<String> candidates = fetchCandidates(":operation-properties-one-two-three(twoo");
        assertEquals(Arrays.asList(), candidates);
    }

    @Test
    public void testTwoWithValueCandidates() {

        List<String> candidates = fetchCandidates(":operation-properties-one-two-three(two=2");
        assertEquals(Arrays.asList(), candidates);
    }

    @Test
    public void testAfterTwoCandidates() {

        List<String> candidates = fetchCandidates(":operation-properties-one-two-three(two=2,");
        assertEquals(Arrays.asList("one", "three"), candidates);
    }

    @Test
    public void testOneAfterTwoCandidates() {

        List<String> candidates = fetchCandidates(":operation-properties-one-two-three(two=2,o");
        //assertEquals(Arrays.asList("one"), candidates);
        assertEquals(Arrays.asList("one"), candidates);
    }

    @Test
    public void testNoMatchAfterTwo() {

        List<String> candidates = fetchCandidates(":operation-properties-one-two-three(two=2,oo");
        assertEquals(Arrays.asList(), candidates);
    }

    @Test
    public void testThreeAfterOneAndTwo() {

        List<String> candidates = fetchCandidates(":operation-properties-one-two-three(two=2,one=1,");
        //assertEquals(Arrays.asList("three"), candidates);
        assertEquals(Arrays.asList("three"), candidates);
    }

    @Test
    public void testAllSpecified() {

        List<String> candidates = fetchCandidates(":operation-properties-one-two-three(two=2,one=1,three=3");
        //assertEquals(Arrays.asList(), candidates);
        assertEquals(Arrays.asList(")"), candidates);
    }

    @Test
    public void testAllSpecifiedWithSeparator() {

        List<String> candidates = fetchCandidates(":operation-properties-one-two-three(two=2,one=1,three=3,");
        assertEquals(Arrays.asList(), candidates);
    }

    @Test
    public void testAllSpecifiedAndAnotherPropName() {

        List<String> candidates = fetchCandidates(":operation-properties-one-two-three(two=2,one=1,three=3,four");
        assertEquals(Arrays.asList(), candidates);
    }

    @Test
    public void testAllSpecifiedAndAnotherProp() {

        List<String> candidates = fetchCandidates(":operation-properties-one-two-three(two=2,one=1,three=3,four=4");
        assertEquals(Arrays.asList(")"), candidates);
    }

    protected List<String> fetchCandidates(String buffer) {
        ArrayList<String> candidates = new ArrayList<String>();
        try {
            ctx.parseCommandLine(buffer, false);
        } catch (CommandFormatException e) {
            return Collections.emptyList();
        }
        completer.complete(ctx, buffer, 0, candidates);
        return candidates;
    }

    protected int fetchResult(String buffer) {
        ArrayList<String> candidates = new ArrayList<>();
        try {
            ctx.parseCommandLine(buffer, false);
        } catch (CommandFormatException e) {
            return -1;
        }
        return completer.complete(ctx, buffer, 0, candidates);
    }
}
