-- ============================================================================
-- Madinat Al-Tayibin — Phase 4 Supabase Schema
-- Part 11: Idempotent Starter City Seed Data (12 Plots + 12 Phase-1 Houses)
-- ============================================================================

-- 1. Seed City Layout (12x12 grid aligned with :core-city StarterCityMap)
INSERT INTO public.city_layout (
    id, version, grid_width, grid_height,
    roads, sidewalks, parks, trees, lamp_posts
) VALUES (
    'main_city',
    1,
    12,
    12,
    '[
      {"x":3,"y":0},{"x":3,"y":1},{"x":3,"y":2},{"x":3,"y":3},{"x":3,"y":4},{"x":3,"y":5},
      {"x":3,"y":6},{"x":3,"y":7},{"x":3,"y":8},{"x":3,"y":9},{"x":3,"y":10},{"x":3,"y":11},
      {"x":8,"y":0},{"x":8,"y":1},{"x":8,"y":2},{"x":8,"y":3},{"x":8,"y":4},{"x":8,"y":5},
      {"x":8,"y":6},{"x":8,"y":7},{"x":8,"y":8},{"x":8,"y":9},{"x":8,"y":10},{"x":8,"y":11},
      {"x":0,"y":3},{"x":1,"y":3},{"x":2,"y":3},{"x":4,"y":3},{"x":5,"y":3},{"x":6,"y":3},
      {"x":7,"y":3},{"x":9,"y":3},{"x":10,"y":3},{"x":11,"y":3},
      {"x":0,"y":8},{"x":1,"y":8},{"x":2,"y":8},{"x":4,"y":8},{"x":5,"y":8},{"x":6,"y":8},
      {"x":7,"y":8},{"x":9,"y":8},{"x":10,"y":8},{"x":11,"y":8}
    ]'::jsonb,
    '[
      {"x":4,"y":4},{"x":5,"y":4},{"x":6,"y":4},{"x":7,"y":4},
      {"x":4,"y":5},{"x":7,"y":5},{"x":4,"y":6},{"x":7,"y":6},
      {"x":4,"y":7},{"x":5,"y":7},{"x":6,"y":7},{"x":7,"y":7},
      {"x":1,"y":2},{"x":2,"y":2},{"x":4,"y":2},{"x":5,"y":2},{"x":6,"y":2},{"x":7,"y":2},{"x":9,"y":2},{"x":10,"y":2},
      {"x":1,"y":9},{"x":2,"y":9},{"x":4,"y":9},{"x":5,"y":9},{"x":6,"y":9},{"x":7,"y":9},{"x":9,"y":9},{"x":10,"y":9},
      {"x":2,"y":4},{"x":2,"y":5},{"x":2,"y":6},{"x":2,"y":7},
      {"x":9,"y":4},{"x":9,"y":5},{"x":9,"y":6},{"x":9,"y":7}
    ]'::jsonb,
    '[{"x":5,"y":5},{"x":6,"y":5},{"x":5,"y":6},{"x":6,"y":6}]'::jsonb,
    '[
      {"id":"tree_01","x":0,"y":0,"scale":1.0,"variant":0},
      {"id":"tree_02","x":2,"y":0,"scale":0.92,"variant":1},
      {"id":"tree_03","x":4,"y":0,"scale":1.05,"variant":0},
      {"id":"tree_04","x":7,"y":0,"scale":0.95,"variant":1},
      {"id":"tree_05","x":9,"y":0,"scale":1.02,"variant":0},
      {"id":"tree_06","x":11,"y":0,"scale":0.94,"variant":1},
      {"id":"tree_07","x":0,"y":4,"scale":1.0,"variant":1},
      {"id":"tree_08","x":0,"y":7,"scale":0.96,"variant":0},
      {"id":"tree_09","x":11,"y":4,"scale":0.98,"variant":0},
      {"id":"tree_10","x":11,"y":7,"scale":1.04,"variant":1},
      {"id":"tree_11","x":5,"y":5,"scale":1.1,"variant":0},
      {"id":"tree_12","x":6,"y":6,"scale":1.06,"variant":1},
      {"id":"tree_13","x":0,"y":11,"scale":0.96,"variant":0},
      {"id":"tree_14","x":2,"y":11,"scale":1.02,"variant":1},
      {"id":"tree_15","x":4,"y":11,"scale":0.94,"variant":0},
      {"id":"tree_16","x":7,"y":11,"scale":1.0,"variant":1},
      {"id":"tree_17","x":9,"y":11,"scale":0.95,"variant":0},
      {"id":"tree_18","x":11,"y":11,"scale":1.04,"variant":1}
    ]'::jsonb,
    '[
      {"id":"lamp_01","x":2,"y":2},{"id":"lamp_02","x":4,"y":2},
      {"id":"lamp_03","x":7,"y":2},{"id":"lamp_04","x":9,"y":2},
      {"id":"lamp_05","x":2,"y":4},{"id":"lamp_06","x":9,"y":4},
      {"id":"lamp_07","x":2,"y":7},{"id":"lamp_08","x":9,"y":7},
      {"id":"lamp_09","x":2,"y":9},{"id":"lamp_10","x":4,"y":9},
      {"id":"lamp_11","x":7,"y":9},{"id":"lamp_12","x":9,"y":9}
    ]'::jsonb
)
ON CONFLICT (id) DO NOTHING;

