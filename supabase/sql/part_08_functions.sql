-- ============================================================================
-- Madinat Al-Tayibin — Phase 4 Supabase Schema
-- Part 08: Public Projection Sync Triggers & Transactional Admin RPCs
-- ============================================================================

-- Rebuilds the sanitized public row for a given house_id.
-- NEVER copies unapproved phone/email, additional_phone, or private_notes.
CREATE OR REPLACE FUNCTION public.refresh_house_public_row(p_house_id text)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_house public.houses%ROWTYPE;
    v_priv public.house_private_details%ROWTYPE;
    v_has_priv boolean := false;
    v_approved_phone text := NULL;
    v_approved_email text := NULL;
BEGIN
    SELECT * INTO v_house FROM public.houses WHERE id = p_house_id;
    IF NOT FOUND THEN
        DELETE FROM public.house_public_details WHERE house_id = p_house_id;
        RETURN;
    END IF;

    SELECT * INTO v_priv FROM public.house_private_details WHERE house_id = p_house_id;
    v_has_priv := FOUND;

    IF v_has_priv THEN
        IF v_priv.allow_phone_public AND nullif(trim(COALESCE(v_priv.phone, '')), '') IS NOT NULL THEN
            v_approved_phone := trim(v_priv.phone);
        END IF;
        IF v_priv.allow_email_public AND nullif(trim(COALESCE(v_priv.email, '')), '') IS NOT NULL THEN
            v_approved_email := trim(v_priv.email);
        END IF;
    END IF;

    INSERT INTO public.house_public_details (
        house_id, plot_id, resident_name, country_code, country,
        flag_stripes, flag_vertical, family_size, house_color, palette_json,
        grid_x, grid_y, public_role, public_about,
        approved_phone, approved_email, updated_at
    ) VALUES (
        v_house.id,
        v_house.plot_id,
        v_house.resident_name,
        v_house.country_code,
        v_house.country,
        v_house.flag_stripes,
        v_house.flag_vertical,
        CASE WHEN v_house.show_family_count_publicly THEN v_house.family_size ELSE NULL END,
        v_house.house_color,
        v_house.palette_json,
        v_house.grid_x,
        v_house.grid_y,
        CASE WHEN v_house.allow_public_info THEN nullif(trim(COALESCE(v_house.public_role, '')), '') ELSE NULL END,
        CASE WHEN v_house.allow_public_info THEN nullif(trim(COALESCE(v_house.public_about, '')), '') ELSE NULL END,
        v_approved_phone,
        v_approved_email,
        now()
    )
    ON CONFLICT (house_id) DO UPDATE SET
        plot_id = EXCLUDED.plot_id,
        resident_name = EXCLUDED.resident_name,
        country_code = EXCLUDED.country_code,
        country = EXCLUDED.country,
        flag_stripes = EXCLUDED.flag_stripes,
        flag_vertical = EXCLUDED.flag_vertical,
        family_size = EXCLUDED.family_size,
        house_color = EXCLUDED.house_color,
        palette_json = EXCLUDED.palette_json,
        grid_x = EXCLUDED.grid_x,
        grid_y = EXCLUDED.grid_y,
        public_role = EXCLUDED.public_role,
        public_about = EXCLUDED.public_about,
        approved_phone = EXCLUDED.approved_phone,
        approved_email = EXCLUDED.approved_email,
        updated_at = now();
END;
$$;

CREATE OR REPLACE FUNCTION public.trg_sync_public_from_houses()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        DELETE FROM public.house_public_details WHERE house_id = OLD.id;
        RETURN OLD;
    END IF;
    PERFORM public.refresh_house_public_row(NEW.id);
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_houses_sync_public ON public.houses;
CREATE TRIGGER trg_houses_sync_public
    AFTER INSERT OR UPDATE OR DELETE ON public.houses
    FOR EACH ROW
    EXECUTE FUNCTION public.trg_sync_public_from_houses();

CREATE OR REPLACE FUNCTION public.trg_sync_public_from_private()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        PERFORM public.refresh_house_public_row(OLD.house_id);
        RETURN OLD;
    END IF;
    PERFORM public.refresh_house_public_row(NEW.house_id);
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_private_sync_public ON public.house_private_details;
CREATE TRIGGER trg_private_sync_public
    AFTER INSERT OR UPDATE OR DELETE ON public.house_private_details
    FOR EACH ROW
    EXECUTE FUNCTION public.trg_sync_public_from_private();

