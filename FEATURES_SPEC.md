# FEATURES_SPEC — مواصفات الميزات ونماذج البيانات

## 1. نماذج محرك المدينة (متوافقة مع الكود الفعلي في `:core-city`)

### أ. القطع والتوسع المكاني (`Plot`, `PlotStatus`, `BuildingRotation`, `GridFootprint`)
- **`PlotStatus`:** `AVAILABLE`, `UNAVAILABLE`, `OCCUPIED`.
- **`Plot`:** يدعم `coord`, `widthCells`, `heightCells` (مثل `1x1`, `2x1`, `2x2`), `rotation` (`0/90/180/270`), `districtId`, `groundType`, `textureVariant`, `plotCode`.
- **`PlotManager`:** يوفر عمليات `createPlot`, `movePlot`, `resizePlot`, `rotatePlot`, `setPlotAvailability`, `splitPlot` (أفقيًا وعموديًا), `mergePlots`، إضافةً إلى `assignHouseToPlot`, `removeHouseFromPlot`, `moveHouse`, `updateHouse`.

### ب. نظام التضاريس والطرق (`CityTileType`, `TileTextureVariant`, `RoadSegment`)
- **`CityTileType`:** `GRASS`, `PARK_GREEN`, `ROAD` (مع تقاطعات ذكية تلقائية), `SIDEWALK`, `PLOT_LOT`, `SOIL`, `STONE`, `DECORATIVE_PAVING`, `FARMLAND`, `PLAZA`, `WATER`.
- **`TileTextureVariant`:** `DEFAULT`, `CHECKER_STONE`, `COBBLE`, `TILE_GRID`, `FURROWED`, `ASPHALT_CROSSWALK`, `PARKING_BAY`.
- **`DistrictZone`:** تقسيم المدينة إلى أحياء ومناطق مسماة ومميزة لونيًا وبيئيًا.

### ج. البيوت السكنية وأساليب البناء (`House`, `HousePalette`, `HouseDesignStyle`)
- **`HousePalette`:** `(wallLeftHex, wallRightHex, roofLeftHex, roofRightHex, roofTrimHex, doorHex, windowHex)`.
- **`HouseDesignStyle` (5 أنماط):** `CLASSIC_HOME`, `MODERN_VILLA`, `COURTYARD_HOUSE`, `TOWNHOME`, `COTTAGE`.
- **الترقية البصرية (`upgradeLevel` 1..5):** تزيد الارتفاع المعماري وتضيف نوافذ الدور العلوي والشرفات وإضاءة النوافذ (`hasLitWindows`) مع سارية العلم المتموجة.

### د. المباني العامة والمؤسسات (`PublicBuilding`, `PublicBuildingType`)
يدعم 12 نوعًا بارتفاعات وأشكال إيزومترية مخصصة وشارات `VectorDrawable`:
`PRESIDENTIAL_PALACE` (`3x3`), `CENTRAL_BANK` (`2x2`), `GOVERNMENT_HALL` (`2x2`), `MUNICIPALITY` (`2x2`), `HOSPITAL` (`2x2`), `SCHOOL_UNIVERSITY` (`2x2`), `COMMERCIAL_MALL` (`2x2`), `RESTAURANT_CAFE` (`1x1`), `FUEL_STATION` (`2x1`), `RECREATION_CENTER` (`2x2`), `MINE_INDUSTRIAL` (`2x2`), `CUSTOM_LANDMARK` (`1x1`).

### هـ. مكتبة الزينة والبيئة (`EnvironmentProp`, `EnvironmentPropType`, `PropLayer`)
- 15 عنصرًا قابلًا لإعادة الاستخدام: أشجار (`TREE_BROADLEAF`, `TREE_CYPRESS`, `PALM_TREE`), أعمدة إنارة (`LAMP_POST`), مقاعد (`BENCH`), نوافير متحركة (`FOUNTAIN`), تماثيل (`STATUE`), لوحات إرشادية (`ROAD_SIGN`), سياج (`FENCE_SEGMENT`), بوابات (`GATE_ARCH`), مواقف (`PARKING_LOT`), أحواض زهور (`FLOWER_BED`, `SIDEWALK_PLANTER`), مظلات حدائق (`GARDEN_GAZEBO`), ومعابر مشاة (`CROSSWALK_MARKER`).
- تصنّف عبر `PropLayer` (`GROUND_OVERLAY`, `STRUCTURE`, `LIGHT_FIXTURE`) للسماح بوضع المعابر والمواقف والأضواء بذكاء ومنع التداخل الهيكلي.

### و. الإضاءة والغلاف الجوي (`CityLightingState`, `TimeOfDayPhase`, `CityLightSource`)
- **الأطوار:** `DAY` (نهار), `SUNSET` (غروب), `NIGHT` (ليل) مع اتجاه شمس متحكم بالظلال (`sunAzimuthDeg`) وتعتيم محيطي.
- **مصادر الضوء (`CityLightSource`):** دائرية ومخروطية (`STREET_LAMP`, `WINDOW_GLOW`, `ENTRANCE_LIGHT`, `FACADE_FLOOD`, `FOUNTAIN_GLOW`, `PARK_LANTERN`, `SIGN_BACKLIGHT`).

