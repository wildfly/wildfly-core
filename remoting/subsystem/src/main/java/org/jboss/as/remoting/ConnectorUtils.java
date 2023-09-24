/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.remoting;

import static org.jboss.as.remoting.CommonAttributes.INCLUDE_MECHANISMS;
import static org.jboss.as.remoting.CommonAttributes.POLICY;
import static org.jboss.as.remoting.CommonAttributes.PROPERTY;
import static org.jboss.as.remoting.CommonAttributes.QOP;
import static org.jboss.as.remoting.CommonAttributes.REUSE_SESSION;
import static org.jboss.as.remoting.CommonAttributes.SASL;
import static org.jboss.as.remoting.CommonAttributes.SASL_POLICY;
import static org.jboss.as.remoting.CommonAttributes.SECURITY;
import static org.jboss.as.remoting.CommonAttributes.SERVER_AUTH;
import static org.jboss.as.remoting.CommonAttributes.STRENGTH;
import static org.jboss.as.remoting.SaslPolicyResource.FORWARD_SECRECY;
import static org.jboss.as.remoting.SaslPolicyResource.NO_ACTIVE;
import static org.jboss.as.remoting.SaslPolicyResource.NO_ANONYMOUS;
import static org.jboss.as.remoting.SaslPolicyResource.NO_DICTIONARY;
import static org.jboss.as.remoting.SaslPolicyResource.NO_PLAIN_TEXT;
import static org.jboss.as.remoting.SaslPolicyResource.PASS_CREDENTIALS;
import static org.jboss.as.remoting.SaslResource.REUSE_SESSION_ATTRIBUTE;
import static org.jboss.as.remoting.SaslResource.SERVER_AUTH_ATTRIBUTE;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.remoting.logging.RemotingLogger;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.jboss.remoting3.RemotingOptions;
import org.wildfly.security.manager.WildFlySecurityManager;
import org.xnio.Option;
import org.xnio.OptionMap;
import org.xnio.Options;
import org.xnio.Sequence;
import org.xnio.sasl.SaslQop;
import org.xnio.sasl.SaslStrength;

/**
 * @author Stuart Douglas
 */
public class ConnectorUtils {


    protected static OptionMap getFullOptions(OperationContext context, ModelNode fullModel) throws OperationFailedException {
        OptionMap.Builder builder = OptionMap.builder();
        builder.set(Options.TCP_NODELAY, true);
        builder.set(Options.REUSE_ADDRESSES, true);

        builder.set(RemotingOptions.SASL_PROTOCOL, ConnectorCommon.SASL_PROTOCOL.resolveModelAttribute(context, fullModel).asString());
        ModelNode serverName = ConnectorCommon.SERVER_NAME.resolveModelAttribute(context, fullModel);
        if (serverName.isDefined()) {
            builder.set(RemotingOptions.SERVER_NAME, serverName.asString());
        }

        ModelNode properties = fullModel.get(PROPERTY);
        if (properties.isDefined() && properties.asInt() > 0) {
            addOptions(context, properties, builder);
        }
        if (fullModel.hasDefined(SECURITY)) {
            ModelNode security = fullModel.require(SECURITY);
            if (security.hasDefined(SASL)) {
                ModelNode sasl = security.require(SASL);
                addSasl(context, sasl, builder);
            }
        }
        return builder.getMap();
    }

    protected static OptionMap getOptions(OperationContext context, ModelNode properties) throws OperationFailedException {
        if (properties.isDefined() && properties.asInt() > 0) {
            OptionMap.Builder builder = OptionMap.builder();
            addOptions(context, properties, builder);
            return builder.getMap();
        } else {
            return OptionMap.EMPTY;
        }
    }

