/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.operation.impl;

import java.util.List;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandLineCompleter;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.operation.ParsedOperationRequestHeader;

/**
 *
 * @author Alexey Loubyansky
 */
public class RolloutPlanCompleter implements CommandLineCompleter {

    public static final RolloutPlanCompleter INSTANCE = new RolloutPlanCompleter();

    private static final DefaultOperationRequestAddress address = new DefaultOperationRequestAddress();
    static {
        address.toNode(Util.MANAGEMENT_CLIENT_CONTENT, Util.ROLLOUT_PLANS);
    }

    private final DefaultCallbackHandler parsedOp = new DefaultCallbackHandler();

    /* (non-Javadoc)
     * @see org.jboss.as.cli.CommandLineCompleter#complete(org.jboss.as.cli.CommandContext, java.lang.String, int, java.util.List)
     */
    @Override
    public int complete(CommandContext ctx, String buffer, int cursor, List<String> candidates) {
        if(!ctx.isDomainMode()) {
            return -1;
        }
        if(buffer.isEmpty()) {
            candidates.add("{rollout");
            return 0;
        }

        try {
            parsedOp.parseOperation(null, buffer, ctx);
        } catch (CommandFormatException e) {
            return -1;
        }
        if(parsedOp.isRequestComplete()) {
            return -1;
        }

        if(parsedOp.endsOnHeaderListStart() || parsedOp.endsOnHeaderSeparator()) {
            candidates.add("rollout");
            return parsedOp.getLastSeparatorIndex() + 1;
        }
        if(parsedOp.getLastHeader() == null) {
            if(ctx.getParsedCommandLine().getOriginalLine().endsWith(" ") /* '{rollout ' */) {
                final String originalLine = ctx.getParsedCommandLine().getOriginalLine();
                int bufferIndex = originalLine.lastIndexOf(buffer);
                if(bufferIndex == -1) { // that's illegal state
                    return -1;
                }
                candidates.add("name=");
                candidates.addAll(Util.getServerGroups(ctx.getModelControllerClient()));
                return originalLine.length() - bufferIndex;
            } else if (ctx.getParsedCommandLine().getOriginalLine().endsWith("rollout")) {
                // In order to have the completion to be allowed to continue
                candidates.add(" ");
            }
            return buffer.length();
        }
        final ParsedOperationRequestHeader lastHeader = parsedOp.getLastHeader();
        if(!(lastHeader instanceof ParsedRolloutPlanHeader)) {
            throw new IllegalStateException("Expected " + ParsedRolloutPlanHeader.class + " but got " + lastHeader.getName() + " of " + lastHeader);
        }
        final ParsedRolloutPlanHeader rollout = (ParsedRolloutPlanHeader) lastHeader;

        if(rollout.endsOnPlanIdValueSeparator()) {
            candidates.addAll(Util.getNodeNames(ctx.getModelControllerClient(), address, Util.ROLLOUT_PLAN));
            return rollout.getLastSeparatorIndex() + 1;
        }

        final String planRef = rollout.getPlanRef();
        if(planRef != null) {
            final List<String> nodeNames = Util.getNodeNames(ctx.getModelControllerClient(), address, Util.ROLLOUT_PLAN);
            for(String name : nodeNames) {
                if(name.startsWith(planRef)) {
                    candidates.add(name);
                }
            }
            return rollout.getLastChunkIndex();
        }

        if(rollout.hasProperties()) {
            final String lastName = rollout.getLastPropertyName();
            if (Util.ROLLBACK_ACROSS_GROUPS.equals(lastName)) {
                if (rollout.getLastPropertyValue() != null) {
                    return -1;
                }
                candidates.add("}");
                candidates.add(";");
                return rollout.getLastChunkIndex() + lastName.length();
            }
            if (Util.ROLLBACK_ACROSS_GROUPS.startsWith(lastName)) {
                candidates.add(Util.ROLLBACK_ACROSS_GROUPS);
            }
            return rollout.getLastChunkIndex();
        }

        final List<String> serverGroups = Util.getServerGroups(ctx.getModelControllerClient());
        boolean containsAllGroups = true;
        for (String group : serverGroups) {
            if (!rollout.containsGroup(group)) {
                containsAllGroups = false;
                break;
            }
        }

        if(rollout.endsOnGroupSeparator()) {
            if (containsAllGroups) {
                return -1;
            }
            for(String group : serverGroups) {
                if(!rollout.containsGroup(group)) {
                    candidates.add(group);
                }
            }
            return buffer.length();
        }

        final SingleRolloutPlanGroup lastGroup = rollout.getLastGroup();
        if(lastGroup == null) {
            return -1;
        }

        if (lastGroup.endsOnPropertyListEnd()) {
            if (!containsAllGroups) {
                candidates.add("^");
                candidates.add(",");
            } else if (Character.isWhitespace(buffer.charAt(buffer.length() - 1))) {
                candidates.add(Util.ROLLBACK_ACROSS_GROUPS);

            } else {
                candidates.add(" ");
            }
            return buffer.length();
        }

        if(lastGroup.endsOnPropertyListStart()) {
            candidates.add(Util.MAX_FAILED_SERVERS);
            candidates.add(Util.MAX_FAILURE_PERCENTAGE);
            candidates.add(Util.ROLLING_TO_SERVERS);
            candidates.add(Util.NOT_OPERATOR);
            return buffer.length();
        }

        // Only return the set of boolean properties
        if (lastGroup.endsOnNotOperator()) {
            candidates.add(Util.ROLLING_TO_SERVERS);
            return buffer.length();
        }

        if (lastGroup.hasProperties()) {
            // To propose the right end character
            boolean containsAll = lastGroup.hasProperty(Util.MAX_FAILED_SERVERS)
                    && lastGroup.hasProperty(Util.MAX_FAILURE_PERCENTAGE)
                    && lastGroup.hasProperty(Util.ROLLING_TO_SERVERS);

            final String propValue = lastGroup.getLastPropertyValue();
            if(propValue != null) {
                if(Util.TRUE.startsWith(propValue)) {
                    candidates.add(Util.TRUE);
                } else if(Util.FALSE.startsWith(propValue)) {
                    candidates.add(Util.FALSE);
                } else {
                    candidates.add(containsAll ? ")" : ",");
                    return buffer.length();
                }
            } else if(lastGroup.endsOnPropertyValueSeparator()) {
                if(Util.ROLLING_TO_SERVERS.equals(lastGroup.getLastPropertyName())) {
                    candidates.add(Util.FALSE);
                    candidates.add(containsAll ? ")" : ",");
                }
                return buffer.length();
            } else if(lastGroup.endsOnPropertySeparator()) {
                if(!lastGroup.hasProperty(Util.MAX_FAILED_SERVERS)) {
                    candidates.add(Util.MAX_FAILED_SERVERS);
                }
                if(!lastGroup.hasProperty(Util.MAX_FAILURE_PERCENTAGE)) {
                    candidates.add(Util.MAX_FAILURE_PERCENTAGE);
                }
                if(!lastGroup.hasProperty(Util.ROLLING_TO_SERVERS)) {
                    candidates.add(Util.ROLLING_TO_SERVERS);
                    candidates.add(Util.NOT_OPERATOR);
                }
                return lastGroup.getLastSeparatorIndex() + 1;
            } else {
                final String propName = lastGroup.getLastPropertyName();
                if(Util.MAX_FAILED_SERVERS.startsWith(propName)) {
                    candidates.add(Util.MAX_FAILED_SERVERS + '=');
                }
                if(Util.MAX_FAILURE_PERCENTAGE.startsWith(propName)) {
                    candidates.add(Util.MAX_FAILURE_PERCENTAGE + '=');
                } else if (Util.ROLLING_TO_SERVERS.equals(propName)) {
                    if (lastGroup.isLastPropertyNegated() && !containsAll) {
                        candidates.add(Util.ROLLING_TO_SERVERS + ",");
                    } else {
                        candidates.add("=" + Util.FALSE);
                        if (!containsAll) {
                            candidates.add(",");
                        } else {
                            candidates.add(")");
                        }
                    }

                } else if (Util.ROLLING_TO_SERVERS.startsWith(propName)) {
                    candidates.add(Util.ROLLING_TO_SERVERS);
                }
            }
            if (candidates.isEmpty() && containsAll) {
                candidates.add(")");
            }
            return lastGroup.getLastChunkIndex();
        }

        if (Character.isWhitespace(buffer.charAt(buffer.length() - 1))) {
            candidates.add(Util.ROLLBACK_ACROSS_GROUPS);
            return buffer.length();
        }

        int result = lastGroup.getLastChunkIndex();

        final String groupName = lastGroup.getGroupName();

        boolean isLastGroup = false;
        for (String group : serverGroups) {
            if (group.equals(groupName)) {
                isLastGroup = true;
            }
            if (!isLastGroup && group.startsWith(groupName)) {
                candidates.add(group);
            }
        }

        if(Util.NAME.startsWith(groupName)) {
            candidates.add("name=");
        } else {
            if (candidates.isEmpty()) {
                if (isLastGroup) {
                    candidates.add("(");
                    if (containsAllGroups) {
                        candidates.add(Util.ROLLBACK_ACROSS_GROUPS);
                    } else {
                        candidates.add(",");
                        candidates.add("^");
                    }
                }
            }
        }

        return result;
    }

}
