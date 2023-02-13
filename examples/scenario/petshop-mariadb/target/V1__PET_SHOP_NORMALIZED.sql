CREATE TABLE pet_count_by_status
(
    id             INTEGER PRIMARY KEY NOT NULL,
    status_name    VARCHAR(255) NOT NULL,
    count          INTEGER NOT NULL
);

INSERT INTO pet_count_by_status (id, status_name, count) VALUES (1, 'sold', 1);
INSERT INTO pet_count_by_status (id, status_name, count) VALUES (2, 'pending', 1);
INSERT INTO pet_count_by_status (id, status_name, count) VALUES (3, 'available', 1);

