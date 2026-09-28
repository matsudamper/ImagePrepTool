# ImagePrepTool

画像の処理を行い、公開する形に整える Compose Desktop アプリケーション（配布は Windows 向け MSI、開発ビルドは Linux/macOS/Windows で可能）。

## できること

- 対象フォルダを選び、処理する画像を Step1 で選択
- Step2 でリサイズ・形式変換・EXIF 由来テキストの焼き込み（位置: 左上 / 右上 / 左下 / 右下）
- 起動時に `cwebp` / `dwebp` / `magick` / `heif-convert` の PATH 確認

## 対応拡張子（読み込み）

- jpg / jpeg / png / webp / gif / bmp
- HEIF/HEIC（`heif-convert` または `magick` が PATH にある場合）

## 必要な外部ツール（Windows）

PATH に以下がある前提（不足時は Step1 の確認パネルに表示）:

| 用途 | コマンド |
|------|----------|
| WebP 出力 | `cwebp` |
| WebP 読み込み | `dwebp` |
| HEIF 変換 | `heif-convert` または `magick` |

## ビルド

```bash
./gradlew build
```

### Windows 単体 EXE（Java 不要で配布）

Windows 上で実行します。JRE は EXE / 配布フォルダに同梱されます。

| 成果物 | コマンド | 説明 |
|--------|----------|------|
| インストーラ EXE | `gradlew.bat packageReleaseExe` | 1 ファイルのセットアップ EXE（`build/compose/binaries/main-release/exe/`） |
| ポータブル | `gradlew.bat packagePortableWindowsExe` | ZIP 内の `ImagePrepTool.exe` を直接起動（`build/compose/binaries/main-release/portable/`） |

CI では `windows-latest` ジョブが上記をビルドし、Artifacts にアップロードします。

デスクトップ実行（開発時・GUI 環境）:

```bash
./gradlew run
```

## 操作フロー

1. **Step1: 選択** — 対象フォルダ → 画像チェック → 「Step2 へ」
2. **Step2: 編集と出力** — 最大幅/高、出力形式、EXIF 位置などを設定 → 処理実行（既定出力: `<対象フォルダ>/output`）

## CI

GitHub Actions（`.github/workflows/build.yml`）で Linux 上 `./gradlew build` を実行します。
