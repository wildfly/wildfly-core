/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
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

package org.jboss.as.domain.management.security;

import static org.jboss.as.domain.management.security.X509CertificateBuilder.ASN1.BIT_STRING_TYPE;
import static org.jboss.as.domain.management.security.X509CertificateBuilder.ASN1.CONSTRUCTED_MASK;
import static org.jboss.as.domain.management.security.X509CertificateBuilder.ASN1.CONTEXT_SPECIFIC_MASK;
import static org.jboss.as.domain.management.security.X509CertificateBuilder.ASN1.GENERALIZED_TIME_TYPE;
import static org.jboss.as.domain.management.security.X509CertificateBuilder.ASN1.OBJECT_IDENTIFIER_TYPE;
import static org.jboss.as.domain.management.security.X509CertificateBuilder.ASN1.SEQUENCE_TYPE;
import static org.jboss.as.domain.management.security.X509CertificateBuilder.ASN1.SET_TYPE;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import javax.security.auth.x500.X500Principal;

import org.jboss.as.domain.management.logging.DomainManagementLogger;
import org.jboss.logging.Logger;
import org.wildfly.common.Assert;
import org.wildfly.security.util.ByteStringBuilder;

/**
 * A builder for X.509 certificates.
 *
 * @author <a href="mailto:david.lloyd@redhat.com">David M. Lloyd</a>
 */
final class X509CertificateBuilder {

    private static final ZonedDateTime LATEST_VALID = ZonedDateTime.of(9999, 12, 31, 23, 59, 59, 0, ZoneOffset.UTC);

    private static DomainManagementLogger log = Logger.getMessageLogger(DomainManagementLogger.class, "org.jboss.as.domain.management.certificate-generation");

    private int version = 3;
    private BigInteger serialNumber = BigInteger.ONE;
    private X500Principal subjectDn;
    private byte[] subjectUniqueId;
    private X500Principal issuerDn;
    private byte[] issuerUniqueId;
    private ZonedDateTime notValidBefore = ZonedDateTime.now();
    private ZonedDateTime notValidAfter = LATEST_VALID;
    private PublicKey publicKey;
    private PrivateKey signingKey;
    private String signatureAlgorithmName;

    /**
     * Construct a new uninitialized instance.
     */
    public X509CertificateBuilder() {
    }

    /**
     * Get the certificate version.
     *
     * @return the certificate version
     */
    public int getVersion() {
        return version;
    }

    /**
     * Set the certificate version.
     *
     * @param version the certificate version (must be between 1 and 3, inclusive)
     * @return this builder instance
     */
    public X509CertificateBuilder setVersion(final int version) {
        Assert.checkMinimumParameter("version", 1, version);
        Assert.checkMaximumParameter("version", 3, version);
        this.version = version;
        return this;
    }

    /**
     * Get the serial number of the certificate being built.
     *
     * @return the serial number of the certificate being built (must not be {@code null})
     */
    public BigInteger getSerialNumber() {
        return serialNumber;
    }

    /**
     * Set the serial number of the certificate being built.  The serial number must be positive and no larger
     * than 20 octets (or 2^160).
     *
     * @param serialNumber the serial number of the certificate being built
     * @return this builder instance
     */
    public X509CertificateBuilder setSerialNumber(final BigInteger serialNumber) {
        Assert.checkNotNullParam("serialNumber", serialNumber);
        if (BigInteger.ONE.compareTo(serialNumber) > 0) {
            throw log.serialNumberTooSmall();
        }
        if (serialNumber.bitLength() > 20 * 8) {
            throw log.serialNumberTooLarge();
        }
        this.serialNumber = serialNumber;
        return this;
    }

    /**
     * Get the subject DN.
     *
     * @return the subject DN
     */
    public X500Principal getSubjectDn() {
        return subjectDn;
    }

    /**
     * Set the subject DN.
     *
     * @param subjectDn the subject DN (must not be {@code null})
     * @return this builder instance
     */
    public X509CertificateBuilder setSubjectDn(final X500Principal subjectDn) {
        Assert.checkNotNullParam("subjectDn", subjectDn);
        this.subjectDn = subjectDn;
        return this;
    }

    /**
     * Get the subject unique ID.
     *
     * @return the subject unique ID
     */
    public byte[] getSubjectUniqueId() {
        return subjectUniqueId;
    }

    /**
     * Set the subject unique ID.
     *
     * @param subjectUniqueId the subject unique ID (must not be {@code null})
     * @return this builder instance
     */
    public X509CertificateBuilder setSubjectUniqueId(final byte[] subjectUniqueId) {
        Assert.checkNotNullParam("subjectUniqueId", subjectUniqueId);
        this.subjectUniqueId = subjectUniqueId;
        return this;
    }

