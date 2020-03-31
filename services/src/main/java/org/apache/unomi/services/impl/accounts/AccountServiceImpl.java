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

package org.apache.unomi.services.impl.accounts;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.unomi.api.*;
import org.apache.unomi.api.conditions.Condition;
import org.apache.unomi.api.conditions.ConditionType;
import org.apache.unomi.api.query.Query;
import org.apache.unomi.api.segments.Segment;
import org.apache.unomi.api.services.DefinitionsService;
import org.apache.unomi.api.services.AccountService;
import org.apache.unomi.api.services.SchedulerService;
import org.apache.unomi.api.services.SegmentService;
import org.apache.unomi.persistence.spi.PersistenceService;
import org.apache.unomi.persistence.spi.PropertyHelper;
import org.apache.unomi.services.impl.ParserHelper;
import org.osgi.framework.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.apache.unomi.persistence.spi.CustomObjectMapper.getObjectMapper;

public class AccountServiceImpl implements AccountService, SynchronousBundleListener {
    /**
     * This class is responsible for storing property types and permits optimized access to them.
     * In order to assure data consistency, thread-safety and performance, this class is immutable and every operation on
     * property types requires creating a new instance (copy-on-write).
     */
    private static class PropertyTypes {
        private List<PropertyType> allPropertyTypes;
        private Map<String, PropertyType> propertyTypesById = new HashMap<>();
        private Map<String, List<PropertyType>> propertyTypesByTags = new HashMap<>();
        private Map<String, List<PropertyType>> propertyTypesBySystemTags = new HashMap<>();
        private Map<String, List<PropertyType>> propertyTypesByTarget = new HashMap<>();

        public PropertyTypes(List<PropertyType> allPropertyTypes) {
            this.allPropertyTypes = new ArrayList<>(allPropertyTypes);
            propertyTypesById = new HashMap<>();
            propertyTypesByTags = new HashMap<>();
            propertyTypesBySystemTags = new HashMap<>();
            propertyTypesByTarget = new HashMap<>();
            for (PropertyType propertyType : allPropertyTypes) {
                propertyTypesById.put(propertyType.getItemId(), propertyType);
                for (String propertyTypeTag : propertyType.getMetadata().getTags()) {
                    updateListMap(propertyTypesByTags, propertyType, propertyTypeTag);
                }
                for (String propertyTypeSystemTag : propertyType.getMetadata().getSystemTags()) {
                    updateListMap(propertyTypesBySystemTags, propertyType, propertyTypeSystemTag);
                }
                updateListMap(propertyTypesByTarget, propertyType, propertyType.getTarget());
            }
        }

        public List<PropertyType> getAll() {
            return allPropertyTypes;
        }

        public PropertyType get(String propertyId) {
            return propertyTypesById.get(propertyId);
        }

        public Map<String, List<PropertyType>> getAllByTarget() {
            return propertyTypesByTarget;
        }

        public List<PropertyType> getByTag(String tag) {
            return propertyTypesByTags.get(tag);
        }

        public List<PropertyType> getBySystemTag(String systemTag) {
            return propertyTypesBySystemTags.get(systemTag);
        }

        public List<PropertyType> getByTarget(String target) {
            return propertyTypesByTarget.get(target);
        }

        public AccountServiceImpl.PropertyTypes with(PropertyType newProperty) {
            return with(Collections.singletonList(newProperty));
        }

        /**
         * Creates a new instance of this class containing given property types.
         * If property types with the same ID existed before, they will be replaced by the new ones.
         *
         * @param newProperties list of property types to change
         * @return new instance
         */
        public AccountServiceImpl.PropertyTypes with(List<PropertyType> newProperties) {
            Map<String, PropertyType> updatedProperties = new HashMap<>();
            for (PropertyType property : newProperties) {
                if (propertyTypesById.containsKey(property.getItemId())) {
                    updatedProperties.put(property.getItemId(), property);
                }
            }

            List<PropertyType> newPropertyTypes = Stream.concat(
                    allPropertyTypes.stream().map(property -> updatedProperties.getOrDefault(property.getItemId(), property)),
                    newProperties.stream().filter(property -> !propertyTypesById.containsKey(property.getItemId()))
            ).collect(Collectors.toList());

            return new AccountServiceImpl.PropertyTypes(newPropertyTypes);
        }

