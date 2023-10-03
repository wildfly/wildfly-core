/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.core.logmanager.config;

/**
 * Configuration for a single logger.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
public interface LoggerConfiguration extends NamedConfigurable, HandlerContainingConfigurable {

    // TODO (jrp) better document ValueExpression and filter expressions

    /**
     * Get the name of the filter to use.
     *
     * @return the filter name
     */
    String getFilter();

    /**
     * Returns a filter that may be an expression.
     *
     * @return the filter
     */
    ValueExpression<String> getFilterValueExpression();

    /**
     * Set the name of the filter to use, or {@code null} to leave unconfigured.
     *
     * @param name the filter name
     */
    void setFilter(String name);

    /**
     * Sets the expression value and for the filter.
     * <p/>
     * This method will not parse the expression for the value and instead use the {@code value} parameter for the
     * filter on the logger.
     *
     * @param expression the expression
     * @param value      the value to set the filter to
     */
    void setFilter(String expression, String value);

    /**
     * Determine whether parent filters will be used.
     *
     * @return the setting, or {@code null} to leave unconfigured
     */
    Boolean getUseParentFilters();

    /**
     * Returns the value that may be an expression.
     *
     * @return the setting, or {@code null} to leave unconfigured as a value expression
     */
    ValueExpression<Boolean> getUseParentFiltersValueExpression();

    /**
     * Set whether to use parent filters.  A value of {@code null} indicates that the value should be left
     * unconfigured.
     *
     * @param value whether to use parent filters
     */
    void setUseParentFilters(Boolean value);

    /**
     * Set whether to use parent filters.
     *
     * @param expression the expression value used to resolve the setting
     *
     * @see #setUseParentFilters(Boolean)
     * @see ValueExpression
     */
    void setUseParentFilters(String expression);

    /**
     * Set whether to use parent filters.
     * <p>
     * This method will not parse the expression for the value and instead use the {@code value} parameter for the
     * setting on the logger.
     * </p>
     *
     * @param expression the expression
     * @param value      the value to set the setting to
     *
     * @see #setUseParentFilters(Boolean)
     * @see ValueExpression
     */
    void setUseParentFilters(String expression, Boolean value);

    /**
     * Determine whether parent handlers will be used.
     *
     * @return the setting, or {@code null} to leave unconfigured
     */
    Boolean getUseParentHandlers();

    /**
     * Returns the value that may be an expression.
     *
     * @return the setting, or {@code null} to leave unconfigured as a value expression
     */
    ValueExpression<Boolean> getUseParentHandlersValueExpression();

    /**
     * Set whether to use parent handlers.  A value of {@code null} indicates that the value should be left
     * unconfigured.
     *
     * @param value whether to use parent handlers
     */
    void setUseParentHandlers(Boolean value);

    /**
     * Set whether to use parent handlers.
     *
     * @param expression the expression value used to resolve the setting
     *
     * @see #setUseParentHandlers(Boolean)
     * @see ValueExpression
     */
    void setUseParentHandlers(String expression);

    /**
     * Set whether to use parent handlers.
     * <p/>
     * This method will not parse the expression for the value and instead use the {@code value} parameter for the
     * setting on the logger.
     *
     * @param expression the expression
     * @param value      the value to set the setting to
     *
     * @see #setUseParentHandlers(Boolean)
     * @see ValueExpression
     */
    void setUseParentHandlers(String expression, Boolean value);

    /**
     * Gets the level set on the logger.
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
     * Sets the level on the logger.
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
     * level on the logger.
     *
     * @param expression the expression used to resolve the level
     * @param level      the level to use
     *
     * @see #setLevel(String)
     * @see ValueExpression
     */
    void setLevel(String expression, String level);
}
