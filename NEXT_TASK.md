# NEXT_TASK — المهمة التالية

## الخطوات المطلوبة لتفعيل الاتصال الفعلي واعتماد الجهاز الأول قبل المرحلة 5

1. **تفعيل مشروع Supabase وتشغيل SQL (من طرف المالك):**
   - نفّذ ملفات `supabase/sql/part_01_extensions.sql` حتى `part_12_device_auth_functions.sql` بالترتيب داخل **Supabase SQL Editor**.
   - أضف `SUPABASE_URL` و`SUPABASE_ANON_KEY` (في `.env` أو Secrets panel في AI Studio) ثم ابنِ التطبيقين (لا تضع `service_role` أو أي رمز تفعيل داخل APK أو مستودع GitHub).

2. **اعتماد جهاز الإدارة لأول مرة:**
   - من **Supabase SQL Editor**، أنشئ رمز تفعيل لمرة واحدة (صالح مثلاً لـ 60 دقيقة):
     ```sql
     SELECT * FROM public.create_admin_activation_code('جهاز المالك الأساسي', 60);
     ```
   - انسخ الرمز الناتج (بصيغة `MADINA-XXXX-XXXX-XXXX-XXXX`). لن يُعرض هذا الرمز مرة أخرى ويُحفظ على الخادم كبصمة SHA-256 فقط.
   - افتح تطبيق `:app-admin` على جهازك وأدخل رمز التفعيل مرة واحدة. بعد التحقق، يحفظ التطبيق بيانات اعتماد الجهاز مشفّرة في **Android Keystore** ويفتح `Dashboard` مباشرةً في جميع المرات اللاحقة دون أي شاشة تسجيل دخول.

3. **إلغاء اعتماد الجهاز من الخادم (عند فقدان الجهاز أو استبداله):**
   - اعرض الأجهزة المعتمدة أو ألغِ الجهاز المطلوب عبر **Supabase SQL Editor**:
     ```sql
     SELECT device_id, device_name, status, activated_at, last_verified_at FROM public.admin_devices;
     SELECT public.revoke_admin_device('PUT_DEVICE_ID_HERE', 'تم إلغاء الجهاز من مالك النظام');
     ```
   - يُرفض الجهاز الملغى فورًا في أي عملية أو تشغيل لاحق وتُمسح بياناته المحلية تلقائيًا.

4. **التحقق الحي (اختياري عبر البيئة):**
   - لتنفيذ `LiveSupabaseIntegrationTest` ضد المشروع الحقيقي، عيّن `SUPABASE_URL` و`SUPABASE_ANON_KEY` (و`SUPABASE_ADMIN_ACTIVATION_CODE` عند الرغبة باختبار التفعيل الحي).

5. **المرحلة 5 (بانتظار موافقتك الصريحة قبل البدء):**
   - واجهة تقديم طلبات إنشاء البيوت للزوار في `:app` المرتبطة بجدول `house_requests` المجهّز في المرحلة 4.
   - واجهة مراجعة الطلبات والموافقة والرفض في `:app-admin`.
   - البحث العام المتقدم في تطبيق الاستكشاف.

> لن يبدأ تنفيذ المرحلة الخامسة إلا بعد موافقتك الصريحة.
