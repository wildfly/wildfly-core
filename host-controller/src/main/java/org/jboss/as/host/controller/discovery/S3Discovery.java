/*
* JBoss, Home of Professional Open Source.
* Copyright 2013, Red Hat Middleware LLC, and individual contributors
* as indicated by the @author tags. See the copyright.txt file in the
* distribution for a full listing of individual contributors.
*
* This is free software; you can redistribute it and/or modify it
* under the terms of the GNU Lesser General Public License as
* published by the Free Software Foundation; either version 2.1 of
* the License, or (at your option) any later version.
*
* This software is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
* Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public
* License along with this software; if not, write to the Free
* Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
* 02110-1301 USA, or see the FSF site: http://www.fsf.org.
*/

package org.jboss.as.host.controller.discovery;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MASTER;
import static org.jboss.as.host.controller.discovery.Constants.ACCESS_KEY;
import static org.jboss.as.host.controller.discovery.Constants.LOCATION;
import static org.jboss.as.host.controller.discovery.Constants.PREFIX;
import static org.jboss.as.host.controller.discovery.Constants.PRE_SIGNED_DELETE_URL;
import static org.jboss.as.host.controller.discovery.Constants.PRE_SIGNED_PUT_URL;
import static org.jboss.as.host.controller.discovery.Constants.SECRET_ACCESS_KEY;
import static org.jboss.as.host.controller.logging.HostControllerLogger.ROOT_LOGGER;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.host.controller.discovery.S3Util.AWSAuthConnection;
import org.jboss.as.host.controller.discovery.S3Util.Bucket;
import org.jboss.as.host.controller.discovery.S3Util.GetResponse;
import org.jboss.as.host.controller.discovery.S3Util.ListAllMyBucketsResponse;
import org.jboss.as.host.controller.discovery.S3Util.PreSignedUrlParser;
import org.jboss.as.host.controller.discovery.S3Util.S3Object;
import org.jboss.as.host.controller.logging.HostControllerLogger;
import org.jboss.as.remoting.Protocol;
import org.jboss.dmr.ModelNode;

/**
 * Handle domain controller discovery via Amazon's S3 storage.
 * The S3 access code reuses the example shipped by Amazon.
 *
 * @author Farah Juma
 */
public class S3Discovery implements DiscoveryOption {

    // The name of the S3 file that will store the domain controller's host and port
    private static final String DC_FILE_NAME="jboss-domain-master-data";

    // The access key to AWS (S3)
    private String access_key = null;

    // The secret access key to AWS (S3)
    private String secret_access_key = null;

    // The name of the S3 bucket
    private String location = null;

    // The name of the S3 bucket prefix
    private String prefix = null;

    // The pre-signed URL for PUTs
    private String pre_signed_put_url = null;

    // The pre-signed URL for DELETEs
    private String pre_signed_delete_url = null;

    private AWSAuthConnection conn = null;

    /**
     * Create the S3Discovery option.
     *
     * @param properties map of properties needed to access the S3 bucket
     */
    public S3Discovery(Map<String, ModelNode> properties) {
        ModelNode accessKeyNode = properties.get(ACCESS_KEY);
        access_key = (accessKeyNode == null || !accessKeyNode.isDefined()) ? null : accessKeyNode.asString();

        ModelNode secretAccessKeyNode = properties.get(SECRET_ACCESS_KEY);
        secret_access_key = (secretAccessKeyNode == null || !secretAccessKeyNode.isDefined()) ? null : secretAccessKeyNode.asString();

        ModelNode locationNode = properties.get(LOCATION);
        location = (locationNode == null || !locationNode.isDefined()) ? null : locationNode.asString();

        ModelNode prefixNode = properties.get(PREFIX);
        prefix = (prefixNode == null || !prefixNode.isDefined()) ? null : prefixNode.asString();

        ModelNode preSignedPutUrlNode = properties.get(PRE_SIGNED_PUT_URL);
        pre_signed_put_url = (preSignedPutUrlNode == null || !preSignedPutUrlNode.isDefined()) ? null : preSignedPutUrlNode.asString();

        ModelNode preSignedDeleteUrlNode = properties.get(PRE_SIGNED_DELETE_URL);
        pre_signed_delete_url = (preSignedDeleteUrlNode == null || !preSignedDeleteUrlNode.isDefined()) ? null : preSignedDeleteUrlNode.asString();
    }

