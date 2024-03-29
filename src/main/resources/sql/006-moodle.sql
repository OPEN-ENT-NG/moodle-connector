CREATE TABLE moodle.disciplines (
  id BIGSERIAL NOT NULL,
  label VARCHAR NOT NULL,
  CONSTRAINT disciplines_pkey PRIMARY KEY (id),
  CONSTRAINT "Uq_discipline_label" UNIQUE (label)
);
CREATE TABLE moodle.levels (
  id BIGSERIAL NOT NULL,
  label VARCHAR NOT NULL,
  CONSTRAINT level_pkey PRIMARY KEY (id),
  CONSTRAINT "Uq_level_label" UNIQUE (label)
);
