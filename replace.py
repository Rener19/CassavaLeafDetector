import re

with open('app/src/main/java/com/example/cassavaleafdetector/MainActivity.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Replace the text blocks
start_str = "        // Static Placeholder Data for User to Update"
end_str = "        btnClose.setOnClickListener { dialog.dismiss() }"

new_text = '''        // Static Placeholder Data for User to Update
        txtTraining.text = """
            |Training Time:
            |Base: 40 mins  |  Enhanced: 2 hrs 30 mins
            |
            |Memory Usage:
            |Base: System RAM: 4.6GB, GPU RAM: 1.1GB
            |Enhanced: System RAM: 6.0GB, GPU RAM: 2.1GB
            |
            |Weight Discrepancy (L2 Norm):
            |Base: 1855.56  |  Enhanced: 1846.71 (Lower = Better)
        """.trimMargin()
        
        txtClassification.text = """
            |Accuracy:
            |Base: 80.67%  |  Enhanced: 81.47% (+0.80%)
            |
            |Precision (Macro):
            |Base: 81.46%  |  Enhanced: 82.83% (+1.37%)
            |
            |Recall (Macro):
            |Base: 80.18%  |  Enhanced: 81.08% (+0.90%)
            |
            |F1 Score (Macro):
            |Base: 80.38%  |  Enhanced: 81.48% (+1.11%)
            |
            |True Negative Rate (Specificity):
            |Base: 95.10%  |  Enhanced: 95.29% (+0.19%)
            |
            |Matthews Correlation Coefficient (MCC):
            |Base: 0.7583  |  Enhanced: 0.7696 (+0.0114)
        """.trimMargin()
        
        txtInference.text = """
            |Avg. Inference Latency (Per Image):
            |Base: 11.75 ms  |  Enhanced: 18.04 ms (+6.29 ms)
            |
            |Multiply-Accumulate Operations (MACs):
            |Base: 56,577,051  |  Enhanced: 98,254,540 (+41,677,489)
            |
            |Model Size (Total Parameters):
            |Base: 942,005  |  Enhanced: 965,719 (+23,714)
        """.trimMargin()
        
'''

# Find the block and replace
pattern = re.compile(re.escape(start_str) + r'.*?(?=' + re.escape("        btnClose.setOnClickListener") + ')', re.DOTALL)
content = pattern.sub(new_text, content)

with open('app/src/main/java/com/example/cassavaleafdetector/MainActivity.kt', 'w', encoding='utf-8') as f:
    f.write(content)
