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

package org.apache.unomi.api.services;

import org.apache.unomi.api.*;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.query.Query;

import java.net.URL;
import java.util.*;

/**
 * A service to access and operate on {@link Account}s
 */
public interface AccountService {

    /**
     * Retrieves the number of unique accounts.
     * @return the number of unique accounts
     */
    long getAllAccountsCount();

    /**
     * Retrieves accounts or matching the specified query.
     *
     * @param <T>   the specific sub-type of {@link Account} to retrieve
     * @param query a {@link Query} specifying which elements to retrieve
     * @param clazz the class of elements to retrieve
     * @return a {@link PartialList} of {@code T} instances matching the specified query
     */
    <T extends Account> PartialList<T> search(Query query, Class<T> clazz);

    /**
     * Retrieves profile matching the specified query.
     *
     * @param query a {@link Query} specifying which elements to retrieve
     * @return a {@link PartialList} of sessions matching the specified query
     */
    PartialList<Profile> searchProfiles(Query query);

    /**
     * Creates a String containing comma-separated values (CSV) formatted version of accounts matching the specified query.
     *
     * @param query the query specifying which accounts to export
     * @return a CSV-formatted String version of the accounts matching the specified query
     */
    String exportAccountsPropertiesToCsv(Query query);

    /**
     * Find accounts which have the specified property with the specified value, ordered according to the specified {@code sortBy} String and paged: only
     * {@code size} of them are retrieved, starting with the {@code offset}-th one.
     *
     * TODO: replace with version using a query instead of separate parameters
     * TODO: remove as it's unused?
     *
     * @param propertyName  the name of the property we're interested in
     * @param propertyValue the value of the property we want accounts to have
     * @param offset        zero or a positive integer specifying the position of the first accounts in the total ordered collection of matching profiles
     * @param size          a positive integer specifying how many matching accounts should be retrieved or {@code -1} if all of them should be retrieved
     * @param sortBy        an optional ({@code null} if no sorting is required) String of comma ({@code ,}) separated property names on which ordering should be performed, ordering elements according to  the property order in
     *                      the String, considering each in turn and moving on to the next one in case of equality of all preceding ones. Each property name is optionally
     *                      followed by a column ({@code :}) and an order specifier: {@code asc} or {@code desc}.
     * @return a {@link PartialList} of matching accounts
     */
    PartialList<Account> findAccountsByPropertyValue(String propertyName, String propertyValue, int offset, int size, String sortBy);

    /**
     * Merges the specified accounts into the provided so-called master account, merging properties according to the {@link PropertyMergeStrategyType} specified on their {@link
     * PropertyType}.
     *
     * @param masterAccount   the account into which the specified accounts will be merged
     * @param accountsToMerge the list of accounts to merge into the specified master account
     * @return the merged profile
     */
    Account mergeAccounts(Account masterAccount, List<Account> accountsToMerge);

    /**
     * Retrieves the account identified by the specified identifier.
     *
     * @param accountid the identifier of the account to retrieve
     * @return the profile identified by the specified identifier or {@code null} if no such account exists
     */
    Account load(String accountid);

    /**
     * Saves the specified account in the context server.
     *
     * @param account the account to be saved
     * @return the newly saved account
     */
    Account save(Account account);

    /**
     * Merge the specified account properties in an existing account,or save new account it does not exist yet
     *
     * @param account the account to be saved
     * @return the newly saved or merged account or null if the save or merge operation failed.
     */
    Account saveOrMerge(Account account);

    /**
     * Removes the account identified by the specified identifier.
     *
     * @param accountId the identifier of the account to delete
     */
    void delete(String accountId);

    /**
     * Removes the profile on the account identified by the specified identifiers.
     *
     * @param accountId the identifier of the account to delete
     * @param profileId the identifier of the profile to delete
     */
    void delete(String accountId, String profileId);

    /**
     * Retrieves the profiles associated with the account identified by the specified identifier that match the specified query (if specified), ordered according to the specified
     * {@code sortBy} String and and paged: only {@code size} of them are retrieved, starting with the {@code offset}-th one.
     *
     * TODO: use a Query object instead of distinct parameter
     *
     * @param accountId the identifier of the account we want to retrieve profiles from
     * @param query     a String of text used for fulltext filtering which sessions we are interested in or {@code null} (or an empty String) if we want to retrieve all profiles
     * @param offset    zero or a positive integer specifying the position of the first profile in the total ordered collection of matching profiles
     * @param size      a positive integer specifying how many matching profiles should be retrieved or {@code -1} if all of them should be retrieved
     * @param sortBy    an optional ({@code null} if no sorting is required) String of comma ({@code ,}) separated property names on which ordering should be performed, ordering elements according to the property order in the
     *                  String, considering each in turn and moving on to the next one in case of equality of all preceding ones. Each property name is optionally followed by
     *                  a column ({@code :}) and an order specifier: {@code asc} or {@code desc}.
     * @return a {@link PartialList} of matching sessions
     */
    PartialList<Profile> getAccountProfiles(String accountId, String query, int offset, int size, String sortBy);

