import re

with open('app/src/main/res/layout/layout_single_scan.xml', 'r', encoding='utf-8') as f:
    content = f.read()

# 1. Remove Banner Header Card
content = re.sub(r'<!-- Banner Header Card -->.*?/com\.google\.android\.material\.card\.MaterialCardView>', '', content, count=1, flags=re.DOTALL)

# 2. Remove Model Training Metrics Overview
content = re.sub(r'<!-- Model Training Metrics Overview -->.*?/com\.google\.android\.material\.card\.MaterialCardView>', '', content, count=1, flags=re.DOTALL)

# 3. Remove Disease Library
content = re.sub(r'<!-- Disease Library -->.*?/com\.google\.android\.material\.card\.MaterialCardView>', '', content, count=1, flags=re.DOTALL)

with open('app/src/main/res/layout/layout_single_scan.xml', 'w', encoding='utf-8') as f:
    f.write(content)
