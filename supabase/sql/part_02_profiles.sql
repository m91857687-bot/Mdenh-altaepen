-- ============================================================================
-- Madinat Al-Tayibin — Phase 4 Supabase Schema
-- Part 02: Admin Profiles, Device Trust Tables & Verification Helpers
-- ============================================================================

CREATE TABLE IF NOT EXISTS public.profiles (
    id uuid PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    display_name text NOT NULL DEFAULT '',
    role text NOT NULL DEFAULT 'VIEWER' CHECK (role IN ('VIEWER', 'ADMIN')),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

DROP TRIGGER IF EXISTS trg_profiles_updated_at ON public.profiles;
CREATE TRIGGER trg_profiles_updated_at
    BEFORE UPDATE ON public.profiles
    FOR EACH ROW
    EXECUTE FUNCTION public.set_updated_at();

-- Automatically provision a VIEWER profile when a new auth user signs up.
-- Role is ALWAYS 'VIEWER'; admin elevation requires direct SQL/service_role action.
CREATE OR REPLACE FUNCTION public.handle_new_auth_user()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    INSERT INTO public.profiles (id, display_name, role)
    VALUES (
        NEW.id,
        COALESCE(NEW.raw_user_meta_data ->> 'display_name', split_part(COALESCE(NEW.email, ''), '@', 1)),
        'VIEWER'
    )
    ON CONFLICT (id) DO NOTHING;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
    AFTER INSERT ON auth.users
    FOR EACH ROW
    EXECUTE FUNCTION public.handle_new_auth_user();

-- Prevent any non-privileged client from escalating their own or another user's role.
CREATE OR REPLACE FUNCTION public.prevent_profile_role_escalation()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    IF NEW.role IS DISTINCT FROM OLD.role THEN
        IF auth.role() IN ('anon', 'authenticated') THEN
            RAISE EXCEPTION 'Modifying profile role is forbidden from client sessions.'
                USING ERRCODE = '42501';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_prevent_profile_role_escalation ON public.profiles;
CREATE TRIGGER trg_prevent_profile_role_escalation
    BEFORE UPDATE ON public.profiles
    FOR EACH ROW
    EXECUTE FUNCTION public.prevent_profile_role_escalation();

-- ----------------------------------------------------------------------------
-- One-Time Device Enrollment & Trusted Admin Devices (SHA-256 hashes only)
-- ----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS public.admin_activation_codes (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code_hash text NOT NULL UNIQUE,
    label text NOT NULL DEFAULT '',
    expires_at timestamptz NOT NULL,
    used_at timestamptz,
    used_by_device_id text,
    is_revoked boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.admin_devices (
    device_id text PRIMARY KEY CHECK (length(trim(device_id)) >= 8),
    device_name text NOT NULL DEFAULT '',
    device_token_hash text NOT NULL UNIQUE,
    status text NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'REVOKED')),
    activation_code_id uuid REFERENCES public.admin_activation_codes(id) ON DELETE SET NULL,
    activated_at timestamptz NOT NULL DEFAULT now(),
    last_verified_at timestamptz NOT NULL DEFAULT now(),
    revoked_at timestamptz,
    revoke_reason text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

DROP TRIGGER IF EXISTS trg_admin_devices_updated_at ON public.admin_devices;
CREATE TRIGGER trg_admin_devices_updated_at
    BEFORE UPDATE ON public.admin_devices
    FOR EACH ROW
    EXECUTE FUNCTION public.set_updated_at();

-- Safely reads an incoming PostgREST HTTP header in lowercase.
CREATE OR REPLACE FUNCTION public.request_header(p_header_name text)
RETURNS text
LANGUAGE plpgsql
STABLE
AS $$
DECLARE
    v_headers_raw text;
    v_headers jsonb;
BEGIN
    v_headers_raw := current_setting('request.headers', true);
    IF v_headers_raw IS NULL OR trim(v_headers_raw) = '' THEN
        RETURN NULL;
    END IF;
    v_headers := v_headers_raw::jsonb;
    RETURN nullif(trim(COALESCE(v_headers ->> lower(p_header_name), '')), '');
EXCEPTION WHEN OTHERS THEN
    RETURN NULL;
END;
$$;

-- Verifies whether the current HTTP request carries a valid, non-revoked Admin device credential.
CREATE OR REPLACE FUNCTION public.is_admin_device()
RETURNS boolean
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public, extensions
AS $$
DECLARE
    v_device_id text;
    v_device_token text;
    v_token_hash text;
BEGIN
    v_device_id := public.request_header('x-admin-device-id');
    v_device_token := public.request_header('x-admin-device-token');
    IF v_device_id IS NULL OR v_device_token IS NULL THEN
        RETURN false;
    END IF;

    v_token_hash := encode(digest(v_device_token, 'sha256'), 'hex');
    RETURN EXISTS (
        SELECT 1
        FROM public.admin_devices d
        WHERE d.device_id = v_device_id
          AND d.status = 'ACTIVE'
          AND d.revoked_at IS NULL
          AND d.device_token_hash = v_token_hash
    );
END;
$$;

-- Canonical server-side admin check used by RLS policies and transactional RPCs.
-- Accepts either an enrolled ACTIVE admin device credential OR an ADMIN profile session.
CREATE OR REPLACE FUNCTION public.is_admin()
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public, extensions
AS $$
    SELECT public.is_admin_device()
        OR EXISTS (
            SELECT 1
            FROM public.profiles
            WHERE id = auth.uid()
              AND role = 'ADMIN'
        );
$$;

REVOKE ALL ON FUNCTION public.request_header(text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.request_header(text) TO anon, authenticated;

REVOKE ALL ON FUNCTION public.is_admin_device() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.is_admin_device() TO anon, authenticated;

REVOKE ALL ON FUNCTION public.is_admin() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.is_admin() TO anon, authenticated;
