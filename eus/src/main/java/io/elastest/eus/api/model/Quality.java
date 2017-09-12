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
package io.elastest.eus.api.model;

import io.swagger.annotations.ApiModelProperty;

/**
 * Quality.
 *
 * @author Boni Garcia (boni.garcia@urjc.es)
 * @since 0.0.1
 */
public class Quality {
    private String senderSessionId = null;
    private String senderElementId = null;

    public enum AlgorithmEnum {
        PESQ("pesq"),

        SSIM("ssim"),

        PSNR("psnr");

        private String value;

        AlgorithmEnum(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return String.valueOf(value);
        }
    }

    private AlgorithmEnum algorithm = null;

    private Integer sampleRate = 1000;

    public Quality senderSessionId(String senderSessionId) {
        this.senderSessionId = senderSessionId;
        return this;
    }

    @ApiModelProperty(value = "Session identifier of the WebRTC media peer producer")
    public String getSenderSessionId() {
        return senderSessionId;
    }

    public void setSenderSessionId(String senderSessionId) {
        this.senderSessionId = senderSessionId;
    }

    public Quality senderElementId(String senderElementId) {
        this.senderElementId = senderElementId;
        return this;
    }

    @ApiModelProperty(value = "Element identifier of the video tag producer")
    public String getSenderElementId() {
        return senderElementId;
    }

    public void setSenderElementId(String senderElementId) {
        this.senderElementId = senderElementId;
    }

    public Quality algorithm(AlgorithmEnum algorithm) {
        this.algorithm = algorithm;
        return this;
    }

    @ApiModelProperty(value = "QoE algorithm (full-reference) to be used")
    public AlgorithmEnum getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(AlgorithmEnum algorithm) {
        this.algorithm = algorithm;
    }

    public Quality sampleRate(Integer sampleRate) {
        this.sampleRate = sampleRate;
        return this;
    }

    @ApiModelProperty(value = "Sample rate for the quality evaluation (in milliseconds)")
    public Integer getSampleRate() {
        return sampleRate;
    }

    public void setSampleRate(Integer sampleRate) {
        this.sampleRate = sampleRate;
    }

}