    /**
     * Get the issuer DN.
     *
     * @return the issuer DN
     */
    public X500Principal getIssuerDn() {
        return issuerDn;
    }

    /**
     * Set the issuer DN.
     *
     * @param issuerDn the issuer DN (must not be {@code null})
     * @return this builder instance
     */
    public X509CertificateBuilder setIssuerDn(final X500Principal issuerDn) {
        Assert.checkNotNullParam("issuerDn", issuerDn);
        this.issuerDn = issuerDn;
        return this;
    }

    /**
     * Get the issuer unique ID.
     *
     * @return the issuer unique ID
     */
    public byte[] getIssuerUniqueId() {
        return issuerUniqueId;
    }

    /**
     * Set the issuer unique ID.
     *
     * @param issuerUniqueId the issuer unique ID (must not be {@code null})
     * @return this builder instance
     */
    public X509CertificateBuilder setIssuerUniqueId(final byte[] issuerUniqueId) {
        Assert.checkNotNullParam("issuerUniqueId", issuerUniqueId);
        this.issuerUniqueId = issuerUniqueId;
        return this;
    }

    /**
     * Get the not-valid-before date.  The default is the date when this builder was constructed.
     *
     * @return the not-valid-before date
     */
    public ZonedDateTime getNotValidBefore() {
        return notValidBefore;
    }

    /**
     * Set the not-valid-before date.
     *
     * @param notValidBefore the not-valid-before date (must not be {@code null})
     * @return this builder instance
     */
    public X509CertificateBuilder setNotValidBefore(final ZonedDateTime notValidBefore) {
        Assert.checkNotNullParam("notValidBefore", notValidBefore);
        this.notValidBefore = notValidBefore;
        return this;
    }

    /**
     * Get the not-valid-after date.  The default is equal to {@code 99991231235959Z} as specified in {@code RFC 5280}.
     *
     * @return the not-valid-after date
     */
    public ZonedDateTime getNotValidAfter() {
        return notValidAfter;
    }

    /**
     * Set the not-valid-after date.
     *
     * @param notValidAfter the not-valid-after date (must not be {@code null})
     * @return this builder instance
     */
    public X509CertificateBuilder setNotValidAfter(final ZonedDateTime notValidAfter) {
        Assert.checkNotNullParam("notValidAfter", notValidAfter);
        this.notValidAfter = notValidAfter;
        return this;
    }

    /**
     * Get the public key.
     *
     * @return the public key
     */
    public PublicKey getPublicKey() {
        return publicKey;
    }

    /**
     * Set the public key.
     *
     * @param publicKey the public key (must not be {@code null})
     * @return this builder instance
     */
    public X509CertificateBuilder setPublicKey(final PublicKey publicKey) {
        Assert.checkNotNullParam("publicKey", publicKey);
        this.publicKey = publicKey;
        return this;
    }

    /**
     * Get the signing key.
     *
     * @return the signing key
     */
    public PrivateKey getSigningKey() {
        return signingKey;
    }

    /**
     * Set the signing key.
     *
     * @param signingKey the signing key (must not be {@code null})
     * @return this builder instance
     */
    public X509CertificateBuilder setSigningKey(final PrivateKey signingKey) {
        Assert.checkNotNullParam("signingKey", signingKey);
        this.signingKey = signingKey;
        return this;
    }

    /**
     * Get the signature algorithm name.
     *
     * @return the signature algorithm name
     */
    public String getSignatureAlgorithmName() {
        return signatureAlgorithmName;
    }

    /**
     * Set the signature algorithm name.
     *
     * @param signatureAlgorithmName the signature algorithm name (must not be {@code null})
     * @return this builder instance
     */
    public X509CertificateBuilder setSignatureAlgorithmName(final String signatureAlgorithmName) {
        Assert.checkNotNullParam("signatureAlgorithmName", signatureAlgorithmName);
        this.signatureAlgorithmName = signatureAlgorithmName;
        return this;
    }

    /**
     * Attempt to construct and sign an X.509 certificate according to the information in this builder.
     *
     * @return the constructed certificate
     * @throws IllegalArgumentException if one or more of the builder parameters are invalid or missing
     * @throws CertificateException     if the certificate failed to be constructed
     */
    public X509Certificate build() throws CertificateException {
        byte[] tbsCertificate = getTBSBytes();

        ByteStringBuilder b = new ByteStringBuilder();
        DEREncoder derEncoder = new DEREncoder(b);
        derEncoder.startSequence(); // Certificate
        derEncoder.writeEncoded(tbsCertificate);

        // signatureAlgorithm
        final String signatureAlgorithmName = this.signatureAlgorithmName;
        final String signatureAlgorithmOid = ASN1.oidFromSignatureAlgorithm(signatureAlgorithmName);
        derEncoder.startSequence(); // AlgorithmIdentifier
        derEncoder.encodeObjectIdentifier(signatureAlgorithmOid);
        derEncoder.endSequence(); // AlgorithmIdentifier
        try {
            final Signature signature = Signature.getInstance(signatureAlgorithmName);
            signature.initSign(signingKey);
            signature.update(tbsCertificate);
            derEncoder.encodeBitString(signature.sign());
        } catch (NoSuchAlgorithmException | SignatureException | InvalidKeyException e) {
            throw log.certSigningFailed(e);
        }
        derEncoder.endSequence(); // Certificate

        byte[] bytes = b.toArray();
        final CertificateFactory factory = CertificateFactory.getInstance("X.509");
        return (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(bytes));
    }

