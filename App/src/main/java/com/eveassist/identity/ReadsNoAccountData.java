package com.eveassist.identity;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class in an application package that is not a use case: a helper that reads no account's data, such as one that only asks the SDE.
 * Its public methods therefore take no {@link Viewer}, which every use case's do (documentation/architecture/code-structure.md).
 *
 * <p>The architecture checks refuse a marked class that depends on a repository or on database access. They do not follow what its
 * collaborators do, so mark a class only when nothing it calls can reach an account's records.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ReadsNoAccountData {
}
