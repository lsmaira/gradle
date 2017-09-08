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

package org.gradle.api.events;

/**
 * Broadcaster to all {@link CustomEventListener}s registered.
 *
 * <p>An instance of this broadcaster can be injected in task classes in order to get broadcaster and send events.
 *
 * @since 4.3
 */
public interface CustomEventListenerBroadcaster {

    /**
     * Send the result of the given type to the listeners.
     *
     * <p>Listeners registered for listening the same type of results (same class complete name) will be notified.
     *
     * @param resultType The type of the result being sent.
     * @param result The result, implementing the given type.
     */
    void newResult(String resultType, Object result);
}
