import re, subprocess

aapt2 = r'C:\Users\raini\AppData\Local\Android\Sdk\build-tools\36.0.0\aapt2.exe'
apk = r'C:\Users\raini\AndroidStudioProjects\CassavaLeafDetector\app-thesis-debug.apk'

# Build res_map
output = subprocess.check_output([aapt2, 'dump', 'resources', apk], encoding='utf-8', errors='ignore')
res_map = {}
for m in re.finditer(r'resource (0x[0-9a-fA-F]+) ([\w/]+)', output):
    res_map[m.group(1).lower()] = m.group(2)

# Also check system attributes/drawables
sys_map = {
    '0x01080037': 'android:drawable/ic_menu_camera',
    '0x0108003f': 'android:drawable/ic_menu_gallery',
    '0x0108009b': 'android:drawable/ic_menu_help',
    '0x01080038': 'android:drawable/ic_menu_close_clear_cancel',
    '0x0106000d': 'android:color/transparent',
    '0x01010054': '?android:attr/windowBackground',
    '0x01010078': '?android:attr/progressBarStyleHorizontal',
    '0x010103b1': 'center'
}

with open('dump_single_scan.txt', 'r', encoding='utf-8') as f:
    lines = f.readlines()

class Node:
    def __init__(self, tag, depth):
        self.tag = tag
        self.depth = depth
        self.attrs = []
        self.children = []

root = None
stack = []

for line in lines:
    line_str = line.rstrip()
    if not line_str.strip():
        continue
    
    m_elem = re.match(r'^(\s*)E:\s+([\w\.\$]+)', line_str)
    if m_elem:
        indent = len(m_elem.group(1))
        tag = m_elem.group(2)
        node = Node(tag, indent)
        
        while stack and stack[-1].depth >= indent:
            stack.pop()
            
        if not stack:
            root = node
        else:
            stack[-1].children.append(node)
        stack.append(node)
        continue
        
    m_attr = re.match(r'^\s*A:\s+(?:http://schemas\.android\.com/apk/res/android:|http://schemas\.android\.com/apk/res-auto:|)?([\w:]+)(?:\(0x[0-9a-fA-F]+\))?=(.*)', line_str)
    if m_attr and stack:
        attr_name = m_attr.group(1)
        val_raw = m_attr.group(2).strip()
        
        # Dimensions
        if val_raw.startswith('-1'):
            val = 'match_parent'
        elif val_raw.startswith('-2'):
            val = 'wrap_content'
        else:
            m_raw = re.search(r'\(Raw:\s*"([^"]*)"\)', val_raw)
            if m_raw:
                val = m_raw.group(1)
            elif val_raw.startswith('@0x'):
                res_id = val_raw.split()[0][1:].lower() # remove leading @
                if res_id in sys_map:
                    val = '@' + sys_map[res_id]
                elif res_id in res_map:
                    val = '@' + res_map[res_id]
                else:
                    val = '@' + res_id
            elif val_raw.startswith('?0x'):
                res_id = val_raw.split()[0][1:].lower()
                if res_id in sys_map:
                    val = sys_map[res_id]
                elif res_id in res_map:
                    val = '?' + res_map[res_id]
                else:
                    val = '?' + res_id
            else:
                val = val_raw.split()[0]
            
        # Specific attribute fixes
        if attr_name == 'id' and val.startswith('@id/'):
            val = '@+id/' + val[4:]
            
        if attr_name == 'textStyle':
            if val == '0x00000001':
                val = 'bold'
            elif val == '0x00000002':
                val = 'italic'
            elif val == '0x00000000':
                val = 'normal'
                
        if attr_name == 'orientation':
            if val == '1':
                val = 'vertical'
            elif val == '0':
                val = 'horizontal'
                
        if attr_name == 'visibility':
            if val == '2':
                val = 'gone'
            elif val == '1':
                val = 'invisible'
            elif val == '0':
                val = 'visible'
                
        if attr_name == 'gravity':
            if val == '0x00000011':
                val = 'center'
            elif val == '0x00000001':
                val = 'center_horizontal'
            elif val == '0x00000010':
                val = 'center_vertical'
                
        if attr_name == 'layout_gravity':
            if val == '0x00000011':
                val = 'center'
            elif val == '0x00000001':
                val = 'center_horizontal'
            elif val == '0x00000005' or val == '0x00800005':
                val = 'end'
                
        if attr_name == 'scaleType':
            scale_types = {
                '0': 'matrix',
                '1': 'fitXY',
                '2': 'fitStart',
                '3': 'fitCenter',
                '4': 'fitEnd',
                '5': 'center',
                '6': 'centerCrop',
                '7': 'centerInside'
            }
            val = scale_types.get(val, val)
            
        if attr_name == 'style':
            if 'progressBarStyleHorizontal' in val or val == '?0x01010078':
                val = '?android:attr/progressBarStyleHorizontal'
            elif '@0x7f110468' in val or 'Widget_MaterialComponents_Button_TextButton' in val or 'Button' in val:
                val = '@style/Widget.MaterialComponents.Button.TextButton'
                
        if 'res-auto' in line_str or attr_name.startswith('layout_constraint') or attr_name in ['layout_behavior', 'cardCornerRadius', 'cardElevation', 'rippleColor', 'strokeColor', 'strokeWidth', 'cornerRadius', 'indicatorColor', 'trackColor', 'trackCornerRadius', 'elevation', 'menu', 'tint', 'progressTint', 'progressBackgroundTint']:
            full_attr = f'app:{attr_name}'
            if attr_name.startswith('layout_constraint') and val.startswith('@id/'):
                val = '@id/' + val[4:]
            elif attr_name.startswith('layout_constraint') and val == '0':
                val = 'parent'
        else:
            full_attr = f'android:{attr_name}'
            
        stack[-1].attrs.append((full_attr, val))

def to_xml(node, indent=0):
    ind = '    ' * indent
    tag = node.tag
    res = f'{ind}<{tag}'
    if indent == 0:
        res += '\n    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n    xmlns:app=\"http://schemas.android.com/apk/res-auto\"\n    xmlns:tools=\"http://schemas.android.com/tools\"'
    
    for k, v in node.attrs:
        res += f'\n{ind}    {k}=\"{v}\"'
        
    if not node.children:
        res += ' />\n'
    else:
        res += '>\n'
        for child in node.children:
            res += to_xml(child, indent + 1)
        res += f'{ind}</{tag}>\n'
    return res

xml_out = '<?xml version=\"1.0\" encoding=\"utf-8\"?>\n' + to_xml(root)
with open('app/src/main/res/layout/layout_single_scan.xml', 'w', encoding='utf-8') as f:
    f.write(xml_out)

print('Updated layout_single_scan.xml!')
