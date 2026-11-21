# CHANGELOG — سجل التغييرات

## [0.4.2] - 2026-11-21 — استعادة نظام مصادقة المرحلة الرابعة (Supabase Auth + دور ADMIN)
### أُعيد / عُدّل
- استعادة `SupabaseAdminAuthRepository` (`AdminAuthManager` و`AdminAuthState`) لتسجيل الدخول بالبريد الإلكتروني وكلمة المرور عبر **Supabase Auth** مع التحقق من `public.profiles.role = 'ADMIN'`.
- استعادة `AdminLoginScreen` وزر تسجيل الخروج (`admin_sign_out_button`) في `AdminAppScreen`، مع حالات `Unauthenticated` و`AccessDenied` (لرفض حسابات `VIEWER`) و`SessionExpired` و`CheckingSession` و`Unconfigured`.
- تفعيل تحميل وتجديد جلسة الإدارة بأمان في `SupabaseClientProvider.getOrCreateAdminClient` و`SupabaseAdminAuthRepository.restoreAndVerifySession` / `ensureAuthenticatedAdminForMutation`.
- تحديث `SupabaseErrorTranslator` و`SupabaseCityAdminRepository` و`AdminViewModel` و`AdminDashboardScreen` و`strings.xml`.
- تحديث `SupabaseRepositoriesTest` و`LiveSupabaseIntegrationTest` و`AdminViewModelTest` و`AdminRobolectricTest` لتغطية دخول `ADMIN`، رفض `VIEWER`، تجديد الجلسة، انتهاء الجلسة (`401`)، فقدان الصلاحية (`42501`)، وتسجيل الخروج.
- إصلاح قيم `.env.example` الافتراضية لتوليد `BuildConfig.java` بشكل سليم عند غياب `.env`، وإضافة `supabase/sql/part_13_assign_owner_admin.sql`.

### أُزيل من التطبيق
- إزالة `AdminDeviceCredentialStore` (`KeystoreAdminDeviceCredentialStore`) و`SupabaseDeviceAuthRepository` و`AdminDeviceActivationScreen` و`KeystoreDeviceCredentialStoreTest` وترويستي `x-admin-device-id` / `x-admin-device-token` من مسار المصادقة والإدارة دون حذف أي جداول أو بيانات في Supabase.

## [0.4.1] - 2026-11-20 — تحديث نظام دخول الإدارة: اعتماد الجهاز لمرة واحدة وAndroid Keystore (ملغى بموجب [0.4.2])
### أُضيف
- جدولا `admin_activation_codes` و`admin_devices` ودوال `part_12_device_auth_functions.sql` (`create_admin_activation_code`, `activate_admin_device`, `verify_admin_device`, `revoke_admin_device`, `revoke_admin_activation_code`) ببصمات SHA-256 فقط وعزل RLS كامل (`USING (false)`).
- مخزن الاعتماد المشفّر `AdminDeviceCredentialStore` (`KeystoreAdminDeviceCredentialStore`) في `:core-data` باستخدام **Android Keystore (`AES/GCM/NoPadding`)**.
- مستودع `SupabaseDeviceAuthRepository` مع التحقق الخادمي عند تشغيل التطبيق وقبل العمليات الإدارية الحساسة، وفصل ترويستي الإدارة (`x-admin-device-id`, `x-admin-device-token`) في `SupabaseClientProvider.getOrCreateAdminClient`.
- واجهة `AdminDeviceActivationScreen` لحالات `CheckingDevice` و`Unenrolled` و`Activating` و`Revoked` و`OfflineVerificationBlocked`.
- اختبارات `KeystoreDeviceCredentialStoreTest` وتوسيع `SqlSchemaVerificationTest` و`SupabaseRepositoriesTest` و`AdminRobolectricTest`.

### أُزيل / استُبدل
- إزالة شاشة تسجيل الدخول التقليدية بالبريد الإلكتروني وكلمة المرور (`AdminLoginScreen`) ومستودع `SupabaseAdminAuthRepository` وزر تسجيل الخروج من `:app-admin`؛ يدخل الجهاز المعتمد مباشرةً إلى `Dashboard`.

## [0.4.0] - 2026-11-20 — المرحلة 4: تكامل Supabase الخلفي، المصادقة، RLS، والمزامنة الحية
### أُضيف
- ملفات SQL مقسمة (< 500 سطر لكل ملف) في `supabase/sql/` (`part_01` إلى `part_11` + `README.md`) لبناء الجداول (`profiles`, `city_layout`, `plots`, `houses`, `house_private_details`, `house_public_details`, `house_requests`)، القيود المكانية، الدوال الذرية `admin_*`، سياسات RLS، بث Realtime، وبيانات البذر الأولية للـ 12 قطعة و12 بيتًا.
- طبقة Supabase المركزية في `:core-data` (`SupabaseConfig`, `SupabaseClientProvider`, `SupabaseDto`, `SupabaseMapper`, `SupabaseErrorTranslator`, `ConnectivityChecker`, `SupabaseAdminAuthRepository`, `SupabaseCityExplorerRepository`, `SupabaseCityAdminRepository`).
- شاشة تسجيل دخول المدير (`AdminLoginScreen`) في `:app-admin` المرتبطة بـ Supabase Auth وفحص دور `ADMIN` في `profiles` دون بيانات افتراضية أو تسجيل عام.
- قالب `.env.example` لتمرير `SUPABASE_URL` و`SUPABASE_ANON_KEY` بأمان عبر `BuildConfig`.
- اختبارات المرحلة 4: `SupabaseConfigTest`، `SqlSchemaVerificationTest`، `SupabaseRepositoriesTest`، `LiveSupabaseIntegrationTest` (في `:core-data`)، واختبارات حالات `Empty`/`Offline`/`Error` وبوابة المصادقة في `:app` و`:app-admin`.

