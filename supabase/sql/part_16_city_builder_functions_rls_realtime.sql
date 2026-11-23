-- ============================================================================
-- Madinat Al-Tayibin — City Builder Phase 1 Forward Migration
-- Part 16: Updated Public Projection Sync, Admin Builder RPCs, RLS & Realtime
-- ============================================================================

-- 1. Update refresh_house_public_row to propagate architectural size, rotation, style, level, lighting & asset_key
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
        public_role, public_about, approved_phone, approved_email, updated_at
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
        v_house.width_cells,
        v_house.height_cells,
        v_house.rotation_deg,
        v_house.design_style,
        v_house.upgrade_level,
        v_house.has_lit_windows,
        v_house.asset_key,
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
        public_role = EXCLUDED.public_role,
        public_about = EXCLUDED.public_about,
        approved_phone = EXCLUDED.approved_phone,
        approved_email = EXCLUDED.approved_email,
        updated_at = now();
END;
$$;

REVOKE ALL ON FUNCTION public.refresh_house_public_row(text) FROM PUBLIC, anon, authenticated;

-- 2. Admin City Builder RPCs (every RPC strictly requires public.is_admin())

CREATE OR REPLACE FUNCTION public.admin_expand_city_grid(
    p_layout_id text DEFAULT 'main_city',
    p_grid_width integer DEFAULT 24,
    p_grid_height integer DEFAULT 24
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_layout public.city_layout%ROWTYPE;
BEGIN
    IF NOT public.is_admin() THEN
        RAISE EXCEPTION 'صلاحيات المدير مطلوبة لتنفيذ هذه العملية.' USING ERRCODE = '42501';
    END IF;

    SELECT * INTO v_layout FROM public.city_layout WHERE id = p_layout_id FOR UPDATE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'تخطيط المدينة % غير موجود.', p_layout_id USING ERRCODE = 'P0002';
    END IF;

    IF p_grid_width < v_layout.grid_width OR p_grid_height < v_layout.grid_height THEN
        RAISE EXCEPTION 'لا يمكن تقليص أبعاد المدينة عن حجمها الحالي (%x%).', v_layout.grid_width, v_layout.grid_height
            USING ERRCODE = '23514';
    END IF;

    UPDATE public.city_layout
    SET grid_width = p_grid_width,
        grid_height = p_grid_height,
        version = version + 1
    WHERE id = p_layout_id;

    RETURN jsonb_build_object('layout_id', p_layout_id, 'grid_width', p_grid_width, 'grid_height', p_grid_height);
END;
$$;

CREATE OR REPLACE FUNCTION public.admin_upsert_plot(
    p_plot_id text,
    p_layout_id text DEFAULT 'main_city',
    p_plot_code text DEFAULT NULL,
    p_grid_x integer DEFAULT 0,
    p_grid_y integer DEFAULT 0,
    p_width_cells integer DEFAULT 1,
    p_height_cells integer DEFAULT 1,
    p_rotation_deg integer DEFAULT 0,
    p_status text DEFAULT 'AVAILABLE',
    p_district_id text DEFAULT NULL,
    p_ground_type text DEFAULT 'PLOT_LOT',
    p_texture_variant text DEFAULT 'DEFAULT'
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

    INSERT INTO public.plots (
        id, layout_id, plot_code, grid_x, grid_y, width_cells, height_cells,
        rotation_deg, status, district_id, ground_type, texture_variant
    ) VALUES (
        p_plot_id, p_layout_id, COALESCE(nullif(trim(p_plot_code), ''), upper(p_plot_id)),
        p_grid_x, p_grid_y, GREATEST(p_width_cells, 1), GREATEST(p_height_cells, 1),
        p_rotation_deg, p_status, p_district_id, p_ground_type, p_texture_variant
    )
    ON CONFLICT (id) DO UPDATE SET
        plot_code = EXCLUDED.plot_code,
        grid_x = EXCLUDED.grid_x,
        grid_y = EXCLUDED.grid_y,
        width_cells = EXCLUDED.width_cells,
        height_cells = EXCLUDED.height_cells,
        rotation_deg = EXCLUDED.rotation_deg,
        status = EXCLUDED.status,
        district_id = EXCLUDED.district_id,
        ground_type = EXCLUDED.ground_type,
        texture_variant = EXCLUDED.texture_variant;

    RETURN jsonb_build_object('plot_id', p_plot_id);
END;
$$;

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
    p_is_lit_at_night boolean DEFAULT true
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
        primary_color_hex, accent_color_hex, asset_key, is_lit_at_night
    ) VALUES (
        p_building_id, p_layout_id, p_district_id, trim(p_name_ar), p_building_type,
        p_grid_x, p_grid_y, GREATEST(p_width_cells, 1), GREATEST(p_height_cells, 1),
        p_rotation_deg, LEAST(GREATEST(p_upgrade_level, 1), 5),
        p_primary_color_hex, p_accent_color_hex, p_asset_key, p_is_lit_at_night
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
        is_lit_at_night = EXCLUDED.is_lit_at_night;

    RETURN jsonb_build_object('building_id', p_building_id);
END;
$$;

CREATE OR REPLACE FUNCTION public.admin_set_lighting_phase(
    p_layout_id text DEFAULT 'main_city',
    p_lighting_config jsonb DEFAULT '{"phase":"DAY","sunAzimuthDeg":42,"ambientDimming":0,"nightLampAutoOn":true}'::jsonb
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

    UPDATE public.city_layout
    SET lighting_config = COALESCE(p_lighting_config, lighting_config),
        version = version + 1
    WHERE id = p_layout_id;

    RETURN jsonb_build_object('layout_id', p_layout_id);
END;
$$;

-- 3. Row Level Security & Least-Privilege Grants for all new tables
ALTER TABLE public.city_districts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.city_buildings ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.city_environment_props ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.city_lights ENABLE ROW LEVEL SECURITY;

REVOKE ALL ON public.city_districts FROM PUBLIC, anon, authenticated;
REVOKE ALL ON public.city_buildings FROM PUBLIC, anon, authenticated;
REVOKE ALL ON public.city_environment_props FROM PUBLIC, anon, authenticated;
REVOKE ALL ON public.city_lights FROM PUBLIC, anon, authenticated;

GRANT SELECT, INSERT, UPDATE, DELETE ON public.city_districts TO anon, authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.city_buildings TO anon, authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.city_environment_props TO anon, authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON public.city_lights TO anon, authenticated;

DROP POLICY IF EXISTS city_districts_public_read ON public.city_districts;
CREATE POLICY city_districts_public_read
    ON public.city_districts FOR SELECT TO anon, authenticated USING (true);
DROP POLICY IF EXISTS city_districts_admin_write ON public.city_districts;
CREATE POLICY city_districts_admin_write
    ON public.city_districts FOR ALL TO anon, authenticated
    USING (public.is_admin()) WITH CHECK (public.is_admin());

DROP POLICY IF EXISTS city_buildings_public_read ON public.city_buildings;
CREATE POLICY city_buildings_public_read
    ON public.city_buildings FOR SELECT TO anon, authenticated USING (true);
DROP POLICY IF EXISTS city_buildings_admin_write ON public.city_buildings;
CREATE POLICY city_buildings_admin_write
    ON public.city_buildings FOR ALL TO anon, authenticated
    USING (public.is_admin()) WITH CHECK (public.is_admin());

DROP POLICY IF EXISTS city_environment_props_public_read ON public.city_environment_props;
CREATE POLICY city_environment_props_public_read
    ON public.city_environment_props FOR SELECT TO anon, authenticated USING (true);
DROP POLICY IF EXISTS city_environment_props_admin_write ON public.city_environment_props;
CREATE POLICY city_environment_props_admin_write
    ON public.city_environment_props FOR ALL TO anon, authenticated
    USING (public.is_admin()) WITH CHECK (public.is_admin());

DROP POLICY IF EXISTS city_lights_public_read ON public.city_lights;
CREATE POLICY city_lights_public_read
    ON public.city_lights FOR SELECT TO anon, authenticated USING (true);
DROP POLICY IF EXISTS city_lights_admin_write ON public.city_lights;
CREATE POLICY city_lights_admin_write
    ON public.city_lights FOR ALL TO anon, authenticated
    USING (public.is_admin()) WITH CHECK (public.is_admin());

GRANT EXECUTE ON FUNCTION public.admin_expand_city_grid(text, integer, integer) TO anon, authenticated;
GRANT EXECUTE ON FUNCTION public.admin_upsert_plot(text, text, text, integer, integer, integer, integer, integer, text, text, text, text) TO anon, authenticated;
GRANT EXECUTE ON FUNCTION public.admin_upsert_public_building(text, text, text, text, text, integer, integer, integer, integer, integer, integer, bigint, bigint, text, boolean) TO anon, authenticated;
GRANT EXECUTE ON FUNCTION public.admin_set_lighting_phase(text, jsonb) TO anon, authenticated;

-- 4. Enable Realtime replication on new builder tables
ALTER TABLE public.city_districts REPLICA IDENTITY FULL;
ALTER TABLE public.city_buildings REPLICA IDENTITY FULL;
ALTER TABLE public.city_environment_props REPLICA IDENTITY FULL;
ALTER TABLE public.city_lights REPLICA IDENTITY FULL;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_publication_tables WHERE pubname = 'supabase_realtime' AND schemaname = 'public' AND tablename = 'city_districts') THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.city_districts;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_publication_tables WHERE pubname = 'supabase_realtime' AND schemaname = 'public' AND tablename = 'city_buildings') THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.city_buildings;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_publication_tables WHERE pubname = 'supabase_realtime' AND schemaname = 'public' AND tablename = 'city_environment_props') THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.city_environment_props;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_publication_tables WHERE pubname = 'supabase_realtime' AND schemaname = 'public' AND tablename = 'city_lights') THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.city_lights;
    END IF;
END $$;