        /**
         * Creates a new instance of this class containing all property types except the one with given ID.
         *
         * @param propertyId ID of the property to delete
         * @return new instance
         */
        public AccountServiceImpl.PropertyTypes without(String propertyId) {
            List<PropertyType> newPropertyTypes = allPropertyTypes.stream()
                    .filter(property -> property.getItemId().equals(propertyId))
                    .collect(Collectors.toList());

            return new AccountServiceImpl.PropertyTypes(newPropertyTypes);
        }

        private void updateListMap(Map<String, List<PropertyType>> listMap, PropertyType propertyType, String key) {
            List<PropertyType> propertyTypes = listMap.get(key);
            if (propertyTypes == null) {
                propertyTypes = new ArrayList<>();
            }
            propertyTypes.add(propertyType);
            listMap.put(key, propertyTypes);
        }

    }

    private static final String DEFAULT_MERGE_STRATEGY = "defaultMergeStrategy";
    private static final Logger logger = LoggerFactory.getLogger(AccountServiceImpl.class.getName());

    private BundleContext bundleContext;

    private PersistenceService persistenceService;

    private DefinitionsService definitionsService;

    private SchedulerService schedulerService;

    private SegmentService segmentService;

    private Condition purgeAccountQuery;
    private Integer purgeAccountsExistTime = 0;
    private Integer purgeAccountInactiveTime = 0;
    private Integer purgeAccountProfilesTime = 0;
    private Integer purgeAccountInterval = 0;
    private long propertiesRefreshInterval = 10000;

    private AccountServiceImpl.PropertyTypes propertyTypes;

    private boolean forceRefreshOnSave = false;

    public AccountServiceImpl() {
        logger.info("Initializing account service...");
    }

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    public void setDefinitionsService(DefinitionsService definitionsService) {
        this.definitionsService = definitionsService;
    }

    public void setSchedulerService(SchedulerService schedulerService) {
        this.schedulerService = schedulerService;
    }

    public void setSegmentService(SegmentService segmentService) {
        this.segmentService = segmentService;
    }

    public void setForceRefreshOnSave(boolean forceRefreshOnSave) {
        this.forceRefreshOnSave = forceRefreshOnSave;
    }

    public void setPropertiesRefreshInterval(long propertiesRefreshInterval) {
        this.propertiesRefreshInterval = propertiesRefreshInterval;
    }

    public void postConstruct() {
        logger.debug("postConstruct {" + bundleContext.getBundle() + "}");

        loadPropertyTypesFromPersistence();
        processBundleStartup(bundleContext);
        for (Bundle bundle : bundleContext.getBundles()) {
            if (bundle.getBundleContext() != null && bundle.getBundleId() != bundleContext.getBundle().getBundleId()) {
                processBundleStartup(bundle.getBundleContext());
            }
        }
        bundleContext.addBundleListener(this);
        initializePurge();
//        schedulePropertyTypeLoad();
        logger.info("Account service initialized.");
    }

    public void preDestroy() {
        bundleContext.removeBundleListener(this);
        logger.info("Account service shutdown.");
    }

    private void processBundleStartup(BundleContext bundleContext) {
        if (bundleContext == null) {
            return;
        }
        loadPredefinedAccounts(bundleContext);
//        loadPredefinedPropertyTypes(bundleContext);
    }

    private void processBundleStop(BundleContext bundleContext) {
    }

    public void setPurgeAccountExistTime(Integer purgeAccountsExistTime) {
        this.purgeAccountsExistTime = purgeAccountsExistTime;
    }

    public void setPurgeAccountInactiveTime(Integer purgeAccountInactiveTime) {
        this.purgeAccountInactiveTime = purgeAccountInactiveTime;
    }

    public void setPurgeAccountProfilesTime(Integer purgeAccountProfilesTime) {
        this.purgeAccountProfilesTime = purgeAccountProfilesTime;
    }

    public void setPurgeAccountInterval(Integer purgeAccountInterval) {
        this.purgeAccountInterval = purgeAccountInterval;
    }

    public void reloadPropertyTypes(boolean refresh) {
        try {
            if (refresh) {
                persistenceService.refresh();
            }
            loadPropertyTypesFromPersistence();
        } catch (Throwable t) {
            logger.error("Error loading property types from persistence back-end", t);
        }
    }

