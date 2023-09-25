/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.parsing.operation.header;


import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.operation.impl.DefaultCallbackHandler;
import org.jboss.as.cli.operation.impl.ParsedRolloutPlanHeader;
import org.jboss.as.cli.operation.impl.SingleRolloutPlanGroup;
import org.jboss.as.cli.parsing.ParsingContext;
import org.jboss.as.cli.parsing.ParsingStateCallbackHandler;
import org.jboss.as.cli.parsing.operation.HeaderValueState;
import org.jboss.as.cli.parsing.operation.PropertyListState;
import org.jboss.as.cli.parsing.operation.PropertyState;
import org.jboss.as.cli.parsing.operation.PropertyValueState;

/**
 *
 * @author Alexey Loubyansky
 */
public class RolloutPlanHeaderCallbackHandler implements ParsingStateCallbackHandler {

    private final ParsedRolloutPlanHeader header = new ParsedRolloutPlanHeader();
    private final DefaultCallbackHandler handler;

    final StringBuilder buffer = new StringBuilder();

    private String name;
    private SingleRolloutPlanGroup group;
    private boolean concurrent;
    private int lastChunkIndex;

    public RolloutPlanHeaderCallbackHandler(DefaultCallbackHandler handler) {
        this.handler = handler;
    }

    @Override
    public void enteredState(ParsingContext ctx) throws CommandFormatException {
        final String id = ctx.getState().getId();
        //System.out.println("rollout.entered " + id + " '" + ctx.getCharacter() + "'");

        if(HeaderValueState.ID.equals(id)) {
            ctx.enterState(RolloutPlanState.INSTANCE);
        } else if(ServerGroupState.ID.equals(id)) {
            group = new SingleRolloutPlanGroup();
        } else if(ConcurrentSignState.ID.equals(id)) {
            concurrent = true;
            header.groupConcurrentSeparator(ctx.getLocation());
        } else if ("NAME_VALUE_SEPARATOR".equals(id)) {
            name = buffer.length() == 0 ? null : buffer.toString().trim();
            if(name == null || name.isEmpty()) {
                throw new CommandFormatException("Property is missing name at index " + ctx.getLocation());
            }
            if(group != null) {
                group.addProperty(name, lastChunkIndex);
                group.propertyValueSeparator(ctx.getLocation());
            } else {
                header.planIdValueSeparator(ctx.getLocation());
            }
        } else if(ServerGroupSeparatorState.ID.equals(id)) {
            header.groupSequenceSeparator(ctx.getLocation());
        } else if(PropertyListState.ID.equals(id)) {
            if(group != null) {
                group.propertyListStart(ctx.getLocation());
            } else {
                header.propertyListStart(ctx.getLocation());
            }
        }
        buffer.setLength(0);
        lastChunkIndex = ctx.getLocation();
    }

    @Override
    public void leavingState(ParsingContext ctx) throws CommandFormatException {
        final String id = ctx.getState().getId();
        //System.out.println("rollout.leaving " + id + " '" + ctx.getCharacter() + "'");
        if(id.equals(HeaderValueState.ID)) {
            handler.header(header);
        } else if(PropertyValueState.ID.equals(id)) {
            final String value = buffer.length() == 0 ? null : buffer.toString().trim();
            if(value == null || value.isEmpty()) {
                throw new CommandFormatException("Property '" + name + "' is missing value at index " + ctx.getLocation());
            }

            if(group == null) {
                if("id".equals(name) || "name".equals(name)) {
                    header.setPlanRef(lastChunkIndex, value);
                } else {
                    header.addProperty(name, value, lastChunkIndex);
                }
            } else {
                group.addProperty(name, value, lastChunkIndex);
                if(!ctx.isEndOfContent()) {
                    group.propertySeparator(ctx.getLocation());
                }
            }
        } else if(PropertyState.ID.equals(id)) {
            if(name == null && buffer.length() > 0) {
                if(group != null) {
                    group.addProperty(buffer.toString().trim(), lastChunkIndex);
                    if (!ctx.isEndOfContent() && ctx.getCharacter() == ',') {
                        group.propertySeparator(ctx.getLocation());
                    }
                } else {
                    header.addProperty(buffer.toString().trim(), lastChunkIndex);
                }
                buffer.setLength(0);
            } else {
                name = null;
                buffer.setLength(0);
            }
        } else if(ServerGroupNameState.ID.equals(id)) {
            final String groupName = buffer.toString().trim();
            if(groupName.isEmpty()) {
                throw new CommandFormatException("Empty group name at index " + ctx.getLocation());
            }
            group.setGroupName(groupName, lastChunkIndex);
        } else if(ServerGroupState.ID.equals(id)) {
            if(concurrent) {
                header.addConcurrentGroup(group);
                concurrent = false;
            } else {
                header.addGroup(group);
            }
            group = null;
        } else if(!ctx.isEndOfContent() && PropertyListState.ID.equals(id)) {
            if(group != null) {
                group.propertyListEnd(ctx.getLocation());
            } else {
                header.propertyListEnd(ctx.getLocation());
            }
        }
    }

    @Override
    public void character(ParsingContext ctx) throws CommandFormatException {
        //System.out.println("rollout.content " + ctx.getState().getId() + " '" + ctx.getCharacter() + "'");
        buffer.append(ctx.getCharacter());
    }
}
