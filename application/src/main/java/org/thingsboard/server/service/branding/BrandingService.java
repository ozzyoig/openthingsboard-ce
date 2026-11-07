// SPDX-FileCopyrightText: Copyright The Thingsboard Authors
// SPDX-License-Identifier: Apache-2.0
package org.thingsboard.server.service.branding;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Directory;
import com.drew.metadata.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.common.data.AdminSettings;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.id.AdminSettingsId;
import org.thingsboard.server.dao.settings.AdminSettingsService;
import org.thingsboard.server.exception.DataValidationException;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;

@Service
@RequiredArgsConstructor
public class BrandingService {
    static final String KEY = "whiteLabelingV1";
    public static final int MAX_IMAGE_BYTES = 256 * 1024;
    private final AdminSettingsService settingsService;

    public BrandingSettings get(TenantId tenantId) {
        AdminSettings settings = settingsService.findAdminSettingsByTenantIdAndKey(tenantId, KEY);
        return settings == null ? new BrandingSettings() :
                JacksonUtil.convertValue(settings.getJsonValue(), BrandingSettings.class);
    }

    public BrandingSettings save(TenantId tenantId, BrandingSettings value) {
        validate(value, tenantId.equals(TenantId.SYS_TENANT_ID));
        AdminSettings settings = settingsService.findAdminSettingsByTenantIdAndKey(tenantId, KEY);
        if (settings == null) {
            // Stable identity prevents concurrent first saves from creating duplicate
            // rows for this scope in installations without a tenant/key unique index.
            settings = new AdminSettings(new AdminSettingsId(UUID.nameUUIDFromBytes(
                    (tenantId.getId() + ":" + KEY).getBytes(StandardCharsets.UTF_8))));
            settings.setTenantId(tenantId);
            settings.setKey(KEY);
        }
        settings.setJsonValue(JacksonUtil.valueToTree(value));
        settingsService.saveAdminSettings(tenantId, settings);
        return value;
    }

    public void reset(TenantId tenantId) {
        settingsService.deleteAdminSettingsByTenantIdAndKey(tenantId, KEY);
    }

    public BrandingSettings effective(TenantId tenantId) {
        BrandingSettings global = merge(defaults(), get(TenantId.SYS_TENANT_ID));
        return tenantId.equals(TenantId.SYS_TENANT_ID) ? global : merge(global, get(tenantId));
    }

    static BrandingSettings defaults() {
        BrandingSettings defaults = new BrandingSettings();
        defaults.setAppName("OpenThingsBoard CE");
        return defaults;
    }

    static BrandingSettings merge(BrandingSettings parent, BrandingSettings overrides) {
        BrandingSettings result = new BrandingSettings();
        result.setAppName(overrides.getAppName() != null ? overrides.getAppName() : parent.getAppName());
        result.setLogo(overrides.getLogo() != null ? overrides.getLogo() : parent.getLogo());
        result.setLoginLogo(overrides.getLoginLogo() != null ? overrides.getLoginLogo() : parent.getLoginLogo());
        result.setFavicon(overrides.getFavicon() != null ? overrides.getFavicon() : parent.getFavicon());
        result.setPrimaryColor(overrides.getPrimaryColor() != null ? overrides.getPrimaryColor() : parent.getPrimaryColor());
        return result;
    }

    private void validate(BrandingSettings value, boolean global) {
        if (value == null) {
            throw invalid("Configuration is required");
        }
        if (value.getAppName() != null && (value.getAppName().isBlank() || value.getAppName().length() > 80 ||
                value.getAppName().chars().anyMatch(c -> Character.isISOControl(c) || c == '<' || c == '>'))) {
            throw invalid("Application name must contain 1–80 plain text characters");
        }
        if (value.getPrimaryColor() != null && !value.getPrimaryColor().matches("#[0-9a-fA-F]{6}")) {
            throw invalid("Primary color must be #RRGGBB");
        }
        if (!global && value.getLoginLogo() != null) {
            throw invalid("Login branding is global only");
        }
        validateImage(value.getLogo(), false);
        validateImage(value.getLoginLogo(), false);
        validateImage(value.getFavicon(), true);
    }

    public String image(byte[] data, boolean favicon) {
        if (data.length == 0 || data.length > MAX_IMAGE_BYTES) {
            throw invalid("Image must be between 1 byte and 256 KiB");
        }
        String type;
        if (data.length >= 8 && Arrays.equals(Arrays.copyOf(data, 8),
                new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10})) {
            type = "image/png";
        } else if (data.length >= 3 && data[0] == (byte) 255 && data[1] == (byte) 216 && data[2] == (byte) 255) {
            type = "image/jpeg";
        } else if (data.length >= 12 && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F' &&
                data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P') {
            type = "image/webp";
        } else {
            throw invalid("Only PNG, JPEG and WebP raster images are allowed");
        }
        if (favicon && !type.equals("image/png")) {
            throw invalid("Favicon must be PNG");
        }
        // Reuse the metadata parser used by the existing image gallery. Bound dimensions
        // without decoding an attacker-controlled full-size bitmap into memory.
        try {
            boolean dimensionsFound = false;
            for (Directory directory : ImageMetadataReader.readMetadata(new ByteArrayInputStream(data)).getDirectories()) {
                Integer width = null;
                Integer height = null;
                for (Tag tag : directory.getTags()) {
                    String name = tag.getTagName().toLowerCase(java.util.Locale.ROOT);
                    if (Set.of("image width", "width").contains(name)) {
                        width = directory.getInteger(tag.getTagType());
                    } else if (Set.of("image height", "height").contains(name)) {
                        height = directory.getInteger(tag.getTagType());
                    }
                }
                if (width != null && height != null) {
                    if (width < 1 || height < 1 || width > 2048 || height > 2048) {
                        throw invalid("Image dimensions must be between 1 and 2048 pixels");
                    }
                    dimensionsFound = true;
                }
            }
            if (!dimensionsFound) {
                throw invalid("Image dimensions could not be read");
            }
            if (!type.equals("image/webp") && ImageIO.read(new ByteArrayInputStream(data)) == null) {
                throw invalid("Invalid raster image content");
            }
        } catch (DataValidationException e) {
            throw e;
        } catch (Exception e) {
            throw invalid("Invalid image content");
        }
        return "data:" + type + ";base64," + Base64.getEncoder().encodeToString(data);
    }

    private void validateImage(String uri, boolean favicon) {
        if (uri == null) {
            return;
        }
        if (uri.length() > MAX_IMAGE_BYTES * 4 / 3 + 100) {
            throw invalid("Image exceeds 256 KiB");
        }
        int separator = uri.indexOf(',');
        try {
            if (separator < 0 || !image(Base64.getDecoder().decode(uri.substring(separator + 1)), favicon).equals(uri)) {
                throw invalid("Invalid raster image data URI");
            }
        } catch (IllegalArgumentException e) {
            throw invalid("Invalid image encoding");
        }
    }

    private static DataValidationException invalid(String message) {
        return new DataValidationException(message);
    }
}