    byte[] getTBSBytes() {
        final BigInteger serialNumber = this.serialNumber;
        // Cache and/or validate all fields.
        final int version = this.version;
        final String signatureAlgorithmName = this.signatureAlgorithmName;
        if (signatureAlgorithmName == null) {
            throw log.noSignatureAlgorithmNameGiven();
        }
        final String signatureAlgorithmOid = ASN1.oidFromSignatureAlgorithm(signatureAlgorithmName);
        if (signatureAlgorithmOid == null) {
            throw log.unknownSignatureAlgorithmName(signatureAlgorithmName);
        }
        final PrivateKey signingKey = this.signingKey;
        if (signingKey == null) {
            throw log.noSigningKeyGiven();
        }
        final String signingKeyAlgorithm = signingKey.getAlgorithm();
        if (!signatureAlgorithmName.endsWith("with" + signingKeyAlgorithm) || signatureAlgorithmName.contains("with" + signingKeyAlgorithm + "and")) {
            throw log.signingKeyNotCompatWithSig(signingKeyAlgorithm, signatureAlgorithmName);
        }
        final ZonedDateTime notValidBefore = this.notValidBefore;
        final ZonedDateTime notValidAfter = this.notValidAfter;
        if (notValidBefore.compareTo(notValidAfter) > 0) {
            throw log.validAfterBeforeValidBefore(notValidBefore, notValidAfter);
        }
        final X500Principal issuerDn = this.issuerDn;
        if (issuerDn == null) {
            throw log.noIssuerDnGiven();
        }
        final X500Principal subjectDn = this.subjectDn;
        final PublicKey publicKey = this.publicKey;
        if (publicKey == null) {
            throw log.noPublicKeyGiven();
        }
        final byte[] issuerUniqueId = this.issuerUniqueId;
        final byte[] subjectUniqueId = this.subjectUniqueId;
        if (version < 2 && (issuerUniqueId != null || subjectUniqueId != null)) {
            throw log.uniqueIdNotAllowed();
        }

        ByteStringBuilder b = new ByteStringBuilder();
        DEREncoder derEncoder = new DEREncoder(b);

        derEncoder.startSequence(); // TBSCertificate

        derEncoder.startExplicit(0);
        derEncoder.encodeInteger(version - 1);
        derEncoder.endExplicit();
        derEncoder.encodeInteger(serialNumber);
        derEncoder.startSequence(); // AlgorithmIdentifier
        derEncoder.encodeObjectIdentifier(signatureAlgorithmOid);
        derEncoder.endSequence(); // AlgorithmIdentifier
        derEncoder.writeEncoded(issuerDn.getEncoded()); // already a SEQUENCE of SET of SEQUENCE of { OBJECT IDENTIFIER, ANY }
        derEncoder.startSequence(); // Validity
        derEncoder.encodeGeneralizedTime(notValidBefore.withZoneSameInstant(ZoneOffset.UTC));
        derEncoder.encodeGeneralizedTime(notValidAfter.withZoneSameInstant(ZoneOffset.UTC));
        derEncoder.endSequence(); // Validity
        if (subjectDn != null)
            derEncoder.writeEncoded(subjectDn.getEncoded()); // already a SEQUENCE of SET of SEQUENCE of { OBJECT IDENTIFIER, ANY }

        final X509EncodedKeySpec keySpec;
        final String publicKeyAlgorithm = publicKey.getAlgorithm();
        try {
            final KeyFactory keyFactory = KeyFactory.getInstance(publicKeyAlgorithm);
            final Key translatedKey = keyFactory.translateKey(publicKey);
            keySpec = keyFactory.getKeySpec(translatedKey, X509EncodedKeySpec.class);
        } catch (NoSuchAlgorithmException | InvalidKeyException | InvalidKeySpecException e) {
            throw log.invalidKeyForCert(publicKeyAlgorithm, e);
        }
        derEncoder.writeEncoded(keySpec.getEncoded()); // SubjectPublicKeyInfo

        if (issuerUniqueId != null) {
            derEncoder.encodeImplicit(1);
            derEncoder.encodeBitString(issuerUniqueId);
        }
        if (subjectUniqueId != null) {
            derEncoder.encodeImplicit(2);
            derEncoder.encodeBitString(subjectUniqueId);
        }

        derEncoder.endSequence(); // TBSCertificate

        return b.toArray();
    }

