import re

with open('app/src/main/java/com/nightread/app/ui/ReadingActivity.kt', 'r') as f:
    content = f.read()

# Add gesture detector and tvBrightness
content = content.replace("private var isNightMode = false", 
"""private var isNightMode = false
    private lateinit var gestureDetector: android.view.GestureDetector
    private lateinit var tvBrightness: TextView
    private var startX = 0f
    private var isGestureConsumed = false""")

content = content.replace("topBar = findViewById(R.id.topBar)",
"""topBar = findViewById(R.id.topBar)
        tvBrightness = findViewById(R.id.tvBrightness)""")

content = content.replace("setupGestures()",
"""setupGestures()
        gestureDetector = android.view.GestureDetector(this, object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: android.view.MotionEvent): Boolean {
                val cornerSize = 80 * resources.displayMetrics.density
                if (e.x < cornerSize && e.y < cornerSize) {
                    toggleNightMode()
                } else {
                    toggleBars()
                }
                return super.onSingleTapConfirmed(e)
            }
        })""")

# Replace setupGestures implementation and add dispatchTouchEvent
setup_gestures_regex = r"    private fun setupGestures\(\) \{.*?(?=    private fun toggleBars\(\) \{)"
replacement = """    private fun setupGestures() {
        // Only onPageChange callback here now
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateBottomBar(position)
            }
        })
    }

    override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean {
        val width = window.decorView.width
        val height = window.decorView.height
        
        when (event.action) {
            android.view.MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
                isChangingBrightness = false
                isGestureConsumed = false
                
                val rightEdge = width - (60 * resources.displayMetrics.density)
                if (event.x > rightEdge && event.y > height / 2f) {
                    if (!isTouchOnUiBars(event)) {
                        isChangingBrightness = true
                        startBrightness = BrightnessHelper.getBrightness(this)
                    }
                }
            }
            android.view.MotionEvent.ACTION_MOVE -> {
                if (isChangingBrightness) {
                    val dy = startY - event.y
                    if (Math.abs(dy) > 10 * resources.displayMetrics.density || isGestureConsumed) {
                        isGestureConsumed = true
                        val deltaBrightness = dy / (height / 2f)
                        val newBrightness = (startBrightness + deltaBrightness).coerceIn(0.01f, 1f)
                        BrightnessHelper.setBrightness(this, newBrightness)
                        
                        tvBrightness.visibility = View.VISIBLE
                        tvBrightness.text = "☀ ${(newBrightness * 100).toInt()}%"
                        return true
                    }
                }
            }
            android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                if (isGestureConsumed) {
                    tvBrightness.visibility = View.GONE
                    return true
                }
            }
        }
        
        if (!isTouchOnUiBars(event)) {
            gestureDetector.onTouchEvent(event)
        }
        
        return super.dispatchTouchEvent(event)
    }

    private fun isTouchOnUiBars(event: android.view.MotionEvent): Boolean {
        if (!isBarsVisible) return false
        val rect = android.graphics.Rect()
        topBar.getGlobalVisibleRect(rect)
        if (rect.contains(event.rawX.toInt(), event.rawY.toInt())) return true
        
        bottomBar.getGlobalVisibleRect(rect)
        if (rect.contains(event.rawX.toInt(), event.rawY.toInt())) return true
        
        return false
    }

"""

content = re.sub(setup_gestures_regex, replacement, content, flags=re.DOTALL)

with open('app/src/main/java/com/nightread/app/ui/ReadingActivity.kt', 'w') as f:
    f.write(content)
