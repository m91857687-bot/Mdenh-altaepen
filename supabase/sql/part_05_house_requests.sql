-- ============================================================================
-- Madinat Al-Tayibin — Phase 4 Supabase Schema
-- Part 05: House Requests Table (Prepared for Phase 5 — No UI in Phase 4)
-- ============================================================================

CREATE TABLE IF NOT EXISTS public.house_requests (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    applicant_name text NOT NULL CHECK (char_length(trim(applicant_name)) > 0),
    phone text NOT NULL CHECK (char_length(trim(phone)) > 0),
    email text,
    country text NOT NULL CHECK (char_length(trim(country)) > 0),
    family_size integer NOT NULL CHECK (family_size > 0),
    additional_phone text,
    social_accounts jsonb NOT NULL DEFAULT '{}'::jsonb,
    notes text,
    privacy_consent boolean NOT NULL DEFAULT false,
    status text NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    created_at timestamptz NOT NULL DEFAULT now(),
    reviewed_at timestamptz,
    reviewed_by uuid REFERENCES public.profiles(id) ON DELETE SET NULL
);
