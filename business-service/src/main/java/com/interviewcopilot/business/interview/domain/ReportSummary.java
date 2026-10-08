package com.interviewcopilot.business.interview.domain;

import java.util.List;

public record ReportSummary(
        String strengthSummary,
        String weaknessSummary,
        List<String> improvementSuggestions,
        String overallComment
) {
    public ReportSummary {
        strengthSummary = DomainChecks.requiredText(strengthSummary, "strengthSummary", 4000);
        weaknessSummary = DomainChecks.requiredText(weaknessSummary, "weaknessSummary", 4000);
        improvementSuggestions = DomainChecks.textList(
                improvementSuggestions, "improvementSuggestions", 1, 20, 1000);
        overallComment = DomainChecks.requiredText(overallComment, "overallComment", 4000);
    }
}
