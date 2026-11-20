-- ============================================================================
-- Madinat Al-Tayibin — Phase 4 Supabase Schema
-- Part 10: Supabase Realtime Publication Setup
-- ============================================================================

ALTER TABLE public.city_layout REPLICA IDENTITY FULL;
ALTER TABLE public.plots REPLICA IDENTITY FULL;
ALTER TABLE public.houses REPLICA IDENTITY FULL;
ALTER TABLE public.house_public_details REPLICA IDENTITY FULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_publication_tables
        WHERE pubname = 'supabase_realtime' AND schemaname = 'public' AND tablename = 'city_layout'
    ) THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.city_layout;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_publication_tables
        WHERE pubname = 'supabase_realtime' AND schemaname = 'public' AND tablename = 'plots'
    ) THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.plots;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_publication_tables
        WHERE pubname = 'supabase_realtime' AND schemaname = 'public' AND tablename = 'houses'
    ) THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.houses;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_publication_tables
        WHERE pubname = 'supabase_realtime' AND schemaname = 'public' AND tablename = 'house_public_details'
    ) THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.house_public_details;
    END IF;
END $$;
