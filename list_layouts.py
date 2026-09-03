import subprocess, re

aapt2 = r'C:\Users\raini\AppData\Local\Android\Sdk\build-tools\36.0.0\aapt2.exe'
apk = r'C:\Users\raini\AndroidStudioProjects\CassavaLeafDetector\app-thesis-debug.apk'

# Let's see all layouts in apk
output = subprocess.check_output([aapt2, 'dump', 'resources', apk], encoding='utf-8', errors='ignore')
layouts = re.findall(r'resource 0x[0-9a-fA-F]+ layout/(\w+)', output)
print('Layouts in APK:', set(layouts))
