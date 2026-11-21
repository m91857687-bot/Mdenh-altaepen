# NEXT_TASK — المهمة التالية

## تجهيز الاتصال الفعلي واستكمال المرحلة الخامسة

1. **إعداد بيانات Supabase والبيئة:**
   - أضف قيم مشروعك `SUPABASE_URL` و`SUPABASE_ANON_KEY` في Secrets أو `.env` ثم أعد البناء (دون تضمين `SUPABASE_SERVICE_ROLE_KEY` في التطبيق).
   - تأكد من تعيين دور `ADMIN` لحساب المالك في `public.profiles` (يمكنك استخدام `supabase/sql/part_13_assign_owner_admin.sql` عبر Supabase SQL Editor دون حذف أي جداول أو بيانات قائمة).

2. **الاختبار الحي الاختياري ضد Supabase:**
   - لتشغيل `LiveSupabaseIntegrationTest` ضد المشروع الحقيقي، مرّر `SUPABASE_URL` و`SUPABASE_ANON_KEY` (و`SUPABASE_ADMIN_EMAIL` / `SUPABASE_ADMIN_PASSWORD` لاختبار تسجيل دخول الإدارة الحي).

3. **استكمال مهام المرحلة الخامسة (بانتظار توجيهك):**
   - مراجعة الاختبارات الشاملة لمحرك المدينة و`:app` و`:app-admin` وأمان Supabase.
   - مراجعة تحسينات الأداء وتجهيز الإطلاق (`release` / GitHub Actions).
