// SPDX-FileCopyrightText: Copyright The Thingsboard Authors
// SPDX-License-Identifier: Apache-2.0
package org.thingsboard.server.controller;

import org.junit.After;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.dao.service.DaoSqlTest;
import org.thingsboard.server.dao.settings.AdminSettingsService;
import org.thingsboard.server.service.branding.BrandingService;
import org.thingsboard.server.service.branding.BrandingSettings;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DaoSqlTest
@TestPropertySource(properties = "spring.datasource.url=jdbc:tc:postgresql:16:///thingsboard?TC_DAEMON=true&TC_TMPFS=/testtmpfs:rw&TC_INITFUNCTION=org.thingsboard.server.dao.PostgreSqlInitializer::initDb")
public class BrandingControllerTest extends AbstractControllerTest {
    @Autowired private BrandingService brandingService;
    @Autowired private AdminSettingsService settingsService;

    @After
    public void resetGlobalBranding() {
        brandingService.reset(TenantId.SYS_TENANT_ID);
    }

    @Test
    public void globalTenantInheritanceAndIsolation() throws Exception {
        loginSysAdmin();
        var global = new BrandingSettings();
        global.setAppName("Global Brand");
        global.setPrimaryColor("#123456");
        doPost("/api/branding", global).andExpect(status().isOk());
        resetTokens();
        doGet("/api/noauth/branding").andExpect(status().isOk()).andExpect(jsonPath("$.appName", is("Global Brand")));
        loginTenantAdmin();
        doGet("/api/branding/effective").andExpect(jsonPath("$.primaryColor", is("#123456")));
        var own = new BrandingSettings();
        own.setAppName("Private Tenant Brand");
        doPost("/api/branding", own).andExpect(status().isOk());
        doGet("/api/branding/effective").andExpect(jsonPath("$.appName", is("Private Tenant Brand")))
                .andExpect(jsonPath("$.primaryColor", is("#123456")));
        // A new service instance reads the same persisted database record, without a local cache.
        assertThat(new BrandingService(settingsService).effective(tenantId).getAppName()).isEqualTo("Private Tenant Brand");
        loginSysAdmin();
        global.setPrimaryColor("#654321");
        doPost("/api/branding", global).andExpect(status().isOk());
        loginTenantAdmin();
        doGet("/api/branding/effective").andExpect(jsonPath("$.appName", is("Private Tenant Brand")))
                .andExpect(jsonPath("$.primaryColor", is("#654321")));
        loginDifferentTenant();
        doGet("/api/branding").andExpect(jsonPath("$.appName").doesNotExist());
        doGet("/api/branding/effective").andExpect(jsonPath("$.appName", is("Global Brand")));
        own.setAppName("Other Tenant");
        doPost("/api/branding?tenantId=" + tenantId.getId(), own).andExpect(status().isOk());
        loginTenantAdmin();
        doGet("/api/branding").andExpect(jsonPath("$.appName", is("Private Tenant Brand")));
        doDelete("/api/branding").andExpect(status().isOk());
        doGet("/api/branding/effective").andExpect(jsonPath("$.appName", is("Global Brand")));
        doGet("/api/noauth/branding").andExpect(jsonPath("$.appName", is("Global Brand")));
        loginSysAdmin();
        doDelete("/api/branding").andExpect(status().isOk());
        resetTokens();
        doGet("/api/noauth/branding").andExpect(status().isOk())
                .andExpect(jsonPath("$.appName", is("OpenThingsBoard CE")));
    }

    @Test
    public void anonymousAndCustomerCannotManageBranding() throws Exception {
        loginCustomerUser();
        doGet("/api/branding").andExpect(status().isForbidden());
        doPost("/api/branding", new BrandingSettings()).andExpect(status().isForbidden());
        doDelete("/api/branding").andExpect(status().isForbidden());
        doGet("/api/branding/effective").andExpect(status().isOk());
        resetTokens();
        doGet("/api/branding").andExpect(status().isUnauthorized());
        doGet("/api/branding/effective").andExpect(status().isUnauthorized());
        doPost("/api/branding", new BrandingSettings()).andExpect(status().isUnauthorized());
        doGet("/api/noauth/branding").andExpect(status().isOk());
    }

    @Test
    public void uploadIsValidatedAndStoredWithSettings() throws Exception {
        loginTenantAdmin();
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", bytes);
        var request = MockMvcRequestBuilders.multipart("/api/branding/image")
                .file(new MockMultipartFile("file", "logo.png", "image/png", bytes.toByteArray()));
        setJwtToken(request);
        String uri = readResponse(mockMvc.perform(request).andExpect(status().isOk()),
                com.fasterxml.jackson.databind.JsonNode.class).get("data").asText();
        var settings = new BrandingSettings();
        settings.setLogo(uri);
        doPost("/api/branding", settings).andExpect(status().isOk());
        assertThat(new BrandingService(settingsService).get(tenantId).getLogo()).isEqualTo(uri);
        var invalid = MockMvcRequestBuilders.multipart("/api/branding/image")
                .file(new MockMultipartFile("file", "logo.png", "image/png", "<svg/>".getBytes(StandardCharsets.UTF_8)));
        setJwtToken(invalid);
        mockMvc.perform(invalid).andExpect(status().isBadRequest());
    }
}
