/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.completion.mock;

import static org.wildfly.common.Assert.checkNotNullParam;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

import java.util.List;

import org.jboss.as.cli.CommandArgument;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandLineCompleter;
import org.jboss.as.cli.handlers.SimpleTabCompleter;
import org.jboss.as.cli.operation.OperationCandidatesProvider;
import org.jboss.as.cli.operation.OperationRequestAddress;
import org.jboss.as.cli.operation.OperationRequestHeader;
import org.jboss.as.cli.operation.ParsedCommandLine;

/**
*
* @author Alexey Loubyansky
*/
public class MockOperationCandidatesProvider implements OperationCandidatesProvider {

    private final MockNode root;

    public MockOperationCandidatesProvider() {
        this(new MockNode("root"));
    }

    public MockOperationCandidatesProvider(MockNode root) {
        this.root = root;
    }

    @Override
    public List<String> getNodeNames(CommandContext ctx, OperationRequestAddress prefix) {
        checkNotNullParam("prefix", prefix);
        if(prefix.isEmpty()) {
            throw new IllegalArgumentException("Can't call getNodeNames() with an empty prefix.");
        }

        if(!prefix.endsOnType()) {
            throw new IllegalArgumentException("The prefix doesn't end on a type.");
        }

        MockNode target = root;
        for(OperationRequestAddress.Node node : prefix) {
            target = target.getChild(node.getType());
            if(target == null) {
                return Collections.emptyList();
            }
            if(node.getName() != null) {
                target = target.getChild(node.getName());
                if(target == null) {
                    return Collections.emptyList();
                }
            }
        }
        return target.getChildNames();
    }

    @Override
    public List<String> getNodeTypes(CommandContext ctx, OperationRequestAddress prefix) {

        if(prefix.endsOnType()) {
            throw new IllegalArgumentException("The prefix isn't expected to end on a type.");
        }

        MockNode target = root;
        for(OperationRequestAddress.Node node : prefix) {
            target = target.getChild(node.getType());
            if(target == null) {
                return Collections.emptyList();
            }
            if(node.getName() != null) {
                target = target.getChild(node.getName());
                if(target == null) {
                    return Collections.emptyList();
                }
            }
        }
        return target.getChildNames();
    }

    @Override
    public List<String> getOperationNames(CommandContext ctx, OperationRequestAddress prefix) {
        return new ArrayList<String>(root.getOperationNames());
    }

    @Override
    public List<CommandArgument> getProperties(CommandContext ctx, String operationName, OperationRequestAddress address) {
        MockOperation operation = root.getOperation(operationName);
        if(operation == null) {
            return Collections.emptyList();
        }

        final List<MockOperationProperty> properties = operation.getProperties();
        final List<CommandArgument> result = new ArrayList<CommandArgument>(properties.size());
        for(final MockOperationProperty property : properties) {
            String name = property.getName();

            result.add(new CommandArgument(){
                @Override
                public String getFullName() {
                    return name;
                }

                @Override
                public String getShortName() {
                    return null;
                }

                @Override
                public int getIndex() {
                    return -1;
                }

                @Override
                public boolean isPresent(ParsedCommandLine args)
                        throws CommandFormatException {
                    return args.hasProperty(name);
                }

                @Override
                public boolean canAppearNext(CommandContext ctx) throws CommandFormatException {
                    ParsedCommandLine args = ctx.getParsedCommandLine();
                    if (isPresent(args)) {
                        return !isValueComplete(args);
                    }
                    return true;
                }

                @Override
                public String getValue(ParsedCommandLine args) throws CommandFormatException {
                    return args.getPropertyValue(name);
                }

                @Override
                public String getValue(ParsedCommandLine args, boolean required) throws CommandFormatException {
                    if(!isPresent(args)) {
                        throw new CommandFormatException("Property '" + name + "' is missing required value.");
                    }
                    return args.getPropertyValue(name);
                }

                @Override
                public boolean isValueComplete(ParsedCommandLine args) throws CommandFormatException {
                    if(!isPresent(args)) {
                        return false;
                    }
                    if(name.equals(args.getLastParsedPropertyName()) && !args.isLastPropertyNegated()) {
                        return false;
                    }
                    return true;
                }

                @Override
                public boolean isValueRequired() {
                    return property.isValueRequired();
                }

                @Override
                public CommandLineCompleter getValueCompleter() {
                        return property.getPossibleValues() != null
                                ? new SimpleTabCompleter(property.getPossibleValues())
                                : null;
                }});
        }
        return result;
    }

    @Override
    public Map<String, OperationRequestHeader> getHeaders(CommandContext ctx) {
        return null;
    }
}