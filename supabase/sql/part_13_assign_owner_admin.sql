-- ============================================================================
-- Madinat Al-Tayibin — Owner Admin Role Assignment
-- Part 13: Safely Assign 'ADMIN' Role to the Specified Supabase Auth User
-- ============================================================================
-- Execute manually in Supabase SQL Editor (privileged postgres/dashboard role).
-- Does NOT alter schema, RLS policies, triggers, or other user accounts.
-- ============================================================================

BEGIN;

DO $$
DECLARE
    v_target_user_id constant uuid := '44025f86-76f6-4082-b6ba-fb17a70e37a3';
    v_auth_user record;
    v_profile public.profiles%ROWTYPE;
    v_derived_display_name text;
    v_updated_role text;
    v_affected_rows integer := 0;
BEGIN
    -- Guard: must not be executed from client sessions (anon / authenticated)
    IF auth.role() IN ('anon', 'authenticated') THEN
        RAISE EXCEPTION 'تعذر التنفيذ: تعيين صلاحية ADMIN مسموح فقط من Supabase SQL Editor أو دور الخادم الموثوق.'
            USING ERRCODE = '42501';
    END IF;

    -- 1. Verify the target user exists in auth.users
    SELECT id, email, raw_user_meta_data
    INTO v_auth_user
    FROM auth.users
    WHERE id = v_target_user_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'المستخدم المحدد (%) غير موجود في auth.users. يرجى التأكد من إنشاء الحساب في Supabase Auth أولاً.', v_target_user_id
            USING ERRCODE = 'P0002';
    END IF;

    -- 2. Check existing profile in public.profiles
    SELECT *
    INTO v_profile
    FROM public.profiles
    WHERE id = v_target_user_id
    FOR UPDATE;

    IF NOT FOUND THEN
        -- Follow the exact derivation used by public.handle_new_auth_user() in part_02_profiles.sql
        v_derived_display_name := nullif(
            trim(
                COALESCE(
                    v_auth_user.raw_user_meta_data ->> 'display_name',
                    split_part(COALESCE(v_auth_user.email, ''), '@', 1),
                    ''
                )
            ),
            ''
        );

        IF v_derived_display_name IS NULL THEN
            RAISE EXCEPTION 'المستخدم (%) موجود في auth.users لكن لا يوجد له سجل في public.profiles، وتعذر اشتقاق display_name من البريد الإلكتروني أو raw_user_meta_data. أنشئ الملف الشخصي أولاً عبر آلية التسجيل المعتمدة دون تجاوز القيود.', v_target_user_id
                USING ERRCODE = 'P0002';
        END IF;

        RAISE NOTICE 'لم يُعثر على سجل مسبق في public.profiles للمستخدم (%) — سيتم إنشاؤه وفق آلية handle_new_auth_user() المعتمدة وتعيين الدور ADMIN.', v_target_user_id;

        INSERT INTO public.profiles (id, display_name, role)
        VALUES (v_target_user_id, v_derived_display_name, 'ADMIN');
        GET DIAGNOSTICS v_affected_rows = ROW_COUNT;
    ELSE
        -- 3. Promote existing profile to 'ADMIN' without modifying any other user
        IF v_profile.role = 'ADMIN' THEN
            RAISE NOTICE 'المستخدم (%) يمتلك صلاحية ADMIN بالفعل في public.profiles.', v_target_user_id;
            v_affected_rows := 1;
        ELSE
            UPDATE public.profiles
            SET role = 'ADMIN'
            WHERE id = v_target_user_id;
            GET DIAGNOSTICS v_affected_rows = ROW_COUNT;
        END IF;
    END IF;

    -- 4. Post-operation verification inside transaction
    IF v_affected_rows <> 1 THEN
        RAISE EXCEPTION 'فشل التحقق من تحديث السجل: عدد الصفوف المتأثرة = % بدلاً من 1.', v_affected_rows
            USING ERRCODE = 'P0001';
    END IF;

    SELECT role
    INTO v_updated_role
    FROM public.profiles
    WHERE id = v_target_user_id;

    IF v_updated_role IS DISTINCT FROM 'ADMIN' THEN
        RAISE EXCEPTION 'فشل تعيين صلاحية ADMIN للمستخدم (%). القيمة الحالية للدور هي: %.', v_target_user_id, COALESCE(v_updated_role, 'NULL')
            USING ERRCODE = 'P0001';
    END IF;

    RAISE NOTICE 'تم التحقق بنجاح: المستخدم (%) أصبح بدور % في public.profiles.', v_target_user_id, v_updated_role;
END $$;

COMMIT;

-- ============================================================================
-- Post-Execution Verification Query (run separately if desired):
-- SELECT p.id, u.email, p.display_name, p.role, p.created_at, p.updated_at
-- FROM public.profiles p
-- JOIN auth.users u ON u.id = p.id
-- WHERE p.id = '44025f86-76f6-4082-b6ba-fb17a70e37a3';
-- ============================================================================