    static class DEREncoder {
        private static final long LARGEST_UNSHIFTED_LONG = Long.MAX_VALUE / 10L;
        private static final TagComparator TAG_COMPARATOR = new TagComparator();

        private final ArrayDeque<EncoderState> states = new ArrayDeque<EncoderState>();
        private final ArrayList<ByteStringBuilder> buffers = new ArrayList<ByteStringBuilder>();
        private ByteStringBuilder currentBuffer;
        private int currentBufferPos = -1;
        private final ByteStringBuilder target;
        private int implicitTag = -1;

        /**
         * Create a DER encoder that writes its output to the given {@code ByteStringBuilder}.
         *
         * @param target the {@code ByteStringBuilder} to which the DER encoded values are written
         */
        public DEREncoder(ByteStringBuilder target) {
            this.target = target;
            currentBuffer = target;
        }

        public void startSequence() {
            startConstructedElement(ASN1.SEQUENCE_TYPE);
        }

        public void startSet() {
            startConstructedElement(SET_TYPE);
        }

        public void startSetOf() {
            startSet();
        }

        public void startExplicit(int number) {
            startExplicit(CONTEXT_SPECIFIC_MASK, number);
        }

        public void startExplicit(int clazz, int number) {
            int explicitTag = clazz | CONSTRUCTED_MASK | number;
            startConstructedElement(explicitTag);
        }

        private void startConstructedElement(int tag) {
            EncoderState lastState = states.peekLast();
            if ((lastState != null) && (lastState.getTag() == SET_TYPE)) {
                updateCurrentBuffer();
                lastState.addChildElement(tag, currentBufferPos);
            }
            writeTag(tag, currentBuffer);
            if (tag != SET_TYPE) {
                updateCurrentBuffer();
            }
            states.add(new EncoderState(tag, currentBufferPos));
        }

        public void endSequence() throws IllegalStateException {
            EncoderState lastState = states.peekLast();
            if ((lastState == null) || (lastState.getTag() != SEQUENCE_TYPE)) {
                throw log.noSequenceToEnd();
            }
            endConstructedElement();
        }

        public void endExplicit() throws IllegalStateException {
            EncoderState lastState = states.peekLast();
            if ((lastState == null) || (lastState.getTag() == SEQUENCE_TYPE)
                    || (lastState.getTag() == SET_TYPE) || ((lastState.getTag() & CONSTRUCTED_MASK) == 0)) {
                throw log.noExplicitlyTaggedElementToEnd();
            }
            endConstructedElement();
        }

        private void endConstructedElement() {
            ByteStringBuilder dest;
            if (currentBufferPos > 0) {
                // Output the element to its parent buffer
                dest = buffers.get(currentBufferPos - 1);
            } else {
                // Output the element directly to the target destination
                dest = target;
            }
            int length = currentBuffer.length();
            int numLengthOctets = writeLength(length, dest);
            dest.append(currentBuffer);
            currentBuffer.setLength(0);
            currentBuffer = dest;
            currentBufferPos -= 1;
            states.removeLast();

            // If this element's parent element is a set element, update the parent's accumulated length
            EncoderState lastState = states.peekLast();
            if ((lastState != null) && (lastState.getTag() == SET_TYPE)) {
                lastState.addChildLength(1 + numLengthOctets + length);
            }
        }

        public void endSet() throws IllegalStateException {
            endSet(TAG_COMPARATOR);
        }

        private void endSet(Comparator<EncoderState> comparator) {
            EncoderState lastState = states.peekLast();
            if ((lastState == null) || (lastState.getTag() != SET_TYPE)) {
                throw log.noSetToEnd();
            }

            // The child elements of a set must be encoded in ascending order by tag
            LinkedList<EncoderState> childElements = lastState.getSortedChildElements(comparator);
            int setBufferPos = lastState.getBufferPos();
            ByteStringBuilder dest;
            if (setBufferPos >= 0) {
                dest = buffers.get(setBufferPos);
            } else {
                dest = target;
            }

            ByteStringBuilder contents;
            int childLength = lastState.getChildLength();
            int numLengthOctets = writeLength(lastState.getChildLength(), dest);
            for (EncoderState element : childElements) {
                contents = buffers.get(element.getBufferPos());
                dest.append(contents);
                contents.setLength(0);
            }
            currentBuffer = dest;
            currentBufferPos = setBufferPos;
            states.removeLast();

            // If this set's parent element is a set element, update the parent's accumulated length
            lastState = states.peekLast();
            if ((lastState != null) && (lastState.getTag() == SET_TYPE)) {
                lastState.addChildLength(1 + numLengthOctets + childLength);
            }
        }

