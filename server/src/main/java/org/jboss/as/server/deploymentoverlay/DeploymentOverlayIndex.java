/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.server.deploymentoverlay;

import static org.wildfly.common.Assert.checkNotNullParam;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.registry.Resource;
import org.jboss.dmr.ModelNode;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.jboss.as.controller.PathElement.pathElement;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CONTENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.DEPLOYMENT_OVERLAY;

/**
 * Service that aggregates all available deployment overrides
 *
 * @author Stuart Douglas
 */
public class DeploymentOverlayIndex {

    private final Map<String, Map<String, byte[]>> exactMatches;
    private final Map<String, Map<String, byte[]>> wildcards;

    private DeploymentOverlayIndex(Map<String, Map<String, byte[]>> exactMatches, Map<String, Map<String, byte[]>> wildcards) {
        this.exactMatches = exactMatches;
        this.wildcards = wildcards;
    }

    public Map<String, byte[]> getOverlays(final String deployment) {
        Map<String, byte[]> ret = new HashMap<String, byte[]>();
        Map<String, byte[]> exact = exactMatches.get(deployment);
        if(exact != null) {
            ret.putAll(exact);
        }
        for(Map.Entry<String, Map<String, byte[]>> entry : wildcards.entrySet()) {
            if(getPattern(entry.getKey()).matcher(deployment).matches()) {
                for(Map.Entry<String, byte[]> e : entry.getValue().entrySet()) {
                    if(!ret.containsKey(e.getKey())) {
                        ret.put(e.getKey(), e.getValue());
                    }
                }
            }
        }
        return ret;
    }

    public static DeploymentOverlayIndex createDeploymentOverlayIndex(OperationContext context) {
        final Map<String, Map<String, byte[]>> exactMatches = new HashMap<String, Map<String, byte[]>>();
        final Map<String, Map<String, byte[]>> wildcards = new LinkedHashMap<String, Map<String, byte[]>>();
        Set<String> overlayNames = context.readResourceFromRoot(PathAddress.pathAddress(pathElement(DEPLOYMENT_OVERLAY))).getChildrenNames(DEPLOYMENT_OVERLAY);
        for (String overlay : overlayNames) {
            Set<String> deployments = context.readResourceFromRoot(PathAddress.pathAddress(pathElement(DEPLOYMENT_OVERLAY, overlay))).getChildrenNames(DEPLOYMENT);
            for (String deployment : deployments) {
                if (isWildcard(deployment)) {
                    handleContent(context, wildcards, overlay, deployment);
                } else {
                    handleContent(context, exactMatches, overlay, deployment);
                }
            }
        }
        //now we have the overlay names
        //lets get the content hashes
        return new DeploymentOverlayIndex(exactMatches, wildcards);
    }

    private static void handleContent(OperationContext context, Map<String, Map<String, byte[]>> wildcards, String overlay, String deployment) {
        Map<String, byte[]> contentMap = wildcards.get(deployment);
        if(contentMap == null) {
            wildcards.put(deployment, contentMap = new HashMap<String, byte[]>());
        }
        Set<String> content = context.readResourceFromRoot(PathAddress.pathAddress(pathElement(DEPLOYMENT_OVERLAY, overlay))).getChildrenNames(CONTENT);
        for(String contentItem : content) {
            Resource cr = context.readResourceFromRoot(PathAddress.pathAddress(pathElement(DEPLOYMENT_OVERLAY, overlay), pathElement(CONTENT, contentItem)));
            ModelNode sha;
            try {
                sha = DeploymentOverlayContentDefinition.CONTENT_ATTRIBUTE.resolveModelAttribute(context, cr.getModel());
            } catch (OperationFailedException e) {
                throw new RuntimeException(e);
            }
            String key = contentItem.startsWith("/") ? contentItem.substring(1) : contentItem;
            contentMap.put(key, sha.asBytes());
        }
    }

    private static boolean isWildcard(String name) {
        return name.contains("*") || name.contains("?");
    }

    static Pattern getPattern(String name) {
        return Pattern.compile(wildcardToJavaRegexp(name));
    }

    private static String wildcardToJavaRegexp(String expr) {
        checkNotNullParam("expr", expr);

        String regex = expr.replaceAll("([(){}\\[\\].+^$])", "\\\\$1"); // escape regex characters
        regex = regex.replaceAll("\\*", ".*"); // replace * with .*
        regex = regex.replaceAll("\\?", "."); // replace ? with .
        return regex;
    }
}
