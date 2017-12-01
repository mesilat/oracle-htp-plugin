package com.mesilat.ora;

import com.atlassian.confluence.pages.Page;
import com.atlassian.confluence.security.ContentPermission;
import com.atlassian.confluence.security.ContentPermissionSet;
import com.atlassian.sal.api.user.UserKey;
import com.atlassian.sal.api.user.UserManager;

public abstract class ResourceBase {
    private final UserManager userManager;

    public UserManager getUserManager() {
        return userManager;
    }
    public boolean isUserAdmin(UserKey userKey) {
        if (userKey == null) {
            return false;
        }
        return userManager.isSystemAdmin(userKey)
            || userManager.isUserInGroup(userKey, "oracle-dba");
    }
    public boolean isUserAuthorized(UserKey userKey, Page page) {
        if (userKey == null) {
            return false;
        }
        ContentPermissionSet permissions = page.getContentPermissionSet(ContentPermission.EDIT_PERMISSION);
        if (permissions != null) {
            if (permissions.getUserKeys().contains(userKey)) {
                return true;
            }
            for (String groupName : permissions.getGroupNames()) {
                if (userManager.isUserInGroup(userKey, groupName)) {
                    return true;
                }
            }
        }
        permissions = page.getContentPermissionSet(ContentPermission.VIEW_PERMISSION);
        if (permissions != null) {
            if (permissions.getUserKeys().contains(userKey)) {
                return true;
            }
            for (String groupName : permissions.getGroupNames()) {
                if (userManager.isUserInGroup(userKey, groupName)) {
                    return true;
                }
            }
        }
        return false;
    }

    public ResourceBase(UserManager userManager) {
        this.userManager = userManager;
    }
}