        public void encodeBitString(byte[] str) {
            encodeBitString(str, 0); // All bits will be used
        }

        public void encodeBitString(byte[] str, int numUnusedBits) {
            byte[] contents = new byte[str.length + 1];
            contents[0] = (byte) numUnusedBits;
            System.arraycopy(str, 0, contents, 1, str.length);
            writeElement(BIT_STRING_TYPE, contents);
        }
        private static final DateTimeFormatter GENERALIZED_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmssX");

        public void encodeGeneralizedTime(final ZonedDateTime time) {
            writeElement(GENERALIZED_TIME_TYPE, GENERALIZED_TIME_FORMAT.format(time).getBytes(StandardCharsets.UTF_8));
        }

        public void encodeObjectIdentifier(String objectIdentifier) {
            int len = objectIdentifier.length();
            if (len == 0) {
                throw log.asnOidMustHaveAtLeast2Components();
            }

            int offs = 0;
            int idx = 0;
            long t = 0L;
            char c;
            int numComponents = 0;
            int first = -1;
            ByteStringBuilder contents = new ByteStringBuilder();

            a:
            for (; ; ) {
                c = objectIdentifier.charAt(offs + idx++);
                if (Character.isDigit(c)) {
                    int digit = Character.digit(c, 10);
                    if (t > LARGEST_UNSHIFTED_LONG) {
                        BigInteger bi = BigInteger.valueOf(t).multiply(BigInteger.TEN).add(digits[digit]);
                        t = 0L;
                        for (; ; ) {
                            c = objectIdentifier.charAt(offs + idx++);
                            if (Character.isDigit(c)) {
                                digit = Character.digit(c, 10);
                                bi = bi.multiply(BigInteger.TEN).add(digits[digit]);
                            } else if (c == '.') {
                                if (numComponents == 0) {
                                    first = validateFirstOIDComponent(bi);
                                } else {
                                    encodeOIDComponent(bi, contents, numComponents, first);
                                }
                                numComponents++;
                                continue a;
                            } else {
                                throw log.asnInvalidOidCharacter();
                            }
                            if (idx == len) {
                                if (numComponents == 0) {
                                    throw log.asnOidMustHaveAtLeast2Components();
                                }
                                encodeOIDComponent(bi, contents, numComponents, first);
                                writeElement(OBJECT_IDENTIFIER_TYPE, contents);
                                return;
                            }
                        }
                    } else {
                        t = 10L * t + (long) digit;
                    }
                } else if (c == '.') {
                    if (numComponents == 0) {
                        first = validateFirstOIDComponent(t);
                    } else {
                        encodeOIDComponent(t, contents, numComponents, first);
                    }
                    numComponents++;
                    t = 0L;
                } else {
                    throw log.asnInvalidOidCharacter();
                }
                if (idx == len) {
                    if (c == '.') {
                        throw log.asnInvalidOidCharacter();
                    }
                    if (numComponents == 0) {
                        throw log.asnOidMustHaveAtLeast2Components();
                    }
                    encodeOIDComponent(t, contents, numComponents, first);
                    writeElement(OBJECT_IDENTIFIER_TYPE, contents);
                    return;
                }
            }
        }

        public void encodeImplicit(int number) {
            encodeImplicit(CONTEXT_SPECIFIC_MASK, number);
        }

        public void encodeImplicit(int clazz, int number) {
            if (implicitTag == -1) {
                implicitTag = clazz | number;
            }
        }

        void encodeInteger(int integer) {
            encodeInteger(BigInteger.valueOf(integer));
        }

        public void encodeInteger(BigInteger integer) {
            writeElement(ASN1.INTEGER_TYPE, integer.toByteArray());
        }

        public void writeEncoded(byte[] encoded) {
            EncoderState lastState = states.peekLast();
            if ((lastState != null) && (lastState.getTag() == ASN1.SET_TYPE)) {
                updateCurrentBuffer();
                lastState.addChildElement(encoded[0], currentBufferPos);
            }

            if (implicitTag != -1) {
                writeTag(encoded[0], currentBuffer);
                currentBuffer.append(encoded, 1, encoded.length - 1);
            } else {
                currentBuffer.append(encoded);
            }

            // If this element's parent element is a set element, update the parent's accumulated length
            if ((lastState != null) && (lastState.getTag() == ASN1.SET_TYPE)) {
                lastState.addChildLength(currentBuffer.length());
            }
        }