-- ----------------------------------------------------------------------------
-- Transactional Admin RPCs (All verify public.is_admin() server-side)
-- ----------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION public.admin_add_house(
    p_target_plot_id text,
    p_resident_name text,
    p_country_code text,
    p_country text,
    p_flag_stripes jsonb,
    p_flag_vertical boolean,
    p_family_size integer,
    p_house_color text,
    p_palette_json jsonb,
    p_public_role text DEFAULT NULL,
    p_public_about text DEFAULT NULL,
    p_allow_public_info boolean DEFAULT true,
    p_show_family_count_publicly boolean DEFAULT true,
    p_phone text DEFAULT NULL,
    p_email text DEFAULT NULL,
    p_additional_phone text DEFAULT NULL,
    p_private_notes text DEFAULT NULL,
    p_allow_phone_public boolean DEFAULT false,
    p_allow_email_public boolean DEFAULT false
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_plot public.plots%ROWTYPE;
    v_house_id text;
BEGIN
    IF NOT public.is_admin() THEN
        RAISE EXCEPTION 'صلاحيات المدير مطلوبة لتنفيذ هذه العملية.' USING ERRCODE = '42501';
    END IF;

    SELECT * INTO v_plot FROM public.plots WHERE id = p_target_plot_id FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'قطعة الأرض % غير موجودة.', p_target_plot_id USING ERRCODE = '23503';
    END IF;

    IF v_plot.status <> 'AVAILABLE' THEN
        RAISE EXCEPTION 'قطعة الأرض % مشغولة بالفعل.', p_target_plot_id USING ERRCODE = '23514';
    END IF;

    v_house_id := 'house_' || replace(gen_random_uuid()::text, '-', '');

    INSERT INTO public.houses (
        id, plot_id, resident_name, country_code, country,
        flag_stripes, flag_vertical, family_size, house_color, palette_json,
        grid_x, grid_y, public_role, public_about, allow_public_info, show_family_count_publicly
    ) VALUES (
        v_house_id,
        v_plot.id,
        trim(p_resident_name),
        trim(p_country_code),
        trim(p_country),
        COALESCE(p_flag_stripes, '[]'::jsonb),
        COALESCE(p_flag_vertical, false),
        p_family_size,
        trim(p_house_color),
        COALESCE(p_palette_json, '{}'::jsonb),
        v_plot.grid_x,
        v_plot.grid_y,
        nullif(trim(COALESCE(p_public_role, '')), ''),
        nullif(trim(COALESCE(p_public_about, '')), ''),
        COALESCE(p_allow_public_info, true),
        COALESCE(p_show_family_count_publicly, true)
    );

    INSERT INTO public.house_private_details (
        house_id, phone, email, additional_phone, private_notes,
        allow_phone_public, allow_email_public
    ) VALUES (
        v_house_id,
        nullif(trim(COALESCE(p_phone, '')), ''),
        nullif(trim(COALESCE(p_email, '')), ''),
        nullif(trim(COALESCE(p_additional_phone, '')), ''),
        nullif(trim(COALESCE(p_private_notes, '')), ''),
        COALESCE(p_allow_phone_public, false),
        COALESCE(p_allow_email_public, false)
    );

    UPDATE public.plots
    SET status = 'OCCUPIED'
    WHERE id = v_plot.id;

    RETURN jsonb_build_object(
        'house_id', v_house_id,
        'plot_id', v_plot.id
    );
END;
$$;

CREATE OR REPLACE FUNCTION public.admin_update_house(
    p_house_id text,
    p_resident_name text,
    p_country_code text,
    p_country text,
    p_flag_stripes jsonb,
    p_flag_vertical boolean,
    p_family_size integer,
    p_house_color text,
    p_palette_json jsonb,
    p_public_role text DEFAULT NULL,
    p_public_about text DEFAULT NULL,
    p_allow_public_info boolean DEFAULT true,
    p_show_family_count_publicly boolean DEFAULT true,
    p_phone text DEFAULT NULL,
    p_email text DEFAULT NULL,
    p_additional_phone text DEFAULT NULL,
    p_private_notes text DEFAULT NULL,
    p_allow_phone_public boolean DEFAULT false,
    p_allow_email_public boolean DEFAULT false
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_house public.houses%ROWTYPE;
BEGIN
    IF NOT public.is_admin() THEN
        RAISE EXCEPTION 'صلاحيات المدير مطلوبة لتنفيذ هذه العملية.' USING ERRCODE = '42501';
    END IF;

    SELECT * INTO v_house FROM public.houses WHERE id = p_house_id FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'البيت % غير موجود.', p_house_id USING ERRCODE = 'P0002';
    END IF;

    UPDATE public.houses
    SET resident_name = trim(p_resident_name),
        country_code = trim(p_country_code),
        country = trim(p_country),
        flag_stripes = COALESCE(p_flag_stripes, flag_stripes),
        flag_vertical = COALESCE(p_flag_vertical, flag_vertical),
        family_size = p_family_size,
        house_color = trim(p_house_color),
        palette_json = COALESCE(p_palette_json, palette_json),
        public_role = nullif(trim(COALESCE(p_public_role, '')), ''),
        public_about = nullif(trim(COALESCE(p_public_about, '')), ''),
        allow_public_info = COALESCE(p_allow_public_info, allow_public_info),
        show_family_count_publicly = COALESCE(p_show_family_count_publicly, show_family_count_publicly)
    WHERE id = p_house_id;

    INSERT INTO public.house_private_details (
        house_id, phone, email, additional_phone, private_notes,
        allow_phone_public, allow_email_public
    ) VALUES (
        p_house_id,
        nullif(trim(COALESCE(p_phone, '')), ''),
        nullif(trim(COALESCE(p_email, '')), ''),
        nullif(trim(COALESCE(p_additional_phone, '')), ''),
        nullif(trim(COALESCE(p_private_notes, '')), ''),
        COALESCE(p_allow_phone_public, false),
        COALESCE(p_allow_email_public, false)
    )
    ON CONFLICT (house_id) DO UPDATE SET
        phone = EXCLUDED.phone,
        email = EXCLUDED.email,
        additional_phone = EXCLUDED.additional_phone,
        private_notes = EXCLUDED.private_notes,
        allow_phone_public = EXCLUDED.allow_phone_public,
        allow_email_public = EXCLUDED.allow_email_public;

    RETURN jsonb_build_object(
        'house_id', p_house_id,
        'plot_id', v_house.plot_id
    );
END;
$$;

CREATE OR REPLACE FUNCTION public.admin_change_house_color(
    p_house_id text,
    p_house_color text,
    p_palette_json jsonb
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_plot_id text;
BEGIN
    IF NOT public.is_admin() THEN
        RAISE EXCEPTION 'صلاحيات المدير مطلوبة لتنفيذ هذه العملية.' USING ERRCODE = '42501';
    END IF;

    UPDATE public.houses
    SET house_color = trim(p_house_color),
        palette_json = COALESCE(p_palette_json, palette_json)
    WHERE id = p_house_id
    RETURNING plot_id INTO v_plot_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'البيت % غير موجود.', p_house_id USING ERRCODE = 'P0002';
    END IF;

    RETURN jsonb_build_object(
        'house_id', p_house_id,
        'plot_id', v_plot_id
    );
END;
$$;

CREATE OR REPLACE FUNCTION public.admin_move_house(
    p_house_id text,
    p_target_plot_id text
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_house public.houses%ROWTYPE;
    v_old_plot_id text;
    v_target_plot public.plots%ROWTYPE;
BEGIN
    IF NOT public.is_admin() THEN
        RAISE EXCEPTION 'صلاحيات المدير مطلوبة لتنفيذ هذه العملية.' USING ERRCODE = '42501';
    END IF;

    SELECT * INTO v_house FROM public.houses WHERE id = p_house_id FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'البيت % غير موجود.', p_house_id USING ERRCODE = 'P0002';
    END IF;

    v_old_plot_id := v_house.plot_id;
    IF v_old_plot_id = p_target_plot_id THEN
        RAISE EXCEPTION 'البيت موجود بالفعل على القطعة %.', p_target_plot_id USING ERRCODE = '23514';
    END IF;

    -- Lock plots in deterministic order to avoid deadlocks
    IF v_old_plot_id < p_target_plot_id THEN
        PERFORM 1 FROM public.plots WHERE id = v_old_plot_id FOR UPDATE;
        SELECT * INTO v_target_plot FROM public.plots WHERE id = p_target_plot_id FOR UPDATE;
    ELSE
        SELECT * INTO v_target_plot FROM public.plots WHERE id = p_target_plot_id FOR UPDATE;
        PERFORM 1 FROM public.plots WHERE id = v_old_plot_id FOR UPDATE;
    END IF;

    IF v_target_plot.id IS NULL THEN
        RAISE EXCEPTION 'القطعة المستهدفة % غير موجودة.', p_target_plot_id USING ERRCODE = '23503';
    END IF;

    IF v_target_plot.status <> 'AVAILABLE' THEN
        RAISE EXCEPTION 'القطعة المستهدفة % ليست متاحة.', p_target_plot_id USING ERRCODE = '23514';
    END IF;

    UPDATE public.houses
    SET plot_id = v_target_plot.id,
        grid_x = v_target_plot.grid_x,
        grid_y = v_target_plot.grid_y
    WHERE id = p_house_id;

    UPDATE public.plots SET status = 'AVAILABLE' WHERE id = v_old_plot_id;
    UPDATE public.plots SET status = 'OCCUPIED' WHERE id = v_target_plot.id;

    RETURN jsonb_build_object(
        'house_id', p_house_id,
        'old_plot_id', v_old_plot_id,
        'plot_id', v_target_plot.id
    );
END;
$$;

CREATE OR REPLACE FUNCTION public.admin_delete_house(
    p_house_id text
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_plot_id text;
BEGIN
    IF NOT public.is_admin() THEN
        RAISE EXCEPTION 'صلاحيات المدير مطلوبة لتنفيذ هذه العملية.' USING ERRCODE = '42501';
    END IF;

    DELETE FROM public.houses
    WHERE id = p_house_id
    RETURNING plot_id INTO v_plot_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'البيت % غير موجود.', p_house_id USING ERRCODE = 'P0002';
    END IF;

    UPDATE public.plots
    SET status = 'AVAILABLE'
    WHERE id = v_plot_id;

    RETURN jsonb_build_object(
        'house_id', p_house_id,
        'plot_id', v_plot_id
    );
END;
$$;

-- Server-only bootstrap helper to promote an existing Supabase Auth user to ADMIN.
-- Must be run from Supabase SQL Editor (postgres role), never callable by anon/authenticated.
CREATE OR REPLACE FUNCTION public.promote_user_to_admin(p_email text)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth
AS $$
DECLARE
    v_user_id uuid;
BEGIN
    SELECT id INTO v_user_id
    FROM auth.users
    WHERE lower(email) = lower(trim(p_email))
    LIMIT 1;

    IF v_user_id IS NULL THEN
        RAISE EXCEPTION 'Auth user with email % not found.', p_email;
    END IF;

    INSERT INTO public.profiles (id, display_name, role)
    VALUES (v_user_id, split_part(trim(p_email), '@', 1), 'ADMIN')
    ON CONFLICT (id) DO UPDATE SET role = 'ADMIN';
END;
$$;

REVOKE ALL ON FUNCTION public.refresh_house_public_row(text) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.promote_user_to_admin(text) FROM PUBLIC, anon, authenticated;

GRANT EXECUTE ON FUNCTION public.admin_add_house(
    text, text, text, text, jsonb, boolean, integer, text, jsonb,
    text, text, boolean, boolean, text, text, text, text, boolean, boolean
) TO anon, authenticated;

GRANT EXECUTE ON FUNCTION public.admin_update_house(
    text, text, text, text, jsonb, boolean, integer, text, jsonb,
    text, text, boolean, boolean, text, text, text, text, boolean, boolean
) TO anon, authenticated;

GRANT EXECUTE ON FUNCTION public.admin_change_house_color(text, text, jsonb) TO anon, authenticated;
GRANT EXECUTE ON FUNCTION public.admin_move_house(text, text) TO anon, authenticated;
GRANT EXECUTE ON FUNCTION public.admin_delete_house(text) TO anon, authenticated;
