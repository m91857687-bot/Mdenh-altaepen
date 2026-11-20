# CHANGELOG — سجل التغييرات

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
