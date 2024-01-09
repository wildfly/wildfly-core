/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.launcher;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Stores arguments to be passed to the command line.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
class Arguments {

    private final Map<String, Collection<Argument>> map;

    Arguments() {
        this.map = new LinkedHashMap<>();
    }

    /**
     * Clears any arguments currently set.
     */
    public void clear() {
        map.clear();
    }

    /**
     * {@link #parse(String) Parses} the argument and adds it to the collection of arguments ignoring {@code null}
     * arguments.
     *
     * @param arg the argument to add
     */
    public void add(final String arg) {
        if (arg != null) {
            final Argument argument = parse(arg);
            add(argument);
        }
    }

    /**
     * Adds a key/value pair to the collection of arguments.
     * <p/>
     * If the key starts with {@code -D} it's assumed it's a system property argument and the prefix will be stripped
     * from the key when checking for uniqueness.
     *
     * @param key   the key for the argument
     * @param value the value of the argument which may be {@code null}
     */
    public void add(final String key, final String value) {
        if (key != null) {
            final Argument argument;
            if (key.startsWith("-D")) {
                if (value == null) {
                    argument = createSystemProperty(key);
                } else {
                    argument = createSystemProperty(key, value);
                }
            } else {
                if (value == null) {
                    argument = create(key);
                } else {
                    argument = create(key, value);
                }
            }
            add(argument);
        }
    }

    /**
     * Sets a key/value pair to the collection of arguments. This guarantees only one value will be assigned to the
     * argument key. A {@code null} value indicates the key should be removed.
     * <p/>
     * If the key starts with {@code -D} it's assumed it's a system property argument and the prefix will be stripped
     * from the key when checking for uniqueness.
     *
     * @param key   the key for the argument
     * @param value the value of the argument which may be {@code null}
     */
    public void set(final String key, final String value) {
        if (key != null) {
            if (value == null) {
                map.remove(key);
            } else {
                final Argument argument;
                if (key.startsWith("-D")) {
                    argument = createSystemProperty(key, value);
                } else {
                    argument = create(key, value);
                }
                set(argument);
            }
        }
    }

    /**
     * Sets an argument to the collection of arguments. This guarantees only one value will be assigned to the
     * argument key.
     *
     * @param argument the argument to add
     */
    public void set(final Argument argument) {
        if (argument != null) {
            map.put(argument.getKey(), Collections.singleton(argument));
        }
    }

    /**
     * Parses each argument and adds them to the collection of arguments ignoring any {@code null} values.
     *
     * @param args the arguments to add
     *
     * @see #add(String)
     */
    public void addAll(final String... args) {
        if (args != null) {
            for (String arg : args) {
                add(arg);
            }
        }
    }

    /**
     * Gets the first value for the key.
     *
     * @param key the key to check for the value
     *
     * @return the value or {@code null} if the key is not found or the value was {@code null}
     */
    public String get(final String key) {
        final Collection<Argument> args = map.get(key);
        if (args != null) {
            return args.iterator().hasNext() ? args.iterator().next().getValue() : null;
        }
        return null;
    }

    /**
     * Gets the value for the key.
     *
     * @param key the key to check for the value
     *
     * @return the value or an empty collection if no values were set
     */
    public Collection<Argument> getArguments(final String key) {
        final Collection<Argument> args = map.get(key);
        if (args != null) {
            return new ArrayList<>(args);
        }
        return Collections.emptyList();
    }

    /**
     * Removes the argument from the collection of arguments.
     *
     * @param key they key of the argument to remove
     *
     * @return the arguments or {@code null} if the argument was not found
     */
    public Collection<Argument> remove(final String key) {
        return map.remove(key);
    }

    /**
     * Returns the arguments as a list in their command line form.
     *
     * @return the arguments for the command line
     */
    public List<String> asList() {
        final List<String> result = new ArrayList<>();
        for (Collection<Argument> args : map.values()) {
            for (Argument arg : args) {
                result.add(arg.asCommandLineArgument());
            }
        }
        return result;
    }

