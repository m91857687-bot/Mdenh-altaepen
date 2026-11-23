-- ============================================================================
-- Madinat Al-Tayibin — Phase 3 Forward Migration
-- Part 18: Public Building Info, House Public Visibility & Owner Name Privacy
-- ============================================================================

-- 1. Extend city_buildings with optional visitor-facing description and public info
ALTER TABLE public.city_buildings
    ADD COLUMN IF NOT EXISTS description_ar text,
    ADD COLUMN IF NOT EXISTS public_info_ar text;

DROP FUNCTION IF EXISTS public.admin_upsert_public_building(
    text, text, text, text, text, integer, integer, integer, integer, integer, integer, bigint, bigint, text, boolean
);

CREATE OR REPLACE FUNCTION public.admin_upsert_public_building(
    p_building_id text,
    p_layout_id text DEFAULT 'main_city',
    p_district_id text DEFAULT NULL,
    p_name_ar text DEFAULT 'مبنى عام',
    p_building_type text DEFAULT 'CUSTOM_LANDMARK',
    p_grid_x integer DEFAULT 0,
    p_grid_y integer DEFAULT 0,
    p_width_cells integer DEFAULT 1,
    p_height_cells integer DEFAULT 1,
    p_rotation_deg integer DEFAULT 0,
    p_upgrade_level integer DEFAULT 1,
    p_primary_color_hex bigint DEFAULT 4293912536,
    p_accent_color_hex bigint DEFAULT 4281166478,
    p_asset_key text DEFAULT 'building_custom_landmark',
    p_is_lit_at_night boolean DEFAULT true,
    p_description_ar text DEFAULT NULL,
    p_public_info_ar text DEFAULT NULL
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    IF NOT public.is_admin() THEN
        RAISE EXCEPTION 'صلاحيات المدير مطلوبة لتنفيذ هذه العملية.' USING ERRCODE = '42501';
    END IF;

    INSERT INTO public.city_buildings (
        id, layout_id, district_id, name_ar, building_type, grid_x, grid_y,
        width_cells, height_cells, rotation_deg, upgrade_level,
        primary_color_hex, accent_color_hex, asset_key, is_lit_at_night,
        description_ar, public_info_ar
    ) VALUES (
        p_building_id, p_layout_id, p_district_id, trim(p_name_ar), p_building_type,
        p_grid_x, p_grid_y, GREATEST(p_width_cells, 1), GREATEST(p_height_cells, 1),
        p_rotation_deg, LEAST(GREATEST(p_upgrade_level, 1), 5),
        p_primary_color_hex, p_accent_color_hex, p_asset_key, p_is_lit_at_night,
        nullif(trim(COALESCE(p_description_ar, '')), ''),
        nullif(trim(COALESCE(p_public_info_ar, '')), '')
    )
    ON CONFLICT (id) DO UPDATE SET
        district_id = EXCLUDED.district_id,
        name_ar = EXCLUDED.name_ar,
        building_type = EXCLUDED.building_type,
        grid_x = EXCLUDED.grid_x,
        grid_y = EXCLUDED.grid_y,
        width_cells = EXCLUDED.width_cells,
        height_cells = EXCLUDED.height_cells,
        rotation_deg = EXCLUDED.rotation_deg,
        upgrade_level = EXCLUDED.upgrade_level,
        primary_color_hex = EXCLUDED.primary_color_hex,
        accent_color_hex = EXCLUDED.accent_color_hex,
        asset_key = EXCLUDED.asset_key,
        is_lit_at_night = EXCLUDED.is_lit_at_night,
        description_ar = EXCLUDED.description_ar,
        public_info_ar = EXCLUDED.public_info_ar;

    RETURN jsonb_build_object('building_id', p_building_id);
END;
$$;

REVOKE ALL ON FUNCTION public.admin_upsert_public_building(
    text, text, text, text, text, integer, integer, integer, integer, integer, integer, bigint, bigint, text, boolean, text, text
) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.admin_upsert_public_building(
    text, text, text, text, text, integer, integer, integer, integer, integer, integer, bigint, bigint, text, boolean, text, text
) TO anon, authenticated;

-- 2. Extend houses & house_public_details with public title, public status & owner name privacy toggle
ALTER TABLE public.houses
    ADD COLUMN IF NOT EXISTS house_title text,
    ADD COLUMN IF NOT EXISTS public_status text,
    ADD COLUMN IF NOT EXISTS allow_owner_name_public boolean NOT NULL DEFAULT true;

ALTER TABLE public.house_public_details
    ADD COLUMN IF NOT EXISTS house_title text,
    ADD COLUMN IF NOT EXISTS public_status text;

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
        grid_x, grid_y, width_cells, height_cells, rotation_deg,
        design_style, upgrade_level, has_lit_windows, asset_key,
        house_title, public_status,
        public_role, public_about, approved_phone, approved_email, updated_at
    ) VALUES (
        v_house.id,
        v_house.plot_id,
        CASE WHEN v_house.allow_owner_name_public THEN v_house.resident_name ELSE '' END,
        v_house.country_code,
        v_house.country,
        v_house.flag_stripes,
        v_house.flag_vertical,
        CASE WHEN v_house.show_family_count_publicly THEN v_house.family_size ELSE NULL END,
        v_house.house_color,
        v_house.palette_json,
        v_house.grid_x,
        v_house.grid_y,
        v_house.width_cells,
        v_house.height_cells,
        v_house.rotation_deg,
        v_house.design_style,
        v_house.upgrade_level,
        v_house.has_lit_windows,
        v_house.asset_key,
        CASE WHEN v_house.allow_public_info THEN nullif(trim(COALESCE(v_house.house_title, '')), '') ELSE NULL END,
        CASE WHEN v_house.allow_public_info THEN nullif(trim(COALESCE(v_house.public_status, '')), '') ELSE NULL END,
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
        width_cells = EXCLUDED.width_cells,
        height_cells = EXCLUDED.height_cells,
        rotation_deg = EXCLUDED.rotation_deg,
        design_style = EXCLUDED.design_style,
        upgrade_level = EXCLUDED.upgrade_level,
        has_lit_windows = EXCLUDED.has_lit_windows,
        asset_key = EXCLUDED.asset_key,
        house_title = EXCLUDED.house_title,
        public_status = EXCLUDED.public_status,
        public_role = EXCLUDED.public_role,
        public_about = EXCLUDED.public_about,
        approved_phone = EXCLUDED.approved_phone,
        approved_email = EXCLUDED.approved_email,
        updated_at = now();
END;
$$;

REVOKE ALL ON FUNCTION public.refresh_house_public_row(text) FROM PUBLIC, anon, authenticated;

CREATE OR REPLACE FUNCTION public.admin_set_house_public_visibility(
    p_house_id text,
    p_house_title text DEFAULT NULL,
    p_public_status text DEFAULT NULL,
    p_allow_owner_name_public boolean DEFAULT true
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    IF NOT public.is_admin() THEN
        RAISE EXCEPTION 'صلاحيات المدير مطلوبة لتحديث ظهور بيانات البيت.' USING ERRCODE = '42501';
    END IF;

    UPDATE public.houses
    SET house_title = nullif(trim(COALESCE(p_house_title, '')), ''),
        public_status = nullif(trim(COALESCE(p_public_status, '')), ''),
        allow_owner_name_public = COALESCE(p_allow_owner_name_public, true)
    WHERE id = p_house_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'البيت غير موجود.' USING ERRCODE = 'P0002';
    END IF;

    PERFORM public.refresh_house_public_row(p_house_id);

    RETURN jsonb_build_object('house_id', p_house_id);
END;
$$;

REVOKE ALL ON FUNCTION public.admin_set_house_public_visibility(text, text, text, boolean) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.admin_set_house_public_visibility(text, text, text, boolean) TO anon, authenticated;
