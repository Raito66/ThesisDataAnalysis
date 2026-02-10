package org.light;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.data.category.DefaultCategoryDataset;
import org.apache.lucene.analysis.ja.JapaneseTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.ja.tokenattributes.PartOfSpeechAttribute;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ThesisTFIDFAnalysis {

    private static final Logger logger = LoggerFactory.getLogger(ThesisTFIDFAnalysis.class);

    // 停用詞（已針對論文主題大幅調整）
    private static final Set<String> STOPWORDS = new HashSet<>(Arrays.asList(
            // 一般功能詞（助詞、助動詞） ── 保留
            "する","ある","なる","こと","これ","それ","あれ","ため","よう",
            "です","ます","である","及び", "いる","れる","ない",
            "が","の","を","に","へ","と","で","や","から","まで","より","だ",

            // 論文結構詞（保留，但移除最核心的社會相關詞）
            "研究","本研究","本稿","本論文",
            "年","日","月","人","者",

            // 目前 Top 裡明顯不想要的詞 + 雜訊
            "的","さ","し","れ","い","E",
            "一","一般","もの","場合","of",
            "議員","雑則","岡山大学","同性愛","浸透","放送","案田","律令","附屬","当該",

            // 參考文獻與常見雜訊 ── 大量加強
            "…","――","－","閲覧","１","２","３","1","2","3","2022",
            "平成","年度","調査","結果","資料","出典","参照","引用","文献","参考文献",
            "図表","表","図","資料來源","出處","參考","註","注","付録","附録",

            // 英文縮寫、單字母、論文英文部分雜訊 ── 保留
            "a","b","c","d","e","f","g","h","i","j","k","l","m","n","o","p","q","r","s","t","u","v","w","x","y","z",
            "A","B","C","D","E","F","G","H","I","J","K","L","M","N","O","P","Q","R","S","T","U","V","W","X","Y","Z",
            "vol","pp","no","et","al","study","patients","environment","social","comparison","taiwan","japan",
            "thesis","master","degree","university","kaohsiung","science","technology","june","republic","china",

            // 其他明顯噪音詞（社會相關核心詞已移除，但這些仍是雜訊） ── 大量加強
            "ナイフ", "知遇", "表現", "感謝", "契約", "公布", "映画", "アンケート", "用語", "明確",

            // 斷詞錯誤與符號雜訊 ── 保留
            "口","一","口一","ロイ","ー"," - ","&","by","a","the","of","in","for","to","and"
    ));

    // 使用 log-scaled TF（1 + ln(freq)）以降低高頻詞偏差
    private static final boolean USE_LOG_TF = false;

    // Top N 常數
    private static final int TOP_N = 20;

    // 每段落至少幾句才算有效段落
    private static final int PARA_SENTENCE_GROUP = 5;

    // 最小 DF 門檻（建議 5～10，可依結果調整）
    private static final int MIN_DF = 8;

    public static void main(String[] args) {
        String pdfPath = "論文.pdf";
        String tfidfImage = "tfidf_keywords.png";
        String cooccurrenceImage = "cooccurrence_keywords.png";

        // 1. PDF -> 文字（限制只讀 1～154 頁）
        String rawText = extractTextFromPDF(pdfPath);
        System.out.println("=== RAW TEXT (前 300 字) ===");
        System.out.println(safeHead(rawText, 300));
        System.out.println("================================");

        String cleanedFull = cleanText(rawText);
        System.out.println("=== CLEAN TEXT (前 300 字) ===");
        System.out.println(safeHead(cleanedFull, 300));
        System.out.println("================================");

        // TF-IDF corpus
        List<String> tfidfDocs = splitByParagraphs(rawText).stream()
                .map(ThesisTFIDFAnalysis::cleanText)
                .filter(s -> !s.isEmpty())
                .toList();
        System.out.println("TF-IDF 用的文件數: " + tfidfDocs.size());

        List<List<String>> tfidfCorpus = new ArrayList<>();
        int docIndex = 0;
        for (String doc : tfidfDocs) {
            docIndex++;
            System.out.println("---- TF-IDF Doc " + docIndex + " raw (前 80 字) ----");
            System.out.println(safeHead(doc, 80));

            List<String> tokens = tokenizeJapanese(doc);
            System.out.println("TF-IDF Doc " + docIndex + " tokenize 後 token 數: " + tokens.size());
            System.out.println("  tokens (前 20 個): " + safeHeadList(tokens));

            tokens = filterStopwords(tokens);
            System.out.println("TF-IDF Doc " + docIndex + " 停用詞/雜訊過濾後 token 數: " + tokens.size());
            System.out.println("  filtered tokens (前 20 個): " + safeHeadList(tokens));

            List<String> ngrams = generateBigrams(tokens);
            System.out.println("TF-IDF Doc " + docIndex + " n-gram 數: " + ngrams.size());
            System.out.println("================================");

            tfidfCorpus.add(ngrams);
        }

        long tfidfNonEmpty = tfidfCorpus.stream().filter(d -> !d.isEmpty()).count();
        System.out.println("TF-IDF 非空文件數: " + tfidfNonEmpty);

        if (tfidfNonEmpty == 0) {
            System.out.println("TF-IDF 沒有可用的 token。");
            return;
        }

        Map<String, Double> tfidf = calculateTFIDF(tfidfCorpus);
        System.out.println("TF-IDF 詞彙總數: " + tfidf.size());

        if (tfidf.isEmpty()) {
            System.out.println("TF-IDF 結果為空。");
            return;
        }

        Map<String, Double> topKeywords = tfidf.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(50)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));

        System.out.println("=== Top 50 Keywords (TF-IDF) ===");
        topKeywords.forEach((k, v) -> System.out.println(k + " : " + v));

        // 共現分析 corpus
        List<String> sentenceTexts = splitBySentenceForCoOcc(cleanedFull);
        System.out.println("共現分析用的段落數: " + sentenceTexts.size());

        List<List<String>> coCorpus = new ArrayList<>();
        for (String para : sentenceTexts) {
            List<String> tokens = tokenizeJapanese(para);
            tokens = filterStopwords(tokens);
            List<String> ngrams = generateBigrams(tokens);
            coCorpus.add(ngrams);
        }

        long coNonEmpty = coCorpus.stream().filter(d -> !d.isEmpty()).count();
        System.out.println("共現分析 非空段落數: " + coNonEmpty);

        if (coNonEmpty == 0) {
            System.out.println("共現分析沒有可用的 token，跳過共現計算。");
        }

        Map<String, Integer> cooccurrence = calculateCoOccurrences(coCorpus, topKeywords.keySet());
        System.out.println("=== Top " + TOP_N + " Co-occurrence Pairs ===");
        cooccurrence.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(TOP_N)
                .forEach(e -> System.out.println(e.getKey() + " : " + e.getValue()));

        createBarChart(topKeywords, "TF-IDF 關鍵詞分析", "關鍵詞", "TF-IDF 權重", tfidfImage);

        Map<String, Double> topCo = cooccurrence.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(TOP_N)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().doubleValue(),
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
        createBarChart(topCo, "關鍵詞共現分析", "詞對", "共現句數", cooccurrenceImage);

        System.out.println("分析完成，輸出圖檔：");
        System.out.println("1. " + tfidfImage);
        System.out.println("2. " + cooccurrenceImage);
    }

    private static String extractTextFromPDF(String path) {
        try (PDDocument document = Loader.loadPDF(new File(path))) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setLineSeparator("\n");

            // 只讀取 1～154 頁（避開參考文獻）
            stripper.setStartPage(1);
            stripper.setEndPage(154);

            return stripper.getText(document);
        } catch (IOException e) {
            throw new RuntimeException("PDF 讀取失敗: " + path, e);
        }
    }

    // 以下其他方法保持不變，只貼出關鍵修改部分
    // （完整程式碼太長，以下只列出有改動的部分，其餘與你原本相同）

    private static List<String> filterStopwords(List<String> tokens) {
        return tokens.stream()
                .filter(t -> !STOPWORDS.contains(t))
                .filter(t -> !t.matches("[0-9０-９]+"))
                .filter(t -> !t.matches("[.…。]+"))
                .filter(t -> t.length() >= 2)
                .filter(t -> t.matches(".*[\\p{InHiragana}\\p{InKatakana}\\p{InCJKUnifiedIdeographs}].*"))
                .filter(t -> !t.matches("[a-zA-Z0-9\\-ー]+"))
                .collect(Collectors.toList());
    }

    private static Map<String, Double> calculateTFIDF(List<List<String>> corpus) {
        Map<String, Double> tfidf = new HashMap<>();
        Map<String, Integer> df = new HashMap<>();
        int docCount = corpus.size();

        for (List<String> doc : corpus) {
            Set<String> unique = new HashSet<>(doc);
            for (String term : unique) {
                df.put(term, df.getOrDefault(term, 0) + 1);
            }
        }

        if (docCount == 0) return tfidf;

        Map<String, List<Double>> termScores = new HashMap<>();
        for (List<String> doc : corpus) {
            if (doc.isEmpty()) continue;

            Map<String, Long> tf = doc.stream().collect(Collectors.groupingBy(e -> e, Collectors.counting()));

            for (String term : tf.keySet()) {
                Integer dfVal = df.get(term);
                if (dfVal == null || dfVal < MIN_DF) continue;

                long freq = tf.get(term);
                double tfValue = USE_LOG_TF ? (1.0 + Math.log((double) freq)) : (double) freq;
                double idfValue = Math.log((double) (docCount + 1) / (dfVal + 1)) + 1;
                double score = tfValue * idfValue;

                termScores.computeIfAbsent(term, k -> new ArrayList<>()).add(score);
            }
        }

        for (Map.Entry<String, List<Double>> entry : termScores.entrySet()) {
            List<Double> scores = entry.getValue();
            double avgScore = scores.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            tfidf.put(entry.getKey(), avgScore);
        }

        return tfidf;
    }

    private static Map<String, Integer> calculateCoOccurrences(List<List<String>> corpus, Set<String> targetTerms) {
        Map<String, Integer> co = new HashMap<>();

        for (List<String> doc : corpus) {
            if (doc.isEmpty()) continue;

            Set<String> termsInDoc = new HashSet<>();
            for (String t : doc) {
                if (targetTerms.contains(t)) termsInDoc.add(t);
            }

            List<String> list = new ArrayList<>(termsInDoc);
            for (int i = 0; i < list.size(); i++) {
                for (int j = i + 1; j < list.size(); j++) {
                    String t1 = list.get(i);
                    String t2 = list.get(j);
                    String key = t1.compareTo(t2) < 0 ? t1 + " - " + t2 : t2 + " - " + t1;
                    co.put(key, co.getOrDefault(key, 0) + 1);
                }
            }
        }

        // 已註解掉 min 門檻，顯示所有共現
        // co.entrySet().removeIf(e -> e.getValue() < 2);

        return co;
    }

    // 以下方法與你原本相同（safeHead, cleanText, splitByParagraphs, tokenizeJapanese, generateUnigrams, createBarChart, applyFont 等）
    // 這裡省略重複貼上，請直接保留你原本的這些方法

    private static String safeHead(String s, int n) {
        if (s == null) return "";
        return s.substring(0, Math.min(n, s.length()));
    }

    private static <T> String safeHeadList(List<T> list) {
        if (list == null) return "[]";
        return list.stream().limit(20).map(String::valueOf).collect(Collectors.joining(", "));
    }

    private static String cleanText(String text) {
        if (text == null) return "";
        text = text.replaceAll("https?://\\S+", " ");
        text = text.replaceAll("\\p{Punct}", " ").replaceAll("\\s+", " ").trim();
        return text;
    }

    private static List<String> splitBySentenceForCoOcc(String text) {
        if (text == null || text.isEmpty()) return Collections.emptyList();
        String restored = text.replace("。", "。 ").replace("．", "． ").replace(".", ". ");
        String[] parts = restored.split("[。．.]+");
        List<String> docs = new ArrayList<>();
        for (String p : parts) {
            String trimmed = p.trim();
            if (!trimmed.isEmpty()) docs.add(trimmed);
        }
        return docs;
    }

    private static List<String> splitByParagraphs(String rawText) {
        if (rawText == null || rawText.isEmpty()) return Collections.emptyList();
        String[] parts = rawText.split("\\r?\\n\\s*\\r?\\n+");
        List<String> paras = new ArrayList<>();
        for (String p : parts) {
            String t = p.trim();
            if (!t.isEmpty()) paras.add(t);
        }
        if (paras.size() <= 1) {
            List<String> sentences = splitBySentenceForCoOcc(rawText);
            if (sentences.isEmpty()) return paras;
            StringBuilder sb = new StringBuilder();
            int cnt = 0;
            for (String s : sentences) {
                if (!sb.isEmpty()) sb.append(' ');
                sb.append(s);
                cnt++;
                if (cnt >= PARA_SENTENCE_GROUP) {
                    String para = sb.toString().trim();
                    if (!para.isEmpty()) paras.add(para);
                    sb.setLength(0);
                    cnt = 0;
                }
            }
            if (!sb.isEmpty()) {
                String para = sb.toString().trim();
                if (!para.isEmpty()) paras.add(para);
            }
        }
        return paras;
    }

    private static List<String> tokenizeJapanese(String text) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isEmpty()) return result;
        try {
            JapaneseTokenizer tokenizer = new JapaneseTokenizer(null, false, JapaneseTokenizer.Mode.NORMAL);
            tokenizer.setReader(new java.io.StringReader(text));
            tokenizer.reset();
            CharTermAttribute charTermAttr = tokenizer.getAttribute(CharTermAttribute.class);
            PartOfSpeechAttribute posAttr = tokenizer.getAttribute(PartOfSpeechAttribute.class);
            while (tokenizer.incrementToken()) {
                String term = charTermAttr.toString();
                String pos = posAttr.getPartOfSpeech();
                if (pos != null && (pos.startsWith("名詞") || pos.startsWith("動詞") || pos.startsWith("形容詞"))) {
                    result.add(term);
                }
            }
            tokenizer.end();
            tokenizer.close();
        } catch (IOException e) {
            logger.error("日文斷詞時發生 I/O 錯誤", e);
        }
        return result;
    }

