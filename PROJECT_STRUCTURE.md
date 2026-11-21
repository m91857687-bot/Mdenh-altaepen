# PROJECT_STRUCTURE — هيكل الملفات والوحدات

## 1. البنية الحالية للمستودع (بعد المرحلة الرابعة)
يضم المستودع تطبيقين مستقلين (`:app` للزوار/الاستكشاف و`:app-admin` للإدارة) يتشاركان مكتبتي النواة (`:core-city` و`:core-data`) مع طبقة Supabase المركزية في `:core-data`، ومخطط SQL المقسم في `supabase/sql/`، ومسار GitHub Actions لإنتاج ملفي APK مستقلين:

```text
MadinatAlTayibin/
├── .env.example                                # قالب SUPABASE_URL و SUPABASE_ANON_KEY (دون أي مفاتيح سرية)
├── .github/workflows/
│   └── android-dual-apk.yml                    # سير عمل GitHub Actions لاختبار 4 وحدات وإنتاج ملفي APK
│
├── supabase/sql/                               # مخطط قاعدة البيانات المقسم (< 500 سطر لكل ملف)
│   ├── README.md                               # ترتيب التنفيذ والاستعلامات التحققية
│   ├── part_01_extensions.sql                  # pgcrypto و set_updated_at()
│   ├── part_02_profiles.sql                    # profiles + is_admin() (التحقق من role = 'ADMIN')
│   ├── part_03_city_layout.sql                 # city_layout + plots
│   ├── part_04_houses_and_privacy.sql          # houses + house_private_details + house_public_details
│   ├── part_05_house_requests.sql              # house_requests
│   ├── part_06_relationships_constraints.sql   # العلاقات + القيود الفريدة + التحقق المكاني في DB
│   ├── part_07_indexes.sql                     # فهارس الأداء
│   ├── part_08_functions.sql                   # مزامنة البيانات العامة + دوال admin_* الذرية
│   ├── part_09_rls_policies.sql                # سياسات RLS على الجداول
│   ├── part_10_realtime.sql                    # تفعيل REPLICA IDENTITY FULL و supabase_realtime
│   ├── part_11_seed_data.sql                   # بيانات البذر الآمنة للـ 12 قطعة و12 بيتًا
│   ├── part_12_device_auth_functions.sql       # دوال قديمة محفوظة في المستودع دون استخدام في التطبيق
│   └── part_13_assign_owner_admin.sql          # سكربت ترقية حساب المالك إلى دور ADMIN في profiles
│
├── core-city/                                  # وحدة المكتبة المشتركة لمحرك المدينة الأيزومتري
│   ├── build.gradle.kts
│   └── src/
│       ├── main/java/com/example/cityengine/
│       │   ├── model/CityModels.kt
│       │   ├── projection/IsoProjection.kt
│       │   ├── camera/CameraController.kt
│       │   ├── world/{PlotManager,SpatialValidator,StarterCityMap}.kt
│       │   ├── render/{CityRenderer,RoadRenderer,HouseRenderer,TreeRenderer,FlagRenderer}.kt
│       │   ├── CityEngine.kt
│       │   └── ui/CityCanvas.kt
│       └── test/java/com/example/cityengine/
│           ├── IsoProjectionTest.kt
│           ├── CameraControllerTest.kt
│           ├── SpatialValidatorTest.kt
│           ├── StarterCityMapTest.kt
│           └── CityHitTestingTest.kt
│
├── core-data/                                  # وحدة البيانات المشتركة وطبقة Supabase المركزية
│   ├── build.gradle.kts
│   └── src/
│       ├── main/java/com/example/core/data/
│       │   ├── model/{ExplorerModels,AdminModels}.kt
│       │   ├── privacy/HousePrivacySanitizer.kt
│       │   ├── repository/{CityExplorerRepository,CityAdminRepository}.kt
│       │   ├── local/{LocalCityExplorerRepository,LocalCityAdminRepository}.kt
│       │   └── supabase/
│       │       ├── SupabaseConfig.kt
│       │       ├── SupabaseClientProvider.kt
│       │       ├── SupabaseDto.kt
│       │       ├── SupabaseMapper.kt
│       │       ├── ConnectivityChecker.kt
│       │       ├── SupabaseErrorTranslator.kt
│       │       ├── SupabaseAdminAuthRepository.kt
│       │       ├── SupabaseCityExplorerRepository.kt
│       │       └── SupabaseCityAdminRepository.kt
│       └── test/java/com/example/core/data/
│           ├── HousePrivacySanitizerTest.kt
│           ├── LocalCityExplorerRepositoryTest.kt
│           ├── LocalCityAdminRepositoryTest.kt
│           ├── SupabaseConfigTest.kt
│           ├── SqlSchemaVerificationTest.kt
│           ├── SupabaseRepositoriesTest.kt
│           └── LiveSupabaseIntegrationTest.kt
│
├── app/                                        # تطبيق الاستكشاف للزوار (app-explorer host)
│   ├── build.gradle.kts                        # applicationId: com.aistudio.madinataltayibin.k7m2px
│   └── src/
│       ├── main/java/com/example/
│       │   ├── explorer/{CityExplorerViewModel,CityExplorerScreen}.kt
│       │   ├── ui/theme/{Color,Type,Theme}.kt
│       │   └── MainActivity.kt                 # مرتبط بـ BuildConfig.SUPABASE_URL / ANON_KEY
│       └── test/java/com/example/
│           ├── CityExplorerViewModelTest.kt
│           ├── ExampleRobolectricTest.kt
│           └── ExampleUnitTest.kt
│
└── app-admin/                                  # تطبيق الإدارة المستقل الخاص بمالك المدينة
    ├── build.gradle.kts                        # applicationId: com.aistudio.madinataltayibin.admin.m4p9qx
    ├── proguard-rules.pro
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── res/                            # أيقونة تكيفية خاصة، strings.xml (إدارة الطيبين)، خطوط محلية
        │   └── java/com/example/admin/
        │       ├── MainActivity.kt             # مرتبط بـ BuildConfig.SUPABASE_URL / ANON_KEY
        │       ├── AdminViewModel.kt
        │       └── ui/
        │           ├── theme/{Color,Type,Theme}.kt
        │           ├── AdminLoginScreen.kt
        │           ├── AdminAppScreen.kt
        │           ├── AdminDashboardScreen.kt
        │           ├── AdminHousesScreen.kt
        │           ├── AdminPlotsScreen.kt
        │           ├── AdminMapScreen.kt
        │           └── AdminHouseDialogs.kt
        └── test/java/com/example/admin/
            ├── AdminViewModelTest.kt
            └── AdminRobolectricTest.kt
```

---

## 2. مخرجات البناء المستقلة
- **تطبيق الاستكشاف (`:app`):** `app/build/outputs/apk/debug/app-debug.apk`
- **تطبيق الإدارة (`:app-admin`):** `app-admin/build/outputs/apk/debug/app-admin-debug.apk`
- **الاعتماديات:** تعتمد وحدتا `:app` و`:app-admin` على `project(":core-city")` و`project(":core-data")` بدون تكرار كود المحرك أو التحقق المكاني وبدون أي اعتمادات Firebase.
