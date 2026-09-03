import subprocess, re

aapt2 = r'C:\Users\raini\AppData\Local\Android\Sdk\build-tools\36.0.0\aapt2.exe'
apk = r'C:\Users\raini\AndroidStudioProjects\CassavaLeafDetector\app-thesis-debug.apk'

# Let's see the dialog_thesis_metrics layout
dump_dialog = subprocess.check_output([aapt2, 'dump', 'xmltree', '--file', 'res/layout/dialog_thesis_metrics.xml', apk], encoding='utf-8', errors='ignore')
print(dump_dialog[:1000])
