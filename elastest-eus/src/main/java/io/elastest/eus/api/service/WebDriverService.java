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
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.github.dockerjava.api.model.AccessMode;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Volume;

import io.elastest.eus.api.EusException;

/**
 * Service implementation for W3C WebDriver/JSON Wire Protocol.
 *
 * @author Boni Garcia (boni.garcia@urjc.es)
 * @since 0.0.1
 */
@Service
public class WebDriverService {

    private final Logger log = LoggerFactory.getLogger(WebDriverService.class);

    @Value("${server.contextPath}")
    private String contextPath;

    @Value("${eus.container.prefix}")
    private String eusContainerPrefix;

    @Value("${novnc.image.id}")
    private String noVncImageId;

    @Value("${novnc.port}")
    private int noVncPort;

    @Value("${novnc.container.sufix}")
    private String noVncContainerSufix;

    @Value("${novnc.autofocus.html}")
    private String vncAutoFocusHtml;

    @Value("${hub.port}")
    private int hubPort;

    @Value("${hub.vnc.port}")
    private int hubVncPort;

    @Value("${hub.container.sufix}")
    private String hubContainerSufix;

    @Value("${hub.vnc.password}")
    private String hubVncPassword;

    private DockerService dockerService;
    private PropertiesService propertiesService;
    private JsonService jsonService;
    private Map<String, SessionInfo> sessionRegistry = new ConcurrentHashMap<>();

    @Autowired
    public WebDriverService(DockerService dockerService,
            PropertiesService propertiesService, JsonService jsonService) {
        this.dockerService = dockerService;
        this.propertiesService = propertiesService;
        this.jsonService = jsonService;
    }

    public ResponseEntity<String> process(HttpEntity<String> httpEntity,
            HttpServletRequest request) {

        StringBuffer requestUrl = request.getRequestURL();
        String requestContext = requestUrl.substring(
                requestUrl.lastIndexOf(contextPath) + contextPath.length());
        HttpMethod method = HttpMethod.resolve(request.getMethod());
        log.debug("{} {}", method, requestContext);

        log.trace(">> Request : {}", httpEntity.getBody());

        SessionInfo sessionInfo;

        // Intercept create session
        if (jsonService.isPostSessionRequest(method, requestContext)) {
            sessionInfo = starBrowser(httpEntity.getBody());

            // -------------
            // FIXME: Workaround due to bug of selenium-server 3.4.0
            // More info on: https://github.com/SeleniumHQ/selenium/issues/3808
            String browserName = jsonService.getBrowser(httpEntity.getBody());
            String version = jsonService.getVersion(httpEntity.getBody());
            if (browserName.equalsIgnoreCase("firefox")
                    && !version.equals("")) {
                version = "";
                log.warn(
                        "Due to a bug in selenium-server 3.4.0 the W3C capabilities are not handled correctly");
                httpEntity = new HttpEntity<String>("{\n"
                        + "  \"desiredCapabilities\": {\n"
                        + "    \"browserName\": \"firefox\",\n"
                        + "    \"version\": \"\",\n"
                        + "    \"platform\": \"ANY\"\n" + "  }\n" + "}");
            }
            // -------------

        } else {
            Optional<String> sessionIdFromPath = jsonService
                    .getSessionIdFromPath(requestContext);
            if (sessionIdFromPath.isPresent()) {
                String sessionId = sessionIdFromPath.get();
                log.trace("sessionId: {} -- sessionRegistry: {}", sessionId,
                        sessionRegistry);
                sessionInfo = sessionRegistry.get(sessionId);

            } else {
                // This is a special case that it is very unlikely to happen (in
                // theory, clients should only call a different operation of
                // POST /session after the first time. The only regular case out
                // of this rule is the command GET /status, which is meaningless
                // in EUS
                String errorMessage = "Command " + method + " " + requestContext
                        + " not valid";
                log.error(errorMessage);
                throw new EusException(errorMessage);
            }
        }

        String hubUrl = sessionInfo.getHubUrl();
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> exchange = restTemplate.exchange(
                hubUrl + requestContext, method, httpEntity, String.class);
        String response = exchange.getBody();

        log.trace("<< Response: {}", response);

        // Intercept again create session
        if (jsonService.isPostSessionRequest(method, requestContext)) {
            String sessionId = jsonService.getSessionIdFromResponse(response);
            sessionRegistry.put(sessionId, sessionInfo);
        }

        ResponseEntity<String> responseEntity = new ResponseEntity<>(response,
                HttpStatus.OK);

        log.debug("ResponseEntity {}", responseEntity);

        // Intercept destroy session
        if (jsonService.isDeleteSessionRequest(method, requestContext)) {
            log.trace("Intercepted DELETE session");

            stopAllContainerOfSession(sessionInfo);

            // TODO: Implement a timeout mechanism just in case this command is
            // never invoked
        }

        return responseEntity;
    }