    private void loadPropertyTypesFromPersistence() {
        try {
            this.propertyTypes = new AccountServiceImpl.PropertyTypes(persistenceService.getAllItems(PropertyType.class, 0, -1, "rank").getList());
        } catch (Exception e) {
            logger.error("Error loading property types from persistence service", e);
        }
    }

    private void initializePurge() {
        logger.info("Account purge: Initializing");

        if (purgeAccountInactiveTime > 0 || purgeAccountsExistTime > 0 || purgeAccountProfilesTime > 0) {
            if (purgeAccountInactiveTime > 0) {
                logger.info("Account purge: Account with no visits since {} days, will be purged", purgeAccountInactiveTime);
            }
            if (purgeAccountsExistTime > 0) {
                logger.info("Account purge: Account created since {} days, will be purged", purgeAccountsExistTime);
            }

            TimerTask task = new TimerTask() {
                @Override
                public void run() {
                    try {
                        long purgeStartTime = System.currentTimeMillis();
                        logger.debug("Account purge: Purge triggered");

                        if (purgeAccountQuery == null) {
                            ConditionType accountPropertyConditionType = definitionsService.getConditionType("accountPropertyCondition");
                            ConditionType booleanCondition = definitionsService.getConditionType("booleanCondition");
                            if (accountPropertyConditionType == null || booleanCondition == null) {
                                // definition service not yet fully instantiate
                                return;
                            }

                            purgeAccountQuery = new Condition(booleanCondition);
                            purgeAccountQuery.setParameter("operator", "or");
                            List<Condition> subConditions = new ArrayList<>();

                            if (purgeAccountInactiveTime > 0) {
                                Condition inactiveTimeCondition = new Condition(accountPropertyConditionType);
                                inactiveTimeCondition.setParameter("propertyName", "lastVisit");
                                inactiveTimeCondition.setParameter("comparisonOperator", "lessThanOrEqualTo");
                                inactiveTimeCondition.setParameter("propertyValueDateExpr", "now-" + purgeAccountInactiveTime + "d");
                                subConditions.add(inactiveTimeCondition);
                            }

                            if (purgeAccountsExistTime > 0) {
                                Condition existTimeCondition = new Condition(accountPropertyConditionType);
                                existTimeCondition.setParameter("propertyName", "firstVisit");
                                existTimeCondition.setParameter("comparisonOperator", "lessThanOrEqualTo");
                                existTimeCondition.setParameter("propertyValueDateExpr", "now-" + purgeAccountsExistTime + "d");
                                subConditions.add(existTimeCondition);
                            }

                            purgeAccountQuery.setParameter("subConditions", subConditions);
                        }

                        persistenceService.removeByQuery(purgeAccountQuery, Account.class);

                        if (purgeAccountProfilesTime > 0) {
                            persistenceService.purge(getMonth(-purgeAccountProfilesTime).getTime());
                        }

                        logger.info("Account purge: purge executed in {} ms", System.currentTimeMillis() - purgeStartTime);
                    } catch (Throwable t) {
                        logger.error("Error while purging accounts", t);
                    }
                }
            };
            schedulerService.getScheduleExecutorService().scheduleAtFixedRate(task, 1, purgeAccountInterval, TimeUnit.DAYS);

            logger.info("Account purge: purge scheduled with an interval of {} days", purgeAccountInterval);
        } else {
            logger.info("Account purge: No purge scheduled");
        }
    }

    private GregorianCalendar getMonth(int offset) {
        GregorianCalendar gc = new GregorianCalendar();
        gc = new GregorianCalendar(gc.get(Calendar.YEAR), gc.get(Calendar.MONTH), 1);
        gc.add(Calendar.MONTH, offset);
        return gc;
    }

    public long getAllAccountsCount() {
        return persistenceService.getAllItemsCount(Account.ITEM_TYPE);
    }

    public <T extends Account> PartialList<T> search(Query query, final Class<T> clazz) {
        return doSearch(query, clazz);
    }

    public PartialList<Profile> searchProfiles(Query query) {
        return doSearch(query, Profile.class);
    }

