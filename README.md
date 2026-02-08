# Thesis Text Data Analysis

簡短說明（中文）

## 介紹
這個專案是一個簡單的文本分析小工具，用來處理碩士論文／學術文章的文本（支援日文與中文），功能包括：
- 從 PDF 提取文字（使用 Apache PDFBox）
- 日文斷詞（使用 Lucene Kuromoji）與中文斷詞（jieba，專案已加入依賴但範例主要使用日文）
- 計算 TF-IDF、找出 Top 關鍵詞
- 基於句子計算關鍵詞共現
- 將結果輸出為條狀圖（PNG，使用 JFreeChart）

## 需求
- JDK 17
- Maven 3.x

## 建置
在專案根目錄（包含 `pom.xml`）執行：

```powershell
mvn -DskipTests package
```

成功後會在 `target/` 下產生可執行的 shaded JAR（包含依賴）。

## 執行
1. 將欲分析的 PDF 檔案命名為 `論文.pdf` 並放在專案根目錄，或修改 `src/main/java/org/light/ThesisTFIDFAnalysis.java` 中的 `pdfPath` 變數為你的檔案路徑。
2. 在專案根目錄執行：

```powershell
java -jar target\thesis-text-analysis-1.0-SNAPSHOT.jar
```

程式會輸出兩張圖檔：
- `tfidf_keywords.png`（TF-IDF 前 20）
- `cooccurrence_keywords.png`（共現前 20）

圖檔會輸出到專案執行的當前目錄。

## 常見問題
- 如果遇到字型顯示亂碼，請在 `ThesisTFIDFAnalysis#createBarChart` 中將 `Font` 改為你系統支援的中文字型（如 `Meiryo`、`Yu Gothic`、`MS UI Gothic` 等）。
- 若要改為處理多個 PDF 或改變停用詞，可修改程式碼中的對應邏輯。

## 開發者註記
主程式：`src/main/java/org/light/ThesisTFIDFAnalysis.java`

如果你需要我把程式改成可以傳入參數（例如指定 PDF 路徑、輸出目錄），我可以幫你實作。
