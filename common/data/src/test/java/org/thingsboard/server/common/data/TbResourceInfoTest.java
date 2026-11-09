// SPDX-FileCopyrightText: Copyright The Thingsboard Authors
// SPDX-License-Identifier: Apache-2.0
package org.thingsboard.server.common.data;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TbResourceInfoTest {
    @Test
    void explicitSearchTextIsIndependentOfDisplayTitleAndSurvivesCopies() {
        var resource = new TbResourceInfo();
        resource.setTitle("Display title");
        resource.setSearchText("19_1.0:binaryappdatacontainer");

        assertThat(resource.getSearchText()).isEqualTo("19_1.0:binaryappdatacontainer");
        assertThat(new TbResourceInfo(resource).getSearchText()).isEqualTo(resource.getSearchText());
        assertThat(new TbResource(resource).getSearchText()).isEqualTo(resource.getSearchText());
        resource.setTitle("Renamed display title");
        assertThat(resource.getSearchText()).isEqualTo("19_1.0:binaryappdatacontainer");
    }

    @Test
    void resourcesWithoutExplicitSearchTextKeepTitleFallback() {
        var resource = new TbResourceInfo();
        resource.setTitle("Image title");
        assertThat(resource.getSearchText()).isEqualTo("Image title");
        resource.setSearchText("");
        assertThat(resource.getSearchText()).isEmpty();
        resource.setSearchText(null);
        assertThat(resource.getSearchText()).isEqualTo("Image title");
    }
}
