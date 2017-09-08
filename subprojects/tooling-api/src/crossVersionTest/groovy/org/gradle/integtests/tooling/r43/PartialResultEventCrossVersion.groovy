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

package org.gradle.integtests.tooling.r43

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.events.PartialResultEvent
import org.gradle.tooling.events.PartialResultListener

class PartialResultEventCrossVersion  extends ToolingApiSpecification {
    def setup() {
        buildFile << """
            import org.gradle.api.DefaultTask;
            import org.gradle.api.events.CustomEventListener;
            import org.gradle.api.events.CustomEventListenerBroadcaster;
            import org.gradle.api.tasks.TaskAction;
            import org.gradle.internal.event.ListenerBroadcast;
            import org.gradle.internal.event.ListenerManager;
            
            import javax.inject.Inject;

            public interface CustomModel {
                String getValue();
            }

            class DefaultCustomModel implements CustomModel, Serializable {
                private final String value;
                DefaultCustomModel(String value) {
                    this.value = value;
                }
                String getValue() {
                    return value;
                }
            }

            task custom(type: CustomTask) {
            }
            
            class CustomTask extends DefaultTask {
                @Inject
                protected CustomEventListenerBroadcaster getCustomEventListenerBroadcaster() {
                    throw new UnsupportedOperationException();
                }

                @TaskAction
                void runTask() {
                    CustomEventListenerBroadcaster listenerBroadcaster = getCustomEventListenerBroadcaster();
                    listenerBroadcaster.newResult('${CustomModel.name}', new DefaultCustomModel("myValue"));
                }
            }
        """
    }

    @ToolingApiVersion('>=4.2')
    @TargetGradleVersion('>=4.2')
    def "can send events from tasks"() {
        CustomModel partialResult

        when:
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().
                    forTasks('custom').addPartialResultListener(new PartialResultListener<CustomModel>() {
                    @Override
                    Class<CustomModel> getListenerType() {
                        return CustomModel.class
                    }

                    @Override
                    void onResultReceived(PartialResultEvent<CustomModel> event) {
                        partialResult = event.getResult()
                    }
                }).run()
        }

        then:
        assert partialResult != null
        assert partialResult.getValue() == "myValue"
    }
}
