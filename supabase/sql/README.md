# مجلد ملفات SQL لقاعدة بيانات Supabase — مدينة الطيبين (المرحلة 4)

تم تقسيم ملفات SQL الأساسية إلى أجزاء مستقلة ومرتبة بحيث يقل كل ملف عن **500 سطر** لتجنب حد التنفيذ في Supabase SQL Editor، مع الحفاظ على جميع الجداول والدوال والسياسات السابقة دون حذف.

## قائمة الملفات وترتيب التنفيذ الإلزامي

نفّذ الملفات بالترتيب الرقمي الدقيق في **Supabase SQL Editor**:

| الترتيب | الملف | الوظيفة | الاعتماديات |
|---|---|---|---|
| 1 | `part_01_extensions.sql` | تفعيل `pgcrypto` ودالة `set_updated_at()` | لا يوجد |
| 2 | `part_02_profiles.sql` | جدول `profiles` ودالة `is_admin()` (تدعم `profiles.role = 'ADMIN'`) | `part_01`, `auth.users` |
| 3 | `part_03_city_layout.sql` | جدولي `city_layout` و`plots` | `part_01` |
| 4 | `part_04_houses_and_privacy.sql` | جداول `houses` و`house_private_details` و`house_public_details` | `part_01` |
| 5 | `part_05_house_requests.sql` | جدول `house_requests` المجهز للمرحلة الخامسة | `part_01`, `part_02` |
| 6 | `part_06_relationships_constraints.sql` | المفاتيح الأجنبية والقيود الفريدة ومشغّلات التحقق المكاني | `part_03`, `part_04` |
| 7 | `part_07_indexes.sql` | فهارس الاستعلامات السريعة | `part_02`–`part_05` |
| 8 | `part_08_functions.sql` | دوال المزامنة الآمنة لـ `house_public_details` ودوال الإدارة `admin_*` | `part_02`, `part_03`, `part_04`, `part_06` |
| 9 | `part_09_rls_policies.sql` | تفعيل RLS ومنح الصلاحيات وسياسات الزوار والإدارة | `part_02`–`part_05` |
| 10 | `part_10_realtime.sql` | تفعيل `REPLICA IDENTITY FULL` وإضافة الجداول إلى `supabase_realtime` | `part_03`, `part_04` |
| 11 | `part_11_seed_data.sql` | تعبئة تخطيط المدينة والأراضي الـ 12 وبيوت المرحلة الأولى الـ 12 دون تكرار | `part_03`, `part_04`, `part_06`, `part_08` |
| 12 | `part_12_device_auth_functions.sql` | دوال قديمة محفوظة في قاعدة البيانات دون استدعاء من التطبيق | `part_01`, `part_02`, `part_09` |
| 13 | `part_13_assign_owner_admin.sql` | ترقية حساب المالك المُسجَّل في `auth.users` إلى دور `ADMIN` في `public.profiles` | `part_02` |

## نظام المصادقة المعتمد لتطبيق الإدارة (`:app-admin`)

يعتمد `:app-admin` على **Supabase Auth (البريد الإلكتروني وكلمة المرور)** مع التحقق الصارم من أن `public.profiles.role = 'ADMIN'`:
1. أنشئ حساب المالك في **Supabase Authentication -> Users** (أو سجّله بالبريد الإلكتروني وكلمة المرور).
2. شغّل `part_13_assign_owner_admin.sql` (أو حدّث صف المستخدم في `public.profiles` إلى `role = 'ADMIN'`) عبر **Supabase SQL Editor** فقط.
3. أي مستخدم بدور `VIEWER` يُمنع من دخول لوحة الإدارة ويُرفض في الواجهة وفي جميع سياسات RLS ودوال `admin_*` عبر `public.is_admin()`.
