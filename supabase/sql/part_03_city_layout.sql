-- ============================================================================
-- Madinat Al-Tayibin — Phase 4 Supabase Schema
-- Part 03: City Layout & Plots Tables
-- ============================================================================

CREATE TABLE IF NOT EXISTS public.city_layout (
    id text PRIMARY KEY,
    version integer NOT NULL DEFAULT 1 CHECK (version >= 1),
    grid_width integer NOT NULL CHECK (grid_width > 0),
    grid_height integer NOT NULL CHECK (grid_height > 0),
    roads jsonb NOT NULL DEFAULT '[]'::jsonb,
    sidewalks jsonb NOT NULL DEFAULT '[]'::jsonb,
    parks jsonb NOT NULL DEFAULT '[]'::jsonb,
    trees jsonb NOT NULL DEFAULT '[]'::jsonb,
    lamp_posts jsonb NOT NULL DEFAULT '[]'::jsonb,
    updated_at timestamptz NOT NULL DEFAULT now()
);

DROP TRIGGER IF EXISTS trg_city_layout_updated_at ON public.city_layout;
CREATE TRIGGER trg_city_layout_updated_at
    BEFORE UPDATE ON public.city_layout
    FOR EACH ROW
    EXECUTE FUNCTION public.set_updated_at();

CREATE TABLE IF NOT EXISTS public.plots (
    id text PRIMARY KEY,
    layout_id text NOT NULL DEFAULT 'main_city' REFERENCES public.city_layout(id) ON DELETE RESTRICT,
    plot_code text NOT NULL,
    grid_x integer NOT NULL CHECK (grid_x >= 0),
    grid_y integer NOT NULL CHECK (grid_y >= 0),
    status text NOT NULL DEFAULT 'AVAILABLE' CHECK (status IN ('AVAILABLE', 'OCCUPIED')),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

DROP TRIGGER IF EXISTS trg_plots_updated_at ON public.plots;
CREATE TRIGGER trg_plots_updated_at
    BEFORE UPDATE ON public.plots
    FOR EACH ROW
    EXECUTE FUNCTION public.set_updated_at();
