# FEATURES_SPEC — مواصفات الميزات ونماذج البيانات

## 1. نماذج البيانات الأساسية (Domain & Engine Models)

### أ. الإحداثيات وقطع الأراضي (`GridCoord`, `Plot`, `PlotStatus`)
```kotlin
enum class PlotStatus {
    AVAILABLE,
    OCCUPIED
}

data class GridCoord(
    val x: Int,
    val y: Int
)

data class Plot(
    val id: String,              // معرف ثابت (مثل "plot_01" .. "plot_12")
    val coord: GridCoord,        // إحداثي القطعة على الشبكة
    val status: PlotStatus,      // AVAILABLE أو OCCUPIED
    val houseId: String? = null  // معرف البيت المرتبط إن وجد
)
```

### ب. البيت والسكان والعلم (`House`, `CountryFlagStyle`)
```kotlin
data class CountryFlagStyle(
    val countryCode: String,     // رمز الدولة مثل "SA", "EG", "JO", "MA"
    val countryName: String,     // اسم الدولة للعرض في البطاقة
    val stripeColors: List<Long>,// ألوان العلم للرسم المباشر على Canvas
    val isVerticalStripes: Boolean = false
)

data class HousePalette(
    val wallFrontHex: Long,
    val wallSideHex: Long,
    val roofHex: Long,
    val roofTrimHex: Long,
    val doorHex: Long
)

data class House(
    val id: String,
    val plotId: String,
    val coord: GridCoord,
    val ownerName: String,
    val familyMembersCount: Int,
    val flag: CountryFlagStyle,
    val palette: HousePalette
)
```

### ج. عناصر البيئة والبنية التحتية (`CityTileType`, `TreeItem`, `LampPostItem`)
```kotlin
enum class CityTileType {
    GRASS,
    PARK_GREEN,
    ROAD_HORIZONTAL,
    ROAD_VERTICAL,
    ROAD_INTERSECTION,
    SIDEWALK,
    PLOT_LOT
}

data class TreeItem(
    val id: String,
    val coord: GridCoord,
    val scale: Float = 1f
)

data class LampPostItem(
    val id: String,
    val coord: GridCoord
)
```

### د. طلبات بناء البيوت (للمراحل اللاحقة مع Supabase)
```kotlin
enum class RequestStatus {
    PENDING,
    APPROVED,
    REJECTED
}

data class HouseRequest(
    val id: String,
    val targetPlotId: String,
    val applicantName: String,
    val countryCode: String,
    val countryName: String,
    val familyMembersCount: Int,
    val preferredColorKey: String,
    val status: RequestStatus,
    val createdAtIso: String
)
```

---

## 2. مواصفات محرك المدينة (المرحلة الأولى)
1. **الخريطة الأولية:**
   - شبكة منظمة تحتوي على **12 قطعة أرض** ثابتة المعرف (`plot_01` إلى `plot_12`).
   - في المرحلة الأولى: **12 بيتًا صغيرًا متساوي الحجم** موزعة على قطع الأراضي بألوان متنوعة للتحقق البصري واختبار التفاعل (بيانات اختبار واضحة ومحدودة لهذه المرحلة فقط).
   - شوارع متقاطعة ومتصلة تربط جميع القطع، محاطة بأرصفة بسيطة.
   - مساحة خضراء عامة (حديقة مصغرة) في قلب أو جانب الحي.
   - أشجار موزعة بانتظام على الأرصفة وفي المساحة الخضراء.
   - أعمدة إنارة على جوانب الشوارع.
2. **تصميم البيوت:**
   - حجم موحد تمامًا لجميع البيوت لضمان الانسجام البصري.
   - جداران ظاهران بزاوية Isometric 2:1 (واجهة مضاءة وأخرى أغمق قليلًا)، سقف مائل/هرمي أو مسطّح بحافة جميلة، باب واضح، نوافذ مضيئة/متناسقة، وظل خفيف على الأرض.
   - سارية علم صغيرة فوق السقف تحمل علم الدولة مع تموج مستمر وناعم.
3. **الكاميرا والتفاعل:**
   - السحب بإصبع واحد في جميع الاتجاهات.
   - التكبير والتصغير بإصبعين مع تثبيت نقطة المنتصف (Focal Point) تحت الأصابع.
   - تقييد الكاميرا بحدود العالم حتى لا تضيع المدينة خارج الشاشة.
   - النقر على أي بيت يحدده بدقة، يُظهر إطارًا/توهجًا حوله، ويعرض بطاقة تحتوي على: **اسم صاحب البيت**، **الدولة**، و**عدد أفراد الأسرة**.

---

## 3. مواصفات التطبيقين (المراحل اللاحقة)
### تطبيق الاستكشاف (`app-explorer`)
- دخول فوري دون حساب أو تسجيل دخول.
- استكشاف المدينة والتنقل بالكاميرا وتفحص البيوت المأهولة.
- الضغط على قطعة أرض `AVAILABLE` لتقديم طلب إنشاء بيت (دون حساب دائم).

### تطبيق الإدارة (`app-admin`)
- تسجيل دخول خاص بمالك المدينة عبر Supabase Auth (محمي بصلاحيات الخادم RLS).
- تحديد الأراضي الفارغة وبناء بيت مباشرة.
- نقل بيت إلى أرض فارغة أخرى، تعديل بياناته، أو حذفه (مع تحرير الأرض تلقائيًا إلى `AVAILABLE`).
- مراجعة طلبات الزوار المعلّقة والموافقة عليها أو رفضها.
