/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2023 Red Hat, Inc., and individual contributors
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

package org.wildfly.core.logmanager.config;

import static java.lang.Character.isJavaIdentifierPart;
import static java.lang.Character.isJavaIdentifierStart;
import static java.lang.Character.isWhitespace;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TimeZone;
import java.util.logging.ErrorManager;
import java.util.logging.Filter;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;

import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.Logger;
import org.jboss.logmanager.configuration.ContextConfiguration;
import org.jboss.logmanager.filters.AcceptAllFilter;
import org.jboss.logmanager.filters.AllFilter;
import org.jboss.logmanager.filters.AnyFilter;
import org.jboss.logmanager.filters.DenyAllFilter;
import org.jboss.logmanager.filters.InvertFilter;
import org.jboss.logmanager.filters.LevelChangingFilter;
import org.jboss.logmanager.filters.LevelFilter;
import org.jboss.logmanager.filters.LevelRangeFilter;
import org.jboss.logmanager.filters.RegexFilter;
import org.jboss.logmanager.filters.SubstituteFilter;
import org.wildfly.core.logmanager.WildFlyLogContextSelector;

/**
 * A log context configuration.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
// TODO (jrp) This should become not an interface and should a class that extends ContextConfiguration.
// TODO (jrp) We then just have stuff like LoggerConfiguration addLoggerConfiguration(loggerName)
// TODO (jrp) Then each *Configuration should likely extend ConfigurationResource
// TODO (jrp) this needs to be renamed to WildFlContextConfiguration, but that will require a refactor of the subsystem as well
public class LogContextConfiguration extends DelegatingContextConfiguration {

    private final Map<String, LoggerConfigurationImpl> loggers = new HashMap<>();
    private final Map<String, HandlerConfigurationImpl> handlers = new HashMap<>();
    private final Map<String, FormatterConfigurationImpl> formatters = new HashMap<>();
    private final Map<String, FilterConfigurationImpl> filters = new HashMap<>();
    private final Map<String, ErrorManagerConfigurationImpl> errorManagers = new HashMap<>();
    private final Map<String, PojoConfigurationImpl> pojos = new HashMap<>();

    private final Deque<ConfigAction<?>> transactionState = new ArrayDeque<>();
    private final Map<String, Deque<ConfigAction<?>>> postConfigurationTransactionState = new LinkedHashMap<>();
    private final Deque<ConfigAction<?>> preparedTransactions = new ArrayDeque<>();

    private boolean prepared = false;

    // TODO (jrp) there are better ways to do this
    private static final ObjectProducer ACCEPT_PRODUCER = new SimpleObjectProducer(AcceptAllFilter.getInstance());
    private static final ObjectProducer DENY_PRODUCER = new SimpleObjectProducer(DenyAllFilter.getInstance());

    /**
     * Creates a new context configuration.
     *
     * @param context
     */
    public LogContextConfiguration(final LogContext context) {
        super(new ContextConfiguration(context));
    }

    public LogContextConfiguration(final ContextConfiguration delegate) {
        super(delegate);
    }

    public static LogContextConfiguration getInstance() {
        return getInstance(LogContext.getLogContext());
    }

    // TODO (jrp) what do we do here? Just get the LogContext and then use getInstance(LogContext)?
    public static LogContextConfiguration getInstance(final String name) {
        // TODO (jrp) we'll need a way to get this for logging profiles as well.
        // TODO (jrp) what do we do if this is null? It shouldn't happen, but that means this has been accessed before
        // TODO (jrp) the WildFlyConfiguratorFactory has been completed

        // TODO (jrp) I think what we should do is move the LoggingProfileSelector and the WildFlyContextSelector here
        final var selector = WildFlyLogContextSelector.getContextSelector();
        final var profileContext = selector.getProfileContext(name);
        return profileContext == null ? getInstance(selector.getLogContext()) : getInstance(profileContext);
    }

    public static LogContextConfiguration getInstance(final LogContext logContext) {
        var configuration = logContext.getAttachment(ContextConfiguration.CONTEXT_CONFIGURATION_KEY);
        if (configuration == null) {
            configuration = new LogContextConfiguration(logContext);
            final var appearing = logContext.attachIfAbsent(ContextConfiguration.CONTEXT_CONFIGURATION_KEY, configuration);
            if (appearing != null) {
                configuration = appearing;
            }
        }
        // TODO (jrp) this is not thread safe
        if (!(configuration instanceof LogContextConfiguration)) {
            configuration = new LogContextConfiguration(configuration);
            logContext.attach(ContextConfiguration.CONTEXT_CONFIGURATION_KEY, configuration);
        }
        return (LogContextConfiguration) configuration;
    }

    public static LogContextConfiguration create(final LogContext context, final Properties properties) {
        // TODO (jrp) this should likely be attached to the logContext
        final LogContextConfiguration configuration = new LogContextConfiguration(context);
        PropertyLogContextConfiguration.configure(configuration, properties);
        return configuration;
    }

    public static LogContextConfiguration getEmbedded() {
        final LogContext logContext = WildFlyLogContextSelector.getEmbeddedContextSelector().getLogContext();
        var configuration = logContext.getAttachment(ContextConfiguration.CONTEXT_CONFIGURATION_KEY);
        // If there is not configuration attached, then we need to configure it based on the possible properties
        if (configuration == null) {
            configuration = new LogContextConfiguration(logContext);
            final var appearing = logContext.attachIfAbsent(ContextConfiguration.CONTEXT_CONFIGURATION_KEY, configuration);
            if (appearing != null) {
                configuration = appearing;
            }
            // TODO (jrp) this is not thread safe
            if (!(configuration instanceof LogContextConfiguration)) {
                configuration = new LogContextConfiguration(configuration);
                logContext.attach(ContextConfiguration.CONTEXT_CONFIGURATION_KEY, configuration);
            }
            PropertyLogContextConfiguration.configure((LogContextConfiguration) configuration, (InputStream) null);
        }
        return (LogContextConfiguration) configuration;
    }

    // TODO (jrp) rename this to getContext() for refactoring
    public LogContext getLogContext() {
        return getContext();
    }

    public LoggerConfiguration addLoggerConfiguration(final String loggerName) {
        if (loggers.containsKey(loggerName)) {
            throw new IllegalArgumentException(String.format("Logger \"%s\" already exists", loggerName));
        }
        final LoggerConfigurationImpl loggerConfiguration = new LoggerConfigurationImpl(loggerName, this);
        loggers.put(loggerName, loggerConfiguration);
        transactionState.addLast(new ConfigAction<Logger>() {
            public Logger validate() throws IllegalArgumentException {
                return getLogger(loggerName);
            }

            public void applyPreCreate(final Logger param) {
            }

            public void applyPostCreate(Logger param) {
            }

            public void rollback() {
                loggers.remove(loggerName);
            }
        });
        return loggerConfiguration;
    }

    public boolean removeLoggerConfiguration(final String loggerName) {
        final LoggerConfigurationImpl removed = loggers.remove(loggerName);
        if (removed != null) {
            transactionState.addLast(removed.getRemoveAction());
            removed.setRemoved();
            return true;
        } else {
            return false;
        }
    }

    public LoggerConfiguration getLoggerConfiguration(final String loggerName) {
        return loggers.get(loggerName);
    }

    public List<String> getLoggerNames() {
        return new ArrayList<>(loggers.keySet());
    }

    public HandlerConfiguration addHandlerConfiguration(final String moduleName, final String className, final String handlerName, final String... constructorProperties) {
        if (handlers.containsKey(handlerName)) {
            throw new IllegalArgumentException(String.format("Handler \"%s\" already exists", handlerName));
        }
        final HandlerConfigurationImpl handlerConfiguration = new HandlerConfigurationImpl(this, handlerName, moduleName, className, constructorProperties);
        handlers.put(handlerName, handlerConfiguration);
        addAction(handlerConfiguration.getConstructAction());
        return handlerConfiguration;
    }

    public boolean removeHandlerConfiguration(final String handlerName) {
        final HandlerConfigurationImpl removed = handlers.remove(handlerName);
        if (removed != null) {
            transactionState.addLast(removed.getRemoveAction());
            removed.setRemoved();
            return true;
        } else {
            return false;
        }
    }

    public HandlerConfiguration getHandlerConfiguration(final String handlerName) {
        return handlers.get(handlerName);
    }

    public List<String> getHandlerNames() {
        return new ArrayList<>(handlers.keySet());
    }

    public FormatterConfiguration addFormatterConfiguration(final String moduleName, final String className, final String formatterName, final String... constructorProperties) {
        if (formatters.containsKey(formatterName)) {
            throw new IllegalArgumentException(String.format("Formatter \"%s\" already exists", formatterName));
        }
        final FormatterConfigurationImpl formatterConfiguration = new FormatterConfigurationImpl(this, formatterName, moduleName, className, constructorProperties);
        formatters.put(formatterName, formatterConfiguration);
        addAction(formatterConfiguration.getConstructAction());
        return formatterConfiguration;
    }

    public boolean removeFormatterConfiguration(final String formatterName) {
        final FormatterConfigurationImpl removed = formatters.remove(formatterName);
        if (removed != null) {
            transactionState.addLast(removed.getRemoveAction());
            removed.setRemoved();
            return true;
        } else {
            return false;
        }
    }

    public FormatterConfiguration getFormatterConfiguration(final String formatterName) {
        return formatters.get(formatterName);
    }

    public List<String> getFormatterNames() {
        return new ArrayList<>(formatters.keySet());
    }

    public FilterConfiguration addFilterConfiguration(final String moduleName, final String className, final String filterName, final String... constructorProperties) {
        if (filters.containsKey(filterName)) {
            throw new IllegalArgumentException(String.format("Filter \"%s\" already exists", filterName));
        }
        final FilterConfigurationImpl filterConfiguration = new FilterConfigurationImpl(this, filterName, moduleName, className, constructorProperties);
        filters.put(filterName, filterConfiguration);
        addAction(filterConfiguration.getConstructAction());
        return filterConfiguration;
    }

    public boolean removeFilterConfiguration(final String filterName) {
        final FilterConfigurationImpl removed = filters.remove(filterName);
        if (removed != null) {
            transactionState.addLast(removed.getRemoveAction());
            removed.setRemoved();
            return true;
        } else {
            return false;
        }
    }

    public FilterConfiguration getFilterConfiguration(final String filterName) {
        return filters.get(filterName);
    }

    public List<String> getFilterNames() {
        return new ArrayList<>(filters.keySet());
    }

    public ErrorManagerConfiguration addErrorManagerConfiguration(final String moduleName, final String className, final String errorManagerName, final String... constructorProperties) {
        if (errorManagers.containsKey(errorManagerName)) {
            throw new IllegalArgumentException(String.format("ErrorManager \"%s\" already exists", errorManagerName));
        }
        final ErrorManagerConfigurationImpl errorManagerConfiguration = new ErrorManagerConfigurationImpl(this, errorManagerName, moduleName, className, constructorProperties);
        errorManagers.put(errorManagerName, errorManagerConfiguration);
        addAction(errorManagerConfiguration.getConstructAction());
        return errorManagerConfiguration;
    }

    public boolean removeErrorManagerConfiguration(final String errorManagerName) {
        final ErrorManagerConfigurationImpl removed = errorManagers.remove(errorManagerName);
        if (removed != null) {
            transactionState.addLast(removed.getRemoveAction());
            removed.setRemoved();
            return true;
        } else {
            return false;
        }
    }

    public ErrorManagerConfiguration getErrorManagerConfiguration(final String errorManagerName) {
        return errorManagers.get(errorManagerName);
    }

    public List<String> getErrorManagerNames() {
        return new ArrayList<>(errorManagers.keySet());
    }

    public PojoConfiguration addPojoConfiguration(final String moduleName, final String className, final String pojoName, final String... constructorProperties) {
        if (pojos.containsKey(pojoName)) {
            throw new IllegalArgumentException(String.format("POJO \"%s\" already exists", pojoName));
        }
        final PojoConfigurationImpl pojoConfiguration = new PojoConfigurationImpl(this, pojoName, moduleName, className, constructorProperties);
        pojos.put(pojoName, pojoConfiguration);
        transactionState.addLast(pojoConfiguration.getConstructAction());
        return pojoConfiguration;
    }

    public boolean removePojoConfiguration(final String pojoName) {
        final PojoConfigurationImpl removed = pojos.remove(pojoName);
        if (removed != null) {
            transactionState.addLast(removed.getRemoveAction());
            removed.setRemoved();
            return true;
        }
        return false;
    }

    public PojoConfiguration getPojoConfiguration(final String pojoName) {
        return pojos.get(pojoName);
    }

    public List<String> getPojoNames() {
        return new ArrayList<>(pojos.keySet());
    }

    public void prepare() {
        doPrepare(transactionState);
        for (Deque<ConfigAction<?>> items : postConfigurationTransactionState.values()) {
            doPrepare(items);
        }
        prepared = true;
    }

    public void commit() {
        if (!prepared) {
            prepare();
        }
        clear();
    }

    @SuppressWarnings("unchecked")
    private static <T> void doApplyPreCreate(ConfigAction<T> action, Object arg) {
        try {
            action.applyPreCreate((T) arg);
        } catch (Throwable ignored) {
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> void doApplyPostCreate(ConfigAction<T> action, Object arg) {
        try {
            action.applyPostCreate((T) arg);
        } catch (Throwable ignored) {
        }
    }

    public void forget() {
        doForget(transactionState);
        doForget(preparedTransactions);
        for (Deque<ConfigAction<?>> items : postConfigurationTransactionState.values()) {
            doForget(items);
        }
        clear();
    }

    private void clear() {
        prepared = false;
        postConfigurationTransactionState.clear();
        transactionState.clear();
        preparedTransactions.clear();
    }

    private void doPrepare(final Deque<ConfigAction<?>> transactionState) {
        List<Object> items = new ArrayList<>();
        for (ConfigAction<?> action : transactionState) {
            items.add(action.validate());
            preparedTransactions.add(action);
        }
        Iterator<Object> iterator = items.iterator();
        for (ConfigAction<?> action : transactionState) {
            doApplyPreCreate(action, iterator.next());
        }
        iterator = items.iterator();
        for (ConfigAction<?> action : transactionState) {
            doApplyPostCreate(action, iterator.next());
        }
        transactionState.clear();
    }

    private void doForget(final Deque<ConfigAction<?>> transactionState) {
        Iterator<ConfigAction<?>> iterator = transactionState.descendingIterator();
        while (iterator.hasNext()) {
            final ConfigAction<?> action = iterator.next();
            try {
                action.rollback();
            } catch (Throwable ignored) {
            }
        }
    }

    void addAction(final ConfigAction<?> action) {
        transactionState.addLast(action);
    }

    /**
     * Adds or replaces the post configuration actions for the configuration identified by the {@code name} parameter.
     *
     * @param name    the name of the configuration
     * @param actions the actions to be invoked after the properties have been set
     */
    void addPostConfigurationActions(final String name, final Deque<ConfigAction<?>> actions) {
        if (actions != null && !actions.isEmpty()) {
            postConfigurationTransactionState.put(name, actions);
        }
    }

    /**
     * Removes the post configuration actions for the configuration identified by the {@code name} parameter.
     *
     * @param name the name of the configuration
     */
    Deque<?> removePostConfigurationActions(final String name) {
        return postConfigurationTransactionState.remove(name);
    }

    /**
     * Checks to see if configuration actions have already been defined for the configuration.
     *
     * @param name the name of the configuration
     *
     * @return {@code true} if the configuration actions have been defined, otherwise {@code false}
     */
    boolean postConfigurationActionsExist(final String name) {
        return postConfigurationTransactionState.containsKey(name);
    }

    // TODO (jrp) we should use the ValueResolver
    ObjectProducer getValue(final Class<?> objClass, final String propertyName, final Class<?> paramType, final ValueExpression<String> valueExpression, final boolean immediate) {
        if (valueExpression == null || valueExpression.getResolvedValue() == null) {
            if (paramType.isPrimitive()) {
                throw new IllegalArgumentException(String.format("Cannot assign null value to primitive property \"%s\" of %s", propertyName, objClass));
            }
            return ObjectProducer.NULL_PRODUCER;
        }
        final String resolvedValue = valueExpression.getResolvedValue();
        final String trimmedValue = resolvedValue.trim();
        if (paramType == String.class) {
            // Don't use the trimmed value for strings
            return new SimpleObjectProducer(resolvedValue);
        } else if (paramType == Handler.class) {
            if (!handlers.containsKey(trimmedValue) || immediate && !hasHandler(trimmedValue)) {
                throw new IllegalArgumentException(String.format("No handler named \"%s\" is defined", trimmedValue));
            }
            if (immediate) {
                return new SimpleObjectProducer(getHandler(trimmedValue));
            } else {
                return () -> getHandler(trimmedValue);
            }
        } else if (paramType == Filter.class) {
            return resolveFilter(trimmedValue, immediate);
        } else if (paramType == Formatter.class) {
            if (!formatters.containsKey(trimmedValue) || immediate && !hasFormatter(trimmedValue)) {
                throw new IllegalArgumentException(String.format("No formatter named \"%s\" is defined", trimmedValue));
            }
            if (immediate) {
                return new SimpleObjectProducer(getFormatter(trimmedValue));
            } else {
                return () -> getFormatter(trimmedValue);
            }
        } else if (paramType == ErrorManager.class) {
            if (!errorManagers.containsKey(trimmedValue) || immediate && !hasErrorManager(trimmedValue)) {
                throw new IllegalArgumentException(String.format("No error manager named \"%s\" is defined", trimmedValue));
            }
            if (immediate) {
                return new SimpleObjectProducer(getErrorManager(trimmedValue));
            } else {
                return () -> getErrorManager(trimmedValue);
            }
        } else if (paramType == Level.class) {
            return new SimpleObjectProducer(LogContext.getSystemLogContext().getLevelForName(trimmedValue));
        } else if (paramType == java.util.logging.Logger.class) {
            return new SimpleObjectProducer(LogContext.getSystemLogContext().getLogger(trimmedValue));
        } else if (paramType == boolean.class || paramType == Boolean.class) {
            return new SimpleObjectProducer(Boolean.valueOf(trimmedValue));
        } else if (paramType == byte.class || paramType == Byte.class) {
            return new SimpleObjectProducer(Byte.valueOf(trimmedValue));
        } else if (paramType == short.class || paramType == Short.class) {
            return new SimpleObjectProducer(Short.valueOf(trimmedValue));
        } else if (paramType == int.class || paramType == Integer.class) {
            return new SimpleObjectProducer(Integer.valueOf(trimmedValue));
        } else if (paramType == long.class || paramType == Long.class) {
            return new SimpleObjectProducer(Long.valueOf(trimmedValue));
        } else if (paramType == float.class || paramType == Float.class) {
            return new SimpleObjectProducer(Float.valueOf(trimmedValue));
        } else if (paramType == double.class || paramType == Double.class) {
            return new SimpleObjectProducer(Double.valueOf(trimmedValue));
        } else if (paramType == char.class || paramType == Character.class) {
            return new SimpleObjectProducer(Character.valueOf(trimmedValue.length() > 0 ? trimmedValue.charAt(0) : 0));
        } else if (paramType == TimeZone.class) {
            return new SimpleObjectProducer(TimeZone.getTimeZone(trimmedValue));
        } else if (paramType == Charset.class) {
            return new SimpleObjectProducer(Charset.forName(trimmedValue));
        } else if (paramType.isEnum()) {
            return new SimpleObjectProducer(Enum.valueOf(paramType.asSubclass(Enum.class), trimmedValue));
        } else if (pojos.containsKey(trimmedValue)) {
            return () -> getObject(trimmedValue);
        } else {
            throw new IllegalArgumentException("Unknown parameter type for property " + propertyName + " on " + objClass);
        }
    }

    Map<String, FilterConfigurationImpl> getFilterConfigurations() {
        return filters;
    }

    Map<String, ErrorManagerConfigurationImpl> getErrorManagerConfigurations() {
        return errorManagers;
    }

    Map<String, HandlerConfigurationImpl> getHandlerConfigurations() {
        return handlers;
    }

    Map<String, FormatterConfigurationImpl> getFormatterConfigurations() {
        return formatters;
    }

    Map<String, LoggerConfigurationImpl> getLoggerConfigurations() {
        return loggers;
    }

    Map<String, PojoConfigurationImpl> getPojoConfigurations() {
        return pojos;
    }

    private static List<String> tokens(String source) {
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
                            throw new IllegalArgumentException("Truncated filter expression string");
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
                                throw new IllegalArgumentException("Invalid escape found in filter expression string");
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

    private ObjectProducer parseFilterExpression(Iterator<String> iterator, boolean outermost, final boolean immediate) {
        if (!iterator.hasNext()) {
            if (outermost) {
                return ObjectProducer.NULL_PRODUCER;
            }
            throw endOfExpression();
        }
        final String token = iterator.next();
        if ("accept".equals(token)) {
            return ACCEPT_PRODUCER;
        } else if ("deny".equals(token)) {
            return DENY_PRODUCER;
        } else if ("not".equals(token)) {
            expect("(", iterator);
            final ObjectProducer nested = parseFilterExpression(iterator, false, immediate);
            expect(")", iterator);
            return new ObjectProducer() {
                public Object getObject() {
                    return new InvertFilter((Filter) nested.getObject());
                }
            };
        } else if ("all".equals(token)) {
            expect("(", iterator);
            final List<ObjectProducer> producers = new ArrayList<>();
            do {
                producers.add(parseFilterExpression(iterator, false, immediate));
            } while (expect(",", ")", iterator));
            return new ObjectProducer() {
                public Object getObject() {
                    final int length = producers.size();
                    final Filter[] filters = new Filter[length];
                    for (int i = 0; i < length; i++) {
                        filters[i] = (Filter) producers.get(i).getObject();
                    }
                    return new AllFilter(filters);
                }
            };
        } else if ("any".equals(token)) {
            expect("(", iterator);
            final List<ObjectProducer> producers = new ArrayList<>();
            do {
                producers.add(parseFilterExpression(iterator, false, immediate));
            } while (expect(",", ")", iterator));
            return new ObjectProducer() {
                public Object getObject() {
                    final int length = producers.size();
                    final Filter[] filters = new Filter[length];
                    for (int i = 0; i < length; i++) {
                        filters[i] = (Filter) producers.get(i).getObject();
                    }
                    return new AnyFilter(filters);
                }
            };
        } else if ("levelChange".equals(token)) {
            expect("(", iterator);
            final String levelName = expectName(iterator);
            final Level level = getContext().getLevelForName(levelName);
            expect(")", iterator);
            return new SimpleObjectProducer(new LevelChangingFilter(level));
        } else if ("levels".equals(token)) {
            expect("(", iterator);
            final Set<Level> levels = new HashSet<Level>();
            do {
                levels.add(getContext().getLevelForName(expectName(iterator)));
            } while (expect(",", ")", iterator));
            return new SimpleObjectProducer(new LevelFilter(levels));
        } else if ("levelRange".equals(token)) {
            final boolean minInclusive = expect("[", "(", iterator);
            final Level minLevel = getContext().getLevelForName(expectName(iterator));
            expect(",", iterator);
            final Level maxLevel = getContext().getLevelForName(expectName(iterator));
            final boolean maxInclusive = expect("]", ")", iterator);
            return new SimpleObjectProducer(new LevelRangeFilter(minLevel, minInclusive, maxLevel, maxInclusive));
        } else if ("match".equals(token)) {
            expect("(", iterator);
            final String pattern = expectString(iterator);
            expect(")", iterator);
            return new SimpleObjectProducer(new RegexFilter(pattern));
        } else if ("substitute".equals(token)) {
            expect("(", iterator);
            final String pattern = expectString(iterator);
            expect(",", iterator);
            final String replacement = expectString(iterator);
            expect(")", iterator);
            return new SimpleObjectProducer(new SubstituteFilter(pattern, replacement, false));
        } else if ("substituteAll".equals(token)) {
            expect("(", iterator);
            final String pattern = expectString(iterator);
            expect(",", iterator);
            final String replacement = expectString(iterator);
            expect(")", iterator);
            return new SimpleObjectProducer(new SubstituteFilter(pattern, replacement, true));
        } else {
            if (!filters.containsKey(token) || immediate && !hasFilter(token)) {
                throw new IllegalArgumentException(String.format("No filter named \"%s\" is defined", token));
            }
            if (immediate) {
                return new SimpleObjectProducer(getFilter(token));
            } else {
                return () -> getFilter(token);
            }
        }
    }

    private static String expectName(Iterator<String> iterator) {
        if (iterator.hasNext()) {
            final String next = iterator.next();
            if (isJavaIdentifierStart(next.codePointAt(0))) {
                return next;
            }
        }
        throw new IllegalArgumentException("Expected identifier next in filter expression");
    }

    private static String expectString(Iterator<String> iterator) {
        if (iterator.hasNext()) {
            final String next = iterator.next();
            if (next.codePointAt(0) == '"') {
                return next.substring(1);
            }
        }
        throw new IllegalArgumentException("Expected string next in filter expression");
    }

    private static boolean expect(String trueToken, String falseToken, Iterator<String> iterator) {
        final boolean hasNext = iterator.hasNext();
        final String next = hasNext ? iterator.next() : null;
        final boolean result;
        if (!hasNext || !((result = trueToken.equals(next)) || falseToken.equals(next))) {
            throw new IllegalArgumentException("Expected '" + trueToken + "' or '" + falseToken + "' next in filter expression");
        }
        return result;
    }

    private static void expect(String token, Iterator<String> iterator) {
        if (!iterator.hasNext() || !token.equals(iterator.next())) {
            throw new IllegalArgumentException("Expected '" + token + "' next in filter expression");
        }
    }

    private static IllegalArgumentException endOfExpression() {
        return new IllegalArgumentException("Unexpected end of filter expression");
    }

    private ObjectProducer resolveFilter(String expression, final boolean immediate) {
        if (expression == null) {
            return ObjectProducer.NULL_PRODUCER;
        }
        // First check for a defined filter
        if (filters.containsKey(expression)) {
            if (immediate) {
                return new SimpleObjectProducer(getFilter(expression));
            } else {
                return () -> getFilter(expression);
            }
        }
        final Iterator<String> iterator = tokens(expression).iterator();
        final ObjectProducer result = parseFilterExpression(iterator, true, immediate);
        if (iterator.hasNext()) {
            throw new IllegalArgumentException("Extra data after filter expression");
        }
        return result;
    }

    ObjectProducer resolveFilter(String expression) {
        return resolveFilter(expression, false);
    }

    @Override
    public void close() throws Exception {
        super.close();
        loggers.clear();
        handlers.clear();
        filters.clear();
        formatters.clear();
        errorManagers.clear();
        pojos.clear();
    }
}
