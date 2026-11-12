package com.eveassist.identity.internal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Who runs the site and which build it is, for the footer, the privacy page and security.txt. Templates read it as {@code @siteInfo}. */
@Component("siteInfo")
public class SiteInfo {

    private final String operatorName;
    private final String contactEmail;
    private final String commit;

    SiteInfo(@Value("${eveassist.operator.name}") String operatorName,
            @Value("${eveassist.operator.contact-email}") String contactEmail,
            @Value("${eveassist.build.commit:dev}") String commit) {
        this.operatorName = operatorName;
        this.contactEmail = contactEmail;
        this.commit = commit;
    }

    public String getOperatorName() {
        return operatorName;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    /** The short commit id the image was built from, or dev. */
    public String getCommit() {
        return commit;
    }
}
