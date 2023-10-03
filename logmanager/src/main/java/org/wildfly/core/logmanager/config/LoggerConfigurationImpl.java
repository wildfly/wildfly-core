/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.logmanager.config;

import static java.util.Arrays.asList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.logging.Filter;
import java.util.logging.Handler;
import java.util.logging.Level;

import org.jboss.logmanager.Logger;
import org.jboss.logmanager.configuration.ConfigurationResource;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class LoggerConfigurationImpl extends AbstractBasicConfiguration<Logger, LoggerConfigurationImpl> implements LoggerConfiguration {
    private ValueExpression<String> filter;
    private ValueExpression<Boolean> useParentFilters;
    private ValueExpression<Boolean> useParentHandlers;
    private ValueExpression<String> level;
    private final List<String> handlerNames = new ArrayList<>(0);

    LoggerConfigurationImpl(final String name, final LogContextConfiguration configuration) {
        super(name, configuration, configuration.getLoggerConfigurations());
    }

    public String getFilter() {
        return getFilterValueExpression().getResolvedValue();
    }

    @Override
    public ValueExpression<String> getFilterValueExpression() {
        return filter == null ? ValueExpression.NULL_STRING_EXPRESSION : filter;
    }

    public void setFilter(final String filter) {
        setFilter(ValueExpression.STRING_RESOLVER.resolve(filter));
    }

    @Override
    public void setFilter(final String expression, final String value) {
        setFilter(new ValueExpressionImpl<>(expression, value));
    }

    private void setFilter(final ValueExpression<String> valueExpression) {
        final ValueExpression<String> oldFilterName = this.filter;
        this.filter = valueExpression;
        final String filterName = valueExpression.getResolvedValue();
        final LogContextConfiguration configuration = getConfiguration();
        configuration.addAction(new ConfigAction<ObjectProducer>() {
            public ObjectProducer validate() throws IllegalArgumentException {
                return configuration.resolveFilter(filterName);
            }

            public void applyPreCreate(final ObjectProducer param) {
            }

            public void applyPostCreate(final ObjectProducer param) {
                configuration.getLogger(getName()).setFilter((Filter) param.getObject());
            }

            public void rollback() {
                filter = oldFilterName;
            }
        });
    }

    @Override
    public Boolean getUseParentFilters() {
        return getUseParentFiltersValueExpression().getResolvedValue();
    }

    @Override
    public ValueExpression<Boolean> getUseParentFiltersValueExpression() {
        return useParentFilters;
    }

    @Override
    public void setUseParentFilters(final Boolean value) {
        setUseParentFilters(new ValueExpressionImpl<>(null, value));
    }

    @Override
    public void setUseParentFilters(final String expression) {
        setUseParentFilters(ValueExpression.BOOLEAN_RESOLVER.resolve(expression));
    }

    @Override
    public void setUseParentFilters(final String expression, final Boolean value) {
        setUseParentFilters(new ValueExpressionImpl<>(expression, value));
    }

    private void setUseParentFilters(final ValueExpression<Boolean> valueExpression) {
        final ValueExpression<Boolean> oldUseParentFilters = this.useParentFilters;
        this.useParentFilters = valueExpression;
        final Boolean useParentFilters = valueExpression.getResolvedValue();
        final LogContextConfiguration configuration = getConfiguration();
        configuration.addAction(new ConfigAction<Void>() {
            public Void validate() throws IllegalArgumentException {
                return null;
            }

            public void applyPreCreate(final Void param) {
            }

            public void applyPostCreate(final Void param) {
                if (useParentFilters != null)
                    configuration.getLogger(getName()).setUseParentFilters(useParentFilters);
            }

            public void rollback() {
                LoggerConfigurationImpl.this.useParentFilters = oldUseParentFilters;
            }
        });
    }


    public Boolean getUseParentHandlers() {
        return getUseParentHandlersValueExpression().getResolvedValue();
    }

    @Override
    public ValueExpression<Boolean> getUseParentHandlersValueExpression() {
        return useParentHandlers == null ? ValueExpression.NULL_BOOLEAN_EXPRESSION : useParentHandlers;
    }

    public void setUseParentHandlers(final Boolean useParentHandlers) {
        setUseParentHandlers(new ValueExpressionImpl<>(null, useParentHandlers));
    }

    @Override
    public void setUseParentHandlers(final String expression) {
        setUseParentHandlers(ValueExpression.BOOLEAN_RESOLVER.resolve(expression));
    }

    @Override
    public void setUseParentHandlers(final String expression, final Boolean value) {
        setUseParentHandlers(new ValueExpressionImpl<>(expression, value));
    }

    private void setUseParentHandlers(final ValueExpression<Boolean> valueExpression) {
        final ValueExpression<Boolean> oldUseParentHandlers = this.useParentHandlers;
        this.useParentHandlers = valueExpression;
        final Boolean useParentHandlers = valueExpression.getResolvedValue();
        final LogContextConfiguration configuration = getConfiguration();
        configuration.addAction(new ConfigAction<Void>() {
            public Void validate() throws IllegalArgumentException {
                return null;
            }

            public void applyPreCreate(final Void param) {
            }

            public void applyPostCreate(final Void param) {
                if (useParentHandlers != null)
                    configuration.getLogger(getName()).setUseParentHandlers(useParentHandlers);
            }

            public void rollback() {
                LoggerConfigurationImpl.this.useParentHandlers = oldUseParentHandlers;
            }
        });
    }

    public String getLevel() {
        return getLevelValueExpression().getResolvedValue();
    }

    @Override
    public ValueExpression<String> getLevelValueExpression() {
        return level == null ? ValueExpression.NULL_STRING_EXPRESSION : level;
    }

    public void setLevel(final String level) {
        setLevelValueExpression(ValueExpression.STRING_RESOLVER.resolve(level));
    }

    @Override
    public void setLevel(final String expression, final String level) {
        setLevelValueExpression(new ValueExpressionImpl<>(expression, level));
    }

    private void setLevelValueExpression(final ValueExpression<String> expression) {
        final ValueExpression<String> oldLevel = this.level;
        this.level = expression;
        final String resolvedLevel = expression.getResolvedValue();
        final LogContextConfiguration configuration = getConfiguration();
        configuration.addAction(new ConfigAction<Level>() {
            public Level validate() throws IllegalArgumentException {
                return resolvedLevel == null ? null : configuration.getContext().getLevelForName(resolvedLevel);
            }

            public void applyPreCreate(final Level param) {
            }

            public void applyPostCreate(final Level param) {
                configuration.getLogger(getName()).setLevel(param);
            }

            public void rollback() {
                LoggerConfigurationImpl.this.level = oldLevel;
            }
        });
    }

    public List<String> getHandlerNames() {
        return new ArrayList<>(handlerNames);
    }

    public void setHandlerNames(final String... names) {
        final String[] oldHandlerNames = handlerNames.toArray(new String[0]);
        handlerNames.clear();
        final LinkedHashSet<String> strings = new LinkedHashSet<String>(asList(names));
        handlerNames.addAll(strings);
        final String[] stringsArray = strings.toArray(new String[0]);
        final LogContextConfiguration configuration = getConfiguration();
        configuration.addAction(new ConfigAction<Void>() {
            public Void validate() throws IllegalArgumentException {
                for (String name : stringsArray) {
                    if (configuration.getHandlerConfiguration(name) == null) {
                        throw new IllegalArgumentException(String.format("Handler \"%s\" is not found", name));
                    }
                }
                return null;
            }

            public void applyPreCreate(final Void param) {
            }

            public void applyPostCreate(final Void param) {
                final Logger logger = configuration.getLogger(getName());
                final int length = stringsArray.length;
                final Handler[] handlers = new Handler[length];
                for (int i = 0; i < length; i++) {
                    handlers[i] = getConfiguration().getHandler(stringsArray[i]);
                }
                logger.setHandlers(handlers);
            }

            public void rollback() {
                handlerNames.clear();
                handlerNames.addAll(asList(oldHandlerNames));
            }
        });
    }

    public void setHandlerNames(final Collection<String> names) {
        setHandlerNames(names.toArray(new String[0]));
    }

    public boolean addHandlerName(final String name) {
        final LogContextConfiguration configuration = getConfiguration();
        if (handlerNames.contains(name)) {
            return false;
        }
        handlerNames.add(name);
        configuration.addAction(new ConfigAction<Void>() {
            public Void validate() throws IllegalArgumentException {
                if (configuration.getHandlerConfiguration(name) == null) {
                    throw new IllegalArgumentException(String.format("Handler \"%s\" is not found", name));
                }
                return null;
            }

            public void applyPreCreate(final Void param) {
            }

            public void applyPostCreate(final Void param) {
                final Logger logger = getConfiguration().getLogger(getName());
                logger.addHandler(configuration.getHandler(name));
            }

            public void rollback() {
                handlerNames.remove(name);
            }
        });
        return true;
    }

    public boolean removeHandlerName(final String name) {
        final LogContextConfiguration configuration = getConfiguration();
        if (!handlerNames.contains(name)) {
            return false;
        }
        final int index = handlerNames.indexOf(name);
        handlerNames.remove(index);
        configuration.addAction(new ConfigAction<Handler>() {
            public Handler validate() throws IllegalArgumentException {
                return configuration.getHandler(name);
            }

            public void applyPreCreate(final Handler param) {
            }

            public void applyPostCreate(final Handler param) {
                final Logger logger = configuration.getLogger(getName());
                logger.removeHandler(param);
            }

            public void rollback() {
                handlerNames.add(index, name);
            }
        });
        return true;
    }

    @Override
    ConfigurationResource<Logger> removeInstance() {
        // Nothing is configured, so there is nothing to do
        return null;
    }

    @Override
    ConfigAction<Void> getRemoveAction() {
        final String name = getName();
        final Logger refLogger;
        if (getConfiguration().hasLogger(name)) {
            refLogger = getConfiguration().getLogger(name);
        } else {
            refLogger = null;
        }
        final Filter filter;
        final Handler[] handlers;
        final Level level;
        final boolean useParentHandlers;
        if (refLogger == null) {
            filter = null;
            handlers = null;
            level = null;
            useParentHandlers = true;
        } else {
            filter = refLogger.getFilter();
            handlers = refLogger.getHandlers();
            level = refLogger.getLevel();
            useParentHandlers = refLogger.getUseParentHandlers();
        }
        return new ConfigAction<>() {
            public Void validate() throws IllegalArgumentException {
                return null;
            }

            public void applyPreCreate(final Void param) {
            }

            public void applyPostCreate(final Void param) {
                if (refLogger != null) {
                    refLogger.setFilter(null);
                    refLogger.clearHandlers();
                    refLogger.setLevel(null);
                    refLogger.setUseParentHandlers(true);
                }
            }

            public void rollback() {
                if (refLogger != null) {
                    refLogger.setFilter(filter);
                    if (handlers != null) refLogger.setHandlers(handlers);
                    refLogger.setLevel(level);
                    refLogger.setUseParentHandlers(useParentHandlers);
                    configs.put(name, LoggerConfigurationImpl.this);
                    //getConfiguration().addLogger(refLogger);
                }
                clearRemoved();
            }
        };
    }
}
