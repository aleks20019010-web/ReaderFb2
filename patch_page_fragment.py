import sys

content = open('app/src/main/java/com/nightread/app/ui/PageFragment.kt').read()

old_on_view_created = """    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        viewLifecycleOwner.lifecycleScope.launch {"""

new_on_view_created = """    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val textView = view.findViewById<TextView>(R.id.textView)
        ViewCompat.setOnApplyWindowInsetsListener(textView) { v, windowInsets ->
            val displayCutoutInsets = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout())
            val statusBarInsets = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars())
            val topInset = maxOf(statusBarInsets.top, displayCutoutInsets.top)
            
            val dp8 = (8 * v.resources.displayMetrics.density).toInt()
            val dp16 = (16 * v.resources.displayMetrics.density).toInt()
            
            v.setPadding(dp16, dp8 + topInset, dp16, dp8)
            windowInsets
        }
        view.requestApplyInsets()

        viewLifecycleOwner.lifecycleScope.launch {"""

content = content.replace(old_on_view_created, new_on_view_created)

# Also fix the justification mode inside updateStyle
old_update_style = """        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            textView.justificationMode = android.text.Layout.JUSTIFICATION_MODE_NONE
        }"""

new_update_style = """        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            textView.justificationMode = android.text.Layout.JUSTIFICATION_MODE_INTER_WORD
        }"""

content = content.replace(old_update_style, new_update_style)

open('app/src/main/java/com/nightread/app/ui/PageFragment.kt', 'w').write(content)
