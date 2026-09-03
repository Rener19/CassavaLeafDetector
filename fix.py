with open('app/build.gradle.kts', 'r', encoding='utf-8') as f:
    code = f.read()

code = code.replace('versionNameSuffix = "-user"', 'versionNameSuffix = "-user"\n            resValue("string", "app_name", "User Cassava Detector")')
code = code.replace('versionNameSuffix = "-thesis"', 'versionNameSuffix = "-thesis"\n            resValue("string", "app_name", "Cassava Detector")')

with open('app/build.gradle.kts', 'w', encoding='utf-8') as f:
    f.write(code)

with open('app/src/main/res/values/strings.xml', 'w', encoding='utf-8') as f:
    f.write('<resources>\n</resources>')
