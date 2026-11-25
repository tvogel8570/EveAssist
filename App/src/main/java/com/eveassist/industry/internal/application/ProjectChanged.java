package com.eveassist.industry.internal.application;

import com.eveassist.industry.ProjectId;

/** What an edit gives back: which project, and the version it now has, which the next edit has to quote. */
public record ProjectChanged(ProjectId project, long version) {
}
