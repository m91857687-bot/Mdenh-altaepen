# CHANGELOG — سجل التغييرات

## [0.7.0] - 2026-11-23 — المرحلة الثالثة: التكامل النهائي، تطبيق المشاهدة، الدول العربية الـ22، والطلبات
### أُضيف
- **منظومة الدول العربية الـ 22 والأعلام الرسمية (`:core-city`):**
  - 22 أصل `VectorDrawable` في `core-city/src/main/res/drawable/flag_*.xml` لكافة دول جامعة الدول العربية، مع سجل موحد `ArabCountriesRegistry` ومكوّن `ArabFlagBadge`.
  - دعم عنصر البيئة `CITY_FLAG` في شريط الأدوات والمحرر وبطاقات الفحص لعرض أعلام الدول داخل المدينة أو على المنازل، مع اختبار `ArabCountriesRegistryTest.kt`.
- **طلبات بناء المنازل (`submit_house_request` -> `AdminRequestsScreen`):**
  - نماذج الطلبات وحالاتها السبع `HouseRequestModels.kt`، وحوار `HouseRequestDialog` للزوار بدون حساب وبدون حقل عدد أفراد الأسرة.
  - تبويب الطلبات الجديد `AdminRequestsScreen` (`AdminTab.REQUESTS`) في `:app-admin` لتصفية الطلبات وتحديث حالاتها وتخصيص قطعة الأرض وبناء المنزل مباشرة (`assignRequestPlotAndBuildHouse`).
- **الذاكرة الدائمة دون اتصال وخصوصية المنازل والمباني:**
  - `ExplorerSnapshotCache` لحفظ واسترجاع آخر لقطة معتمدة في تطبيق المشاهدة عند انقطاع الإنترنت.
  - حقول `houseTitle`، و`publicStatus`، و`allowOwnerNamePublic` للمنازل، و`descriptionAr` و`publicInfoAr` للمباني العامة، ومتصفح الأحياء في تطبيق المشاهدة (`DistrictsBrowserDialog`).
  - تفكيك `CityExplorerHouseCard.kt` و`AdminEditorPreviewBuilder.kt` لحفظ جميع ملفات المشروع تحت `500` سطر.
- **ترحيلات Supabase الأمامية والاختبارات:**
  - إضافة `part_17_phase3_house_requests_upgrade.sql` و`part_18_phase3_city_metadata_and_flags.sql` وتوسيع `Phase3RequestsAndCacheTest.kt` و`SqlSchemaVerificationTest.kt` واختبارات `:app` / `:app-admin`.

### حُدّث
- ضبط `SupabaseMapper` لمعالجة اسم المالك المحجوب (`"غير معروض"` أو النص الفارغ) بأمان وتحديث `AdminRpcPayloadBuilder` و`SupabaseCityAdminRepository`.
- تهيئة مستودع Robolectric المحلي الشرطي في `app/build.gradle.kts` و`app-admin/build.gradle.kts` لتفادي فشل الجلب الشبكي (`HTTP 429`) عند توفر الحزم المخبأة محليًا.

## [0.6.0] - 2026-11-23 — المرحلة الثانية (City Builder): المحرر المرئي التفاعلي والإدارة
### أُضيف
- **تفاعل المحرر داخل `:core-city`:** دعم `EditorPreviewOverlay` (`EditorPreviewKind`), ومستمعي التفاعل عبر الخريطة (`onBuildingTapped`, `onPropTapped`, `onRoadTapped`, `onGridCoordTapped`, `onGridCoordDragged`)، والتظليل المرئي للتحديد المتعدد والمعاينة الصالحة/المتعارضة، وإلغاء التكرار/الأخطاء المعمارية في `SpatialValidator` و`CityLayoutEditor`.
- **قدرات التعديل والتراجع في `:core-data`:**
  - نماذج مسودات البناء (`CityBuilderDataModels.kt` و`CityBuilderServerCapabilities`).
  - مدير التراجع والإعادة الآمن من التعارضات `CityEditorHistoryManager` (`history/CityEditorHistoryManager.kt`).
  - تفويضات التخزين والمزامنة المنفصلة (`LocalCityBuilderDelegate.kt`, `SupabaseCityBuilderDelegate.kt`, `SupabasePlotLayoutDelegate.kt`, `SupabaseAdminRealtimeSync.kt`) لحفظ جميع الملفات أقل من 500 سطر.
  - اختبارات `CityEditorHistoryManagerTest`, `SupabaseCityBuilderTest`, و`SupabaseAdminAuthRepositoriesTest`.
- **المحرر المرئي التفاعلي في `:app-admin`:**
  - `AdminEditorState.kt` و`AdminEditorDelegate.kt` لتشغيل المعاينة الفورية، التأكيد قبل الحفظ، والربط مع التراجع/الإعادة.
  - مكونات الخريطة والحوارات (`AdminMapEditorComponents.kt`, `AdminMapInspectorCard.kt`, `AdminBuilderDialogs.kt`, `AdminHouseActionDialogs.kt`) لدعم توسعة الشبكة، الأراضي (إنشاء، نقل، تحجيم، تدوير، تقسيم أفقي/عمودي، دمج، تفعيل/تعطيل، ربط الأحياء، حذف، والتحديد المتعدد الجماعي)، فرش الشوارع/الأرصفة/معابر المشاة/الحدائق/الساحات/المسح و`RoadSegment`، الأحياء، المباني العامة الـ 12، عناصر البيئة، ومصادر الإضاءة وإعدادات `DAY`/`SUNSET`/`NIGHT`.
  - شاشة التحقق في الاختبارات `AdminBuilderRobolectricTest.kt`.
