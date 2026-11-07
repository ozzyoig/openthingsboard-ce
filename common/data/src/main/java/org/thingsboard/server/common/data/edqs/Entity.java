// SPDX-FileCopyrightText: Copyright The Thingsboard Authors
// SPDX-License-Identifier: Apache-2.0
package org.thingsboard.server.common.data.edqs;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.thingsboard.server.common.data.EntityType;
import org.thingsboard.server.common.data.ObjectType;
import org.thingsboard.server.common.data.edqs.fields.AbstractEntityFields;
import org.thingsboard.server.common.data.edqs.fields.ApiUsageStateFields;
import org.thingsboard.server.common.data.edqs.fields.AssetFields;
import org.thingsboard.server.common.data.edqs.fields.AssetProfileFields;
import org.thingsboard.server.common.data.edqs.fields.CustomerFields;
import org.thingsboard.server.common.data.edqs.fields.DashboardFields;
import org.thingsboard.server.common.data.edqs.fields.DeviceFields;
import org.thingsboard.server.common.data.edqs.fields.DeviceProfileFields;
import org.thingsboard.server.common.data.edqs.fields.EdgeFields;
import org.thingsboard.server.common.data.edqs.fields.EntityFields;
import org.thingsboard.server.common.data.edqs.fields.EntityIdFields;
import org.thingsboard.server.common.data.edqs.fields.EntityViewFields;
import org.thingsboard.server.common.data.edqs.fields.GenericFields;
import org.thingsboard.server.common.data.edqs.fields.QueueStatsFields;
import org.thingsboard.server.common.data.edqs.fields.RuleChainFields;
import org.thingsboard.server.common.data.edqs.fields.RuleNodeFields;
import org.thingsboard.server.common.data.edqs.fields.TenantFields;
import org.thingsboard.server.common.data.edqs.fields.TenantProfileFields;
import org.thingsboard.server.common.data.edqs.fields.UserFields;
import org.thingsboard.server.common.data.edqs.fields.WidgetTypeFields;
import org.thingsboard.server.common.data.edqs.fields.WidgetsBundleFields;

import java.util.UUID;

@Data
@NoArgsConstructor
public class Entity implements EdqsObject {

    private EntityType type;

    @JsonIgnoreProperties(ignoreUnknown = true)
    // Preserve legacy @class values without accepting arbitrary Java class names.
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "@class")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = AbstractEntityFields.class, name = "org.thingsboard.server.common.data.edqs.fields.AbstractEntityFields"),
            @JsonSubTypes.Type(value = ApiUsageStateFields.class, name = "org.thingsboard.server.common.data.edqs.fields.ApiUsageStateFields"),
            @JsonSubTypes.Type(value = AssetFields.class, name = "org.thingsboard.server.common.data.edqs.fields.AssetFields"),
            @JsonSubTypes.Type(value = AssetProfileFields.class, name = "org.thingsboard.server.common.data.edqs.fields.AssetProfileFields"),
            @JsonSubTypes.Type(value = CustomerFields.class, name = "org.thingsboard.server.common.data.edqs.fields.CustomerFields"),
            @JsonSubTypes.Type(value = DashboardFields.class, name = "org.thingsboard.server.common.data.edqs.fields.DashboardFields"),
            @JsonSubTypes.Type(value = DeviceFields.class, name = "org.thingsboard.server.common.data.edqs.fields.DeviceFields"),
            @JsonSubTypes.Type(value = DeviceProfileFields.class, name = "org.thingsboard.server.common.data.edqs.fields.DeviceProfileFields"),
            @JsonSubTypes.Type(value = EdgeFields.class, name = "org.thingsboard.server.common.data.edqs.fields.EdgeFields"),
            @JsonSubTypes.Type(value = EntityIdFields.class, name = "org.thingsboard.server.common.data.edqs.fields.EntityIdFields"),
            @JsonSubTypes.Type(value = EntityViewFields.class, name = "org.thingsboard.server.common.data.edqs.fields.EntityViewFields"),
            @JsonSubTypes.Type(value = GenericFields.class, name = "org.thingsboard.server.common.data.edqs.fields.GenericFields"),
            @JsonSubTypes.Type(value = QueueStatsFields.class, name = "org.thingsboard.server.common.data.edqs.fields.QueueStatsFields"),
            @JsonSubTypes.Type(value = RuleChainFields.class, name = "org.thingsboard.server.common.data.edqs.fields.RuleChainFields"),
            @JsonSubTypes.Type(value = RuleNodeFields.class, name = "org.thingsboard.server.common.data.edqs.fields.RuleNodeFields"),
            @JsonSubTypes.Type(value = TenantFields.class, name = "org.thingsboard.server.common.data.edqs.fields.TenantFields"),
            @JsonSubTypes.Type(value = TenantProfileFields.class, name = "org.thingsboard.server.common.data.edqs.fields.TenantProfileFields"),
            @JsonSubTypes.Type(value = UserFields.class, name = "org.thingsboard.server.common.data.edqs.fields.UserFields"),
            @JsonSubTypes.Type(value = WidgetTypeFields.class, name = "org.thingsboard.server.common.data.edqs.fields.WidgetTypeFields"),
            @JsonSubTypes.Type(value = WidgetsBundleFields.class, name = "org.thingsboard.server.common.data.edqs.fields.WidgetsBundleFields")
    })
    private EntityFields fields;

    public Entity(EntityType type) {
        this.type = type;
    }

    public Entity(EntityType type, EntityFields fields) {
        this.type = type;
        this.fields = fields;
    }

    public Entity(EntityType entityType, UUID id, long version) {
        this.type = entityType;
        this.fields = new EntityIdFields(id, version);
    }

    @Override
    public String stringKey() {
        return "e_" + fields.getId().toString();
    }

    @Override
    public Long version() {
        return fields.getVersion();
    }

    @Override
    public ObjectType type() {
        return ObjectType.fromEntityType(type);
    }

    public record Key(UUID id) implements EdqsObjectKey {}

}
