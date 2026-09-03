import subprocess, re

aapt2 = r'C:\Users\raini\AppData\Local\Android\Sdk\build-tools\36.0.0\aapt2.exe'
apk = r'C:\Users\raini\AndroidStudioProjects\CassavaLeafDetector\app-thesis-debug.apk'

output = subprocess.check_output([aapt2, 'dump', 'resources', apk], encoding='utf-8', errors='ignore')
res_map = {}
for m in re.finditer(r'resource (0x[0-9a-fA-F]+) ([\w/]+)', output):
    res_map[m.group(1).lower()] = m.group(2)

for hex_id in ['0x7f08010b', '0x7f08007b', '0x7f0800ff', '0x7f08010a', '0x7f080078', '0x7f0800fd']:
    print(hex_id, '->', res_map.get(hex_id))
