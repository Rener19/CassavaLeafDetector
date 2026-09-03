import re

with open('app/src/main/java/com/example/cassavaleafdetector/MainActivity.kt', 'r', encoding='utf-8') as f:
    code = f.read()

var_injection = '''
    // User version UI
    private var txtDiseaseDesc: TextView? = null
    private var txtDiseaseTreatment: TextView? = null
'''
code = code.replace('private var txtBaseMetrics: TextView? = null', 'private var txtBaseMetrics: TextView? = null' + var_injection)

init_injection = '''
        txtDiseaseDesc = findViewById(R.id.txt_disease_desc)
        txtDiseaseTreatment = findViewById(R.id.txt_disease_treatment)
'''
code = code.replace('btnViewThesisMetrics = findViewById(R.id.btn_view_thesis_metrics)', 'btnViewThesisMetrics = findViewById(R.id.btn_view_thesis_metrics)' + init_injection)

disease_dict = '''
    private val diseaseDescriptions = mapOf(
        0 to Pair("CBB (Cassava Bacterial Blight) is characterized by water-soaked lesions, angular leaf spots, and wilting. It can lead to severe defoliation and stem dieback.", "Plant resistant varieties, use disease-free planting materials, and practice crop rotation. Remove and destroy infected plants immediately to prevent spread."),
        1 to Pair("CBSD (Cassava Brown Streak Disease) causes yellowish mottling and chlorosis on lower leaves, and brown streaks on stems. It severely impacts tuber quality with necrotic rot.", "Use certified virus-free cuttings, plant tolerant cultivars, and control whitefly populations. Uproot and burn infected plants."),
        2 to Pair("CGM (Cassava Green Mite) damage appears as tiny yellow/white speckles on leaves, which may lose green color, stunt, or deform. Severe infestations cause leaf drop.", "Introduce natural predatory mites, plant tolerant varieties, and maintain good plant vigor. Chemical acaricides can be used as a last resort."),
        3 to Pair("CMD (Cassava Mosaic Disease) is marked by severe mosaic patterns, yellowing, and distorted, crumpled leaves. It drastically reduces tuber yield.", "Plant resistant or tolerant varieties (the most effective control). Use clean, virus-free stem cuttings and rogue out infected plants early."),
        4 to Pair("Healthy cassava leaves exhibit uniform green color, normal shape, and vigorous growth without any signs of lesions, mottling, or pest damage.", "Maintain regular field monitoring, good agricultural practices, weed control, and optimal soil fertility to ensure continued plant health.")
    )
'''
code = code.replace('private val galleryLauncher = registerForActivityResult', disease_dict + '\n    private val galleryLauncher = registerForActivityResult')

process_injection = '''
        val descInfo = diseaseDescriptions[result.enhancedResult.index]
        if (descInfo != null) {
            txtDiseaseDesc?.text = descInfo.first
            txtDiseaseTreatment?.text = descInfo.second
        }
'''
code = code.replace('txtBaseLabel?.text = result.baseResult.label', process_injection + '\n        txtBaseLabel?.text = result.baseResult.label')

with open('app/src/main/java/com/example/cassavaleafdetector/MainActivity.kt', 'w', encoding='utf-8') as f:
    f.write(code)
