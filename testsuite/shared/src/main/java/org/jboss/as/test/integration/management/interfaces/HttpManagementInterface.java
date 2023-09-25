/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.integration.management.interfaces;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILURE_DESCRIPTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import javax.net.ssl.SSLContext;

import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.config.AuthSchemes;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.StandardHttpRequestRetryHandler;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;
import org.jboss.dmr.ModelNode;

/**
 * @author Ladislav Thon <lthon@redhat.com>
 */
public class HttpManagementInterface implements ManagementInterface {
    public static final String MANAGEMENT_REALM = "ManagementRealm";

    private final URI uri;
    private final CloseableHttpClient httpClient;

    public HttpManagementInterface(String uriScheme, String host, int port, String username, String password) {
        try {
            this.uri = new URI(uriScheme + "://" + host + ":" + port + "/management");
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

        this.httpClient = createHttpClient(host, port, username, password);
    }

    @Override
    public ModelNode execute(ModelNode operation) {
        String operationJson = operation.toJSONString(true);
        try {
            HttpPost post = new HttpPost(uri);
            post.setEntity(new StringEntity(operationJson, ContentType.APPLICATION_JSON));
            post.setHeader(HttpHeaders.ACCEPT, ContentType.APPLICATION_JSON.getMimeType());
            HttpResponse response = httpClient.execute(post);
            return parseResponse(response);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private ModelNode parseResponse(HttpResponse response) {
        try {
            String content = EntityUtils.toString(response.getEntity());
            int status = response.getStatusLine().getStatusCode();
            ModelNode modelResponse;
            if (status == HttpStatus.SC_OK) {
                modelResponse = ModelNode.fromJSONString(content);
            } else {
                modelResponse = new ModelNode();
                modelResponse.get(OUTCOME).set(FAILED);
                modelResponse.get(FAILURE_DESCRIPTION).set(content);
            }
            return modelResponse;
        } catch (IOException e) {
            throw new RuntimeException("Unable to read response content as String");
        }
    }

    @Override
    public void close() throws IOException {
        httpClient.close();
    }

    private static CloseableHttpClient createHttpClient(String host, int port, String username, String password) {
        SSLContext sslContext = org.apache.http.ssl.SSLContexts.createDefault();
                    SSLConnectionSocketFactory sslConnectionSocketFactory = new SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE);

        Registry<ConnectionSocketFactory> registry = RegistryBuilder.<ConnectionSocketFactory>create()
                        .register("http", PlainConnectionSocketFactory.getSocketFactory())
                        .register("https", sslConnectionSocketFactory)
                        .build();
        BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(new AuthScope(host, port, MANAGEMENT_REALM, AuthSchemes.DIGEST),
                        new UsernamePasswordCredentials(username, password));

        return HttpClientBuilder.create()
                        .setConnectionManager(new PoolingHttpClientConnectionManager(registry))
                        .setRetryHandler(new StandardHttpRequestRetryHandler(5, true))
                        .setDefaultCredentialsProvider(credentialsProvider)
                        .build();
    }

    public static ManagementInterface create(String host, int port, String username, String password) {
        return new HttpManagementInterface("http", host, port, username, password);
    }

    public static ManagementInterface createSecure(String host, int port, String username, String password) {
        return new HttpManagementInterface("https", host, port, username, password);
    }
}
