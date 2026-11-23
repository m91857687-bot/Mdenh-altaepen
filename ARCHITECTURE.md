# ARCHITECTURE — المعمارية التقنية لمشروع مدينة الطيبين

## 1. النظرة العامة والمعمارية العامة
يعتمد المشروع نمط **Modular Clean Architecture + MVVM** مقسمًا إلى أربع وحدات Gradle فعلية (`:core-city`, `:core-data`, `:app`, `:app-admin`):
1. **نواة محرك المدينة (`:core-city`)**: طبقة مستقلة عن التطبيقات وعن الشبكة، مسؤولة عن الإسقاط الأيزومتري `2:1`، الكاميرا الديناميكية، إدارة قطع الأراضي متعددة الخلايا، التحقق المكاني، نظام الأصول (`CityAssetRegistry`/`CityAssetCache`)، محرك التحسين (`ViewportCuller`/`SpatialGridIndex`/`LodTier`)، ومُصيّر الرسم متعدد المراحل (`CityRenderer` + `LightingRenderer`).
2. **نواة البيانات (`:core-data`)**: نماذج الشبكة/التخزين، مصادقة الإدارة عبر Supabase Auth (`SupabaseAdminAuthRepository`)، ومستودعات Supabase مع حماية الخصوصية وقواعد ProGuard المجمّعة.
3. **تطبيق الاستكشاف (`:app`)**: واجهات الزوار، استكشاف المدينة بالكاميرا فقط، وبطاقة البيت العامة.
4. **تطبيق الإدارة (`:app-admin`)**: لوحة تحكم المالك المحمية بتسجيل الدخول عبر Supabase Auth والتحقق من `profiles.role = 'ADMIN'`.

---

## 2. اختيار محرك الرسم ونظام الأصول الرسومية (المرحلة 1 — City Builder)
- **محرك الرسم:** Jetpack Compose `Canvas` (Hardware-Accelerated Skia 2D) مع إسقاط Isometric بنسبة **2:1** (`tileWidth = 2 * tileHeight`).
- **الأصول المتجهة والذاكرة المؤقتة (`CityAssetRegistry` + `CityAssetCache`):**
  - أصول `VectorDrawable` منظمة داخل `core-city/src/main/res/drawable/asset_*.xml` لـ 12 نوعًا من المباني العامة والزينة البيئية.
  - تحويل كسول عند الطلب إلى `ImageBitmap` داخل ذاكرة `LruCache` محدودة الحجم (`64` مدخلًا افتراضيًا) مع تحرير تلقائي عند التخلص من `CityCanvas`.
  - دمج مع الهندسة الإيزومترية متعددة الخلايا ومستويات الترقية (`upgradeLevel`) والزوايا (`0/90/180/270`).

---

## 3. مراحل الرسم والإضاءة (`CityRenderer` + `LightingRenderer`)
تُنفذ عملية الرسم في `CityRenderer.render()` دون تخصيصات ثقيلة أثناء الإطار بالترتيب التالي:
1. **الخلفية الجوية (`drawSkyBackdrop`):** تدرج السماء حسب الطور (`DAY`, `SUNSET`, `NIGHT`).
2. **حساب حدود الرؤية ومستوى التفصيل (`ViewportCuller`):** استخلاص `[minX..maxX, minY..maxY]` واختيار `LodTier` (`HIGH`, `MEDIUM`, `LOW`) من مستوى `camera.zoom`.
3. **قاعدة الجزيرة والأرضيات (`drawIslandSkirt`, `drawTile`):** رسم البلاطات المرئية فقط (تضاريس العشب، الحدائق، التربة، الحقول الزراعية، الحجر، البلاط المزخرف، الساحات، المياه، الأرصفة، الشوارع والتقاطعات ومعابر المشاة).
4. **حدود القطع وعناصر الأرضية (`drawPlotFootprintPad`, `GROUND_OVERLAY`):** مواقف السيارات ومعابر المشاة وإبراز حالة القطع (`AVAILABLE`, `UNAVAILABLE`, `OCCUPIED`).
5. **برك الضوء والأضواء الليلية (`drawLightPools`):** هالات إنارة الشوارع ومصادر الضوء الدائرية والمخروطية عند الغروب والليل.
6. **ترتيب العمق للأجسام المرتفعة (`SpatialGridIndex` + `footprintDepthKey`):** فرز البيوت والمباني العامة والأشجار وأعمدة الإنارة والزينة المرئية فقط من الخلف إلى الأمام.
7. **طبقة الجو العامة (`drawAmbientAtmosphereOverlay`):** صبغة غروب أو ليل خفيفة تحافظ على وضوح العناصر المضيئة (`hasLitWindows`, `isLitAtNight`).

