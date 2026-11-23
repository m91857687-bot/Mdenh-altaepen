# NEXT_TASK — الخطوات التالية للتشغيل والإطلاق الفعلي

بعد اكتمال **المرحلة الثالثة** واجتياز جميع الاختبارات وبناء حزمتي Debug بنجاح، تبقى خطوات الإطلاق الفعلي على خادم Supabase الحي والأجهزة الحقيقية:

1. **تنفيذ ترحيلات Supabase الأمامية بالترتيب في Supabase SQL Editor:**
   - شغّل الملفات التالية بالترتيب دون أي حذف أو مساس بالجداول والبيانات القائمة:
     1. `supabase/sql/part_14_city_builder_schema.sql`
     2. `supabase/sql/part_15_city_builder_constraints_indexes.sql`
     3. `supabase/sql/part_16_city_builder_functions_rls_realtime.sql`
     4. `supabase/sql/part_17_phase3_house_requests_upgrade.sql`
     5. `supabase/sql/part_18_phase3_city_metadata_and_flags.sql`

2. **ضبط مفاتيح الاتصال الآمنة في لوحة Secrets:**
   - أدخل `SUPABASE_URL` و`SUPABASE_ANON_KEY` (دون استخدام `service_role` مطلقًا في التطبيقين) ثم أعد البناء لتفعيل المزامنة الحية واختبارات `LiveSupabaseIntegrationTest`.

3. **الاختبار الميداني على جهازَي Android (الإدارة والمشاهدة):**
   - تثبيت حزمتي `:app-admin` و`:app` من المخرجات المبنية أو مسار CI المزدوج.
   - تسجيل الدخول في تطبيق الإدارة بحساب `ADMIN`، ونشر تعديلات المدينة (أحياء، أراضٍ، أعلام عربية، مبانٍ عامة بأوصافها، وإضاءة ليلية)، والتحقق من ظهورها لحظيًا في تطبيق المشاهدة ومن ثم عمله بالنسخة المخبأة عند فصل الإنترنت.
   - إرسال طلب بناء منزل من تطبيق المشاهدة ثم متابعته وتخصيص قطعة أرض وبناء المنزل من تبويب الطلبات في تطبيق الإدارة.
