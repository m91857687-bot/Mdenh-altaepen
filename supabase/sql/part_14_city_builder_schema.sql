-- ============================================================================
-- Madinat Al-Tayibin — City Builder Phase 1 Forward Migration
-- Part 14: Extended Layout, Districts, Multi-Cell Plots, Public Buildings,
--          Environment Props & City Lighting Schema
-- Note: Forward-only & idempotent. Preserves all existing tables and rows.
-- ============================================================================

-- 1. Extend city_layout with rich surface tiles, road segments, and lighting state
ALTER TABLE public.city_layout
    ADD COLUMN IF NOT EXISTS custom_tiles jsonb NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS road_segments jsonb NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS lighting_config jsonb NOT NULL DEFAULT '{"phase":"DAY","sunAzimuthDeg":42,"ambientDimming":0,"nightLampAutoOn":true}'::jsonb;

-- 2. Extend plots for multi-cell dimensions, rotation, district assignment, ground/texture, and UNAVAILABLE state
ALTER TABLE public.plots
    ADD COLUMN IF NOT EXISTS width_cells integer NOT NULL DEFAULT 1 CHECK (width_cells >= 1),
    ADD COLUMN IF NOT EXISTS height_cells integer NOT NULL DEFAULT 1 CHECK (height_cells >= 1),
    ADD COLUMN IF NOT EXISTS rotation_deg integer NOT NULL DEFAULT 0 CHECK (rotation_deg IN (0, 90, 180, 270)),
    ADD COLUMN IF NOT EXISTS district_id text,
    ADD COLUMN IF NOT EXISTS ground_type text NOT NULL DEFAULT 'PLOT_LOT',
    ADD COLUMN IF NOT EXISTS texture_variant text NOT NULL DEFAULT 'DEFAULT';

DO $$
DECLARE
    v_con text;
BEGIN
    FOR v_con IN
        SELECT c.conname
        FROM pg_constraint c
        WHERE c.conrelid = 'public.plots'::regclass
          AND c.contype = 'c'
          AND pg_get_constraintdef(c.oid) ILIKE '%status%'
    LOOP
        EXECUTE format('ALTER TABLE public.plots DROP CONSTRAINT %I', v_con);
    END LOOP;

    ALTER TABLE public.plots
        ADD CONSTRAINT chk_plots_status CHECK (status IN ('AVAILABLE', 'UNAVAILABLE', 'OCCUPIED'));
END $$;

-- 3. Extend houses & house_public_details with multi-cell sizing, rotation, style, upgrade level, lighting & asset key
ALTER TABLE public.houses
    ADD COLUMN IF NOT EXISTS width_cells integer NOT NULL DEFAULT 1 CHECK (width_cells >= 1),
    ADD COLUMN IF NOT EXISTS height_cells integer NOT NULL DEFAULT 1 CHECK (height_cells >= 1),
    ADD COLUMN IF NOT EXISTS rotation_deg integer NOT NULL DEFAULT 0 CHECK (rotation_deg IN (0, 90, 180, 270)),
    ADD COLUMN IF NOT EXISTS design_style text NOT NULL DEFAULT 'CLASSIC_HOME',
    ADD COLUMN IF NOT EXISTS upgrade_level integer NOT NULL DEFAULT 1 CHECK (upgrade_level >= 1 AND upgrade_level <= 5),
    ADD COLUMN IF NOT EXISTS has_lit_windows boolean NOT NULL DEFAULT true,
    ADD COLUMN IF NOT EXISTS asset_key text NOT NULL DEFAULT 'house_classic';

ALTER TABLE public.house_public_details
    ADD COLUMN IF NOT EXISTS width_cells integer NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS height_cells integer NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS rotation_deg integer NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS design_style text NOT NULL DEFAULT 'CLASSIC_HOME',
    ADD COLUMN IF NOT EXISTS upgrade_level integer NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS has_lit_windows boolean NOT NULL DEFAULT true,
    ADD COLUMN IF NOT EXISTS asset_key text NOT NULL DEFAULT 'house_classic';

