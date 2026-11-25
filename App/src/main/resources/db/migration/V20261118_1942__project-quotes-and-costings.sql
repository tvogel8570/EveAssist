-- Quotes, their costings and the costings' traces (see architecture/projects.md). The JSON documents are written by the application, and
-- each has its own format version, the layout of the document, which is not the project's edit version. A reader looks at the format
-- version before it parses the document.

-- A quote: frozen when it is issued. Its inputs are a snapshot, because the project's own tables move on.
CREATE TABLE project_quote (
    id                   bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    project_id           bigint         NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    -- 1, 2, 3 for each project, never reused after a quote is deleted.
    number               integer        NOT NULL CHECK (number > 0),
    issued_at            timestamptz    NOT NULL DEFAULT now(),
    expires_at           timestamptz,
    buyer_name           text,
    note                 text,
    mode                 text           NOT NULL CHECK (mode IN ('margin', 'market')),
    price                numeric(24, 2) NOT NULL CHECK (price >= 0),
    inputs_format_version smallint      NOT NULL CHECK (inputs_format_version > 0),
    inputs               jsonb          NOT NULL,
    UNIQUE (project_id, number),
    UNIQUE (id, project_id)
);

-- A costing. The Draft has one, replaced each time it is costed (quote_id is null), and each quote has its own, never changed. The
-- project and the quote must agree, which the second foreign key checks.
CREATE TABLE project_costing (
    id                       bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    project_id               bigint         NOT NULL REFERENCES project (id) ON DELETE CASCADE,
    quote_id                 bigint         UNIQUE,
    -- The project's edit version this was costed for, which says when a Draft's costing is out of date.
    costed_project_version   bigint         NOT NULL,
    costed_sde_version       text           NOT NULL,
    prices_as_of             timestamptz,
    costed_at                timestamptz    NOT NULL DEFAULT now(),
    delivered_cost           numeric(24, 2) NOT NULL,
    quotable                 boolean        NOT NULL,
    document_format_version  smallint       NOT NULL CHECK (document_format_version > 0),
    document                 jsonb          NOT NULL,
    FOREIGN KEY (quote_id, project_id) REFERENCES project_quote (id, project_id) ON DELETE CASCADE
);
-- One Draft costing for each project.
CREATE UNIQUE INDEX project_costing_draft_ux ON project_costing (project_id) WHERE quote_id IS NULL;

-- The detail for comparing with the game. Its own table so that the purge of an archived project's traces deletes rows
-- and never changes a frozen quote.
CREATE TABLE project_trace (
    costing_id              bigint      PRIMARY KEY REFERENCES project_costing (id) ON DELETE CASCADE,
    document_format_version smallint    NOT NULL CHECK (document_format_version > 0),
    document                jsonb       NOT NULL,
    created_at              timestamptz NOT NULL DEFAULT now()
);
