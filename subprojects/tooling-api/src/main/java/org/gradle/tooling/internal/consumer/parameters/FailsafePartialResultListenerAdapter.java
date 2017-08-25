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

package org.gradle.tooling.internal.consumer.parameters;

import org.gradle.internal.event.ListenerNotificationException;
import org.gradle.tooling.internal.protocol.InternalPartialResultListener;
import org.gradle.tooling.internal.protocol.events.InternalPartialResultEvent;

import java.util.Collections;

public class FailsafePartialResultListenerAdapter implements InternalPartialResultListener {
    private final InternalPartialResultListener delegate;
    private Throwable listenerFailure;

    public FailsafePartialResultListenerAdapter(InternalPartialResultListener delegate) {
        this.delegate = delegate;
    }

    @Override
    public void onEvent(InternalPartialResultEvent event) {
        if (listenerFailure != null) {
            // Discard event
            return;
        }
        try {
            delegate.onEvent(event);
        } catch (Throwable t) {
            listenerFailure = t;
        }
    }

    public void rethrowErrors() {
        if (listenerFailure != null) {
            throw new ListenerNotificationException(null, "One or more partial result listeners failed with an exception.", Collections.singletonList(listenerFailure));
        }
    }
}
