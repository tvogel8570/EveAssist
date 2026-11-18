package com.eveassist.esi;

import java.time.Instant;
import java.util.function.Function;

/**
 * What an ESI call gave back. A failure is a value, not an exception, so that a caller can keep stored data and say
 * which part could not be refreshed.
 *
 * @param data    the new data when {@code outcome} is {@code UPDATED}, otherwise null
 * @param expires when ESI's copy changes next, so the caller knows how long this result is good for
 * @param lastModified ESI's {@code Last-Modified} header as sent, or null where ESI sent none. It is not a reliable age: for
 *                     {@code /markets/prices} it has been ahead of ESI's own {@code Date} header (see {@code AdjustedPriceAge})
 * @param problem a short reason when {@code outcome} is {@code FAILED}, otherwise null
 */
public record EsiResult<T>(Outcome outcome, T data, Instant expires, Instant lastModified, String problem) {

    public enum Outcome {
        /** New data came from ESI. */
        UPDATED,
        /** The caller's stored copy is still current: not yet expired, or ESI answered 304. */
        UNCHANGED,
        /** ESI could not be asked or did not answer well: throttled, down, an error or a bad page set. */
        FAILED
    }

    /** The same outcome with the data converted. */
    public <R> EsiResult<R> map(Function<T, R> conversion) {
        return new EsiResult<>(outcome, data == null ? null : conversion.apply(data), expires, lastModified, problem);
    }

    public static <T> EsiResult<T> updated(T data, Instant expires) {
        return updated(data, expires, null);
    }

    public static <T> EsiResult<T> updated(T data, Instant expires, Instant lastModified) {
        return new EsiResult<>(Outcome.UPDATED, data, expires, lastModified, null);
    }

    public static <T> EsiResult<T> unchanged(Instant expires) {
        return unchanged(expires, null);
    }

    public static <T> EsiResult<T> unchanged(Instant expires, Instant lastModified) {
        return new EsiResult<>(Outcome.UNCHANGED, null, expires, lastModified, null);
    }

    public static <T> EsiResult<T> failed(String problem) {
        return new EsiResult<>(Outcome.FAILED, null, null, null, problem);
    }
}
