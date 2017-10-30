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
import org.gradle.tooling.internal.protocol.events.InternalPartialResultEvent;

class ClientForwardingCustomEventListener implements CustomEventListener {
    private final BuildEventConsumer eventConsumer;

    ClientForwardingCustomEventListener(BuildEventConsumer eventConsumer) {
        this.eventConsumer = eventConsumer;
    }

    @Override
    public void newResult(final String resultType, final Object result) {
        eventConsumer.dispatch(new InternalPartialResultEvent() {
            @Override
            public boolean isOfType(String type) {
                return resultType.equals(type);
            }

            @Override
            public Object getResult() {
                return result;
            }
        });
    }
}
