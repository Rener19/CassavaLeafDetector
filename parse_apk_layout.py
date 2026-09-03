import subprocess, re, os

aapt2 = r'C:\Users\raini\AppData\Local\Android\Sdk\build-tools\36.0.0\aapt2.exe'
apk = r'C:\Users\raini\AndroidStudioProjects\CassavaLeafDetector\app-thesis-debug.apk'

# Build res_map
output = subprocess.check_output([aapt2, 'dump', 'resources', apk], encoding='utf-8', errors='ignore')
res_map = {}
for m in re.finditer(r'resource (0x[0-9a-fA-F]+) ([\w/]+)', output):
    res_map[m.group(1).lower()] = m.group(2)

def dump_and_parse(layout_name):
    dump_out = subprocess.check_output([aapt2, 'dump', 'xmltree', '--file', f'res/layout/{layout_name}.xml', apk], encoding='utf-8', errors='ignore')
    return dump_out

with open('dump_single_scan.txt', 'w', encoding='utf-8') as f:
    f.write(dump_and_parse('layout_single_scan'))

print('Saved dump_single_scan.txt')
