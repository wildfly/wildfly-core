/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.cli.parsing.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.jboss.as.cli.impl.AttributeNamePathCompleter;
import org.jboss.dmr.ModelNode;
import org.junit.Test;

/**
 *
 * @author Alexey Loubyansky
 */
public class AttributeNamePathCompletionTestCase {

    private static final String attrsDescrMapAndLists = "{"
            + "\"str\" => {"
            + "      \"type\" => STRING,"
            + "      \"description\" => \"String attribute\","
            + "      \"expressions-allowed\" => false,"
            + "      \"required\" => true,"
            + "      \"nillable\" => false"
            + "},"
            + "\"str2\" => {"
            + "      \"type\" => STRING,"
            + "      \"description\" => \"String attribute\","
            + "      \"expressions-allowed\" => false,"
            + "      \"required\" => true,"
            + "      \"nillable\" => false"
            + "},"
            + "\"attr-read-only\" => {"
            + "      \"type\" => STRING,"
            + "      \"description\" => \"String attribute\","
            + "      \"expressions-allowed\" => false,"
            + "      \"required\" => true,"
            + "      \"nillable\" => false,"
            + "      \"access-type\" => \"read-only\""
            + "},"
            + "\"attr-read-write\" => {"
            + "      \"type\" => STRING,"
            + "      \"description\" => \"String attribute\","
            + "      \"expressions-allowed\" => false,"
            + "      \"required\" => true,"
            + "      \"nillable\" => false,"
            + "      \"access-type\" => \"read-write\""
            + "},"
            + "\"attr-metric\" => {"
            + "      \"type\" => STRING,"
            + "      \"description\" => \"String attribute\","
            + "      \"expressions-allowed\" => false,"
            + "      \"required\" => true,"
            + "      \"nillable\" => false,"
            + "      \"access-type\" => \"metric\""
            + "},"
            + "\"step1\" => {"
            + "      \"type\" => OBJECT,"
            + "      \"description\" => \"Object attribute\","
            + "      \"expressions-allowed\" => false,"
            + "      \"required\" => true,"
            + "      \"nillable\" => true,"
            + "      \"value-type\" => {"
            + "          \"str\" => {"
            + "              \"type\" => STRING,"
            + "              \"description\" => \"String attribute\","
            + "              \"expressions-allowed\" => false,"
            + "              \"required\" => true,"
            + "              \"nillable\" => false"
            + "          },"
            + "          \"step2\" => {"
            + "              \"type\" => LIST,"
            + "              \"description\" => \"List attribute\","
            + "              \"expressions-allowed\" => false,"
            + "              \"required\" => true,"
            + "              \"nillable\" => false,"
            + "              \"value-type\" => {"
            + "                  \"str\" => {"
            + "                      \"type\" => STRING,"
            + "                      \"description\" => \"String attribute\","
            + "                      \"expressions-allowed\" => false,"
            + "                      \"required\" => true,"
            + "                      \"nillable\" => false"
            + "                  },"
            + "                  \"module-options2\" => {"
            + "                      \"type\" => OBJECT,"
            + "                      \"description\" => \"Map of module options containing a name/value pair.\","
            + "                      \"expressions-allowed\" => true,"
            + "                      \"nillable\" => true,"
            + "                      \"value-type\" => STRING,"
            + "                      \"access-type\" => \"read-write\","
            + "                      \"storage\" => \"configuration\","
            + "                      \"restart-required\" => \"no-services\""
            + "                   }"
            + "              }"
            + "          }"
            + "      }"
            + "},"
            + "\"module-options\" => {"
            + "      \"type\" => OBJECT,"
            + "      \"description\" => \"Map of module options containing a name/value pair.\","
            + "      \"expressions-allowed\" => true,"
            + "      \"nillable\" => true,"
            + "      \"value-type\" => STRING,"
            + "      \"access-type\" => \"read-write\","
            + "      \"storage\" => \"configuration\","
            + "      \"restart-required\" => \"no-services\""
            + "},"
            + "\"module-options-lst\" => {"
            + "      \"type\" => OBJECT,"
            + "      \"description\" => \"Map of module options containing a name/value pair.\","
            + "      \"expressions-allowed\" => true,"
            + "      \"nillable\" => true,"
            + "      \"value-type\" => LIST,"
            + "      \"access-type\" => \"read-only\","
            + "      \"storage\" => \"configuration\","
            + "      \"restart-required\" => \"no-services\""
            + "},"
            + "\"lst-options-rw\" => {"
            + "      \"type\" => LIST,"
            + "      \"description\" => \"List of module options containing a name/value pair.\","
            + "      \"expressions-allowed\" => true,"
            + "      \"nillable\" => true,"
            + "      \"value-type\" => STRING,"
            + "      \"access-type\" => \"read-write\","
            + "      \"storage\" => \"configuration\","
            + "      \"restart-required\" => \"no-services\""
            + "},"
            + "\"lst-options-ro\" => {"
            + "      \"type\" => LIST,"
            + "      \"description\" => \"List of module options containing a name/value pair.\","
            + "      \"expressions-allowed\" => true,"
            + "      \"nillable\" => true,"
            + "      \"value-type\" => STRING,"
            + "      \"access-type\" => \"read-only\","
            + "      \"storage\" => \"configuration\","
            + "      \"restart-required\" => \"no-services\""
            + "}"
            + "}";

