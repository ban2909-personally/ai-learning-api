-- Trusted PostgreSQL extensions; Flyway's database owner must be able to install them.
CREATE EXTENSION IF NOT EXISTS unaccent WITH SCHEMA public;
CREATE EXTENSION IF NOT EXISTS pg_trgm WITH SCHEMA public;

-- Fixed dictionary. Any later dictionary change requires rebuilding dependent indexes.
CREATE FUNCTION public.platform_search_key(value text) RETURNS text
LANGUAGE sql IMMUTABLE STRICT PARALLEL SAFE
SET search_path = pg_catalog, public
AS $$ SELECT lower(public.unaccent('public.unaccent'::regdictionary, value)) $$;

CREATE INDEX idx_users_public_name_search ON users
USING gin (public.platform_search_key(display_name) gin_trgm_ops) WHERE status='ACTIVE';
CREATE INDEX idx_community_space_name_search ON community_spaces
USING gin (public.platform_search_key(name) gin_trgm_ops);
