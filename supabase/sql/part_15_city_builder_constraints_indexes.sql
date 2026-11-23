-- ============================================================================
-- Madinat Al-Tayibin — City Builder Phase 1 Forward Migration
-- Part 15: Multi-Cell Constraints, Overlap Triggers & Performance Indexes
-- Note: Forward-only. Safely replaces 1x1 origin-unique constraints with
--       multi-cell rectangle overlap guards without dropping data.
-- ============================================================================

-- 1. Link plots.district_id safely to city_districts
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_plots_district') THEN
        ALTER TABLE public.plots
            ADD CONSTRAINT fk_plots_district FOREIGN KEY (district_id)
            REFERENCES public.city_districts(id) ON DELETE SET NULL;
    END IF;
END $$;

-- 2. Drop legacy 1x1 single-coordinate unique constraints so split/resize/multi-cell mutations succeed cleanly
ALTER TABLE public.plots DROP CONSTRAINT IF EXISTS uq_plots_grid_coord;
ALTER TABLE public.houses DROP CONSTRAINT IF EXISTS uq_houses_grid_coord;

-- 3. Multi-cell rectangular footprint overlap helper (considering 90/270 rotation swap)
CREATE OR REPLACE FUNCTION public.footprint_overlaps(
    ax integer, ay integer, aw integer, ah integer, arot integer,
    bx integer, by integer, bw integer, bh integer, brot integer
)
RETURNS boolean
LANGUAGE sql
IMMUTABLE
AS $$
    SELECT ax < (bx + CASE WHEN brot IN (90, 270) THEN bh ELSE bw END)
       AND (ax + CASE WHEN arot IN (90, 270) THEN ah ELSE aw END) > bx
       AND ay < (by + CASE WHEN brot IN (90, 270) THEN bw ELSE bh END)
       AND (ay + CASE WHEN arot IN (90, 270) THEN aw ELSE ah END) > by;
$$;

-- 4. Upgrade plot spatial integrity check to validate full multi-cell footprints against map, roads, buildings & other plots
CREATE OR REPLACE FUNCTION public.validate_plot_spatial_integrity()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_layout public.city_layout%ROWTYPE;
    v_eff_w integer;
    v_eff_h integer;
    v_dx integer;
    v_dy integer;
    v_coord jsonb;
BEGIN
    SELECT * INTO v_layout FROM public.city_layout WHERE id = NEW.layout_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'City layout % not found.', NEW.layout_id USING ERRCODE = '23503';
    END IF;

    v_eff_w := CASE WHEN COALESCE(NEW.rotation_deg, 0) IN (90, 270) THEN GREATEST(NEW.height_cells, 1) ELSE GREATEST(NEW.width_cells, 1) END;
    v_eff_h := CASE WHEN COALESCE(NEW.rotation_deg, 0) IN (90, 270) THEN GREATEST(NEW.width_cells, 1) ELSE GREATEST(NEW.height_cells, 1) END;

    IF NEW.grid_x < 0 OR (NEW.grid_x + v_eff_w) > v_layout.grid_width
       OR NEW.grid_y < 0 OR (NEW.grid_y + v_eff_h) > v_layout.grid_height THEN
        RAISE EXCEPTION 'Plot % footprint (%,% %x%) exceeds map bounds (%x%).',
            NEW.id, NEW.grid_x, NEW.grid_y, v_eff_w, v_eff_h, v_layout.grid_width, v_layout.grid_height
            USING ERRCODE = '23514';
    END IF;

    FOR v_dx IN 0..(v_eff_w - 1) LOOP
        FOR v_dy IN 0..(v_eff_h - 1) LOOP
            v_coord := jsonb_build_object('x', NEW.grid_x + v_dx, 'y', NEW.grid_y + v_dy);
            IF (v_layout.roads @> jsonb_build_array(v_coord))
               OR (v_layout.sidewalks @> jsonb_build_array(v_coord))
               OR (v_layout.parks @> jsonb_build_array(v_coord)) THEN
                RAISE EXCEPTION 'Plot % cell (%,%) overlaps road, sidewalk, or park.',
                    NEW.id, NEW.grid_x + v_dx, NEW.grid_y + v_dy
                    USING ERRCODE = '23514';
            END IF;
        END LOOP;
    END LOOP;

    IF EXISTS (
        SELECT 1 FROM public.plots p
        WHERE p.layout_id = NEW.layout_id
          AND p.id <> NEW.id
          AND public.footprint_overlaps(
                NEW.grid_x, NEW.grid_y, NEW.width_cells, NEW.height_cells, COALESCE(NEW.rotation_deg, 0),
                p.grid_x, p.grid_y, p.width_cells, p.height_cells, COALESCE(p.rotation_deg, 0)
              )
    ) THEN
        RAISE EXCEPTION 'Plot % overlaps an existing plot.', NEW.id USING ERRCODE = '23514';
    END IF;

    IF EXISTS (
        SELECT 1 FROM public.city_buildings b
        WHERE b.layout_id = NEW.layout_id
          AND public.footprint_overlaps(
                NEW.grid_x, NEW.grid_y, NEW.width_cells, NEW.height_cells, COALESCE(NEW.rotation_deg, 0),
                b.grid_x, b.grid_y, b.width_cells, b.height_cells, COALESCE(b.rotation_deg, 0)
              )
    ) THEN
        RAISE EXCEPTION 'Plot % overlaps an existing public building.', NEW.id USING ERRCODE = '23514';
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_validate_plot_spatial ON public.plots;
CREATE TRIGGER trg_validate_plot_spatial
    BEFORE INSERT OR UPDATE OF layout_id, grid_x, grid_y, width_cells, height_cells, rotation_deg ON public.plots
    FOR EACH ROW
    EXECUTE FUNCTION public.validate_plot_spatial_integrity();

