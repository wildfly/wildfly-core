/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.test.security.common.other;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OPERATION_HEADERS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ROLLBACK_ON_RUNTIME_FAILURE;

import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.test.integration.management.util.CLIWrapper;
import org.jboss.as.test.integration.security.common.CoreUtils;
import org.jboss.dmr.ModelNode;
import org.wildfly.test.security.common.elytron.ConfigurableElement;

/**
 * Configuration helper for https configuration in '/core-service=management/management-interface=http-interface'. It sets
 * {@code ssl-context}, {@code secure-socket-binding} and {@code http-upgrade.sasl-authentication-factory} attributes.
 *
 * @author Josef Cacek
 */
public class HttpMgmtConfigurator implements ConfigurableElement {

    private static final PathAddress HTTP_IFACE_ADDR = PathAddress.pathAddress().append("core-service", "management")
            .append("management-interface", "http-interface");
    private final String sslContext;
    private final String secureSocketBinding;
    private final String saslAuthenticationFactory;

    private String originalSslContext;
    private String originalSecureSocketBinding;
    private String originalSaslAuthenticationFactory;

    private HttpMgmtConfigurator(Builder builder) {
        this.sslContext = builder.sslContext;
        this.secureSocketBinding = builder.secureSocketBinding;
        this.saslAuthenticationFactory = builder.saslAuthenticationFactory;
    }

    @Override
    public void create(ModelControllerClient client, CLIWrapper cli) throws Exception {
        originalSslContext = readAttribute(client, "ssl-context");
        originalSecureSocketBinding = readAttribute(client, "secure-socket-binding");
        originalSaslAuthenticationFactory = readAttribute(client, "http-upgrade.sasl-authentication-factory");

        ModelNode composite = Util.createEmptyOperation("composite", null);
        composite.get(OPERATION_HEADERS, ROLLBACK_ON_RUNTIME_FAILURE).set(false);
        ModelNode steps = composite.get("steps");
        steps.add(createWriteAttributeOp("ssl-context", sslContext));
        steps.add(createWriteAttributeOp("secure-socket-binding", secureSocketBinding));
        steps.add(createWriteAttributeOp("http-upgrade.sasl-authentication-factory", saslAuthenticationFactory));
        CoreUtils.applyUpdate(composite, client);
    }

    @Override
    public void remove(ModelControllerClient client, CLIWrapper cli) throws Exception {
        ModelNode composite = Util.createEmptyOperation("composite", null);
        composite.get(OPERATION_HEADERS, ROLLBACK_ON_RUNTIME_FAILURE).set(false);
        ModelNode steps = composite.get("steps");
        steps.add(createWriteAttributeOp("ssl-context", originalSslContext));
        steps.add(createWriteAttributeOp("secure-socket-binding", originalSecureSocketBinding));
        steps.add(createWriteAttributeOp("http-upgrade.sasl-authentication-factory", originalSaslAuthenticationFactory));
        CoreUtils.applyUpdate(composite, client);

        originalSslContext = null;
        originalSecureSocketBinding = null;
        originalSaslAuthenticationFactory = null;
    }

    @Override
    public String getName() {
        return "/core-service=management/management-interface=http-interface";
    }

    private String readAttribute(ModelControllerClient client, String name) throws Exception {
        String originalVal;
        ModelNode op = Util.createEmptyOperation("read-attribute", HTTP_IFACE_ADDR);
        op.get("name").set(name);
        ModelNode result = client.execute(op);
        if (Operations.isSuccessfulOutcome(result)) {
            result = Operations.readResult(result);
            originalVal = result.isDefined() ? result.asString() : null;
        } else {
            throw new RuntimeException(
                    "Reading existing value of attribute " + name + " failed: " + Operations.getFailureDescription(result));
        }
        return originalVal;
    }

    private ModelNode createWriteAttributeOp(String name, String value) throws Exception {
        ModelNode op = Util.createEmptyOperation("write-attribute", HTTP_IFACE_ADDR);
        op.get("name").set(name);
        if (value != null) {
            op.get("value").set(value);
        }
        return op;
    }

    /**
     * Creates builder to build {@link HttpMgmtConfigurator}.
     *
     * @return created builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder to build {@link HttpMgmtConfigurator}.
     */
    public static final class Builder {
        private String sslContext;
        private String secureSocketBinding;
        private String saslAuthenticationFactory;

        private Builder() {
        }

        public Builder withSslContext(String sslContext) {
            this.sslContext = sslContext;
            return this;
        }

        public Builder withSecureSocketBinding(String secureSocketBinding) {
            this.secureSocketBinding = secureSocketBinding;
            return this;
        }

        public Builder withSaslAuthenticationFactory(String saslAuthenticationFactory) {
            this.saslAuthenticationFactory = saslAuthenticationFactory;
            return this;
        }

        public HttpMgmtConfigurator build() {
            return new HttpMgmtConfigurator(this);
        }
    }

}
