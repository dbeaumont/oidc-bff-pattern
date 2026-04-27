CREATE TABLE items (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    description TEXT
);

INSERT INTO items (name, description) VALUES
    ('Item 1', 'Premier item de démonstration'),
    ('Item 2', 'Deuxième item de démonstration');
