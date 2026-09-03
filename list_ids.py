import subprocess, re

aapt2 = r'C:\Users\raini\AppData\Local\Android\Sdk\build-tools\36.0.0\aapt2.exe'
apk = r'C:\Users\raini\AndroidStudioProjects\CassavaLeafDetector\app-thesis-debug.apk'

# Let's see all IDs in layout_single_scan in the APK
dump_out = subprocess.check_output([aapt2, 'dump', 'xmltree', '--file', 'res/layout/layout_single_scan.xml', apk], encoding='utf-8', errors='ignore')

# Extract id lines
ids = re.findall(r'id\(0x[0-9a-fA-F]+\)=@([\w/]+|0x[0-9a-fA-F]+)', dump_out)

res_output = subprocess.check_output([aapt2, 'dump', 'resources', apk], encoding='utf-8', errors='ignore')
res_map = {}
for m in re.finditer(r'resource (0x[0-9a-fA-F]+) ([\w/]+)', res_output):
    res_map[m.group(1).lower()] = m.group(2)

named_ids = [res_map.get(i.lower(), i) for i in ids]
print('IDs in layout_single_scan in APK:')
for nid in named_ids:
    print(' ', nid)
