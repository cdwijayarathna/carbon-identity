/*
 * Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.identity.workflow.mgt.impl.userstore;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.context.CarbonContext;
import org.wso2.carbon.identity.workflow.mgt.extension.AbstractWorkflowRequestHandler;
import org.wso2.carbon.identity.workflow.mgt.impl.dao.EntityDAO;
import org.wso2.carbon.identity.workflow.mgt.impl.dao.EntityRelationshipDAO;
import org.wso2.carbon.identity.workflow.mgt.util.WorkflowDataType;
import org.wso2.carbon.identity.workflow.mgt.exception.WorkflowException;
import org.wso2.carbon.identity.workflow.mgt.util.WorkflowRequestStatus;
import org.wso2.carbon.identity.workflow.mgt.impl.internal.IdentityWorkflowDataHolder;
import org.wso2.carbon.user.api.UserRealm;
import org.wso2.carbon.user.api.UserStoreException;
import org.wso2.carbon.user.core.service.RealmService;
import org.wso2.carbon.user.core.util.UserCoreUtil;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class UpdateRoleNameWFRequestHandler extends AbstractWorkflowRequestHandler {

    private static final String FRIENDLY_NAME = "Update Rolename";
    private static final String FRIENDLY_DESCRIPTION = "Triggered when a role name is updates";

    private static final String ROLENAME = "Role Name";
    private static final String NEW_ROLENAME = "New Role Name";
    private static final String USER_STORE_DOMAIN = "User Store Domain";

    private static final Map<String, String> PARAM_DEFINITION;
    private static Log log = LogFactory.getLog(DeleteRoleWFRequestHandler.class);


    static {
        PARAM_DEFINITION = new LinkedHashMap<>();
        PARAM_DEFINITION.put(ROLENAME, WorkflowDataType.STRING_TYPE);
        PARAM_DEFINITION.put(NEW_ROLENAME, WorkflowDataType.STRING_TYPE);
        PARAM_DEFINITION.put(USER_STORE_DOMAIN, WorkflowDataType.STRING_TYPE);
    }

    public boolean startUpdateRoleNameFlow(String userStoreDomain, String roleName, String newRoleName) throws
            WorkflowException {
        Map<String, Object> wfParams = new HashMap<>();
        Map<String, Object> nonWfParams = new HashMap<>();
        if (!Boolean.TRUE.equals(getWorkFlowCompleted())) {
            EntityDAO entityDAO = new EntityDAO();
            EntityRelationshipDAO entityRelationshipDAO = new EntityRelationshipDAO();
            String tenant = CarbonContext.getThreadLocalCarbonContext().getTenantDomain();
            String nameWithTenant = UserCoreUtil.addTenantDomainToEntry(roleName, tenant);
            String fullyQualifiedName = UserCoreUtil.addDomainToName(nameWithTenant, userStoreDomain);
            String newNameWithTenant = UserCoreUtil.addTenantDomainToEntry(newRoleName, tenant);
            String fullyQualifiedNewName = UserCoreUtil.addDomainToName(newNameWithTenant, userStoreDomain);
            if (!entityDAO.checkEntityLocked(fullyQualifiedName, "ROLE") || !entityDAO.checkEntityLocked
                    (fullyQualifiedNewName, "ROLE") || !entityRelationshipDAO.checkIfEntityHasAnyRelationShip
                    (fullyQualifiedName, "ROLE") || !entityDAO.updateEntityLockedState(fullyQualifiedName, "ROLE",
                    "RENAME") || !entityDAO.updateEntityLockedState(fullyQualifiedNewName, "ROLE", "RENAME")) {
                throw new WorkflowException("Role cannot rename, it is currently pending in 1 or more workflows.");
            }
            entityRelationshipDAO.addNewRelationship(fullyQualifiedName, "ROLE", fullyQualifiedNewName, "ROLE",
                    "RENAME");

        }
        wfParams.put(ROLENAME, roleName);
        wfParams.put(NEW_ROLENAME, newRoleName);
        wfParams.put(USER_STORE_DOMAIN, userStoreDomain);
        return startWorkFlow(wfParams, nonWfParams);
    }

    @Override
    public void onWorkflowCompletion(String status, Map<String, Object> requestParams,
                                     Map<String, Object> responseAdditionalParams, int tenantId)
            throws WorkflowException {
        String roleName = (String) requestParams.get(ROLENAME);
        String newRoleName = (String) requestParams.get(NEW_ROLENAME);
        if (roleName == null) {
            throw new WorkflowException("Callback request for rename role received without the mandatory " +
                    "parameter 'roleName'");
        }
        if (newRoleName == null) {
            throw new WorkflowException("Callback request for rename role received without the mandatory " +
                    "parameter 'newRoleName'");
        }

        String userStoreDomain = (String) requestParams.get(USER_STORE_DOMAIN);
        if (StringUtils.isNotBlank(userStoreDomain)) {
            roleName = userStoreDomain + "/" + roleName;
        }

        if (WorkflowRequestStatus.APPROVED.toString().equals(status) ||
                WorkflowRequestStatus.SKIPPED.toString().equals(status)) {
            try {
                RealmService realmService = IdentityWorkflowDataHolder.getInstance().getRealmService();
                UserRealm userRealm = realmService.getTenantUserRealm(tenantId);
                userRealm.getUserStoreManager().updateRoleName(roleName, newRoleName);
            } catch (UserStoreException e) {
                throw new WorkflowException("Error when re-requesting updateRoleName operation for " + roleName, e);
            }

            if (WorkflowRequestStatus.APPROVED.toString().equals(status)) {
                String roleNameWithoutDomain = UserCoreUtil.removeDomainFromName(roleName);
                String newRoleNameWithoutDomain = UserCoreUtil.removeDomainFromName(newRoleName);
                EntityDAO entityDAO = new EntityDAO();
                EntityRelationshipDAO entityRelationshipDAO = new EntityRelationshipDAO();
                String tenant = CarbonContext.getThreadLocalCarbonContext().getTenantDomain();
                String nameWithTenant = UserCoreUtil.addTenantDomainToEntry(roleNameWithoutDomain, tenant);
                String fullyQualifiedName = UserCoreUtil.addDomainToName(nameWithTenant, userStoreDomain);
                String newNameWithTenant = UserCoreUtil.addTenantDomainToEntry(newRoleNameWithoutDomain, tenant);
                String fullyQualifiedNewName = UserCoreUtil.addDomainToName(newNameWithTenant, userStoreDomain);
                entityRelationshipDAO.deleteEntityRelationshipState(fullyQualifiedName, "ROLE",
                        fullyQualifiedNewName, "ROLE", "RENAME");
                entityDAO.deleteEntityLockedState(fullyQualifiedName, "ROLE", "RENAME");
                entityDAO.deleteEntityLockedState(fullyQualifiedNewName, "ROLE", "RENAME");
            }
        } else {
            String roleNameWithoutDomain = UserCoreUtil.removeDomainFromName(roleName);
            String newRoleNameWithoutDomain = UserCoreUtil.removeDomainFromName(newRoleName);
            EntityDAO entityDAO = new EntityDAO();
            EntityRelationshipDAO entityRelationshipDAO = new EntityRelationshipDAO();
            String tenant = CarbonContext.getThreadLocalCarbonContext().getTenantDomain();
            String nameWithTenant = UserCoreUtil.addTenantDomainToEntry(roleNameWithoutDomain, tenant);
            String fullyQualifiedName = UserCoreUtil.addDomainToName(nameWithTenant, userStoreDomain);
            String newNameWithTenant = UserCoreUtil.addTenantDomainToEntry(newRoleNameWithoutDomain, tenant);
            String fullyQualifiedNewName = UserCoreUtil.addDomainToName(newNameWithTenant, userStoreDomain);
            entityRelationshipDAO.deleteEntityRelationshipState(fullyQualifiedName, "ROLE",
                    fullyQualifiedNewName, "ROLE", "RENAME");
            entityDAO.deleteEntityLockedState(fullyQualifiedName, "ROLE", "RENAME");
            entityDAO.deleteEntityLockedState(fullyQualifiedNewName, "ROLE", "RENAME");
            if (retryNeedAtCallback()) {
                //unset threadlocal variable
                unsetWorkFlowCompleted();
            }
            if (log.isDebugEnabled()) {
                log.debug("Updating role is aborted for role '" + roleName + "', Reason: Workflow response was " +
                        status);
            }
        }
    }

    @Override
    public boolean retryNeedAtCallback() {
        return true;
    }

    @Override
    public String getEventId() {
        return UserStoreWFConstants.UPDATE_ROLE_NAME_EVENT;
    }

    @Override
    public Map<String, String> getParamDefinitions() {
        return PARAM_DEFINITION;
    }

    @Override
    public String getFriendlyName() {
        return FRIENDLY_NAME;
    }

    @Override
    public String getDescription() {
        return FRIENDLY_DESCRIPTION;
    }

    @Override
    public String getCategory() {
        return UserStoreWFConstants.CATEGORY_USERSTORE_OPERATIONS;
    }
}
