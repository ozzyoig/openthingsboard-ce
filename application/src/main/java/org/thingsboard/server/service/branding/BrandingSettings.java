// SPDX-FileCopyrightText: Copyright The Thingsboard Authors
// SPDX-License-Identifier: Apache-2.0
package org.thingsboard.server.service.branding;

import lombok.Data;

/** Null fields inherit the global value; an empty configuration resets all overrides. */
@Data
public class BrandingSettings {
    private String appName;
    private String logo;
    private String loginLogo;
    private String favicon;
    private String primaryColor;
}
