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
 * Interface for custom events with partial results.
 *
 * <p> Events can be triggered by any build operation.
 *
 * @param <T> The type of result this event is carrying.
 * @since 4.3
 */
@Incubating
public interface PartialResultEvent<T> {

    /**
     * Returns a proxy view to the result carried by this event.
     *
     * @return The result.
     */
    T getResult();
}
