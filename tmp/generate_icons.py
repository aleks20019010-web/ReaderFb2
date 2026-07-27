import xml.etree.ElementTree as ET
import subprocess
import os

def android_vector_to_svg(vector_xml_path, svg_out_path):
    tree = ET.parse(vector_xml_path)
    root = tree.getroot()
    
    # Extract viewport dimensions
    vp_w = root.get('{http://schemas.android.com/apk/res/android}viewportWidth', '108')
    vp_h = root.get('{http://schemas.android.com/apk/res/android}viewportHeight', '108')
    
    svg_header = f'<?xml version="1.0" encoding="UTF-8"?>\n<svg xmlns="http://www.w3.org/2000/svg" width="{vp_w}" height="{vp_h}" viewBox="0 0 {vp_w} {vp_h}">\n'
    svg_body = ""
    
    for child in root:
        tag = child.tag.split('}')[-1]
        if tag == 'path':
            fill_color = child.get('{http://schemas.android.com/apk/res/android}fillColor')
            stroke_color = child.get('{http://schemas.android.com/apk/res/android}strokeColor')
            stroke_width = child.get('{http://schemas.android.com/apk/res/android}strokeWidth')
            stroke_cap = child.get('{http://schemas.android.com/apk/res/android}strokeLineCap')
            path_data = child.get('{http://schemas.android.com/apk/res/android}pathData')
            
            attrs = []
            if path_data:
                attrs.append(f'd="{path_data}"')
            if fill_color:
                if fill_color.startswith('#'):
                    # Handle #AARRGGBB vs #RRGGBB
                    if len(fill_color) == 9: # #AARRGGBB
                        alpha = int(fill_color[1:3], 16) / 255.0
                        hex_c = '#' + fill_color[3:]
                        attrs.append(f'fill="{hex_c}" fill-opacity="{alpha:.3f}"')
                    else:
                        attrs.append(f'fill="{fill_color}"')
                else:
                    attrs.append(f'fill="{fill_color}"')
            else:
                attrs.append('fill="none"')
                
            if stroke_color:
                if stroke_color.startswith('#'):
                    if len(stroke_color) == 9:
                        alpha = int(stroke_color[1:3], 16) / 255.0
                        hex_c = '#' + stroke_color[3:]
                        attrs.append(f'stroke="{hex_c}" stroke-opacity="{alpha:.3f}"')
                    else:
                        attrs.append(f'stroke="{stroke_color}"')
            if stroke_width:
                attrs.append(f'stroke-width="{stroke_width}"')
            if stroke_cap:
                attrs.append(f'stroke-linecap="{stroke_cap}"')
                
            svg_body += f'  <path {" ".join(attrs)} />\n'
            
    svg_footer = '</svg>\n'
    
    with open(svg_out_path, 'w', encoding='utf-8') as f:
        f.write(svg_header + svg_body + svg_footer)

# Create temp SVG for foreground
android_vector_to_svg("app/src/main/res/drawable/ic_launcher_foreground.xml", "/tmp/fg.svg")

# Create background SVG
bg_svg = """<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" width="108" height="108" viewBox="0 0 108 108">
  <defs>
    <linearGradient id="bgGrad" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#001FAF"/>
      <stop offset="100%" stop-color="#640998"/>
    </linearGradient>
  </defs>
  <rect width="108" height="108" fill="url(#bgGrad)"/>
</svg>
"""
with open("/tmp/bg.svg", "w") as f:
    f.write(bg_svg)

# Round background SVG
bg_round_svg = """<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" width="108" height="108" viewBox="0 0 108 108">
  <defs>
    <linearGradient id="bgGrad" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" stop-color="#001FAF"/>
      <stop offset="100%" stop-color="#640998"/>
    </linearGradient>
  </defs>
  <circle cx="54" cy="54" r="54" fill="url(#bgGrad)"/>
</svg>
"""
with open("/tmp/bg_round.svg", "w") as f:
    f.write(bg_round_svg)

densities = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192
}

for folder, size in densities.items():
    dir_path = f"app/src/main/res/{folder}"
    os.makedirs(dir_path, exist_ok=True)
    
    # Render square icon composite
    cmd_square = f"convert -background none -resize {size}x{size} /tmp/bg.svg /tmp/bg_{size}.png && " \
                 f"convert -background none -resize {size}x{size} /tmp/fg.svg /tmp/fg_{size}.png && " \
                 f"convert /tmp/bg_{size}.png /tmp/fg_{size}.png -composite {dir_path}/ic_launcher.png"
    
    # Render round icon composite
    cmd_round = f"convert -background none -resize {size}x{size} /tmp/bg_round.svg /tmp/bg_r_{size}.png && " \
                f"convert /tmp/bg_r_{size}.png /tmp/fg_{size}.png -composite {dir_path}/ic_launcher_round.png"
                
    subprocess.run(cmd_square, shell=True, check=True)
    subprocess.run(cmd_round, shell=True, check=True)
    print(f"Generated {folder} ({size}x{size})")

print("All icons successfully generated!")
