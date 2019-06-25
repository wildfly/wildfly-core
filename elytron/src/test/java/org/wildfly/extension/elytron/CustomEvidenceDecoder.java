/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
