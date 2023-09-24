/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.integration.management.util;

import io.undertow.util.StatusCodes;
import java.net.URL;

import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.jboss.as.test.http.Authentication;
import org.jboss.dmr.ModelNode;

/**
 *
 * @author Dominik Pospisil <dpospisi@redhat.com>
 */
public class HttpMgmtProxy {

    private static final String APPLICATION_JSON = "application/json";
    private URL url;
    private HttpClient httpClient;
    private HttpContext httpContext = new BasicHttpContext();

    public HttpMgmtProxy(URL mgmtURL) {
        this.url = mgmtURL;
        CredentialsProvider credsProvider = new BasicCredentialsProvider();
                credsProvider.setCredentials(new AuthScope(url.getHost(), url.getPort()), new UsernamePasswordCredentials(Authentication.USERNAME, Authentication.PASSWORD));

        this.httpClient = HttpClients.custom()
                .setDefaultCredentialsProvider(credsProvider)
                .build();
    }

    public ModelNode sendGetCommand(String cmd) throws Exception {
        return ModelNode.fromJSONString(sendGetCommandJson(cmd));
    }

    public String sendGetCommandJson(String cmd) throws Exception {
        HttpGet get = new HttpGet(url.toURI().toString() + cmd);

        HttpResponse response = httpClient.execute(get, httpContext);
        return EntityUtils.toString(response.getEntity());
    }

    public ModelNode sendPostCommand(String address, String operation) throws Exception {
        return sendPostCommand(getOpNode(address, operation));
    }

    public ModelNode sendPostCommand(ModelNode cmd) throws Exception {
        return sendPostCommand(cmd, false);
    }

    public ModelNode sendPostCommand(ModelNode cmd, boolean ignoreFailure) throws Exception {
        String cmdStr = cmd.toJSONString(true);
        HttpPost post = new HttpPost(url.toURI());
        StringEntity entity = new StringEntity(cmdStr);
        entity.setContentType(APPLICATION_JSON);
        post.setEntity(entity);

        HttpResponse response = httpClient.execute(post, httpContext);
        String str = EntityUtils.toString(response.getEntity());
        if (response.getStatusLine().getStatusCode() == StatusCodes.OK || ignoreFailure) {
            return ModelNode.fromJSONString(str);
        }
        throw new Exception("Could not execute command: " + str);
    }

    public static ModelNode getOpNode(String address, String operation) {
        ModelNode op = new ModelNode();

        // set address
        String[] pathSegments = address.split("/");
        ModelNode list = op.get("address").setEmptyList();
        for (String segment : pathSegments) {
            String[] elements = segment.split("=");
            list.add(elements[0], elements[1]);
        }
        op.get("operation").set(operation);
        return op;
    }
}
