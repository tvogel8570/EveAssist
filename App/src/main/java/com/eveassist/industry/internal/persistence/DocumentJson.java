package com.eveassist.industry.internal.persistence;

import com.eveassist.industry.internal.application.CostingDocumentV1;
import com.eveassist.industry.internal.application.QuoteInputsV1;
import com.eveassist.industry.internal.application.TraceDocumentV1;
import com.eveassist.industry.internal.application.UnreadableDocumentException;
import tools.jackson.core.StreamWriteFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * The JSON of the stored documents (a costing, its trace, a quote's inputs). The documents are plain records, so they are written and
 * read as they are. The mapper is this one's own, and not the application's shared one, so that what is stored does not change when
 * someone changes how the application writes JSON elsewhere.
 *
 * <p>Two of its settings are insurance and are not what makes the tests pass today: Jackson 3 already ignores a field it does not know
 * (so a document of the same format that gained one is still read), and Postgres {@code jsonb} already stores a number such as
 * {@code 1E+3} as {@code 1000}. They are set so that neither depends on a default staying as it is, or on the column staying
 * {@code jsonb}.
 */
final class DocumentJson {

    private static final JsonMapper JSON = JsonMapper.builder()
            .enable(StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private DocumentJson() {
    }

    /** A costing document, read by its format version. A format no reader knows is refused by name. */
    static CostingDocumentV1 readCosting(int format, String json) {
        if (format != CostingDocumentV1.FORMAT) {
            throw new UnreadableDocumentException("No reader for a costing document of format " + format);
        }
        return read(json, CostingDocumentV1.class);
    }

    static TraceDocumentV1 readTrace(int format, String json) {
        if (format != TraceDocumentV1.FORMAT) {
            throw new UnreadableDocumentException("No reader for a trace document of format " + format);
        }
        return read(json, TraceDocumentV1.class);
    }

    static QuoteInputsV1 readInputs(int format, String json) {
        if (format != QuoteInputsV1.FORMAT) {
            throw new UnreadableDocumentException("No reader for a quote's inputs of format " + format);
        }
        return read(json, QuoteInputsV1.class);
    }

    static String write(Object document) {
        return JSON.writeValueAsString(document);
    }

    static <T> T read(String json, Class<T> type) {
        try {
            return JSON.readValue(json, type);
        } catch (RuntimeException e) {
            throw new UnreadableDocumentException("A stored " + type.getSimpleName() + " cannot be read", e);
        }
    }
}
