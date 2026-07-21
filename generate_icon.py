import math

def generate_stars(num_stars, seed):
    import random
    random.seed(seed)
    stars = ""
    for _ in range(num_stars):
        x = random.uniform(10, 98)
        y = random.uniform(10, 98)
        r = random.uniform(0.5, 2.0)
        alpha = random.uniform(0.3, 1.0)
        color = random.choice(["#FFFFFF", "#FFD700", "#FFF68F", "#E8D8F0", "#C8B8D8"])
        stars += f'    <path android:fillColor="{color}" android:fillAlpha="{alpha:.2f}" android:pathData="M {x:.1f} {y:.1f} a {r:.1f} {r:.1f} 0 1 0 {2*r:.1f} 0 a {r:.1f} {r:.1f} 0 1 0 -{2*r:.1f} 0" />\n'
    return stars

def generate_sparkles(num_sparkles, seed):
    import random
    random.seed(seed)
    sparkles = ""
    for _ in range(num_sparkles):
        x = random.uniform(20, 88)
        y = random.uniform(20, 88)
        s = random.uniform(2, 6)
        color = random.choice(["#FFFFFF", "#FFD700", "#FFF68F"])
        path = f"M {x},{y} L {x+s*0.2},{y+s*0.8} L {x+s},{y+s} L {x+s*0.2},{y+s*1.2} L {x},{y+s*2} L {x-s*0.2},{y+s*1.2} L {x-s},{y+s} L {x-s*0.2},{y+s*0.8} Z"
        sparkles += f'    <path android:fillColor="{color}" android:pathData="{path}" />\n'
    return sparkles

def generate_pages(left=True):
    pages = ""
    num_pages = 12
    for i in range(num_pages):
        t = i / (num_pages - 1)
        # interpolating the curl
        if left:
            start_x = 26 - t * 2
            start_y = 70 + t * 4
            ctrl1_x = 26 - t * 4
            ctrl1_y = 46 + t * 2
            end_x = 54
            end_y = 42
            base_x = 54
            base_y = 66
        else:
            start_x = 82 + t * 2
            start_y = 70 + t * 4
            ctrl1_x = 82 + t * 4
            ctrl1_y = 46 + t * 2
            end_x = 54
            end_y = 42
            base_x = 54
            base_y = 66
            
        color = f"#{int(240 + t*15):02X}{int(230 + t*25):02X}{int(245 + t*10):02X}"
        if not left:
            color = f"#{int(230 + t*25):02X}{int(216 + t*39):02X}{int(240 + t*15):02X}"
            
        if t == 0:
            color = "#FFFFFF"

        path = f"M {start_x:.1f},{start_y:.1f} C {start_x:.1f},{start_y:.1f} {ctrl1_x:.1f},{ctrl1_y:.1f} {end_x:.1f},{end_y:.1f} L {base_x:.1f},{base_y:.1f} C {start_x + (base_x-start_x)*0.3:.1f},{base_y - (base_y-start_y)*0.3:.1f} {start_x:.1f},{start_y:.1f} {start_x:.1f},{start_y:.1f} Z"
        
        pages = f'    <path android:fillColor="{color}" android:strokeColor="#D0C8D8" android:strokeWidth="0.3" android:pathData="{path}" />\n' + pages
    return pages

def generate_swirls():
    swirls = ""
    for i in range(40):
        t = i / 39.0
        angle = t * math.pi * 6
        radius = 5 + t * 30
        x = 54 + math.cos(angle) * radius
        y = 54 + math.sin(angle) * radius - 15
        
        r = 3.0 * (1.0 - t)
        alpha = 1.0 - t
        color = "#FFD700" if i % 2 == 0 else "#9B59B6"
        swirls += f'    <path android:fillColor="{color}" android:fillAlpha="{alpha:.2f}" android:pathData="M {x:.1f} {y:.1f} a {r:.1f} {r:.1f} 0 1 0 {2*r:.1f} 0 a {r:.1f} {r:.1f} 0 1 0 -{2*r:.1f} 0" />\n'
    return swirls

bg_xml = f"""<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:type="radial"
                android:centerX="54"
                android:centerY="54"
                android:gradientRadius="76"
                android:startColor="#2A1040"
                android:endColor="#05020E" />
        </aapt:attr>
    </path>
    <path android:pathData="M 0,0 C 40,20 20,80 108,108 L 108,0 Z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:type="linear"
                android:startX="0"
                android:startY="0"
                android:endX="108"
                android:endY="108"
                android:startColor="#1A9B59B6"
                android:endColor="#00000000" />
        </aapt:attr>
    </path>
{generate_stars(50, 42)}
</vector>
"""

