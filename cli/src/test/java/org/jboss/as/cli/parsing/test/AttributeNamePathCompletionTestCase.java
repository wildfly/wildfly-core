/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
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

    private static final String attrsDescr = "{" +
            "\"str\" => {" +
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
        assertEquals(Arrays.asList("step1", "str", "str2"), candidates);
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
    }
}