    @Override
    public void allowDiscovery(List<DomainControllerManagementInterface> interfaces) {
        try {
            // Write the domain controller data to an S3 file
            List<DomainControllerData> data = new ArrayList<>(interfaces.size());
            for (DomainControllerManagementInterface managementInterface : interfaces) {
                data.add(new DomainControllerData(managementInterface.getProtocol().toString(), managementInterface.getHost(), managementInterface.getPort()));
            }
            Collections.sort(data, new Comparator<DomainControllerData>() {
                @Override
                public int compare(DomainControllerData data, DomainControllerData otherData) {
                    Protocol protocol = Protocol.forName(data.getProtocol());
                    if (Protocol.REMOTE == protocol) {
                        return 1;
                    }
                    if (Protocol.HTTPS_REMOTING == protocol || Protocol.REMOTE_HTTPS == protocol) {
                        Protocol otherProtocol  = Protocol.forName(otherData.getProtocol());
                        if (Protocol.REMOTE == otherProtocol) {
                            return -1;
                        }
                        return 1;
                    }
                    return -1;
                }});
            writeToFile(data, MASTER);
        } catch (Exception e) {
            ROOT_LOGGER.cannotWriteDomainControllerData(e);
        }
    }

    @Override
    public List<RemoteDomainControllerConnectionConfiguration> discover() {
        // Read the domain controller data from an S3 file
        List<DomainControllerData> dataDc = readFromFile(MASTER);
        List<RemoteDomainControllerConnectionConfiguration> options = new ArrayList<>(dataDc.size());
        for (DomainControllerData data : dataDc) {
            if (data != null) {
                // Validate and set the host and port
                RemoteDomainControllerConnectionConfiguration discovery = new RemoteDomainControllerConnectionConfiguration(
                        data.getProtocol(), data.getHost(), data.getPort());
                String host = data.getHost();
                try {
                    // Use the static discovery AD's. They don't allow undefined.
                    StaticDiscoveryResourceDefinition.HOST.getValidator()
                            .validateParameter(StaticDiscoveryResourceDefinition.HOST.getName(),
                                    host == null ? new ModelNode() : new ModelNode(host));
                    StaticDiscoveryResourceDefinition.PORT.getValidator()
                            .validateParameter(StaticDiscoveryResourceDefinition.PORT.getName(), new ModelNode(discovery.getPort()));
                    StaticDiscoveryResourceDefinition.PROTOCOL.getValidator()
                            .validateParameter(StaticDiscoveryResourceDefinition.PROTOCOL.getName(), new ModelNode(discovery.getProtocol()));
                    options.add(discovery);
                } catch (OperationFailedException e) {
                }
            } else {
                throw HostControllerLogger.ROOT_LOGGER.failedMarshallingDomainControllerData();
            }
        }
        if (options.isEmpty()) {
            throw HostControllerLogger.ROOT_LOGGER.failedMarshallingDomainControllerData();
        }
        return options;
    }

    @Override
    public void cleanUp() {
        // Remove the S3 file
        remove(MASTER);
    }

    @Override
        public String toString() {
        // TODO consider including 'location' but that may be sensitive data not wanted in logs
        return getClass().getSimpleName();
    }

    /**
     * Determine whether or not pre-signed URLs will be used.
     */
    private boolean usingPreSignedUrls() {
        return pre_signed_put_url != null;
    }

    /**
     * Make sure {@code pre_signed_put_url} and {@code pre_signed_delete_url} are valid.
     */
    private void validatePreSignedUrls() {
        if (pre_signed_put_url != null && pre_signed_delete_url != null) {
            PreSignedUrlParser parsedPut = new PreSignedUrlParser(pre_signed_put_url);
            PreSignedUrlParser parsedDelete = new PreSignedUrlParser(pre_signed_delete_url);
            if (!parsedPut.getBucket().equals(parsedDelete.getBucket()) ||
                    !parsedPut.getPrefix().equals(parsedDelete.getPrefix())) {
                throw HostControllerLogger.ROOT_LOGGER.preSignedUrlsMustHaveSamePath();
            }
        } else if (pre_signed_put_url != null || pre_signed_delete_url != null) {
            throw HostControllerLogger.ROOT_LOGGER.preSignedUrlsMustBeSetOrUnset();
        }
    }

