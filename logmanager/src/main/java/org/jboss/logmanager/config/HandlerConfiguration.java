/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2014 Red Hat, Inc., and individual contributors
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

package org.jboss.logmanager.config;

import java.util.logging.Handler;

/**
 * Configuration for a single handler.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface HandlerConfiguration extends HandlerContainingConfigurable, NamedConfigurable, PropertyConfigurable, ObjectConfigurable<Handler> {

    /**
     * Get the name of the configured formatter for this handler.
     *
     * @return the formatter name
     */
    String getFormatterName();

    /**
     * Gets the formatter name which may be an expression.
     *
     * @return the formatter name
     */
    ValueExpression<String> getFormatterNameValueExpression();

    /**
     * Set the name of the configured formatter for this handler.
     *
     * @param name the formatter name
     */
    void setFormatterName(String name);


    /**
     * Sets the expression value for the formatter name.
     * <p/>
     * This method will not parse the expression for the value and instead use the {@code level} parameter for the
     * formatter name on the handler.
     *
     * @param expression the expression used to resolve the level
     * @param value      the value to set the formatter name to
     *
     * @see #setFormatterName(String)
     * @see ValueExpression
     */
    void setFormatterName(String expression, String value);

    /**
     * Gets the level set on the handler.
     *
     * @return the level
     */
    String getLevel();

    /**
     * Returns the level that may be an expression.
     *
     * @return the level
     */
    ValueExpression<String> getLevelValueExpression();

    /**
     * Sets the level on the handler.
     *
     * @param level the level to set, may be an expression
     *
     * @see ValueExpression
     */
    void setLevel(String level);

    /**
     * Sets the expression value for the level.
     * <p/>
     * This method will not parse the expression for the value and instead use the {@code level} parameter for the
     * level on the handler.
     *
     * @param expression the expression used to resolve the level
     * @param level      the level to use
     *
     * @see #setLevel(String)
     * @see ValueExpression
     */
    void setLevel(String expression, String level);

    // TODO (jrp) better document ValueExpression and filter expressions

    String getFilter();

    /**
     * Returns a filter that may be an expression.
     *
     * @return the filter
     */
    ValueExpression<String> getFilterValueExpression();

    void setFilter(String name);

    /**
     * Sets the expression value and for the filter.
     * <p/>
     * This method will not parse the expression for the value and instead use the {@code value} parameter for the
     * filter on the handler.
     *
     * @param expression the expression
     * @param value      the value to set the filter to
     */
    void setFilter(String expression, String value);

    String getEncoding();

    /**
     * Returns the encoding which may be an expression.
     *
     * @return the encoding
     */
    ValueExpression<String> getEncodingValueExpression();

    void setEncoding(String name);

    /**
     * Sets the expression value for the encoding.
     * <p/>
     * This method will not parse the expression for the value and instead use the {@code value} parameter for the
     * encoding on the handler.
     *
     * @param expression the expression
     * @param value      the value to set the encoding to
     *
     * @see #setEncoding(String)
     * @see ValueExpression
     */
    void setEncoding(String expression, String value);

    String getErrorManagerName();

    /**
     * Returns the error manager name which may be an expression.
     *
     * @return the error manager name
     */
    ValueExpression<String> getErrorManagerNameValueExpression();

    void setErrorManagerName(String name);

    /**
     * Sets the expression value for the error manager name.
     * <p/>
     * This method will not parse the expression for the value and instead use the {@code value} parameter for the
     * error manager name on the handler.
     *
     * @param expression the expression
     * @param value      the value to set the error manager name to
     *
     * @see #setErrorManagerName(String)
     * @see ValueExpression
     */
    void setErrorManagerName(String expression, String value);
}