    /**
     * Retrieves the profile in an account identified by the specified identifiers.
     *
     * @param accountId the identifier of the account to be retrieved
     * @param profileId the identifier of the profile to be retrieved
     * @return the profile identified by the specified identifier
     */
    Profile loadProfileFromAccount(String accountId, String profileId);

    /**
     * Saves the specified profile.
     *
     * @param accountId the account to save the profile to
     * @param profile the profile to be saved
     * @return the newly saved profile
     */
    void saveProfileToAccount(String accountId, Profile profile);

    /**
     * Retrieves profiles associated with the account identified by the specified identifier.
     *
     * @param accountId the account id for which we want to retrieve the profiles
     * @return a {@link PartialList} of the account's profiles
     */
    List<Profile> findProfilesInAccount(String accountId);

    /**
     * Checks whether the specified account and/or profile satisfy the specified condition.
     *
     * @param condition the condition we're testing against which might or might not have account- or profile-specific sub-conditions
     * @param account   the account we're testing
     * @param profile   the profile we're testing
     * @return {@code true} if the account and/or profile match the specified condition, {@code false} otherwise
     */
    boolean matchCondition(Condition condition, Account account, Profile profile);

    /**
     * Update all accounts in batch according to the specified {@link BatchUpdate}
     *
     * @param update the batch update specification
     */
    void batchAccountsUpdate(BatchUpdate update);

    /**
     * Retrieves all the property types associated with the specified target.
     *
     * TODO: move to a different class
     *
     * @param target the target for which we want to retrieve the associated property types
     * @return a collection of all the property types associated with the specified target
     */
    Collection<PropertyType> getTargetPropertyTypes(String target);

    /**
     * Retrieves all known property types.
     *
     * TODO: move to a different class
     * TODO: use Map instead of HashMap
     *
     * @return a Map associating targets as keys to related {@link PropertyType}s
     */
    Map<String, Collection<PropertyType>> getTargetPropertyTypes();

    /**
     * Retrieves all property types with the specified tag
     *
     * TODO: move to a different class
     *
     * @param tag   the tag name marking property types we want to retrieve
     * @return a Set of the property types with the specified tag
     */
    Set<PropertyType> getPropertyTypeByTag(String tag);

    /**
     * Retrieves all property types with the specified system tag
     *
     * TODO: move to a different class
     *
     * @param tag   the system tag name marking property types we want to retrieve
     * @return a Set of the property types with the specified system tag
     */
    Set<PropertyType> getPropertyTypeBySystemTag(String tag);

    /**
     * TODO
     * @param fromPropertyTypeId fromPropertyTypeId
     * @return property type mapping
     */
    String getPropertyTypeMapping(String fromPropertyTypeId);

    /**
     * TODO
     * @param propertyName the property name
     * @return list of property types
     */
    Collection<PropertyType> getPropertyTypeByMapping(String propertyName);

    /**
     * Retrieves the property type identified by the specified identifier.
     *
     * TODO: move to a different class
     *
     * @param id the identifier of the property type to retrieve
     * @return the property type identified by the specified identifier or {@code null} if no such property type exists
     */
    PropertyType getPropertyType(String id);

    /**
     * Persists the specified property type in the context server.
     *
     * TODO: move to a different class
     *
     * @param property the property type to persist
     * @return {@code true} if the property type was properly created, {@code false} otherwise (for example, if the property type already existed
     */
    boolean setPropertyType(PropertyType property);

    /**
     * This function will try to set the target on the property type if not set already, based on the file URL
     *
     * @param predefinedPropertyTypeURL
     * @param propertyType
     */
    void setPropertyTypeTarget(URL predefinedPropertyTypeURL, PropertyType propertyType);

    /**
     * Deletes the property type identified by the specified identifier.
     *
     * TODO: move to a different class
     *
     * @param propertyId the identifier of the property type to delete
     * @return {@code true} if the property type was properly deleted, {@code false} otherwise
     */
    boolean deletePropertyType(String propertyId);

    /**
     * Retrieves the existing property types for the specified type as defined by the Item subclass public field {@code ITEM_TYPE} and with the specified tag.
     *
     * TODO: move to a different class
     *
     * @param tag      the tag we're interested in
     * @param itemType the String representation of the item type we want to retrieve the count of, as defined by its class' {@code ITEM_TYPE} field
     * @return all property types defined for the specified item type and with the specified tag
     */
    Set<PropertyType> getExistingProperties(String tag, String itemType);

    /**
     * Retrieves the existing property types for the specified type as defined by the Item subclass public
     * field {@code ITEM_TYPE} and with the specified tag (system or regular)
     *
     * TODO: move to a different class
     *
     * @param tag      the tag we're interested in
     * @param itemType the String representation of the item type we want to retrieve the count of, as defined by its class' {@code ITEM_TYPE} field
     * @param systemTag whether the specified is a system tag or a regular one
     * @return all property types defined for the specified item type and with the specified tag
     */
    Set<PropertyType> getExistingProperties(String tag, String itemType, boolean systemTag);

    /**
     * Forces a refresh of the account service, to load data from persistence immediately instead of waiting for
     * scheduled tasks to execute. Warning : this may have serious impacts on performance so it should only be used
     * in specific scenarios such as integration tests.
     */
    void refresh();
}
