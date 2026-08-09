with open("app/src/main/java/com/nightread/app/ui/customlayout/PriorityPaginationController.kt", "r") as f:
    content = f.read()

replacement = """
                if (processed.contains(key)) continue
                
                // Process
                ReaderMetrics.jobStarted()
                try {
                    val pages = paginateChunkCallback(task.chapterIndex, task.chunkIndex)
                    processed.add(key)
                    withContext(Dispatchers.Main) {
                        onChunkReady(task.chapterIndex, task.chunkIndex, pages)
                    }
                } catch (e: CancellationException) {
                    ReaderMetrics.logPaginationCancelled(task.chapterIndex, task.chunkIndex)
                    throw e
                } finally {
                    ReaderMetrics.jobFinished()
                }
"""

import re
content = re.sub(r'if \(processed\.contains\(key\)\) continue\s*// Process\s*try \{.*?\}.*?throw e\s*\}', replacement.strip(), content, flags=re.DOTALL)

with open("app/src/main/java/com/nightread/app/ui/customlayout/PriorityPaginationController.kt", "w") as f:
    f.write(content)
