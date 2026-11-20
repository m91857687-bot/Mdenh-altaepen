# PROJECT_STRUCTURE — هيكل الملفات والوحدات

## 1. البنية الحالية للمستودع (بعد المرحلة الثالثة)
يضم المستودع تطبيقين مستقلين (`:app` للزوار/الاستكشاف و`:app-admin` للإدارة) يتشاركان مكتبتي النواة (`:core-city` و`:core-data`) مع إبقاء `:app` كوحدة الاستكشاف المعمول بها في AI Studio، وإضافة مسار GitHub Actions لإنتاج ملفي APK مستقلين:

```text
MadinatAlTayibin/
├── .github/workflows/
│   └── android-dual-apk.yml                    # سير عمل GitHub Actions لاختبار 4 وحدات وإنتاج ملفي APK
│
├── core-city/                                  # وحدة المكتبة المشتركة لمحرك المدينة الأيزومتري
│   ├── build.gradle.kts
│   └── src/
│       ├── main/java/com/example/cityengine/
│       │   ├── model/CityModels.kt             # + HouseColorPreset, selectedPlotId, PlotSelected
│       │   ├── projection/IsoProjection.kt
│       │   ├── camera/CameraController.kt
│       │   ├── world/{PlotManager,SpatialValidator,StarterCityMap}.kt
│       │   ├── render/{CityRenderer,RoadRenderer,HouseRenderer,TreeRenderer,FlagRenderer}.kt
│       │   ├── CityEngine.kt                   # + findPlotAtScreenPoint, findPlotAtWorldPoint
│       │   └── ui/CityCanvas.kt                # + onPlotTapped اختياري
│       └── test/java/com/example/cityengine/
│           ├── IsoProjectionTest.kt
│           ├── CameraControllerTest.kt
│           ├── SpatialValidatorTest.kt
│           ├── StarterCityMapTest.kt
│           └── CityHitTestingTest.kt
│
├── core-data/                                  # وحدة البيانات المشتركة وحماية الخصوصية والإدارة المحلية
│   ├── build.gradle.kts
│   └── src/
│       ├── main/java/com/example/core/data/
│       │   ├── model/{ExplorerModels,AdminModels}.kt
│       │   ├── privacy/HousePrivacySanitizer.kt
│       │   ├── repository/{CityExplorerRepository,CityAdminRepository}.kt
│       │   └── local/{LocalCityExplorerRepository,LocalCityAdminRepository}.kt
│       └── test/java/com/example/core/data/
│           ├── HousePrivacySanitizerTest.kt
│           ├── LocalCityExplorerRepositoryTest.kt
│           └── LocalCityAdminRepositoryTest.kt
│
├── app/                                        # تطبيق الاستكشاف للزوار (app-explorer host)
│   ├── build.gradle.kts                        # applicationId: com.aistudio.madinataltayibin.k7m2px
│   └── src/
│       ├── main/java/com/example/
│       │   ├── explorer/{CityExplorerViewModel,CityExplorerScreen}.kt
│       │   ├── ui/theme/{Color,Type,Theme}.kt
│       │   └── MainActivity.kt
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
        │       ├── MainActivity.kt
        │       ├── AdminViewModel.kt
        │       └── ui/
        │           ├── theme/{Color,Type,Theme}.kt
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
