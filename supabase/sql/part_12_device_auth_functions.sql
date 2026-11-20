-- ============================================================================
-- Madinat Al-Tayibin — Phase 4 Supabase Schema
-- Part 12: One-Time Device Activation, Verification & Server Revocation RPCs
-- ============================================================================

-- 1. Server-only helper (run by owner in Supabase SQL Editor) to mint a single-use
--    activation code. Stores ONLY SHA-256 hash; returns plaintext code once.
CREATE OR REPLACE FUNCTION public.create_admin_activation_code(
    p_label text DEFAULT 'جهاز الإدارة الرئيسي',
    p_ttl_minutes integer DEFAULT 60
)
RETURNS TABLE (
    activation_code text,
    code_id uuid,
    label text,
    expires_at timestamptz
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, extensions
AS $$
DECLARE
    v_raw text;
    v_formatted text;
    v_normalized text;
    v_hash text;
    v_expires timestamptz;
    v_id uuid;
BEGIN
    IF p_ttl_minutes IS NULL OR p_ttl_minutes <= 0 OR p_ttl_minutes > 10080 THEN
        RAISE EXCEPTION 'p_ttl_minutes must be between 1 and 10080 (7 days).' USING ERRCODE = '22023';
    END IF;

    v_raw := upper(encode(gen_random_bytes(8), 'hex'));
    v_formatted := 'MADINA-' || substr(v_raw, 1, 4) || '-' || substr(v_raw, 5, 4) || '-' || substr(v_raw, 9, 4) || '-' || substr(v_raw, 13, 4);
    v_normalized := upper(regexp_replace(v_formatted, '[\s-]+', '', 'g'));
    v_hash := encode(digest(v_normalized, 'sha256'), 'hex');
    v_expires := now() + make_interval(mins => p_ttl_minutes);

    INSERT INTO public.admin_activation_codes (code_hash, label, expires_at)
    VALUES (v_hash, COALESCE(nullif(trim(p_label), ''), 'جهاز الإدارة الرئيسي'), v_expires)
    RETURNING id INTO v_id;

    activation_code := v_formatted;
    code_id := v_id;
    label := COALESCE(nullif(trim(p_label), ''), 'جهاز الإدارة الرئيسي');
    expires_at := v_expires;
    RETURN NEXT;
END;
$$;

-- 2. One-time device enrollment RPC called by :app-admin on initial setup.
--    Validates single-use code, atomically consumes it, issues a 256-bit device token once,
--    and stores ONLY the token SHA-256 hash in admin_devices.
CREATE OR REPLACE FUNCTION public.activate_admin_device(
    p_activation_code text,
    p_device_id text,
    p_device_name text DEFAULT 'Android Admin Device'
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, extensions
AS $$
DECLARE
    v_normalized_code text;
    v_code_hash text;
    v_code_row public.admin_activation_codes%ROWTYPE;
    v_clean_device_id text;
    v_clean_device_name text;
    v_raw_token text;
    v_token_hash text;
    v_now timestamptz := now();
BEGIN
    v_clean_device_id := trim(COALESCE(p_device_id, ''));
    IF length(v_clean_device_id) < 8 THEN
        RAISE EXCEPTION 'معرّف الجهاز غير صالح.' USING ERRCODE = '22023';
    END IF;

    v_normalized_code := upper(regexp_replace(trim(COALESCE(p_activation_code, '')), '[\s-]+', '', 'g'));
    IF length(v_normalized_code) < 10 THEN
        RAISE EXCEPTION 'يرجى إدخال رمز التفعيل الكامل.' USING ERRCODE = '22023';
    END IF;

    v_code_hash := encode(digest(v_normalized_code, 'sha256'), 'hex');

    SELECT *
    INTO v_code_row
    FROM public.admin_activation_codes
    WHERE code_hash = v_code_hash
    FOR UPDATE;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'رمز التفعيل غير صحيح.' USING ERRCODE = '42501';
    END IF;

    IF v_code_row.is_revoked THEN
        RAISE EXCEPTION 'تم إلغاء رمز التفعيل هذا من الخادم.' USING ERRCODE = '42501';
    END IF;

    IF v_code_row.used_at IS NOT NULL THEN
        RAISE EXCEPTION 'رمز التفعيل استُخدم مسبقًا ولا يمكن إعادة استخدامه.' USING ERRCODE = '42501';
    END IF;

    IF v_code_row.expires_at <= v_now THEN
        RAISE EXCEPTION 'انتهت صلاحية رمز التفعيل. أنشئ رمزًا جديدًا من الخادم.' USING ERRCODE = '42501';
    END IF;

    UPDATE public.admin_activation_codes
    SET used_at = v_now,
        used_by_device_id = v_clean_device_id
    WHERE id = v_code_row.id;

    v_clean_device_name := COALESCE(nullif(trim(p_device_name), ''), 'Android Admin Device');
    v_raw_token := encode(gen_random_bytes(32), 'hex');
    v_token_hash := encode(digest(v_raw_token, 'sha256'), 'hex');

    INSERT INTO public.admin_devices (
        device_id, device_name, device_token_hash, status,
        activation_code_id, activated_at, last_verified_at, revoked_at, revoke_reason
    ) VALUES (
        v_clean_device_id,
        v_clean_device_name,
        v_token_hash,
        'ACTIVE',
        v_code_row.id,
        v_now,
        v_now,
        NULL,
        NULL
    )
    ON CONFLICT (device_id) DO UPDATE SET
        device_name = EXCLUDED.device_name,
        device_token_hash = EXCLUDED.device_token_hash,
        status = 'ACTIVE',
        activation_code_id = EXCLUDED.activation_code_id,
        activated_at = v_now,
        last_verified_at = v_now,
        revoked_at = NULL,
        revoke_reason = NULL;

    RETURN jsonb_build_object(
        'device_id', v_clean_device_id,
        'device_token', v_raw_token,
        'device_name', v_clean_device_name,
        'status', 'ACTIVE',
        'activated_at', v_now
    );
END;
$$;

-- 3. Server-side verification RPC invoked on app startup and before sensitive operations.
CREATE OR REPLACE FUNCTION public.verify_admin_device(
    p_device_id text DEFAULT NULL,
    p_device_token text DEFAULT NULL
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, extensions
AS $$
DECLARE
    v_device_id text;
    v_device_token text;
    v_token_hash text;
    v_device public.admin_devices%ROWTYPE;
BEGIN
    v_device_id := COALESCE(nullif(trim(p_device_id), ''), public.request_header('x-admin-device-id'));
    v_device_token := COALESCE(nullif(trim(p_device_token), ''), public.request_header('x-admin-device-token'));

    IF v_device_id IS NULL OR v_device_token IS NULL THEN
        RETURN jsonb_build_object(
            'authorized', false,
            'status', 'UNENROLLED',
            'reason', 'الجهاز غير مفعّل بعد.'
        );
    END IF;

    SELECT * INTO v_device FROM public.admin_devices WHERE device_id = v_device_id;
    IF NOT FOUND THEN
        RETURN jsonb_build_object(
            'authorized', false,
            'status', 'REVOKED',
            'reason', 'الجهاز غير مسجل كجهاز إدارة معتمد.'
        );
    END IF;

    IF v_device.status <> 'ACTIVE' OR v_device.revoked_at IS NOT NULL THEN
        RETURN jsonb_build_object(
            'authorized', false,
            'status', 'REVOKED',
            'reason', COALESCE(v_device.revoke_reason, 'تم إلغاء اعتماد هذا الجهاز من الخادم.')
        );
    END IF;

    v_token_hash := encode(digest(v_device_token, 'sha256'), 'hex');
    IF v_device.device_token_hash <> v_token_hash THEN
        RETURN jsonb_build_object(
            'authorized', false,
            'status', 'REVOKED',
            'reason', 'بيانات اعتماد الجهاز غير متطابقة أو تم استبدالها.'
        );
    END IF;

    UPDATE public.admin_devices
    SET last_verified_at = now()
    WHERE device_id = v_device_id;

    RETURN jsonb_build_object(
        'authorized', true,
        'status', 'ACTIVE',
        'device_id', v_device.device_id,
        'device_name', v_device.device_name,
        'activated_at', v_device.activated_at
    );
END;
$$;

-- 4. Server-only revocation RPCs (run from Supabase SQL Editor as postgres/service_role).
CREATE OR REPLACE FUNCTION public.revoke_admin_device(
    p_device_id text,
    p_reason text DEFAULT 'تم إلغاء اعتماد الجهاز من مالك النظام'
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, extensions
AS $$
BEGIN
    UPDATE public.admin_devices
    SET status = 'REVOKED',
        revoked_at = now(),
        revoke_reason = COALESCE(nullif(trim(p_reason), ''), 'تم إلغاء اعتماد الجهاز من مالك النظام'),
        device_token_hash = 'REVOKED_' || encode(gen_random_bytes(16), 'hex')
    WHERE device_id = trim(p_device_id);

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Admin device % not found.', p_device_id;
    END IF;
END;
$$;

CREATE OR REPLACE FUNCTION public.revoke_admin_activation_code(p_code_id uuid)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    UPDATE public.admin_activation_codes
    SET is_revoked = true
    WHERE id = p_code_id;
END;
$$;

-- Strictly lock server-only admin management helpers away from anon & authenticated clients
REVOKE ALL ON FUNCTION public.create_admin_activation_code(text, integer) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.revoke_admin_device(text, text) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION public.revoke_admin_activation_code(uuid) FROM PUBLIC, anon, authenticated;

-- Allow device enrollment and server verification RPCs
REVOKE ALL ON FUNCTION public.activate_admin_device(text, text, text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.activate_admin_device(text, text, text) TO anon, authenticated;

REVOKE ALL ON FUNCTION public.verify_admin_device(text, text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.verify_admin_device(text, text) TO anon, authenticated;