//    private static List<String> generateUnigrams(List<String> tokens) {
//        if (tokens == null || tokens.isEmpty()) return Collections.emptyList();
//        return new ArrayList<>(tokens);
//    }

    // 原本的 generateUnigrams 改名或保留，新增一個 2-gram 方法
    private static List<String> generateBigrams(List<String> tokens) {
        if (tokens == null || tokens.size() < 2) {
            return Collections.emptyList();
        }
        List<String> bigrams = new ArrayList<>();
        for (int i = 0; i < tokens.size() - 1; i++) {
            String bigram = tokens.get(i) + tokens.get(i + 1);  // 直接連在一起，例如 "就労支援"
            bigrams.add(bigram);
        }
        return bigrams;
    }


    private static void createBarChart(Map<String, Double> data, String title, String categoryLabel, String valueLabel, String outputFile) {
        if (data == null || data.isEmpty()) {
            System.out.println("沒有資料可以畫圖：" + title);
            return;
        }
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        data.forEach((k, v) -> dataset.addValue(v, title, k));
        JFreeChart chart = ChartFactory.createBarChart(title, categoryLabel, valueLabel, dataset);
        Font font = new Font("MS UI Gothic", Font.PLAIN, 16);
        applyFont(chart, font);
        try {
            ChartUtils.saveChartAsPNG(new File(outputFile), chart, 8500, 800);
        } catch (IOException e) {
            logger.error("圖檔儲存失敗: {}", outputFile, e);
            throw new RuntimeException("圖檔儲存失敗: " + outputFile, e);
        }
    }

    private static void applyFont(JFreeChart chart, Font font) {
        if (chart.getTitle() != null) chart.getTitle().setFont(font);
        CategoryPlot plot = chart.getCategoryPlot();
        plot.getDomainAxis().setTickLabelFont(font);
        plot.getDomainAxis().setLabelFont(font);
        plot.getRangeAxis().setTickLabelFont(font);
        plot.getRangeAxis().setLabelFont(font);
        if (chart.getLegend() != null) chart.getLegend().setItemFont(font);
    }
}