-- 2. Seed the 12 designated plots (idempotent)
INSERT INTO public.plots (id, layout_id, plot_code, grid_x, grid_y, status) VALUES
    ('plot_01', 'main_city', 'P-01', 1, 1, 'AVAILABLE'),
    ('plot_02', 'main_city', 'P-02', 5, 1, 'AVAILABLE'),
    ('plot_03', 'main_city', 'P-03', 6, 1, 'AVAILABLE'),
    ('plot_04', 'main_city', 'P-04', 10, 1, 'AVAILABLE'),
    ('plot_05', 'main_city', 'P-05', 1, 5, 'AVAILABLE'),
    ('plot_06', 'main_city', 'P-06', 1, 6, 'AVAILABLE'),
    ('plot_07', 'main_city', 'P-07', 10, 5, 'AVAILABLE'),
    ('plot_08', 'main_city', 'P-08', 10, 6, 'AVAILABLE'),
    ('plot_09', 'main_city', 'P-09', 1, 10, 'AVAILABLE'),
    ('plot_10', 'main_city', 'P-10', 5, 10, 'AVAILABLE'),
    ('plot_11', 'main_city', 'P-11', 6, 10, 'AVAILABLE'),
    ('plot_12', 'main_city', 'P-12', 10, 10, 'AVAILABLE')
ON CONFLICT (id) DO NOTHING;

