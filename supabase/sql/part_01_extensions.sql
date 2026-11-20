-- ============================================================================
-- Madinat Al-Tayibin — Phase 4 Supabase Schema
-- Part 01: Extensions & Shared Trigger Helpers
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Automatically updates updated_at columns on row modification
CREATE OR REPLACE FUNCTION public.set_updated_at()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END;
$$;