### عُدّل
- تحديث `CityDataState` في `:core-data` بإضافة `Empty` و`Offline`، وتوسيع `AdminHouseDraft` و`HouseResidentSourceRecord` ببيانات التواصل الخاصة وموافقات النشر المنفصلة.
- ربط `MainActivity` و`CityExplorerViewModel` في `:app` و`MainActivity` و`AdminViewModel` في `:app-admin` بمستودعات Supabase مع إذني `INTERNET` و`ACCESS_NETWORK_STATE`.
- تحديث `CityExplorerScreen` و`AdminAppScreen` و`AdminDashboardScreen` لعرض حالات التحميل والفراغ وعدم الاتصال والأخطاء والوضع المباشر بوضوح.

## [0.3.0] - 2026-11-20 — المرحلة 3: تطبيق الإدارة المستقل (`:app-admin`) ومسار البناء المزدوج
### أُضيف
- وحدة تطبيق الإدارة المستقلة `:app-admin` (`com.aistudio.madinataltayibin.admin.m4p9qx`) بأيقونة تكيفية خاصة وخطوط محلية وتجربة عربية متجاوبة عبر 4 أقسام: لوحة التحكم، إدارة البيوت، إدارة الأراضي، والخريطة التفاعلية.
- حزمة الإدارة في `:core-data`: `AdminModels.kt`، واجهة `CityAdminRepository`، ومستودع `LocalCityAdminRepository` (بيانات تجريبية محلية معزولة بـ 9 بيوت مشغولة و3 أراضٍ متاحة).
- اختبارات المرحلة 3: `LocalCityAdminRepositoryTest` (في `:core-data`)، و`AdminViewModelTest` و`AdminRobolectricTest` (في `:app-admin`)، واختبار النقر على القطع في `CityHitTestingTest` (في `:core-city`).
- مسار GitHub Actions (`.github/workflows/android-dual-apk.yml`) لتشغيل اختبارات الوحدات الأربع وبناء ملفي APK مستقلين (`:app` و`:app-admin`).

### عُدّل
- ترقية `:core-city` (`CityModels.kt`، `PlotManager.kt`، `StarterCityMap.kt`، `RoadRenderer.kt`، `CityRenderer.kt`، `CityEngine.kt`، `CityCanvas.kt`) لدعم تحديد وتظليل قطع الأراضي وتعديل بيانات البيوت وألوانها من النواة المشتركة.
- تحديث `settings.gradle.kts` لإدراج `:app-admin`.
- تحديث `PROJECT_STATUS.md` و`PROJECT_STRUCTURE.md` و`NEXT_TASK.md`.

## [0.2.0] - 2026-11-20 — المرحلة 2: تطبيق الاستكشاف والنواة المشتركة
### أُضيف
- وحدة `:core-city` (مكتبة Android مشتركة تضم محرك المدينة الأيزومتري واختباراته).
- وحدة `:core-data` (مكتبة Android مشتركة تضم `CityExplorerRepository`، `LocalCityExplorerRepository`، `HousePrivacySanitizer`، ونماذج `PublicHouseDetails` / `CityDataState`).
- اختبارات `:core-data` (`HousePrivacySanitizerTest`, `LocalCityExplorerRepositoryTest`) واختبارات Explorer (`CityExplorerViewModelTest` وتوسيع `ExampleRobolectricTest`).

### عُدّل
- تحديث `CityExplorerViewModel` و`CityExplorerScreen` للعمل عبر `CityExplorerRepository`، مع عرض المعلومات المنشورة، ومعالجة البيانات غير المتوفرة، وحماية الخصوصية، والتجاوب مع الشاشات العريضة، ودعم `BackHandler`.
- تحديث `settings.gradle.kts` وroot `build.gradle.kts` و`gradle/libs.versions.toml` و`app/build.gradle.kts` لربط الوحدات المشتركة.
- تحديث `PROJECT_STRUCTURE.md` و`PROJECT_STATUS.md` و`NEXT_TASK.md`.

## [0.1.0] - 2026-11-20 — المرحلة 1: محرك مدينة الطيبين (Isometric City Engine)
### أُضيف
- حزمة محرك المدينة المعزول `com.example.cityengine` وواجهة العرض الأولية والاختبارات والأيقونة والخطوط.

## [0.0.1] - 2026-11-20 — المرحلة 0: التحليل والتخطيط وتأسيس التوثيق
### أُضيف
- إنشاء ملفات التوثيق التسعة المعتمدة للمشروع وتهيئة اسم التطبيق في `metadata.json` و`strings.xml`.
