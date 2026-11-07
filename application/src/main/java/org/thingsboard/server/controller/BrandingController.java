// SPDX-FileCopyrightText: Copyright The Thingsboard Authors
// SPDX-License-Identifier: Apache-2.0
package org.thingsboard.server.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.security.Authority;
import org.thingsboard.server.queue.util.TbCoreComponent;
import org.thingsboard.server.service.branding.BrandingService;
import org.thingsboard.server.service.branding.BrandingSettings;

import java.util.Map;

@RestController
@TbCoreComponent
@RequiredArgsConstructor
public class BrandingController extends BaseController {
    private final BrandingService brandingService;

    @GetMapping("/api/noauth/branding")
    public ResponseEntity<BrandingSettings> publicBranding() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(brandingService.effective(TenantId.SYS_TENANT_ID));
    }

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN', 'CUSTOMER_USER')")
    @GetMapping("/api/branding/effective")
    public ResponseEntity<BrandingSettings> effectiveBranding() throws Exception {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(brandingService.effective(scope()));
    }

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN')")
    @GetMapping("/api/branding")
    public ResponseEntity<BrandingSettings> ownBranding() throws Exception {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(brandingService.get(scope()));
    }

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN')")
    @PostMapping("/api/branding")
    public BrandingSettings saveBranding(@RequestBody BrandingSettings settings) throws Exception {
        return brandingService.save(scope(), settings);
    }

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN')")
    @DeleteMapping("/api/branding")
    public void resetBranding() throws Exception {
        brandingService.reset(scope());
    }

    @PreAuthorize("hasAnyAuthority('SYS_ADMIN', 'TENANT_ADMIN')")
    @PostMapping(value = "/api/branding/image", consumes = "multipart/form-data")
    public Map<String, String> upload(@RequestParam MultipartFile file,
                                     @RequestParam(defaultValue = "false") boolean favicon) throws Exception {
        if (file.getSize() > BrandingService.MAX_IMAGE_BYTES) {
            throw new org.thingsboard.server.exception.DataValidationException("Image exceeds 256 KiB");
        }
        return Map.of("data", brandingService.image(file.getBytes(), favicon));
    }

    private TenantId scope() throws Exception {
        var user = getCurrentUser();
        return user.getAuthority() == Authority.SYS_ADMIN ? TenantId.SYS_TENANT_ID : user.getTenantId();
    }
}