    public void stopAllContainerOfSession(String sessionId) {
        stopAllContainerOfSession(sessionRegistry.get(sessionId));
    }

    public void stopAllContainerOfSession(SessionInfo sessionInfo) {
        String hubContainerName = sessionInfo.getHubContainerName();
        if (hubContainerName != null) {
            dockerService.stopAndRemoveContainer(hubContainerName);
        }

        String vncContainerName = sessionInfo.getVncContainerName();
        if (vncContainerName != null) {
            dockerService.stopAndRemoveContainer(vncContainerName);
        }
    }

    public SessionInfo starBrowser(String jsonCapabilities) {
        String browserName = jsonService.getBrowser(jsonCapabilities);
        String version = jsonService.getVersion(jsonCapabilities);
        String platform = jsonService.getPlatform(jsonCapabilities);
        String imageId = propertiesService
                .getDockerImageFromCapabilities(browserName, version, platform);

        String hubContainerName = dockerService
                .generateContainerName(eusContainerPrefix + hubContainerSufix);

        log.debug("Starting browser with container name {}", hubContainerName);

        dockerService.startAndWaitContainer(imageId, hubContainerName);

        String hubUrl = getHubUrl(hubContainerName);
        dockerService.waitForHostIsReachable(hubUrl);

        log.trace("Container: {} -- Hub URL: {}", hubContainerName, hubUrl);

        SessionInfo sessionInfo = new SessionInfo();
        sessionInfo.setHubUrl(hubUrl);
        sessionInfo.setHubContainerName(hubContainerName);

        return sessionInfo;
    }

    public String getHubUrl(String containerName) {
        return "http://" + dockerService.getContainerIpAddress(containerName)
                + ":" + hubPort + "/wd/hub";
    }

    public ResponseEntity<String> getVncUrl(String sessionId) {
        String vncContainerName = dockerService.generateContainerName(
                eusContainerPrefix + noVncContainerSufix);

        String originPath = ClassLoader.getSystemResource(vncAutoFocusHtml)
                .getFile();
        String targetPath = "/root/noVNC/" + vncAutoFocusHtml;
        Volume[] volumes = { new Volume(targetPath) };
        Bind[] binds = { new Bind(originPath, volumes[0], AccessMode.rw) };

        log.trace("Mounting volume {} from {}", targetPath, originPath);

        dockerService.startAndWaitContainerWithVolumes(noVncImageId,
                vncContainerName, volumes, binds);
        String vncContainerIp = dockerService
                .getContainerIpAddress(vncContainerName);

        SessionInfo sessionInfo = sessionRegistry.get(sessionId);

        String hubContainerIp = dockerService
                .getContainerIpAddress(sessionInfo.getHubContainerName());
        String vncUrl = "http://" + vncContainerIp + ":" + noVncPort + "/"
                + vncAutoFocusHtml + "?host=" + hubContainerIp + "&port="
                + hubVncPort + "&resize=scale&autoconnect=true&password="
                + hubVncPassword;

        dockerService.waitForHostIsReachable(vncUrl);

        sessionInfo.setVncContainerName(vncContainerName);
        sessionInfo.setVncUrl(vncUrl);

        ResponseEntity<String> responseEntity = new ResponseEntity<>(vncUrl,
                HttpStatus.OK);
        return responseEntity;
    }

    class SessionInfo {
        private String hubUrl;
        private String hubContainerName;
        private String vncUrl;
        private String vncContainerName;

        public String getHubUrl() {
            return hubUrl;
        }

        public void setHubUrl(String hubUrl) {
            this.hubUrl = hubUrl;
        }

        public String getHubContainerName() {
            return hubContainerName;
        }

        public void setHubContainerName(String hubContainerName) {
            this.hubContainerName = hubContainerName;
        }

        public String getVncUrl() {
            return vncUrl;
        }

        public void setVncUrl(String vncUrl) {
            this.vncUrl = vncUrl;
        }

        public String getVncContainerName() {
            return vncContainerName;
        }

        public void setVncContainerName(String vncContainerName) {
            this.vncContainerName = vncContainerName;
        }

        @Override
        public String toString() {
            return "SessionInfo [hubUrl=" + hubUrl + ", hubContainerName="
                    + hubContainerName + ", vncUrl=" + vncUrl
                    + ", vncContainerName=" + vncContainerName + "]";
        }

    }

}