        public void flush() {
            while (states.size() != 0) {
                EncoderState lastState = states.peekLast();
                if (lastState.getTag() == ASN1.SEQUENCE_TYPE) {
                    endSequence();
                } else if (lastState.getTag() == ASN1.SET_TYPE) {
                    endSet();
                }
            }
        }

        private int validateFirstOIDComponent(long value) {
            if (value < 0 || value > 2) {
                throw log.asnInvalidValueForFirstOidComponent();
            }
            return (int) value;
        }

        private int validateFirstOIDComponent(BigInteger value) {
            if ((value.compareTo(BigInteger.valueOf(0)) == -1)
                    || (value.compareTo(BigInteger.valueOf(2)) == 1)) {
                throw log.asnInvalidValueForFirstOidComponent();
            }
            return value.intValue();
        }

        private void validateSecondOIDComponent(long second, int first) {
            if ((first < 2) && (second > 39)) {
                throw log.asnInvalidValueForSecondOidComponent();
            }
        }

        private void validateSecondOIDComponent(BigInteger second, int first) {
            if ((first < 2) && (second.compareTo(BigInteger.valueOf(39)) == 1)) {
                throw log.asnInvalidValueForSecondOidComponent();
            }
        }

        private void encodeOIDComponent(long value, ByteStringBuilder contents,
                                        int numComponents, int first) {
            if (numComponents == 1) {
                validateSecondOIDComponent(value, first);
                encodeOIDComponent(value + (40 * first), contents);
            } else {
                encodeOIDComponent(value, contents);
            }
        }

        private void encodeOIDComponent(BigInteger value, ByteStringBuilder contents,
                                        int numComponents, int first) {
            if (numComponents == 1) {
                validateSecondOIDComponent(value, first);
                encodeOIDComponent(value.add(BigInteger.valueOf(40 * first)), contents);
            } else {
                encodeOIDComponent(value, contents);
            }
        }

        private void encodeOIDComponent(long value, ByteStringBuilder contents) {
            int shift = 56;
            int octet;
            while (shift > 0) {
                if (value >= (1L << shift)) {
                    octet = (int) ((value >>> shift) | 0x80);
                    contents.append((byte) octet);
                }
                shift = shift - 7;
            }
            octet = (int) (value & 0x7f);
            contents.append((byte) octet);
        }

        private void encodeOIDComponent(BigInteger value, ByteStringBuilder contents) {
            int numBytes = (value.bitLength() + 6) / 7;
            if (numBytes == 0) {
                contents.append((byte) 0);
            } else {
                byte[] result = new byte[numBytes];
                BigInteger currValue = value;
                for (int i = numBytes - 1; i >= 0; i--) {
                    result[i] = (byte) ((currValue.intValue() & 0x7f) | 0x80);
                    currValue = currValue.shiftRight(7);
                }
                result[numBytes - 1] &= 0x7f;
                contents.append(result);
            }
        }

        private static final BigInteger[] digits = {
                BigInteger.ZERO,
                BigInteger.ONE,
                BigInteger.valueOf(2),
                BigInteger.valueOf(3),
                BigInteger.valueOf(4),
                BigInteger.valueOf(5),
                BigInteger.valueOf(6),
                BigInteger.valueOf(7),
                BigInteger.valueOf(8),
                BigInteger.valueOf(9),
        };

        private void writeTag(int tag, ByteStringBuilder dest) {
            int constructed = tag & ASN1.CONSTRUCTED_MASK;
            if (implicitTag != -1) {
                tag = implicitTag | constructed;
                implicitTag = -1;
            }
            int tagClass = tag & ASN1.CLASS_MASK;
            int tagNumber = tag & ASN1.TAG_NUMBER_MASK;
            if (tagNumber < 31) {
                dest.append((byte) (tagClass | constructed | tagNumber));
            } else {
                // High-tag-number-form
                dest.append((byte) (tagClass | constructed | 0x1f));
                if (tagNumber < 128) {
                    dest.append((byte) tagNumber);
                } else {
                    int shift = 28;
                    int octet;
                    while (shift > 0) {
                        if (tagNumber >= (1 << shift)) {
                            octet = (tagNumber >>> shift) | 0x80;
                            dest.append((byte) octet);
                        }
                        shift = shift - 7;
                    }
                    octet = tagNumber & 0x7f;
                    dest.append((byte) octet);
                }
            }
        }

        private int writeLength(int length, ByteStringBuilder dest) {
            int numLengthOctets;
            if (length < 0) {
                throw log.asnInvalidLength();
            } else if (length <= 127) {
                // Short form
                numLengthOctets = 1;
            } else {
                // Long form
                numLengthOctets = 1;
                int value = length;
                while ((value >>>= 8) != 0) {
                    numLengthOctets += 1;
                }
            }
            if (length > 127) {
                dest.append((byte) (numLengthOctets | 0x80));
            }
            for (int i = (numLengthOctets - 1) * 8; i >= 0; i -= 8) {
                dest.append((byte) (length >> i));
            }
            return numLengthOctets;
        }

