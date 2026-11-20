# مجلد ملفات SQL لقاعدة بيانات Supabase — مدينة الطيبين (المرحلة 4)

تم تقسيم ملفات SQL إلى **12 جزءًا مستقلاً ومرتبًا** بحيث يقل كل ملف عن **500 سطر** لتجنب حد التنفيذ في Supabase SQL Editor.

## قائمة الملفات وترتيب التنفيذ الإلزامي

نفّذ الملفات بالترتيب الرقمي الدقيق في **Supabase SQL Editor**:

| الترتيب | الملف | الوظيفة | الاعتماديات |
|---|---|---|---|
| 1 | `part_01_extensions.sql` | تفعيل `pgcrypto` ودالة `set_updated_at()` | لا يوجد |
| 2 | `part_02_profiles.sql` | جدول `profiles`، جداول اعتماد الأجهزة (`admin_activation_codes`, `admin_devices`)، ودوال `is_admin_device()` و`is_admin()` | `part_01`, `auth.users` |
| 3 | `part_03_city_layout.sql` | جدولي `city_layout` و`plots` | `part_01` |
| 4 | `part_04_houses_and_privacy.sql` | جداول `houses` و`house_private_details` و`house_public_details` | `part_01` |
| 5 | `part_05_house_requests.sql` | جدول `house_requests` المجهز للمرحلة الخامسة | `part_01`, `part_02` |
| 6 | `part_06_relationships_constraints.sql` | المفاتيح الأجنبية والقيود الفريدة ومشغّلات التحقق المكاني | `part_03`, `part_04` |
| 7 | `part_07_indexes.sql` | فهارس الاستعلامات السريعة | `part_02`–`part_05` |
| 8 | `part_08_functions.sql` | دوال المزامنة الآمنة لـ `house_public_details` ودوال الإدارة `admin_*` | `part_02`, `part_03`, `part_04`, `part_06` |
| 9 | `part_09_rls_policies.sql` | تفعيل RLS ومنح الصلاحيات وسياسات الزوار والأجهزة المعتمدة | `part_02`–`part_05` |
| 10 | `part_10_realtime.sql` | تفعيل `REPLICA IDENTITY FULL` وإضافة الجداول إلى `supabase_realtime` | `part_03`, `part_04` |
| 11 | `part_11_seed_data.sql` | تعبئة تخطيط المدينة والأراضي الـ 12 وبيوت المرحلة الأولى الـ 12 دون تكرار | `part_03`, `part_04`, `part_06`, `part_08` |
| 12 | `part_12_device_auth_functions.sql` | دوال إصدار رمز التفعيل لمرة واحدة، واعتماد الجهاز، والتحقق، والإلغاء من الخادم | `part_01`, `part_02`, `part_09` |

## تفعيل جهاز الإدارة للمرة الأولى (One-Time Activation Code)

لا يعتمد التطبيق على البريد الإلكتروني أو كلمة المرور، ولا يمنح صلاحية الإدارة تلقائيًا لأي جهاز جديد:
1. نفّذ في **Supabase SQL Editor** الأمر التالي لإنشاء رمز تفعيل صالح لمرة واحدة (مثلاً لمدة 60 دقيقة):
   ```sql
   SELECT * FROM public.create_admin_activation_code('هاتف المالك الرئيسي', 60);
   ```
2. انسخ رمز التفعيل الناتج (يظهر مرة واحدة فقط في نتيجة الاستعلام بصيغة `MADINA-XXXX-XXXX-XXXX-XXXX` ولا يُخزّن نصًا عاديًا في القاعدة).
3. افتح تطبيق الإدارة `:app-admin` وأدخل رمز التفعيل مرة واحدة. يحفظ التطبيق بيانات الاعتماد مشفّرة داخل **Android Keystore** ويدخل بعدها مباشرة إلى لوحة التحكم في كل تشغيل.

## إلغاء اعتماد الجهاز من جهة الخادم (Revocation)

لاستعراض الأجهزة المعتمدة وإلغاء اعتماد أي جهاز فورًا:
```sql
-- 1. عرض الأجهزة المعتمدة
SELECT device_id, device_name, status, activated_at, last_verified_at, revoked_at
FROM public.admin_devices
ORDER BY activated_at DESC;

-- 2. إلغاء اعتماد جهاز محدد فورًا
SELECT public.revoke_admin_device('DEVICE_ID_HERE', 'فقدان الهاتف أو استبداله');
```
بمجرد الإلغاء، يرفض الخادم جميع استعلامات وعمليات الجهاز فورًا وتُمسح بيانات اعتماده محليًا ويُمنع من دخول لوحة الإدارة.
