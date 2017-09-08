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

package org.gradle.internal.progress;

import org.gradle.api.events.CustomEventListener;
import org.gradle.api.events.CustomEventListenerBroadcaster;
import org.gradle.internal.event.ListenerManager;

public class DefaultCustomEventListenerManager implements CustomEventListenerManager, CustomEventListenerBroadcaster {

    private final ListenerManager listenerManager;

    public DefaultCustomEventListenerManager(ListenerManager listenerManager) {
        this.listenerManager = listenerManager;
    }

    @Override
    public void addListener(CustomEventListener listener) {
        listenerManager.addListener(listener);
    }

    @Override
    public void removeListener(CustomEventListener listener) {
        listenerManager.removeListener(listener);
    }

    @Override
    public void newResult(String resultType, Object result) {
        listenerManager.getBroadcaster(CustomEventListener.class).newResult(resultType, result);
    }
}
