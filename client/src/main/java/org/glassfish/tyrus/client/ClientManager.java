/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2013 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * http://glassfish.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
package org.glassfish.tyrus.client;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import javax.websocket.ClientEndpoint;
import javax.websocket.ClientEndpointConfig;
import javax.websocket.ContainerProvider;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.Extension;
import javax.websocket.HandshakeResponse;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;

import org.glassfish.tyrus.core.*;
import org.glassfish.tyrus.spi.SPIHandshakeListener;
import org.glassfish.tyrus.spi.TyrusClientSocket;
import org.glassfish.tyrus.spi.TyrusContainer;

/**
 * ClientManager implementation.
 *
 * @author Stepan Kopriva (stepan.kopriva at oracle.com)
 * @author Pavel Bucek (pavel.bucek at oracle.com)
 */
public class ClientManager extends BaseContainer implements WebSocketContainer {

    /**
     * Default {@link TyrusContainer} class name.
     * <p/>
     * Uses Grizzly as transport implementation.
     */
    private static final String ENGINE_PROVIDER_CLASSNAME = "org.glassfish.tyrus.container.grizzly.GrizzlyEngine";
    private static final Logger LOGGER = Logger.getLogger(ClientManager.class.getName());
    private final TyrusContainer engine;
    private final ComponentProviderService componentProvider;
    private final ErrorCollector collector;
    private final Map<String, Object> properties = new HashMap<String, Object>();

    private long defaultAsyncSendTimeout;
    private long defaultMaxSessionIdleTimeout;
    private int maxBinaryMessageBufferSize;
    private int maxTextMessageBufferSize;

    /**
     * Create new {@link ClientManager} instance.
     * <p/>
     * Uses {@link ClientManager#ENGINE_PROVIDER_CLASSNAME} as container implementation, thus relevant module needs to
     * be on classpath. Setting different container is possible via {@link ClientManager#createClient(String)}.
     *
     * @see ClientManager#createClient(String)
     */
    public static ClientManager createClient() {
        return createClient(ENGINE_PROVIDER_CLASSNAME);
    }

    /**
     * Create new ClientManager instance.
     *
     * @return new ClientManager instance.
     */
    public static ClientManager createClient(String engineProviderClassname) {
        return new ClientManager(engineProviderClassname);
    }

    /**
     * Create new {@link ClientManager} instance.
     * <p/>
     * Uses {@link ClientManager#ENGINE_PROVIDER_CLASSNAME} as container implementation, thus relevant module needs to
     * be on classpath. Setting different container is possible via {@link ClientManager#createClient(String)}}.
     *
     * @see ClientManager#createClient(String)
     */
    public ClientManager() {
        this(ENGINE_PROVIDER_CLASSNAME);
    }

    private ClientManager(String engineProviderClassname) {
        collector = new ErrorCollector();
        componentProvider = ComponentProviderService.create();
        Class engineProviderClazz = null;
        try {
            engineProviderClazz = ReflectionHelper.classForNameWithException(engineProviderClassname);
        } catch (ClassNotFoundException e) {
            collector.addException(e);
        }
        LOGGER.config(String.format("Provider class loaded: %s", engineProviderClassname));
        this.engine = (TyrusContainer) ReflectionHelper.getInstance(engineProviderClazz, collector);
        TyrusContainerProvider.getContainerProvider().setContainer(this);
        if (!collector.isEmpty()) {
            throw new RuntimeException(collector.composeComprehensiveException());
        }
    }

    @Override
    public Session connectToServer(Class annotatedEndpointClass, URI path) throws DeploymentException {
        if (annotatedEndpointClass.getAnnotation(ClientEndpoint.class) == null) {
            throw new DeploymentException(String.format("Class argument in connectToServer(Class, URI) is to be annotated endpoint class." +
                    "Class %s does not have @ClientEndpoint", annotatedEndpointClass.getName()));
        }
        return connectToServer(annotatedEndpointClass, null, path.toString());
    }

    @Override
    public Session connectToServer(Class<? extends Endpoint> endpointClass, ClientEndpointConfig cec, URI path) throws DeploymentException {
        return connectToServer(endpointClass, cec, path.toString());
    }

    @Override
    public Session connectToServer(Endpoint endpointInstance, ClientEndpointConfig cec, URI path) throws DeploymentException, IOException {
        return connectToServer(endpointInstance, cec, path.toString());
    }

    public Session connectToServer(Object obj, URI path) throws DeploymentException {
        return connectToServer(obj, null, path.toString());
    }

    public Session connectToServer(Object obj, ClientEndpointConfig cec, URI path) throws DeploymentException {
        return connectToServer(obj, cec, path.toString());
    }

