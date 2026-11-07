// SPDX-FileCopyrightText: Copyright The Thingsboard Authors
// SPDX-License-Identifier: Apache-2.0
package org.thingsboard.server.common.data.notification.info;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.thingsboard.server.common.data.id.DashboardId;
import org.thingsboard.server.common.data.id.EntityId;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
// Keep legacy class-name identifiers on the wire, but resolve only registered CE types.
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = AlarmAssignmentNotificationInfo.class, name = "org.thingsboard.server.common.data.notification.info.AlarmAssignmentNotificationInfo"),
        @JsonSubTypes.Type(value = AlarmCommentNotificationInfo.class, name = "org.thingsboard.server.common.data.notification.info.AlarmCommentNotificationInfo"),
        @JsonSubTypes.Type(value = AlarmNotificationInfo.class, name = "org.thingsboard.server.common.data.notification.info.AlarmNotificationInfo"),
        @JsonSubTypes.Type(value = ApiUsageLimitNotificationInfo.class, name = "org.thingsboard.server.common.data.notification.info.ApiUsageLimitNotificationInfo"),
        @JsonSubTypes.Type(value = DeviceActivityNotificationInfo.class, name = "org.thingsboard.server.common.data.notification.info.DeviceActivityNotificationInfo"),
        @JsonSubTypes.Type(value = EdgeCommunicationFailureNotificationInfo.class, name = "org.thingsboard.server.common.data.notification.info.EdgeCommunicationFailureNotificationInfo"),
        @JsonSubTypes.Type(value = EdgeConnectionNotificationInfo.class, name = "org.thingsboard.server.common.data.notification.info.EdgeConnectionNotificationInfo"),
        @JsonSubTypes.Type(value = EntitiesLimitIncreaseRequestNotificationInfo.class, name = "org.thingsboard.server.common.data.notification.info.EntitiesLimitIncreaseRequestNotificationInfo"),
        @JsonSubTypes.Type(value = EntitiesLimitNotificationInfo.class, name = "org.thingsboard.server.common.data.notification.info.EntitiesLimitNotificationInfo"),
        @JsonSubTypes.Type(value = EntityActionNotificationInfo.class, name = "org.thingsboard.server.common.data.notification.info.EntityActionNotificationInfo"),
        @JsonSubTypes.Type(value = GeneralNotificationInfo.class, name = "org.thingsboard.server.common.data.notification.info.GeneralNotificationInfo"),
        @JsonSubTypes.Type(value = NewPlatformVersionNotificationInfo.class, name = "org.thingsboard.server.common.data.notification.info.NewPlatformVersionNotificationInfo"),
        @JsonSubTypes.Type(value = RateLimitsNotificationInfo.class, name = "org.thingsboard.server.common.data.notification.info.RateLimitsNotificationInfo"),
        @JsonSubTypes.Type(value = ResourcesShortageNotificationInfo.class, name = "org.thingsboard.server.common.data.notification.info.ResourcesShortageNotificationInfo"),
        @JsonSubTypes.Type(value = RuleEngineComponentLifecycleEventNotificationInfo.class, name = "org.thingsboard.server.common.data.notification.info.RuleEngineComponentLifecycleEventNotificationInfo"),
        @JsonSubTypes.Type(value = RuleEngineOriginatedNotificationInfo.class, name = "org.thingsboard.server.common.data.notification.info.RuleEngineOriginatedNotificationInfo"),
        @JsonSubTypes.Type(value = TaskProcessingFailureNotificationInfo.class, name = "org.thingsboard.server.common.data.notification.info.TaskProcessingFailureNotificationInfo")
})
public interface NotificationInfo {

    @JsonIgnore
    Map<String, String> getTemplateData();

    default EntityId getStateEntityId() {
        return null;
    }

    default DashboardId getDashboardId() {
        return null;
    }

}
