-- ============================================================================
-- Madinat Al-Tayibin — Phase 4 Supabase Schema
-- Part 04: Houses, Private Details & Sanitized Public Details Projection
-- ============================================================================

CREATE TABLE IF NOT EXISTS public.houses (
    id text PRIMARY KEY,
    plot_id text NOT NULL,
    resident_name text NOT NULL CHECK (char_length(trim(resident_name)) > 0),
    country_code text NOT NULL CHECK (char_length(trim(country_code)) > 0),
    country text NOT NULL CHECK (char_length(trim(country)) > 0),
    flag_stripes jsonb NOT NULL DEFAULT '[]'::jsonb,
    flag_vertical boolean NOT NULL DEFAULT false,
    family_size integer NOT NULL CHECK (family_size > 0),
    house_color text NOT NULL CHECK (char_length(trim(house_color)) > 0),
    palette_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    grid_x integer NOT NULL CHECK (grid_x >= 0),
    grid_y integer NOT NULL CHECK (grid_y >= 0),
    public_role text,
    public_about text,
    allow_public_info boolean NOT NULL DEFAULT true,
    show_family_count_publicly boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

DROP TRIGGER IF EXISTS trg_houses_updated_at ON public.houses;
CREATE TRIGGER trg_houses_updated_at
    BEFORE UPDATE ON public.houses
    FOR EACH ROW
    EXECUTE FUNCTION public.set_updated_at();

-- Strictly admin-only private contact details & notes.
-- Phone and email publication consents are separate and default to FALSE.
CREATE TABLE IF NOT EXISTS public.house_private_details (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    house_id text NOT NULL,
    phone text,
    email text,
    additional_phone text,
    private_notes text,
    allow_phone_public boolean NOT NULL DEFAULT false,
    allow_email_public boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

DROP TRIGGER IF EXISTS trg_house_private_details_updated_at ON public.house_private_details;
CREATE TRIGGER trg_house_private_details_updated_at
    BEFORE UPDATE ON public.house_private_details
    FOR EACH ROW
    EXECUTE FUNCTION public.set_updated_at();

-- Visitor-safe public projection table maintained exclusively by database triggers.
-- Unapproved phone/email, additional_phone, private_notes, and hidden fields are NEVER written here.
CREATE TABLE IF NOT EXISTS public.house_public_details (
    house_id text PRIMARY KEY,
    plot_id text NOT NULL,
    resident_name text NOT NULL,
    country_code text NOT NULL,
    country text NOT NULL,
    flag_stripes jsonb NOT NULL DEFAULT '[]'::jsonb,
    flag_vertical boolean NOT NULL DEFAULT false,
    family_size integer,
    house_color text NOT NULL,
    palette_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    grid_x integer NOT NULL,
    grid_y integer NOT NULL,
    public_role text,
    public_about text,
    approved_phone text,
    approved_email text,
    updated_at timestamptz NOT NULL DEFAULT now()
);
