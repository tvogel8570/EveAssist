package com.eveassist.industry.internal.web;

import com.eveassist.industry.internal.application.ProjectOverview;
import com.eveassist.industry.internal.domain.ProjectStatus;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * What a project is called on the page. The domain knows two states, Draft and Quoted. The page adds two that it works out: a Draft
 * that has been costed is <em>Costed</em>, and a Quoted project whose latest quote has run out is <em>Expired</em>.
 */
@Component("projectStates")
class ProjectStates {

    /** The names a state can have, in the order of the list's filter. They are also the message keys {@code projects.state.*}. */
    static final java.util.List<String> ALL = java.util.List.of("draft", "costed", "quoted", "expired");

    private final Clock clock;

    ProjectStates(Clock clock) {
        this.clock = clock;
    }

    public String of(ProjectOverview project) {
        return of(project.status(), project.costedAt() != null, project.latestQuoteExpiresAt());
    }

    String of(ProjectStatus status, boolean costed, Instant latestQuoteExpiresAt) {
        if (status == ProjectStatus.QUOTED) {
            return latestQuoteExpiresAt != null && latestQuoteExpiresAt.isBefore(clock.instant()) ? "expired" : "quoted";
        }
        return costed ? "costed" : "draft";
    }

    /** Whole days from now until the quote runs out, negative when it already has. Null when there is no expiry. */
    public Long daysLeft(Instant expiresAt) {
        if (expiresAt == null) {
            return null;
        }
        return java.time.Duration.between(clock.instant(), expiresAt).toDays();
    }
}
