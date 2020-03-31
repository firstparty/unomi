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

package org.apache.unomi.api;

import org.apache.commons.lang3.RandomStringUtils;

import java.util.*;

/**
 * An account gathering all known information about an organization, which can include multiple profiles
 */
public class Account extends Item {

    /**
     * The Account ITEM_TYPE
     */
    public static final String ITEM_TYPE = "account";
    private Map<String, Object> properties = new HashMap<>();

    private Map<String, Object> systemProperties = new HashMap<>();

    private Set<String> profileIds = new HashSet<>();
    private String accountId;

    /**
     * Instantiates a new Account
     */
    public Account() {
        this.accountId =  RandomStringUtils.random(10, true, true);
    }

    /**
     * Instantiates a new Account with the specified identifier
     * @param accountId the account identifier
     */
    public Account(String accountId) {
        this.accountId = accountId;
    }

    public String getAccountId() {
        return this.accountId;
    }

    /**
     * Specifies the system property name - value pairs for this account.
     *
     * @param systemProperties a Map of system property name - value pairs for this account
     */
    public void setSystemProperties(Map<String, Object> systemProperties) {
        this.systemProperties = systemProperties;
    }

    /**
     * Sets a system property, overwriting an existing one if it existed. This call will also created the system
     * properties hash map if it didn't exist.
     * @param key the key for the system property hash map
     * @param value the value for the system property hash map
     * @return the previous value object if it existing.
     */
    public Object setSystemProperty(String key, Object value) {
        if (this.systemProperties == null) {
            this.systemProperties = new LinkedHashMap<>();
        }
        return this.systemProperties.put(key, value);
    }

    /**
     * Retrieves a Map of system property name - value pairs for this account. System properties can be used by implementations to store non-user visible properties needed for
     * internal purposes.
     *
     * @return a Map of system property name - value pairs for this account
     */
    public Map<String, Object> getSystemProperties() {
        return systemProperties;
    }

    /**
     * Sets the property identified by the specified name to the specified value. If a property with that name already exists, replaces its value, otherwise adds the new
     * property with the specified name and value.
     *
     * @param name  the name of the property to set
     * @param value the value of the property
     */
    public void setProperty(String name, Object value) {
        this.properties.put(name, value);
    }

    /**
     * Retrieves the property identified by the specified name.
     *
     * @param name the name of the property to retrieve
     * @return the value of the specified property or {@code null} if no such property exists
     */
    public Object getProperty(String name) {
        return properties.get(name);
    }

    /**
     * Retrieves a Map of all property name - value pairs for this account.
     *
     * @return a Map of all property name - value pairs for this account
     */
    public Map<String, Object> getProperties() {
        return properties;
    }

    /**
     * Sets the property name - value pairs for this account.
     *
     * @param properties a Map containing the property name - value pairs for this account
     */
    public void setProperties(Map<String, Object> properties) {
        this.properties = properties;
    }

    /**
     * Assugns a profile ID that belongs to the account
     * @param profileId a string representing the profile identifier
     */
    public void addProfileId(String profileId) {
        this.profileIds.add(profileId);
    }

    /**
     * Removes a profile ID from the account
     * @param profileId a string representing the profile identifier
     */
    public void removeProfileId(String profileId) {
        this.profileIds.remove(profileId);
    }

    /**
     * Return a list of the associated profile IDs from the account
     * @return a List of ids
     */
    public List<String> getProfileIds() {
        return new ArrayList<>(this.profileIds);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("Account{");
        sb.append("properties=").append(properties);
        sb.append(", systemProperties=").append(systemProperties);
        sb.append(", itemId='").append(itemId).append('\'');
        sb.append(", profileIds='").append(profileIds).append('\'');
        sb.append(", itemType='").append(itemType).append('\'');
        sb.append(", scope='").append(scope).append('\'');
        sb.append(", version=").append(version);
        sb.append('}');
        return sb.toString();
    }

}
