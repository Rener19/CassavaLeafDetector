import subprocess, zipfile, os

apk = r'C:\Users\raini\AndroidStudioProjects\CassavaLeafDetector\app-thesis-debug.apk'

with zipfile.ZipFile(apk, 'r') as z:
    for name in z.namelist():
        if name.endswith('.dex'):
            print('Dex file:', name)