        private void updateCurrentBuffer() {
            currentBufferPos += 1;
            if (currentBufferPos < buffers.size()) {
                currentBuffer = buffers.get(currentBufferPos);
            } else {
                ByteStringBuilder buffer = new ByteStringBuilder();
                buffers.add(buffer);
                currentBuffer = buffer;
            }
        }

        private void writeElement(int tag, byte[] contents) {
            EncoderState lastState = states.peekLast();
            if ((lastState != null) && (lastState.getTag() == ASN1.SET_TYPE)) {
                updateCurrentBuffer();
                lastState.addChildElement(tag, currentBufferPos);
            }

            writeTag(tag, currentBuffer);
            writeLength(contents.length, currentBuffer);
            currentBuffer.append(contents);

            // If this element's parent element is a set element, update the parent's accumulated length
            if ((lastState != null) && (lastState.getTag() == ASN1.SET_TYPE)) {
                lastState.addChildLength(currentBuffer.length());
            }
        }

        private void writeElement(int tag, ByteStringBuilder contents) {
            EncoderState lastState = states.peekLast();
            if ((lastState != null) && (lastState.getTag() == ASN1.SET_TYPE)) {
                updateCurrentBuffer();
                lastState.addChildElement(tag, currentBufferPos);
            }

            writeTag(tag, currentBuffer);
            writeLength(contents.length(), currentBuffer);
            currentBuffer.append(contents);

            // If this element's parent element is a set element, update the parent's accumulated length
            if ((lastState != null) && (lastState.getTag() == ASN1.SET_TYPE)) {
                lastState.addChildLength(currentBuffer.length());
            }
        }

        /**
         * A class used to maintain state information during DER encoding.
         */
        private class EncoderState {
            private final int tag;
            private final int bufferPos;
            private LinkedList<EncoderState> childElements = new LinkedList<EncoderState>();
            private int childLength = 0;

            public EncoderState(int tag, int bufferPos) {
                this.tag = tag;
                this.bufferPos = bufferPos;
            }

            public int getTag() {
                return tag;
            }

            public int getBufferPos() {
                return bufferPos;
            }

            public ByteStringBuilder getBuffer() {
                return buffers.get(getBufferPos());
            }

            public int getChildLength() {
                return childLength;
            }

            public LinkedList<EncoderState> getSortedChildElements(Comparator<EncoderState> comparator) {
                Collections.sort(childElements, comparator);
                return childElements;
            }

            public void addChildElement(int tag, int bufferPos) {
                childElements.add(new EncoderState(tag, bufferPos));
            }

            public void addChildLength(int length) {
                childLength += length;
            }
        }

        /**
         * A class that compares DER encodings based on their tags.
         */
        private static class TagComparator implements Comparator<EncoderState> {
            public int compare(EncoderState state1, EncoderState state2) {
                // Ignore the constructed bit when comparing tags
                return (state1.getTag() | ASN1.CONSTRUCTED_MASK) - (state2.getTag() | ASN1.CONSTRUCTED_MASK);
            }
        }
    }

    static class ASN1 {

        /**
         * The universal boolean type tag.
         */
        public static final int BOOLEAN_TYPE = 1;

        /**
         * The universal integer type tag.
         */
        public static final int INTEGER_TYPE = 2;

        /**
         * The universal bit string type tag.
         */
        public static final int BIT_STRING_TYPE = 3;

        /**
         * The universal octet string type tag.
         */
        public static final int OCTET_STRING_TYPE = 4;

        /**
         * The universal null type tag.
         */
        public static final int NULL_TYPE = 5;

        /**
         * The universal object identifier type tag.
         */
        public static final int OBJECT_IDENTIFIER_TYPE = 6;

        /**
         * The universal UTF-8 string type tag.
         */
        public static final int UTF8_STRING_TYPE = 12;

        /**
         * The universal printable string type tag.
         */
        public static final int PRINTABLE_STRING_TYPE = 19;

        /**
         * The universal IA5 string type tag.
         */
        public static final int IA5_STRING_TYPE = 22;

        /**
         * A type for representing timestamps.
         */
        public static final int GENERALIZED_TIME_TYPE = 24;

        /**
         * The universal (UTF-32 big-endian) string type tag.
         */
        public static final int UNIVERSAL_STRING_TYPE = 28;

        /**
         * The universal BMP (UTF-16 big-endian) string type tag.
         */
        public static final int BMP_STRING_TYPE = 30;

        /**
         * The universal sequence type tag.
         */
        public static final int SEQUENCE_TYPE = 48;

