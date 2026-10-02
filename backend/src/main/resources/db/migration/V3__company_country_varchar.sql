-- Fix-forward migration. V1 declared country as CHAR(2) but the entity maps a String (VARCHAR);
-- Hibernate's ddl-auto=validate refused to start. V1 already ran, and editing an applied migration changes
-- its checksum and Flyway refuses to run, so we correct the column in a NEW migration instead.
ALTER TABLE company ALTER COLUMN country TYPE VARCHAR(2);
