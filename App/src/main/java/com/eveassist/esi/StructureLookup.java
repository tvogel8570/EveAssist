package com.eveassist.esi;

/**
 * What ESI said about one player structure, from {@code GET /universe/structures/{structure_id}}. A structure's name
 * is only given to a character that may dock there, so "no access" is an ordinary answer, not a failure.
 *
 * @param name          the structure's name, only when {@code status} is {@code FOUND}
 * @param solarSystemId the system it is in, only when {@code status} is {@code FOUND}
 * @param problem       a short reason when {@code status} is {@code FAILED}
 */
public record StructureLookup(Status status, String name, int solarSystemId, String problem) {

    public enum Status {
        FOUND,
        /** ESI answered 403: this character may not dock there. Each such answer counts toward ESI's error limit. */
        NO_ACCESS,
        /** ESI answered 404: the structure no longer exists, or the id is not a structure. */
        NOT_FOUND,
        /** ESI could not be asked or did not answer well: throttled, down, or the character has no usable token. */
        FAILED
    }

    public static StructureLookup found(String name, int solarSystemId) {
        return new StructureLookup(Status.FOUND, name, solarSystemId, null);
    }

    public static StructureLookup noAccess() {
        return new StructureLookup(Status.NO_ACCESS, null, 0, null);
    }

    public static StructureLookup notFound() {
        return new StructureLookup(Status.NOT_FOUND, null, 0, null);
    }

    public static StructureLookup failed(String problem) {
        return new StructureLookup(Status.FAILED, null, 0, problem);
    }
}