    private <T extends Item> PartialList<T> doSearch(Query query, Class<T> clazz) {
        if (query.getCondition() != null && definitionsService.resolveConditionType(query.getCondition())) {
            if (StringUtils.isNotBlank(query.getText())) {
                return persistenceService.queryFullText(query.getText(), query.getCondition(), query.getSortby(), clazz, query.getOffset(), query.getLimit());
            } else {
                return persistenceService.query(query.getCondition(), query.getSortby(), clazz, query.getOffset(), query.getLimit());
            }
        } else {
            if (StringUtils.isNotBlank(query.getText())) {
                return persistenceService.queryFullText(query.getText(), query.getSortby(), clazz, query.getOffset(), query.getLimit());
            } else {
                return persistenceService.getAllItems(clazz, query.getOffset(), query.getLimit(), query.getSortby());
            }
        }
    }

    @Override
    public boolean setPropertyType(PropertyType property) {
        PropertyType previousProperty = persistenceService.load(property.getItemId(), PropertyType.class);
        boolean result = false;
        if (previousProperty == null) {
            result = persistenceService.save(property);
            propertyTypes = propertyTypes.with(property);
        } else if (merge(previousProperty, property)) {
            result = persistenceService.save(previousProperty);
            propertyTypes = propertyTypes.with(previousProperty);
        }
        return result;
    }

    @Override
    public boolean deletePropertyType(String propertyId) {
        boolean result = persistenceService.remove(propertyId, PropertyType.class);
        propertyTypes = propertyTypes.without(propertyId);
        return result;
    }

    @Override
    public Set<PropertyType> getExistingProperties(String tag, String itemType) {
        return getExistingProperties(tag, itemType, false);
    }

    @Override
    public Set<PropertyType> getExistingProperties(String tag, String itemType, boolean systemTag) {
        Set<PropertyType> filteredProperties = new LinkedHashSet<PropertyType>();
        // TODO: here we limit the result to the definition we have, but what if some properties haven't definition but exist in ES mapping ?
        Set<PropertyType> profileProperties = systemTag ? getPropertyTypeBySystemTag(tag) : getPropertyTypeByTag(tag);
        Map<String, Map<String, Object>> itemMapping = persistenceService.getPropertiesMapping(itemType);

        if (itemMapping == null || itemMapping.isEmpty() || itemMapping.get("properties") == null || itemMapping.get("properties").get("properties") == null) {
            return filteredProperties;
        }

        Map<String, Map<String, String>> propMapping = (Map<String, Map<String, String>>) itemMapping.get("properties").get("properties");
        for (PropertyType propertyType : profileProperties) {
            if (propMapping.containsKey(propertyType.getMetadata().getId())) {
                filteredProperties.add(propertyType);
            }
        }
        return filteredProperties;
    }

    public String exportAccountsPropertiesToCsv(Query query) {
        StringBuilder sb = new StringBuilder();
        Set<PropertyType> propertyTypes = getExistingProperties("accountProperties", Account.ITEM_TYPE);
        PartialList<Account> accounts = search(query, Account.class);

        HashMap<String, PropertyType> propertyTypesById = new LinkedHashMap<>();
        for (PropertyType propertyType : propertyTypes) {
            propertyTypesById.put(propertyType.getMetadata().getId(), propertyType);
        }
        for (Account account : accounts.getList()) {
            for (String key : account.getProperties().keySet()) {
                if (!propertyTypesById.containsKey(key)) {
                    propertyTypesById.put(key, null);
                }
            }
        }

        sb.append("accountId;");
        // headers
        for (String propertyId : propertyTypesById.keySet()) {
            sb.append(propertyId);
            sb.append(";");
        }
        sb.append("profiles\n");

        // rows
        for (Account account : accounts.getList()) {
            sb.append(account.getAccountId());
            sb.append(";");
            for (Map.Entry<String, PropertyType> propertyIdAndType : propertyTypesById.entrySet()) {
                String propertyId = propertyIdAndType.getKey();
                if (account.getProperties().get(propertyId) != null) {
                    handleExportProperty(sb, account.getProperties().get(propertyId), propertyIdAndType.getValue());
                } else {
                    sb.append("");
                }
                sb.append(";");
            }
            List<String> profiles = new ArrayList<String>();
            for (String segment : account.getProfileIds()) {
                Segment s = segmentService.getSegmentDefinition(segment);
                profiles.add(csvEncode(s.getMetadata().getName()));
            }
            sb.append(csvEncode(StringUtils.join(profiles, ",")));
            sb.append('\n');
        }
        return sb.toString();
    }