-- 3. Seed the 12 initial houses ONLY if the houses table is currently empty
-- (prevents overwriting or duplicating live server data on subsequent runs)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM public.houses LIMIT 1) THEN
        INSERT INTO public.houses (
            id, plot_id, resident_name, country_code, country,
            flag_stripes, flag_vertical, family_size, house_color, palette_json,
            grid_x, grid_y, public_role, public_about, allow_public_info, show_family_count_publicly
        ) VALUES
        ('house_01','plot_01','أبو عبدالله الحربي','SA','المملكة العربية السعودية','[4279664454,4294967295,4279664454]'::jsonb,false,5,'terracotta','{"wallLeftHex":4294373346,"wallRightHex":4293121987,"roofLeftHex":4291320378,"roofRightHex":4288626725,"roofTrimHex":4286788124,"doorHex":4284299817,"windowHex":4293129466}'::jsonb,1,1,'منسق مجلس الحي','أهلاً بكم في دار الطيبين — القهوة العربية حاضرة دائمًا للجيران.',true,true),
        ('house_02','plot_02','م. طارق المنصور','EG','مصر','[4291694886,4294967295,4280163870]'::jsonb,false,4,'cobalt','{"wallLeftHex":4294047224,"wallRightHex":4292469739,"roofLeftHex":4281166478,"roofRightHex":4280241771,"roofTrimHex":4279646800,"doorHex":4283053864,"windowHex":4293129466}'::jsonb,5,1,'مهندس معماري','مهتم بتجميل الواجهات والمساحات الخضراء في الحي.',true,true),
        ('house_03','plot_03','د. ليلى العلي','JO','الأردن','[4280163870,4294967295,4278221373]'::jsonb,false,3,'ochre','{"wallLeftHex":4294702052,"wallRightHex":4293450432,"roofLeftHex":4292121140,"roofRightHex":4289753886,"roofTrimHex":4287388180,"doorHex":4284365348,"windowHex":4293129466}'::jsonb,6,1,'طبيبة أطفال','يسعدني تقديم الإرشاد الصحي لأسر الحي.',true,true),
        ('house_04','plot_04','يوسف الإدريسي','MA','المغرب','[4290848557,4278215219,4290848557]'::jsonb,false,6,'sage','{"wallLeftHex":4294112752,"wallRightHex":4292732377,"roofLeftHex":4283398746,"roofRightHex":4281949001,"roofTrimHex":4280960305,"doorHex":4283710509,"windowHex":4293129466}'::jsonb,10,1,'خطاط ومصمم','أهلاً بأهل الحي الكرام في البيت المغربي.',true,true),
        ('house_05','plot_05','سالم المهيري','AE','الإمارات','[4278219567,4294967295,4280163870]'::jsonb,false,4,'coral','{"wallLeftHex":4294635760,"wallRightHex":4293448916,"roofLeftHex":4290070875,"roofRightHex":4287574851,"roofTrimHex":4285408305,"doorHex":4283380780,"windowHex":4293129466}'::jsonb,1,5,NULL,'نحب الهدوء والتعاون بين الجيران.',true,true),
        ('house_06','plot_06','فهد الصباح','KW','الكويت','[4278221373,4294967295,4291694886]'::jsonb,false,5,'teal','{"wallLeftHex":4293981941,"wallRightHex":4292339428,"roofLeftHex":4281106296,"roofRightHex":4280114777,"roofTrimHex":4279518786,"doorHex":4284235307,"windowHex":4293129466}'::jsonb,1,6,'معلم رياضيات','باب بيتنا مفتوح دائمًا لمبادرات الطلبة.',true,true),
        ('house_07','plot_07','خالد الكواري','QA','قطر','[4294967295,4287437629]'::jsonb,true,3,'lavender','{"wallLeftHex":4294440170,"wallRightHex":4293188299,"roofLeftHex":4285225868,"roofRightHex":4283384427,"roofTrimHex":4282003536,"doorHex":4283315753,"windowHex":4293129466}'::jsonb,10,5,'هاوي زراعة','نشارك شتلات النعناع والريحان مع جيران الشارع.',true,true),
        ('house_08','plot_08','سعيد البلوشي','OM','عُمان','[4294967295,4292548123,4278222848]'::jsonb,false,4,'sandstone','{"wallLeftHex":4294308845,"wallRightHex":4292926930,"roofLeftHex":4289096252,"roofRightHex":4286795560,"roofTrimHex":4284758811,"doorHex":4282330146,"windowHex":4293129466}'::jsonb,10,6,NULL,NULL,false,true),
        ('house_09','plot_09','أمينة بن علي','TN','تونس','[4293328915,4294967295,4293328915]'::jsonb,false,2,'royal','{"wallLeftHex":4294112504,"wallRightHex":4292665578,"roofLeftHex":4282343060,"roofRightHex":4281156976,"roofTrimHex":4280234580,"doorHex":4283971624,"windowHex":4293129466}'::jsonb,1,10,'باحثة تاريخية','سعداء بالانضمام إلى حي مدينة الطيبين.',true,true),
        ('house_10','plot_10','رامي الخوري','LB','لبنان','[4293729316,4294967295,4293729316]'::jsonb,false,4,'clay','{"wallLeftHex":4294636779,"wallRightHex":4293319625,"roofLeftHex":4291063109,"roofRightHex":4288565550,"roofTrimHex":4286397985,"doorHex":4283314981,"windowHex":4293129466}'::jsonb,5,10,'صانع محتوى',NULL,true,false),
        ('house_11','plot_11','نورة الدوسري','BH','البحرين','[4294967295,4291694886]'::jsonb,true,3,'olive','{"wallLeftHex":4294178546,"wallRightHex":4292732890,"roofLeftHex":4284320334,"roofRightHex":4282607927,"roofTrimHex":4281421607,"doorHex":4283841322,"windowHex":4293129466}'::jsonb,6,10,'مصممة حدائق','الحديقة الوسطى هي أجمل مكان للقاء العائلات.',true,true),
        ('house_12','plot_12','عمر البرغوثي','PS','فلسطين','[4280163870,4294967295,4278221373]'::jsonb,false,5,'plum','{"wallLeftHex":4294504948,"wallRightHex":4293187292,"roofLeftHex":4288433000,"roofRightHex":4286207566,"roofTrimHex":4284229177,"doorHex":4283249704,"windowHex":4293129466}'::jsonb,10,10,NULL,'تحية طيبة لكل زوار المدينة.',true,true);

        INSERT INTO public.house_private_details (
            house_id, phone, email, allow_phone_public, allow_email_public
        ) VALUES
        ('house_01', NULL, NULL, false, false),
        ('house_02', NULL, NULL, false, false),
        ('house_03', NULL, NULL, false, false),
        ('house_04', NULL, NULL, false, false),
        ('house_05', NULL, NULL, false, false),
        ('house_06', NULL, NULL, false, false),
        ('house_07', NULL, NULL, false, false),
        ('house_08', NULL, NULL, false, false),
        ('house_09', NULL, NULL, false, false),
        ('house_10', NULL, NULL, false, false),
        ('house_11', NULL, NULL, false, false),
        ('house_12', NULL, NULL, false, false);

        UPDATE public.plots
        SET status = 'OCCUPIED'
        WHERE id IN (SELECT plot_id FROM public.houses);
    END IF;
END $$;
