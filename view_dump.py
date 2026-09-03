import re

with open('dump_single_scan.txt', 'r', encoding='utf-8') as f:
    text = f.read()

print('Length of dump:', len(text))
# Let's see some samples of attributes
for line in text.splitlines()[:50]:
    print(line)
