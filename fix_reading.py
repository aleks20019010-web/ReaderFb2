import re

with open('app/src/main/java/com/nightread/app/ui/ReadingActivity.kt', 'r') as f:
    content = f.read()

pattern = r"    private fun setupGestures\(\).*?    private fun toggleBars\(\) \{"

replacement = """    private fun setupGestures() {
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
        })
        
        viewPager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
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
                        if (!isGestureConsumed) {
                            isGestureConsumed = true
                            val cancelEvent = android.view.MotionEvent.obtain(event)
                            cancelEvent.action = android.view.MotionEvent.ACTION_CANCEL
                            super.dispatchTouchEvent(cancelEvent)
                            cancelEvent.recycle()
                        }
                        
                        val deltaBrightness = dy / (height / 2f)
                        val newBrightness = (startBrightness + deltaBrightness).coerceIn(0.01f, 1f)
                        BrightnessHelper.setBrightness(this, newBrightness)
                        com.nightread.app.data.SettingsManager.setBrightness(this, newBrightness)
                        
                        tvBrightness.visibility = android.view.View.VISIBLE
                        tvBrightness.text = "☀ ${(newBrightness * 100).toInt()}%"
                        return true
                    }
                }
            }
            android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                if (isGestureConsumed) {
                    tvBrightness.visibility = android.view.View.GONE
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

    private fun toggleBars() {"""

content = re.sub(pattern, replacement, content, flags=re.DOTALL)

with open('app/src/main/java/com/nightread/app/ui/ReadingActivity.kt', 'w') as f:
    f.write(content)