-- 4. City Districts / Zones
CREATE TABLE IF NOT EXISTS public.city_districts (
    id text PRIMARY KEY,
    layout_id text NOT NULL DEFAULT 'main_city' REFERENCES public.city_layout(id) ON DELETE RESTRICT,
    name_ar text NOT NULL CHECK (char_length(trim(name_ar)) > 0),
    code text NOT NULL UNIQUE,
    theme_color_hex bigint NOT NULL DEFAULT 4281174670,
    grid_x integer NOT NULL CHECK (grid_x >= 0),
    grid_y integer NOT NULL CHECK (grid_y >= 0),
    width_cells integer NOT NULL CHECK (width_cells >= 1),
    height_cells integer NOT NULL CHECK (height_cells >= 1),
    ambient_style text NOT NULL DEFAULT 'RESIDENTIAL',
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

DROP TRIGGER IF EXISTS trg_city_districts_updated_at ON public.city_districts;
CREATE TRIGGER trg_city_districts_updated_at
    BEFORE UPDATE ON public.city_districts
    FOR EACH ROW
    EXECUTE FUNCTION public.set_updated_at();

-- 5. Civic & Public Buildings (Palace, Bank, Hospital, School, Mall, Fuel, Mine, etc.)
CREATE TABLE IF NOT EXISTS public.city_buildings (
    id text PRIMARY KEY,
    layout_id text NOT NULL DEFAULT 'main_city' REFERENCES public.city_layout(id) ON DELETE RESTRICT,
    district_id text REFERENCES public.city_districts(id) ON DELETE SET NULL,
    name_ar text NOT NULL CHECK (char_length(trim(name_ar)) > 0),
    building_type text NOT NULL CHECK (building_type IN (
        'PRESIDENTIAL_PALACE', 'CENTRAL_BANK', 'GOVERNMENT_HALL', 'MUNICIPALITY',
        'HOSPITAL', 'SCHOOL_UNIVERSITY', 'COMMERCIAL_MALL', 'RESTAURANT_CAFE',
        'FUEL_STATION', 'RECREATION_CENTER', 'MINE_INDUSTRIAL', 'CUSTOM_LANDMARK'
    )),
    grid_x integer NOT NULL CHECK (grid_x >= 0),
    grid_y integer NOT NULL CHECK (grid_y >= 0),
    width_cells integer NOT NULL DEFAULT 1 CHECK (width_cells >= 1),
    height_cells integer NOT NULL DEFAULT 1 CHECK (height_cells >= 1),
    rotation_deg integer NOT NULL DEFAULT 0 CHECK (rotation_deg IN (0, 90, 180, 270)),
    upgrade_level integer NOT NULL DEFAULT 1 CHECK (upgrade_level >= 1 AND upgrade_level <= 5),
    primary_color_hex bigint NOT NULL DEFAULT 4293912536,
    accent_color_hex bigint NOT NULL DEFAULT 4281166478,
    asset_key text NOT NULL DEFAULT 'building_custom_landmark',
    is_lit_at_night boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

DROP TRIGGER IF EXISTS trg_city_buildings_updated_at ON public.city_buildings;
CREATE TRIGGER trg_city_buildings_updated_at
    BEFORE UPDATE ON public.city_buildings
    FOR EACH ROW
    EXECUTE FUNCTION public.set_updated_at();

-- 6. Reusable Environment Props Library
CREATE TABLE IF NOT EXISTS public.city_environment_props (
    id text PRIMARY KEY,
    layout_id text NOT NULL DEFAULT 'main_city' REFERENCES public.city_layout(id) ON DELETE RESTRICT,
    prop_type text NOT NULL,
    grid_x integer NOT NULL CHECK (grid_x >= 0),
    grid_y integer NOT NULL CHECK (grid_y >= 0),
    width_cells integer NOT NULL DEFAULT 1 CHECK (width_cells >= 1),
    height_cells integer NOT NULL DEFAULT 1 CHECK (height_cells >= 1),
    rotation_deg integer NOT NULL DEFAULT 0 CHECK (rotation_deg IN (0, 90, 180, 270)),
    scale real NOT NULL DEFAULT 1.0 CHECK (scale > 0.1 AND scale <= 4.0),
    variant integer NOT NULL DEFAULT 0,
    label_ar text,
    layer text NOT NULL DEFAULT 'STRUCTURE' CHECK (layer IN ('GROUND_OVERLAY', 'STRUCTURE', 'LIGHT_FIXTURE')),
    primary_color_hex bigint NOT NULL DEFAULT 4283405133,
    asset_key text NOT NULL DEFAULT 'prop_tree_broadleaf',
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

DROP TRIGGER IF EXISTS trg_city_environment_props_updated_at ON public.city_environment_props;
CREATE TRIGGER trg_city_environment_props_updated_at
    BEFORE UPDATE ON public.city_environment_props
    FOR EACH ROW
    EXECUTE FUNCTION public.set_updated_at();

-- 7. City Lighting & Atmospheric Light Sources
CREATE TABLE IF NOT EXISTS public.city_lights (
    id text PRIMARY KEY,
    layout_id text NOT NULL DEFAULT 'main_city' REFERENCES public.city_layout(id) ON DELETE RESTRICT,
    light_type text NOT NULL,
    grid_x integer NOT NULL CHECK (grid_x >= 0),
    grid_y integer NOT NULL CHECK (grid_y >= 0),
    offset_x real NOT NULL DEFAULT 0.0,
    offset_y real NOT NULL DEFAULT 0.0,
    color_hex bigint NOT NULL DEFAULT 4294959234,
    intensity real NOT NULL DEFAULT 1.0 CHECK (intensity >= 0.0 AND intensity <= 3.0),
    radius_world real NOT NULL DEFAULT 96.0 CHECK (radius_world > 0.0 AND radius_world <= 600.0),
    direction_deg real NOT NULL DEFAULT 0.0,
    cone_angle_deg real NOT NULL DEFAULT 360.0 CHECK (cone_angle_deg > 0.0 AND cone_angle_deg <= 360.0),
    is_enabled boolean NOT NULL DEFAULT true,
    linked_entity_id text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

DROP TRIGGER IF EXISTS trg_city_lights_updated_at ON public.city_lights;
CREATE TRIGGER trg_city_lights_updated_at
    BEFORE UPDATE ON public.city_lights
    FOR EACH ROW
    EXECUTE FUNCTION public.set_updated_at();