    /**
     * Connects client endpoint o to the specified url.
     *
     * @param o             the endpoint.
     * @param configuration of the endpoint.
     * @param url           to which the client will connect.
     * @return {@link Session}.
     * @throws DeploymentException
     */
    Session connectToServer(Object o, ClientEndpointConfig configuration, String url) throws DeploymentException {
        // TODO use maxSessionIdleTimeout, maxBinaryMessageBufferSize and maxTextMessageBufferSize
        ClientEndpointConfig config = null;
        Endpoint endpoint;
        TyrusClientSocket clientSocket = null;

        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.equals("ws") || scheme.equals("wss"))) {
                throw new DeploymentException("Incorrect scheme in WebSocket endpoint URI=" + url);
            }
        } catch (URISyntaxException e) {
            throw new DeploymentException("Incorrect WebSocket endpoint URI=" + url, e);
        }

        final CountDownLatch responseLatch = new CountDownLatch(1);

        try {
            if (o instanceof Endpoint) {
                endpoint = (Endpoint) o;
                config = configuration == null ? ClientEndpointConfig.Builder.create().build() : configuration;
            } else if (o instanceof Class) {
                if (Endpoint.class.isAssignableFrom((Class<?>) o)) {
                    //noinspection unchecked
                    endpoint = ReflectionHelper.getInstance(((Class<Endpoint>) o), collector);
                    config = configuration == null ? ClientEndpointConfig.Builder.create().build() : configuration;
                } else if ((((Class<?>) o).getAnnotation(ClientEndpoint.class) != null)) {
                    endpoint = AnnotatedEndpoint.fromClass((Class) o, componentProvider, false, collector);
                    config = (ClientEndpointConfig) ((AnnotatedEndpoint) endpoint).getEndpointConfig();
                } else {
                    collector.addException(new DeploymentException(String.format("Class %s in not Endpoint descendant and does not have @ClientEndpoint", ((Class<?>) o).getName())));
                    endpoint = null;
                    config = null;
                }
            } else {
                endpoint = AnnotatedEndpoint.fromInstance(o, componentProvider, false, collector);
                config = (ClientEndpointConfig) ((AnnotatedEndpoint) endpoint).getEndpointConfig();
            }

            final ClientEndpointConfig finalConfig = config;

            if (endpoint != null) {
                EndpointWrapper clientEndpoint = new EndpointWrapper(endpoint, config, componentProvider, this, url, collector, null);
                SPIHandshakeListener listener = new SPIHandshakeListener() {

                    @Override
                    public void onResponseHeaders(final Map<String, String> originalHeaders) {

                        final Map<String, List<String>> headers =
                                new TreeMap<String, List<String>>(new Comparator<String>() {

                                    @Override
                                    public int compare(String o1, String o2) {
                                        return o1.toLowerCase().compareTo(o2.toLowerCase());
                                    }
                                });

                        for (Map.Entry<String, String> entry : originalHeaders.entrySet()) {
                            headers.put(entry.getKey(), Arrays.asList(entry.getValue()));
                        }

                        finalConfig.getConfigurator().afterResponse(new HandshakeResponse() {

                            @Override
                            public Map<String, List<String>> getHeaders() {
                                return headers;
                            }
                        });

                        responseLatch.countDown();
                    }

                    @Override
                    public void onError(Throwable exception) {
                        finalConfig.getUserProperties().put("org.glassfish.tyrus.client.exception", exception);
                        responseLatch.countDown();
                    }
                };
                clientSocket = engine.openClientSocket(url, config, clientEndpoint, listener, properties);
            }

        } catch (Exception e) {
            collector.addException(new DeploymentException("Connection failed.", e));
        }

        if (!collector.isEmpty()) {
            throw collector.composeComprehensiveException();
        }

        if (clientSocket != null) {
            try {
                // TODO - configurable timeout?
                responseLatch.await(10, TimeUnit.SECONDS);
                if (responseLatch.getCount() == 0) {
                    final Object exception = config.getUserProperties().get("org.glassfish.tyrus.client.exception");
                    if (exception != null) {
                        throw new DeploymentException("Handshake error.", (Throwable) exception);
                    }

                    return clientSocket.getSession();
                }
            } catch (InterruptedException e) {
                throw new DeploymentException("Handshaker response not received.", e);
            }

            throw new DeploymentException("Handshake response not received.");
        }

        return null;
    }

    @Override
    public int getDefaultMaxBinaryMessageBufferSize() {
        return maxBinaryMessageBufferSize;
    }

    @Override
    public void setDefaultMaxBinaryMessageBufferSize(int i) {
        maxBinaryMessageBufferSize = i;
    }

    @Override
    public int getDefaultMaxTextMessageBufferSize() {
        return maxTextMessageBufferSize;
    }

    @Override
    public void setDefaultMaxTextMessageBufferSize(int i) {
        maxTextMessageBufferSize = i;
    }

    @Override
    public Set<Extension> getInstalledExtensions() {
        return Collections.emptySet();
    }

    @Override
    public long getDefaultAsyncSendTimeout() {
        return defaultAsyncSendTimeout;
    }

    @Override
    public void setAsyncSendTimeout(long timeoutmillis) {
        this.defaultAsyncSendTimeout = timeoutmillis;
    }

    @Override
    public long getDefaultMaxSessionIdleTimeout() {
        return defaultMaxSessionIdleTimeout;
    }

    @Override
    public void setDefaultMaxSessionIdleTimeout(long defaultMaxSessionIdleTimeout) {
        this.defaultMaxSessionIdleTimeout = defaultMaxSessionIdleTimeout;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }
}
