package archfixture.broken.industry.internal.application;

import archfixture.broken.industry.UnmarkedPort;

/** Calls an interface of its own module's base package without implementing it, and it is no marked port. */
class UnmarkedPortUser {
    UnmarkedPort port;
}
