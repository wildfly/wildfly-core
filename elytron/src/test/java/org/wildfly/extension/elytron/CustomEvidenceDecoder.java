/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.elytron;

import java.util.Locale;

import org.wildfly.security.auth.principal.NamePrincipal;
import org.wildfly.security.auth.server.EvidenceDecoder;
import org.wildfly.security.evidence.Evidence;
import org.wildfly.security.evidence.X509PeerCertificateChainEvidence;

/**
 * A custom evidence decoder used in MappersTestCase.
 *
 * @author <a href="mailto:fjuma@redhat.com">Farah Juma</a>
 */
public class CustomEvidenceDecoder implements EvidenceDecoder {

    public NamePrincipal getPrincipal(final Evidence evidence) {
        if (! (evidence instanceof X509PeerCertificateChainEvidence)) {
            return null;
        }
        // just returns subject name as a NamePrincipal in upper case
        return new NamePrincipal(((X509PeerCertificateChainEvidence) evidence).getFirstCertificate().getSubjectX500Principal().getName().toUpperCase(Locale.ROOT));
    }
}