    // Copied from ProfileServiceImp.java
    // TODO create as class methods in a Service parent class
    private <T> boolean merge(T target, T object) {
        if (object != null) {
            try {
                Map<String, Object> objectValues = PropertyUtils.describe(object);
                Map<String, Object> targetValues = PropertyUtils.describe(target);
                if (merge(targetValues, objectValues)) {
                    BeanUtils.populate(target, targetValues);
                    return true;
                }
            } catch (ReflectiveOperationException e) {
                logger.error("Cannot merge properties", e);
            }
        }
        return false;
    }

    private void handleExportProperty(StringBuilder sb, Object propertyValue, PropertyType propertyType) {
        if (propertyValue instanceof Collection && propertyType != null && propertyType.isMultivalued() != null && propertyType.isMultivalued()) {
            Collection propertyValues = (Collection) propertyValue;
            Collection encodedValues = new ArrayList(propertyValues.size());
            for (Object value : propertyValues) {
                encodedValues.add(csvEncode(value.toString()));
            }
            sb.append(csvEncode(StringUtils.join(encodedValues, ",")));
        } else {
            sb.append(csvEncode(propertyValue.toString()));
        }
    }

    private String csvEncode(String input) {
        if (StringUtils.containsAny(input, '\n', '"', ',')) {
            return "\"" + input.replace("\"", "\"\"") + "\"";
        }
        return input;
    }

    public PartialList<Account> findAccountsByPropertyValue(String propertyName, String propertyValue, int offset, int size, String sortBy) {
        return persistenceService.query(propertyName, propertyValue, sortBy, Account.class, offset, size);
    }

    public Account load(String accountId) {
        return persistenceService.load(accountId, Account.class);
    }

    public Account save(Account account) {
        return save(account, forceRefreshOnSave);
    }

    private Account save(Account account, boolean forceRefresh) {
        if (account.getItemId() == null) {
            return null;
        }
        account.setSystemProperty("lastUpdated", new Date());
        if (persistenceService.save(account)) {
            if (forceRefresh) {
                // triggering a load will force an in-place refresh, that may be expensive in performance but will make data immediately available.
                return persistenceService.load(account.getAccountId(), Account.class);
            } else {
                return account;
            }
        }
        return null;
    }

    public Account saveOrMerge(Account account) {
        Account previousAccount = persistenceService.load(account.getAccountId(), Account.class);
        account.setSystemProperty("lastUpdated", new Date());
        if (previousAccount == null) {
            if (persistenceService.save(account)) {
                return account;
            } else {
                return null;
            }
        } else if (merge(previousAccount, account)) {
            if (persistenceService.save(previousAccount)) {
                return previousAccount;
            } else {
                return null;
            }
        }
        return null;
    }

    public void delete(String accountId) {
        persistenceService.remove(accountId, Account.class);
    }

    // TODO test that this works
    public void delete(String accountId, String profileId) {
        Condition mergeCondition = new Condition(definitionsService.getConditionType("accountPropertyCondition"));
        mergeCondition.setParameter("propertyName", "account");
        mergeCondition.setParameter("comparisonOperator", "equals");
        mergeCondition.setParameter("propertyValue", accountId);
        mergeCondition.setParameter("propertyName", "profileId");
        mergeCondition.setParameter("comparisonOperator", "contains");
        mergeCondition.setParameter("propertyValue", profileId);
        persistenceService.removeByQuery(mergeCondition, Profile.class);
    }

