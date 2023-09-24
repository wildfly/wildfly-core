/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.patching.cli;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;

/**
 *
 * @author Alexey Loubyansky
 */
public class CLIPatchInfoUtil {

    private static final String PATCH_ID = "Patch ID";
    private static final String TYPE = "Type";
    private static final String IDENTITY_NAME = "Identity name";
    private static final String IDENTITY_VERSION = "Identity version";
    private static final String DESCR = "Description";

    private static final String CP = "cumulative";
    private static final String ONE_OFF = "one-off";

    public static void assertPatchInfo(byte[] info, String patchId, String link, boolean oneOff, String targetName, String targetVersion,
            String description) {
        try (final ByteArrayInputStream bis = new ByteArrayInputStream(info);
             final InputStreamReader reader = new InputStreamReader(bis, StandardCharsets.UTF_8);
             final BufferedReader buf = new BufferedReader(reader)){
            assertPatchInfo(buf, patchId, link, oneOff, targetName, targetVersion, description);
        } catch (IOException e) {
            //
        }
    }

    public static void assertPatchInfo(BufferedReader buf, String patchId, String link, boolean oneOff, String targetName, String targetVersion,
            String description) throws IOException {

        final Map<String,String> actual = parseTable(buf);

        final Map<String,String> expected = new HashMap<String,String>();
        expected.put(PATCH_ID, patchId);
        expected.put(TYPE, oneOff ? ONE_OFF : CP);
        expected.put(IDENTITY_NAME, targetName);
        expected.put(IDENTITY_VERSION, targetVersion);
        expected.put(DESCR, description);
        expected.put("Link", link);

        Assert.assertEquals(expected, actual);
    }

    public static void assertPatchInfo(byte[] info, String patchId, String link, boolean oneOff, String targetName, String targetVersion,
            String description, List<Map<String,String>> elements) {

        try (final ByteArrayInputStream bis = new ByteArrayInputStream(info);
                final InputStreamReader reader = new InputStreamReader(bis, StandardCharsets.UTF_8);
                final BufferedReader buf = new BufferedReader(reader)){
            assertPatchInfo(buf, patchId, link, oneOff, targetName, targetVersion, description, elements);
            if (buf.ready()) {
                final StringBuilder str = new StringBuilder();
                String line;
                while ((line = buf.readLine()) != null) {
                    str.append(line).append("\n");
                }
                Assert.fail("The output contained more info: " + str.toString());
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read the input", e);
        }
    }

    public static void assertPatchInfo(BufferedReader buf, String patchId, String link, boolean oneOff, String targetName, String targetVersion,
            String description, List<Map<String,String>> elements) throws IOException {

        final Map<String,String> expected = new HashMap<String,String>();
        expected.put(PATCH_ID, patchId);
        expected.put(TYPE, oneOff ? ONE_OFF : CP);
        expected.put(IDENTITY_NAME, targetName);
        expected.put(IDENTITY_VERSION, targetVersion);
        expected.put(DESCR, description);
        expected.put("Link", link);

        Map<String, String> actual = parseTable(buf);
        Assert.assertEquals(expected, actual);

        if (buf.ready()) {
            String readLine = buf.readLine();
            if (!"ELEMENTS".equals(readLine)) {
                Assert.fail("Expected 'ELEMENTS' but was '" + readLine + "'");
            }
            if (!buf.ready()) {
                Assert.fail("Expected an empty line");
            }
            readLine = buf.readLine();
            if (readLine == null || !readLine.isEmpty()) {
                Assert.fail("Expected an empty line but received '" + readLine + "'");
            }
        }

        for (Map<String, String> e : elements) {
            if (!buf.ready()) {
                Assert.fail("No more output");
            }
            actual = parseTable(buf);
            Assert.assertEquals(e, actual);
        }
    }

    public static Map<String, String> parseTable(byte[] table) throws IOException {
        try (final ByteArrayInputStream bis = new ByteArrayInputStream(table);
             final InputStreamReader reader = new InputStreamReader(bis, StandardCharsets.UTF_8);
             final BufferedReader buf = new BufferedReader(reader)) {
            return parseTable(buf);
        }
    }

    public static Map<String, String> parseTable(final BufferedReader buf) throws IOException {
        final Map<String, String> actual = new HashMap<String, String>();
        String line = null;
        while ((line = buf.readLine()) != null && !line.isEmpty()) {
            final int colon = line.indexOf(':');
            if (colon < 0) {
                Assert.fail("Failed to locate ':' in '" + line + "'");
            }
            if (colon == line.length() - 1) {
                Assert.fail("The line appears to end on ':'");
            }
            actual.put(line.substring(0, colon), line.substring(colon + 1).trim());
        }
        return actual;
    }
}
