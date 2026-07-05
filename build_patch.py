with open("app/build.gradle.kts", "r") as f:
    content = f.read()

content = content.replace(
    'implementation(libs.androidx.swiperefreshlayout)',
    'implementation(libs.androidx.swiperefreshlayout)\n    implementation("com.facebook.shimmer:shimmer:0.5.0")'
)

with open("app/build.gradle.kts", "w") as f:
    f.write(content)
