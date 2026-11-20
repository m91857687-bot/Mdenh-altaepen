-- ============================================================================
-- Madinat Al-Tayibin — Phase 4 Supabase Schema
-- Part 07: Query & Lookup Indexes
-- ============================================================================

CREATE INDEX IF NOT EXISTS idx_profiles_role
    ON public.profiles (role);

CREATE INDEX IF NOT EXISTS idx_plots_status
    ON public.plots (status);

CREATE INDEX IF NOT EXISTS idx_plots_layout_status
    ON public.plots (layout_id, status);

CREATE INDEX IF NOT EXISTS idx_houses_plot_id
    ON public.houses (plot_id);

CREATE INDEX IF NOT EXISTS idx_houses_country_code
    ON public.houses (country_code);

CREATE INDEX IF NOT EXISTS idx_house_public_details_plot_id
    ON public.house_public_details (plot_id);

CREATE INDEX IF NOT EXISTS idx_house_public_details_grid
    ON public.house_public_details (grid_x, grid_y);

CREATE INDEX IF NOT EXISTS idx_house_requests_status_created
    ON public.house_requests (status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_admin_activation_codes_lookup
    ON public.admin_activation_codes (expires_at, used_at, is_revoked);

CREATE INDEX IF NOT EXISTS idx_admin_devices_status
    ON public.admin_devices (status, revoked_at);
