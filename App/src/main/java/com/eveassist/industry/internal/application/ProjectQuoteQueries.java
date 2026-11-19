package com.eveassist.industry.internal.application;

import com.eveassist.identity.Viewer;
import com.eveassist.industry.ProjectId;
import java.util.List;
import org.jmolecules.architecture.hexagonal.PrimaryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads of a project's quotes. They change nothing. */
@Service
@PrimaryPort
@Transactional(readOnly = true)
public class ProjectQuoteQueries {

    private final ProjectRepository projects;
    private final ProjectQuoteRepository quotes;

    ProjectQuoteQueries(ProjectRepository projects, ProjectQuoteRepository quotes) {
        this.projects = projects;
        this.quotes = quotes;
    }

    /** The project's quotes, oldest first. */
    public List<ProjectQuoteRepository.QuoteSummary> list(Viewer viewer, ProjectId project) {
        projects.find(viewer, project).orElseThrow(ProjectNotFoundException::new);
        return quotes.list(viewer, project);
    }

    /** One quote with its frozen costing. */
    public QuoteView quote(Viewer viewer, ProjectId project, int number) {
        var summary = quotes.find(viewer, project, number).orElseThrow(QuoteNotFoundException::new);
        var costing = quotes.findCosting(viewer, project, number).orElseThrow(QuoteNotFoundException::new);
        return new QuoteView(summary, costing);
    }

    /** The trace of one quote's costing. */
    public TraceDocumentV1 trace(Viewer viewer, ProjectId project, int number) {
        return quotes.findTrace(viewer, project, number).orElseThrow(QuoteNotFoundException::new);
    }
}
