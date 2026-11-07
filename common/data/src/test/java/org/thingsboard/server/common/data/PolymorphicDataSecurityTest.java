// SPDX-FileCopyrightText: Copyright The Thingsboard Authors
// SPDX-License-Identifier: Apache-2.0
package org.thingsboard.server.common.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidTypeIdException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.thingsboard.server.common.data.edqs.Entity;
import org.thingsboard.server.common.data.edqs.fields.EntityFields;
import org.thingsboard.server.common.data.edqs.fields.EntityIdFields;
import org.thingsboard.server.common.data.notification.info.GeneralNotificationInfo;
import org.thingsboard.server.common.data.notification.info.NotificationInfo;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PolymorphicDataSecurityTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @ParameterizedTest
    @ValueSource(strings = {
            "AlarmAssignmentNotificationInfo", "AlarmCommentNotificationInfo", "AlarmNotificationInfo",
            "ApiUsageLimitNotificationInfo", "DeviceActivityNotificationInfo", "EdgeCommunicationFailureNotificationInfo",
            "EdgeConnectionNotificationInfo", "EntitiesLimitIncreaseRequestNotificationInfo", "EntitiesLimitNotificationInfo",
            "EntityActionNotificationInfo", "GeneralNotificationInfo", "NewPlatformVersionNotificationInfo",
            "RateLimitsNotificationInfo", "ResourcesShortageNotificationInfo", "RuleEngineComponentLifecycleEventNotificationInfo",
            "RuleEngineOriginatedNotificationInfo", "TaskProcessingFailureNotificationInfo"
    })
    void readsLegacyNotificationTypeIdentifiers(String name) throws Exception {
        String id = "org.thingsboard.server.common.data.notification.info." + name;
        NotificationInfo info = mapper.readValue("{\"type\":\"" + id + "\",\"futureField\":true}", NotificationInfo.class);
        assertThat(info.getClass().getName()).isEqualTo(id);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "AbstractEntityFields", "ApiUsageStateFields", "AssetFields", "AssetProfileFields", "CustomerFields",
            "DashboardFields", "DeviceFields", "DeviceProfileFields", "EdgeFields", "EntityIdFields", "EntityViewFields",
            "GenericFields", "QueueStatsFields", "RuleChainFields", "RuleNodeFields", "TenantFields", "TenantProfileFields",
            "UserFields", "WidgetTypeFields", "WidgetsBundleFields"
    })
    void readsLegacyEdqsTypeIdentifiers(String name) throws Exception {
        String id = "org.thingsboard.server.common.data.edqs.fields." + name;
        Entity entity = mapper.readValue("{\"type\":\"DEVICE\",\"fields\":{\"@class\":\"" + id +
                "\",\"futureField\":true}}", Entity.class);
        assertThat(entity.getFields().getClass().getName()).isEqualTo(id);
    }

    @Test
    void writesCompatibleNotificationIdentifiersAndPreservesData() throws Exception {
        NotificationInfo original = new GeneralNotificationInfo(Map.of("message", "test notification"));
        String json = mapper.writerFor(NotificationInfo.class).writeValueAsString(original);
        assertThat(mapper.readTree(json).get("type").asText()).isEqualTo(GeneralNotificationInfo.class.getName());
        NotificationInfo decoded = mapper.readValue(json, NotificationInfo.class);
        assertThat(decoded).isEqualTo(original);
        assertThat(decoded.getTemplateData()).containsEntry("message", "test notification");
    }

    @Test
    void writesCompatibleEdqsIdentifiersAndPreservesIdentityAndVersion() throws Exception {
        UUID id = UUID.randomUUID();
        Entity original = new Entity(EntityType.DEVICE, new EntityIdFields(id, 42L));
        String json = mapper.writeValueAsString(original);
        assertThat(mapper.readTree(json).get("fields").get("@class").asText()).isEqualTo(EntityIdFields.class.getName());
        Entity decoded = mapper.readValue(json, Entity.class);
        assertThat(decoded.getFields()).isEqualTo(original.getFields());
        assertThat(decoded.stringKey()).isEqualTo("e_" + id);
        assertThat(decoded.version()).isEqualTo(42L);
    }

    @Test
    void rejectsUnregisteredNotificationClassesBeforeConstruction() {
        UnregisteredNotification.constructed = false;
        String json = "{\"type\":\"" + UnregisteredNotification.class.getName() + "\"}";
        assertThatThrownBy(() -> mapper.readValue(json, NotificationInfo.class)).isInstanceOf(InvalidTypeIdException.class);
        assertThat(UnregisteredNotification.constructed).isFalse();
    }

    @Test
    void rejectsUnregisteredEdqsClassesBeforeConstruction() {
        UnregisteredFields.constructed = false;
        String json = "{\"type\":\"DEVICE\",\"fields\":{\"@class\":\"" + UnregisteredFields.class.getName() + "\"}}";
        assertThatThrownBy(() -> mapper.readValue(json, Entity.class)).isInstanceOf(InvalidTypeIdException.class);
        assertThat(UnregisteredFields.constructed).isFalse();
    }

    @Test
    void rejectsMissingTypeDiscriminators() {
        assertThatThrownBy(() -> mapper.readValue("{}", NotificationInfo.class)).isInstanceOf(InvalidTypeIdException.class);
        assertThatThrownBy(() -> mapper.readValue("{\"fields\":{}}", Entity.class)).isInstanceOf(InvalidTypeIdException.class);
    }

    public static class UnregisteredNotification implements NotificationInfo {
        static boolean constructed;
        public UnregisteredNotification() { constructed = true; }
        @Override public Map<String, String> getTemplateData() { return Map.of(); }
    }

    public static class UnregisteredFields implements EntityFields {
        static boolean constructed;
        public UnregisteredFields() { constructed = true; }
        @Override public UUID getId() { return null; }
    }
}
