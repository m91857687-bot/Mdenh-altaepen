# PROJECT_STRUCTURE — هيكل الملفات والوحدات

## 1. البنية الحالية للمستودع (بعد المرحلة الثالثة: التكامل النهائي والدول الـ22 والطلبات)
يضم المستودع تطبيقين مستقلين (`:app` للزوار/الاستكشاف و`:app-admin` للإدارة) يتشاركان مكتبتي النواة (`:core-city` و`:core-data`) مع طبقة Supabase المركزية في `:core-data`، ومخطط SQL المقسم (من `part_01` إلى `part_18`) في `supabase/sql/`، وملفات التعريب AR/EN، وجميع الملفات `< 500` سطر:

```text
MadinatAlTayibin/
├── .env.example                                # قالب SUPABASE_URL و SUPABASE_ANON_KEY (دون أي مفاتيح سرية)
├── .github/workflows/
│   └── android-dual-apk.yml                    # سير عمل GitHub Actions لاختبار 4 وحدات وإنتاج ملفي APK
│
├── supabase/sql/                               # مخطط قاعدة البيانات المقسم (< 500 سطر لكل ملف)
│   ├── README.md                               # ترتيب التنفيذ والاستعلامات التحققية
│   ├── part_01_extensions.sql .. part_16_city_builder_functions_rls_realtime.sql
│   ├── part_17_phase3_house_requests_upgrade.sql    # ترقية house_requests (7 حالات + RPC للزوار والإدارة + Realtime)
│   └── part_18_phase3_city_metadata_and_flags.sql   # أوصاف المباني العامة + خصوصية اسم المالك وعناوين المنازل
│
├── core-city/                                  # وحدة المكتبة المشتركة لمحرك المدينة الأيزومتري الموسّع
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── res/drawable/asset_*.xml        # أصول VectorDrawable منظمة للمباني العامة والبيئة
│       │   ├── res/drawable/flag_*.xml         # 22 علم دولة عربية رسمي (flag_sa .. flag_km)
│       │   └── java/com/example/cityengine/
│       │       ├── model/{CityModels,CityBuilderModels,ArabCountriesRegistry}.kt
│       │       ├── assets/{CityAssetRegistry,CityAssetCache}.kt
│       │       ├── performance/{SpatialIndex,ViewportCuller}.kt
│       │       ├── projection/IsoProjection.kt
│       │       ├── camera/CameraController.kt
│       │       ├── world/{SpatialValidator,PlotManager,CityLayoutEditor,StarterCityMap}.kt
│       │       ├── render/{CityRenderer,LightingRenderer,RoadRenderer,HouseRenderer,PublicBuildingRenderer,EnvironmentPropRenderer,TreeRenderer,FlagRenderer}.kt
│       │       ├── CityEngine.kt
│       │       └── ui/{CityCanvas,ArabFlagBadge}.kt
│       └── test/java/com/example/cityengine/
│           ├── {IsoProjectionTest,CameraControllerTest,SpatialValidatorTest,StarterCityMapTest,CityHitTestingTest,CityBuilderEngineTest,ArabCountriesRegistryTest}.kt
│
├── core-data/                                  # وحدة البيانات المشتركة وطبقة Supabase المركزية
│   ├── consumer-rules.pro                      # قواعد ProGuard/R8 لحماية DTOs عند النشر
│   └── src/
│       ├── main/java/com/example/core/data/
│       │   ├── history/CityEditorHistoryManager.kt # إدارة التراجع/الإعادة الآمنة من التعارضات
│       │   ├── model/{ExplorerModels,AdminModels,CityBuilderDataModels,HouseRequestModels}.kt
│       │   ├── privacy/HousePrivacySanitizer.kt
│       │   ├── repository/{CityExplorerRepository,CityAdminRepository}.kt
│       │   ├── local/{LocalCityExplorerRepository,LocalCityAdminRepository,LocalCityBuilderDelegate,ExplorerSnapshotCache}.kt
│       │   └── supabase/
│       │       ├── {SupabaseConfig,SupabaseClientProvider,SupabaseDto,SupabaseMapper}.kt
│       │       ├── {ConnectivityChecker,SupabaseErrorTranslator,AdminRpcPayloadBuilder}.kt
│       │       ├── {SupabaseAdminAuthRepository,SupabaseCityExplorerRepository}.kt
│       │       └── {SupabaseCityAdminRepository,SupabaseCityBuilderDelegate,SupabasePlotLayoutDelegate,SupabaseAdminRealtimeSync}.kt
│       └── test/java/com/example/core/data/
│           ├── {HousePrivacySanitizerTest,LocalCityExplorerRepositoryTest,LocalCityAdminRepositoryTest}.kt
│           ├── {CityEditorHistoryManagerTest,SupabaseConfigTest,SqlSchemaVerificationTest,Phase3RequestsAndCacheTest}.kt
│           └── {SupabaseRepositoriesTest,SupabaseAdminAuthRepositoriesTest,SupabaseCityBuilderTest,LiveSupabaseIntegrationTest}.kt
│
├── app/                                        # تطبيق المشاهدة والاستكشاف للزوار (:app)
│   └── src/
│       ├── main/res/{values,values-en}/strings.xml
│       ├── main/java/com/example/
│       │   ├── MainActivity.kt
│       │   └── explorer/{CityExplorerViewModel,CityExplorerScreen,CityExplorerDetailCards,CityExplorerHouseCard,CityExplorerDialogs}.kt
│       └── test/java/com/example/{ExampleUnitTest,CityExplorerViewModelTest,ExampleRobolectricTest}.kt
│
└── app-admin/                                  # تطبيق الإدارة المستقل (:app-admin)
    └── src/
        ├── main/res/{values,values-en}/strings.xml
        ├── main/java/com/example/admin/
        │   ├── {MainActivity,AdminViewModel,AdminEditorState,AdminEditorDelegate}.kt
        │   └── ui/
        │       ├── {AdminAppScreen,AdminLoginScreen,AdminDashboardScreen,AdminHousesScreen,AdminPlotsScreen,AdminRequestsScreen}.kt
        │       ├── {AdminMapScreen,AdminMapEditorComponents,AdminMapInspectorCard}.kt
        │       └── {AdminBuilderDialogs,AdminHouseDialogs,AdminHouseActionDialogs}.kt
        └── test/java/com/example/admin/{AdminViewModelTest,AdminRobolectricTest,AdminBuilderRobolectricTest}.kt
```
