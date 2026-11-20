# ARCHITECTURE — المعمارية التقنية لمشروع مدينة الطيبين

## 1. النظرة العامة والمعمارية العامة
يعتمد المشروع نمط **Modular Clean Architecture + MVVM** مع فصل صارم بين:
1. **نواة محرك المدينة (`core-city`)**: طبقة مستقلة عن التطبيقات وعن الشبكة، مسؤولة عن الإسقاط الأيزومتري، الكاميرا، إدارة قطع الأراضي، التحقق المكاني، ومُصيّر الرسم (Renderer).
2. **نواة البيانات (`core-data`)**: نماذج الشبكة/التخزين، ومخزن اعتماد الجهاز المشفّر (`Android Keystore`)، ومستودعات Supabase، بدون أي اعتماد على واجهة المستخدم.
3. **تطبيق الاستكشاف (`app-explorer`)**: واجهات الزوار، عرض المدينة، بطاقة البيت، ونموذج طلب بناء بيت (مستقبلاً).
4. **تطبيق الإدارة (`app-admin`)**: لوحة تحكم المالك المباشرة بعد اعتماد الجهاز لمرة واحدة عبر الخادم، إدارة البيوت والأراضي، ومعالجة الطلبات.

```
+-------------------+       +-------------------+
|   app-explorer    |       |     app-admin     |
| (Guest UI & Flow) |       | (Owner UI & Flow) |
+---------+---------+       +---------+---------+
          |                           |
          +-------------+-------------+
                        |
          +-------------v-------------+
          |         core-city         |
          |  Engine, Iso, Camera,     |
          |  SpatialValidator, Canvas |
          +-------------+-------------+
                        |
          +-------------v-------------+
          |         core-data         |
          |  Models & Supabase Repos  |
          +---------------------------+
```

> **ملاحظة تقنية حول بيئة AI Studio الحالية:**
> القالب الحالي في AI Studio مبني حول وحدة واحدة `:app` تُشغَّل في المعاينة المباشرة. لذلك في المرحلة الأولى نبني حزم المحرك مفصولة منطقيًا وداخليًا داخل بنية نظيفة قابلة للنقل الفوري، ثم في مرحلة تقسيم الوحدات (Phase 2) نُفعّل `:core-city` و`:core-data` و`:app-explorer` و`:app-admin` (مع إبقاء توافق التشغيل أو توضيح إعدادات Gradle متعددة التطبيقات لـ GitHub Actions).

---

