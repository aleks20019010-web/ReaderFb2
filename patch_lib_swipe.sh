sed -i 's/if (viewHolder is SeriesGroupAdapter.HeaderViewHolder) return 0//g' app/src/main/java/com/nightread/app/ui/LibraryFragment.kt
sed -i 's/return if (adapter.getItemViewType(position) == SeriesGroupAdapter.VIEW_TYPE_HEADER) 3 else 1/return 1/g' app/src/main/java/com/nightread/app/ui/LibraryFragment.kt
