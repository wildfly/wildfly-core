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

import static java.util.Arrays.asList;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.logging.Filter;
import java.util.logging.Handler;
import java.util.logging.Level;

import org.jboss.logmanager.ExtHandler;
import org.jboss.logmanager.configuration.ConfigurationResource;

/**
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class HandlerConfigurationImpl extends AbstractPropertyConfiguration<Handler, HandlerConfigurationImpl> implements HandlerConfiguration {
    static final ValueExpression<String> DEFAULT_ENCODING;

    static {
        if (System.getSecurityManager() == null) {
            DEFAULT_ENCODING = ValueExpression.constant(System.getProperty("file.encoding", "UTF-8"));
        } else {
            DEFAULT_ENCODING = AccessController.doPrivileged((PrivilegedAction<ValueExpression<String>>) () ->
                    ValueExpression.constant(System.getProperty("file.encoding", "UTF-8")));
        }
    }

    private final List<String> handlerNames = new ArrayList<String>(0);

    private ValueExpression<String> formatterName;
    private ValueExpression<String> level;
    private ValueExpression<String> filter;
    private ValueExpression<String> encoding = DEFAULT_ENCODING;
    private ValueExpression<String> errorManagerName;

    HandlerConfigurationImpl(final LogContextConfiguration configuration, final String name, final String moduleName, final String className, final String[] constructorProperties) {
        super(Handler.class, configuration, configuration.getHandlerConfigurations(), name, moduleName, className, constructorProperties);
    }

    public String getFormatterName() {
        return getFormatterNameValueExpression().getResolvedValue();
    }

    @Override
    public ValueExpression<String> getFormatterNameValueExpression() {
        return formatterName == null ? ValueExpression.NULL_STRING_EXPRESSION : formatterName;
    }

    public void setFormatterName(final String formatterName) {
        setFormatterName(ValueExpression.STRING_RESOLVER.resolve(formatterName));
    }

    @Override
    public void setFormatterName(final String expression, final String value) {
        setFormatterName(new ValueExpressionImpl<String>(expression, value));
    }

    private void setFormatterName(final ValueExpression<String> expression) {
        final ValueExpression<String> oldFormatterName = this.formatterName;
        this.formatterName = expression;
        final String formatterName = expression.getResolvedValue();
        final LogContextConfiguration configuration = getConfiguration();
        configuration.addAction(new ConfigAction<Void>() {
            public Void validate() throws IllegalArgumentException {
                if (formatterName != null && configuration.getFormatterConfiguration(formatterName) == null) {
                    throw new IllegalArgumentException(String.format("Formatter \"%s\" is not found", formatterName));
                }
                return null;
            }

            public void applyPreCreate(final Void param) {
                addPostConfigurationActions();
            }

            public void applyPostCreate(final Void param) {
                getInstance().setFormatter(formatterName == null ? null : configuration.getFormatter(formatterName));
            }

            public void rollback() {
                HandlerConfigurationImpl.this.formatterName = oldFormatterName;
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
                addPostConfigurationActions();
            }

            public void applyPostCreate(final Level param) {
                getInstance().setLevel(param);
            }

            public void rollback() {
                HandlerConfigurationImpl.this.level = oldLevel;
            }
        });
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
        setFilter(new ValueExpressionImpl<String>(expression, value));
    }

    private void setFilter(final ValueExpression<String> expression) {
        final ValueExpression<String> oldFilterName = this.filter;
        this.filter = expression;
        final String filterName = expression.getResolvedValue();
        final LogContextConfiguration configuration = getConfiguration();
        configuration.addAction(new ConfigAction<ObjectProducer>() {
            public ObjectProducer validate() throws IllegalArgumentException {
                return configuration.resolveFilter(filterName);
            }

            public void applyPreCreate(final ObjectProducer param) {
                addPostConfigurationActions();
            }

            public void applyPostCreate(final ObjectProducer param) {
                getInstance().setFilter((Filter) param.getObject());
            }

            public void rollback() {
                HandlerConfigurationImpl.this.filter = oldFilterName;
            }
        });
    }

    public String getEncoding() {
        return getEncodingValueExpression().getResolvedValue();
    }

    @Override
    public ValueExpression<String> getEncodingValueExpression() {
        return encoding == null ? DEFAULT_ENCODING : encoding;
    }

    public void setEncoding(final String encoding) {
        setEncoding(ValueExpression.STRING_RESOLVER.resolve(encoding));
    }

    @Override
    public void setEncoding(final String expression, final String value) {
        setEncoding(new ValueExpressionImpl<>(expression, value));
    }

    private void setEncoding(final ValueExpression<String> expression) {
        final ValueExpression<String> oldEncoding = this.encoding;
        this.encoding = expression == ValueExpression.NULL_STRING_EXPRESSION ? DEFAULT_ENCODING : expression;
        final String encoding = expression.getResolvedValue();
        final LogContextConfiguration configuration = getConfiguration();
        configuration.addAction(new ConfigAction<Void>() {
            public Void validate() throws IllegalArgumentException {
                if (encoding != null) {
                    try {
                        Charset.forName(encoding);
                    } catch (Throwable t) {
                        throw new IllegalArgumentException(String.format("Unsupported character set \"%s\"", encoding));
                    }
                }
                return null;
            }

            public void applyPreCreate(final Void param) {
                addPostConfigurationActions();
            }

            public void applyPostCreate(final Void param) {
                try {
                    getInstance().setEncoding(encoding);
                } catch (UnsupportedEncodingException e) {
                    throw new IllegalArgumentException(String.format("The encoding value '%s' is invalid.", encoding), e);
                }
            }

            public void rollback() {
                HandlerConfigurationImpl.this.encoding = oldEncoding;
            }
        });
    }

    public String getErrorManagerName() {
        return getErrorManagerNameValueExpression().getResolvedValue();
    }

    @Override
    public ValueExpression<String> getErrorManagerNameValueExpression() {
        return errorManagerName == null ? ValueExpression.NULL_STRING_EXPRESSION : errorManagerName;
    }

    public void setErrorManagerName(final String errorManagerName) {
        setErrorManagerName(ValueExpression.STRING_RESOLVER.resolve(errorManagerName));
    }

    @Override
    public void setErrorManagerName(final String expression, final String value) {
        setErrorManagerName(new ValueExpressionImpl<String>(expression, value));
    }

    private void setErrorManagerName(final ValueExpression<String> expression) {
        final ValueExpression<String> oldErrorManagerName = this.errorManagerName;
        this.errorManagerName = expression;
        final String errorManagerName = expression.getResolvedValue();
        final LogContextConfiguration configuration = getConfiguration();
        configuration.addAction(new ConfigAction<Void>() {
            public Void validate() throws IllegalArgumentException {
                if (errorManagerName != null && configuration.getErrorManagerConfiguration(errorManagerName) == null) {
                    throw new IllegalArgumentException(String.format("errorManager \"%s\" is not found", errorManagerName));
                }
                return null;
            }

            public void applyPreCreate(final Void param) {
                addPostConfigurationActions();
            }

            public void applyPostCreate(final Void param) {
                getInstance().setErrorManager(errorManagerName == null ? null : configuration.getErrorManager(errorManagerName));
            }

            public void rollback() {
                HandlerConfigurationImpl.this.errorManagerName = oldErrorManagerName;
            }
        });
    }

    public List<String> getHandlerNames() {
        return new ArrayList<>(handlerNames);
    }

    public void setHandlerNames(final String... names) {
        final String[] oldHandlerNames = handlerNames.toArray(new String[0]);
        handlerNames.clear();
        final LinkedHashSet<String> strings = new LinkedHashSet<>(asList(names));
        handlerNames.addAll(strings);
        final String[] stringsArray = strings.toArray(new String[0]);
        final LogContextConfiguration configuration = getConfiguration();
        if (!ExtHandler.class.isAssignableFrom(getActualClass())) {
            if (names.length == 0) {
                return;
            }
            throw new IllegalArgumentException("Nested handlers not supported for handler " + getActualClass());
        }
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
                addPostConfigurationActions();
            }

            public void applyPostCreate(final Void param) {
                final ExtHandler handler = (ExtHandler) getInstance();
                final int length = stringsArray.length;
                final Handler[] handlers = new Handler[length];
                for (int i = 0; i < length; i++) {
                    handlers[i] = configuration.getHandler(stringsArray[i]);
                }
                handler.setHandlers(handlers);
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
        if (!ExtHandler.class.isAssignableFrom(getActualClass())) {
            throw new IllegalArgumentException("Nested handlers not supported for handler " + getActualClass());
        }
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
                addPostConfigurationActions();
            }

            public void applyPostCreate(final Void param) {
                final ExtHandler handler = (ExtHandler) getInstance();
                handler.addHandler(configuration.getHandler(name));
            }

            public void rollback() {
                handlerNames.remove(name);
            }
        });
        return true;
    }

    public boolean removeHandlerName(final String name) {
        final LogContextConfiguration configuration = getConfiguration();
        if (!ExtHandler.class.isAssignableFrom(getActualClass())) {
            return false;
        }
        if (!handlerNames.contains(name)) {
            return false;
        }
        final int index = handlerNames.indexOf(name);
        handlerNames.remove(index);
        configuration.addAction(new ConfigAction<Handler>() {
            public Handler validate() throws IllegalArgumentException {
                return getInstance();
            }

            public void applyPreCreate(final Handler param) {
                addPostConfigurationActions();
            }

            public void applyPostCreate(final Handler param) {
                final ExtHandler handler = (ExtHandler) getInstance();
                handler.removeHandler(param);
            }

            public void rollback() {
                handlerNames.add(index, name);
            }
        });
        return true;
    }

    String getDescription() {
        return "handler";
    }

    @Override
    void addConfigurationResource(final ConfigurationResource<Handler> resource) {
        getConfiguration().addHandler(getName(), resource);
    }

    @Override
    ConfigAction<Handler> getConstructAction() {
        return new ConstructAction() {
            @Override
            public void rollback() {
                final ConfigurationResource<Handler> handler = removeInstance();
                if (handler != null) {
                    try {
                        handler.close();
                    } catch (Exception ignore) {
                    }
                }
                super.rollback();
            }
        };
    }


    @Override
    ConfigurationResource<Handler> removeInstance() {
        return getConfiguration().removeHandler(getName());
    }

    @Override
    ConfigAction<Void> getRemoveAction() {
        return new ConfigAction<>() {
            @Override
            public Void validate() throws IllegalArgumentException {
                return null;
            }

            @Override
            public void applyPreCreate(final Void param) {
                final ConfigurationResource<Handler> handler = removeInstance();
                if (handler != null) {
                    try {
                        handler.close();
                    } catch (Exception e) {
                        // TODO (jrp) what do we do here?
                    }
                }
            }

            @Override
            public void applyPostCreate(final Void param) {
                removePostConfigurationActions();
            }

            @Override
            public void rollback() {
                configs.put(getName(), HandlerConfigurationImpl.this);
                clearRemoved();
                addPostConfigurationActions();
            }
        };
    }

    @Override
    public Handler getInstance() {
        return getConfiguration().getHandler(getName());
    }
}