        /**
         * The universal set type tag.
         */
        public static final int SET_TYPE = 49;

        /**
         * Mask used to determine if a type tag is constructed.
         */
        public static final int CONSTRUCTED_MASK = 0x20;

        /**
         * Mask used to determine if a type tag is application-specific.
         */
        public static final int APPLICATION_SPECIFIC_MASK = 0x40;

        /**
         * Mask used to determine if a type tag is context-specific.
         */
        public static final int CONTEXT_SPECIFIC_MASK = 0x80;

        /**
         * Mask used to obtain the class bits from a type tag.
         */
        public static final int CLASS_MASK = 0xc0;

        /**
         * Mask used to obtain the tag number bits from a type tag.
         */
        public static final int TAG_NUMBER_MASK = 0x1f;

        // 1.2.840.10040

        /**
         * Object identifier for the SHA1 with DSA signature algorithm.
         */
        public static final String OID_SHA1_WITH_DSA = "1.2.840.10040.4.3";

        // 1.2.840.10045

        /**
         * Object identifier for the SHA1 with ECDSA signature algorithm.
         */
        public static final String OID_SHA1_WITH_ECDSA = "1.2.840.10045.4.1";

        /**
         * Object identifier for the SHA-225 with ECDSA signature algorithm.
         */
        public static final String OID_SHA224_WITH_ECDSA = "1.2.840.10045.4.3.1";

        /**
         * Object identifier for the SHA-256 with ECDSA signature algorithm.
         */
        public static final String OID_SHA256_WITH_ECDSA = "1.2.840.10045.4.3.2";

        /**
         * Object identifier for the SHA-384 with ECDSA signature algorithm.
         */
        public static final String OID_SHA384_WITH_ECDSA = "1.2.840.10045.4.3.3";

        /**
         * Object identifier for the SHA-512 with ECDSA signature algorithm.
         */
        public static final String OID_SHA512_WITH_ECDSA = "1.2.840.10045.4.3.4";

        // 1.2.840.113549.1

        /**
         * Object identifier for the MD2 with RSA signature algorithm.
         */
        public static final String OID_MD2_WITH_RSA = "1.2.840.113549.1.1.2";

        /**
         * Object identifier for the MD4 with RSA signature algorithm.
         */
        public static final String OID_MD4_WITH_RSA = "1.2.840.113549.1.1.3";

        /**
         * Object identifier for the MD5 with RSA signature algorithm.
         */
        public static final String OID_MD5_WITH_RSA = "1.2.840.113549.1.1.4";

        /**
         * Object identifier for the SHA1 with RSA signature algorithm.
         */
        public static final String OID_SHA1_WITH_RSA = "1.2.840.113549.1.1.5";

        /**
         * Object identifier for the SHA-256 with RSA signature algorithm.
         */
        public static final String OID_SHA256_WITH_RSA = "1.2.840.113549.1.1.11";

        /**
         * Object identifier for the SHA-384 with RSA signature algorithm.
         */
        public static final String OID_SHA384_WITH_RSA = "1.2.840.113549.1.1.12";

        /**
         * Object identifier for the SHA-512 with RSA signature algorithm.
         */
        public static final String OID_SHA512_WITH_RSA = "1.2.840.113549.1.1.13";

        @SuppressWarnings("SpellCheckingInspection")
        public static String oidFromSignatureAlgorithm(String algorithmName) {
            switch (algorithmName) {
                case "NONEwithRSA": {
                    return null;
                }
                case "MD2withRSA": {
                    return OID_MD2_WITH_RSA;
                }
                case "MD5withRSA": {
                    return OID_MD5_WITH_RSA;
                }
                case "SHA1withRSA": {
                    return OID_SHA1_WITH_RSA;
                }
                case "SHA256withRSA": {
                    return OID_SHA256_WITH_RSA;
                }
                case "SHA384withRSA": {
                    return OID_SHA384_WITH_RSA;
                }
                case "SHA512withRSA": {
                    return OID_SHA512_WITH_RSA;
                }
                case "NONEwithDSA": {
                    return null;
                }
                case "SHA1withDSA": {
                    return OID_SHA1_WITH_DSA;
                }
                case "NONEwithECDSA": {
                    return null;
                }
                case "ECDSA": // obsolete alias for SHA1withECDSA
                case "SHA1withECDSA": {
                    return OID_SHA1_WITH_ECDSA;
                }
                case "SHA256withECDSA": {
                    return OID_SHA256_WITH_ECDSA;
                }
                case "SHA384withECDSA": {
                    return OID_SHA384_WITH_ECDSA;
                }
                case "SHA512withECDSA": {
                    return OID_SHA512_WITH_ECDSA;
                }
                default: {
                    return null;
                }
            }
        }
    }

}
