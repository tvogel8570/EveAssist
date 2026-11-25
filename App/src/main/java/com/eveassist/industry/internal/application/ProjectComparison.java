package com.eveassist.industry.internal.application;

import com.eveassist.identity.Viewer;
import com.eveassist.industry.ProjectId;
import com.eveassist.industry.internal.domain.ProjectStatus;
import com.eveassist.sde.TypeId;
import java.util.ArrayList;
import java.util.List;
import org.jmolecules.architecture.hexagonal.PrimaryPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A costing laid out like the game's Industry window, to compare with it (layouts A and B of the mockup). It is made from the costing's stored trace and
 * works nothing out again. Which costing is asked for: a quote's when {@code quoteNumber} is given, and otherwise the Draft's latest or, for a Quoted
 * project, its latest quote's, which is frozen. A project with no costing gives a view with no jobs. Another account's project looks missing.
 */
@Service
@PrimaryPort
@Transactional(readOnly = true)
public class ProjectComparison {

    private final ProjectQueries queries;
    private final ProjectCostings costings;
    private final ProjectQuoteRepository quotes;
    private final ProjectRecipes recipes;

    ProjectComparison(ProjectQueries queries, ProjectCostings costings, ProjectQuoteRepository quotes, ProjectRecipes recipes) {
        this.queries = queries;
        this.costings = costings;
        this.quotes = quotes;
        this.recipes = recipes;
    }

    public CompareView of(Viewer viewer, ProjectId id, Integer quoteNumber) {
        var details = queries.details(viewer, id);
        var number = quoteNumber;
        if (number == null && details.status() == ProjectStatus.QUOTED && !details.quotes().isEmpty()) {
            number = details.quotes().getLast().number();
        }
        TraceDocumentV1 trace;
        CompareView.Source source;
        boolean outOfDate = false;
        String key;
        if (number != null) {
            quotes.find(viewer, id, number).orElseThrow(QuoteNotFoundException::new);
            trace = quotes.findTrace(viewer, id, number).orElseThrow(QuoteNotFoundException::new);
            source = CompareView.Source.QUOTE;
            key = "quote-" + number;
        } else {
            var found = costings.latestTrace(viewer, id);
            if (found.isEmpty()) {
                return new CompareView(id, details.name(), CompareView.Source.DRAFT, 0, null, false, "none", List.of());
            }
            trace = found.get();
            source = CompareView.Source.DRAFT;
            outOfDate = details.costing() != null && !details.costing().current();
            // a new costing is a new key, so what was typed against an older one is not shown against it
            key = "draft-" + trace.data().costedAt().toEpochMilli();
        }
        var jobs = new ArrayList<CompareView.Job>();
        for (var job : trace.jobs()) {
            jobs.add(JobComparisons.of(jobs.size(), job, job.runs() * job.unitsPerRun(), type -> recipes.name(new TypeId(type))));
        }
        return new CompareView(id, details.name(), source, number == null ? 0 : number, trace.data().costedAt(), outOfDate, key, jobs);
    }
}
