# PROJECT_STATUS — الحالة الحالية للمشروع

- **المرحلة الحالية:** المرحلة 4 — تكامل Supabase الخلفي، اعتماد الجهاز لمرة واحدة، RLS، والمزامنة الحية — **مكتملة برمجيًا وجاهزة لتفعيل بيانات مشروع Supabase**.
- **نسبة الإنجاز:**
  - المرحلة 0 (التحليل والتوثيق): 100%
  - المرحلة 1 (محرك المدينة الأيزومتري): 100%
  - المرحلة 2 (تطبيق الاستكشاف + فصل `:core-city` و`:core-data`): 100%
  - المرحلة 3 (تطبيق الإدارة المستقل `:app-admin` + مسار GitHub Actions للـ APK المزدوج): 100%
  - المرحلة 4 (مخطط Supabase SQL + RLS + اعتماد الجهاز لمرة واحدة مع Android Keystore + مستودعات Supabase + Realtime + الحالات الشاملة): 100% (برمجيًا واختباريًا محليًا؛ بانتظار إدخال `SUPABASE_URL` و`SUPABASE_ANON_KEY` وتشغيل ملفات SQL في مشروعك الفعلي لتنفيذ الاختبار الحي)
  - المرحلة 5+ (واجهات تقديم طلبات البناء والموافقة/الرفض والبحث العام والإحصائيات المتقدمة): 0% (مؤجلة للمرحلة الخامسة والسادسة)

## ملخص ما تم إنجازه في المرحلة 4 (بما يشمل تحديث اعتماد الجهاز)
- إعداد 12 ملف SQL مقسمة ومنظمة في `supabase/sql/` (كل ملف أقل من 500 سطر) مع `README.md` يشرح الترتيب والاعتماديات والتحقق:
  - الجداول التسعة: `profiles`, `city_layout`, `plots`, `houses`, `house_private_details`, `house_public_details`, `house_requests`, `admin_activation_codes`, `admin_devices`.
  - القيود والمشغّلات: فريدية `plot_code` و`(grid_x, grid_y)`، منع تكرار القطعة لبيتين، التحقق المكاني من حدود الخريطة والطرق والحدائق في قاعدة البيانات، وفصل البيانات الخاصة عن جدول `house_public_details` المحمي بمشغّل مزامنة آمن.
  - دوال اعتماد الجهاز وإلغائه (`part_12_device_auth_functions.sql`): `create_admin_activation_code` (خادمي فقط)، `activate_admin_device` (استهلاك ذري لرمز التفعيل لمرة واحدة)، `verify_admin_device` (التحقق عند الإقلاع وقبل التعديلات)، `revoke_admin_device` و`revoke_admin_activation_code` (خادمي فقط) — مع تخزين بصمات SHA-256 فقط.
  - دوال الإدارة الآمنة (`admin_add_house`, `admin_update_house`, `admin_change_house_color`, `admin_move_house`, `admin_delete_house`) مع تحقق `is_admin()` وتحديث ذري متسق بين البيت والقطعة.
  - سياسات RLS شاملة على الجداول التسعة، وتفعيل `supabase_realtime`، وبيانات البذر الأولية الآمنة (`ON CONFLICT DO NOTHING`) للـ 12 قطعة و12 بيتًا.
- إضافة طبقة اتصال Supabase المركزية وحماية الاعتماد في `:core-data` (`SupabaseConfig`, `SupabaseClientProvider`, `SupabaseMapper`, `SupabaseErrorTranslator`, `ConnectivityChecker`, `AdminDeviceCredentialStore` المشفّر عبر **Android Keystore AES-GCM**, `SupabaseDeviceAuthRepository`, `SupabaseCityExplorerRepository`, `SupabaseCityAdminRepository`) مع منع استخدام أي `service_role` key في التطبيق.
- تحديث `:app-admin` لإزالة تجربة تسجيل الدخول التقليدية بالبريد وكلمة المرور وإزاحة زر تسجيل الخروج: يفتح الجهاز المعتمد والُمصدّق من الخادم لوحة الإدارة `Dashboard` مباشرةً، بينما تظهر للجهاز الجديد شاشة التفعيل الأولي لمرة واحدة (`AdminDeviceActivationScreen`) وتُحجب اللوحة تمامًا في حالات الإلغاء (`Revoked`) وانقطاع الإنترنت (`OfflineVerificationBlocked`).