### ز. المحرر المرئي التفاعلي وإدارة المدينة (`:app-admin` و`:app` — المرحلة الثانية)
- **شريط أدوات التحرير (`EditorToolDock`, `EditorTopActionBar`):** فئات `SELECT`, `PLOTS`, `ROADS`, `DISTRICTS`, `BUILDINGS`, `PROPS_LIGHTS`، مع رسم السحب على المربعات (`dragPaintEnabled`)، وتحديد الأبعاد المبدئية والدوران.
- **المعاينة الحية (`EditorPreviewOverlay` + `EditorPendingPreviewBanner`):** إظهار التضليل الزمردي للمواقع الصالحة والأحمر للمتعارضة قبل الحفظ الفعلي.
- **إدارة الأراضي المتقدمة:** إنشاء، نقل، تغيير الأبعاد، تدوير، تقسيم أفقي/عمودي، دمج قطع متجاورة، إتاحة/تعطيل (`AVAILABLE`/`UNAVAILABLE`)، ربط بالأحياء، حذف القطع غير المشغولة، بالإضافة إلى التحديد المتعدد والعمليات الجماعية (`batchUpdatePlotAvailability`, `batchAssignPlotsDistrict`, `batchDeletePlots`).
- **التراجع والإعادة الآمنان (`CityEditorHistoryManager`):** التحقق المسبق (`canUndoPredicate`/`canRedoPredicate`) من عدم تعارض الأمر مع تعديل أحدث في الجلسة أو من طرف آخر، مع القفل التفاؤلي عبر `city_layout.version`.
- **الاستكشاف القرائي في `:app` (`CityExplorerDetailCards`, `CityExplorerDialogs`):** استعراض الأحياء (`DistrictsBrowserDialog`)، المباني العامة مع `descriptionAr` و`publicInfoAr`، عناصر البيئة وأعلام الدول الـ22 المستقلة، الشوارع المسماة، وحالة الأراضي بصلاحيات قراءة فقط دون أي عناصر تعديل.
- **التوطين ثنائي اللغة (AR/EN):** دعم العربية (`values/strings.xml`) والإنجليزية (`values-en/strings.xml`) مع توجيه `LayoutDirection` تلقائيًا حسب اللغة.

---

## 2. ميزات المرحلة الثالثة: التكامل النهائي، الدول العربية الـ22، وطلبات البناء
- **الدول العربية الـ 22 وأعلامها الرسمية الموحدة (`ArabCountriesRegistry`, `flag_*.xml`, `ArabFlagBadge`):**
  - دعم كامل وموحد لجميع الدول العربية الـ22 (`SA, YE, BH, KW, OM, QA, AE, JO, SY, LB, PS, IQ, EG, SD, LY, TN, DZ, MA, MR, SO, DJ, KM`) بمصادر `VectorDrawable` عالية الدقة في `:core-city`، تُرسم فوق المنازل وعلى أعلام المدينة المستقلة (`CITY_FLAG`) وداخل بطاقات التفاصيل ومُنتقي الأعلام في `:app-admin` و`:app`.
  - يعتمد علم سوريا (`flag_sy.xml`) التصميم الرسمي المعتمد (أخضر/أبيض/أسود مع ثلاث نجوم حمراء).
- **طلب بناء منزل من تطبيق المشاهدة (`HouseRequestDialog` + `submit_house_request`):**
  - نموذج زائر خفيف بدون حساب: الاسم الكامل، الجوال (اختياري)، البريد (اختياري)، والملاحظات — **دون** أي حقل لعدد أفراد الأسرة.
  - استدعاء `submit_house_request` (`SECURITY DEFINER`) مع فحص الإدخال وحماية التكرار، وإظهار حالات التحميل والخطأ ونجاح الإرسال.
- **طابور إدارة طلبات البناء في `:app-admin` (`AdminRequestsScreen`):**
  - مراجعة الطلبات الفورية عبر 7 حالات: `NEW`, `UNDER_REVIEW`, `APPROVED`, `PREPARING_PLOT`, `BUILDING_HOUSE`, `COMPLETED`, `REJECTED`، مع فلترة الحالة، ملاحظات الإدارة، تخصيص قطعة أرض، والاعتماد وبناء المنزل مباشرة مع تعبئة بيانات الطلب تلقائيًا.
- **حوكمة الخصوصية والبيانات العامة المحسّنة:**
  - دعم `houseTitle`, `publicStatus`, و`allowOwnerNamePublic` في بطاقة البيت العامة وحوار الإدارة (`AddOrEditHouseDialog`) مع استمرار إخفاء عدد أفراد الأسرة والهاتف والبريد ما لم يُصرّح بنشرهما، وإضافة `descriptionAr` و`publicInfoAr` للمباني العامة.
- **التخزين المؤقت الدائم (`ExplorerSnapshotCache`):**
  - حفظ آخر نسخة مدينة صالحة محليًا لضمان التشغيل بدون إنترنت ومزامنة تلقائية عند عودة الشبكة.
