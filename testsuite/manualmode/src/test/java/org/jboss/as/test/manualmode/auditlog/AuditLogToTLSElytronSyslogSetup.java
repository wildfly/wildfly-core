/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.manualmode.auditlog;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.AUTHENTICATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CLIENT_CERT_STORE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.KEYSTORE_PATH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROTOCOL;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.TLS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.TRUSTSTORE;

import java.util.ArrayList;
import java.util.List;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.domain.management.audit.SyslogAuditLogProtocolResourceDefinition;
import org.jboss.as.test.integration.auditlog.AuditLogToTLSSyslogSetup;
import org.jboss.dmr.ModelNode;

/**
 *
 * @author Emmanuel Hugonnet (c) 2017 Red Hat, inc.
 */
public class AuditLogToTLSElytronSyslogSetup extends AuditLogToTLSSyslogSetup{

    @Override
    protected List<ModelNode> addProtocolSettings(PathAddress syslogHandlerAddress) {
        PathAddress protocolAddress = syslogHandlerAddress.append(PROTOCOL, TLS);
        List<ModelNode> ops = new ArrayList<>(2);
        ModelNode op1 = Util.createAddOperation(protocolAddress.append(AUTHENTICATION, TRUSTSTORE));
        op1.get(KEYSTORE_PATH).set(CLIENT_TRUSTSTORE_FILE.getAbsolutePath());
        ModelNode trustStoreCredentialRef = new ModelNode();
        trustStoreCredentialRef.get("store").set("test");
        trustStoreCredentialRef.get("alias").set(TRUSTSTORE);
        op1.get(SyslogAuditLogProtocolResourceDefinition.TlsKeyStore.KEYSTORE_PASSWORD_CREDENTIAL_REFERENCE.getName()).set(trustStoreCredentialRef);
        ops.add(op1);

        ModelNode op2 = Util.createAddOperation(protocolAddress.append(AUTHENTICATION, CLIENT_CERT_STORE));
        op2.get(KEYSTORE_PATH).set(CLIENT_KEYSTORE_FILE.getAbsolutePath());
        ModelNode clientCertCredentialRef = new ModelNode();
        clientCertCredentialRef.get("store").set("test");
        clientCertCredentialRef.get("alias").set(CLIENT_CERT_STORE);
        op2.get(SyslogAuditLogProtocolResourceDefinition.TlsKeyStore.KEYSTORE_PASSWORD_CREDENTIAL_REFERENCE.getName()).set(clientCertCredentialRef);
        ops.add(op2);
        return ops;
    }
}