-- 5. Upgrade house spatial integrity check to inherit/validate multi-cell footprint from target plot
CREATE OR REPLACE FUNCTION public.validate_house_spatial_integrity()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_plot public.plots%ROWTYPE;
    v_layout public.city_layout%ROWTYPE;
BEGIN
    SELECT * INTO v_plot FROM public.plots WHERE id = NEW.plot_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Target plot % does not exist.', NEW.plot_id USING ERRCODE = '23503';
    END IF;

    SELECT * INTO v_layout FROM public.city_layout WHERE id = v_plot.layout_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'City layout % not found.', v_plot.layout_id USING ERRCODE = '23503';
    END IF;

    IF NEW.grid_x <> v_plot.grid_x OR NEW.grid_y <> v_plot.grid_y THEN
        RAISE EXCEPTION 'House coordinate (%,%) must match plot % coordinate (%,%).',
            NEW.grid_x, NEW.grid_y, v_plot.id, v_plot.grid_x, v_plot.grid_y
            USING ERRCODE = '23514';
    END IF;

    IF TG_OP = 'INSERT' OR (TG_OP = 'UPDATE' AND NEW.plot_id IS DISTINCT FROM OLD.plot_id) THEN
        IF v_plot.status <> 'AVAILABLE' THEN
            RAISE EXCEPTION 'Target plot % is not AVAILABLE (status=%).', v_plot.id, v_plot.status
                USING ERRCODE = '23514';
        END IF;
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_validate_house_spatial ON public.houses;
CREATE TRIGGER trg_validate_house_spatial
    BEFORE INSERT OR UPDATE OF plot_id, grid_x, grid_y, width_cells, height_cells, rotation_deg ON public.houses
    FOR EACH ROW
    EXECUTE FUNCTION public.validate_house_spatial_integrity();

-- 6. Public building spatial integrity trigger
CREATE OR REPLACE FUNCTION public.validate_building_spatial_integrity()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_layout public.city_layout%ROWTYPE;
    v_eff_w integer;
    v_eff_h integer;
BEGIN
    SELECT * INTO v_layout FROM public.city_layout WHERE id = NEW.layout_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'City layout % not found.', NEW.layout_id USING ERRCODE = '23503';
    END IF;

    v_eff_w := CASE WHEN COALESCE(NEW.rotation_deg, 0) IN (90, 270) THEN GREATEST(NEW.height_cells, 1) ELSE GREATEST(NEW.width_cells, 1) END;
    v_eff_h := CASE WHEN COALESCE(NEW.rotation_deg, 0) IN (90, 270) THEN GREATEST(NEW.width_cells, 1) ELSE GREATEST(NEW.height_cells, 1) END;

    IF NEW.grid_x < 0 OR (NEW.grid_x + v_eff_w) > v_layout.grid_width
       OR NEW.grid_y < 0 OR (NEW.grid_y + v_eff_h) > v_layout.grid_height THEN
        RAISE EXCEPTION 'Building % exceeds map bounds.', NEW.id USING ERRCODE = '23514';
    END IF;

    IF EXISTS (
        SELECT 1 FROM public.city_buildings b
        WHERE b.layout_id = NEW.layout_id
          AND b.id <> NEW.id
          AND public.footprint_overlaps(
                NEW.grid_x, NEW.grid_y, NEW.width_cells, NEW.height_cells, COALESCE(NEW.rotation_deg, 0),
                b.grid_x, b.grid_y, b.width_cells, b.height_cells, COALESCE(b.rotation_deg, 0)
              )
    ) THEN
        RAISE EXCEPTION 'Building % overlaps another public building.', NEW.id USING ERRCODE = '23514';
    END IF;

    IF EXISTS (
        SELECT 1 FROM public.plots p
        WHERE p.layout_id = NEW.layout_id
          AND public.footprint_overlaps(
                NEW.grid_x, NEW.grid_y, NEW.width_cells, NEW.height_cells, COALESCE(NEW.rotation_deg, 0),
                p.grid_x, p.grid_y, p.width_cells, p.height_cells, COALESCE(p.rotation_deg, 0)
              )
    ) THEN
        RAISE EXCEPTION 'Building % overlaps a residential plot.', NEW.id USING ERRCODE = '23514';
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_validate_building_spatial ON public.city_buildings;
CREATE TRIGGER trg_validate_building_spatial
    BEFORE INSERT OR UPDATE OF layout_id, grid_x, grid_y, width_cells, height_cells, rotation_deg ON public.city_buildings
    FOR EACH ROW
    EXECUTE FUNCTION public.validate_building_spatial_integrity();

-- 7. Spatial & Query Performance Indexes
CREATE INDEX IF NOT EXISTS idx_plots_district_status ON public.plots (district_id, status);
CREATE INDEX IF NOT EXISTS idx_city_districts_layout ON public.city_districts (layout_id, grid_x, grid_y);
CREATE INDEX IF NOT EXISTS idx_city_buildings_layout_type ON public.city_buildings (layout_id, building_type);
CREATE INDEX IF NOT EXISTS idx_city_buildings_coords ON public.city_buildings (grid_x, grid_y);
CREATE INDEX IF NOT EXISTS idx_city_props_layout_layer ON public.city_environment_props (layout_id, layer, grid_x, grid_y);
CREATE INDEX IF NOT EXISTS idx_city_lights_layout_enabled ON public.city_lights (layout_id, is_enabled, grid_x, grid_y);
