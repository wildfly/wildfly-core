/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2019 Red Hat, Inc., and individual contributors
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

package org.jboss.as.logging.filters;

import static java.lang.Character.isJavaIdentifierPart;
import static java.lang.Character.isJavaIdentifierStart;
import static java.lang.Character.isWhitespace;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.logging.CommonAttributes;
import org.jboss.as.logging.Logging;
import org.jboss.as.logging.logging.LoggingLogger;
import org.jboss.dmr.ModelNode;

/**
 * Filter utilities and constants.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@SuppressWarnings("DuplicatedCode")
public class Filters {

    public static final String ACCEPT = "accept";
    public static final String ALL = "all";
    public static final String ANY = "any";
    public static final String DENY = "deny";
    public static final String LEVELS = "levels";
    public static final String LEVEL_CHANGE = "levelChange";
    public static final String LEVEL_RANGE = "levelRange";
    public static final String MATCH = "match";
    public static final String NOT = "not";
    public static final String SUBSTITUTE = "substitute";
    public static final String SUBSTITUTE_ALL = "substituteAll";

    /**
     * Converts the legacy {@link CommonAttributes#FILTER filter} to the new filter spec.
     *
     * @param value the value to convert
     *
     * @return the filter expression (filter spec) or an empty String the value is not
     * {@linkplain ModelNode#isDefined() defined}
     *
     * @throws org.jboss.as.controller.OperationFailedException if a conversion error occurs
     */
    public static String filterToFilterSpec(final ModelNode value) throws OperationFailedException {
        if (value.isDefined()) {
            final StringBuilder result = new StringBuilder();
            filterToFilterSpec(value, result, false);
            if (result.length() == 0) {
                final String name = value.hasDefined(CommonAttributes.FILTER.getName()) ? value.get(CommonAttributes.FILTER.getName()).asString() : value.asString();
                throw Logging.createOperationFailure(LoggingLogger.ROOT_LOGGER.invalidFilter(name));
            }
            return result.toString();
        }
        return "";
    }

    /**
     * Converts a filter spec to a legacy {@link CommonAttributes#FILTER filter}.
     *
     * @param value the value to convert
     *
     * @return the complex filter object
     */
    public static ModelNode filterSpecToFilter(final String value) {
        final ModelNode filter = new ModelNode(CommonAttributes.FILTER.getName()).setEmptyObject();
        final Iterator<String> iterator = tokens(value).iterator();
        parseFilterExpression(iterator, filter);
        return filter;
    }

    /**
     * Returns a collection of names of custom filters inside a {@code filter-spec} attribute.
     *
     * @param filterSpec the filter specification string
     *
     * @return a collection of custom filter names
     */
    public static Collection<String> getCustomFilterNames(final String filterSpec) {
        final Collection<String> result = new HashSet<>();
        if (filterSpec != null) {
            result.addAll(parseFilterExpression(filterSpec));
        }
        return result;
    }

    private static Collection<String> parseFilterExpression(final String value) {
        return parseFilterExpression(tokens(value).iterator(), new ModelNode(), true, false);
    }

    private static void parseFilterExpression(final Iterator<String> iterator, final ModelNode model) {
        parseFilterExpression(iterator, model, true, true);
    }

    @SuppressWarnings("ConstantConditions")
    private static Collection<String> parseFilterExpression(final Iterator<String> iterator, final ModelNode model, final boolean outermost, final boolean forFilter) {
        final Collection<String> result = new ArrayList<>();
        if (!iterator.hasNext()) {
            if (outermost) {
                model.setEmptyObject();
                return Collections.emptyList();
            }
            throw LoggingLogger.ROOT_LOGGER.unexpectedEnd();
        }
        final String token = iterator.next();
        if (ACCEPT.equals(token)) {
            set(CommonAttributes.ACCEPT, model, true);
        } else if (DENY.equals(token)) {
            set(CommonAttributes.DENY, model, true);
        } else if (NOT.equals(token)) {
            expect("(", iterator);
            result.addAll(parseFilterExpression(iterator, model.get(CommonAttributes.NOT.getName()), false, forFilter));
            expect(")", iterator);
        } else if (ALL.equals(token)) {
            expect("(", iterator);
            do {
                final ModelNode m = model.get(CommonAttributes.ALL.getName());
                result.addAll(parseFilterExpression(iterator, m, false, forFilter));
            } while (expect(",", ")", iterator));
        } else if (ANY.equals(token)) {
            expect("(", iterator);
            do {
                final ModelNode m = model.get(CommonAttributes.ANY.getName());
                result.addAll(parseFilterExpression(iterator, m, false, forFilter));
            } while (expect(",", ")", iterator));
        } else if (LEVEL_CHANGE.equals(token)) {
            expect("(", iterator);
            final String levelName = expectName(iterator);
            set(CommonAttributes.CHANGE_LEVEL, model, levelName);
            expect(")", iterator);
        } else if (LEVELS.equals(token)) {
            expect("(", iterator);
            final Set<String> levels = new HashSet<>();
            do {
                levels.add(expectName(iterator));
            } while (expect(",", ")", iterator));
            // The model only allows for one level,just use the first
            if (levels.iterator().hasNext()) set(CommonAttributes.LEVEL, model, levels.iterator().next());
        } else if (LEVEL_RANGE.equals(token)) {
            final ModelNode levelRange = model.get(CommonAttributes.LEVEL_RANGE_LEGACY.getName());
            final boolean minInclusive = expect("[", "(", iterator);
            if (minInclusive) set(CommonAttributes.MIN_INCLUSIVE, levelRange, minInclusive);
            final String minLevel = expectName(iterator);
            set(CommonAttributes.MIN_LEVEL, levelRange, minLevel);
            expect(",", iterator);
            final String maxLevel = expectName(iterator);
            set(CommonAttributes.MAX_LEVEL, levelRange, maxLevel);
            final boolean maxInclusive = expect("]", ")", iterator);
            if (maxInclusive) set(CommonAttributes.MAX_INCLUSIVE, levelRange, maxInclusive);
        } else if (MATCH.equals(token)) {
            expect("(", iterator);
            final String pattern = expectString(iterator);
            set(CommonAttributes.MATCH, model, pattern);
            expect(")", iterator);
        } else if (SUBSTITUTE.equals(token)) {
            final ModelNode substitute = model.get(CommonAttributes.REPLACE.getName());
            substitute.get(CommonAttributes.REPLACE_ALL.getName()).set(false);
            expect("(", iterator);
            final String pattern = expectString(iterator);
            set(CommonAttributes.FILTER_PATTERN, substitute, pattern);
            expect(",", iterator);
            final String replacement = expectString(iterator);
            set(CommonAttributes.REPLACEMENT, substitute, replacement);
            expect(")", iterator);
        } else if (SUBSTITUTE_ALL.equals(token)) {
            final ModelNode substitute = model.get(CommonAttributes.REPLACE.getName());
            substitute.get(CommonAttributes.REPLACE_ALL.getName()).set(true);
            expect("(", iterator);
            final String pattern = expectString(iterator);
            set(CommonAttributes.FILTER_PATTERN, substitute, pattern);
            expect(",", iterator);
            final String replacement = expectString(iterator);
            set(CommonAttributes.REPLACEMENT, substitute, replacement);
            expect(")", iterator);
        } else {
            if (forFilter) {
                // Previously we threw an error here, however it would likely never be thrown. With the introduction of
                // custom filters a named filter may be present. In this case we're just going to log a debug message
                // indicating the named filter is not supported for the legacy filter.
                LoggingLogger.ROOT_LOGGER.debugf("Only known filters are supported on the filter attribute. Ignoring " +
                        "filter %s.", token);
            }
            result.add(token);
        }
        return result;
    }

    private static String expectName(Iterator<String> iterator) {
        if (iterator.hasNext()) {
            final String next = iterator.next();
            if (isJavaIdentifierStart(next.codePointAt(0))) {
                return next;
            }
        }
        throw LoggingLogger.ROOT_LOGGER.expectedIdentifier();
    }

    private static String expectString(final Iterator<String> iterator) {
        if (iterator.hasNext()) {
            final String next = iterator.next();
            if (next.codePointAt(0) == '"') {
                return next.substring(1);
            }
        }
        throw LoggingLogger.ROOT_LOGGER.expectedString();
    }

    private static boolean expect(final String trueToken, final String falseToken, final Iterator<String> iterator) {
        final boolean hasNext = iterator.hasNext();
        final String next = hasNext ? iterator.next() : null;
        final boolean result;
        if (!hasNext || !((result = trueToken.equals(next)) || falseToken.equals(next))) {
            throw LoggingLogger.ROOT_LOGGER.expected(trueToken, falseToken);
        }
        return result;
    }

    private static void expect(String token, Iterator<String> iterator) {
        if (!iterator.hasNext() || !token.equals(iterator.next())) {
            throw LoggingLogger.ROOT_LOGGER.expected(token);
        }
    }

    private static void set(final AttributeDefinition attribute, final ModelNode model, final String value) {
        set(attribute.getName(), model, value);
    }

    private static void set(final AttributeDefinition attribute, final ModelNode model, final boolean value) {
        set(attribute.getName(), model, value);
    }

    private static void set(final String name, final ModelNode model, final String value) {
        model.get(name).set(value);
    }

    private static void set(final String name, final ModelNode model, final boolean value) {
        model.get(name).set(value);
    }


    @SuppressWarnings("UnusedAssignment")
    private static List<String> tokens(final String source) {
        final List<String> tokens = new ArrayList<>();
        final int length = source.length();
        int idx = 0;
        while (idx < length) {
            int ch;
            ch = source.codePointAt(idx);
            if (isWhitespace(ch)) {
                ch = source.codePointAt(idx);
                idx = source.offsetByCodePoints(idx, 1);
            } else if (isJavaIdentifierStart(ch)) {
                int start = idx;
                do {
                    idx = source.offsetByCodePoints(idx, 1);
                } while (idx < length && isJavaIdentifierPart(ch = source.codePointAt(idx)));
                tokens.add(source.substring(start, idx));
            } else if (ch == '"') {
                final StringBuilder b = new StringBuilder();
                // tag token as a string
                b.append('"');
                idx = source.offsetByCodePoints(idx, 1);
                while (idx < length && (ch = source.codePointAt(idx)) != '"') {
                    ch = source.codePointAt(idx);
                    if (ch == '\\') {
                        idx = source.offsetByCodePoints(idx, 1);
                        if (idx == length) {
                            throw LoggingLogger.ROOT_LOGGER.truncatedFilterExpression();
                        }
                        ch = source.codePointAt(idx);
                        switch (ch) {
                            case '\\':
                                b.append('\\');
                                break;
                            case '\'':
                                b.append('\'');
                                break;
                            case '"':
                                b.append('"');
                                break;
                            case 'b':
                                b.append('\b');
                                break;
                            case 'f':
                                b.append('\f');
                                break;
                            case 'n':
                                b.append('\n');
                                break;
                            case 'r':
                                b.append('\r');
                                break;
                            case 't':
                                b.append('\t');
                                break;
                            default:
                                throw LoggingLogger.ROOT_LOGGER.invalidEscapeFoundInFilterExpression();
                        }
                    } else {
                        b.appendCodePoint(ch);
                    }
                    idx = source.offsetByCodePoints(idx, 1);
                }
                idx = source.offsetByCodePoints(idx, 1);
                tokens.add(b.toString());
            } else {
                int start = idx;
                idx = source.offsetByCodePoints(idx, 1);
                tokens.add(source.substring(start, idx));
            }
        }
        return tokens;
    }

    private static void filterToFilterSpec(final ModelNode value, final StringBuilder result, final boolean prefixComma) throws OperationFailedException {
        if (!value.isDefined()) {
            return;
        }
        if (value.has(CommonAttributes.ACCEPT.getName())) {
            if (value.get(CommonAttributes.ACCEPT.getName()).isDefined()) {
                if (prefixComma) {
                    result.append(',');
                }
                result.append(ACCEPT);
            }
        } else if (value.has(CommonAttributes.ALL.getName())) {
            final ModelNode allValue = value.get(CommonAttributes.ALL.getName());
            if (allValue.isDefined()) {
                if (prefixComma) {
                    result.append(',');
                }
                result.append(ALL).append('(');
                boolean addComma = false;
                for (ModelNode filterValue : allValue.asList()) {
                    final int currentLen = result.length();
                    filterToFilterSpec(filterValue, result, addComma);
                    if (result.length() > currentLen) {
                        addComma = true;
                    }
                }
                result.append(")");
            }
        } else if (value.has(CommonAttributes.ANY.getName())) {
            final ModelNode anyValue = value.get(CommonAttributes.ANY.getName());
            if (anyValue.isDefined()) {
                if (prefixComma) {
                    result.append(',');
                }
                result.append(ANY).append('(');
                boolean addComma = false;
                for (ModelNode filterValue : anyValue.asList()) {
                    final int currentLen = result.length();
                    filterToFilterSpec(filterValue, result, addComma);
                    if (result.length() > currentLen) {
                        addComma = true;
                    }
                }
                result.append(")");
            }
        } else if (value.has(CommonAttributes.CHANGE_LEVEL.getName())) {
            final ModelNode changeLevelValue = value.get(CommonAttributes.CHANGE_LEVEL.getName());
            if (changeLevelValue.isDefined()) {
                if (prefixComma) {
                    result.append(',');
                }
                result.append(LEVEL_CHANGE).append('(').append(changeLevelValue.asString()).append(')');
            }
        } else if (value.has(CommonAttributes.DENY.getName())) {
            if (value.get(CommonAttributes.DENY.getName()).isDefined()) {
                if (prefixComma) {
                    result.append(',');
                }
                result.append(DENY);
            }
        } else if (value.has(CommonAttributes.LEVEL.getName())) {
            final ModelNode levelValue = value.get(CommonAttributes.LEVEL.getName());
            if (levelValue.isDefined()) {
                if (prefixComma) {
                    result.append(',');
                }
                result.append(LEVELS).append('(').append(levelValue.asString()).append(')');
            }
        } else if (value.has(CommonAttributes.LEVEL_RANGE_LEGACY.getName())) {
            final ModelNode levelRangeValue = value.get(CommonAttributes.LEVEL_RANGE_LEGACY.getName());
            if (levelRangeValue.isDefined()) {
                if (prefixComma) {
                    result.append(',');
                }
                final ModelNode levelRange = value.get(CommonAttributes.LEVEL_RANGE_LEGACY.getName());
                result.append(LEVEL_RANGE);
                final boolean minInclusive = (levelRange.hasDefined(CommonAttributes.MIN_INCLUSIVE.getName()) && levelRange.get(CommonAttributes.MIN_INCLUSIVE.getName()).asBoolean());
                final boolean maxInclusive = (levelRange.hasDefined(CommonAttributes.MAX_INCLUSIVE.getName()) && levelRange.get(CommonAttributes.MAX_INCLUSIVE.getName()).asBoolean());
                if (minInclusive) {
                    result.append("[");
                } else {
                    result.append("(");
                }
                result.append(levelRange.get(CommonAttributes.MIN_LEVEL.getName()).asString()).append(",");
                result.append(levelRange.get(CommonAttributes.MAX_LEVEL.getName()).asString());
                if (maxInclusive) {
                    result.append("]");
                } else {
                    result.append(")");
                }
            }
        } else if (value.has(CommonAttributes.MATCH.getName())) {
            final ModelNode matchValue = value.get(CommonAttributes.MATCH.getName());
            if (matchValue.isDefined()) {
                if (prefixComma) {
                    result.append(',');
                }
                result.append(MATCH).append('(').append(escapeString(matchValue)).append(')');
            }
        } else if (value.has(CommonAttributes.NOT.getName())) {
            final ModelNode notValue = value.get(CommonAttributes.NOT.getName());
            if (notValue.isDefined()) {
                if (prefixComma) {
                    result.append(',');
                }
                result.append(NOT).append('(');
                filterToFilterSpec(notValue, result, false);
                result.append(')');
            }
        } else if (value.has(CommonAttributes.REPLACE.getName())) {
            final ModelNode replaceValue = value.get(CommonAttributes.REPLACE.getName());
            if (replaceValue.isDefined()) {
                if (prefixComma) {
                    result.append(',');
                }
                final boolean replaceAll;
                if (replaceValue.hasDefined(CommonAttributes.REPLACE_ALL.getName())) {
                    replaceAll = replaceValue.get(CommonAttributes.REPLACE_ALL.getName()).asBoolean();
                } else {
                    replaceAll = CommonAttributes.REPLACE_ALL.getDefaultValue().asBoolean();
                }
                if (replaceAll) {
                    result.append(SUBSTITUTE_ALL);
                } else {
                    result.append(SUBSTITUTE);
                }
                result.append("(")
                        .append(escapeString(CommonAttributes.FILTER_PATTERN, replaceValue))
                        .append(",").append(escapeString(CommonAttributes.REPLACEMENT, replaceValue))
                        .append(")");
            }
        } else {
            final String name = value.hasDefined(CommonAttributes.FILTER.getName()) ? value.get(CommonAttributes.FILTER.getName()).asString() : value.asString();
            throw Logging.createOperationFailure(LoggingLogger.ROOT_LOGGER.invalidFilter(name));
        }
    }

    private static String escapeString(final AttributeDefinition attribute, final ModelNode value) {
        return escapeString(value.get(attribute.getName()));
    }

    private static String escapeString(final ModelNode value) {
        return String.format("\"%s\"", value.asString().replace("\\", "\\\\"));
    }
}
