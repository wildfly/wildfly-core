/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.jmx.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.jmx.logging.JmxLogger;

/**
 * Utility class to convert between PathAddress and ObjectName
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
class ObjectNameAddressUtil {

    private static final EscapedCharacter[] ESCAPED_KEY_CHARACTERS;
    private static final EscapedCharacter[] ESCAPED_QUOTED_VALUE_CHARACTERS;
    static {

        List<EscapedCharacter> keys = new ArrayList<EscapedCharacter>();

        //From ObjectName javadoc:
        //Each key is a nonempty string of characters which may not contain any of the characters
        //comma (,), equals (=), colon, asterisk, or question mark. The same key may not occur twice in a given ObjectName.
        keys.add(new EscapedCharacter('*'));
        keys.add(new EscapedCharacter('?'));
        keys.add(new EscapedCharacter(':'));
        keys.add(new EscapedCharacter('='));
        keys.add(new EscapedCharacter(','));

        ESCAPED_KEY_CHARACTERS = keys.toArray(new EscapedCharacter[keys.size()]);

        List<EscapedCharacter> values = new ArrayList<EscapedCharacter>();

        //From ObjectName javadoc:
        /*
        A <em>quoted value</em> consists of a quote (<code>"</code>),
        followed by a possibly empty string of characters, followed by
        another quote.  Within the string of characters, the backslash
        (<code>\</code>) has a special meaning.  It must be followed by
        one of the following characters:

        * Another backslash.  The second backslash has no special
          meaning and the two characters represent a single backslash.

        * The character 'n'.  The two characters represent a newline
          ('\n' in Java).

        * A quote.  The two characters represent a quote, and that quote
          is not considered to terminate the quoted value. An ending closing
          quote must be present for the quoted value to be valid.

        * A question mark (?) or asterisk (*).  The two characters represent
          a question mark or asterisk respectively.
        */
        // Process \ itself first so it doesn't escape the \ added by the others
        values.add(new EscapedCharacter("\\", "\\\\"));
        values.add(new EscapedCharacter("*", "\\*"));
        values.add(new EscapedCharacter("\n", "\\n"));
        values.add(new EscapedCharacter("?", "\\?"));
        values.add(new EscapedCharacter( "\"", "\\\""));

        ESCAPED_QUOTED_VALUE_CHARACTERS = values.toArray(new EscapedCharacter[values.size()]);
    }

    /**
     * Contextual object that can be passed to multiple invocations of
     * {@link #createObjectName(String, PathAddress, ObjectNameCreationContext)}
     * allowing that method to share state across invocations.
     */
    static final class ObjectNameCreationContext {

        static ObjectNameCreationContext create() {
            return new ObjectNameCreationContext();
        }

        private final Map<String, String> keyCache = new HashMap<>();
        private final Map<String, String> valueCache = new HashMap<>();

        private String getCachedKey(String key) {
            return keyCache.get(key);
        }

        private void cacheKey(String key, String toCache) {
            keyCache.put(key, toCache);
        }

        private String getCachedValue(String value) {
            return valueCache.get(value);
        }

        private void cacheValue(String value, String toCache) {
            valueCache.put(value, toCache);
        }
    }

    /**
     * Creates an ObjectName representation of a {@link PathAddress}.
     * @param domain the JMX domain to use for the ObjectName. Cannot be {@code null}
     * @param pathAddress the address. Cannot be {@code null}
     * @return the ObjectName. Will not return {@code null}
     */
    static ObjectName createObjectName(final String domain, final PathAddress pathAddress) {
        return createObjectName(domain, pathAddress, null);
    }

    /**
     * Creates an ObjectName representation of a {@link PathAddress}.
     * @param domain the JMX domain to use for the ObjectName. Cannot be {@code null}
     * @param pathAddress the address. Cannot be {@code null}
     * @param context contextual objection that allows this method to cache state across invocations. May be {@code null}
     * @return the ObjectName. Will not return {@code null}
     */
    static ObjectName createObjectName(final String domain, final PathAddress pathAddress, ObjectNameCreationContext context) {

        if (pathAddress.size() == 0) {
            return ModelControllerMBeanHelper.createRootObjectName(domain);
        }
        final StringBuilder sb = new StringBuilder(domain);
        sb.append(":");
        boolean first = true;
        for (PathElement element : pathAddress) {
            if (first) {
                first = false;
            } else {
                sb.append(",");
            }
            escapeKey(ESCAPED_KEY_CHARACTERS, sb, element.getKey(), context);
            sb.append("=");
            escapeValue(sb, element.getValue(), context);
        }
        try {
            return ObjectName.getInstance(sb.toString());
        } catch (MalformedObjectNameException e) {
            throw JmxLogger.ROOT_LOGGER.cannotCreateObjectName(e, pathAddress, sb.toString());
        }
    }

    /**
     * Converts the ObjectName to a PathAddress.
     *
     * @param domain the name of the caller's JMX domain
     * @param rootResource the root resource for the management model
     * @param name the ObjectName
     *
     * @return the PathAddress if it exists in the model, {@code null} otherwise
     */
    static PathAddress resolvePathAddress(final String domain, final Resource rootResource, final ObjectName name) {
        return resolvePathAddress(ModelControllerMBeanHelper.createRootObjectName(domain), rootResource, name);
    }

    /**
     * Converts the ObjectName to a PathAddress.
     *
     * @param domainRoot the ObjectName used for mbean in the caller's JMX domain that represent the root management resource
     * @param rootResource the root resource for the management model
     * @param name the ObjectName to resolve
     *
     * @return the PathAddress if it exists in the model, {@code null} otherwise
     */
    static PathAddress resolvePathAddress(final ObjectName domainRoot, final Resource rootResource, final ObjectName name) {
        String domain = domainRoot.getDomain();
        if (!name.getDomain().equals(domain)) {
            return null;
        }
        if (name.equals(domainRoot)) {
            return PathAddress.EMPTY_ADDRESS;
        }
        final Hashtable<String, String> properties = name.getKeyPropertyList();
        return searchPathAddress(PathAddress.EMPTY_ADDRESS, rootResource, properties);
    }

    private static PathAddress searchPathAddress(final PathAddress address, final Resource resource, final Map<String, String> properties) {
        if (properties.size() == 0) {
            return address;
        }
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            PathElement childElement = PathElement.pathElement(
                    replaceEscapedCharactersInKey(entry.getKey()),
                    replaceEscapedCharactersInValue(entry.getValue()));
            Resource child = resource.getChild(childElement);
            if (child != null) {
                Map<String, String> childProps = new HashMap<String, String>(properties);
                childProps.remove(entry.getKey());
                PathAddress foundAddr = searchPathAddress(address.append(childElement), child, childProps);
                if (foundAddr != null) {
                    return foundAddr;
                }
            }
        }
        return null;
    }

    /**
     * Straight conversion from an ObjectName to a PathAddress.
     *
     * There may not necessarily be a Resource at this path address (if that correspond to a pattern) but it must
     * match a model in the registry.
     *
     * @param domain the name of the caller's JMX domain
     * @param registry the root resource for the management model
     * @param name the ObjectName to convert
     *
     * @return the PathAddress, or {@code null} if no address matches the object name
     */
    static PathAddress toPathAddress(String domain, ImmutableManagementResourceRegistration registry, ObjectName name) {
        if (!name.getDomain().equals(domain)) {
            return PathAddress.EMPTY_ADDRESS;
        }
        if (name.equals(ModelControllerMBeanHelper.createRootObjectName(domain))) {
            return PathAddress.EMPTY_ADDRESS;
        }
        final Hashtable<String, String> properties = name.getKeyPropertyList();
        return searchPathAddress(PathAddress.EMPTY_ADDRESS, registry, properties);
    }

    /**
     * Straight conversion from an ObjectName to a PathAddress.
     *
     * There may not necessarily be a Resource at this path address (if that correspond to a pattern) but it must
     * match a model in the registry.

     * @param domainRoot the ObjectName used for mbean in the caller's JMX domain that represent the root management resource
     * @param registry the root resource for the management model
     * @param name the ObjectName to convert
     *
     * @return the PathAddress, or {@code null} if no address matches the object name
     */
    static PathAddress toPathAddress(final ObjectName domainRoot, ImmutableManagementResourceRegistration registry, ObjectName name) {
        String domain = domainRoot.getDomain();
        if (!name.getDomain().equals(domain)) {
            return PathAddress.EMPTY_ADDRESS;
        }
        if (name.equals(domainRoot)) {
            return PathAddress.EMPTY_ADDRESS;
        }
        final Hashtable<String, String> properties = name.getKeyPropertyList();
        return searchPathAddress(PathAddress.EMPTY_ADDRESS, registry, properties);
    }

    private static PathAddress searchPathAddress(PathAddress address, ImmutableManagementResourceRegistration registry, Map<String, String> properties) {
        if (properties.size() == 0) {
            return address;
        }
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            PathAddress childAddress = PathAddress.pathAddress(
                    replaceEscapedCharactersInKey(entry.getKey()),
                    replaceEscapedCharactersInValue(entry.getValue()));
            ImmutableManagementResourceRegistration subModel = registry.getSubModel(childAddress);
            if (subModel != null) {
                Map<String, String> childProps = new HashMap<String, String>(properties);
                childProps.remove(entry.getKey());
                PathAddress foundAddr = searchPathAddress(address.append(childAddress), subModel, childProps);
                if (foundAddr != null) {
                    return foundAddr;
                }
            }
        }
        return null;
    }

    private static void escapeKey(EscapedCharacter[] escapedCharacters, StringBuilder sb, String value, ObjectNameCreationContext context) {
        String escaped = context == null ? null : context.getCachedKey(value);
        if (escaped == null) {
            escaped = value;
            for (EscapedCharacter escapedCharacter : escapedCharacters) {
                escaped = escapedCharacter.escapeString(escaped);
            }
            if (context != null) {
                context.cacheKey(value, escaped);
            }
        }
        sb.append(escaped);
    }

    private static void escapeValue(final StringBuilder sb, final String value, ObjectNameCreationContext context) {
        String escaped = context == null ? null : context.getCachedValue(value);
        if (escaped == null) {
            escaped = value;
            for (EscapedCharacter escapedCharacter : ESCAPED_QUOTED_VALUE_CHARACTERS) {
                escaped = escapedCharacter.escapeString(escaped);
            }
            boolean quoted = !value.equals(escaped);
            if (!quoted) {
                // Must use quoted value if colon, comma are equals are included
                for (char c : value.toCharArray()) {
                    if (c == ':' || c == ',' || c == '=') {
                        quoted = true;
                        break;
                    }
                }
            }
            if (quoted) {
                escaped = "\"" + escaped + "\"";
            }
            if (context != null) {
                context.cacheValue(value, escaped);
            }
        }
        sb.append(escaped);
    }

    private static String replaceEscapedCharactersInKey(String escaped) {
        for (EscapedCharacter escapedCharacter : ESCAPED_KEY_CHARACTERS) {
            escaped = escapedCharacter.unescapeString(escaped);
        }
        return escaped;
    }

    private static String replaceEscapedCharactersInValue(final String escaped) {
        if (escaped.startsWith("\"") && escaped.endsWith("\"")) {
            String replaced = escaped.substring(1, escaped.length() - 1);
            // Unescape in reverse order so we deal with the \ itself last
            for (int i = ESCAPED_QUOTED_VALUE_CHARACTERS.length - 1; i >= 0; i--) {
                replaced = ESCAPED_QUOTED_VALUE_CHARACTERS[i].unescapeString(replaced);
            }
            return replaced;
        } else {
            return escaped;
        }
    }

    /**
     * Caches objects used in standard String.replaceAll operations
     * so they can be reused in string escaping/unescaping calls.
     */
    private static class EscapedCharacter {
        // Pattern that finds the string to escape
        private final Pattern pattern;
        // String to replace it with
        private final String quotedReplacement;
        // Pattern that finds the replacement
        private final Pattern reversePattern;
        // String to restore
        private final String reverseQuotedReplacement;

        EscapedCharacter(Character ch) {
            this(String.valueOf(ch), "%x" + Integer.toHexString(ch));
        }

        EscapedCharacter(String toReplace, String replacement) {
            this.pattern = Pattern.compile(toReplace, Pattern.LITERAL);
            this.quotedReplacement = Matcher.quoteReplacement(replacement);
            this.reversePattern = Pattern.compile(replacement, Pattern.LITERAL);
            this.reverseQuotedReplacement = Matcher.quoteReplacement(toReplace);
        }

        String escapeString(String toEscape) {
            return pattern.matcher(toEscape).replaceAll(quotedReplacement);
        }

        String unescapeString(String toUnescape) {
            return reversePattern.matcher(toUnescape).replaceAll(reverseQuotedReplacement);
        }
    }
}
