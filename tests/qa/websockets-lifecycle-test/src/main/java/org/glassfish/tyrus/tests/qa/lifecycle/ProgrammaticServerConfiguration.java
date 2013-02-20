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
package org.glassfish.tyrus.tests.qa.lifecycle;

import org.glassfish.tyrus.tests.qa.lifecycle.ProgrammaticServer;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import javax.websocket.Decoder;
import javax.websocket.Encoder;
import javax.websocket.Extension;
import javax.websocket.HandshakeResponse;
import javax.websocket.server.HandshakeRequest;
import javax.websocket.server.ServerEndpointConfiguration;
import org.glassfish.tyrus.tests.qa.handlers.BasicMessageHandler;
import java.util.concurrent.ConcurrentMap;
import org.glassfish.tyrus.tests.qa.tools.SessionController;

/**
 *
 * @author michal.conos at oracle.com
 */
public class ProgrammaticServerConfiguration implements ServerEndpointConfiguration {

    private static ConcurrentMap<String, BasicMessageHandler> _messageHandlers = new ConcurrentHashMap<String, BasicMessageHandler>();
    
    public static void registerMessageHandler(String name, BasicMessageHandler handler) {
        _messageHandlers.put(name, handler);
    }
    
    public static BasicMessageHandler getMessageHandler(String name) {
        return _messageHandlers.get(name);
    }
    
    @Override
    public Class<?> getEndpointClass() {
        return ProgrammaticServer.class;
    }

    @Override
    public String getNegotiatedSubprotocol(List<String> list) {
        return null;
    }

    @Override
    public List<Extension> getNegotiatedExtensions(List<Extension> list) {
        return Collections.EMPTY_LIST;
    }

    @Override
    public boolean checkOrigin(String string) {
        return true;
    }

    @Override
    public boolean matchesURI(URI uri) {
        return true;
    }

    @Override
    public void modifyHandshake(HandshakeRequest hr, HandshakeResponse hr1) {
    }

    @Override
    public String getPath() {
        return LifeCycleDeployment.PROGRAMMATIC_ENDPOINT;
    }

    @Override
    public List<Encoder> getEncoders() {
        return Collections.EMPTY_LIST;
    }

    @Override
    public List<Decoder> getDecoders() {
        return Collections.EMPTY_LIST;
    }
}