    /**
     * Adds the argument to the collection of arguments ignoring {@code null} values.
     *
     * @param argument the argument to add
     */
    void add(final Argument argument) {
        if (argument != null) {
            if (argument.multipleValuesAllowed()) {
                final Collection<Argument> arguments = map.computeIfAbsent(argument.getKey(), k -> new ArrayList<>());
                arguments.add(argument);
            } else {
                set(argument);
            }
        }
    }

    /**
     * Attempts to parse the argument into a key value pair. The separator is assumed to be {@code =}. If the value
     * starts with a {@code -D} it's assumed to be a system property.
     * <p/>
     * If the argument is not a traditional key/value pair separated by an {@code =} the arguments key will be the full
     * argument passed in and the arguments value will be {@code null}.
     *
     * @param arg the argument to parse
     *
     * @return the parsed argument
     */
    public static Argument parse(final String arg) {
        if (arg.startsWith("-D")) {
            final String key;
            final String value;
            // Check for an =
            final int index = arg.indexOf('=');
            if (index == -1) {
                key = arg.substring(2);
                value = null;
            } else {
                key = arg.substring(2, index);
                if (arg.length() < (index + 1)) {
                    value = null;
                } else {
                    value = arg.substring(index + 1);
                }
            }
            return new SystemPropertyArgument(key, value);
        }
        final String key;
        final String value;
        // Check for an =
        final int index = arg.indexOf('=');
        if (index == -1) {
            key = arg;
            value = null;
        } else {
            key = arg.substring(0, index);
            if (arg.length() < (index + 1)) {
                value = null;
            } else {
                value = arg.substring(index + 1);
            }
        }
        return new DefaultArgument(key, value);
    }

    private static Argument create(final String arg) {
        return new DefaultArgument(arg, null);
    }

    private static Argument create(final String key, final String value) {
        return new DefaultArgument(key, value);
    }

    private static Argument createSystemProperty(final String arg) {
        return new SystemPropertyArgument(arg, null);
    }

    private static Argument createSystemProperty(final String key, final String value) {
        return new SystemPropertyArgument(key, value);
    }

    /**
     * Represents a command line argument in a possible key/value pair.
     */
    public abstract static class Argument {
        private final String key;
        private final String value;

        protected Argument(final String key, final String value) {
            this.key = key;
            this.value = value;
        }

        /**
         * They key to the command line argument which may be the full argument.
         *
         * @return the key
         */
        public String getKey() {
            return key;
        }

        /**
         * The optional value for the command line argument.
         *
         * @return the value or {@code null}
         */
        public String getValue() {
            return value;
        }

        /**
         * Indicates whether or not multiple values are allowed for the argument. In the case of system properties only
         * one value should be set for the property.
         *
         * @return {@code true} if multiple values should be allowed for an argument, otherwise {@code false}
         */
        public boolean multipleValuesAllowed() {
            return true;
        }

        /**
         * The argument formatted for the command line.
         *
         * @return the command line argument
         */
        public abstract String asCommandLineArgument();

        @Override
        public String toString() {
            return asCommandLineArgument();
        }
    }


    private static final class SystemPropertyArgument extends Argument {
        private final String cliArg;

        public SystemPropertyArgument(final String key, final String value) {
            super(key, value);
            if (value != null) {
                cliArg = "-D" + key + "=" + value;
            } else {
                cliArg = "-D" + key;
            }
        }

        @Override
        public boolean multipleValuesAllowed() {
            return false;
        }

        @Override
        public String asCommandLineArgument() {
            return cliArg;
        }
    }

    private static final class DefaultArgument extends Argument {
        private final String cliArg;

        public DefaultArgument(final String key, final String value) {
            super(key, value);
            if (value != null) {
                cliArg = key + "=" + value;
            } else {
                cliArg = key;
            }
        }

        @Override
        public String asCommandLineArgument() {
            return cliArg;
        }
    }
}