## 2. اختيار محرك الرسم والتوافق مع بيئة البناء
### المحرك المختار: **Jetpack Compose `Canvas` (Hardware-Accelerated Skia 2D)**
**أسباب الاختيار:**
- متوافق 100% مع Kotlin وJetpack Compose وAGP 9.1.1 / `compileSdk = 36` بدون مكتبات C++/NDK أو إضافات ثقيلة قد تفشل في بيئة البناء السحابية.
- يتيح رسمًا متجهيًا ناعمًا للبيوت، الأسقف، النوافذ، الظلال، الأشجار، الشوارع، والأعلام المتحركة على أي كثافة شاشة (DPI-independent).
- يدعم التحكم الكامل في ترتيب الرسم (Painter's Algorithm / Depth Sorting حسب `gridX + gridY`).
- يسمح بعزل حركة الكاميرا والأعلام داخل **مرحلة الرسم (Draw Phase)** فقط.

**لماذا تم استبعاد البدائل؟**
- **Unity / Unreal**: ثقل كبير في حجم APK وتعارض مع بساطة واجهات Compose وبيئة البناء.
- **LibGDX / OpenGL ES الخام**: تعقيد إدارة دورة حياة SurfaceView والتفاعل مع بطاقات Material 3 فوق الخريطة دون حاجة فعلية في خريطة متجهية نظيفة.

---

## 3. نظام المنظور الأيزومتري (2:1 Isometric Projection)
تُخزّن عناصر العالم على شبكة منطقية ثنائية الأبعاد `(gridX, gridY)`، وتُحوّل إلى إحداثيات العالم الأيزومتري بنسبة عرض إلى ارتفاع **2:1** (`tileWidth = 2 * tileHeight`):

- **التحويل الأمامي (Grid -> World):**
  - `worldX = (gridX - gridY) * (tileWidth / 2f)`
  - `worldY = (gridX + gridY) * (tileHeight / 2f)`
- **التحويل العكسي (World -> Grid):**
  - `halfW = tileWidth / 2f`، `halfH = tileHeight / 2f`
  - `gridX = (worldX / halfW + worldY / halfH) / 2f`
  - `gridY = (worldY / halfH - worldX / halfW) / 2f`
- **ترتيب العمق (Depth Sorting):**
  - يُحسب عمق الرسم لكل عنصر وفق `depth = gridX + gridY` لضمان رسم العناصر الخلفية قبل الأمامية بشكل طبيعي بدون تداخل بصري.

---

## 4. نظام الكاميرا والأداء (60 FPS بدون Recomposition)
### حالة الكاميرا (`CameraState`)
- `panX: Float`, `panY: Float` (إزاحة مركز العالم بالبكسل المنطقي أو بوحدة العالم)
- `zoom: Float` (مقيد بين `minZoom` و`maxZoom`، مثلاً `0.6f .. 2.8f`)
- `viewportWidth: Float`, `viewportHeight: Float`

### التحويل بين الشاشة والعالم
- `screenX = (worldX * zoom) + panX + viewportWidth / 2f`
- `screenY = (worldY * zoom) + panY + viewportHeight / 2f`
- `worldX = (screenX - viewportWidth / 2f - panX) / zoom`
- `worldY = (screenY - viewportHeight / 2f - panY) / zoom`

### الحفاظ على نقطة التركيز أثناء التكبير (Focal-Point Zoom)
عند تغيّر `zoom` من `oldZoom` إلى `newZoom` حول نقطة اللمس `focalScreen`:
1. نحسب نقطة العالم تحت الإصبع قبل التكبير: `worldBefore = screenToWorld(focalScreen, oldCamera)`.
2. نحدّث `zoom = clamp(oldZoom * zoomFactor, minZoom, maxZoom)`.
3. نضبط `panX` و`panY` بحيث تبقى `worldBefore` مطابقة لـ `focalScreen` بعد التكبير، ثم نطبّق حدود العالم (`clampToWorldBounds`).

### منع Recomposition أثناء الحركة ورفرفة الأعلام
- تُحفظ حالة الكاميرا داخل `CameraController` باستخدام حقول mutable مدعومة بـ `State<Long>` أو `mutableFloatStateOf` تُقرأ **فقط** داخل `Canvas { ... }` (Draw Phase) وداخل `pointerInput`.
- حركة رفرفة الأعلام تُدار عبر `withInfiniteAnimationFrameNanos` / `LaunchedEffect` يحدّث `animationTimeSeconds` داخل حالة مخصصة للرسم فقط، فلا يُعاد تركيب شجرة Compose UI عند تغير الإطارات.
- الكائنات الهندسية (`Path`, `Paint`, مصفوفات النقاط) تُعاد استخدامها داخل طبقة الرسم لتفادي الضغط على جامع القمامة (GC).

---

## 5. التفاعل وتحديد البيوت (Hit-Testing)
عند نقرة المستخدم `(tapX, tapY)`:
1. تحويل نقطة الشاشة إلى إحداثيات العالم `(worldX, worldY)` عبر `CameraController`.
2. التحقق الدقيق من البيوت المرئية مرتبةً من **الأقرب للكاميرا إلى الأبعد** (عكس ترتيب الرسم)، باستخدام مضلع/صندوق الإحاطة الهندسي الفعلي للبيت (بما يشمل ارتفاع الجدران والسقف) وليس فقط قاعدة الأرضية.
3. إذا لم يُصب جسم بيت مرتفع، نتحقق من خلية الشبكة تحت المؤشر `(floor(gridX), floor(gridY))` لمطابقة قطعة الأرض.
4. عند المطابقة، يُصدر المحرك حدثًا منضبطًا `CityInteractionEvent.HouseSelected(houseId)` أو `PlotSelected(plotId)` إلى واجهة Android.

---

## 6. ربط محرك المدينة بواجهات Android
يعتمد الربط على **تدفق بيانات أحادي الاتجاه (Unidirectional Data Flow)**:
- **المدخلات إلى المحرك (`CitySceneState`):** قائمة قطع الأراضي، البيوت، العناصر البيئية (شوارع/أشجار/أعمدة إنارة/مساحة خضراء)، ومعرّف العنصر المحدد حاليًا.
- **المخرجات من المحرك (`onCityEvent`):** أحداث النقر (`HouseTapped`, `PlotTapped`, `EmptySpaceTapped`) دون احتواء المحرك على أي منطق تنقّل أو نوافذ منبثقة.
- **الواجهة الحاضنة (`Compose Overlay`):** تستقبل الحدث عبر `ViewModel` وتعرض بطاقة Material 3 عائمة فوق الخريطة (تفاصيل البيت في `app-explorer`، أو أدوات التحرير/البناء في `app-admin`).

---

## 7. تخزين إحداثيات البيوت والأراضي
- **في الخريطة والمحرك:** إحداثيات شبكة صحيحة `(gridX: Int, gridY: Int)` مع أبعاد منطقية ثابتة للقطعة (`widthCells = 1, heightCells = 1` أو بحسب التوسع).
- **مزايا الإحداثيات الشبكية الصحيحة:**
  1. دقة تامة دون أخطاء الفاصلة العائمة (`Float rounding drift`).
  2. منع التداخل برمجيًا عبر `SpatialValidator` وقاعدة بيانات PostgreSQL عبر قيد `UNIQUE (grid_x, grid_y)`.
  3. استعلام سريع جدًا وسهولة الفهرسة مستقبلًا في Supabase عند نمو المدينة.

---

## 8. المزامنة الخلفية وأمان اعتماد الجهاز (Supabase — المرحلة 4)
- **التخزين المركزي:** Supabase PostgreSQL للجداول التسعة: `profiles`, `city_layout`, `plots`, `houses`, `house_private_details`, `house_public_details`, `house_requests`, `admin_activation_codes`, `admin_devices`.
- **اعتماد الجهاز لمرة واحدة في `:app-admin` (بديل الدخول التقليدي):**
  1. يُصدر مالك المشروع رمز تفعيل لمرة واحدة من جهة الخادم فقط عبر `create_admin_activation_code()` (تُخزَّن بصمته SHA-256 فقط، وتُمنع قراءته من أي عميل).
  2. في أول تشغيل لجهاز غير معتمد، تظهر شاشة تفعيل أولي فقط (`AdminDeviceActivationScreen`). عند إدخال الرمز، تُنفّذ دالة `activate_admin_device()` فحصًا ذريًا (`FOR UPDATE`) وتُرجع رمز جلسة جهاز عشوائي (256-bit) مع حفظ بصمته SHA-256 في `admin_devices`.
  3. يحفظ `:app-admin` بيانات اعتماد الجهاز مشفّرة عبر **Android Keystore (`AES/GCM/NoPadding`)** داخل `KeystoreAdminDeviceCredentialStore` ولا يكتب أي رمز كنص صريح.
  4. عند كل فتح للتطبيق وقبل كل عملية تعديل إدارية حساسة، يتحقق `SupabaseDeviceAuthRepository` عبر `verify_admin_device()`:
     - جهاز معتمد (`ACTIVE`): فتح `Dashboard` مباشرةً دون شاشة دخول.
     - جهاز غير معتمد (`UNENROLLED`): عرض شاشة التفعيل الأولي فقط.
     - جهاز ملغى (`REVOKED` عبر `revoke_admin_device()`): مسح الاعتماد المحلي وحظر لوحة الإدارة.
     - انقطاع الإنترنت: حظر الدخول والعمليات الإدارية برسالة واضحة دون اعتماد التخزين المحلي وحده.
  5. يُرسل عميل الإدارة فقط (`SupabaseClientProvider.getOrCreateAdminClient`) ترويستي `x-admin-device-id` و`x-admin-device-token` مع طلبات PostgREST، وتتحقق الدالة الخادمية `public.is_admin_device()` (المستدعاة داخل `public.is_admin()`) منهما في كل سياسة RLS ودالة `admin_*`.
- **فصل الخصوصية:**
  - `house_private_details` و`houses` محميان كليًا بصلاحيات الإدارة فقط (`public.is_admin()`), وتكون موافقات نشر الهاتف (`allow_phone_public`) والبريد (`allow_email_public`) مفصولة ومرفوضة افتراضيًا (`false`).
  - `house_public_details` يُحدَّث تلقائيًا عبر مشغّلات قاعدة البيانات ولا يحتوي أبدًا على بيانات خاصة غير مصرّح بنشرها.
  - جدولا `admin_activation_codes` و`admin_devices` مقفلان بسياسة `USING (false) WITH CHECK (false)` ولا يُدارا إلا عبر دوال `SECURITY DEFINER` وSQL Editor.
- **المزامنة اللحظية (Supabase Realtime):**
  - يتلقى تطبيق الاستكشاف التحديثات التزايدية على `house_public_details` و`plots` و`city_layout`.
  - يتلقى تطبيق الإدارة تنبيهات التغيير عبر `house_public_details` و`plots` و`city_layout` ثم يحدّث الصف المعني من `houses` و`house_private_details` عبر طلب PostgREST محمي بترويسات الجهاز المعتمدة.
