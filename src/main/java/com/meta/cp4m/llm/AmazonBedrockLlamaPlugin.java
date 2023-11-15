/*
 *
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.cp4m.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.meta.cp4m.message.Message;
import com.meta.cp4m.message.ThreadState;
import java.io.IOException;
import java.time.Instant;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

public class AmazonBedrockLlamaPlugin<T extends Message> implements LLMPlugin<T> {
    private static final Logger LOGGER = LoggerFactory.getLogger(AmazonBedrockLlamaPlugin.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final AmazonBedrockLlamaConfig config;
    private final HuggingFaceLlamaPrompt<T> promptCreator;
    private final BedrockRuntimeClient bedrockRuntimeClient;

    public AmazonBedrockLlamaPlugin(AmazonBedrockLlamaConfig config) {
        this.config = config;
        this.promptCreator = new HuggingFaceLlamaPrompt<>(config.systemMessage(), config.maxInputTokens());
        this.bedrockRuntimeClient = BedrockRuntimeClient.builder()
                .region(config.region())
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @Override
    public T handle(ThreadState<T> threadState) throws IOException {
        ObjectNode body = MAPPER.createObjectNode();

        config.topP().ifPresent(v -> body.put("top_p", v));
        config.temperature().ifPresent(v -> body.put("temperature", v));
        config.maxOutputTokens().ifPresent(v -> body.put("max_gen_len", v));

        Optional<String> prompt = promptCreator.createPrompt(threadState);

        if (prompt.isEmpty()) {
            return threadState.newMessageFromBot(Instant.now(), "I'm sorry but that request was too long for me.");
        }

        body.put("prompt", prompt.get());

        SdkBytes bodySdkBytes;
        try {
            String bodyString = MAPPER.writeValueAsString(body);
            bodySdkBytes = SdkBytes.fromUtf8String(bodyString);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e); // this should be impossible
        }

        InvokeModelRequest request = InvokeModelRequest.builder()
                .modelId(config.model())
                .body(bodySdkBytes)
                .build();

        Instant timestamp = Instant.now();
        String llmResponse;
        try {
        InvokeModelResponse response = bedrockRuntimeClient.invokeModel(request);

        JsonNode responseBody = MAPPER.readTree(response.body().asUtf8String());
        String allGeneratedText = responseBody.get("generation").textValue();
        llmResponse = allGeneratedText.strip().replace(prompt.get().strip(), "");
        LOGGER.info("Response from Llama: " + llmResponse);
        } catch (Exception e) {
            llmResponse = "Sorry, I had an issue generating a response to your message.";
        }

        return threadState.newMessageFromBot(timestamp, llmResponse);
    }
}