    private static void addSasl(OperationContext context, ModelNode sasl, OptionMap.Builder builder) throws OperationFailedException {
        if (sasl.hasDefined(INCLUDE_MECHANISMS)) {
            builder.set(Options.SASL_MECHANISMS, Sequence.of(asStringSet(sasl.get(INCLUDE_MECHANISMS))));
        }
        if (sasl.hasDefined(QOP)) {
            builder.set(Options.SASL_QOP, Sequence.of(asQopSet(sasl.get(QOP))));
        }
        if (sasl.hasDefined(STRENGTH)) {
            ModelNode strength = sasl.get(STRENGTH);
            for (ModelNode current : strength.asList()) {
                builder.set(Options.SASL_STRENGTH, SaslStrength.fromString(current.asString()));
            }
        }
        if (sasl.hasDefined(SERVER_AUTH)) {
            builder.set(Options.SASL_SERVER_AUTH, SERVER_AUTH_ATTRIBUTE.resolveModelAttribute(context, sasl).asBoolean());
        }
        if (sasl.hasDefined(REUSE_SESSION)) {
            builder.set(Options.SASL_REUSE, REUSE_SESSION_ATTRIBUTE.resolveModelAttribute(context, sasl).asBoolean());
        }
        ModelNode saslPolicy;
        if (sasl.hasDefined(SASL_POLICY) && (saslPolicy = sasl.get(SASL_POLICY)).hasDefined(POLICY)) {
            ModelNode policy = saslPolicy.get(POLICY);
            if (policy.hasDefined(FORWARD_SECRECY.getName())) {
                builder.set(Options.SASL_POLICY_FORWARD_SECRECY, FORWARD_SECRECY.resolveModelAttribute(context, policy).asBoolean());
            }
            if (policy.hasDefined(NO_ACTIVE.getName())) {
                builder.set(Options.SASL_POLICY_NOACTIVE, NO_ACTIVE.resolveModelAttribute(context, policy).asBoolean());
            }
            if (policy.hasDefined(NO_ANONYMOUS.getName())) {
                builder.set(Options.SASL_POLICY_NOANONYMOUS, NO_ANONYMOUS.resolveModelAttribute(context, policy).asBoolean());
            }
            if (policy.hasDefined(NO_DICTIONARY.getName())) {
                builder.set(Options.SASL_POLICY_NODICTIONARY, NO_DICTIONARY.resolveModelAttribute(context, policy).asBoolean());
            }
            if (policy.hasDefined(NO_PLAIN_TEXT.getName())) {
                builder.set(Options.SASL_POLICY_NOPLAINTEXT, NO_PLAIN_TEXT.resolveModelAttribute(context, policy).asBoolean());
            }
            if (policy.hasDefined(PASS_CREDENTIALS.getName())) {
                builder.set(Options.SASL_POLICY_PASS_CREDENTIALS, PASS_CREDENTIALS.resolveModelAttribute(context, policy).asBoolean());
            }
        }

        if (sasl.hasDefined(PROPERTY)) {
            ModelNode property = sasl.get(PROPERTY);
            List<Property> props = property.asPropertyList();
            List<org.xnio.Property> converted = new ArrayList<org.xnio.Property>(props.size());
            for (Property current : props) {
                converted.add(org.xnio.Property.of(current.getName(), PropertyResource.VALUE.resolveModelAttribute(context, current.getValue()).asString()));
            }
            builder.set(Options.SASL_PROPERTIES, Sequence.of(converted));
        }
    }

    private static void addOptions(OperationContext context, ModelNode properties, OptionMap.Builder builder) throws OperationFailedException {
        final ClassLoader loader = WildFlySecurityManager.getClassLoaderPrivileged(ConnectorUtils.class);
        for (Property property : properties.asPropertyList()) {
            final Option option = getAndValidateOption(loader, property.getName());
            String value = PropertyResource.VALUE.resolveModelAttribute(context, property.getValue()).asString();
            builder.set(option, option.parseValue(value, loader));
        }
    }

    static Option<?> getAndValidateOption(ClassLoader loader, String name) throws OperationFailedException {
        if (!name.contains(".")) {
            name = "org.xnio.Options." + name;
        }
        try {
            return Option.fromString(name, loader);
        } catch (IllegalArgumentException iae) {
            throw RemotingLogger.ROOT_LOGGER.invalidOption(iae.getMessage());
        }
    }

    private static Collection<String> asStringSet(final ModelNode node) {
        final Set<String> set = new HashSet<String>();
        for (final ModelNode element : node.asList()) {
            set.add(element.asString());
        }
        return set;
    }

    private static Collection<SaslQop> asQopSet(final ModelNode node) {
        final Set<SaslQop> set = new HashSet<SaslQop>();
        for (final ModelNode element : node.asList()) {
            set.add(SaslQop.fromString(element.asString()));
        }
        return set;
    }

    private ConnectorUtils() {

    }
}
