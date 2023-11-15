/*
 *
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.cp4m.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class AmazonBedrockLlamaConfigTest {
    @Test
    void onlyValidRegionsAreAllowed() throws JsonProcessingException {
        assertThatThrownBy(() -> AmazonBedrockLlamaConfig.builder().region("foo-1").build())
                .isInstanceOf(IllegalArgumentException.class);
    }
}
