# PROJECT_STATUS — الحالة الحالية للمشروع

- **المرحلة الحالية:** المرحلة 4 — استعادة وإتمام نظام المصادقة التقليدي (`Supabase Auth` بالبريد وكلمة المرور + `profiles.role = 'ADMIN'`) — **مكتملة وجاهزة لاستكمال مهام المرحلة الخامسة**.
- **نسبة الإنجاز:**
  - المرحلة 0 (التحليل والتوثيق): 100%
  - المرحلة 1 (محرك المدينة الأيزومتري): 100%
  - المرحلة 2 (تطبيق الاستكشاف + فصل `:core-city` و`:core-data`): 100%
  - المرحلة 3 (تطبيق الإدارة المستقل `:app-admin` + مسار GitHub Actions للـ APK المزدوج): 100%
  - المرحلة 4 (مخطط Supabase SQL + RLS + مصادقة Supabase Auth بدور `ADMIN` + مستودعات Supabase + Realtime + الحالات الشاملة): 100% (مُختبرة محليًا بالكامل عبر اختبارات Unit وRobolectric وMockEngine؛ والاختبار الحي يُشغَّل عند توفير `SUPABASE_URL` و`SUPABASE_ANON_KEY`)
  - المرحلة 5 (الاختبار الشامل، تحسين الأداء، التحقق الأمني، وتجهيز الإطلاق): قيد الإعداد (بانتظار أمر الاستكمال بعد حسم المصادقة)

## ملخص حالة المرحلة الرابعة المعتمدة حاليًا
- قاعدة البيانات في `supabase/sql/` (`part_01` إلى `part_13` مع الإبقاء على جميع الجداول والدوال والسياسات دون حذف):
  - تدعم دالة `public.is_admin()` فحص `profiles.role = 'ADMIN'` مباشرة عبر `auth.uid()` لجميع سياسات RLS ودوال `admin_*`.
  - أُضيف سكربت `part_13_assign_owner_admin.sql` لإسناد دور `ADMIN` لحساب المالك في `public.profiles` بأمان.
- طبقة `:core-data`:
  - `SupabaseAdminAuthRepository` يدير تسجيل الدخول بالبريد الإلكتروني وكلمة المرور، التحقق من `profiles.role = 'ADMIN'`، رفض حسابات `VIEWER`، استعادة الجلسة وتجديدها بأمان، والتعامل مع انتهاء الجلسة (`401`/`JWT expired`) وفقدان الصلاحيات (`403`/`42501`) وتسجيل الخروج.
  - أُزيل `AdminDeviceCredentialStore` (`KeystoreAdminDeviceCredentialStore`) و`SupabaseDeviceAuthRepository` وترويستا `x-admin-device-*` من `SupabaseClientProvider`.
  - استمرار جميع عمليات `SupabaseCityAdminRepository` (`addHouse`, `updateHouse`, `changeHouseColor`, `moveHouse`, `deleteHouse`) و`SupabaseCityExplorerRepository` و`Realtime` وحماية الخصوصية.
- تطبيق `:app-admin`:
  - واجهة `AdminLoginScreen` بالبريد الإلكتروني وكلمة المرور مع حظر لوحة التحكم لغير المصرح لهم وزر تسجيل الخروج في الشريط العلوي.
  - جميع شاشات الإدارة (`Dashboard`, `Houses`, `Plots`, `Map`, وحوارات الإضافة/التعديل/اللون/النقل/الحذف) محفوظة وتعمل بالكامل.
