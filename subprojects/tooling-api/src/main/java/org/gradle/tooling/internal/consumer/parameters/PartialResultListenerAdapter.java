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

import org.gradle.internal.event.ListenerBroadcast;
import org.gradle.tooling.events.PartialResultEvent;
import org.gradle.tooling.events.PartialResultListener;
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter;
import org.gradle.tooling.internal.adapter.ViewBuilder;
import org.gradle.tooling.internal.protocol.InternalPartialResultListener;
import org.gradle.tooling.internal.protocol.events.InternalPartialResultEvent;

import java.util.List;

public class PartialResultListenerAdapter implements InternalPartialResultListener {

    private final List<PartialResultListener> listeners;

    PartialResultListenerAdapter(List<PartialResultListener> listeners) {
        this.listeners = listeners;
    }

    @Override
    public void onEvent(InternalPartialResultEvent event) {
        doBroadcast(event);
    }

    private void doBroadcast(InternalPartialResultEvent event) {
        ListenerBroadcast<WrapperListener> listenerBroadcast = new ListenerBroadcast<WrapperListener>(WrapperListener.class);
        ProtocolToModelAdapter adapter = new ProtocolToModelAdapter();

        for (PartialResultListener listener : listeners) {
            if (event.isOfType(listener.getListenerType().getName())) {
                listenerBroadcast.add(adaptAndCreateListener(adapter, listener, event));
            }
        }

        try {
            listenerBroadcast.getSource().broadcastEvent();
        } finally {
            listenerBroadcast.removeAll();
        }
    }

    private <T> WrapperListener adaptAndCreateListener(ProtocolToModelAdapter adapter, final PartialResultListener<T> listener, InternalPartialResultEvent event) {
        ViewBuilder<T> viewBuilder = adapter.builder(listener.getListenerType());
        final T result = viewBuilder.build(event.getResult());
        final PartialResultEvent<T> clientEvent = new PartialResultEvent<T>() {
            @Override
            public T getResult() {
                return result;
            }
        };
        return new WrapperListener() {
            @Override
            public void broadcastEvent() {
                listener.onResultReceived(clientEvent);
            }
        };
    }

    private interface WrapperListener {
        void broadcastEvent();
    }
}
