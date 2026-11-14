package com.eveassist.identity.internal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Who runs the site and which build it is, for the footer, the privacy page and security.txt. Templates read it as {@code @siteInfo}. */
@Component("siteInfo")
public class SiteInfo {

    private final String operatorName;
    private final String contactEmail;
    private final String commit;
    private final String version;

    SiteInfo(@Value("${eveassist.operator.name}") String operatorName,
            @Value("${eveassist.operator.contact-email}") String contactEmail,
            @Value("${eveassist.build.commit:dev}") String commit,
            @Value("${eveassist.build.version:}") String version) {
        this.operatorName = operatorName;
        this.contactEmail = contactEmail;
        this.commit = commit;
        this.version = version;
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

    /** The release version the image was built for (passed to the Docker build), or empty when it was not given, as in a run from source. */
    public String getVersion() {
        return version;
    }
}
