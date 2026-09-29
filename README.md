# ImagePrepTool

画像の処理を行い、公開する形に整える Compose Desktop アプリケーション（配布は Windows 向け MSI、開発ビルドは Linux/macOS/Windows で可能）。

## できること

- フォルダを開く／フォルダや画像をウィンドウにドロップして読み込み（起動引数でフォルダ指定も可）
- サムネイル一覧で書き出す画像を選択（Space で切り替え、矢印キーで移動）
- 書き出し後の見た目をその場でプレビュー（元画像との切り替え）
- リサイズ（元のサイズ／長辺指定／幅×高さに収める。拡大はしない）
- 形式変換（JPEG／PNG／WebP／元の形式）と品質指定。メタデータは除去
- キャプション焼き込み（撮影情報 or 任意テキスト、四隅・大きさ・スタイル指定）
- EXIF の向きを反映、元画像は上書きしない、同名ファイルは確認（別名／上書き／スキップ）
- 設定・書き出し先・最近使ったフォルダは次回起動時に復元

## 対応形式

| 読み込み | 追加ツール |
|----------|------------|
| JPEG / PNG / WebP / GIF / BMP | 不要 |
| HEIC / HEIF | `heif-dec`（`heif-convert`）または `magick` |

| 書き出し | 追加ツール |
|----------|------------|
| JPEG / PNG | 不要 |
| WebP | `cwebp` |

外部ツールの有無は画面右上のアイコンから確認できます。不足している場合は該当操作の近くに案内が出ます。

## モジュール構成

| モジュール | 内容 |
|------------|------|
| `:app` | Compose Desktop UI・ViewModel・エントリポイント |
| `:image-processing` | 画像リサイズ/変換、EXIF、外部ツール確認、ドメインモデル |

## ビルド

```bash
./gradlew build
```

### Windows 単体 EXE（Java 不要で配布）

Windows 上で実行します。JRE は EXE / 配布フォルダに同梱されます。

| 成果物 | コマンド | 説明 |
|--------|----------|------|
| インストーラ EXE | `gradlew.bat :app:packageReleaseExe` | `app/build/compose/binaries/main-release/exe/` |
| ポータブル | `gradlew.bat :app:packagePortableWindowsExe` | `app/build/compose/binaries/main-release/portable/` |

CI では `windows-latest` ジョブが上記をビルドし、Artifacts にアップロードします。

デスクトップ実行（開発時・GUI 環境）:

```bash
./gradlew :app:run
```

## 操作フロー

1. フォルダを開く（またはドロップ）
2. 一覧で書き出す画像を選び、右パネルでサイズ・形式・キャプション・書き出し先を設定（プレビューに即反映）
3. 「N 枚を書き出す」（Ctrl+Enter）→ 進捗表示 → 結果から書き出し先フォルダを開く

## CI

GitHub Actions（`.github/workflows/build.yml`）で Linux 上 `./gradlew build` を実行します。
