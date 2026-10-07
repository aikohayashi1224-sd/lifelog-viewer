# のたろん（Android アプリ）

「ひとりじゃない、を、ゆるやかに。」仲間と、そろそろ会いたくなるきっかけをつくる、小さなグループのためのアプリ。

- 使用技術：Kotlin / Jetpack Compose (Material3) / Supabase（認証・PostgreSQL・RLS・ストレージ）/ Google Apps Script（招待メール）
- APK：`../apk/nota_1006.apk`（デバッグビルド）

## ビルド方法
1. `local.properties.example` を `local.properties` にコピーし、自分の Supabase / GAS の値を入れる（`local.properties` は Git に含めない）。
2. Android Studio で開いて実行、または `./gradlew assembleDebug`。

※ 接続先（Supabase・GAS）の設定は含まれていないため、ソースだけでは動作しません。