---

## 4. الأداء والقياس للمدن الكبيرة (آلاف العناصر)
- **`SpatialGridIndex<T>` (حواضن `4x4`):** تقسيم المكاني في الذاكرة للبيوت، المباني العامة، الأراضي، الزينة، والأشجار، يُحدَّث فقط عند تغيّر مرجع `CitySceneState` (`cachedSceneRef === scene`) ليعمل البحث والنقر (`Hit-Testing`) في وقت شبه ثابت `O(1)`.
- **`ViewportCuller`:** تجاهل تام لجميع البلاطات والعناصر خارج الشاشة + هامش أمان للأسطح المرتفعة.
- **مستويات التفصيل (`LodTier`):** تقليل التفاصيل الدقيقة (إطارات النوافذ، تفاصيل الرصف، المقاعد والزهور الصغيرة) عند التصغير الواسع (`LOW LOD`) مع إبقاء الهوية البصرية للمباني واضحة.
- **كاميرا ديناميكية:** `CityEngine.syncSceneBounds(scene)` يُحدّث حدود السحب تلقائيًا عند توسع الشبكة فوق `12x12`.

---

## 5. التوافق الخلفي والأمامي مع Supabase
- **ترحيلات أمامية (`part_14`–`part_18`) بدون حذف:** توسيع `city_layout`, `plots`, `houses`, `house_public_details`, `city_buildings`, `house_requests` بحقول ودوال جديدة دون حذف أي جدول أو عمود قائم، مع إنشاء `city_districts`, `city_buildings`, `city_environment_props`, `city_lights` محمية بـ RLS و`public.is_admin()`.
- **قراءة آمنة اختيارية في `:core-data`:** يستعلم `SupabaseCityExplorerRepository` و`SupabaseCityAdminRepository` الجداول الجديدة ضمن `runCatching { ... }.getOrDefault(emptyList())` لضمان عمل التطبيقين قبل أو بعد تشغيل الترحيلات الجديدة.
- **ذاكرة الحالة الدائمة في Explorer (`ExplorerSnapshotCache`):** تخزين آخر لقطة مدينة متزامنة محليًا بتنسيق JSON حتى يعمل تطبيق المشاهدة بسرعة ويستمر بعرض آخر تصميم معتمد عند انقطاع الشبكة، ثم يتزامن تلقائيًا عبر Realtime والتحديثات التزايدية عند عودة الاتصال.
- **منظومة الأعلام العربية الـ 22 الموحدة (`ArabCountriesRegistry` + `FlagRenderer` + `ArabFlagBadge`):** ربط موحد بين رسوم `VectorDrawable` المضمنة (`flag_sa.xml`..`flag_km.xml`)، الرسم الإيزومتري للأعلام فوق المنازل وأعلام المدينة المستقلة (`CITY_FLAG`)، ومكوّن `ArabFlagBadge` في البطاقات والقوائم.
- **تدفق طلبات بناء المنازل (`submit_house_request` -> `AdminRequestsScreen`):** إرسال آمن للزوار عبر RPC (`SECURITY DEFINER`) بحماية التكرار وتجريد حقل عدد أفراد الأسرة، متزامنًا مع لوحة الإدارة (`house_requests` عبر Realtime) وربط مباشر بالأرض المتاحة وبناء المنزل ذريًا (`admin_assign_request_plot_and_house`).
- **عزل المصادقة والخصوصية:** مستمر بالكامل وفق **ADR-008** و**ADR-011** (Supabase Auth بالبريد وكلمة المرور + `profiles.role = 'ADMIN'`).

