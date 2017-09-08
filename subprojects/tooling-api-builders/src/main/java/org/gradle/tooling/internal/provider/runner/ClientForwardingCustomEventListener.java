/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.tooling.internal.provider.runner;

import org.gradle.initialization.BuildEventConsumer;
import org.gradle.api.events.CustomEventListener;
import org.gradle.tooling.internal.provider.events.DefaultPartialResultEvent;
import org.gradle.tooling.internal.provider.serialization.PayloadSerializer;
import org.gradle.tooling.internal.provider.serialization.SerializedPayload;

class ClientForwardingCustomEventListener implements CustomEventListener {
    private final BuildEventConsumer eventConsumer;
    private final PayloadSerializer serializer;

    ClientForwardingCustomEventListener(BuildEventConsumer eventConsumer, PayloadSerializer serializer) {
        this.eventConsumer = eventConsumer;
        this.serializer = serializer;
    }

    @Override
    public void newResult(final String type, final Object result) {
        SerializedPayload serializedPayload = serializer.serialize(result);
        eventConsumer.dispatch(new DefaultPartialResultEvent(type, serializedPayload));
    }
}
