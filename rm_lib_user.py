import re

with open('app/src/user/res/layout/layout_single_scan.xml', 'r', encoding='utf-8') as f:
    content = f.read()

content = re.sub(r'<!-- Disease Library Section -->.*?</HorizontalScrollView>', '', content, count=1, flags=re.DOTALL)

with open('app/src/user/res/layout/layout_single_scan.xml', 'w', encoding='utf-8') as f:
    f.write(content)
