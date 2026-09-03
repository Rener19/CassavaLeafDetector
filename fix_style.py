with open('app/src/main/res/layout/layout_single_scan.xml', 'r', encoding='utf-8') as f:
    c = f.read()

c = c.replace('style=\"@style/Widget\"', 'style=\"@style/Widget.MaterialComponents.Button.TextButton\"')

with open('app/src/main/res/layout/layout_single_scan.xml', 'w', encoding='utf-8') as f:
    f.write(c)