fg_xml = f"""<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    
    <!-- Outer Glow / Magic Aura -->
    <path android:pathData="M 54,40 A 34,34 0 1,1 53.9,40 Z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:type="radial"
                android:centerX="54"
                android:centerY="40"
                android:gradientRadius="34"
                android:startColor="#669B59B6"
                android:endColor="#009B59B6" />
        </aapt:attr>
    </path>
    
    <path android:pathData="M 54,40 A 20,20 0 1,1 53.9,40 Z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:type="radial"
                android:centerX="54"
                android:centerY="40"
                android:gradientRadius="20"
                android:startColor="#88FFD700"
                android:endColor="#00FFD700" />
        </aapt:attr>
    </path>

    <!-- Crescent Moon -->
    <path android:fillColor="#FFD700" android:pathData="M 54,20 A 25,25 0 1,0 79,45 A 20,20 0 1,1 54,20 Z" />
    <path android:fillColor="#FFF68F" android:pathData="M 55,22 A 23,23 0 1,0 78,45 A 18,18 0 1,1 55,22 Z" />

    <!-- Distant Stars/Orbs -->
{generate_stars(30, 99)}
{generate_sparkles(10, 100)}

    <!-- Magical Swirl -->
{generate_swirls()}

    <!-- Ornate Book Back Cover -->
    <path
        android:fillColor="#1A0D2A"
        android:strokeColor="#FFD700"
        android:strokeWidth="1.5"
        android:strokeLineJoin="round"
        android:pathData="M 20,78 C 20,78 35,68 54,72 C 73,68 88,78 88,78 L 88,83 C 88,83 73,73 54,77 C 35,73 20,83 20,83 Z" />
        
    <!-- Golden Cover Corners -->
    <path android:fillColor="#FFD700" android:pathData="M 20,78 L 26,75 L 26,81 L 20,83 Z M 88,78 L 82,75 L 82,81 L 88,83 Z" />
    
    <!-- Cover Runes/Ornaments -->
    <path android:fillColor="#FFD700" android:pathData="M 35,74 A 1,1 0 1,1 35.1,74 Z M 45,74 A 1,1 0 1,1 45.1,74 Z M 63,74 A 1,1 0 1,1 63.1,74 Z M 73,74 A 1,1 0 1,1 73.1,74 Z" />

    <!-- Stacked Pages -->
{generate_pages(left=True)}
{generate_pages(left=False)}

    <!-- Top Left Page Text/Runes -->
    <path
        android:strokeColor="#C0A0D0"
        android:strokeWidth="1"
        android:strokeLineCap="round"
        android:pathData="M 32,52 C 38,48 46,48 50,49 M 32,56 C 38,52 46,52 50,53 M 32,60 C 38,56 46,56 50,57 M 32,64 C 36,61 40,60 44,61" />
    <path
        android:strokeColor="#9B59B6"
        android:strokeWidth="0.5"
        android:strokeLineCap="round"
        android:pathData="M 34,54 C 40,50 48,50 52,51 M 34,58 C 40,54 48,54 52,55" />

    <!-- Top Right Page Text/Runes -->
    <path
        android:strokeColor="#C0A0D0"
        android:strokeWidth="1"
        android:strokeLineCap="round"
        android:pathData="M 76,52 C 70,48 62,48 58,49 M 76,56 C 70,52 62,52 58,53 M 76,60 C 70,56 62,56 58,57 M 76,64 C 70,60 62,60 58,61" />
    <path
        android:strokeColor="#9B59B6"
        android:strokeWidth="0.5"
        android:strokeLineCap="round"
        android:pathData="M 74,54 C 68,50 60,50 56,51 M 74,58 C 68,54 60,54 56,55" />

    <!-- Book Spine Overlay -->
    <path
        android:pathData="M 53,42 C 53,42 54,41 55,42 L 55,79 C 55,79 54,80 53,79 Z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:type="linear"
                android:startX="53"
                android:startY="60"
                android:endX="55"
                android:endY="60"
                android:startColor="#C0A0D0"
                android:centerColor="#FFFFFF"
                android:endColor="#C0A0D0" />
        </aapt:attr>
    </path>
    
    <!-- AI Core Orb (Local AI representation) -->
    <path
        android:pathData="M 54,34 A 6,6 0 1,1 53.9,34 Z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:type="radial"
                android:centerX="54"
                android:centerY="34"
                android:gradientRadius="6"
                android:startColor="#FFFFFF"
                android:endColor="#FFD700" />
        </aapt:attr>
    </path>
    <path
        android:pathData="M 54,28 L 56,32 L 60,34 L 56,36 L 54,40 L 52,36 L 48,34 L 52,32 Z"
        android:fillColor="#FFFFFF" />
        
    <path
        android:pathData="M 54,30 L 55.5,33 L 58.5,34 L 55.5,35 L 54,38 L 52.5,35 L 49.5,34 L 52.5,33 Z"
        android:fillColor="#FFF68F" />

    <!-- Glowing Bookmark Ribbon -->
    <path
        android:pathData="M 54,42 C 60,50 65,60 62,82 L 58,78 L 54,84 C 58,68 53,52 54,42 Z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:type="linear"
                android:startX="54"
                android:startY="42"
                android:endX="62"
                android:endY="84"
                android:startColor="#FF4081"
                android:endColor="#9C27B0" />
        </aapt:attr>
    </path>
    <!-- Ribbon Gold Edge -->
    <path
        android:strokeColor="#FFD700"
        android:strokeWidth="0.5"
        android:pathData="M 54,42 C 60,50 65,60 62,82 L 58,78 L 54,84" />
        
    <!-- Front Sparkles -->
{generate_sparkles(5, 55)}

</vector>
"""

with open('app/src/main/res/drawable/ic_launcher_background.xml', 'w') as f:
    f.write(bg_xml)

with open('app/src/main/res/drawable/ic_launcher_foreground.xml', 'w') as f:
    f.write(fg_xml)

print("Icons generated successfully!")
