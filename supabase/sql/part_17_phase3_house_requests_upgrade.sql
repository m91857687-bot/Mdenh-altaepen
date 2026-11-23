-- ============================================================================
-- Madinat Al-Tayibin — Phase 3 Forward Migration
-- Part 17: House Building Requests Lifecycle, Secure Visitor RPC & Admin RPCs
-- ============================================================================

-- 1. Upgrade house_requests forward-compatibly (preserve all existing rows)
ALTER TABLE public.house_requests
    ADD COLUMN IF NOT EXISTS target_plot_id text REFERENCES public.plots(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS assigned_house_id text REFERENCES public.houses(id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS admin_notes text,
    ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now();

ALTER TABLE public.house_requests
    ALTER COLUMN phone DROP NOT NULL,
    ALTER COLUMN country DROP NOT NULL,
    ALTER COLUMN family_size DROP NOT NULL,
    ALTER COLUMN status SET DEFAULT 'NEW';

-- Drop legacy NOT NULL / CHECK constraints on phone, country, family_size, and old 3-state status
DO $$
DECLARE
    v_con record;
BEGIN
    FOR v_con IN
        SELECT conname
        FROM pg_constraint
        WHERE conrelid = 'public.house_requests'::regclass
          AND contype = 'c'
          AND (
              pg_get_constraintdef(oid) ILIKE '%phone%'
              OR pg_get_constraintdef(oid) ILIKE '%country%'
              OR pg_get_constraintdef(oid) ILIKE '%family_size%'
              OR pg_get_constraintdef(oid) ILIKE '%status%'
          )
    LOOP
        EXECUTE format('ALTER TABLE public.house_requests DROP CONSTRAINT IF EXISTS %I', v_con.conname);
    END LOOP;
END $$;

ALTER TABLE public.house_requests
    ADD CONSTRAINT chk_house_requests_applicant_name
        CHECK (char_length(trim(applicant_name)) >= 2),
    ADD CONSTRAINT chk_house_requests_status_v2
        CHECK (status IN (
            'NEW',
            'UNDER_REVIEW',
            'APPROVED',
            'PREPARING_PLOT',
            'BUILDING_HOUSE',
            'COMPLETED',
            'REJECTED',
            'PENDING'
        ));

CREATE INDEX IF NOT EXISTS idx_house_requests_status_created
    ON public.house_requests(status, created_at DESC);

DROP TRIGGER IF EXISTS trg_house_requests_updated_at ON public.house_requests;
CREATE TRIGGER trg_house_requests_updated_at
    BEFORE UPDATE ON public.house_requests
    FOR EACH ROW
    EXECUTE FUNCTION public.set_updated_at();

-- 2. Controlled public submission RPC (no PII read access exposed to visitors)
CREATE OR REPLACE FUNCTION public.submit_house_request(
    p_applicant_name text,
    p_phone text DEFAULT NULL,
    p_email text DEFAULT NULL,
    p_notes text DEFAULT NULL
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_name text := trim(COALESCE(p_applicant_name, ''));
    v_phone text := nullif(trim(COALESCE(p_phone, '')), '');
    v_email text := nullif(trim(COALESCE(p_email, '')), '');
    v_notes text := nullif(trim(COALESCE(p_notes, '')), '');
    v_id uuid;
BEGIN
    IF char_length(v_name) < 2 OR char_length(v_name) > 120 THEN
        RAISE EXCEPTION 'يرجى إدخال الاسم الكامل بشكل صحيح (بين حرفين و120 حرفًا).' USING ERRCODE = '23514';
    END IF;

    IF v_phone IS NOT NULL AND (char_length(v_phone) < 5 OR char_length(v_phone) > 32) THEN
        RAISE EXCEPTION 'رقم الجوال غير صالح.' USING ERRCODE = '23514';
    END IF;

    IF v_email IS NOT NULL AND (char_length(v_email) > 120 OR v_email !~* '^[^@\s]+@[^@\s]+\.[^@\s]+$') THEN
        RAISE EXCEPTION 'صيغة البريد الإلكتروني غير صحيحة.' USING ERRCODE = '23514';
    END IF;

    IF v_notes IS NOT NULL AND char_length(v_notes) > 1000 THEN
        RAISE EXCEPTION 'الملاحظات تتجاوز الحد المسموح (1000 حرف).' USING ERRCODE = '23514';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM public.house_requests r
        WHERE lower(trim(r.applicant_name)) = lower(v_name)
          AND COALESCE(r.phone, '') = COALESCE(v_phone, '')
          AND COALESCE(r.email, '') = COALESCE(v_email, '')
          AND r.created_at > now() - interval '2 minutes'
    ) THEN
        RAISE EXCEPTION 'تم استلام طلب مشابه قبل قليل. يرجى الانتظار قبل إرسال طلب جديد.' USING ERRCODE = 'P0001';
    END IF;

    INSERT INTO public.house_requests (
        applicant_name,
        phone,
        email,
        notes,
        status
    ) VALUES (
        v_name,
        v_phone,
        v_email,
        v_notes,
        'NEW'
    )
    RETURNING id INTO v_id;

    RETURN jsonb_build_object(
        'request_id', v_id,
        'status', 'NEW'
    );
END;
$$;

-- 3. Admin request management RPCs (strictly guarded by public.is_admin())
CREATE OR REPLACE FUNCTION public.admin_update_house_request_status(
    p_request_id uuid,
    p_status text,
    p_admin_notes text DEFAULT NULL,
    p_target_plot_id text DEFAULT NULL
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_norm_status text := upper(trim(COALESCE(p_status, '')));
BEGIN
    IF NOT public.is_admin() THEN
        RAISE EXCEPTION 'صلاحيات المدير مطلوبة لتحديث طلبات البناء.' USING ERRCODE = '42501';
    END IF;

    IF v_norm_status NOT IN ('NEW', 'UNDER_REVIEW', 'APPROVED', 'PREPARING_PLOT', 'BUILDING_HOUSE', 'COMPLETED', 'REJECTED') THEN
        RAISE EXCEPTION 'حالة الطلب غير صالحة: %', p_status USING ERRCODE = '23514';
    END IF;

    UPDATE public.house_requests
    SET status = v_norm_status,
        admin_notes = COALESCE(nullif(trim(p_admin_notes), ''), admin_notes),
        target_plot_id = COALESCE(p_target_plot_id, target_plot_id),
        reviewed_at = now(),
        reviewed_by = auth.uid()
    WHERE id = p_request_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'طلب البناء غير موجود.' USING ERRCODE = 'P0002';
    END IF;

    RETURN jsonb_build_object(
        'request_id', p_request_id,
        'status', v_norm_status,
        'plot_id', p_target_plot_id
    );
END;
$$;

CREATE OR REPLACE FUNCTION public.admin_assign_request_plot_and_house(
    p_request_id uuid,
    p_target_plot_id text,
    p_assigned_house_id text DEFAULT NULL,
    p_status text DEFAULT 'COMPLETED',
    p_admin_notes text DEFAULT NULL
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_norm_status text := upper(trim(COALESCE(p_status, 'COMPLETED')));
BEGIN
    IF NOT public.is_admin() THEN
        RAISE EXCEPTION 'صلاحيات المدير مطلوبة لتنفيذ هذه العملية.' USING ERRCODE = '42501';
    END IF;

    IF v_norm_status NOT IN ('NEW', 'UNDER_REVIEW', 'APPROVED', 'PREPARING_PLOT', 'BUILDING_HOUSE', 'COMPLETED', 'REJECTED') THEN
        RAISE EXCEPTION 'حالة الطلب غير صالحة: %', p_status USING ERRCODE = '23514';
    END IF;

    UPDATE public.house_requests
    SET target_plot_id = p_target_plot_id,
        assigned_house_id = COALESCE(p_assigned_house_id, assigned_house_id),
        status = v_norm_status,
        admin_notes = COALESCE(nullif(trim(p_admin_notes), ''), admin_notes),
        reviewed_at = now(),
        reviewed_by = auth.uid()
    WHERE id = p_request_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'طلب البناء غير موجود.' USING ERRCODE = 'P0002';
    END IF;

    RETURN jsonb_build_object(
        'request_id', p_request_id,
        'plot_id', p_target_plot_id,
        'house_id', p_assigned_house_id,
        'status', v_norm_status
    );
END;
$$;

-- 4. Permissions & Realtime
REVOKE ALL ON FUNCTION public.submit_house_request(text, text, text, text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.submit_house_request(text, text, text, text) TO anon, authenticated;

REVOKE ALL ON FUNCTION public.admin_update_house_request_status(uuid, text, text, text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.admin_update_house_request_status(uuid, text, text, text) TO anon, authenticated;

REVOKE ALL ON FUNCTION public.admin_assign_request_plot_and_house(uuid, text, text, text, text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.admin_assign_request_plot_and_house(uuid, text, text, text, text) TO anon, authenticated;

ALTER TABLE public.house_requests REPLICA IDENTITY FULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_publication_tables
        WHERE pubname = 'supabase_realtime' AND schemaname = 'public' AND tablename = 'house_requests'
    ) THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.house_requests;
    END IF;
END $$;
