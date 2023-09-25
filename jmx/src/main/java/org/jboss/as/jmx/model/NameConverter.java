/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.jmx.model;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;

import org.jboss.as.controller.PathElement;

/**
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
class NameConverter {

    public static String createValidAddOperationName(PathElement childElement) {
        return createValidName(ADD, childElement.getKey(), childElement.getValue());
    }

    public static String convertToCamelCase(String word) {
        StringBuilder sb = new StringBuilder();
        appendCamelCaseWord(sb, true, word.split("-"));
        return sb.toString();
    }

    public static String convertFromCamelCase(String word) {
        StringBuilder sb = new StringBuilder();
        for (char ch : word.toCharArray()) {
            if (Character.isLowerCase(ch)) {
                sb.append(ch);
            } else {
                sb.append("-");
                sb.append(Character.toLowerCase(ch));
            }
        }
        return sb.toString();
    }

    private static String createValidName(String...parts) {
        StringBuilder sb = new StringBuilder(parts[0]);
        for (int i = 1 ; i < parts.length ; i++) {
            if (parts[i].equals("*")) {
                continue;
            }
            appendCamelCaseWord(sb, false, parts[i].split("-"));
        }
        return sb.toString();
    }

    private static void appendCamelCaseWord(StringBuilder sb, boolean isStart, String...parts) {
        if (parts.length == 1) {
            if (!isStart) {
                sb.append(Character.toUpperCase(parts[0].charAt(0)));
                sb.append(parts[0].substring(1));
            } else {
                sb.append(parts[0]);
            }
        } else {
            for (int i = 0 ; i < parts.length ; i++) {
                final boolean isCurrentStart = isStart && i == 0;
                appendCamelCaseWord(sb, isCurrentStart, parts[i]);
            }
        }
    }
}
