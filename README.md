# 碩士論文關鍵詞與共現分析工具

## 專案概述

這是一個用來分析碩士論文文字內容的 Java 小工具，專門針對論文：

**《考察圍繞精神疾病者的社會環境之研究 ─ 通通過台灣、日本的比較》**  
**A Study Examining the Social Environment of Mentally Ill Patients by a Comparison between Taiwan & Japan**

作者：洪翊倫  
完成年份：2022

工具主要功能是從論文 PDF 自動提取關鍵詞（使用 TF-IDF），並分析關鍵詞之間的共現關係，產生視覺化長條圖，幫助快速掌握論文的主題重點與概念關聯。

## 功能特色

- 讀取 PDF 檔案（支援日文、中文、英文混排）
- 使用 Lucene JapaneseTokenizer 進行日文斷詞
- 計算 TF-IDF 關鍵詞重要性（支援 log-TF 與 DF 門檻）
- 計算句子級別的關鍵詞共現
- 自動產生兩張 PNG 圖表：
    - TF-IDF 關鍵詞排行圖
    - 關鍵詞共現強度圖
- 內建大量停用詞清單（功能詞、論文通用詞、參考文獻雜訊、英文縮寫等）

## 使用方式

1. 將論文 PDF 檔命名為 `論文.pdf`，放在與程式相同的資料夾
2. 執行 `ThesisTFIDFAnalysis.java` 的 `main` 方法
3. 程式會自動產生：
    - `tfidf_keywords.png`：TF-IDF 關鍵詞排行
    - `cooccurrence_keywords.png`：關鍵詞共現排行
4. 控制台會顯示 Top 關鍵詞與 Top 共現詞對，以及斷詞與過濾過程的 debug 資訊

## 主要參數調整

位於程式開頭的常數：

```java
private static final int END_PAGE = 154;           // 只讀取到第幾頁（避開參考文獻）
private static final int TOP_N = 20;               // 顯示前幾名
private static final int MIN_DF = 5;               // 詞至少出現在幾個文件
private static final int PARA_SENTENCE_GROUP = 5;  // 合併段落時每幾句一組
private static final boolean USE_LOG_TF = true;    // 是否使用 log-scaled TF