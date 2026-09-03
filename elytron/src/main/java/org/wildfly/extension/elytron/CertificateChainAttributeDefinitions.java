/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.elytron;

import static org.wildfly.extension.elytron.ElytronExtension.ISO_8601_FORMAT;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;

import org.jboss.as.controller.ObjectListAttributeDefinition;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.version.Stability;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.wildfly.common.iteration.ByteIterator;

/**
 * Class to contain the attribute definitions for certificates and their chains.
 *
 * Also contains utility methods to convert from the {@link Certificate} to the {@link ModelNode} representation.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class CertificateChainAttributeDefinitions {

    private static final String SHA_1 = "SHA-1";

    private static final String SHA_256 = "SHA-256";

    private static final SimpleAttributeDefinition TYPE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.TYPE, ModelType.STRING).build();

    private static final SimpleAttributeDefinition ALGORITHM = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.ALGORITHM, ModelType.STRING).build();

    private static final SimpleAttributeDefinition FORMAT = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.FORMAT, ModelType.STRING).build();

    private static final SimpleAttributeDefinition PUBLIC_KEY = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.PUBLIC_KEY, ModelType.STRING).build();

    private static final SimpleAttributeDefinition SHA_1_DIGEST = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.SHA_1_DIGEST, ModelType.STRING).build();

    private static final SimpleAttributeDefinition SHA_256_DIGEST = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.SHA_256_DIGEST, ModelType.STRING).build();

    private static final SimpleAttributeDefinition ENCODED = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.ENCODED, ModelType.STRING).build();

    /*
     * X509 Certificate Specific Attributes
     */

    private static final SimpleAttributeDefinition SUBJECT = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.SUBJECT, ModelType.STRING)
        .setRequired(false)
        .build();

    private static final SimpleAttributeDefinition ISSUER = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.ISSUER, ModelType.STRING)
        .setRequired(false)
        .build();

    private static final SimpleAttributeDefinition NOT_BEFORE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.NOT_BEFORE, ModelType.STRING)
        .setRequired(false)
        .build();

    private static final SimpleAttributeDefinition NOT_AFTER = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.NOT_AFTER, ModelType.STRING)
            .setRequired(false)
            .build();

    private static final SimpleAttributeDefinition SERIAL_NUMBER = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.SERIAL_NUMBER, ModelType.STRING)
        .setRequired(false)
        .build();

    private static final SimpleAttributeDefinition SIGNATURE_ALGORITHM = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.SIGNATURE_ALGORITHM, ModelType.STRING)
        .setRequired(false)
        .build();

    private static final SimpleAttributeDefinition SIGNATURE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.SIGNATURE, ModelType.STRING)
        .setRequired(false)
        .build();

    private static final SimpleAttributeDefinition VERSION = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.VERSION, ModelType.STRING)
        .setRequired(false)
        .build();

    // TODO - Consider adding some of the more detailed fields from X509Certificate

    // derivative attributes
    private static final SimpleAttributeDefinition VALIDITY = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.VALIDITY, ModelType.STRING)
            .setRequired(false)
            .setStability(Stability.COMMUNITY)
            .build();

    static final ObjectTypeAttributeDefinition CERTIFICATE = new ObjectTypeAttributeDefinition.Builder(ElytronDescriptionConstants.CERTIFICATE,
            TYPE, ALGORITHM, FORMAT, PUBLIC_KEY, SHA_1_DIGEST, SHA_256_DIGEST, ENCODED,
            SUBJECT, ISSUER, NOT_BEFORE, NOT_AFTER, SERIAL_NUMBER, SIGNATURE_ALGORITHM, SIGNATURE, VERSION, VALIDITY)
        .setStorageRuntime()
        .build();

    static ObjectListAttributeDefinition getNamedCertificateList(final String name) {
        return new ObjectListAttributeDefinition.Builder(name, CERTIFICATE)
                .setRequired(false)
                .setStorageRuntime()
                .build();
    }

    static void writeCertificate(final ModelNode certificateModel, final Certificate certificate, final Stability stability, final long expirationWarningWaterMark) throws CertificateEncodingException, NoSuchAlgorithmException {
        writeCertificate(certificateModel, certificate, true, stability, expirationWarningWaterMark);
    }

    static void writeCertificate(final ModelNode certificateModel, final Certificate certificate, final boolean verbose, final Stability stability, final long expirationWarningWaterMark) throws CertificateEncodingException, NoSuchAlgorithmException {
       certificateModel.get(ElytronDescriptionConstants.TYPE).set(certificate.getType());

        PublicKey publicKey = certificate.getPublicKey();
        certificateModel.get(ElytronDescriptionConstants.ALGORITHM).set(publicKey.getAlgorithm());
        certificateModel.get(ElytronDescriptionConstants.FORMAT).set(publicKey.getFormat());
        if (verbose) {
            certificateModel.get(ElytronDescriptionConstants.PUBLIC_KEY).set(encodedHexString(publicKey.getEncoded()));
        }

        byte[] encodedCertificate = certificate.getEncoded();
        certificateModel.get(ElytronDescriptionConstants.SHA_1_DIGEST).set(encodedHexString(digest(SHA_1, encodedCertificate)));
        certificateModel.get(ElytronDescriptionConstants.SHA_256_DIGEST).set(encodedHexString(digest(SHA_256, encodedCertificate)));
        if (verbose) {
            certificateModel.get(ElytronDescriptionConstants.ENCODED).set(encodedHexString(encodedCertificate));
        }

        if (certificate instanceof X509Certificate) {
            writeX509Certificate(certificateModel, (X509Certificate) certificate, stability, expirationWarningWaterMark);
        }
    }

    private static void writeX509Certificate(final ModelNode certificateModel, final X509Certificate certificate, final Stability stability, final long expirationWarningWaterMark) throws CertificateEncodingException, NoSuchAlgorithmException {
        SimpleDateFormat sdf = new SimpleDateFormat(ISO_8601_FORMAT);

        certificateModel.get(ElytronDescriptionConstants.SUBJECT).set(certificate.getSubjectX500Principal().getName());
        certificateModel.get(ElytronDescriptionConstants.ISSUER).set(certificate.getIssuerX500Principal().getName());
        certificateModel.get(ElytronDescriptionConstants.NOT_BEFORE).set(sdf.format(certificate.getNotBefore()));
        certificateModel.get(ElytronDescriptionConstants.NOT_AFTER).set(sdf.format(certificate.getNotAfter()));
        certificateModel.get(ElytronDescriptionConstants.SERIAL_NUMBER).set(delimit(certificate.getSerialNumber().toString(16).toCharArray()));
        certificateModel.get(ElytronDescriptionConstants.SIGNATURE_ALGORITHM).set(certificate.getSigAlgName());
        certificateModel.get(ElytronDescriptionConstants.SIGNATURE).set(encodedHexString(certificate.getSignature()));
        certificateModel.get(ElytronDescriptionConstants.VERSION).set("v" + certificate.getVersion());
        //artificial parameters here, after concrete?
        if(stability.enables(Stability.COMMUNITY)) {
            certificateModel.get(ElytronDescriptionConstants.VALIDITY).set(CertificateValidity.getValidity(certificate.getNotBefore(), certificate.getNotAfter(), expirationWarningWaterMark).toString());
        }
    }

    /**
     * Populate the supplied response with the model representation of the certificates.
     *
     * @param result the response to populate.
     * @param certificates the certificates to add to the response.
     * @throws CertificateEncodingException
     * @throws NoSuchAlgorithmException
     */
    static void writeCertificates(final ModelNode result, final Certificate[] certificates, final Stability stability) throws CertificateEncodingException, NoSuchAlgorithmException {
        writeCertificates(result, certificates, true, stability, KeyStoreDefinition.EXPIRATION_WATERMARK.getDefaultValue().asLong());
    }


    /**
     * Populate the supplied response with the model representation of the certificates.
     *
     * @param result the response to populate.
     * @param certificates the certificates to add to the response.
     * @param stability of operation context. Can be null in case its not part of model operations.
     * @param expiratioWatermark threshold for expiration warning.
     * @throws CertificateEncodingException
     * @throws NoSuchAlgorithmException
     */
    static void writeCertificates(final ModelNode result, final Certificate[] certificates, final Stability stability, final long expiratioWatermark) throws CertificateEncodingException, NoSuchAlgorithmException {
        writeCertificates(result, certificates, true, stability, expiratioWatermark);
    }

    /**
     * Populate the supplied response with the model representation of the certificates.
     *
     * @param result the response to populate.
     * @param certificates the certificates to add to the response.
     * @param verbose mode of output.
     * @param stability of operation context. Can be null in case its not part of model operations.
     * @param expiratioWatermark threshold for expiration warning.
     * @throws CertificateEncodingException
     * @throws NoSuchAlgorithmException
     */
    static void writeCertificates(final ModelNode result, final Certificate[] certificates, final boolean verbose, final Stability stability, final long expirationWarningWaterMark) throws CertificateEncodingException, NoSuchAlgorithmException {
        if (certificates != null) {
            for (Certificate current : certificates) {
                ModelNode certificate = new ModelNode();
                writeCertificate(certificate, current, verbose, stability, expirationWarningWaterMark);
                result.add(certificate);
            }
        }
    }

    private static byte[] digest(final String algorithm, final byte[] encoded) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance(algorithm);

        return digest.digest(encoded);
    }

    private static String encodedHexString(byte[] encoded) {
        return delimit(ByteIterator.ofBytes(encoded).hexEncode().drainToString().toCharArray());
    }

    private static String delimit(final char[] chars) {
        StringBuilder sb = new StringBuilder();
        int offset = 1;
        if (chars.length % 2 != 0) {
            sb.append('0');
            offset++;
        }

        for (int i = 0; i < chars.length; i++) {
            sb.append(chars[i]);
            if (i + 1 < chars.length && (i + offset) % 2 == 0) {
                sb.append(':');
            }
        }
        return sb.toString();
    }

}
