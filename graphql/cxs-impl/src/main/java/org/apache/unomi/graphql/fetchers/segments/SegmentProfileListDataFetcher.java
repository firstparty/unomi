/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.unomi.graphql.fetchers.segments;

import graphql.schema.DataFetchingEnvironment;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class SegmentProfileListDataFetcher extends BaseSegmentContainsDataFetcher<List<String>> {

    @Override
    @SuppressWarnings("unchecked")
    public List<String> get(DataFetchingEnvironment environment) throws Exception {
        return getSubConditions(environment).stream()
                .filter(condition -> "profileUserListCondition".equals(condition.getConditionTypeId()))
                .flatMap(condition -> ((ArrayList<String>) condition.getParameter("lists")).stream())
                .collect(Collectors.toList());
    }

}
