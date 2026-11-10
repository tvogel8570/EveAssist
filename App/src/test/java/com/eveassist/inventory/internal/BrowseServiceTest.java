package com.eveassist.inventory.internal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BrowseServiceTest {

    @Test
    void showMoreIsOfferedOnlyWhileAnotherPageWouldShowSomething() {
        assertThat(BrowseService.moreAvailable(250, 100, 100)).isTrue();
        assertThat(BrowseService.moreAvailable(100, 100, 100)).isFalse();
        assertThat(BrowseService.moreAvailable(50, 50, 100)).isFalse();
        assertThat(BrowseService.moreAvailable(5_000, 900, 900)).isTrue();
    }

    @Test
    void atTheMostThePageEverListsItSaysSoInsteadOfOfferingMore() {
        var most = BrowseFilter.MOST_ITEMS;

        assertThat(BrowseService.moreAvailable(5_000, most, most)).isFalse();
        assertThat(BrowseService.capped(5_000, most, most)).isTrue();
        assertThat(BrowseService.capped(most, most, most)).isFalse();
        assertThat(BrowseService.capped(5_000, 100, 100)).isFalse();
    }

    @Test
    void anExportThatReachedItsSizeLimitIsCutShort() {
        assertThat(BrowseService.cutShort(100_000, 100_000)).isTrue();
        assertThat(BrowseService.cutShort(99_999, 100_000)).isFalse();
        assertThat(BrowseService.cutShort(0, 100_000)).isFalse();
    }
}