    /**
     * Do the set-up that's needed to access Amazon S3.
     */
    private void init() {
        validatePreSignedUrls();

        try {
            conn = new AWSAuthConnection(access_key, secret_access_key);
            // Determine the bucket name if prefix is set or if pre-signed URLs are being used
            if (prefix != null && prefix.length() > 0) {
                ListAllMyBucketsResponse bucket_list = conn.listAllMyBuckets(null);
                List buckets = bucket_list.entries;
                if (buckets != null) {
                    boolean found = false;
                    for (Object tmp : buckets) {
                        if (tmp instanceof Bucket) {
                            Bucket bucket = (Bucket) tmp;
                            if (bucket.name.startsWith(prefix)) {
                                location = bucket.name;
                                found = true;
                            }
                        }
                    }
                    if (!found) {
                        location = prefix + "-" + java.util.UUID.randomUUID().toString();
                    }
                }
            }
            if (usingPreSignedUrls()) {
                PreSignedUrlParser parsedPut = new PreSignedUrlParser(pre_signed_put_url);
                location = parsedPut.getBucket();
            }
            if (!conn.checkBucketExists(location)) {
                conn.createBucket(location, AWSAuthConnection.LOCATION_DEFAULT, null).connection.getResponseMessage();
            }
        } catch (Exception e) {
            throw HostControllerLogger.ROOT_LOGGER.cannotAccessS3Bucket(location, e.getLocalizedMessage());
        }
    }

    /**
     * Read the domain controller data from an S3 file.
     *
     * @param directoryName the name of the directory in the bucket that contains the S3 file
     * @return the domain controller data
     */
    private List<DomainControllerData> readFromFile(String directoryName) {
        List<DomainControllerData> data = new ArrayList<DomainControllerData>();
        if (directoryName == null) {
            return data;
        }

        if (conn == null) {
            init();
        }

        try {
            if (usingPreSignedUrls()) {
                PreSignedUrlParser parsedPut = new PreSignedUrlParser(pre_signed_put_url);
                directoryName = parsedPut.getPrefix();
            }
            String key = S3Util.sanitize(directoryName) + "/" + S3Util.sanitize(DC_FILE_NAME);
            GetResponse val = conn.get(location, key, null);
            if (val.object != null) {
                byte[] buf = val.object.data;
                if (buf != null && buf.length > 0) {
                    try {
                        data = S3Util.domainControllerDataFromByteBuffer(buf);
                    } catch (Exception e) {
                        throw HostControllerLogger.ROOT_LOGGER.failedMarshallingDomainControllerData();
                    }
                }
            }
            return data;
        } catch (IOException e) {
            throw HostControllerLogger.ROOT_LOGGER.cannotAccessS3File(e.getLocalizedMessage());
        }
    }

    /**
     * Write the domain controller data to an S3 file.
     *
     * @param data the domain controller data
     * @param domainName the name of the directory in the bucket to write the S3 file to
     * @throws IOException
     */
    private void writeToFile(List<DomainControllerData> data, String domainName) throws IOException {
        if(domainName == null || data == null) {
            return;
        }

        if (conn == null) {
            init();
        }

        try {
            String key = S3Util.sanitize(domainName) + "/" + S3Util.sanitize(DC_FILE_NAME);
            byte[] buf = S3Util.domainControllerDataToByteBuffer(data);
            S3Object val = new S3Object(buf, null);
            if (usingPreSignedUrls()) {
                Map headers = new TreeMap();
                headers.put("x-amz-acl", Arrays.asList("public-read"));
                conn.put(pre_signed_put_url, val, headers).connection.getResponseMessage();
            } else {
                Map headers = new TreeMap();
                headers.put("Content-Type", Arrays.asList("text/plain"));
                conn.put(location, key, val, headers).connection.getResponseMessage();
            }
        }
        catch(Exception e) {
            throw HostControllerLogger.ROOT_LOGGER.cannotWriteToS3File(e.getLocalizedMessage());
        }
    }

    /**
     * Remove the S3 file that contains the domain controller data.
     *
     * @param directoryName the name of the directory that contains the S3 file
     */
    private void remove(String directoryName) {
        if ((directoryName == null) || (conn == null))
            return;

        String key = S3Util.sanitize(directoryName) + "/" + S3Util.sanitize(DC_FILE_NAME);
        try {
            Map headers = new TreeMap();
            headers.put("Content-Type", Arrays.asList("text/plain"));
            if (usingPreSignedUrls()) {
                conn.delete(pre_signed_delete_url).connection.getResponseMessage();
            } else {
                conn.delete(location, key, headers).connection.getResponseMessage();
            }
        }
        catch(Exception e) {
            ROOT_LOGGER.cannotRemoveS3File(e);
        }
    }
}
