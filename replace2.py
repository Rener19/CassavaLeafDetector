import re

with open('app/src/main/java/com/example/cassavaleafdetector/MainActivity.kt', 'r', encoding='utf-8') as f:
    content = f.read()

start_str = "        txtClassification.text = \"\"\""
end_str = "        \"\"\".trimMargin()"

new_text = '''        txtClassification.text = """
            |Accuracy:
            |Base: 80.14%  |  Enhanced: 82.22% (+2.08%)
            |
            |Precision (Macro):
            |Base: 82.52%  |  Enhanced: 84.07% (+1.55%)
            |
            |Recall (Macro):
            |Base: 78.67%  |  Enhanced: 81.34% (+2.67%)
            |
            |F1 Score (Macro):
            |Base: 79.81%  |  Enhanced: 82.24% (+2.43%)
            |
            |True Negative Rate (Specificity):
            |Base: 94.84%  |  Enhanced: 95.41% (+0.57%)
            |
            |Matthews Correlation Coefficient (MCC):
            |Base: 0.7525  |  Enhanced: 0.7783 (+0.0258)
        """.trimMargin()'''

pattern = re.compile(re.escape(start_str) + r'.*?' + re.escape(end_str), re.DOTALL)

# Because there are three trimMargin blocks, let's just make sure we hit the correct one.
# Wait, the end_str will just match the first one! We have three txt*.text blocks!
# Let's be very specific in the script.