    private static final String attrsDescr = "{"
            +            "\"str\" => {" +
            "      \"type\" => STRING," +
            "      \"description\" => \"String attribute\"," +
            "      \"expressions-allowed\" => false," +
            "      \"required\" => true," +
            "      \"nillable\" => false" +
            "}," +
            "\"str2\" => {" +
            "      \"type\" => STRING," +
            "      \"description\" => \"String attribute\"," +
            "      \"expressions-allowed\" => false," +
            "      \"required\" => true," +
            "      \"nillable\" => false" +
            "}," +
            "\"attr-read-only\" => {" +
            "      \"type\" => STRING," +
            "      \"description\" => \"String attribute\"," +
            "      \"expressions-allowed\" => false," +
            "      \"required\" => true," +
            "      \"nillable\" => false," +
            "      \"access-type\" => \"read-only\""+
            "}," +
            "\"attr-read-write\" => {" +
            "      \"type\" => STRING," +
            "      \"description\" => \"String attribute\"," +
            "      \"expressions-allowed\" => false," +
            "      \"required\" => true," +
            "      \"nillable\" => false," +
            "      \"access-type\" => \"read-write\""+
            "}," +
            "\"attr-metric\" => {" +
            "      \"type\" => STRING," +
            "      \"description\" => \"String attribute\"," +
            "      \"expressions-allowed\" => false," +
            "      \"required\" => true," +
            "      \"nillable\" => false," +
            "      \"access-type\" => \"metric\""+
            "}," +

            "\"step1\" => {" +
            "      \"type\" => OBJECT," +
            "      \"description\" => \"Object attribute\"," +
            "      \"expressions-allowed\" => false," +
            "      \"required\" => true," +
            "      \"nillable\" => true," +
            "      \"value-type\" => {" +
            "          \"str\" => {" +
            "              \"type\" => STRING," +
            "              \"description\" => \"String attribute\"," +
            "              \"expressions-allowed\" => false," +
            "              \"required\" => true," +
            "              \"nillable\" => false" +
            "          }," +
            "          \"step2\" => {" +
            "              \"type\" => LIST," +
            "              \"description\" => \"List attribute\"," +
            "              \"expressions-allowed\" => false," +
            "              \"required\" => true," +
            "              \"nillable\" => false," +
            "              \"value-type\" => {" +
            "                  \"str\" => {" +
            "                      \"type\" => STRING," +
            "                      \"description\" => \"String attribute\"," +
            "                      \"expressions-allowed\" => false," +
            "                      \"required\" => true," +
            "                      \"nillable\" => false" +
            "                  }" +
            "              }" +
            "          }" +
            "      }" +
            "}," +
            "\"module-options\" => {" +
            "      \"type\" => OBJECT," +
            "      \"description\" => \"List of module options containing a name/value pair.\"," +
            "      \"expressions-allowed\" => true," +
            "      \"nillable\" => true," +
            "      \"value-type\" => STRING," +
            "      \"access-type\" => \"read-write\"," +
            "      \"storage\" => \"configuration\"," +
            "      \"restart-required\" => \"no-services\"" +
            "}" +
        "}";

