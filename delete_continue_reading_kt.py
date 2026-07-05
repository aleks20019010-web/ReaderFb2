with open("app/src/main/java/com/nightread/app/ui/LibraryFragment.kt", "r") as f:
    content = f.read()

import re

# Remove variable declarations
content = re.sub(r'\s*private lateinit var continueReadingAdapter: ContinueReadingAdapter', '', content)
content = re.sub(r'\s*private lateinit var layoutContinueReading: View', '', content)
content = re.sub(r'\s*private lateinit var rvContinueReading: RecyclerView', '', content)

# Remove bindings
content = re.sub(r'\s*layoutContinueReading = view\.findViewById\(R\.id\.layoutContinueReading\)', '', content)
content = re.sub(r'\s*rvContinueReading = view\.findViewById\(R\.id\.rvContinueReading\)', '', content)

# Remove adapter initialization
adapter_init = r'\s*continueReadingAdapter = ContinueReadingAdapter\([^)]*\)\s*\{\s*book\s*->[^}]*\}\s*'
content = re.sub(adapter_init, '', content, flags=re.DOTALL)

# Remove rv settings
content = re.sub(r'\s*rvContinueReading\.layoutManager = LinearLayoutManager\(requireContext\(\), LinearLayoutManager\.HORIZONTAL, false\)', '', content)
content = re.sub(r'\s*rvContinueReading\.adapter = continueReadingAdapter', '', content)

# Remove visibility toggles
content = re.sub(r'\s*layoutContinueReading\.visibility = View\.GONE', '', content)
content = re.sub(r'\s*layoutContinueReading\.visibility = View\.VISIBLE', '', content)

# Remove updates
content = re.sub(r'\s*continueReadingAdapter\.updateBooks\(recentBooks\)', '', content)

with open("app/src/main/java/com/nightread/app/ui/LibraryFragment.kt", "w") as f:
    f.write(content)
