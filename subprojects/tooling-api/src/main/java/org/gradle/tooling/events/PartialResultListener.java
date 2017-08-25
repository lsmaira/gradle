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

package org.gradle.tooling.events;

import org.gradle.api.Incubating;

/**
 * A listener which is notified when partial results of the given type are triggered by any build operation.
 *
 * @see org.gradle.tooling.LongRunningOperation#addPartialResultListener(PartialResultListener)
 * @param <T> The type of result to be received by this listener.
 * @since 4.3
 */
@Incubating
public interface PartialResultListener<T> {

    /**
     * Returns the type of result expected by this listener.
     *
     * @return The type of result.
     */
    Class<T> getListenerType();

    /**
     * Called when a new result of the given type is available.
     *
     * @param event The event carrying the result.
     */
    void onResultReceived(PartialResultEvent<T> event);
}
