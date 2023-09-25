/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.operation.impl;

import static org.wildfly.common.Assert.checkNotEmptyParam;

import java.util.HashMap;
import java.util.Map;

import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.Util;
import org.jboss.dmr.ModelNode;

/**
 *
 * @author Alexey Loubyansky
 */
public class SingleRolloutPlanGroup implements RolloutPlanGroup {

    private static final int SEPARATOR_PROPERTY_LIST_START = 1;
    private static final int SEPARATOR_PROPERTY_LIST_END = 2;
    private static final int SEPARATOR_PROPERTY_VALUE = 3;
    private static final int SEPARATOR_PROPERTY = 4;
    private static final int SEPARATOR_NOT_OPERATOR = 5;

    private String groupName;
    private Map<String,String> props;

    private int lastSeparatorIndex;
    private int separator;
    private int lastChunkIndex;

    private String lastPropertyName;
    private String lastPropertyValue;

    private int lastNotOperatorIndex = -1;

    public SingleRolloutPlanGroup() {
    }

    public SingleRolloutPlanGroup(String groupName) {
        this.groupName = checkNotEmptyParam("groupName", groupName);
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName, int index) {
        this.groupName = groupName;
        this.lastChunkIndex = index;
    }

    public int getLastChunkIndex() {
        return lastChunkIndex;
    }

    public boolean endsOnNotOperator() {
        return separator == SEPARATOR_NOT_OPERATOR;
    }

    public boolean isLastPropertyNegated() {
        return lastPropertyName != null
                && lastNotOperatorIndex + 1 == lastChunkIndex;
    }

    // TODO perhaps add a list of allowed properties and their values
    public void addProperty(String name, String value, int valueIndex) {
        checkNotEmptyParam("name", name);
        checkNotEmptyParam("value", value);
        if(props == null) {
            props = new HashMap<String,String>();
        }
        props.put(name, value);
        this.lastPropertyName = name;
        this.lastPropertyValue = value;
        this.lastChunkIndex = valueIndex;
        separator = -1;
    }

    public void addProperty(String name, int index) {
        checkNotEmptyParam("name", name);
        // Property name without a value. can be an implicit value.
        if (props == null) {
            props = new HashMap<String, String>();
        }
        String value = Util.TRUE;
        if (name.startsWith(Util.NOT_OPERATOR)) {
            value = Util.FALSE;
            name = name.substring(1);
            notOperator(index);
            index += 1;
        }

        if (name.length() > 0) {
            // Default value for boolean
            props.put(name, value);
            this.lastPropertyName = name;
            this.lastChunkIndex = index;
            separator = -1;
        }
    }

    public void notOperator(int index) {
        separator = SEPARATOR_NOT_OPERATOR;
        this.lastNotOperatorIndex = index;
        this.lastPropertyName = null;
        this.lastPropertyValue = null;
    }

    public void propertyValueSeparator(int index) {
        separator = SEPARATOR_PROPERTY_VALUE;
        this.lastSeparatorIndex = index;
    }

    public void propertySeparator(int index) {
        separator = SEPARATOR_PROPERTY;
        this.lastSeparatorIndex = index;
        this.lastPropertyName = null;
        this.lastPropertyValue = null;
    }

    public boolean hasProperties() {
        return lastPropertyName != null || props != null;
    }

    public void propertyListStart(int index) {
        this.lastSeparatorIndex = index;
        separator = SEPARATOR_PROPERTY_LIST_START;
    }

    public boolean endsOnPropertyListStart() {
        return separator == SEPARATOR_PROPERTY_LIST_START;
    }

    public void propertyListEnd(int index) {
        this.lastSeparatorIndex = index;
        separator = SEPARATOR_PROPERTY_LIST_END;
        this.lastPropertyName = null;
        this.lastPropertyValue = null;
    }

    public boolean endsOnPropertyListEnd() {
        return separator == SEPARATOR_PROPERTY_LIST_END;
    }

    public boolean endsOnPropertyValueSeparator() {
        return separator == SEPARATOR_PROPERTY_VALUE;
    }

    public boolean endsOnPropertySeparator() {
        return separator == SEPARATOR_PROPERTY;
    }

    public int getLastSeparatorIndex() {
        return lastSeparatorIndex;
    }

    public String getLastPropertyName() {
        return lastPropertyName;
    }

    public String getLastPropertyValue() {
        return lastPropertyValue;
    }

    public boolean hasProperty(String name) {
        return props == null ? false : props.containsKey(name);
    }

    /* (non-Javadoc)
     * @see org.jboss.as.cli.operation.impl.RolloutPlanGroup#toModelNode()
     */
    @Override
    public ModelNode toModelNode() throws CommandFormatException {
        ModelNode node = new ModelNode();
        if(props != null) {
            for(String propName : props.keySet()) {
                node.get(propName).set(props.get(propName));
            }
        }
        return node;
    }

    @Override
    public void addTo(ModelNode inSeries) throws CommandFormatException {
        inSeries.add().get(Util.SERVER_GROUP).get(this.groupName).set(toModelNode());
    }

/*    public static void main(String[] args) throws Exception {

        SingleRolloutPlanGroup group = new SingleRolloutPlanGroup("groupA");
        group.addProperty("rolling-to-servers", "true");
        group.addProperty("max-failed-servers", "1");
        group.addProperty("max-failure-percentage", "20");
        System.out.println(group.toModelNode());
    }
*/}