    public Account mergeAccounts(Account masterAccount, List<Account> accountsToMerge) {
        List<Account> filteredAccountsToMerge = new ArrayList<>();

        for (Account filteredAccount : accountsToMerge) {
            if (!filteredAccount.getAccountId().equals(masterAccount.getAccountId())) {
                filteredAccountsToMerge.add(filteredAccount);
            }
        }

        if (filteredAccountsToMerge.isEmpty()) {
            return masterAccount;
        }

        accountsToMerge = filteredAccountsToMerge;
        Set<String> allAccountProperties = new LinkedHashSet<>();
        for (Account account : accountsToMerge) {
            allAccountProperties.addAll(account.getProperties().keySet());
        }

        Collection<PropertyType> accountPropertyTypes = getTargetPropertyTypes("accounts");
        Map<String, PropertyType> accountPropertyTypeById = new HashMap<>();
        for (PropertyType propertyType : accountPropertyTypes) {
            accountPropertyTypeById.put(propertyType.getMetadata().getId(), propertyType);
        }
        Set<String> accountIdsToMerge = new TreeSet<>();
        for (Account accountToMerge : accountsToMerge) {
            accountIdsToMerge.add(accountToMerge.getAccountId());
        }
        logger.info("Merging accounts " + accountIdsToMerge + " into account " + masterAccount.getAccountId());

        boolean masterAccountChanged = false;

        for (String accountProperty : allAccountProperties) {
            PropertyType propertyType = accountPropertyTypeById.get(accountProperty);
            String propertyMergeStrategyId = DEFAULT_MERGE_STRATEGY;
            if (propertyType != null) {
                if (propertyType.getMergeStrategy() != null && propertyMergeStrategyId.length() > 0) {
                    propertyMergeStrategyId = propertyType.getMergeStrategy();
                }
            }
            PropertyMergeStrategyType propertyMergeStrategyType = definitionsService.getPropertyMergeStrategyType(propertyMergeStrategyId);
            if (propertyMergeStrategyType == null) {
                if (propertyMergeStrategyId.equals(DEFAULT_MERGE_STRATEGY)) {
                    logger.warn("Couldn't resolve default strategy, ignoring property merge for property " + accountProperty);
                    continue;
                } else {
                    // todo: improper algorithm… it is possible that the defaultMergeStrategy couldn't be resolved here
                    logger.warn("Couldn't resolve strategy " + propertyMergeStrategyId + " for property " + accountProperty + ", using default strategy instead");
                    propertyMergeStrategyId = DEFAULT_MERGE_STRATEGY;
                    propertyMergeStrategyType = definitionsService.getPropertyMergeStrategyType(propertyMergeStrategyId);
                }
            }

            Collection<ServiceReference<PropertyMergeStrategyExecutor>> matchingPropertyMergeStrategyExecutors;
            try {
                matchingPropertyMergeStrategyExecutors = bundleContext.getServiceReferences(PropertyMergeStrategyExecutor.class, propertyMergeStrategyType.getFilter());
                for (ServiceReference<PropertyMergeStrategyExecutor> propertyMergeStrategyExecutorReference : matchingPropertyMergeStrategyExecutors) {
                    PropertyMergeStrategyExecutor propertyMergeStrategyExecutor = bundleContext.getService(propertyMergeStrategyExecutorReference);
//                    masterAccountChanged |= propertyMergeStrategyExecutor.mergeProperty(accountProperty, propertyType, accountsToMerge, masterAccount);
                }
            } catch (InvalidSyntaxException e) {
                logger.error("Error retrieving strategy implementation", e);
            }
        }

        // merge System properties
        for (Account account : accountsToMerge) {
            masterAccountChanged = mergeSystemProperties(masterAccount.getSystemProperties(), account.getSystemProperties()) || masterAccountChanged;
        }

        // we now have to merge the accounts's profiles
        for (Account account : accountsToMerge) {
            if (account.getProfileIds() != null && account.getProfileIds().size() > 0) {
                masterAccount.getProfileIds().addAll(account.getProfileIds());
                // TODO better segments diff calculation
                masterAccountChanged = true;
            }
        }

        if (masterAccountChanged) {
            persistenceService.save(masterAccount);
        }

        return masterAccount;

    }

    // TODO: move this and other similar methods into a parent class to reduce COPY PASTA shit
    private boolean mergeSystemProperties(Map<String, Object> targetProperties, Map<String, Object> sourceProperties) {
        boolean changed = false;
        for (Map.Entry<String, Object> sourceProperty : sourceProperties.entrySet()) {
            if (sourceProperty.getValue() != null) {
                if (!targetProperties.containsKey(sourceProperty.getKey())) {
                    targetProperties.put(sourceProperty.getKey(), sourceProperty.getValue());
                    changed = true;
                } else {
                    Object targetProperty = targetProperties.get(sourceProperty.getKey());

                    if (targetProperty instanceof Map && sourceProperty.getValue() instanceof Map) {
                        // merge Maps like "goals", "campaigns"
                        @SuppressWarnings("unchecked")
                        Map<String, Object> mapSourceProp = (Map<String, Object>) sourceProperty.getValue();
                        @SuppressWarnings("unchecked")
                        Map<String, Object> mapTargetProp = (Map<String, Object>) targetProperty;

                        for (Map.Entry<String, ?> mapSourceEntry : mapSourceProp.entrySet()) {
                            if (!mapTargetProp.containsKey(mapSourceEntry.getKey())) {
                                mapTargetProp.put(mapSourceEntry.getKey(), mapSourceEntry.getValue());
                                changed = true;
                            }
                        }
                    } else if (targetProperty instanceof Collection && sourceProperty.getValue() instanceof Collection) {
                        // merge Collections like "lists"
                        Collection sourceCollection = (Collection) sourceProperty.getValue();
                        Collection targetCollection = (Collection) targetProperty;

                        for (Object sourceItem : sourceCollection) {
                            if (!targetCollection.contains(sourceItem)) {
                                try {
                                    targetCollection.add(sourceItem);
                                    changed = true;
                                } catch (Exception e) {
                                    // may be Collection type issue
                                }
                            }
                        }
                    }
                }
            }
        }

        return changed;
    }

