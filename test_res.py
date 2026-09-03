import sys, re

with open('apk_single_scan_dump.txt', 'r', encoding='utf-8') as f:
    lines = f.readlines()

res_map = {}
# Read resources from apk
import subprocess
aapt2 = r'C:\Users\raini\AppData\Local\Android\Sdk\build-tools\36.0.0\aapt2.exe'
apk = r'C:\Users\raini\AndroidStudioProjects\CassavaLeafDetector\app-thesis-debug.apk'
output = subprocess.check_output([aapt2, 'dump', 'resources', apk], encoding='utf-8', errors='ignore')

for m in re.finditer(r'resource (0x[0-9a-fA-F]+) ([\w/]+)', output):
    res_map[m.group(1).lower()] = m.group(2)

print(f"Loaded {len(res_map)} resource mappings.")
