-- ============================================================================
-- Madinat Al-Tayibin — Phase 4 Supabase Schema
-- Part 09: Row Level Security (RLS) Policies & Least-Privilege Grants
-- ============================================================================

-- Enable RLS on every table
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.city_layout ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.plots ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.houses ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.house_private_details ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.house_public_details ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.house_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.admin_activation_codes ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.admin_devices ENABLE ROW LEVEL SECURITY;

-- Ensure clean baseline privileges
REVOKE ALL ON public.profiles FROM PUBLIC, anon, authenticated;
REVOKE ALL ON public.city_layout FROM PUBLIC, anon, authenticated;
REVOKE ALL ON public.plots FROM PUBLIC, anon, authenticated;
REVOKE ALL ON public.houses FROM PUBLIC, anon, authenticated;
REVOKE ALL ON public.house_private_details FROM PUBLIC, anon, authenticated;
REVOKE ALL ON public.house_public_details FROM PUBLIC, anon, authenticated;
REVOKE ALL ON public.house_requests FROM PUBLIC, anon, authenticated;
REVOKE ALL ON public.admin_activation_codes FROM PUBLIC, anon, authenticated;
REVOKE ALL ON public.admin_devices FROM PUBLIC, anon, authenticated;

-- Visitors (anon) & authenticated users can read ONLY public layout, plots, and sanitized public details
GRANT SELECT ON public.city_layout TO anon, authenticated;
GRANT SELECT ON public.plots TO anon, authenticated;
GRANT SELECT ON public.house_public_details TO anon, authenticated;

-- Authenticated profiles read-own
GRANT SELECT ON public.profiles TO authenticated;

-- Admin-guarded table grants (every row & action strictly enforced by public.is_admin() RLS)
GRANT SELECT, INSERT, UPDATE, DELETE ON public.city_layout TO anon, authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.plots TO anon, authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.houses TO anon, authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.house_private_details TO anon, authenticated;
GRANT SELECT, UPDATE, DELETE ON public.house_requests TO anon, authenticated;

-- 1. profiles policies
DROP POLICY IF EXISTS profiles_select_own_or_admin ON public.profiles;
CREATE POLICY profiles_select_own_or_admin
    ON public.profiles FOR SELECT
    TO authenticated
    USING (auth.uid() = id OR public.is_admin());

-- 2. city_layout policies
DROP POLICY IF EXISTS city_layout_public_read ON public.city_layout;
CREATE POLICY city_layout_public_read
    ON public.city_layout FOR SELECT
    TO anon, authenticated
    USING (true);

DROP POLICY IF EXISTS city_layout_admin_device_write ON public.city_layout;
DROP POLICY IF EXISTS city_layout_admin_write ON public.city_layout;
CREATE POLICY city_layout_admin_write
    ON public.city_layout FOR ALL
    TO anon, authenticated
    USING (public.is_admin())
    WITH CHECK (public.is_admin());

-- 3. plots policies
DROP POLICY IF EXISTS plots_public_read ON public.plots;
CREATE POLICY plots_public_read
    ON public.plots FOR SELECT
    TO anon, authenticated
    USING (true);

DROP POLICY IF EXISTS plots_admin_device_write ON public.plots;
DROP POLICY IF EXISTS plots_admin_write ON public.plots;
CREATE POLICY plots_admin_write
    ON public.plots FOR ALL
    TO anon, authenticated
    USING (public.is_admin())
    WITH CHECK (public.is_admin());

-- 4. houses policies (Admin-only via verified device or ADMIN profile; visitors read house_public_details)
DROP POLICY IF EXISTS houses_admin_all ON public.houses;
CREATE POLICY houses_admin_all
    ON public.houses FOR ALL
    TO anon, authenticated
    USING (public.is_admin())
    WITH CHECK (public.is_admin());

-- 5. house_private_details policies (Strictly Admin-only)
DROP POLICY IF EXISTS house_private_admin_all ON public.house_private_details;
CREATE POLICY house_private_admin_all
    ON public.house_private_details FOR ALL
    TO anon, authenticated
    USING (public.is_admin())
    WITH CHECK (public.is_admin());

-- 6. house_public_details policies (Public read-only; mutated exclusively via DB triggers)
DROP POLICY IF EXISTS house_public_read ON public.house_public_details;
CREATE POLICY house_public_read
    ON public.house_public_details FOR SELECT
    TO anon, authenticated
    USING (true);

-- 7. house_requests policies (Admin-only in Phase 4; guest submission opens in Phase 5)
DROP POLICY IF EXISTS house_requests_admin_all ON public.house_requests;
CREATE POLICY house_requests_admin_all
    ON public.house_requests FOR ALL
    TO anon, authenticated
    USING (public.is_admin())
    WITH CHECK (public.is_admin());

-- 8. admin_activation_codes & admin_devices policies:
-- ZERO direct client access; managed strictly via SECURITY DEFINER RPCs and SQL Editor.
DROP POLICY IF EXISTS admin_activation_codes_deny_all ON public.admin_activation_codes;
CREATE POLICY admin_activation_codes_deny_all
    ON public.admin_activation_codes FOR ALL
    TO anon, authenticated
    USING (false)
    WITH CHECK (false);

DROP POLICY IF EXISTS admin_devices_deny_all ON public.admin_devices;
CREATE POLICY admin_devices_deny_all
    ON public.admin_devices FOR ALL
    TO anon, authenticated
    USING (false)
    WITH CHECK (false);