    public PartialList<Profile> getAccountProfiles(String accountId, String query, int offset, int size, String sortBy) {
        if (StringUtils.isNotBlank(query)) {
            return persistenceService.queryFullText("accountId", accountId, query, sortBy, Profile.class, offset, size);
        } else {
            return persistenceService.query("accountId", accountId, sortBy, Profile.class, offset, size);
        }
    }

    public String getPropertyTypeMapping(String fromPropertyTypeId) {
        Collection<PropertyType> types = getPropertyTypeByMapping(fromPropertyTypeId);
        if (types.size() > 0) {
            return types.iterator().next().getMetadata().getId();
        }
        return null;
    }

    public Profile loadProfileFromAccount(String accountId, String profileId) {
        Account account = persistenceService.load(accountId, Account.class);
        if (account != null && profileId != null) {
            for (String id : account.getProfileIds()) {
                if (id.equals((profileId))) {
                    return persistenceService.load(profileId, Profile.class);
                }
            }
        }
        return null;
    }

    public void saveProfileToAccount(String accountId, Profile profile) {
        Account account = persistenceService.load(accountId, Account.class);
        if (account != null) {
            account.getProfileIds().add(profile.getItemId());
            persistenceService.save(account);
        }
    }

    public List<Profile> findProfilesInAccount(String accountId) {
        List<Profile> profilesInAccount = new ArrayList<>();
        Account account = persistenceService.load(accountId, Account.class);
        if (account != null) {
            for (String profileId : account.getProfileIds()) {
                profilesInAccount.add(persistenceService.load(profileId, Profile.class));
            }
        }
        return profilesInAccount;
    }

    @Override
    public boolean matchCondition(Condition condition, Account account, Profile profile) {
        ParserHelper.resolveConditionType(definitionsService, condition);

        if (condition.getConditionTypeId().equals("booleanCondition")) {
            List<Condition> subConditions = (List<Condition>) condition.getParameter("subConditions");
            boolean isAnd = "and".equals(condition.getParameter("operator"));
            for (Condition subCondition : subConditions) {
                if (isAnd && !matchCondition(subCondition, account, profile)) {
                    return false;
                }
                if (!isAnd && matchCondition(subCondition, account, profile)) {
                    return true;
                }
            }
            return subConditions.size() > 0 && isAnd;
        } else {
            Condition accountCondition = definitionsService.extractConditionBySystemTag(condition, "accountCondition");
            Condition profileCondition = definitionsService.extractConditionBySystemTag(condition, "profileCondition");
            if (accountCondition != null && !persistenceService.testMatch(accountCondition, account)) {
                return false;
            }
            return !(profileCondition != null && !persistenceService.testMatch(profileCondition, profile));
        }
    }

    public void batchAccountsUpdate(BatchUpdate update) {
        ParserHelper.resolveConditionType(definitionsService, update.getCondition());
        List<Account> accounts = persistenceService.query(update.getCondition(), null, Account.class);

        for (Account account : accounts) {
            if (PropertyHelper.setProperty(account, update.getPropertyName(), update.getPropertyValue(), update.getStrategy())) {
                save(account);
            }
        }
    }

    public Collection<PropertyType> getTargetPropertyTypes(String target) {
        if (target == null) {
            return null;
        }
        Collection<PropertyType> result = propertyTypes.getByTarget(target);
        if (result == null) {
            return new ArrayList<>();
        }
        return result;
    }

