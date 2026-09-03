import re, subprocess

aapt2 = r'C:\Users\raini\AppData\Local\Android\Sdk\build-tools\36.0.0\aapt2.exe'
apk = r'C:\Users\raini\AndroidStudioProjects\CassavaLeafDetector\app-thesis-debug.apk'

# Build res_map
output = subprocess.check_output([aapt2, 'dump', 'resources', apk], encoding='utf-8', errors='ignore')
res_map = {}
for m in re.finditer(r'resource (0x[0-9a-fA-F]+) ([\w/]+)', output):
    res_map[m.group(1).lower()] = m.group(2)

# Load dump
with open('dump_single_scan.txt', 'r', encoding='utf-8') as f:
    lines = f.readlines()

class Node:
    def __init__(self, tag, depth):
        self.tag = tag
        self.depth = depth
        self.attrs = {}
        self.children = []

root = None
stack = []

attr_re = re.compile(r'A:\s+(?:http://schemas\.android\.com/apk/res/android:|http://schemas\.android\.com/apk/res-auto:)?([\w:]+)(?:\(0x[0-9a-fA-F]+\))?=(.*)')

for line in lines:
    line_str = line.rstrip()
    if not line_str.strip():
        continue
    
    # Check Element
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
        
    # Check Attribute
    m_attr = re.match(r'^\s*A:\s+(?:http://schemas\.android\.com/apk/res/android:|http://schemas\.android\.com/apk/res-auto:|)?([\w:]+)(?:\(0x[0-9a-fA-F]+\))?=(.*)', line_str)
    if m_attr and stack:
        attr_name = m_attr.group(1)
        val_raw = m_attr.group(2).strip()
        
        # Resolve raw or resource
        # e.g. "Start Diagnosis" (Raw: "Start Diagnosis")
        m_raw = re.search(r'\(Raw:\s*"([^"]*)"\)', val_raw)
        if m_raw:
            val = m_raw.group(1)
        elif val_raw.startswith('@0x'):
            res_id = val_raw.split()[0].lower()
            val = '@' + res_map.get(res_id, val_raw)
        elif val_raw.startswith('?0x'):
            res_id = '0x' + val_raw[3:].split()[0].lower()
            val = '?' + res_map.get(res_id, val_raw)
        else:
            val = val_raw.split()[0]
            
        # Determine namespace
        if 'res-auto' in line_str or attr_name in ['layout_behavior', 'cardCornerRadius', 'cardElevation', 'rippleColor', 'strokeColor', 'strokeWidth', 'cornerRadius', 'indicatorColor', 'trackColor', 'trackCornerRadius', 'elevation', 'menu', 'tint', 'layout_constraintDimensionRatio', 'layout_constraintBottom_toTopOf', 'layout_constraintBottom_toBottomOf', 'layout_constraintEnd_toEndOf', 'layout_constraintStart_toStartOf', 'layout_constraintStart_toEndOf', 'layout_constraintTop_toTopOf', 'layout_constraintTop_toBottomOf']:
            full_attr = f'app:{attr_name}'
        else:
            full_attr = f'android:{attr_name}'
            
        stack[-1].attrs[full_attr] = val

# Function to render Node to XML
def to_xml(node, indent=0):
    ind = '    ' * indent
    tag = node.tag
    res = f'{ind}<{tag}'
    if indent == 0:
        res += '\n    xmlns:android=\"http://schemas.android.com/apk/res/android\"\n    xmlns:app=\"http://schemas.android.com/apk/res-auto\"\n    xmlns:tools=\"http://schemas.android.com/tools\"'
    
    for k, v in node.attrs.items():
        res += f'\n{ind}    {k}=\"{v}\"'
        
    if not node.children:
        res += ' />\n'
    else:
        res += '>\n'
        for child in node.children:
            res += to_xml(child, indent + 1)
        res += f'{ind}</{tag}>\n'
    return res

xml_content = '<?xml version=\"1.0\" encoding=\"utf-8\"?>\n' + to_xml(root)
with open('reconstructed_layout_single_scan.xml', 'w', encoding='utf-8') as f:
    f.write(xml_content)

print('Generated reconstructed_layout_single_scan.xml')
