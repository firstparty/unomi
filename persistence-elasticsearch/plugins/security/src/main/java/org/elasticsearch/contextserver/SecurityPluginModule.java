package org.elasticsearch.contextserver;

/*
 * #%L
 * context-server-persistence-elasticsearch-plugins-security
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2014 - 2015 Jahia Solutions
 * %%
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
 * #L%
 */

import org.elasticsearch.common.inject.AbstractModule;
import org.elasticsearch.common.settings.Settings;

public class SecurityPluginModule extends AbstractModule {

    private final Settings settings;

    public SecurityPluginModule(Settings settings) {
        this.settings = settings;
    }

    @SuppressWarnings({"unchecked"})
    @Override
    protected void configure() {
        bind(SecurityPluginService.class).asEagerSingleton();
    }
}