- **الاستكشاف الموسّع (قراءة فقط) والتعريب:**
  - إضافة شريط الأحياء وبطاقات الفحص القرائية في `:app` (`CityExplorerDetailCards.kt`) مع إخفاء أي أدوات تعديل أو بيانات خاصة.
  - ملفات موارد اللغة الإنجليزية `app/src/main/res/values-en/strings.xml` و`app-admin/src/main/res/values-en/strings.xml` مع توجيه `LocalLayoutDirection` حسب اللغة (`RTL`/`LTR`).

### حُدّث
- تحديث `AdminAppScreen.kt` لإدارة تراكب الـ `Snackbar` مع لافتة المعاينة/بطاقة الفحص، وتحديث `AdminViewModel.kt`, `AdminMapScreen.kt`, `AdminHouseDialogs.kt`, و`AdminPlotsScreen.kt`.
- تحديث `CityExplorerViewModel.kt` و`CityExplorerScreen.kt` وتوسيع `ExampleRobolectricTest.kt` و`CityExplorerViewModelTest.kt`.

## [0.5.0] - 2026-11-22 — المرحلة الأولى (City Builder): تأسيس المدينة وتوسيع محركها
### أُضيف
- **منظومة الأصول الرسومية وذاكرة التخزين:** 12 أصل `VectorDrawable` نظيفًا في `core-city/src/main/res/drawable/asset_*.xml` + سجل `CityAssetRegistry` + مخزن `CityAssetCache` (ذاكرة LRU آمنة عند `context = null`).
- **نماذج التخطيط المتقدم في `:core-city`:** ملف `CityBuilderModels.kt` (`BuildingRotation`, `DistrictZone`, `TileTextureVariant`, `RoadSegment`, `PublicBuildingType` بـ 12 نوعًا، `PublicBuilding`, `EnvironmentPropType` بـ 15 نوعًا، `EnvironmentProp`, `PropLayer`, `TimeOfDayPhase`, `LightingState`, `CityLightSource`, `GridFootprint`, `HouseDesignStyle` بـ 5 أنماط).
- **أدوات الأداء والتوسع:** `SpatialGridIndex` (`SpatialIndex.kt`) و`ViewportCuller` + `LodTier` + `VisibleGridBounds` (`ViewportCuller.kt`) للتوسع السلس لآلاف العناصر.
- **موسّع المدينة والتحرير البرمجي:** `CityLayoutEditor` + خريطة العرض الموسّعة `StarterCityMap.createExpandedShowcaseCity(phase)` (`26x26`، 3 أحياء، مبانٍ عامة، طرق متصلة، وزينة وإضاءة ليلية/غروب) مع الحفاظ التام على `createScene()` (`12x12`).
- **طبقات الرسم المحسّنة:** `LightingRenderer` (`DAY`/`SUNSET`/`NIGHT`، ظلال، برك ضوء)، `PublicBuildingRenderer` (12 مبنى عامًا بأبعاد `WxH`)، `EnvironmentPropRenderer` (15 عنصر بيئة وزينة).
- **ترحيلات Supabase الأمامية (`supabase/sql/`):** `part_14_city_builder_schema.sql` و`part_15_city_builder_constraints_indexes.sql` و`part_16_city_builder_functions_rls_realtime.sql` (أقل من 500 سطر لكل ملف، دون أي حذف أو مساس ببيانات المرحلة الرابعة) وتحديث `supabase/sql/README.md`.
- **مساعد وحماية `:core-data`:** `AdminRpcPayloadBuilder.kt` وقواعد ProGuard/R8 في `core-data/consumer-rules.pro` للنماذج المتسلسلة، وتوثيق التعارضات في `KNOWN_ISSUES.md`.
- **اختبارات جديدة ومحدّثة:** `CityBuilderEngineTest.kt` في `:core-city` وتوسيع `SqlSchemaVerificationTest.kt` و`SupabaseRepositoriesTest.kt` في `:core-data`.

### حُدّث
- ترقية `CityModels.kt` و`IsoProjection.kt` و`CameraController.kt` و`SpatialValidator.kt` و`PlotManager.kt` و`RoadRenderer.kt` و`HouseRenderer.kt` و`CityRenderer.kt` و`CityEngine.kt` و`CityCanvas.kt` لدعم التمدد الديناميكي > `12x12`، والأراضي متعددة الخلايا والدوران وحالة `UNAVAILABLE` والتقسيم/الدمج/تغيير الحجم، ومستويات المباني والإنارة.
- تحديث `SupabaseDto.kt` و`SupabaseMapper.kt` و`SupabaseCityExplorerRepository.kt` و`SupabaseCityAdminRepository.kt` لقراءة بيانات City Builder الجديدة اختياريًا بأمان (`runCatching`) دون كسر التوافق مع مخطط المرحلة الرابعة قبل تنفيذ `part_14`–`part_16`.
- مواءمة `AdminMapScreen.kt` و`AdminPlotsScreen.kt` في `:app-admin` مع حالة `PlotStatus.UNAVAILABLE` وأبعاد القطع المتعددة.
- تحديث وثائق المشروع (`PROJECT_STRUCTURE.md`, `ARCHITECTURE.md`, `FEATURES_SPEC.md`, `DECISIONS.md`, `PROJECT_STATUS.md`, `NEXT_TASK.md`).

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
