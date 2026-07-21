import math
import random

def get_gradient(cx, cy, r, start_color, end_color):
    return f"""<aapt:attr name="android:fillColor">
            <gradient android:type="radial" android:centerX="{cx}" android:centerY="{cy}" android:gradientRadius="{r}" android:startColor="{start_color}" android:endColor="{end_color}" />
        </aapt:attr>"""

def generate():
    # BACKGROUND
    xml = []
    xml.append('<?xml version="1.0" encoding="utf-8"?>')
    xml.append('<vector xmlns:android="http://schemas.android.com/apk/res/android" xmlns:aapt="http://schemas.android.com/aapt" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">')
    
    # Background base
    xml.append(f'<path android:pathData="M0,0h108v108h-108z">\n{get_gradient(54, 54, 80, "#1A0530", "#000000")}\n</path>')
    
    # Nebulas
    random.seed(42)
    for _ in range(20):
        cx, cy = random.uniform(0, 108), random.uniform(0, 108)
        r = random.uniform(20, 55)
        c1 = random.choice(["#339B59B6", "#224A148C", "#33FF4081", "#2200BCD4", "#33E0B0FF"])
        c2 = "#00000000"
        xml.append(f'<path android:pathData="M {cx},{cy} m -{r},0 a {r},{r} 0 1,1 {r*2},0 a {r},{r} 0 1,1 -{r*2},0">\n{get_gradient(cx, cy, r, c1, c2)}\n</path>')
        
    # Starfield
    for _ in range(400):
        x, y = random.uniform(0, 108), random.uniform(0, 108)
        r = random.uniform(0.1, 0.9)
        a = random.uniform(0.1, 0.9)
        c = random.choice(["#FFFFFF", "#FFD700", "#E0B0FF", "#87CEFA", "#F5F0F8"])
        xml.append(f'<path android:fillColor="{c}" android:fillAlpha="{a:.2f}" android:pathData="M {x},{y} m -{r},0 a {r},{r} 0 1,1 {r*2},0 a {r},{r} 0 1,1 -{r*2},0" />')

    # Star bursts
    for _ in range(50):
        x, y = random.uniform(5, 103), random.uniform(5, 103)
        s = random.uniform(1, 4)
        a = random.uniform(0.3, 0.9)
        c = random.choice(["#FFFFFF", "#FFD700", "#FFF68F"])
        path = f"M {x},{y-s} Q {x},{y} {x+s},{y} Q {x},{y} {x},{y+s} Q {x},{y} {x-s},{y} Q {x},{y} {x},{y-s} Z"
        xml.append(f'<path android:fillColor="{c}" android:fillAlpha="{a:.2f}" android:pathData="{path}" />')

    xml.append("</vector>")
    with open("app/src/main/res/drawable/ic_launcher_background.xml", "w") as f:
        f.write("\n".join(xml))
        
    # FOREGROUND
    xml = []
    xml.append('<?xml version="1.0" encoding="utf-8"?>')
    xml.append('<vector xmlns:android="http://schemas.android.com/apk/res/android" xmlns:aapt="http://schemas.android.com/aapt" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">')
    
    # Book Cover Base
    xml.append('<path android:fillColor="#0D0415" android:strokeColor="#FFD700" android:strokeWidth="2.0" android:strokeLineJoin="round" android:pathData="M 12,85 C 12,85 35,70 54,75 C 73,70 96,85 96,85 L 96,93 C 96,93 73,78 54,83 C 35,78 12,93 12,93 Z" />')
    
    # Intricate Cover Filigree / Mandala
    for i in range(40):
        t = i / 40
        cx = 33
        cy = 82
        x = cx + math.cos(t * math.pi * 2) * 12
        y = cy + math.sin(t * math.pi * 2) * 4
        path = f"M {x},{y} C {x+4},{y-4} {x+8},{y+4} {x},{y+8} C {x-4},{y+4} {x},{y} {x},{y}"
        xml.append(f'<path android:strokeColor="#B8860B" android:strokeAlpha="0.7" android:strokeWidth="0.2" android:fillColor="#00000000" android:pathData="{path}" />')
        
        cx = 75
        x = cx + math.cos(t * math.pi * 2) * 12
        y = cy + math.sin(t * math.pi * 2) * 4
        path = f"M {x},{y} C {x+4},{y-4} {x+8},{y+4} {x},{y+8} C {x-4},{y+4} {x},{y} {x},{y}"
        xml.append(f'<path android:strokeColor="#B8860B" android:strokeAlpha="0.7" android:strokeWidth="0.2" android:fillColor="#00000000" android:pathData="{path}" />')

    # Extremely Detailed Book Pages (80 pages)
    num_pages = 40
    pages_xml = []
    for i in range(num_pages):
        t = i / (num_pages - 1)
        
        # Left pages
        start_x = 16 - t * 4
        start_y = 75 + t * 7
        ctrl1_x = 16 - t * 16
        ctrl1_y = 42 + t * 6
        end_x = 54
        end_y = 35
        base_x = 54
        base_y = 75
        
        r = int(220 + t * 35)
        g = int(200 + t * 55)
        b = int(240 + t * 15)
        color = f"#{r:02X}{g:02X}{b:02X}"
        if t == 0: color = "#FFFFFF"
        
        path = f"M {start_x:.2f},{start_y:.2f} C {start_x:.2f},{start_y:.2f} {ctrl1_x:.2f},{ctrl1_y:.2f} {end_x:.2f},{end_y:.2f} L {base_x:.2f},{base_y:.2f} C {start_x + (base_x-start_x)*0.2:.2f},{base_y - (base_y-start_y)*0.2:.2f} {start_x:.2f},{start_y:.2f} {start_x:.2f},{start_y:.2f} Z"
        pages_xml.insert(0, f'<path android:fillColor="{color}" android:strokeColor="#C8A2C8" android:strokeWidth="0.1" android:pathData="{path}" />')

        # Right pages
        start_x = 92 + t * 4
        start_y = 75 + t * 7
        ctrl1_x = 92 + t * 16
        ctrl1_y = 42 + t * 6
        end_x = 54
        end_y = 35
        base_x = 54
        base_y = 75
        
        r = int(200 + t * 55)
        g = int(180 + t * 75)
        b = int(220 + t * 35)
        color = f"#{r:02X}{g:02X}{b:02X}"
        if t == 0: color = "#FFFFFF"
        
        path = f"M {start_x:.2f},{start_y:.2f} C {start_x:.2f},{start_y:.2f} {ctrl1_x:.2f},{ctrl1_y:.2f} {end_x:.2f},{end_y:.2f} L {base_x:.2f},{base_y:.2f} C {start_x + (base_x-start_x)*0.2:.2f},{base_y - (base_y-start_y)*0.2:.2f} {start_x:.2f},{start_y:.2f} {start_x:.2f},{start_y:.2f} Z"
        pages_xml.insert(0, f'<path android:fillColor="{color}" android:strokeColor="#C8A2C8" android:strokeWidth="0.1" android:pathData="{path}" />')
        
    xml.extend(pages_xml)

    # Page writing / Runes
    for i in range(18):
        y = 48 + i * 1.5
        w = 17 - i * 0.6
        xml.append(f'<path android:strokeColor="#9B59B6" android:strokeAlpha="0.8" android:strokeWidth="0.4" android:pathData="M {50-w},{y} Q {50-w/2},{y-1.5} 50,{y+1.5}" />')
        xml.append(f'<path android:strokeColor="#9B59B6" android:strokeAlpha="0.8" android:strokeWidth="0.4" android:pathData="M {58+w},{y} Q {58+w/2},{y-1.5} 58,{y+1.5}" />')

    # Center Magic Source / Core
    xml.append(f'<path android:pathData="M 54,35 m -28,0 a 28,28 0 1,1 56,0 a 28,28 0 1,1 -56,0">\n{get_gradient(54, 35, 28, "#669B59B6", "#009B59B6")}\n</path>')
    xml.append(f'<path android:pathData="M 54,35 m -16,0 a 16,16 0 1,1 32,0 a 16,16 0 1,1 -32,0">\n{get_gradient(54, 35, 16, "#AAFFD700", "#00FFD700")}\n</path>')
    
    # The Core Crystal
    xml.append('<path android:fillColor="#FFFFFF" android:pathData="M 54,26 L 58,32 L 64,35 L 58,38 L 54,44 L 50,38 L 44,35 L 50,32 Z" />')
    xml.append('<path android:fillColor="#FFF68F" android:pathData="M 54,29 L 56,33 L 60,35 L 56,37 L 54,41 L 52,37 L 48,35 L 52,33 Z" />')

    # Volumetric Light Rays (150 rays!)
    for i in range(150):
        angle = (i / 150) * math.pi * 2
        length = random.uniform(30, 75)
        width = random.uniform(0.1, 0.6)
        alpha = random.uniform(0.05, 0.4)
        x2 = 54 + math.cos(angle) * length
        y2 = 35 + math.sin(angle) * length
        c = random.choice(["#FFFFFF", "#FFD700", "#E0B0FF", "#9B59B6", "#FFF68F"])
        xml.append(f'<path android:strokeColor="{c}" android:strokeWidth="{width:.2f}" android:strokeAlpha="{alpha:.2f}" android:pathData="M 54,35 L {x2:.2f},{y2:.2f}" />')

    # Floating Magical Runes
    for _ in range(80):
        x = random.uniform(15, 93)
        y = random.uniform(5, 70)
        s = random.uniform(1, 4)
        c = random.choice(["#FFD700", "#FFF68F", "#E0B0FF", "#FFFFFF", "#FF4081"])
        a = random.uniform(0.4, 1.0)
        paths = [
            f"M {x},{y} L {x+s},{y+s}", f"M {x+s},{y} L {x},{y+s}",
            f"M {x+s/2},{y-s/2} L {x+s/2},{y+s*1.5}", f"M {x-s/2},{y+s/2} L {x+s*1.5},{y+s/2}",
            f"M {x},{y+s/2} L {x+s},{y+s/2}"
        ]
        selected = " ".join(random.sample(paths, random.randint(2, 4)))
        xml.append(f'<path android:strokeColor="{c}" android:strokeAlpha="{a:.2f}" android:strokeWidth="0.4" android:strokeLineCap="round" android:pathData="{selected}" />')

    # Bookmark Ribbon
    xml.append(f'<path android:pathData="M 54,38 C 65,45 75,60 70,95 L 63,88 L 56,98 C 65,65 58,50 54,38 Z">\n{get_gradient(60, 60, 45, "#FF4081", "#7B1FA2")}\n</path>')
    xml.append('<path android:strokeColor="#FFD700" android:strokeWidth="1.0" android:pathData="M 54,38 C 65,45 75,60 70,95 L 63,88 L 56,98" />')
    
    # Foreground Sparkles
    for _ in range(50):
        x, y = random.uniform(25, 83), random.uniform(15, 85)
        s = random.uniform(1.5, 3.0)
        a = random.uniform(0.6, 1.0)
        c = random.choice(["#FFFFFF", "#FFD700"])
        path = f"M {x},{y-s} Q {x},{y} {x+s},{y} Q {x},{y} {x},{y+s} Q {x},{y} {x-s},{y} Q {x},{y} {x},{y-s} Z"
        xml.append(f'<path android:fillColor="{c}" android:fillAlpha="{a:.2f}" android:pathData="{path}" />')

    xml.append('</vector>')
    
    with open("app/src/main/res/drawable/ic_launcher_foreground.xml", "w") as f:
        f.write("\n".join(xml))

generate()