    public Map<String, Collection<PropertyType>> getTargetPropertyTypes() {
        return new HashMap<>(propertyTypes.getAllByTarget());
    }

    public Set<PropertyType> getPropertyTypeByTag(String tag) {
        if (tag == null) {
            return null;
        }
        List<PropertyType> result = propertyTypes.getByTag(tag);
        if (result == null) {
            return new LinkedHashSet<>();
        }
        return new LinkedHashSet<>(result);
    }

    public Set<PropertyType> getPropertyTypeBySystemTag(String tag) {
        if (tag == null) {
            return null;
        }
        List<PropertyType> result = propertyTypes.getBySystemTag(tag);
        if (result == null) {
            return new LinkedHashSet<>();
        }
        return new LinkedHashSet<>(result);
    }

    public Collection<PropertyType> getPropertyTypeByMapping(String propertyName) {
        Collection<PropertyType> l = new TreeSet<PropertyType>(new Comparator<PropertyType>() {
            @Override
            public int compare(PropertyType o1, PropertyType o2) {
                if (o1.getRank() == o2.getRank()) {
                    return o1.getMetadata().getName().compareTo(o1.getMetadata().getName());
                } else if (o1.getRank() < o2.getRank()) {
                    return -1;
                } else {
                    return 1;
                }
            }
        });

        for (PropertyType propertyType : propertyTypes.getAll()) {
            if (propertyType.getAutomaticMappingsFrom() != null && propertyType.getAutomaticMappingsFrom().contains(propertyName)) {
                l.add(propertyType);
            }
        }
        return l;
    }

    public PropertyType getPropertyType(String id) {
        return propertyTypes.get(id);
    }

    // TODO: Figure this part out in "META-INF.cxs.personas" as reference
    private void loadPredefinedAccounts(BundleContext bundleContext) {}

    // TODO: Figure out if this is necessary
    public void setPropertyTypeTarget(URL predefinedPropertyTypeURL, PropertyType propertyType) {
        if (StringUtils.isBlank(propertyType.getTarget())) {
            String[] splitPath = predefinedPropertyTypeURL.getPath().split("/");
            String target = splitPath[4];
            if (StringUtils.isNotBlank(target)) {
                propertyType.setTarget(target);
            }
        }
    }

    private boolean merge(Map<String, Object> target, Map<String, Object> object) {
        boolean changed = false;
        for (Map.Entry<String, Object> newEntry : object.entrySet()) {
            if (newEntry.getValue() != null) {
                String packageName = newEntry.getValue().getClass().getPackage().getName();
                if (newEntry.getValue() instanceof Collection) {
                    target.put(newEntry.getKey(), newEntry.getValue());
                    changed = true;
                } else if (newEntry.getValue() instanceof Map) {
                    Map<String, Object> currentMap = (Map) target.get(newEntry.getKey());
                    if (currentMap == null) {
                        target.put(newEntry.getKey(), newEntry.getValue());
                        changed = true;
                    } else {
                        changed |= merge(currentMap, (Map) newEntry.getValue());
                    }
                } else if (StringUtils.equals(packageName, "java.lang")) {
                    if (newEntry.getValue() != null && !newEntry.getValue().equals(target.get(newEntry.getKey()))) {
                        target.put(newEntry.getKey(), newEntry.getValue());
                        changed = true;
                    }
                } else if (newEntry.getValue().getClass().isEnum()) {
                    target.put(newEntry.getKey(), newEntry.getValue());
                    changed = true;
                } else {
                    if (target.get(newEntry.getKey()) != null) {
                        changed |= merge(target.get(newEntry.getKey()), newEntry.getValue());
                    } else {
                        target.put(newEntry.getKey(), newEntry.getValue());
                        changed = true;
                    }
                }
            } else {
                if (target.containsKey(newEntry.getKey())) {
                    target.remove(newEntry.getKey());
                    changed = true;
                }
            }
        }
        return changed;
    }

    public void bundleChanged(BundleEvent event) {
        switch (event.getType()) {
            case BundleEvent.STARTED:
                processBundleStartup(event.getBundle().getBundleContext());
                break;
            case BundleEvent.STOPPING:
                processBundleStop(event.getBundle().getBundleContext());
                break;
        }
    }


    public void refresh() {
        reloadPropertyTypes(true);
    }
}
