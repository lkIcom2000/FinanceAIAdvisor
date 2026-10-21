-- Story 1.3: personas module tables and seeded accounts.
-- Every account uses the demo password "fine-demo" (synthetic personas only; see README).

CREATE TABLE personas (
    id           VARCHAR(64)  PRIMARY KEY,
    display_name VARCHAR(120) NOT NULL
);

CREATE TABLE users (
    username      VARCHAR(64)  PRIMARY KEY,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(16)  NOT NULL CHECK (role IN ('PERSONA', 'ADVISOR', 'RESEARCHER')),
    persona_id    VARCHAR(64)  REFERENCES personas (id),
    -- Only staff accounts carry their own name; persona accounts use personas.display_name.
    display_name  VARCHAR(120),
    CHECK ((role = 'PERSONA') = (persona_id IS NOT NULL)),
    CHECK ((role = 'PERSONA') = (display_name IS NULL))
);

-- simulated_date and fixture_version are filled by the clock (Story 1.4) and fixture (Story 2.1) stories.
CREATE TABLE persona_state (
    persona_id      VARCHAR(64) PRIMARY KEY REFERENCES personas (id),
    simulated_date  DATE,
    fixture_version VARCHAR(64)
);

INSERT INTO personas (id, display_name) VALUES
    ('p-lena', 'Lena'),
    ('p-marco', 'Marco'),
    ('p-jonas', 'Jonas'),
    ('p-petra', 'Petra'),
    ('p-tim', 'Tim'),
    ('p-shortfall', 'Shortfall test persona'),
    ('p-harness', 'Test harness persona');

INSERT INTO persona_state (persona_id)
SELECT id FROM personas;

INSERT INTO users (username, password_hash, role, persona_id)
SELECT id, '{bcrypt}$2a$10$ISgkb5YeLb3ZsR.SOXiHgOxgqBRQYFfUevXp9livWL56y8Lr3BBAi', 'PERSONA', id FROM personas;

INSERT INTO users (username, password_hash, role, persona_id, display_name) VALUES
    ('advisor-kaya', '{bcrypt}$2a$10$ISgkb5YeLb3ZsR.SOXiHgOxgqBRQYFfUevXp9livWL56y8Lr3BBAi', 'ADVISOR', NULL, 'Kaya'),
    ('researcher', '{bcrypt}$2a$10$ISgkb5YeLb3ZsR.SOXiHgOxgqBRQYFfUevXp9livWL56y8Lr3BBAi', 'RESEARCHER', NULL, 'Researcher');