    @Test
    public void testMain() throws Exception {
        final ModelNode propDescr = ModelNode.fromString(attrsDescr);
        assertTrue(propDescr.isDefined());

        final AttributeNamePathCompleter completer = new AttributeNamePathCompleter(propDescr);
        final List<String> candidates = new ArrayList<String>();

        int i;
        i = completer.complete(null, "", 0, candidates);
        assertEquals(Arrays.asList("attr-metric", "attr-read-only", "attr-read-write", "module-options", "step1", "str", "str2"), candidates);
        assertEquals(0, i);

        candidates.clear();
        i = completer.complete(null, "s", 0, candidates);
        assertEquals(Arrays.asList("step1", "str", "str2"), candidates);
        assertEquals(0, i);

        candidates.clear();
        i = completer.complete(null, "st", 0, candidates);
        assertEquals(Arrays.asList("step1", "str", "str2"), candidates);
        assertEquals(0, i);

        candidates.clear();
        i = completer.complete(null, "str", 0, candidates);
        assertEquals(Arrays.asList("str2"), candidates);
        assertEquals(0, i);

        candidates.clear();
        i = completer.complete(null, "ste", 0, candidates);
        assertEquals(Collections.singletonList("step1"), candidates);
        assertEquals(0, i);

        candidates.clear();
        i = completer.complete(null, "step1", 0, candidates);
        assertEquals(Collections.singletonList("."), candidates);
        assertEquals(5, i);

        candidates.clear();
        i = completer.complete(null, "step1.", 0, candidates);
        assertEquals(Arrays.asList("step2", "str"), candidates);
        assertEquals(6, i);

        candidates.clear();
        i = completer.complete(null, "step1.ste", 0, candidates);
        assertEquals(Arrays.asList("step2"), candidates);
        assertEquals(6, i);

        candidates.clear();
        i = completer.complete(null, "step1.step2", 0, candidates);
        assertEquals(Arrays.asList("["), candidates);
        assertEquals(11, i);

        candidates.clear();
        i = completer.complete(null, "step1.step2[", 0, candidates);
        assertEquals(Collections.emptyList(), candidates);
        assertEquals(-1, i);

        candidates.clear();
        i = completer.complete(null, "step1.step2[1", 0, candidates);
        assertEquals(Collections.emptyList(), candidates);
        assertEquals(-1, i);

        candidates.clear();
        i = completer.complete(null, "step1.step2[12", 0, candidates);
        assertEquals(Collections.emptyList(), candidates);
        assertEquals(-1, i);

        candidates.clear();
        i = completer.complete(null, "step1.step2[12]", 0, candidates);
        assertEquals(Arrays.asList(".", "="), candidates);
        assertEquals(15, i);

        candidates.clear();
        i = completer.complete(null, "step1.step2[12].", 0, candidates);
        assertEquals(Arrays.asList("str"), candidates);
        assertEquals(16, i);

        candidates.clear();
        i = completer.complete(null, "m", 0, candidates);
        assertEquals(Arrays.asList("module-options"), candidates);
        assertEquals(0, i);

        candidates.clear();
        i = completer.complete(null, "module-options", 0, candidates);
        assertEquals(Arrays.asList("."), candidates);
        assertEquals(14, i);

        candidates.clear();
        i = completer.complete(null, "module-options.", 0, candidates);
        assertEquals(Collections.emptyList(), candidates);
        assertEquals(-1, i);
    }

    @Test
    public void testAttributeAccessType() throws Exception {
        // WFCORE-1908
        final ModelNode propDescr = ModelNode.fromString(attrsDescr);
        assertTrue(propDescr.isDefined());

        final AttributeNamePathCompleter completer = new AttributeNamePathCompleter(propDescr);
        final List<String> candidates = new ArrayList<String>();

        // test write-only attribute
        int i = completer.complete("attr", candidates, propDescr, true);
        assertEquals(Arrays.asList("attr-read-write"), candidates);
        assertEquals(0, i);

        // test NOT write-only attribute
        candidates.clear();
        i = completer.complete("attr", candidates, propDescr, false);
        assertEquals(Arrays.asList("attr-metric", "attr-read-only", "attr-read-write"), candidates);
        assertEquals(0, i);
    }

    @Test
    public void testListOperations() throws Exception {
        final ModelNode propDescr = ModelNode.fromString(attrsDescrMapAndLists);
        assertTrue(propDescr.isDefined());

        final AttributeNamePathCompleter completer = new AttributeNamePathCompleter(propDescr, AttributeNamePathCompleter.LIST_FILTER);
        {
            final List<String> candidates = new ArrayList<>();
            completer.complete("", candidates, propDescr, false);
            assertEquals(Arrays.asList("lst-options-ro", "lst-options-rw", "module-options-lst", "step1"), candidates);
        }
        {
            final List<String> candidates = new ArrayList<>();
            completer.complete("", candidates, propDescr, true);
            assertEquals(Arrays.asList("lst-options-rw", "step1"), candidates);
        }
        {
            final List<String> candidates = new ArrayList<>();
            completer.complete("step1.", candidates, propDescr, false);
            assertEquals(Arrays.asList("step2"), candidates);
        }
    }

    @Test
    public void testMapOperations() throws Exception {
        final ModelNode propDescr = ModelNode.fromString(attrsDescrMapAndLists);
        assertTrue(propDescr.isDefined());

        final AttributeNamePathCompleter completer = new AttributeNamePathCompleter(propDescr, AttributeNamePathCompleter.MAP_FILTER);
        {
            final List<String> candidates = new ArrayList<>();
            completer.complete("", candidates, propDescr, false);
            assertEquals(Arrays.asList("module-options", "module-options-lst", "step1"), candidates);
        }
        {
            final List<String> candidates = new ArrayList<>();
            completer.complete("", candidates, propDescr, true);
            assertEquals(Arrays.asList("module-options", "step1"), candidates);
        }
        {
            final List<String> candidates = new ArrayList<>();
            completer.complete("step1.step2[0].", candidates, propDescr, false);
            assertEquals(Arrays.asList("module-options2"), candidates);
        }
    }
}
