/*
 * (C) Copyright 2017-2019 ElasTest (http://elastest.io/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package io.elastest.eus.api.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import io.elastest.eus.api.session.SessionInfo;

/**
 * Service implementation session registry.
 *
 * @author Boni Garcia (boni.garcia@urjc.es)
 * @since 0.0.1
 */
@Service
public class RegistryService {

    private Map<String, SessionInfo> sessionRegistry = new ConcurrentHashMap<>();

    public void removeSession(String sessionId) {
        sessionRegistry.remove(sessionId);
    }

    public void putSession(String sessionId, SessionInfo sessionEntry) {
        sessionRegistry.put(sessionId, sessionEntry);
    }

    public SessionInfo getSession(String sessionId) {
        return sessionRegistry.get(sessionId);
    }

    public Map<String, SessionInfo> getSessionRegistry() {
        return sessionRegistry;
    }

}
