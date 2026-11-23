# KNOWN_ISSUES — المشاكل المعروفة والتعارضات الموثقة

## 1. تعارضات التوثيق مع الكود الفعلي المكتشفة عند الفحص السابق
1. **فروق التوقيعات التاريخية في `FEATURES_SPEC.md`:** جرى توثيق ومطابقة توقيعات `HousePalette(wallLeftHex, wallRightHex, ...)` و`CityTileType.ROAD` الفعلية بالكامل.
2. **قيود التفرّد في قاعدة البيانات (`uq_plots_grid_coord`, `uq_houses_grid_coord`):** عولجت في `part_15_city_builder_constraints_indexes.sql` عبر مشغّلات تداخل المستطيلات.
3. **ملاحظة اسم المالك المحجوب (`house_public_details.resident_name`):** قد يُخزّن الخادم سلسلة فارغة `""` (`part_18`) أو نص `"غير معروض"` (`HousePrivacySanitizer`). يعالج `SupabaseMapper` الحالتين بتعيين `allowOwnerNamePublic = false` وعرض عنوان البديل في Explorer دون كشف الاسم.

## 2. قيود وملحوظات التشغيل الحالية (المرحلة الثالثة)
1. **اعتماد بيئة Supabase الحية على الترحيلات الأمامية (`part_14`–`part_18`):**
   - لم تُنفَّذ سكربتات `part_14` إلى `part_18` على خادم Supabase الحي بعد حتى يُنفّذها المالك يدويًا عبر **Supabase SQL Editor**.
   - **الأثر قبل التنفيذ:** يعتمد `:core-data` قراءة متسامحة via `CityBuilderServerCapabilities`، بينما تتطلب كتابة عناصر City Builder، وإرسال طلبات الزوار (`submit_house_request`)، وإدارة الحالات السبع وخصوصية اسم المالك (`admin_set_house_public_visibility`) تطبيق `part_14`–`part_18`.
2. **اختبارات البيئة الحية والمحاكي الميداني:**
   - في بيئة البناء السحابية لا يتوفر محاكي تفاعلي أو `adb` أو مفاتيح Secrets حية؛ لذلك جرى التحقق من المنظومة عبر 93 اختبار وحدة و`Robolectric` و`Ktor MockEngine` (وتخطي اختباري `LiveSupabaseIntegrationTest` لغياب مفاتيح `SUPABASE_URL`/`SUPABASE_ANON_KEY`) وبناء حزمتي `app-debug.apk` و`app-admin-debug.apk`.
   - يتبقى قياس الأداء الفعلي (`60 FPS`) والمزامنة الحية بين جهازَي الإدارة والمشاهدة بعد تشغيل `part_14`–`part_18` وتوفير مفاتيح Secrets.
3. **تنزيل تبعيات Robolectric على الأجهزة غير المتصلة:**
   - في البيئات التي لا يكون فيها مستودع Maven مخبأً تحت `${user.home}/.m2/repository`، قد يحاول Robolectric تنزيل `android-all-instrumented` من Maven Central؛ تمت معالجة ذلك في `app/build.gradle.kts` و`app-admin/build.gradle.kts` عبر التوجيه الشرطي إلى المسار المخبأ محليًا عند وجوده.
