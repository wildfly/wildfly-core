/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.management.interfaces;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jboss.dmr.ModelNode;

/**
 * @author jcechace
 * @author Ladislav Thon <lthon@redhat.com>
 */
final class JmxInterfaceStringUtils {
    private JmxInterfaceStringUtils() {}

    static String rawString(String message) {
        String raw = removeQuotes(message);
        // This is need as StringModelValue#toString() returns escaped output
        return removeEscapes(raw);
    }

    private static String removeQuotes(String string) {
        if (string.startsWith("\"") && string.endsWith("\"")) {
            string = string.substring(1, string.length() - 1);
        }
        return string;
    }

    private static String removeEscapes(String string) {
        Pattern pattern = Pattern.compile("\\\\(.)");
        Matcher matcher = pattern.matcher(string);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String followup = matcher.group();
            matcher.appendReplacement(result, followup);
        }
        matcher.appendTail(result);
        return result.toString();
    }

    static String toDashCase(String string) {
        String regex = "([a-z])([A-Z])";
        String replacement = "$1-$2";
        return string.replaceAll(regex, replacement).toLowerCase(Locale.ENGLISH);
    }

    static String toCamelCase(String str) {
        Pattern pattern = Pattern.compile("-([a-z])");
        Matcher matcher = pattern.matcher(str);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String upperCaseLetter = matcher.group(1).toUpperCase(Locale.ENGLISH);
            matcher.appendReplacement(result, upperCaseLetter);
        }
        matcher.appendTail(result);
        return result.toString();
    }

    static ModelNode nodeFromString(String string) {
        ModelNode result;
        try {
            result = ModelNode.fromString(string);
        } catch (Exception e) {
            try {
                result = ModelNode.fromJSONString(string);
            } catch (Exception e1) {
                result = new ModelNode(string);
            }
        }
        return result;
    }
}
