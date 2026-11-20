-- ============================================================================
-- Madinat Al-Tayibin — Phase 4 Supabase Schema
-- Part 06: Foreign Keys, Unique Constraints & Database-Level Spatial Validation
-- ============================================================================

-- Plots constraints: unique plot_code and unique grid coordinate
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_plots_plot_code') THEN
        ALTER TABLE public.plots ADD CONSTRAINT uq_plots_plot_code UNIQUE (plot_code);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_plots_grid_coord') THEN
        ALTER TABLE public.plots ADD CONSTRAINT uq_plots_grid_coord UNIQUE (grid_x, grid_y);
    END IF;
END $$;

-- Houses constraints: 1 house per plot, 1 house per grid coordinate
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_houses_plot') THEN
        ALTER TABLE public.houses
            ADD CONSTRAINT fk_houses_plot FOREIGN KEY (plot_id)
            REFERENCES public.plots(id) ON DELETE RESTRICT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_houses_plot_id') THEN
        ALTER TABLE public.houses ADD CONSTRAINT uq_houses_plot_id UNIQUE (plot_id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_houses_grid_coord') THEN
        ALTER TABLE public.houses ADD CONSTRAINT uq_houses_grid_coord UNIQUE (grid_x, grid_y);
    END IF;
END $$;

-- Private and public details foreign keys
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_private_details_house') THEN
        ALTER TABLE public.house_private_details
            ADD CONSTRAINT fk_private_details_house FOREIGN KEY (house_id)
            REFERENCES public.houses(id) ON DELETE CASCADE;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_private_details_house_id') THEN
        ALTER TABLE public.house_private_details ADD CONSTRAINT uq_private_details_house_id UNIQUE (house_id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_public_details_house') THEN
        ALTER TABLE public.house_public_details
            ADD CONSTRAINT fk_public_details_house FOREIGN KEY (house_id)
            REFERENCES public.houses(id) ON DELETE CASCADE;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_public_details_plot_id') THEN
        ALTER TABLE public.house_public_details ADD CONSTRAINT uq_public_details_plot_id UNIQUE (plot_id);
    END IF;
END $$;

-- Database-level spatial integrity guard for plots against city_layout
CREATE OR REPLACE FUNCTION public.validate_plot_spatial_integrity()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_layout public.city_layout%ROWTYPE;
    v_coord jsonb;
BEGIN
    SELECT * INTO v_layout FROM public.city_layout WHERE id = NEW.layout_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'City layout % not found.', NEW.layout_id USING ERRCODE = '23503';
    END IF;

    IF NEW.grid_x < 0 OR NEW.grid_x >= v_layout.grid_width
       OR NEW.grid_y < 0 OR NEW.grid_y >= v_layout.grid_height THEN
        RAISE EXCEPTION 'Plot % coordinate (%,%) exceeds map bounds (%x%).',
            NEW.id, NEW.grid_x, NEW.grid_y, v_layout.grid_width, v_layout.grid_height
            USING ERRCODE = '23514';
    END IF;

    v_coord := jsonb_build_object('x', NEW.grid_x, 'y', NEW.grid_y);
    IF (v_layout.roads @> jsonb_build_array(v_coord))
       OR (v_layout.sidewalks @> jsonb_build_array(v_coord))
       OR (v_layout.parks @> jsonb_build_array(v_coord)) THEN
        RAISE EXCEPTION 'Plot % coordinate (%,%) overlaps road, sidewalk, or park.',
            NEW.id, NEW.grid_x, NEW.grid_y
            USING ERRCODE = '23514';
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_validate_plot_spatial ON public.plots;
CREATE TRIGGER trg_validate_plot_spatial
    BEFORE INSERT OR UPDATE OF layout_id, grid_x, grid_y ON public.plots
    FOR EACH ROW
    EXECUTE FUNCTION public.validate_plot_spatial_integrity();

-- Database-level spatial integrity guard for houses
CREATE OR REPLACE FUNCTION public.validate_house_spatial_integrity()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_plot public.plots%ROWTYPE;
    v_layout public.city_layout%ROWTYPE;
    v_coord jsonb;
BEGIN
    SELECT * INTO v_plot FROM public.plots WHERE id = NEW.plot_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Target plot % does not exist.', NEW.plot_id USING ERRCODE = '23503';
    END IF;

    SELECT * INTO v_layout FROM public.city_layout WHERE id = v_plot.layout_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'City layout % not found.', v_plot.layout_id USING ERRCODE = '23503';
    END IF;

    IF NEW.grid_x < 0 OR NEW.grid_x >= v_layout.grid_width
       OR NEW.grid_y < 0 OR NEW.grid_y >= v_layout.grid_height THEN
        RAISE EXCEPTION 'House coordinate (%,%) is outside map bounds.', NEW.grid_x, NEW.grid_y
            USING ERRCODE = '23514';
    END IF;

    IF NEW.grid_x <> v_plot.grid_x OR NEW.grid_y <> v_plot.grid_y THEN
        RAISE EXCEPTION 'House coordinate (%,%) must match plot % coordinate (%,%).',
            NEW.grid_x, NEW.grid_y, v_plot.id, v_plot.grid_x, v_plot.grid_y
            USING ERRCODE = '23514';
    END IF;

    v_coord := jsonb_build_object('x', NEW.grid_x, 'y', NEW.grid_y);
    IF (v_layout.roads @> jsonb_build_array(v_coord))
       OR (v_layout.sidewalks @> jsonb_build_array(v_coord))
       OR (v_layout.parks @> jsonb_build_array(v_coord)) THEN
        RAISE EXCEPTION 'Cannot build house on road, sidewalk, or public park at (%,%).',
            NEW.grid_x, NEW.grid_y USING ERRCODE = '23514';
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
    BEFORE INSERT OR UPDATE OF plot_id, grid_x, grid_y ON public.houses
    FOR EACH ROW
    EXECUTE FUNCTION public.validate_house_spatial_integrity();
