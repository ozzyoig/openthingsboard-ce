// SPDX-FileCopyrightText: Copyright The Thingsboard Authors
// SPDX-License-Identifier: Apache-2.0
package org.thingsboard.server.service.branding;

import org.junit.jupiter.api.Test;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.dao.settings.AdminSettingsService;
import org.thingsboard.server.exception.DataValidationException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class BrandingServiceTest {
    private final AdminSettingsService repository = mock(AdminSettingsService.class);
    private final BrandingService service = new BrandingService(repository);

    @Test
    void inheritsFieldsWithoutMutatingGlobalSettings() {
        var global = BrandingService.defaults();
        global.setPrimaryColor("#123456");
        var tenant = new BrandingSettings();
        tenant.setAppName("Tenant One");
        var effective = BrandingService.merge(global, tenant);
        assertThat(effective.getAppName()).isEqualTo("Tenant One");
        assertThat(effective.getPrimaryColor()).isEqualTo("#123456");
        assertThat(global.getAppName()).isEqualTo("OpenThingsBoard CE");
    }

    @Test
    void acceptsPngAndJpegAndRestrictsFavicon() throws Exception {
        assertThat(service.image(raster("png", 2, 2), true)).startsWith("data:image/png;base64,");
        byte[] jpeg = raster("jpeg", 2, 2);
        assertThat(service.image(jpeg, false)).startsWith("data:image/jpeg;base64,");
        assertThatThrownBy(() -> service.image(jpeg, true)).isInstanceOf(DataValidationException.class);
        byte[] webp = Base64.getDecoder().decode("UklGRiQAAABXRUJQVlA4IBgAAAAwAQCdASoCAAIAAUAmJaQAA3AA/vz0AAA=");
        assertThat(service.image(webp, false)).startsWith("data:image/webp;base64,");
        assertThatThrownBy(() -> service.image(webp, true)).isInstanceOf(DataValidationException.class);
    }

    @Test
    void rejectsSvgSpoofingInvalidDataAndOversizedImages() throws Exception {
        assertThatThrownBy(() -> service.image("<svg onload='alert(1)'/>".getBytes(StandardCharsets.UTF_8), false))
                .isInstanceOf(DataValidationException.class);
        assertThatThrownBy(() -> service.image(new byte[BrandingService.MAX_IMAGE_BYTES + 1], false))
                .isInstanceOf(DataValidationException.class);
        byte[] hugeDimensions = raster("png", 2049, 1);
        assertThatThrownBy(() -> service.image(hugeDimensions, false)).isInstanceOf(DataValidationException.class);
        assertThatThrownBy(() -> service.image(new byte[]{(byte) 255, (byte) 216, (byte) 255}, false))
                .isInstanceOf(DataValidationException.class);
    }

    @Test
    void rejectsUnsafeSettingsBeforeWriting() {
        TenantId tenant = new TenantId(UUID.randomUUID());
        var settings = new BrandingSettings();
        settings.setPrimaryColor("url(javascript:alert(1))");
        assertThatThrownBy(() -> service.save(tenant, settings)).isInstanceOf(DataValidationException.class);
        settings.setPrimaryColor(null);
        settings.setAppName("<script>alert(1)</script>");
        assertThatThrownBy(() -> service.save(tenant, settings)).isInstanceOf(DataValidationException.class);
        settings.setAppName("Tenant");
        settings.setLogo("data:image/svg+xml;base64," + Base64.getEncoder().encodeToString("<svg/>".getBytes(StandardCharsets.UTF_8)));
        assertThatThrownBy(() -> service.save(tenant, settings)).isInstanceOf(DataValidationException.class);
        settings.setLogo(null);
        settings.setLoginLogo("anything");
        assertThatThrownBy(() -> service.save(tenant, settings)).isInstanceOf(DataValidationException.class);
        verifyNoInteractions(repository);
    }

    private static byte[] raster(String format, int width, int height) throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), format, output);
        return output.toByteArray();
    }
}
