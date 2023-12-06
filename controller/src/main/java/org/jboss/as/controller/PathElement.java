/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.controller;

import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.version.Stability;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;

/**
 * An element of a path specification for matching operations with addresses.
 * @author Brian Stansberry
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public class PathElement implements Feature {

    public static final String WILDCARD_VALUE = "*";

    private final String key;
    private final String value;
    private final boolean multiTarget;
    private final int hashCode;
    private final Stability stability;

    /**
     * Construct a new instance with a wildcard value.
     * @param key the path key to match
     * @return the new path element
     */
    public static PathElement pathElement(final String key) {
        return new PathElement(key);
    }

    /**
     * Construct a new instance.
     * @param key the path key to match
     * @param value the path value or wildcard to match
     * @return the new path element
     */
    public static PathElement pathElement(final String key, final String value) {
        return new PathElement(key, value);
    }

    /**
     * Construct a new instance with a wildcard value.
     * @param key the path key to match
     * @param stability the stability level of the associated resources
     * @return the new path element
     */
    public static PathElement pathElement(final String key, Stability stability) {
        return new PathElement(key, stability);
    }

    /**
     * Construct a new instance.
     * @param key the path key to match
     * @param value the path value or wildcard to match
     * @param stability the stability level of the associated resource
     * @return the new path element
     */
    public static PathElement pathElement(final String key, final String value, Stability stability) {
        return new PathElement(key, value, stability);
    }

    /**
     * Construct a new instance with a wildcard value.
     * @param key the path key to match
     */
    PathElement(final String key) {
        this(key, WILDCARD_VALUE);
    }

    /**
     * Construct a new instance with a wildcard value.
     * @param key the path key to match
     */
    PathElement(final String key, Stability stability) {
        this(key, WILDCARD_VALUE, stability);
    }

    /**
     * Construct a new instance.
     * @param key the path key to match
     * @param value the path value or wildcard to match
     */
    PathElement(final String key, final String value) {
        this(key, value, Stability.DEFAULT);
    }

    /**
     * Construct a new instance.
     * @param key the path key to match
     * @param value the path value or wildcard to match
     */
    PathElement(final String key, final String value, Stability stability) {
        if (!isValidKey(key)) {
            final String element = key + "=" + value;
            throw new OperationClientIllegalArgumentException(ControllerLogger.ROOT_LOGGER.invalidPathElementKey(element, key));
        }
        if (value == null || value.isEmpty()) {
            final String element = key + "=" + value;
            throw new OperationClientIllegalArgumentException(ControllerLogger.ROOT_LOGGER.invalidPathElementValue(element, value, ' '));
        }
        boolean multiTarget = false;
        if(key.equals(WILDCARD_VALUE)) {
            this.key = WILDCARD_VALUE;
            multiTarget = true;
        } else {
            this.key = key;
        }
        if (value.equals(WILDCARD_VALUE)) {
            this.value = WILDCARD_VALUE;
            multiTarget = true;
        } else if (value.charAt(0) == '[' && value.charAt(value.length() - 1) == ']') {
            this.value = value.substring(1, value.length() - 1);
            multiTarget |= value.indexOf(',') != -1;
        } else {
            this.value = value;
        }
        this.multiTarget = multiTarget;
        hashCode = key.hashCode() * 19 + value.hashCode();
        this.stability = stability;
    }

    /**
     * A valid key contains alphanumerics and underscores, cannot start with a
     * number, and cannot start or end with {@code -}.
     */
    private static boolean isValidKey(final String s) {
        // Equivalent to this regex \*|[_a-zA-Z](?:[-_a-zA-Z0-9]*[_a-zA-Z0-9]) but faster
        if (s == null) {
            return false;
        }
        if (s.equals(WILDCARD_VALUE)) {
            return true;
        }
        int lastIndex = s.length() - 1;
        if (lastIndex == -1) {
            return false;
        }
        if (!isValidKeyStartCharacter(s.charAt(0))) {
            return false;
        }
        for (int i = 1; i < lastIndex; i++) {
            if (!isValidKeyCharacter(s.charAt(i))) {
                return false;
            }
        }
        if (lastIndex > 0 && !isValidKeyEndCharacter(s.charAt(lastIndex))) {
            return false;
        }
        return true;
    }

    private static boolean isValidKeyStartCharacter(final char c) {
        return c == '_'
            || c >= 'a' && c <= 'z'
            || c >= 'A' && c <= 'Z';
    }

    private static boolean isValidKeyEndCharacter(final char c) {
        return c == '_'
            || c >= '0' && c <= '9'
            || c >= 'a' && c <= 'z'
            || c >= 'A' && c <= 'Z';
    }

    private static boolean isValidKeyCharacter(char c) {
        return c == '_' || c == '-'
            || c >= '0' && c <= '9'
            || c >= 'a' && c <= 'z'
            || c >= 'A' && c <= 'Z';
    }

    @Override
    public Stability getStability() {
        return this.stability;
    }

    /**
     * Get the path key.
     * @return the path key
     */
    public String getKey() {
        return key;
    }

    /**
     * Get the path value.
     * @return the path value
     */
    public String getValue() {
        return value;
    }

    /**
     * Determine whether the given property matches this element.
     * A property matches this element when property name and this key are equal,
     * values are equal or this element value is a wildcard.
     * @param property the property to check
     * @return {@code true} if the property matches
     */
    public boolean matches(Property property) {
        return property.getName().equals(key) && (value == WILDCARD_VALUE || property.getValue().asString().equals(value));
    }

    /**
     * Determine whether the given element matches this element.
     * An element matches this element when keys are equal, values are equal
     * or this element value is a wildcard.
     * @param pe the element to check
     * @return {@code true} if the element matches
     */
    public boolean matches(PathElement pe) {
        return pe.key.equals(key) && (isWildcard() || pe.value.equals(value));
    }

    /**
     * Determine whether the value is the wildcard value.
     * @return {@code true} if the value is the wildcard value
     */
    public boolean isWildcard() {
        return WILDCARD_VALUE == value; //this is ok as we are expecting exact same object.
    }

    public boolean isMultiTarget() {
        return multiTarget;
    }

    public String[] getSegments() {
        return value.split(",");
    }

    public String[] getKeyValuePair(){
        return new String[]{key,value};
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    /**
     * Determine whether this object is equal to another.
     * @param other the other object
     * @return {@code true} if they are equal, {@code false} otherwise
     */
    public boolean equals(Object other) {
        return other instanceof PathElement && equals((PathElement) other);
    }

    /**
     * Determine whether this object is equal to another.
     * @param other the other object
     * @return {@code true} if they are equal, {@code false} otherwise
     */
    public boolean equals(PathElement other) {
        return this == other || other != null && other.key.equals(key) && other.value.equals(value);
    }

    @Override
    public String toString() {
        return "\"" + key + "\" => \"" + value + "\"";
    }

    /**
     * AS7-2905. An IAE that implements OperationClientException. Allows PathElement to continue to throw IAE
     * in case client code expects that failure type, but lets operation handling code detect that the
     * IAE is a client error.
     */
    private static class OperationClientIllegalArgumentException extends IllegalArgumentException implements OperationClientException {

        private static final long serialVersionUID = -9073168544821068948L;

        private OperationClientIllegalArgumentException(final String msg) {
            super(msg);
            assert msg != null : "msg is null";
        }

        @Override
        public ModelNode getFailureDescription() {
            return new ModelNode(getLocalizedMessage());
        }
    }
}
